import Foundation

/// Standard .cube RGB ordering: red changes fastest. Values retain Android's Float precision.
public struct CubeLut: Sendable {
    public let size: Int
    public let values: [Float]
    public let min: [Float]
    public let max: [Float]
    public let title: String

    public static func parse(data: Data) throws -> CubeLut {
        guard let text = String(data: data, encoding: .utf8) else {
            throw CoreError.invalidValue("Tệp LUT phải là văn bản UTF-8")
        }
        return try parse(text)
    }

    public static func parse(_ text: String) throws -> CubeLut {
        try Task.checkCancellation()
        var size = 0, data: [Float] = [], lo: [Float] = [0, 0, 0], hi: [Float] = [1, 1, 1]
        var title = "LUT tùy chỉnh", foundSize = false
        // Enumerate lines rather than creating an array of every line of a large LUT.
        var lineNumber = 0
        var failure: Error?
        text.enumerateLines { raw, stop in
            do {
                lineNumber += 1
                if lineNumber & 1023 == 0 { try Task.checkCancellation() }
                guard lineNumber <= 300_000 else { throw CoreError.invalidValue("LUT vượt giới hạn số dòng") }
                let line = String(raw.prefix { $0 != "#" }).trimmingCharacters(in: .whitespacesAndNewlines)
                if line.isEmpty { return }
                let parts = line.split(whereSeparator: \.isWhitespace)
                func triple() throws -> [Float] {
                    guard parts.count == 4 else { throw CoreError.invalidValue("Miền LUT cần 3 giá trị") }
                    return try parts.dropFirst().map { word in
                        guard let value = Float(word) else { throw CoreError.invalidValue("Số không hợp lệ ở dòng \(lineNumber)") }
                        return value
                    }
                }
                switch parts[0] {
                case "TITLE": title = line.dropFirst(5).trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "\"", with: "")
                case "LUT_1D_SIZE": throw CoreError.invalidValue("Chỉ hỗ trợ LUT 3D .cube")
                case "LUT_3D_SIZE":
                    guard !foundSize, parts.count == 2 else { throw CoreError.invalidValue("Kích thước LUT bị lặp hoặc sai") }
                    guard let parsed = Int(parts[1]) else { throw CoreError.invalidValue("Số không hợp lệ ở dòng \(lineNumber)") }
                    guard (2...65).contains(parsed) else { throw CoreError.invalidValue("LUT cần từ 2 đến 65 điểm mỗi chiều") }
                    size = parsed; foundSize = true; data.reserveCapacity(size * size * size * 3)
                case "DOMAIN_MIN": lo = try triple()
                case "DOMAIN_MAX": hi = try triple()
                default:
                    guard parts.count == 3, foundSize, data.count + 3 <= size * size * size * 3 else {
                        throw CoreError.invalidValue("Dữ liệu LUT không khớp kích thước")
                    }
                    for part in parts {
                        guard let value = Float(part), value.isFinite else { throw CoreError.invalidValue("Giá trị LUT không hữu hạn hoặc không hợp lệ") }
                        data.append(value)
                    }
                }
            } catch { failure = error; stop = true }
        }
        if let failure { throw failure }
        try Task.checkCancellation()
        guard foundSize, data.count == size * size * size * 3 else { throw CoreError.invalidValue("LUT thiếu điểm màu") }
        for index in 0..<3 where !lo[index].isFinite || !hi[index].isFinite || lo[index] >= hi[index] {
            throw CoreError.invalidValue("Miền màu không hợp lệ")
        }
        return CubeLut(size: size, values: data, min: lo, max: hi, title: title)
    }

    public func sample(_ r: Double, _ g: Double, _ b: Double) -> (red: Double, green: Double, blue: Double) {
        func coordinate(_ value: Double, _ channel: Int) -> Double {
            // Subtract domain bounds as Float before promotion, matching Java arithmetic.
            let scaled = (value - Double(min[channel])) / Double(max[channel] - min[channel])
            return Swift.max(0, Swift.min(1, scaled)) * Double(size - 1)
        }
        let vr = coordinate(r, 0), vg = coordinate(g, 1), vb = coordinate(b, 2)
        let ir = Int(vr), ig = Int(vg), ib = Int(vb)
        let fr = vr - Double(ir), fg = vg - Double(ig), fb = vb - Double(ib)
        var red = 0.0, green = 0.0, blue = 0.0
        for z in 0..<2 { for y in 0..<2 { for x in 0..<2 {
            let index = (Swift.min(ir + x, size - 1) + Swift.min(ig + y, size - 1) * size + Swift.min(ib + z, size - 1) * size * size) * 3
            let weight = (x == 0 ? 1 - fr : fr) * (y == 0 ? 1 - fg : fg) * (z == 0 ? 1 - fb : fb)
            red += Double(values[index]) * weight
            green += Double(values[index + 1]) * weight
            blue += Double(values[index + 2]) * weight
        } } }
        return (red, green, blue)
    }
}
