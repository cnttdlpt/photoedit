import SwiftUI
import PhotosUI
import Photos

private struct EditorSession: Codable {
    var settings: Settings
    var recipe: Recipe?
    var photoName: String?
    var info: PhotoInfo
    var frameStyle: Int
}

enum EditorFailure: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}

/// CPU and disk work are serialized away from the main actor; cancelled previews stop before the next job.
actor ImageWorker {
    func thumbnail(_ source: RasterImage, settings: Settings?, lut: CubeLut?, info: PhotoInfo, style: Int) throws -> UIImage {
        try Task.checkCancellation()
        var small = (source.width <= 192 && source.height <= 192) ? source : try PhotoIO.resized(source, maxDimension: 192)
        if let settings { small = try ColorEngine.render(small, settings: settings, lut: lut) }
        try Task.checkCancellation()
        return try PhotoIO.uiImage(PhotoFrame.render(small, info: info, style: style))
    }
    func preview(_ source: RasterImage, settings: Settings, lut: CubeLut?, info: PhotoInfo, style: Int) throws -> UIImage {
        try Task.checkCancellation()
        let colored = try ColorEngine.render(source, settings: settings, lut: lut)
        try Task.checkCancellation()
        return try PhotoIO.uiImage(PhotoFrame.render(colored, info: info, style: style))
    }

    func open(_ url: URL) throws -> (RasterImage, UIImage, PhotoInfo) {
        let raster = try PhotoIO.decodePreview(url: url)
        return (raster, try PhotoIO.uiImage(raster), PhotoIO.readMetadata(url: url))
    }

    func read(_ url: URL, limit: Int) throws -> Data {
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= limit else { throw EditorFailure.message("Tệp quá lớn (giới hạn \(limit / 1_048_576) MB).") }
        let data = try Data(contentsOf: url, options: .mappedIfSafe)
        guard data.count <= limit else { throw EditorFailure.message("Tệp quá lớn.") }
        return data
    }

    func savePhoto(_ data: Data, directory: URL) throws -> URL {
        guard data.count <= 100 * 1_048_576 else { throw EditorFailure.message("Ảnh phải nhỏ hơn 100 MB.") }
        let url = directory.appendingPathComponent("photo-\(UUID().uuidString).image")
        try data.write(to: url, options: .atomic)
        return url
    }

    func export(source: URL, settings: Settings, lut: CubeLut?, size: ExportSize, format: ExportFormat, info: PhotoInfo, style: Int, destination: URL) throws -> (Int, Int) {
        let dimensions = try PhotoIO.export(sourceURL: source, settings: settings, lut: lut, size: size, format: format, info: info, frameStyle: style, destinationURL: destination)
        return (dimensions.width, dimensions.height)
    }
}

struct UndoState: Equatable {
    let settings: Settings
    let recipe: Recipe?
    let frameStyle: Int
    let photoInfo: PhotoInfo
    let notes: [String]
    let isAutoBalanced: Bool
}

@MainActor
final class EditorModel: ObservableObject {
    @Published var settings = Settings()
    @Published var recipe: Recipe?
    @Published var notes: [String] = []
    @Published var recipes: [Recipe] = []
    @Published var localLUTs: [LocalLUT] = []
    @Published var filmBindings: [String: String] = [:]
    @Published var onlineItems: [OnlineLUT] = []
    @Published var onlineRepository = "t0saki/lumix-original-looks"
    @Published var automaticUpdates = false
    @Published var photoInfo = PhotoInfo()
    @Published var frameStyle = 0
    @Published var originalImage: UIImage?
    @Published var editedImage: UIImage?
    @Published var isLoading = false
    @Published var isRendering = false
    @Published var isExporting = false
    @Published var onlineBusy = false
    @Published var status = "Ảnh được xử lý trên thiết bị"
    @Published var onlineStatus = "Chọn Tìm / cập nhật để xem LUT trong kho công khai."
    @Published var errorMessage: String?
    @Published var exportedURL: URL?
    @Published var exportedDimensions = ""
    @Published var ready = false
    @Published var thumbnailVersion = 0
    @Published var undoStack: [UndoState] = []
    @Published var redoStack: [UndoState] = []
    @Published var isAutoBalanced = false

    var canUndo: Bool { !undoStack.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }

    private(set) var activeLUT: CubeLut?
    private var sourceURL: URL?
    private var preview: RasterImage?
    private var thumbnailBase: RasterImage?
    private var store: LibraryStore?
    private var online: OnlineLUTService?
    private let worker = ImageWorker()
    private let thumbnailWorker = ImageWorker()
    private var thumbnails: [String: UIImage] = [:]
    private var renderTask: Task<Void, Never>?
    private var renderRevision = 0
    private var editRevision = 0
    private var lutRevision = 0
    private var started = false
    private var directory: URL?
    private var lastStoredRepository: String?
    private let applicationDirectory: URL?
    private let boundLUTLoader: (@MainActor (String) async throws -> CubeLut?)?

    init(applicationDirectory: URL? = nil, boundLUTLoader: (@MainActor (String) async throws -> CubeLut?)? = nil) {
        self.applicationDirectory = applicationDirectory
        self.boundLUTLoader = boundLUTLoader
    }

    private func loadBoundLUT(for film: String) async throws -> CubeLut? {
        if let boundLUTLoader { return try await boundLUTLoader(film) }
        return try await store?.boundLUT(for: film)
    }

    var hasPhoto: Bool { originalImage != nil }
    var busy: Bool { isLoading || isExporting }
    var lutName: String { localLUTs.first(where: { $0.id == filmBindings[settings.film] })?.name ?? "Chưa gắn LUT màu nền" }

    func start() async {
        guard !started else { return }; started = true
        do {
            let base = try applicationDirectory ?? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("IRO", isDirectory: true)
            let dir = base.appendingPathComponent("Editor", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            directory = dir
            let library = try LibraryStore(directory: base.appendingPathComponent("Library", isDirectory: true)); store = library
            recipes = try await library.loadBundledRecipes()
            let service = OnlineLUTService(store: library); online = service
            await reloadLibrary()
            onlineStatus = await library.snapshot().onlinePreferences.status
            let sessionURL = dir.appendingPathComponent("session.json")
            if FileManager.default.fileExists(atPath: sessionURL.path) {
                do {
                    let session = try JSONDecoder().decode(EditorSession.self, from: Data(contentsOf: sessionURL))
                    settings = try session.settings.validated(); recipe = session.recipe
                    photoInfo = session.info; frameStyle = min(30, max(0, session.frameStyle))
                    if let name = session.photoName, name == URL(fileURLWithPath: name).lastPathComponent {
                        let url = dir.appendingPathComponent(name)
                        let loaded = try await worker.open(url)
                        sourceURL = url; preview = loaded.0; originalImage = loaded.1
                        thumbnailBase = try? PhotoIO.resized(loaded.0, maxDimension: 192)
                    }
                } catch { status = "Không khôi phục được phiên trước. Bạn có thể mở ảnh mới." }
            }
            ready = true
            await updateBoundLUT()
            _ = await service.checkOnOpen()
            onlineStatus = await library.snapshot().onlinePreferences.status
            await reloadLibrary()
        } catch { show(error); ready = false; started = false }
    }

    func show(_ error: Error) { if !(error is CancellationError) { errorMessage = error.localizedDescription } }

    func reloadLibrary() async {
        guard let store else { return }
        let snapshot = await store.snapshot()
        recipes = snapshot.recipes
        localLUTs = snapshot.localLUTs; filmBindings = snapshot.filmBindings
        let storedRepository = snapshot.onlinePreferences.repository
        if lastStoredRepository == nil || onlineRepository == lastStoredRepository || storedRepository != lastStoredRepository {
            onlineRepository = storedRepository
        }
        lastStoredRepository = storedRepository
        automaticUpdates = snapshot.onlinePreferences.automaticUpdates
        thumbnails.removeAll(); thumbnailVersion += 1
    }

    private func updateBoundLUT() async {
        guard store != nil else { return }
        lutRevision += 1
        let film = settings.film, revision = lutRevision
        do {
            let lut = try await loadBoundLUT(for: film)
            guard settings.film == film, revision == lutRevision else { return }
            activeLUT = lut; changed(invalidateLookRequest: false)
        } catch {
            guard settings.film == film, revision == lutRevision else { return }
            activeLUT = nil; show(error); changed(invalidateLookRequest: false)
        }
    }

    func changed(immediate: Bool = false, invalidateLookRequest: Bool = true) {
        exportedURL = nil; exportedDimensions = ""
        if invalidateLookRequest { editRevision += 1 }
        persist()
        renderPreview(immediate: immediate)
    }

    private func renderPreview(immediate: Bool) {
        renderRevision += 1
        let revision = renderRevision
        renderTask?.cancel()
        guard let preview else { isRendering = false; return }
        let settings = settings, lut = activeLUT, info = photoInfo, style = frameStyle
        isRendering = true
        renderTask = Task {
            do {
                if !immediate {
                    try await Task.sleep(for: .milliseconds(120))
                }
                let image = try await worker.preview(preview, settings: settings, lut: lut, info: info, style: style)
                try Task.checkCancellation()
                guard revision == renderRevision else { return }
                editedImage = image; isRendering = false
                status = "\(preview.width) × \(preview.height) xem trước · \(style == 0 ? "Không khung" : PhotoFrame.styles[style])"
            } catch {
                if revision == renderRevision { isRendering = false; show(error) }
            }
        }
    }

    @discardableResult
    private func persist() -> Bool {
        guard ready, let directory else { return false }
        do {
            let session = EditorSession(settings: settings, recipe: recipe, photoName: sourceURL?.lastPathComponent, info: photoInfo, frameStyle: frameStyle)
            try JSONEncoder().encode(session).write(to: directory.appendingPathComponent("session.json"), options: .atomic)
            return true
        } catch { status = "Chưa lưu được phiên chỉnh sửa: \(error.localizedDescription)"; return false }
    }

    func openPhoto(_ item: PhotosPickerItem) async {
        guard !busy else { return }
        editRevision += 1
        isLoading = true; defer { isLoading = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { throw EditorFailure.message("Không đọc được ảnh đã chọn.") }
            try await acceptPhoto(data)
        } catch { show(error) }
    }

    func importFile(_ url: URL, kind: ImportKind) async {
        guard !busy else { return }
        editRevision += 1
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        isLoading = true; defer { isLoading = false }
        do {
            let limit = kind == .image ? 100 : kind == .lut ? 35 : 8
            let data = try await worker.read(url, limit: limit * 1_048_576)
            switch kind {
            case .image: try await acceptPhoto(data)
            case .lut:
                guard let store else { return }
                _ = try await store.importLUT(data: data, name: url.deletingPathExtension().lastPathComponent, film: settings.film)
                await reloadLibrary(); await updateBoundLUT()
            case .recipe:
                guard let store else { return }
                let imported = try await store.importRecipe(data: data)
                await reloadLibrary()
                switch imported {
                case .recipes(let items): if let first = items.first { await apply(first) }
                case .editing(let snapshot):
                    recordUndo(); settings = snapshot.settings; recipe = snapshot.recipe; notes = []; isAutoBalanced = false
                    await updateBoundLUT()
                }
            }
        } catch { show(error) }
    }

    private func acceptPhoto(_ data: Data) async throws {
        guard let directory else { throw EditorFailure.message("Thư viện chưa sẵn sàng.") }
        let url = try await worker.savePhoto(data, directory: directory)
        do {
            let loaded = try await worker.open(url)
            let previous = sourceURL
            sourceURL = url; preview = loaded.0; originalImage = loaded.1; photoInfo = loaded.2
            thumbnailBase = try? PhotoIO.resized(loaded.0, maxDimension: 192)
            thumbnails.removeAll(); thumbnailVersion += 1
            undoStack.removeAll()
            redoStack.removeAll()
            isAutoBalanced = false
            editedImage = nil; exportedURL = nil
            // Resolve the current film again after closing or replacing a photo.
            await updateBoundLUT()
            changed()
            if persist(), let previous, previous != url { try? FileManager.default.removeItem(at: previous) }
        } catch { try? FileManager.default.removeItem(at: url); throw error }
    }

    private var undoState: UndoState {
        UndoState(settings: settings, recipe: recipe, frameStyle: frameStyle,
                  photoInfo: photoInfo, notes: notes, isAutoBalanced: isAutoBalanced)
    }

    func recordUndo() {
        editRevision += 1
        let current = undoState
        if undoStack.last != current {
            undoStack.append(current)
            if undoStack.count > 40 { undoStack.removeFirst() }
        }
        redoStack.removeAll()
    }

    private func restore(_ state: UndoState, lut: CubeLut?) {
        lutRevision += 1
        settings = state.settings; recipe = state.recipe; frameStyle = state.frameStyle
        photoInfo = state.photoInfo; notes = state.notes; isAutoBalanced = state.isAutoBalanced
        activeLUT = lut
        changed(immediate: true)
    }

    func undo() async {
        guard !busy, let previous = undoStack.last else { return }
        editRevision += 1
        lutRevision += 1
        let revision = editRevision
        do {
            let lut = try await loadBoundLUT(for: previous.settings.film)
            guard revision == editRevision else { return }
            undoStack.removeLast(); redoStack.append(undoState)
            restore(previous, lut: lut)
        } catch { if revision == editRevision { show(error) } }
    }

    func redo() async {
        guard !busy, let next = redoStack.last else { return }
        editRevision += 1
        lutRevision += 1
        let revision = editRevision
        do {
            let lut = try await loadBoundLUT(for: next.settings.film)
            guard revision == editRevision else { return }
            redoStack.removeLast(); undoStack.append(undoState)
            restore(next, lut: lut)
        } catch { if revision == editRevision { show(error) } }
    }

    func autoBalance() async {
        guard !busy, let preview else { return }
        recordUndo()
        settings = AutoBalancer.analyze(preview, baseSettings: settings)
        isAutoBalanced = true
        changed(immediate: true)
    }

    func apply(_ chosen: Recipe) async {
        guard !isExporting else { return }
        editRevision += 1
        lutRevision += 1
        let revision = editRevision
        var newNotes: [String] = []
        do {
            let newSettings = try chosen.adjustments(notes: &newNotes).validated()
            let lut = try await loadBoundLUT(for: newSettings.film)
            // A later selection, slider gesture, import or close supersedes this request.
            guard revision == editRevision else { return }
            recordUndo()
            lutRevision += 1
            notes = newNotes; settings = newSettings; recipe = chosen; activeLUT = lut
            isAutoBalanced = false
            changed(immediate: true)
        } catch { if revision == editRevision { show(error) } }
    }

    func reset() async { await restoreOriginal(resetFrame: true) }

    func originalLook() async { await restoreOriginal(resetFrame: false) }

    private func restoreOriginal(resetFrame: Bool) async {
        guard !busy else { return }
        editRevision += 1
        lutRevision += 1
        let revision = editRevision
        do {
            let newSettings = Settings()
            let lut = try await loadBoundLUT(for: newSettings.film)
            guard revision == editRevision else { return }
            recordUndo()
            lutRevision += 1
            settings = newSettings; recipe = nil; notes = []; activeLUT = lut
            if resetFrame { frameStyle = 0 }
            isAutoBalanced = false
            changed(immediate: true)
        } catch { if revision == editRevision { show(error) } }
    }

    func applyFrame(style: Int, info: PhotoInfo) {
        guard !busy else { return }
        let style = min(PhotoFrame.styles.count - 1, max(0, style)), info = info.sanitized()
        guard style != frameStyle || info != photoInfo else { return }
        recordUndo()
        frameStyle = style
        photoInfo = info
        changed()
    }

    func clearPhoto() {
        guard !busy else { return }
        editRevision += 1
        lutRevision += 1
        renderTask?.cancel()
        renderRevision += 1
        undoStack.removeAll()
        redoStack.removeAll()
        isAutoBalanced = false
        let previous = sourceURL
        sourceURL = nil
        preview = nil
        originalImage = nil
        editedImage = nil
        exportedURL = nil
        exportedDimensions = ""
        activeLUT = nil
        thumbnailBase = nil
        thumbnails.removeAll()
        thumbnailVersion += 1
        photoInfo = PhotoInfo()
        frameStyle = 0
        settings = Settings()
        recipe = nil
        notes = []
        isRendering = false
        status = ""
        if persist(), let previous { try? FileManager.default.removeItem(at: previous) }
    }

    @discardableResult
    func assign(_ lut: LocalLUT?) async -> Bool {
        guard let store, !busy else { return false }
        do {
            if let lut { try await store.assignLUT(lut, to: settings.film) }
            else { try await store.removeLUTBinding(for: settings.film) }
            await reloadLibrary(); await updateBoundLUT()
            return true
        } catch { show(error); return false }
    }

    func recipeData() async throws -> Data {
        guard let store else { throw EditorFailure.message("Thư viện chưa sẵn sàng.") }
        return try await store.exportRecipe(settings: settings, recipe: recipe)
    }

    @discardableResult
    func saveRecipe(_ edited: Recipe) async -> Bool {
        guard let store, !busy else { return false }
        do {
            try await store.saveRecipe(edited); await reloadLibrary(); await apply(edited)
            return true
        } catch { show(error); return false }
    }

    func refreshOnline() async {
        guard let online, !onlineBusy else { return }
        onlineBusy = true; defer { onlineBusy = false }
        do {
            let result = try await online.refresh(repository: onlineRepository)
            onlineItems = result.items; onlineStatus = result.message
            await reloadLibrary()
        } catch { show(error); onlineStatus = error.localizedDescription }
    }

    func loadOnlineCache() async { onlineItems = await online?.cachedCatalog() ?? [] }

    func toggleAutomaticUpdates(_ enabled: Bool) async {
        guard let store else { return }
        do { try await store.setAutomaticUpdates(enabled); await reloadLibrary() } catch { show(error) }
    }

    func download(_ item: OnlineLUT) async {
        guard let online, !onlineBusy else { return }
        let film = settings.film
        onlineBusy = true; defer { onlineBusy = false }
        do {
            _ = try await online.download(item, assignTo: film)
            onlineStatus = "Đã tải \(item.name) và gắn cho \(film)."
            await reloadLibrary(); await updateBoundLUT()
        } catch { show(error) }
    }

    func export(size: ExportSize, format: ExportFormat) async {
        guard let source = sourceURL, !busy else { return }
        editRevision += 1
        let revision = editRevision, bindingRevision = lutRevision
        let needsPreview = isRendering
        isExporting = true; exportedURL = nil
        defer {
            isExporting = false
            if needsPreview { renderPreview(immediate: true) }
        }
        renderRevision += 1
        renderTask?.cancel(); isRendering = false
        let destination = FileManager.default.temporaryDirectory.appendingPathComponent("IRO-\(UUID().uuidString).\(format.fileExtension)")
        do {
            let result = try await worker.export(source: source, settings: settings, lut: activeLUT, size: size, format: format, info: photoInfo, style: frameStyle, destination: destination)
            guard revision == editRevision, bindingRevision == lutRevision, source == sourceURL else {
                throw EditorFailure.message("Thiết lập màu đã thay đổi trong lúc xuất. Hãy tạo lại ảnh xuất.")
            }
            exportedDimensions = "\(result.0) × \(result.1) px"
            exportedURL = destination; status = "Đã xuất \(exportedDimensions)"
        } catch { try? FileManager.default.removeItem(at: destination); show(error) }
    }

    func saveToPhotos() async -> Bool {
        guard let url = exportedURL else { return false }
        let authorization = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard authorization == .authorized || authorization == .limited else { errorMessage = "Cho phép IRO thêm ảnh trong Cài đặt → Quyền riêng tư → Ảnh, hoặc dùng Chia sẻ / Lưu vào Tệp."; return false }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: url)
            }
            status = "Đã lưu \(exportedDimensions) vào Ảnh."
            return true
        } catch { show(error); return false }
    }

    func recipeThumbnail(_ recipe: Recipe) async -> UIImage? {
        let key = "recipe-\(thumbnailVersion)-\(recipe.id)"
        if let cached = thumbnails[key] { return cached }
        guard let source = thumbnailBase ?? preview, let store else { return nil }
        do {
            let version = thumbnailVersion
            let lut = try await store.boundLUT(for: recipe.film)
            var notes: [String] = []
            // Match Android: recipes without an assigned LUT show the source with a missing-LUT label.
            let adjustment = lut == nil ? nil : recipe.adjustments(notes: &notes)
            let image = try await thumbnailWorker.thumbnail(source, settings: adjustment, lut: lut, info: PhotoInfo(), style: 0)
            try Task.checkCancellation()
            guard version == thumbnailVersion else { return nil }
            if thumbnails.count >= 256 {
                let keysToRemove = Array(thumbnails.keys.prefix(64))
                for k in keysToRemove { thumbnails.removeValue(forKey: k) }
            }
            thumbnails[key] = image; return image
        } catch { return nil }
    }

    func frameThumbnail(style: Int, info: PhotoInfo) async -> UIImage? {
        guard let source = thumbnailBase ?? preview else { return nil }
        return try? await thumbnailWorker.thumbnail(source, settings: settings, lut: activeLUT, info: info, style: style)
    }
}
