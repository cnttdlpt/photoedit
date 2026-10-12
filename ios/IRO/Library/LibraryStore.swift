import Foundation
import CryptoKit

enum LibraryError: LocalizedError {
    case invalidFile(String)
    case tooLarge
    case missingLUT

    var errorDescription: String? {
        switch self {
        case .invalidFile(let message): return message
        case .tooLarge: return "File vượt giới hạn dung lượng."
        case .missingLUT: return "Không tìm thấy LUT đã lưu. Hãy nhập lại file."
        }
    }
}

struct LocalLUT: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let name: String
    let fileName: String
    let source: OnlineLUT?
    let importedAt: Date
}

struct OnlinePreferences: Codable, Equatable, Sendable {
    var repository = OnlineLUTService.defaultRepository
    var automaticUpdates = false
    var lastChecked: Date?
    var lastAttempt: Date?
    var status = "Bấm Tìm / cập nhật để lấy danh sách."
}

struct LibrarySnapshot: Sendable {
    let recipes: [Recipe]
    let localLUTs: [LocalLUT]
    /// Film simulation name → the exact installed LUT version's ID.
    let filmBindings: [String: String]
    let onlinePreferences: OnlinePreferences
}

struct EditorSnapshot: Codable, Sendable {
    var settings: Settings
    var recipe: Recipe?
}

enum RecipeImport: Sendable {
    case recipes([Recipe])
    case editing(EditorSnapshot)
}

/// Serialized file access keeps imports, downloads, and UI actions from overwriting one another.
/// Application Support files remain available offline and are included in the app's normal backup.
actor LibraryStore {
    static let maximumLUTBytes = 35 * 1024 * 1024
    static let maximumJSONBytes = 8 * 1024 * 1024

    private struct Manifest: Codable {
        var version = 1
        var recipes: [Recipe] = []
        var localLUTs: [LocalLUT] = []
        var filmBindings: [String: String] = [:]
        var onlinePreferences = OnlinePreferences()
        var catalogs: [String: [OnlineLUT]] = [:]
        /// Source repository/path → newest installed content hash. Film bindings are independent.
        var installedVersions: [String: String] = [:]
    }

    private let directory: URL
    private let manifestURL: URL
    private var state: Manifest
    private var bundledRecipes: [Recipe] = []
    // IDs are content-addressed, so a parsed cube is immutable for a given cache key.
    // Up to sixteen parsed LUTs are reused across recipe thumbnails.
    private var parsedLUTs: [String: CubeLut] = [:]
    private var parsedLUTOrder: [String] = []

    init(directory: URL? = nil) throws {
        let root: URL
        if let directory {
            root = directory
        } else {
            root = try FileManager.default.url(for: .applicationSupportDirectory,
                                               in: .userDomainMask,
                                               appropriateFor: nil, create: true)
                .appendingPathComponent("IRO/Library", isDirectory: true)
        }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        self.directory = root
        self.manifestURL = root.appendingPathComponent("library.json")
        if FileManager.default.fileExists(atPath: manifestURL.path) {
            let data = try Self.readFile(manifestURL, maximum: 32 * 1024 * 1024)
            let decoded = try JSONDecoder().decode(Manifest.self, from: data)
            guard decoded.version == 1 else {
                throw LibraryError.invalidFile("Phiên bản thư viện chưa được hỗ trợ.")
            }
            self.state = decoded
        } else {
            self.state = Manifest()
        }
    }

    func snapshot() -> LibrarySnapshot {
        LibrarySnapshot(recipes: state.recipes + bundledRecipes,
                        localLUTs: state.localLUTs,
                        filmBindings: state.filmBindings,
                        onlinePreferences: state.onlinePreferences)
    }

    @discardableResult
    func loadBundledRecipes(bundle: Bundle = .main) throws -> [Recipe] {
        guard let libraryURL = bundle.url(forResource: "recipes", withExtension: "json"),
              let sampleURL = bundle.url(forResource: "sample-recipe", withExtension: "json") else {
            throw LibraryError.invalidFile("Không tìm thấy thư viện recipe trong ứng dụng.")
        }
        struct Catalog: Decodable { let recipes: [Recipe] }
        let decoder = JSONDecoder()
        let catalog = try decoder.decode(Catalog.self,
                                         from: Self.readFile(libraryURL, maximum: Self.maximumJSONBytes))
        let sample = try decoder.decode(Recipe.self,
                                       from: Self.readFile(sampleURL, maximum: 100_000))
        var seen = Set(state.recipes.map(\.id))
        bundledRecipes = ([sample] + catalog.recipes).filter { seen.insert($0.id).inserted }
        return state.recipes + bundledRecipes
    }

    /// Callers reading a document picker URL should use security-scoped access for that URL.
    /// This variant caps the read before allocating a file-sized buffer.
    func importLUT(from url: URL, film: String? = nil) throws -> LocalLUT {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let bytes = try Self.readFile(url, maximum: Self.maximumLUTBytes)
        return try importLUT(data: bytes, name: url.deletingPathExtension().lastPathComponent, film: film)
    }

    @discardableResult
    func importLUT(data: Data, name: String, film: String? = nil) throws -> LocalLUT {
        guard data.count <= Self.maximumLUTBytes else { throw LibraryError.tooLarge }
        let parsed = try CubeLut.parse(data: data)
        let hash = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        let id = "local-" + hash
        let lut = state.localLUTs.first(where: { $0.id == id }) ?? LocalLUT(
            id: id, name: String(name.prefix(300)), fileName: id + ".cube",
            source: nil, importedAt: Date())
        try install(lut, data: data, film: film)
        remember(parsed, for: lut.id)
        return lut
    }

    func lut(_ item: LocalLUT) throws -> CubeLut {
        guard let installed = state.localLUTs.first(where: { $0.id == item.id }) else {
            throw LibraryError.missingLUT
        }
        if let parsed = parsedLUTs[installed.id] {
            remember(parsed, for: installed.id)
            return parsed
        }
        let parsed = try CubeLut.parse(data: verifiedLUTData(installed))
        remember(parsed, for: installed.id)
        return parsed
    }

    /// Safe to call on a memory warning; the next request reloads from persistent storage.
    func clearParsedLUTCache() {
        parsedLUTs.removeAll()
        parsedLUTOrder.removeAll()
    }

    func boundLUT(for film: String) throws -> CubeLut? {
        guard let id = state.filmBindings[film] else { return nil }
        guard let item = state.localLUTs.first(where: { $0.id == id }) else {
            throw LibraryError.missingLUT
        }
        return try lut(item)
    }

    func assignLUT(_ lut: LocalLUT, to film: String) throws {
        guard let saved = state.localLUTs.first(where: { $0.id == lut.id }),
              FileManager.default.fileExists(atPath: try fileURL(saved).path) else {
            throw LibraryError.missingLUT
        }
        // Validate the saved bytes before changing the previous binding. A parsed cache
        // entry alone cannot prove the on-disk file still exists and is usable after restore.
        let parsed = try CubeLut.parse(data: verifiedLUTData(saved))
        var next = state
        next.filmBindings[film] = saved.id
        try commit(next)
        remember(parsed, for: saved.id)
    }

    func removeLUTBinding(for film: String) throws {
        var next = state
        next.filmBindings.removeValue(forKey: film)
        try commit(next)
    }

    /// Imports an Android/iOS edit package, one raw recipe, or {"recipes": [...]}.
    /// LUT data is deliberately separate, matching the Android JSON format.
    func importRecipe(data: Data) throws -> RecipeImport {
        guard data.count <= Self.maximumJSONBytes else { throw LibraryError.tooLarge }
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw LibraryError.invalidFile("Recipe phải là một đối tượng JSON.")
        }
        let decoder = JSONDecoder()
        if let app = object["app"] as? String, ["IRO_ANDROID", "IRO_IOS"].contains(app) {
            struct Package: Decodable {
                let version: Int?
                let settings: Settings
                let recipe: Recipe?
            }
            let package = try decoder.decode(Package.self, from: data)
            guard package.version == nil || package.version == 1 else {
                throw LibraryError.invalidFile("Phiên bản công thức chưa được hỗ trợ.")
            }
            let settings = try package.settings.validated()
            return .editing(EditorSnapshot(settings: settings, recipe: package.recipe))
        }
        let recipes: [Recipe]
        if object["recipes"] != nil {
            struct Catalog: Decodable { let recipes: [Recipe] }
            recipes = try decoder.decode(Catalog.self, from: data).recipes
        } else {
            recipes = [try decoder.decode(Recipe.self, from: data)]
        }
        guard !recipes.isEmpty, recipes.count <= 1_000 else {
            throw LibraryError.invalidFile("Mỗi file cần có từ 1 đến 1000 recipe.")
        }
        var seen = Set((state.recipes + bundledRecipes).map(\.id))
        let unique = recipes.map { value -> Recipe in
            var value = value
            if !seen.insert(value.id).inserted {
                value.id = "import-" + UUID().uuidString
                seen.insert(value.id)
            }
            return value
        }
        var next = state
        next.recipes.insert(contentsOf: unique, at: 0)
        try commit(next)
        return .recipes(unique)
    }

    func importRecipe(from url: URL) throws -> RecipeImport {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        return try importRecipe(data: Self.readFile(url, maximum: Self.maximumJSONBytes))
    }

    func saveRecipe(_ recipe: Recipe) throws {
        // Re-decoding uses the same validation as imported files.
        let checked = try JSONDecoder().decode(Recipe.self, from: JSONEncoder().encode(recipe))
        var next = state
        next.recipes.removeAll { $0.id == checked.id }
        next.recipes.insert(checked, at: 0)
        try commit(next)
        bundledRecipes.removeAll { $0.id == checked.id }
    }

    func exportRecipe(settings: Settings, recipe: Recipe?) throws -> Data {
        try recipe?.validate()
        struct Package: Encodable {
            // Android v1 only recognizes IRO_ANDROID. Keep this discriminator for round trips.
            let app = "IRO_ANDROID"
            let platform = "iOS"
            let version = 1
            let settings: Settings
            let recipe: Recipe?
            let lutNote = "LUT must be imported separately for this film simulation"
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(Package(settings: settings.validated(), recipe: recipe))
    }

    func setRepository(_ input: String) throws {
        let repository = try OnlineLUTService.normalizeRepository(input)
        guard repository != state.onlinePreferences.repository else { return }
        var next = state
        next.onlinePreferences.repository = repository
        next.onlinePreferences.lastChecked = nil
        next.onlinePreferences.lastAttempt = nil
        next.onlinePreferences.status = "Bấm Tìm / cập nhật để lấy danh sách."
        try commit(next)
    }

    func setAutomaticUpdates(_ enabled: Bool) throws {
        var next = state
        next.onlinePreferences.automaticUpdates = enabled
        try commit(next)
    }

    func cachedCatalog(repository: String) -> [OnlineLUT] { state.catalogs[repository] ?? [] }

    func installedVersion(for item: OnlineLUT) -> String? { state.installedVersions[item.sourceID] }

    func cachedLUT(for item: OnlineLUT) throws -> LocalLUT? {
        guard let lut = state.localLUTs.first(where: { $0.id == "online-" + item.sha }) else { return nil }
        let data = try Self.readFile(fileURL(lut), maximum: Self.maximumLUTBytes)
        guard OnlineLUTService.blobHash(data) == item.sha else { return nil }
        let parsed = try parsedLUTs[lut.id] ?? CubeLut.parse(data: data)
        remember(parsed, for: lut.id)
        return lut
    }

    func installOnline(_ item: OnlineLUT, data: Data, film: String?) throws -> LocalLUT {
        guard data.count <= Self.maximumLUTBytes else { throw LibraryError.tooLarge }
        try item.validate()
        guard OnlineLUTService.blobHash(data) == item.sha else {
            throw LibraryError.invalidFile("Checksum LUT không khớp nguồn.")
        }
        let parsed = try CubeLut.parse(data: data)
        let id = "online-" + item.sha
        let lut = LocalLUT(id: id, name: item.name, fileName: id + ".cube", source: item, importedAt: Date())
        try install(lut, data: data, film: film)
        remember(parsed, for: lut.id)
        return lut
    }

    func markInstalled(_ item: OnlineLUT) throws {
        var next = state
        next.installedVersions[item.sourceID] = item.sha
        try commit(next)
    }

    func recordAttempt(_ date: Date) throws {
        var next = state
        next.onlinePreferences.lastAttempt = date
        try commit(next)
    }

    func saveCatalog(_ items: [OnlineLUT], repository: String, at date: Date) throws {
        var next = state
        next.catalogs[repository] = items
        if next.onlinePreferences.repository == repository {
            next.onlinePreferences.lastChecked = date
        }
        try commit(next)
    }

    func setOnlineStatus(_ message: String, repository: String) throws {
        guard state.onlinePreferences.repository == repository else { return }
        var next = state
        next.onlinePreferences.status = message
        try commit(next)
    }

    private func install(_ lut: LocalLUT, data: Data, film: String?) throws {
        let target = try fileURL(lut)
        let fileExisted = FileManager.default.fileExists(atPath: target.path)
        try data.write(to: target, options: [.atomic])
        var next = state
        next.localLUTs.removeAll { $0.id == lut.id }
        next.localLUTs.insert(lut, at: 0)
        if let film { next.filmBindings[film] = lut.id }
        if let source = lut.source { next.installedVersions[source.sourceID] = source.sha }
        do {
            try commit(next)
        } catch {
            // A failed metadata commit must not leave a newly created, unreachable LUT
            // consuming storage. An existing version can still be referenced by old state.
            if !fileExisted { try? FileManager.default.removeItem(at: target) }
            throw error
        }
    }

    private func fileURL(_ item: LocalLUT) throws -> URL {
        guard item.fileName == item.id + ".cube",
              item.fileName.range(of: "^(local-[0-9a-f]{64}|online-[0-9a-f]{40})\\.cube$",
                                  options: .regularExpression) != nil else {
            throw LibraryError.invalidFile("Tên file LUT trong thư viện không hợp lệ.")
        }
        return directory.appendingPathComponent(item.fileName)
    }

    private func verifiedLUTData(_ item: LocalLUT) throws -> Data {
        let data = try Self.readFile(fileURL(item), maximum: Self.maximumLUTBytes)
        let expectedID: String
        if item.id.hasPrefix("online-") {
            expectedID = "online-" + OnlineLUTService.blobHash(data)
        } else {
            expectedID = "local-" + SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        }
        guard item.id == expectedID else {
            throw LibraryError.invalidFile("LUT đã lưu bị thay đổi hoặc bị hỏng. Hãy nhập hoặc tải lại file.")
        }
        return data
    }

    private func remember(_ parsed: CubeLut, for id: String) {
        parsedLUTs[id] = parsed
        parsedLUTOrder.removeAll { $0 == id }
        parsedLUTOrder.append(id)
        while parsedLUTOrder.count > 16 {
            parsedLUTs.removeValue(forKey: parsedLUTOrder.removeFirst())
        }
    }

    private func commit(_ next: Manifest) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(next)
        guard data.count <= 32 * 1024 * 1024 else { throw LibraryError.tooLarge }
        try data.write(to: manifestURL, options: [.atomic])
        state = next
    }

    private static func readFile(_ url: URL, maximum: Int) throws -> Data {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var result = Data()
        while let chunk = try handle.read(upToCount: min(65_536, maximum + 1 - result.count)), !chunk.isEmpty {
            result.append(chunk)
            guard result.count <= maximum else { throw LibraryError.tooLarge }
        }
        return result
    }
}
