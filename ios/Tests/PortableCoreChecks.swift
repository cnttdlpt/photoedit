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

        for (values, shouldLift): ([UInt8], Bool) in [([8], true), ([80, 160, 220], false)] {
            let image = try RasterImage(width: values.count, height: 1, pixels: values.flatMap { [$0, $0, $0, 255] })
            let balanced = AutoBalancer.analyze(image)
            var withoutShadow = balanced; withoutShadow.shadow = 0
            let result = try ColorEngine.render(image, settings: balanced).pixels[0]
            let reference = try ColorEngine.render(image, settings: withoutShadow).pixels[0]
            try check(shouldLift ? result > reference : result == reference, "auto balance \(shouldLift ? "lifts crushed" : "preserves normal") shadows")
        }
        let mixedBrightnessFixtures: [[UInt8]] = [
            Array(repeating: 60, count: 25) + Array(repeating: 210, count: 75),
            Array(repeating: 120, count: 15) + Array(repeating: 225, count: 85),
            Array(repeating: 80, count: 30) + Array(repeating: 160, count: 50) + Array(repeating: 220, count: 20),
            Array(repeating: 25, count: 35) + Array(repeating: 250, count: 65)
        ]
        for values in mixedBrightnessFixtures {
            let image = try RasterImage(width: values.count, height: 1, pixels: values.flatMap { [$0, $0, $0, 255] })
            let correction = AutoBalancer.analyze(image)
            let rendered = try ColorEngine.render(image, settings: correction)
            try check(correction.exposure >= 0 && correction.shadow <= 0 && correction.highlight == 0,
                      "auto balance does not mistake bright backgrounds for overexposure")
            try check(zip(rendered.pixels, image.pixels).allSatisfy { $0 >= $1 },
                      "auto balance preserves brightness of subjects in mixed bright scenes")
        }
        let clippedWhites = try RasterImage(width: 2, height: 1, pixels: [25, 25, 25, 255, 255, 255, 255, 255])
        let clippedCorrection = AutoBalancer.analyze(clippedWhites)
        let clippedResult = try ColorEngine.render(clippedWhites, settings: clippedCorrection)
        try check(clippedCorrection.exposure == 0 && clippedCorrection.highlight == -0.5,
                  "auto balance restrains genuinely clipped whites without lowering exposure")
        try check(clippedResult.pixels[0] > 25 && (250..<255).contains(clippedResult.pixels[4]),
                  "clipped-white restraint preserves shadow subjects and keeps whites bright")
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

        let highContrast = try RasterImage(width: 100, height: 1, pixels:
            Array(repeating: [UInt8(40), 40, 40, 255], count: 90).flatMap { $0 }
            + Array(repeating: [UInt8(240), 240, 240, 255], count: 10).flatMap { $0 })
        let protected = AutoBalancer.analyze(highContrast)
        try check(protected.exposure <= 0.1, "auto exposure respects existing highlight headroom")
        try check(ColorEngine.render(highContrast, settings: protected).pixels[360] < 255, "auto exposure does not clip highlight detail")
        let blueHighlight = try RasterImage(width: 1, height: 1, pixels: [0, 0, 220, 255])
        let blueCorrection = AutoBalancer.analyze(blueHighlight)
        try check(blueCorrection.exposure <= 0.4 && ColorEngine.render(blueHighlight, settings: blueCorrection).pixels[2] < 255,
                  "auto exposure protects individual color-channel highlights")
        let darkGray = try RasterImage(width: 1, height: 1, pixels: [80, 80, 80, 255])
        try check(abs(AutoBalancer.analyze(darkGray).exposure - 0.8) < 0.0001, "auto exposure derives EV from linear light")
        let healthyGray = try RasterImage(width: 1, height: 1, pixels: [145, 145, 145, 255])
        try check(ColorEngine.render(healthyGray, settings: AutoBalancer.analyze(healthyGray)) == healthyGray, "auto balance preserves healthy midtones")
        for value: UInt8 in [225, 250] {
            let highKey = try RasterImage(width: 1, height: 1, pixels: [value, value, value, 255])
            try check(ColorEngine.render(highKey, settings: AutoBalancer.analyze(highKey)) == highKey, "auto balance preserves intentional high-key scenes")
        }
        var creativeBase = Settings(); creativeBase.temperature = 7; creativeBase.clarity = -1.5
        creativeBase.sharpness = -1; creativeBase.color = -0.5
        for rgb: [UInt8] in [[220, 50, 30], [30, 80, 220], [80, 200, 50]] {
            let dominantColor = try RasterImage(width: 1, height: 1, pixels: rgb + [255])
            let correction = AutoBalancer.analyze(dominantColor, baseSettings: creativeBase)
            try check(correction.temperature == 7, "dominant subject color is not mistaken for white balance")
            try check(correction.color == -0.5 && correction.sharpness == -1 && correction.clarity == -1.5, "auto balance preserves creative detail and saturation")
        }
        let warmNeutral = try RasterImage(width: 1, height: 1, pixels: [150, 140, 130, 255])
        try check((-15..<0).contains(AutoBalancer.analyze(warmNeutral).temperature), "neutral-pixel evidence produces a restrained WB correction")

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

        // LightCraft Engine & Histogram Checks
        let neutralLC = LightCraftState()
        try check(neutralLC.isIdentity, "default LightCraftState is identity")
        try check(try LightCraftEngine.render(source, state: neutralLC) == source, "neutral LightCraft preserves RGBA bytes")
        var dummySettings = Settings()
        for preset in LightCraftEngine.presets {
            var presetState = LightCraftState()
            preset.apply(to: &presetState, settings: &dummySettings)
            try check(!presetState.isIdentity, "LightCraft preset \(preset.id) is active")
            let renderedPreset = try LightCraftEngine.render(source, state: presetState)
            try check(renderedPreset.width == source.width && renderedPreset.height == source.height && renderedPreset != source, "LightCraft preset \(preset.id) transforms image")
        }
        var maskState = LightCraftState()
        maskState.maskMode = .radial
        maskState.maskExposure = 1.0
        let maskedRender = try LightCraftEngine.render(source, state: maskState, includeMaskOverlay: false)
        try check(maskedRender != source, "LightCraft radial mask transforms image")
        let hist = RgbHistogramData.analyze(source)
        try check(hist.r.count == 64 && hist.g.count == 64 && hist.b.count == 64, "RgbHistogramData computes 64 bins")
        try check(decoder.decode(LightCraftState.self, from: JSONEncoder().encode(maskState)) == maskState, "LightCraftState Codable round-trip")
        maskState.maskExposure = 0; maskState.showMaskOverlay = true
        try check(LightCraftEngine.render(source, state: maskState) != source, "mask overlay is visible in preview")
        try check(LightCraftEngine.render(source, state: maskState, includeMaskOverlay: false) == source, "mask overlay never appears in export")
        var curveState = LightCraftState()
        curveState.masterCurve = [CurvePoint(x: 0.2, y: 0.2), CurvePoint(x: 0.5, y: 0.5), CurvePoint(x: 0.8, y: 0.8)]
        let endpoints = try RasterImage(width: 2, height: 1, pixels: [0, 0, 0, 73, 255, 255, 255, 200])
        try check(!curveState.isIdentity, "moved curve endpoints remain active after adding a point")
        try check(LightCraftEngine.render(endpoints, state: curveState).pixels == [51, 51, 51, 73, 204, 204, 204, 200], "curve endpoint clipping preserves alpha")
        try rejects("malformed neutral LightCraft buffer") { _ = try LightCraftEngine.render(malformed, state: neutralLC) }
        let cancelledLightCraft = Task.detached {
            withUnsafeCurrentTask { $0?.cancel() }
            return try LightCraftEngine.render(source, state: neutralLC)
        }
        do { _ = try await cancelledLightCraft.value; throw Failed(description: "FAIL: cancelled neutral LightCraft render completed") }
        catch is CancellationError { checks += 1 }

        print("PASS: \(checks) portable Swift core checks, including 9 exact Android RGBA parity fixtures & LightCraft darkroom checks")
    }
}
#endif
