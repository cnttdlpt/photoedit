import Foundation

/// Original source values are retained, including ranges and capture-only settings.
public struct Recipe: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var film: String
    public var sourceUrl: String
    public var sensors: [String]
    public var rawSettings: [String: String]
    public var reviewRequired: Bool
    public var missingFields: [String]

    public init(id: String = "custom", name: String = "Recipe tùy chỉnh", film: String = "Original",
                sourceUrl: String = "", sensors: [String] = [], rawSettings: [String: String] = [:],
                reviewRequired: Bool = false, missingFields: [String] = []) {
        self.id = id; self.name = name; self.film = film; self.sourceUrl = sourceUrl
        self.sensors = sensors; self.rawSettings = rawSettings
        self.reviewRequired = reviewRequired; self.missingFields = missingFields
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, film, sourceUrl, sensors, rawSettings, reviewRequired, missingFields
    }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        self.init(id: try values.decodeIfPresent(String.self, forKey: .id) ?? "custom",
                  name: try values.decodeIfPresent(String.self, forKey: .name) ?? "Recipe tùy chỉnh",
                  film: try values.decodeIfPresent(String.self, forKey: .film) ?? "Original",
                  sourceUrl: try values.decodeIfPresent(String.self, forKey: .sourceUrl) ?? "",
                  sensors: try values.decodeIfPresent([String].self, forKey: .sensors) ?? [],
                  rawSettings: try values.decode([String: String].self, forKey: .rawSettings),
                  reviewRequired: try values.decodeIfPresent(Bool.self, forKey: .reviewRequired) ?? false,
                  missingFields: try values.decodeIfPresent([String].self, forKey: .missingFields) ?? [])
        try validate()
    }

    public func validate() throws {
        guard rawSettings.count <= 60 else { throw CoreError.invalidValue("Recipe có quá nhiều trường") }
        guard film.utf16.count <= 300 else { throw CoreError.invalidValue("Giá trị Film quá dài") }
        for (key, value) in rawSettings where key.utf16.count > 80 || value.utf16.count > 500 {
            throw CoreError.invalidValue("Thông số quá dài")
        }
        // Source fields can become editor strings (Film Simulation and White Balance).
        // Reject an import now if it would fail rendering when the recipe is applied.
        var notes: [String] = []
        _ = try adjustments(notes: &notes).validated()
    }

    public var sourceDescription: String {
        "\(name)\n\(sensors.joined(separator: " / "))\n\nFilm Simulation: \(film)\n" +
        rawSettings.keys.sorted().map { "\($0): \(rawSettings[$0]!)\n" }.joined()
    }

    public static func number(_ text: String, fallback: Double = 0) -> Double {
        let text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let range = text.range(of: "^[+−–-]?\\d+(?:\\.\\d+)?(?:/\\d+)?", options: .regularExpression) else { return fallback }
        let parts = text[range].replacingOccurrences(of: "−", with: "-")
            .replacingOccurrences(of: "–", with: "-").split(separator: "/")
        guard let first = parts.first, let value = Double(first) else { return fallback }
        if parts.count == 2, let denominator = Double(parts[1]) { return value / denominator }
        return value
    }

    private static func bound(_ value: Double, _ low: Double, _ high: Double) -> Double {
        value.isFinite ? max(low, min(high, value)) : low
    }

    public func adjustments(notes: inout [String]) -> Settings {
        var settings = Settings()
        settings.film = film
        // A stable traversal makes note display reproducible. Source keys remain untouched.
        for key in rawSettings.keys.sorted() {
            let value = rawSettings[key]!, lower = value.lowercased(), number = Self.number(value)
            if lower.range(of: "(?:\\bto\\b|\\bor\\b|sometimes|depending|usually|occasionally)", options: .regularExpression) != nil {
                notes.append("\(key): có tùy chọn/range; xem giá trị nguồn.")
            }
            switch key.lowercased() {
            case "film simulation": settings.film = value
            case "highlight", "highlights", "highlight tone": settings.highlight = Self.bound(number, -2, 4)
            case "shadow", "shadows", "shadow tone": settings.shadow = Self.bound(number, -2, 4)
            case "highlight & shadow": settings.highlight = Self.bound(number, -2, 4); settings.shadow = Self.bound(number, -2, 4)
            case "color", "saturation": settings.color = Self.bound(number, -4, 4)
            case "sharpness", "sharpeness", "sharpening": settings.sharpness = Self.bound(number, -4, 4)
            case "noise reduction", "noise-reduction", "high iso nr": settings.nr = Self.bound(number, -4, 4)
            case "clarity": settings.clarity = Self.bound(number, -5, 5)
            case "grain", "grain effect":
                settings.grain = lower.contains("strong") ? 18 : lower.contains("weak") ? 8 : 0
                settings.grainSize = lower.contains("large") ? 2 : 1
            case "color chrome", "color chrome effect": settings.colorChrome = lower.contains("strong") ? 2 : lower.contains("weak") ? 1 : 0
            case "color chrome fx blue", "color chrome effect blue": settings.chromeBlue = lower.contains("strong") ? 2 : lower.contains("weak") ? 1 : 0
            case "dynamic range", "dr":
                settings.dr = lower.contains("400") ? 400 : lower.contains("200") ? 200 : 100
                if lower.contains("auto") { notes.append("DR Auto: giữ DR100 trong editor, không giả lập quyết định đo sáng của máy.") }
            case "white balance", "wb":
                settings.wb = String(value.split(separator: ",", omittingEmptySubsequences: false).first ?? "")
                func shift(_ channel: String) -> Double? {
                    guard let range = value.range(of: "([+−–-]?\\d+)\\s*\(channel)", options: [.regularExpression, .caseInsensitive]) else { return nil }
                    return Self.bound(Self.number(String(value[range])), -9, 9)
                }
                if let red = shift("Red") { settings.red = red }
                if let blue = shift("Blue") { settings.blue = blue }
                notes.append("WB \(settings.wb): giữ WB gốc của ảnh; chỉ áp dụng Red/Blue. Chỉnh Nhiệt độ nếu cần.")
            case "exposure compensation", "exposure":
                if lower.contains(" to ") || lower.contains(" or ") {
                    settings.exposure = 0
                    notes.append("Bù sáng là một khoảng: tự chọn EV, không tự lấy đầu khoảng.")
                } else { settings.exposure = Self.bound(number, -3, 3) }
            case "iso": notes.append("ISO là thông số lúc chụp, không áp dụng lại lên JPEG.")
            default: notes.append("\(key): giữ để tham khảo, bộ xử lý chưa áp dụng.")
            }
        }
        return settings
    }
}
