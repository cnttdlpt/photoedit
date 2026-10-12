import Foundation

/// Editor controls for developed sRGB images; film names are metadata until a LUT is supplied.
public struct Settings: Codable, Equatable, Sendable {
    public var exposure = 0.0
    public var highlight = 0.0
    public var shadow = 0.0
    public var color = 0.0
    public var red = 0.0
    public var blue = 0.0
    public var temperature = 0.0
    public var sharpness = 0.0
    public var nr = -4.0
    public var grain = 0.0
    public var grainSize = 1.0
    public var clarity = 0.0
    public var colorChrome = 0.0
    public var chromeBlue = 0.0
    public var lutAmount = 100.0
    public var dr = 100
    public var film = "Original"
    public var wb = "As shot"

    public init() {}

    private enum CodingKeys: String, CodingKey, CaseIterable {
        case exposure, highlight, shadow, color, red, blue, temperature, sharpness, nr
        case grain, grainSize, clarity, colorChrome, chromeBlue, lutAmount, dr, film, wb
    }

    public init(from decoder: Decoder) throws {
        self.init()
        let values = try decoder.container(keyedBy: CodingKeys.self)
        func value(_ key: CodingKeys, _ fallback: Double) throws -> Double {
            if values.contains(key) { return try values.decode(Double.self, forKey: key) }
            return fallback
        }
        exposure = try value(.exposure, exposure)
        highlight = try value(.highlight, highlight)
        shadow = try value(.shadow, shadow)
        color = try value(.color, color)
        red = try value(.red, red)
        blue = try value(.blue, blue)
        temperature = try value(.temperature, temperature)
        sharpness = try value(.sharpness, sharpness)
        nr = try value(.nr, nr)
        grain = try value(.grain, grain)
        grainSize = try value(.grainSize, grainSize)
        clarity = try value(.clarity, clarity)
        colorChrome = try value(.colorChrome, colorChrome)
        chromeBlue = try value(.chromeBlue, chromeBlue)
        lutAmount = try value(.lutAmount, lutAmount)
        if values.contains(.dr) { dr = try values.decode(Int.self, forKey: .dr) }
        if values.contains(.film) { film = try values.decode(String.self, forKey: .film) }
        if values.contains(.wb) { wb = try values.decode(String.self, forKey: .wb) }
        self = try validated()
    }

    @discardableResult
    public func validated() throws -> Settings {
        let bounds: [(String, Double, ClosedRange<Double>)] = [
            ("exposure", exposure, -3...3), ("highlight", highlight, -2...4),
            ("shadow", shadow, -2...4), ("color", color, -4...4),
            ("red", red, -9...9), ("blue", blue, -9...9),
            ("temperature", temperature, -100...100), ("sharpness", sharpness, -4...4),
            ("nr", nr, -4...4), ("grain", grain, 0...30), ("grainSize", grainSize, 1...2),
            ("clarity", clarity, -5...5), ("colorChrome", colorChrome, 0...2),
            ("chromeBlue", chromeBlue, 0...2), ("lutAmount", lutAmount, 0...100)
        ]
        for (name, value, range) in bounds where !value.isFinite || !range.contains(value) {
            throw CoreError.invalidValue("Ngoài phạm vi: \(name)")
        }
        guard [100, 200, 400].contains(dr) else { throw CoreError.invalidValue("DR không hợp lệ") }
        // Java's String.length measures UTF-16 units.
        guard film.utf16.count <= 300, wb.utf16.count <= 300 else {
            throw CoreError.invalidValue("Giá trị Film hoặc WB quá dài")
        }
        return self
    }
}

public enum CoreError: Error, LocalizedError, Equatable, Sendable {
    case invalidValue(String)
    public var errorDescription: String? {
        switch self { case .invalidValue(let message): return message }
    }
}
