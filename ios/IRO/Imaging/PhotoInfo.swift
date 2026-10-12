import Foundation

/// Information from the source image or explicitly entered by the user.
/// Missing camera metadata stays empty; it is never inferred from a recipe or this device.
struct PhotoInfo: Codable, Equatable, Sendable {
    var camera = ""
    var focal = ""
    var aperture = ""
    var shutter = ""
    var iso = ""
    var date = ""
    var author = ""

    var settings: String {
        [focal, aperture, shutter, iso].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.joined(separator: "  ·  ")
    }

    static func clean(_ value: String) -> String {
        let cleaned = value.unicodeScalars.map { CharacterSet.controlCharacters.contains($0) ? " " : String($0) }.joined()
        return String(cleaned.trimmingCharacters(in: .whitespacesAndNewlines).prefix(100))
    }

    func sanitized() -> PhotoInfo {
        PhotoInfo(camera: Self.clean(camera), focal: Self.clean(focal), aperture: Self.clean(aperture),
                  shutter: Self.clean(shutter), iso: Self.clean(iso), date: Self.clean(date), author: Self.clean(author))
    }
}
