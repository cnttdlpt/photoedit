#if PORTABLE_CORE_CHECKS
import Foundation

/// Run by run-core-checks.sh using the independent macOS Command Line Tools SDK.
/// Kept behind a flag so the iOS XCTest target can include this file unchanged.
@main
enum PortableCoreChecks {
    struct Failed: Error, CustomStringConvertible { let description: String }
    final class PixelCounter: @unchecked Sendable {
        private let lock = NSLock()
        private var count = 0
        func add(_ value: Int) { lock.lock(); count += value; lock.unlock() }
        var value: Int { lock.lock(); defer { lock.unlock() }; return count }
    }

    static func main() async throws {
        var checks = 0
        func check(_ condition: Bool, _ name: String) throws {
            guard condition else { throw Failed(description: "FAIL: \(name)") }
            checks += 1
        }
        func rejects(_ name: String, _ body: () throws -> Void) throws {
            do { try body() } catch { checks += 1; return }
            throw Failed(description: "FAIL: accepted \(name)")
        }
        let identity = "TITLE \"Identity\"\nLUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n"
        let lut = try CubeLut.parse(identity)
        let interpolated = lut.sample(0.25, 0.67, 0.9)
        try check(abs(interpolated.red - 0.25) < 0.000001 && abs(interpolated.green - 0.67) < 0.000001 && abs(interpolated.blue - 0.9) < 0.000001, "trilinear identity")
        let clamped = lut.sample(-10, 3, 0)
        try check(clamped.red == 0 && clamped.green == 1 && clamped.blue == 0, "LUT domain clamp")
        for invalid in [identity + "0 0 0", identity.replacingOccurrences(of: "1 1 1", with: "NaN 1 1"), "LUT_1D_SIZE 2", "DOMAIN_MIN 1 0 0\n" + identity, identity.replacingOccurrences(of: "LUT_3D_SIZE 2", with: "LUT_3D_SIZE 66"), "LUT_3D_SIZE 2\n" + identity] {
            try rejects("invalid LUT") { _ = try CubeLut.parse(invalid) }
        }
        var pixels: [UInt8] = []
        for y in 0..<11 { for x in 0..<13 {
            pixels += [UInt8((x * 31 + y * 17 + 11) & 255), UInt8((x * 7 + y * 41 + 29) & 255), UInt8((x * 53 + y * 3 + 73) & 255), UInt8((x * 19 + y * 23 + 97) & 255)]
        } }
        let source = try RasterImage(width: 13, height: 11, pixels: pixels)
        try check(ColorEngine.render(source, settings: Settings()) == source, "neutral preserves RGBA bytes")
        try check(ColorEngine.render(source, settings: Settings(), lut: lut) == source, "identity preserves RGBA bytes")
        var settings = Settings(); settings.film = "Classic Chrome"
        try check(ColorEngine.render(source, settings: settings) == source, "film metadata never invents a transform")
        let gray = try RasterImage(width: 1, height: 1, pixels: [128, 128, 128, 73])
        settings.exposure = 1
        try check(ColorEngine.render(gray, settings: settings).pixels == [176, 176, 176, 73], "EV uses linear light")
        try rejects("zero-sized image") { _ = try RasterImage(width: 0, height: 1, pixels: []) }
        try rejects("overflow-sized image") { _ = try RasterImage(width: Int.max, height: 2, pixels: []) }
        try rejects("wrong pixel count") { _ = try RasterImage(width: 1, height: 1, pixels: [0]) }

        func parity(_ s: Settings, _ expected: String, lut: CubeLut? = nil) throws {
            var hash: UInt64 = 0xcbf29ce484222325
            for byte in try ColorEngine.render(source, settings: s, lut: lut).pixels { hash = (hash ^ UInt64(byte)) &* 0x100000001b3 }
            try check(String(format: "%016llx", hash) == expected, "Android pixel parity \(expected), got \(String(format: "%016llx", hash))")
        }
        var s = Settings(); try parity(s, "22180939cf4cfbdb")
        s.exposure = 1; try parity(s, "9ca0f4b73b8ed5b7")
        s = Settings(); s.red = 7; s.blue = -6; s.temperature = 37; try parity(s, "252e5d23359850ac")
        s = Settings(); s.highlight = -2; s.shadow = 3; s.color = 4; s.dr = 400; s.colorChrome = 2; s.chromeBlue = 1
        try parity(s, "64ef2e3a327f0b15")
        s = Settings(); s.grain = 18; s.grainSize = 2; try parity(s, "4bde4cff5fc4fa9b")
        s = Settings(); s.nr = 4; try parity(s, "f237ebc4ed31cce4")
        s = Settings(); s.sharpness = 3; s.clarity = -5; try parity(s, "77db1c6007af0caf")
        s = Settings(); s.exposure = -0.7; s.highlight = 2; s.shadow = -1; s.color = -3
        s.red = 3; s.blue = -2; s.temperature = -15; s.sharpness = 2; s.nr = -1
        s.grain = 8; s.grainSize = 1; s.clarity = 3; s.colorChrome = 1; s.chromeBlue = 2; s.lutAmount = 63; s.dr = 200
        let mapped = try CubeLut.parse("TITLE \"Domain swap\"\nDOMAIN_MIN -0.1 0.1 0.05\nDOMAIN_MAX 1.2 0.9 1.05\nLUT_3D_SIZE 2\n0.05 0.1 0\n0.05 0.3 1\n0.05 0.9 0\n0.05 1.1 1\n0.95 0.1 0\n0.95 0.3 1\n0.95 0.9 0\n0.95 1.1 1\n")
        try parity(s, "4d255e8c74abd734", lut: mapped)

        let decoder = JSONDecoder()
        try check(decoder.decode(Settings.self, from: Data("{}".utf8)) == Settings(), "settings defaults")
        try check(decoder.decode(Settings.self, from: JSONEncoder().encode(s)) == s, "settings round trip")
        for invalid in ["{\"exposure\":4}", "{\"dr\":300}", "{\"nr\":-5}", "{\"grainSize\":0}", "{\"lutAmount\":101}", "{\"film\":null}"] {
            try rejects("invalid settings") { _ = try decoder.decode(Settings.self, from: Data(invalid.utf8)) }
        }
        settings.exposure = .nan
        try rejects("nonfinite settings") { _ = try settings.validated() }
        let recipe = Recipe(film: "Classic Chrome", rawSettings: ["Dynamic Range": "DR-Auto", "Highlight": "+1 (0 sometimes)", "White Balance": "Daylight, +4 Red & −2 Blue", "ISO": "Auto, up to ISO 6400", "Exposure Compensation": "-1/3 to +1/3", "Grain Effect": "Strong, Large"])
        var notes: [String] = []
        let applied = recipe.adjustments(notes: &notes)
        try check(applied.exposure == 0 && applied.red == 4 && applied.blue == -2 && applied.temperature == 0 && applied.highlight == 1 && applied.dr == 100 && applied.grain == 18 && applied.grainSize == 2, "recipe applies supported values")
        try check(notes.contains { $0.contains("Bù sáng là một khoảng") } && notes.contains { $0.contains("ISO là thông số lúc chụp") }, "recipe retains capture-only/range cautions")
        try check(decoder.decode(Recipe.self, from: JSONEncoder().encode(recipe)) == recipe, "recipe round trip")
        try check(abs(Recipe.number("−1/3 to +1/3") + 1.0 / 3) < 0.000001, "unicode fractional source values")
        try rejects("oversized recipe") { try Recipe(rawSettings: ["value": String(repeating: "a", count: 501)]).validate() }
        let tooLong = String(repeating: "a", count: 301)
        for recipe in [Recipe(film: tooLong), Recipe(rawSettings: ["Film Simulation": tooLong]), Recipe(rawSettings: ["White Balance": tooLong])] {
            try rejects("recipe producing invalid editor strings") { try recipe.validate() }
            let data = try JSONEncoder().encode(recipe)
            try rejects("import producing invalid editor strings") { _ = try decoder.decode(Recipe.self, from: data) }
        }
        try Recipe(rawSettings: ["White Balance": "Daylight, " + tooLong]).validate()
        try check(true, "long WB source notes remain valid after the name")
        if CommandLine.arguments.count > 1 {
            struct Catalog: Decodable { let recipes: [Recipe] }
            let catalog = try decoder.decode(Catalog.self, from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
            try check(catalog.recipes.count == 391, "bundled Android catalog decodes all 391 recipes")
            for recipe in catalog.recipes { var notes: [String] = []; _ = try recipe.adjustments(notes: &notes).validated() }
            try check(true, "every bundled recipe produces valid editor settings")
        }
        let full = try ExportSizing.dimensions(width: 2000, height: 1500, scale: 2, fullSize: true, pixelBudget: 4_000_000)
        try check(full.width == 2000 && full.height == 1500, "2x source dimensions")
        let standard = try ExportSizing.dimensions(width: 8000, height: 6000, scale: 1, fullSize: false, pixelBudget: 50_000_000)
        try check(standard.width == 4000 && standard.height == 3000, "12 MP standard")
        let limited = try ExportSizing.dimensions(width: 4000, height: 3000, scale: 1, fullSize: false, pixelBudget: 3_000_000)
        try check(limited.width == 2000 && limited.height == 1500, "memory-limited standard")
        try rejects("unsafe full export") { _ = try ExportSizing.dimensions(width: 6000, height: 4000, scale: 2, fullSize: true, pixelBudget: 10_000_000) }
        try rejects("overflow export") { _ = try ExportSizing.dimensions(width: 50_000, height: 50_000, scale: 2, fullSize: true, pixelBudget: Int.max) }
        let cancelled = Task.detached {
            withUnsafeCurrentTask { $0?.cancel() }
            return try ColorEngine.render(source, settings: Settings())
        }
        do { _ = try await cancelled.value; throw Failed(description: "FAIL: cancelled render completed") }
        catch is CancellationError { checks += 1 }

        let started = DispatchSemaphore(value: 0)
        let gate = DispatchGroup(); gate.enter()
        let processed = PixelCounter()
        let work = Task.detached(priority: .userInitiated) {
            try ColorEngine.forEachPixelBatch(count: 1_000_000) { range in
                if range.lowerBound == 0 { started.signal() }
                gate.wait()
                processed.add(range.count)
            }
        }
        let began = await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(returning: started.wait(timeout: .now() + 10) == .success)
            }
        }
        work.cancel(); gate.leave()
        do { try await work.value; throw Failed(description: "FAIL: cancelled pixel batches completed") }
        catch is CancellationError { checks += 1 }
        try check(began && processed.value > 0 && processed.value < 1_000_000, "cancellation during worker execution stops unfinished pixel batches")

        for (value, shouldLift) in [(UInt8(8), true), (UInt8(80), false)] {
            let image = try RasterImage(width: 1, height: 1, pixels: [value, value, value, 255])
            let balanced = AutoBalancer.analyze(image)
            var withoutShadow = balanced; withoutShadow.shadow = 0
            let result = try ColorEngine.render(image, settings: balanced).pixels[0]
            let reference = try ColorEngine.render(image, settings: withoutShadow).pixels[0]
            try check(shouldLift ? result > reference : result < reference, "auto balance \(shouldLift ? "lifts crushed" : "deepens washed-out") shadows")
        }
        var base = Settings(); base.film = "Acros"; base.lutAmount = 42; base.grain = 18; base.dr = 400
        let opaque: [UInt8] = [100, 100, 100, 255]
        let visible = try RasterImage(width: 1, height: 1, pixels: opaque)
        let padded = try RasterImage(width: 10, height: 1, pixels: opaque + Array(repeating: [255, 0, 0, 0], count: 9).flatMap { $0 })
        let balanced = AutoBalancer.analyze(visible, baseSettings: base)
        try check(AutoBalancer.analyze(padded, baseSettings: base) == balanced, "auto balance ignores fully transparent pixels")
        try check(balanced.film == base.film && balanced.lutAmount == base.lutAmount && balanced.grain == base.grain && balanced.dr == base.dr, "auto balance preserves film/LUT/grain/DR choices")
        let empty = try RasterImage(width: 1, height: 1, pixels: [0, 0, 0, 0])
        try check(AutoBalancer.analyze(empty, baseSettings: base) == base, "transparent image leaves settings unchanged")
        var malformed = visible; malformed.pixels.removeLast()
        try check(AutoBalancer.analyze(malformed, baseSettings: base) == base, "auto balance safely rejects malformed pixel buffer")

        var impulse = Array(repeating: UInt8(0), count: 3 * 3 * 4)
        for index in stride(from: 3, to: impulse.count, by: 4) { impulse[index] = 255 }
        impulse.replaceSubrange(16..<20, with: [225, 225, 225, 128])
        var detail = Settings(); detail.sharpness = -3
        let spatial = try ColorEngine.render(RasterImage(width: 3, height: 3, pixels: impulse), settings: detail)
        try check(Array(spatial.pixels[16..<20]) == [104, 104, 104, 128], "spatial half-byte rounding matches original Java multiplication order")
        var largePixels: [UInt8] = []
        for y in 0..<193 { for x in 0..<257 {
            largePixels.append(UInt8((x * 31 + y * 17 + 11) & 255))
            largePixels.append(UInt8((x * 7 + y * 41 + 29) & 255))
            largePixels.append(UInt8((x * 53 + y * 3 + 73) & 255))
            largePixels.append(UInt8((x * 19 + y * 23 + 97) & 255))
        } }
        let largeImage = try RasterImage(width: 257, height: 193, pixels: largePixels)
        detail.exposure = 0.3; detail.nr = 1; detail.clarity = 2; detail.grain = 8; detail.grainSize = 2
        var largeHash: UInt64 = 0xcbf29ce484222325
        for byte in try ColorEngine.render(largeImage, settings: detail).pixels { largeHash = (largeHash ^ UInt64(byte)) &* 0x100000001b3 }
        try check(String(format: "%016llx", largeHash) == "d047abccb0d854d5", "parallel render matches Android across pixel batch and row boundaries")
        print("PASS: \(checks) portable Swift core checks, including 9 exact Android RGBA parity fixtures")
    }
}
#endif
