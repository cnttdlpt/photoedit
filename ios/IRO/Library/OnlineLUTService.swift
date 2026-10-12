import Foundation
import CryptoKit

struct OnlineLUT: Codable, Identifiable, Equatable, Sendable {
    let repository: String
    let path: String
    let name: String
    let sha: String
    let revision: String
    let byteCount: Int
    let license: String
    let input: String

    var id: String { sourceID + "@" + sha }
    var sourceID: String { repository + "/" + path }
    var sourceURL: URL? { URL(string: "https://github.com/" + repository) }

    func validate() throws {
        _ = try OnlineLUTService.normalizeRepository(repository)
        guard OnlineLUTService.isGitHash(sha), OnlineLUTService.isGitHash(revision),
              byteCount >= 0, byteCount <= LibraryStore.maximumLUTBytes,
              path.lowercased().hasSuffix(".cube"), !path.hasPrefix("/"),
              !path.contains("\\"), !path.contains("\0"),
              !path.split(separator: "/", omittingEmptySubsequences: false)
                .contains(where: { $0.isEmpty || $0 == "." || $0 == ".." }) else {
            throw OnlineLUTError.invalidSource
        }
    }
}

struct OnlineRefreshResult: Sendable {
    let items: [OnlineLUT]
    let updatedCount: Int
    let failedCount: Int

    var message: String {
        "Tìm thấy \(items.count) LUT"
            + (updatedCount > 0 ? " · đã tải \(updatedCount) bản mới" : "")
            + (failedCount > 0 ? " · \(failedCount) bản tải lỗi, giữ bản cũ" : "")
    }
}

enum OnlineLUTError: LocalizedError {
    case invalidRepository
    case invalidSource
    case rateLimited
    case http(Int)
    case tooManyItems
    case truncatedTree
    case busy
    case slowSource

    var errorDescription: String? {
        switch self {
        case .invalidRepository: return "Nhập owner/repository hoặc URL kho GitHub công khai."
        case .invalidSource: return "Nguồn tải hoặc phiên bản LUT không hợp lệ."
        case .rateLimited: return "GitHub giới hạn lượt tải. Hãy thử lại sau."
        case .http(let code): return "Nguồn trả HTTP \(code)."
        case .tooManyItems: return "Kho có hơn 1000 LUT; hãy dùng kho nhỏ hơn."
        case .truncatedTree: return "Kho quá lớn để liệt kê đầy đủ; hãy dùng kho nhỏ hơn."
        case .busy: return "Đang kiểm tra nguồn LUT."
        case .slowSource: return "Nguồn tải quá chậm. Hãy thử lại."
        }
    }
}

/// Only public GitHub metadata and .cube files are downloaded. No photo data enters this service.
/// Downloads use immutable commit URLs and the Git blob SHA-1 announced by that commit's tree.
actor OnlineLUTService {
    static let defaultRepository = "t0saki/lumix-original-looks"
    private let store: LibraryStore
    private var refreshing = false

    init(store: LibraryStore) { self.store = store }

    static func normalizeRepository(_ input: String) throws -> String {
        var value = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let prefix = "https://github.com/"
        if value.hasPrefix(prefix) { value.removeFirst(prefix.count) }
        while value.hasSuffix("/") { value.removeLast() }
        guard value.range(of: "^[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_.-]{1,100}$",
                          options: .regularExpression) != nil,
              !value.hasSuffix("/.."), !value.hasSuffix("/.") else {
            throw OnlineLUTError.invalidRepository
        }
        return value
    }

    static func blobHash(_ data: Data) -> String {
        var digest = Insecure.SHA1()
        digest.update(data: Data("blob \(data.count)\0".utf8))
        digest.update(data: data)
        return digest.finalize().map { String(format: "%02x", $0) }.joined()
    }

    static func isGitHash(_ value: String) -> Bool {
        value.range(of: "^[0-9a-f]{40}$", options: .regularExpression) != nil
    }

    func cachedCatalog(query: String = "") async -> [OnlineLUT] {
        let repository = await store.snapshot().onlinePreferences.repository
        let catalog = await store.cachedCatalog(repository: repository)
        let search = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return search.isEmpty ? catalog : catalog.filter {
            $0.name.localizedCaseInsensitiveContains(search) || $0.path.localizedCaseInsensitiveContains(search)
        }
    }

    func state(for item: OnlineLUT) async -> String {
        guard let saved = await store.installedVersion(for: item) else { return "Chưa tải" }
        return saved == item.sha ? "Đã tải · dùng offline" : "Có bản cập nhật"
    }

    /// Auto checks run only on app opening, after opt-in; never on a scheduled background task.
    /// A failed check may retry after one hour; a successful check waits one day.
    func checkOnOpen() async -> OnlineRefreshResult? {
        let preferences = await store.snapshot().onlinePreferences
        let now = Date()
        guard preferences.automaticUpdates, !refreshing,
              preferences.lastChecked.map({ now.timeIntervalSince($0) >= 86_400 }) ?? true,
              preferences.lastAttempt.map({ now.timeIntervalSince($0) >= 3_600 }) ?? true else {
            return nil
        }
        do {
            return try await refresh()
        } catch {
            return nil // refresh persists a user-readable status; offline files remain available.
        }
    }

    @discardableResult
    func refresh(repository requested: String? = nil) async throws -> OnlineRefreshResult {
        guard !refreshing else { throw OnlineLUTError.busy }
        refreshing = true
        defer { refreshing = false }
        if let requested { try await store.setRepository(requested) }
        let preferences = await store.snapshot().onlinePreferences
        let repository = preferences.repository
        do {
            try await store.recordAttempt(Date())
            let items = try await discover(repository)
            try await store.saveCatalog(items, repository: repository, at: Date())
            var updated = 0
            var failed = 0
            if preferences.automaticUpdates {
                for item in items {
                    try Task.checkCancellation()
                    guard updated + failed < 20 else { break }
                    let active = await store.snapshot().onlinePreferences
                    guard active.automaticUpdates, active.repository == repository else { break }
                    // Only sources already installed are eligible for automatic downloads.
                    guard let old = await store.installedVersion(for: item), old != item.sha else { continue }
                    do {
                        _ = try await download(item)
                        updated += 1
                    } catch is CancellationError {
                        throw CancellationError()
                    } catch {
                        failed += 1
                    }
                }
            }
            let result = OnlineRefreshResult(items: items, updatedCount: updated, failedCount: failed)
            try await store.setOnlineStatus(result.message, repository: repository)
            return result
        } catch {
            try? await store.setOnlineStatus("Chưa cập nhật được: \(error.localizedDescription) Vẫn dùng được LUT đã tải.",
                                             repository: repository)
            throw error
        }
    }

    @discardableResult
    func download(_ item: OnlineLUT, assignTo film: String? = nil) async throws -> LocalLUT {
        try item.validate()
        try Task.checkCancellation()
        // A corrupt or missing cached file can be repaired with a verified fresh download.
        let cached: LocalLUT?
        do {
            cached = try await store.cachedLUT(for: item)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            cached = nil
        }
        try Task.checkCancellation()
        if let cached {
            try await store.markInstalled(item)
            if let film { try await store.assignLUT(cached, to: film) }
            return cached
        }
        let repository = try Self.normalizeRepository(item.repository)
        let url = try makeURL(host: "raw.githubusercontent.com",
                              path: repository.split(separator: "/").map(String.init)
                                + [item.revision] + item.path.split(separator: "/").map(String.init))
        let data = try await fetch(url, maximum: LibraryStore.maximumLUTBytes)
        try Task.checkCancellation()
        return try await store.installOnline(item, data: data, film: film)
    }

    private struct RepositoryMetadata: Decodable {
        let default_branch: String
        let license: License?
        struct License: Decodable { let spdx_id: String? }
    }
    private struct Commit: Decodable { let sha: String }
    private struct Tree: Decodable {
        let truncated: Bool?
        let tree: [Node]
        struct Node: Decodable {
            let path: String
            let type: String
            let sha: String
            let size: Int?
        }
    }

    private func discover(_ input: String) async throws -> [OnlineLUT] {
        let repository = try Self.normalizeRepository(input)
        let base = ["repos"] + repository.split(separator: "/").map(String.init)
        let metadata: RepositoryMetadata = try await json(makeURL(host: "api.github.com", path: base))
        let commit: Commit = try await json(makeURL(host: "api.github.com", path: base + ["commits", metadata.default_branch]))
        guard Self.isGitHash(commit.sha) else { throw OnlineLUTError.invalidSource }
        let tree: Tree = try await json(makeURL(host: "api.github.com", path: base + ["git", "trees", commit.sha],
                                                query: [URLQueryItem(name: "recursive", value: "1")]))
        guard tree.truncated != true else { throw OnlineLUTError.truncatedTree }
        var items: [OnlineLUT] = []
        for node in tree.tree {
            try Task.checkCancellation()
            guard node.type == "blob", node.path.lowercased().hasSuffix(".cube"),
                  let size = node.size, size >= 0, size <= LibraryStore.maximumLUTBytes,
                  Self.isGitHash(node.sha) else { continue }
            guard items.count < 1_000 else { throw OnlineLUTError.tooManyItems }
            let fileName = node.path.split(separator: "/").last.map(String.init) ?? node.path
            let item = OnlineLUT(repository: repository, path: node.path,
                                 name: String(fileName.dropLast(5)), sha: node.sha, revision: commit.sha,
                                 byteCount: size, license: metadata.license?.spdx_id ?? "Chưa xác định",
                                 input: repository.lowercased() == Self.defaultRepository
                                    ? "sRGB / LUMIX Standard (theo tác giả)"
                                    : "Chưa xác định — xem hướng dẫn nguồn")
            try item.validate()
            items.append(item)
        }
        return items.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    private func json<T: Decodable>(_ url: URL) async throws -> T {
        let data = try await fetch(url, maximum: LibraryStore.maximumJSONBytes)
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func makeURL(host: String, path: [String], query: [URLQueryItem] = []) throws -> URL {
        let safe = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~")
        var components = URLComponents()
        components.scheme = "https"
        components.host = host
        components.percentEncodedPath = "/" + path.map { $0.addingPercentEncoding(withAllowedCharacters: safe) ?? "" }.joined(separator: "/")
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw OnlineLUTError.invalidSource }
        return url
    }

    private func fetch(_ url: URL, maximum: Int) async throws -> Data {
        guard url.scheme == "https", let host = url.host,
              ["api.github.com", "raw.githubusercontent.com"].contains(host) else {
            throw OnlineLUTError.invalidSource
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 60
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url)
        request.setValue("IRO-iOS-LUTs", forHTTPHeaderField: "User-Agent")
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        let (bytes, response) = try await session.bytes(for: request, delegate: NoRedirectDelegate())
        guard let response = response as? HTTPURLResponse else { throw OnlineLUTError.invalidSource }
        if response.statusCode == 403 || response.statusCode == 429 { throw OnlineLUTError.rateLimited }
        guard response.statusCode == 200 else { throw OnlineLUTError.http(response.statusCode) }
        guard response.expectedContentLength <= Int64(maximum) else { throw LibraryError.tooLarge }
        var data = Data()
        if response.expectedContentLength > 0 { data.reserveCapacity(Int(response.expectedContentLength)) }
        let deadline = Date().addingTimeInterval(60)
        for try await byte in bytes {
            guard data.count < maximum else { throw LibraryError.tooLarge }
            data.append(byte)
            if data.count % 8_192 == 0 {
                try Task.checkCancellation()
                guard Date() < deadline else { throw OnlineLUTError.slowSource }
            }
        }
        return data
    }
}

private final class NoRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
