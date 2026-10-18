import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Dịch vụ đồng bộ và sao lưu ảnh đã xử lý lên máy chủ IRO Cloud (npccc.io.vn).
/// Hoạt động bất đồng bộ trong nền, gửi ảnh và thông số công thức màu (recipe)
/// mà không làm gián đoạn hoặc chặn luồng giao diện người dùng.
public final class CloudSyncService: Sendable {

    public static let shared = CloudSyncService()

    public static let defaultEndpoint = URL(string: "https://npccc.io.vn/api/mobile/sync-edit")!

    private static let syncEnabledKey = "iro.cloud.sync.enabled"

    /// Tùy chọn quyền riêng tư: Cho phép người dùng bật hoặc tắt tự động tải ảnh lên máy chủ
    public var isEnabled: Bool {
        get {
            UserDefaults.standard.object(forKey: Self.syncEnabledKey) as? Bool ?? true
        }
        set {
            UserDefaults.standard.set(newValue, forKey: Self.syncEnabledKey)
        }
    }

    public struct SyncResult: Codable, Sendable {
        public let ok: Bool
        public let sessionId: String?
        public let url: String?
        public let fullUrl: String?
        public let message: String?
    }

    private init() {}

    /// Tải tệp ảnh đã xuất từ URL cục bộ lên IRO Cloud.
    public func uploadExportedPhoto(
        fileURL: URL,
        filename: String? = nil,
        recipeName: String = "Classic Chrome",
        settings: Settings? = nil,
        width: Int = 0,
        height: Int = 0
    ) async -> Result<SyncResult, Error> {
        do {
            let data = try Data(contentsOf: fileURL, options: .mappedIfSafe)
            let safeName = filename ?? fileURL.lastPathComponent
            return await uploadExportedPhoto(
                photoData: data,
                filename: safeName,
                recipeName: recipeName,
                settings: settings,
                width: width,
                height: height
            )
        } catch {
            return .failure(error)
        }
    }

    /// Tải trực tiếp Data nhị phân của ảnh (JPEG/PNG) kèm siêu dữ liệu lên IRO Cloud.
    public func uploadExportedPhoto(
        photoData: Data,
        filename: String = "iro_ios_photo.jpg",
        recipeName: String = "Classic Chrome",
        settings: Settings? = nil,
        width: Int = 0,
        height: Int = 0
    ) async -> Result<SyncResult, Error> {
        let boundary = "===IRO_IOS_BOUNDARY_\(UUID().uuidString)==="
        var request = URLRequest(url: Self.defaultEndpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        #if canImport(UIKit)
        let deviceModel = UIDevice.current.name.isEmpty 
            ? "Apple Device" 
            : "\(UIDevice.current.model) (\(UIDevice.current.systemName) \(UIDevice.current.systemVersion))"
        #else
        let deviceModel = "Apple Device (iOS / macOS)"
        #endif

        request.setValue("IRO-iOS/\(deviceModel)", forHTTPHeaderField: "User-Agent")

        var body = Data()

        func appendField(name: String, value: String) {
            if let boundaryData = "--\(boundary)\r\n".data(using: .utf8),
               let dispositionData = "Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8),
               let valueData = "\(value)\r\n".data(using: .utf8) {
                body.append(boundaryData)
                body.append(dispositionData)
                body.append(valueData)
            }
        }

        appendField(name: "platform", value: "iOS")
        appendField(name: "deviceId", value: deviceModel)
        appendField(name: "filename", value: filename.isEmpty ? "photo.jpg" : filename)
        appendField(name: "recipeName", value: recipeName.isEmpty ? "Classic Chrome" : recipeName)
        appendField(name: "width", value: String(width))
        appendField(name: "height", value: String(height))

        var settingsJson = "{}"
        if let settings = settings,
           let enc = try? JSONEncoder().encode(settings),
           let str = String(data: enc, encoding: .utf8) {
            settingsJson = str
        }
        appendField(name: "settings", value: settingsJson)

        // Photo Binary
        let ext = (filename as NSString).pathExtension.lowercased()
        let mime = (ext == "png") ? "image/png" : "image/jpeg"
        if let partHeader = "--\(boundary)\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"\(filename)\"\r\nContent-Type: \(mime)\r\n\r\n".data(using: .utf8),
           let partFooter = "\r\n--\(boundary)--\r\n".data(using: .utf8) {
            body.append(partHeader)
            body.append(photoData)
            body.append(partFooter)
        }

        request.httpBody = body

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                let errText = String(data: data, encoding: .utf8) ?? "HTTP \(code)"
                return .failure(NSError(domain: "CloudSyncService", code: code, userInfo: [NSLocalizedDescriptionKey: errText]))
            }

            let result = try JSONDecoder().decode(SyncResult.self, from: data)
            return .success(result)
        } catch {
            return .failure(error)
        }
    }
}
