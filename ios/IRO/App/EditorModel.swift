import SwiftUI
import PhotosUI
import Photos

private struct EditorSession: Codable {
    var settings: Settings
    var recipe: Recipe?
    var photoName: String?
    var info: PhotoInfo
    var frameStyle: Int
    var lightCraft: LightCraftState

    init(settings: Settings, recipe: Recipe?, photoName: String?, info: PhotoInfo, frameStyle: Int, lightCraft: LightCraftState = LightCraftState()) {
        self.settings = settings
        self.recipe = recipe
        self.photoName = photoName
        self.info = info
        self.frameStyle = frameStyle
        self.lightCraft = lightCraft
    }

    enum CodingKeys: String, CodingKey {
        case settings, recipe, photoName, info, frameStyle, lightCraft
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        settings = try c.decode(Settings.self, forKey: .settings)
        recipe = try c.decodeIfPresent(Recipe.self, forKey: .recipe)
        photoName = try c.decodeIfPresent(String.self, forKey: .photoName)
        info = try c.decode(PhotoInfo.self, forKey: .info)
        frameStyle = try c.decode(Int.self, forKey: .frameStyle)
        lightCraft = try c.decodeIfPresent(LightCraftState.self, forKey: .lightCraft) ?? LightCraftState()
    }
}

enum EditorFailure: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}

/// CPU and disk work are serialized away from the main actor; cancelled previews stop before the next job.
actor ImageWorker {
    func automaticSettings(_ source: RasterImage, proposed: Settings) throws -> Settings {
        try AutoBalanceGuard.protect(source, proposed: proposed)
    }

    func thumbnail(_ source: RasterImage, settings: Settings?, lut: CubeLut?, lightCraft: LightCraftState? = nil, info: PhotoInfo, style: Int) throws -> UIImage {
        try Task.checkCancellation()
        var small = (source.width <= 192 && source.height <= 192) ? source : try PhotoIO.resized(source, maxDimension: 192)
        if let settings { small = try ColorEngine.render(small, settings: settings, lut: lut) }
        try Task.checkCancellation()
        if let lightCraft { small = try LightCraftEngine.render(small, state: lightCraft, includeMaskOverlay: false) }
        try Task.checkCancellation()
        return try PhotoIO.uiImage(PhotoFrame.render(small, info: info, style: style))
    }

    func preview(_ source: RasterImage, settings: Settings, lut: CubeLut?, lightCraft: LightCraftState, info: PhotoInfo, style: Int) throws -> (UIImage, RgbHistogramData) {
        try Task.checkCancellation()
        var colored = try ColorEngine.render(source, settings: settings, lut: lut)
        try Task.checkCancellation()
        colored = try LightCraftEngine.render(colored, state: lightCraft, includeMaskOverlay: true)
        let hist = RgbHistogramData.analyze(colored)
        try Task.checkCancellation()
        return (try PhotoIO.uiImage(PhotoFrame.render(colored, info: info, style: style)), hist)
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

    func export(source: URL, settings: Settings, lut: CubeLut?, lightCraft: LightCraftState, size: ExportSize, format: ExportFormat, info: PhotoInfo, style: Int, destination: URL) throws -> (Int, Int) {
        let dimensions = try PhotoIO.export(sourceURL: source, settings: settings, lut: lut, lightCraft: lightCraft, size: size, format: format, info: info, frameStyle: style, destinationURL: destination)
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
    let lightCraft: LightCraftState
    let aiAutoInsight: GeminiAutoResult?

    init(settings: Settings, recipe: Recipe?, frameStyle: Int, photoInfo: PhotoInfo, notes: [String], isAutoBalanced: Bool, lightCraft: LightCraftState = LightCraftState(), aiAutoInsight: GeminiAutoResult? = nil) {
        self.settings = settings
        self.recipe = recipe
        self.frameStyle = frameStyle
        self.photoInfo = photoInfo
        self.notes = notes
        self.isAutoBalanced = isAutoBalanced
        self.lightCraft = lightCraft
        self.aiAutoInsight = aiAutoInsight
    }
}

@MainActor
final class EditorModel: ObservableObject {
    @Published var settings = Settings()
    @Published var lightCraft = LightCraftState()
    @Published var histogram = RgbHistogramData()
    @Published var previewSize = CGSize(width: 4, height: 3)
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
    @Published var isAutoAnalyzing = false
    @Published var aiAutoInsight: GeminiAutoResult?

    var canUndo: Bool { !undoStack.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }

    private(set) var activeLUT: CubeLut?
    private var sourceURL: URL?
    private var preview: RasterImage?
    private var interactivePreview: RasterImage?
    private var thumbnailBase: RasterImage?
    private var store: LibraryStore?
    private var online: OnlineLUTService?
    private let worker = ImageWorker()
    private let thumbnailWorker = ImageWorker()
    private var thumbnails: [String: UIImage] = [:]
    private var renderTask: Task<Void, Never>?
    private var needsImmediateFullRender = false
    private var renderRevision = 0
    private var editRevision = 0
    private var lutRevision = 0
    private var started = false
    private var directory: URL?
    private var lastStoredRepository: String?
    private let applicationDirectory: URL?
    private let boundLUTLoader: (@MainActor (String) async throws -> CubeLut?)?
    private let autoAnalyzer: (@MainActor (Data, RgbHistogramData, Settings) async throws -> GeminiAutoResult)?
    private var autoAnalysisID: UUID?

    init(applicationDirectory: URL? = nil, boundLUTLoader: (@MainActor (String) async throws -> CubeLut?)? = nil,
         autoAnalyzer: (@MainActor (Data, RgbHistogramData, Settings) async throws -> GeminiAutoResult)? = nil) {
        self.applicationDirectory = applicationDirectory
        self.boundLUTLoader = boundLUTLoader
        self.autoAnalyzer = autoAnalyzer
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
                    lightCraft = session.lightCraft
                    if let name = session.photoName, name == URL(fileURLWithPath: name).lastPathComponent {
                        let url = dir.appendingPathComponent(name)
                        let loaded = try await worker.open(url)
                        sourceURL = url; preview = loaded.0; originalImage = loaded.1
                        previewSize = CGSize(width: loaded.0.width, height: loaded.0.height)
                        histogram = RgbHistogramData.analyze(loaded.0)
                        interactivePreview = try? PhotoIO.resized(loaded.0, maxDimension: 640)
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
            activeLUT = lut; changed(immediate: true, invalidateLookRequest: false)
        } catch {
            guard settings.film == film, revision == lutRevision else { return }
            activeLUT = nil; show(error); changed(immediate: true, invalidateLookRequest: false)
        }
    }

    func changed(immediate: Bool = false, invalidateLookRequest: Bool = true) {
        exportedURL = nil; exportedDimensions = ""
        if invalidateLookRequest { editRevision += 1 }
        if immediate {
            persist()
        }
        renderPreview(immediate: immediate)
    }

    private func renderPreview(immediate: Bool) {
        renderRevision += 1
        if immediate {
            needsImmediateFullRender = true
        }
        guard preview != nil else {
            renderTask?.cancel()
            renderTask = nil
            isRendering = false
            return
        }
        isRendering = true
        if renderTask == nil {
            renderTask = Task { [weak self] in
                await self?.runRenderLoop()
            }
        }
    }

    private func runRenderLoop() async {
        defer {
            renderTask = nil
            isRendering = false
        }
        while !Task.isCancelled {
            guard let preview else { return }
            let revision = renderRevision
            let immediateFull = needsImmediateFullRender
            needsImmediateFullRender = false
            let settings = self.settings
            let lut = self.activeLUT
            let lc = self.lightCraft
            let info = self.photoInfo
            let style = self.frameStyle
            let useFast = !immediateFull && (interactivePreview != nil)
            let sourceRaster = (useFast ? interactivePreview : nil) ?? preview

            do {
                let (image, hist) = try await worker.preview(
                    sourceRaster,
                    settings: settings,
                    lut: lut,
                    lightCraft: lc,
                    info: info,
                    style: style
                )
                if Task.isCancelled { return }
                editedImage = image
                histogram = hist
                status = "\(preview.width) × \(preview.height) xem trước · \(style == 0 ? "Không khung" : PhotoFrame.styles[style])"

                if renderRevision != revision {
                    continue
                }

                if useFast && (sourceRaster.width != preview.width || sourceRaster.height != preview.height) {
                    try? await Task.sleep(for: .milliseconds(35))
                    if Task.isCancelled { return }
                    if renderRevision != revision {
                        continue
                    }
                    let (fullImage, fullHist) = try await worker.preview(
                        preview,
                        settings: settings,
                        lut: lut,
                        lightCraft: lc,
                        info: info,
                        style: style
                    )
                    if Task.isCancelled { return }
                    if renderRevision == revision {
                        editedImage = fullImage
                        histogram = fullHist
                    } else {
                        continue
                    }
                }

                persist()
                if renderRevision == revision {
                    break
                }
            } catch {
                if !(error is CancellationError) { show(error) }
                break
            }
        }
    }

    @discardableResult
    private func persist() -> Bool {
        guard ready, let directory else { return false }
        do {
            let session = EditorSession(settings: settings, recipe: recipe, photoName: sourceURL?.lastPathComponent, info: photoInfo, frameStyle: frameStyle, lightCraft: lightCraft)
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

    func openStudioSample(filmKeyword: String? = nil, defaultFrameStyle: Int = 0) async {
        guard !busy else { return }
        editRevision += 1
        isLoading = true; defer { isLoading = false }
        do {
            let (data, sampleInfo) = try PhotoIO.generateStudioSample()
            try await acceptPhoto(data, overrideInfo: sampleInfo)
            if defaultFrameStyle > 0 {
                frameStyle = min(PhotoFrame.styles.count - 1, defaultFrameStyle)
                changed(immediate: true)
            }
            if let keyword = filmKeyword, !keyword.isEmpty {
                let q = keyword.lowercased()
                let firstToken = q.components(separatedBy: " ").first ?? q
                if let match = recipes.first(where: { $0.name.lowercased().contains(q) || $0.film.lowercased().contains(q) })
                    ?? recipes.first(where: { $0.name.lowercased().contains(firstToken) || $0.film.lowercased().contains(firstToken) }) {
                    await apply(match)
                }
            }
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

    private func acceptPhoto(_ data: Data, overrideInfo: PhotoInfo? = nil) async throws {
        guard let directory else { throw EditorFailure.message("Thư viện chưa sẵn sàng.") }
        let url = try await worker.savePhoto(data, directory: directory)
        do {
            let loaded = try await worker.open(url)
            let previous = sourceURL
            sourceURL = url; preview = loaded.0; originalImage = loaded.1
            photoInfo = overrideInfo ?? loaded.2
            previewSize = CGSize(width: loaded.0.width, height: loaded.0.height)
            histogram = RgbHistogramData.analyze(loaded.0)
            interactivePreview = try? PhotoIO.resized(loaded.0, maxDimension: 640)
            thumbnailBase = try? PhotoIO.resized(loaded.0, maxDimension: 192)
            thumbnails.removeAll(); thumbnailVersion += 1
            undoStack.removeAll()
            redoStack.removeAll()
            isAutoBalanced = false
            autoAnalysisID = nil
            isAutoAnalyzing = false
            aiAutoInsight = nil
            editedImage = nil; exportedURL = nil
            // Resolve the current film again after closing or replacing a photo.
            await updateBoundLUT()
            changed(immediate: true)
            if persist(), let previous, previous != url { try? FileManager.default.removeItem(at: previous) }
        } catch { try? FileManager.default.removeItem(at: url); throw error }
    }

    private var undoState: UndoState {
        UndoState(settings: settings, recipe: recipe, frameStyle: frameStyle,
                  photoInfo: photoInfo, notes: notes, isAutoBalanced: isAutoBalanced,
                  lightCraft: lightCraft, aiAutoInsight: aiAutoInsight)
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
        lightCraft = state.lightCraft
        aiAutoInsight = state.aiAutoInsight
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
        guard !busy, !isAutoAnalyzing, let preview else { return }
        editRevision += 1
        let revision = editRevision
        if autoAnalyzer != nil || GeminiAutoService.hasAPIKey {
            let requestID = UUID()
            autoAnalysisID = requestID
            isAutoAnalyzing = true
            let previousStatus = status
            status = "✨ Gemini AI đang phân tích ngữ cảnh & ánh sáng…"
            defer {
                // Closing/opening a photo can start another analysis before this one returns.
                if autoAnalysisID == requestID {
                    autoAnalysisID = nil
                    isAutoAnalyzing = false
                }
            }
            do {
                let small = (try? PhotoIO.resized(preview, maxDimension: 384)) ?? preview
                let uiImg = try PhotoIO.uiImage(small)
                guard let jpegData = uiImg.jpegData(compressionQuality: 0.78) else {
                    throw GeminiAutoError.invalidResponse("Không mã hóa được ảnh thu nhỏ.")
                }
                let baseline = AutoBalancer.analyze(preview)
                let hist = RgbHistogramData.analyze(small)
                let result: GeminiAutoResult
                if let autoAnalyzer {
                    result = try await autoAnalyzer(jpegData, hist, baseline)
                } else {
                    result = try await GeminiAutoService.analyze(
                        jpegData: jpegData,
                        histogram: hist,
                        fallbackBaseline: baseline
                    )
                }
                guard revision == editRevision, autoAnalysisID == requestID else { return }
                try Task.checkCancellation()
                _ = try await applyAutomaticBalance(source: preview, revision: revision,
                                      settings: result.appliedSettings(base: Settings(), baseline: baseline),
                                      lightCraft: result.appliedLightCraft(base: LightCraftState()), insight: result)
                return
            } catch {
                // A newer edit, import, close or export supersedes both AI and local fallback.
                guard revision == editRevision, autoAnalysisID == requestID else { return }
                if error is CancellationError || (error as? URLError)?.code == .cancelled || Task.isCancelled {
                    status = previousStatus
                    return
                }
                // Offline or API error: fall back gracefully to local AutoBalancer
                status = previousStatus
                do {
                    if try await applyAutomaticBalance(source: preview, revision: revision, settings: AutoBalancer.analyze(preview)) {
                        show(EditorFailure.message("Đã dùng Auto nội bộ (Gemini AI: \(error.localizedDescription))"))
                    }
                } catch {
                    if revision == editRevision && !Task.isCancelled { show(error) }
                }
                return
            }
        }

        do {
            _ = try await applyAutomaticBalance(source: preview, revision: revision, settings: AutoBalancer.analyze(preview))
        } catch {
            if revision == editRevision && !Task.isCancelled { show(error) }
        }
    }

    private func applyAutomaticBalance(source: RasterImage, revision: Int, settings newSettings: Settings, lightCraft newLightCraft: LightCraftState = LightCraftState(), insight: GeminiAutoResult? = nil) async throws -> Bool {
        let protected = try await worker.automaticSettings(source, proposed: newSettings)
        guard revision == editRevision else { return false }
        try Task.checkCancellation()
        recordUndo()
        lutRevision += 1
        settings = protected
        // Auto analyzes the original pixels, so prior LUTs, recipes and masks must not stack on it.
        // Store the bypass as well so an Original LUT cannot reappear after restoring the session.
        settings.lutAmount = 0
        activeLUT = nil
        recipe = nil
        notes = []
        lightCraft = newLightCraft
        aiAutoInsight = insight
        if protected != newSettings {
            aiAutoInsight?.summary = "Cân bằng màu tự nhiên, giữ độ sáng chủ thể và hạn chế cháy vùng sáng."
        }
        isAutoBalanced = true
        changed(immediate: true)
        return true
    }

    /// Xoay ảnh 90 độ theo chiều kim đồng hồ (cập nhật canvas và tệp gốc).
    func rotateClockwise() async {
        guard !busy, let currentPreview = preview else { return }
        recordUndo()
        editRevision += 1
        let revision = editRevision
        isLoading = true
        defer { isLoading = false }
        do {
            let rotated = try PhotoIO.rotated90(currentPreview, clockwise: true)
            guard revision == editRevision else { return }
            preview = rotated
            previewSize = CGSize(width: rotated.width, height: rotated.height)
            originalImage = try PhotoIO.uiImage(rotated)
            interactivePreview = try? PhotoIO.resized(rotated, maxDimension: 640)
            thumbnailBase = try? PhotoIO.resized(rotated, maxDimension: 192)
            histogram = RgbHistogramData.analyze(rotated)
            
            if let sourceURL {
                try? PhotoIO.rotateFile(url: sourceURL, clockwise: true)
            }
            changed(immediate: true)
        } catch {
            show(error)
        }
    }

    /// Xoay ảnh 90 độ ngược chiều kim đồng hồ (cập nhật canvas và tệp gốc).
    func rotateCounterClockwise() async {
        guard !busy, let currentPreview = preview else { return }
        recordUndo()
        editRevision += 1
        let revision = editRevision
        isLoading = true
        defer { isLoading = false }
        do {
            let rotated = try PhotoIO.rotated90(currentPreview, clockwise: false)
            guard revision == editRevision else { return }
            preview = rotated
            previewSize = CGSize(width: rotated.width, height: rotated.height)
            originalImage = try PhotoIO.uiImage(rotated)
            interactivePreview = try? PhotoIO.resized(rotated, maxDimension: 640)
            thumbnailBase = try? PhotoIO.resized(rotated, maxDimension: 192)
            histogram = RgbHistogramData.analyze(rotated)
            
            if let sourceURL {
                try? PhotoIO.rotateFile(url: sourceURL, clockwise: false)
            }
            changed(immediate: true)
        } catch {
            show(error)
        }
    }

    func applyRecommendedFilm(_ keyword: String) async {
        guard !busy else { return }
        func normalized(_ value: String) -> String {
            let words = value.lowercased().split(whereSeparator: { !$0.isLetter && !$0.isNumber })
            let joined = words.joined(separator: " ")
            switch joined {
            case "classic neg": return "classic negative"
            case "nostalgic negative": return "nostalgic neg"
            default: return joined
            }
        }
        let q = normalized(keyword)
        guard !q.isEmpty else { return }
        let candidates = Array(filmBindings.keys).sorted() + recipes.map(\.film)
        let film = candidates.first(where: { normalized($0) == q }) ?? keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        editRevision += 1
        lutRevision += 1
        let revision = editRevision
        do {
            let lut = try await loadBoundLUT(for: film)
            guard revision == editRevision else { return }
            guard let lut else {
                throw EditorFailure.message("Film \(film) chưa được gắn LUT. Hãy gắn LUT của film này trong Thư viện LUT.")
            }
            recordUndo()
            lutRevision += 1
            settings.film = film
            settings.lutAmount = 100
            activeLUT = lut
            recipe = nil
            notes = []
            isAutoBalanced = false
            changed(immediate: true)
        } catch { if revision == editRevision { show(error) } }
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
            lightCraft.resetAll()
            if resetFrame { frameStyle = 0 }
            isAutoBalanced = false
            aiAutoInsight = nil
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
        changed(immediate: true)
    }

    func clearPhoto() {
        guard !busy else { return }
        editRevision += 1
        lutRevision += 1
        renderTask?.cancel()
        renderTask = nil
        renderRevision += 1
        undoStack.removeAll()
        redoStack.removeAll()
        isAutoBalanced = false
        autoAnalysisID = nil
        isAutoAnalyzing = false
        aiAutoInsight = nil
        let previous = sourceURL
        sourceURL = nil
        preview = nil
        interactivePreview = nil
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
        lightCraft = LightCraftState()
        histogram = RgbHistogramData()
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
        renderTask?.cancel(); renderTask = nil; isRendering = false
        let destination = FileManager.default.temporaryDirectory.appendingPathComponent("IRO-\(UUID().uuidString).\(format.fileExtension)")
        do {
            let result = try await worker.export(source: source, settings: settings, lut: activeLUT, lightCraft: lightCraft, size: size, format: format, info: photoInfo, style: frameStyle, destination: destination)
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
        return try? await thumbnailWorker.thumbnail(source, settings: settings, lut: activeLUT, lightCraft: lightCraft, info: info, style: style)
    }
}
