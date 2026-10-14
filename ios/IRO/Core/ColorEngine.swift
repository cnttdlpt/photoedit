import Foundation

/// Straight-alpha, 8-bit sRGB pixels in R, G, B, A order.
public struct RasterImage: Equatable, Sendable {
    public let width: Int
    public let height: Int
    public var pixels: [UInt8]

    public init(width: Int, height: Int, pixels: [UInt8]) throws {
        guard width > 0, height > 0, width <= Int.max / height,
              width * height <= Int.max / 4, width * height * 4 == pixels.count else {
            throw CoreError.invalidValue("Kích thước ảnh không hợp lệ")
        }
        self.width = width; self.height = height; self.pixels = pixels
    }
}

/// A faithful port of Android's sRGB post-processing. Film color requires an imported LUT.
public enum ColorEngine {
    private static func clamp(_ value: Double) -> Double { max(0, min(1, value)) }
    private static func linear(_ value: Double) -> Double { value <= 0.04045 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4) }
    private static func gamma(_ value: Double) -> Double { value <= 0.0031308 ? 12.92 * value : 1.055 * pow(value, 1 / 2.4) - 0.055 }
    private static func byte(_ value: Double) -> UInt8 { UInt8(floor(clamp(value) * 255 + 0.5)) }

    /// Precomputed sRGB → linear-light conversion for all 256 byte values.
    /// Eliminates a per-pixel `pow(_:2.4)` call in the colour-grading hot path.
    private static let linearLUT: [Double] = (0...255).map { b in linear(Double(b) / 255.0) }

    /// GCD workers do not inherit the calling Swift task. Join bounded chunks, then
    /// check cancellation on the caller before scheduling more pixels.
    static func forEachPixelBatch(count: Int, body: (Range<Int>) -> Void) throws {
        let chunkSize = 4_096
        let workers = max(1, min(8, ProcessInfo.processInfo.activeProcessorCount))
        let batchSize = chunkSize * workers
        for start in stride(from: 0, to: count, by: batchSize) {
            try Task.checkCancellation()
            let remaining = count - start
            let slices = min(workers, (remaining - 1) / chunkSize + 1)
            DispatchQueue.concurrentPerform(iterations: slices) { slice in
                let lower = start + slice * chunkSize
                body(lower..<min(count, lower + chunkSize))
            }
        }
        try Task.checkCancellation()
    }

    public static func render(_ image: RasterImage, settings s: Settings, lut: CubeLut? = nil) throws -> RasterImage {
        _ = try s.validated()
        let w = image.width, h = image.height
        guard image.pixels.count == w * h * 4 else { throw CoreError.invalidValue("Kích thước ảnh không hợp lệ") }
        try Task.checkCancellation()
        let pixels = image.pixels
        var output = pixels
        let ev = pow(2, s.exposure)
        // Pre-compute per-render scalars that do not vary per pixel.
        // Each exp() / multiplication here saves one call per pixel inside the hot loop.
        let rExpFactor = exp(s.red * 0.023 + s.temperature * 0.002)
        let bExpFactor = exp(s.blue * 0.023 - s.temperature * 0.002)
        let colorFactor = 1 + s.color * 0.08
        let tonHigh = s.highlight * 0.035
        let tonShad = s.shadow * 0.035
        let tonDR   = Double(s.dr - 100) / 300.0 * 0.10
        let ccFactor = s.colorChrome * 0.012
        let cbFactor = s.chromeBlue * 0.04
        let grainSize = Int(max(1, s.grainSize))

        // Precompute per-byte gamma LUTs (256 entries each) to eliminate 3 * w * h calls to pow(_, 1/2.4).
        let rGammaLUT: [Double] = (0...255).map { gamma(max(0, linearLUT[$0] * ev * rExpFactor)) }
        let gGammaLUT: [Double] = (0...255).map { gamma(max(0, linearLUT[$0] * ev)) }
        let bGammaLUT: [Double] = (0...255).map { gamma(max(0, linearLUT[$0] * ev * bExpFactor)) }

        // Workers cover disjoint pixel ranges, so concurrent pointer writes are safe.
        try pixels.withUnsafeBufferPointer { inBuf in
            try output.withUnsafeMutableBufferPointer { outBuf in
                guard let inPtr = inBuf.baseAddress, let outPtr = outBuf.baseAddress else { return }
                try rGammaLUT.withUnsafeBufferPointer { rLutBuf in
                    try gGammaLUT.withUnsafeBufferPointer { gLutBuf in
                        try bGammaLUT.withUnsafeBufferPointer { bLutBuf in
                            guard let rLut = rLutBuf.baseAddress, let gLut = gLutBuf.baseAddress, let bLut = bLutBuf.baseAddress else { return }
                            try forEachPixelBatch(count: w * h) { range in
                                let firstRow = range.lowerBound / w
                                let lastRow = (range.upperBound - 1) / w
                                for y in firstRow...lastRow {
                                    let gy = UInt32(truncatingIfNeeded: y / grainSize)
                                    let gyHash = gy &* 668_265_263
                                    let firstX = y == firstRow ? range.lowerBound % w : 0
                                    let endX = y == lastRow ? (range.upperBound - 1) % w + 1 : w
                                    for x in firstX..<endX {
                                        let offset = (y * w + x) * 4
                                        var r = rLut[Int(inPtr[offset    ])]
                                        var g = gLut[Int(inPtr[offset + 1])]
                                        var b = bLut[Int(inPtr[offset + 2])]
                                        if let lut {
                                            let mapped = lut.sample(r, g, b)
                                            r += (mapped.red   - r) * s.lutAmount / 100
                                            g += (mapped.green - g) * s.lutAmount / 100
                                            b += (mapped.blue  - b) * s.lutAmount / 100
                                        }
                                        let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
                                        let cLum = clamp(luminance)
                                        let invLum = 1 - cLum
                                        let shadow = invLum * invLum
                                        let high = cLum * cLum
                                        let chroma = max(r, max(g, b)) - min(r, min(g, b))
                                        let blueWeight = max(0, b - max(r, g))
                                        let tone = tonHigh * high - tonShad * shadow - tonDR * (high * cLum)
                                        let gx = UInt32(truncatingIfNeeded: x / grainSize)
                                        // Java int overflow and unsigned right shifts are intentional here.
                                        var hash = (gx &* 374_761_393 &+ gyHash) ^ 0x5a1f1234
                                        hash = (hash ^ (hash >> 13)) &* 1_274_126_177
                                        let noise = (Double((hash >> 8) & 65_535) / 65_535 - 0.5) * s.grain / 255
                                        outPtr[offset    ] = byte(luminance + (r - luminance) * colorFactor + tone - ccFactor * chroma - cbFactor * blueWeight + noise)
                                        outPtr[offset + 1] = byte(luminance + (g - luminance) * colorFactor + tone - ccFactor * chroma - cbFactor * blueWeight + noise)
                                        outPtr[offset + 2] = byte(luminance + (b - luminance) * colorFactor + tone - ccFactor * chroma - cbFactor * blueWeight + noise)
                                        // Alpha channel is already present via the initial `output = pixels` copy.
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Spatial filter pass uses the same bounded parallel batches.
        if (s.nr > -4 || s.sharpness != 0 || s.clarity != 0), w > 2, h > 2 {
            let source = output
            let noiseAmount = (s.nr + 4) / 8 * 0.65
            let hasClarity = s.clarity != 0
            try source.withUnsafeBufferPointer { srcBuf in
                try output.withUnsafeMutableBufferPointer { outBuf in
                    guard let srcPtr = srcBuf.baseAddress, let outPtr = outBuf.baseAddress else { return }
                    let interiorWidth = w - 2
                    try forEachPixelBatch(count: interiorWidth * (h - 2)) { range in
                        for pixel in range {
                            let y = pixel / interiorWidth + 1
                            let x = pixel % interiorWidth + 1
                            let offset = (y * w + x) * 4
                            for channel in 0..<3 {
                                let index = offset + channel, value = Double(srcPtr[index])
                                let average = (Double(srcPtr[index - 4]) + Double(srcPtr[index + 4]) + Double(srcPtr[index - w * 4]) + Double(srcPtr[index + w * 4])) / 4
                                // Keep Android's multiplication order: regrouping the
                                // sharpness/clarity scalars can change half-byte rounding.
                                var filtered = value + (average - value) * noiseAmount + (value - average) * s.sharpness * 0.18
                                if hasClarity, x >= 4, x < w - 4, y >= 4, y < h - 4 {
                                    let broad = (Double(srcPtr[index - 16]) + Double(srcPtr[index + 16]) + Double(srcPtr[index - w * 16]) + Double(srcPtr[index + w * 16])) / 4
                                    filtered += (value - broad) * s.clarity * 0.06
                                }
                                outPtr[index] = byte(filtered / 255)
                            }
                        }
                    }
                }
            }
        }
        try Task.checkCancellation()
        return try RasterImage(width: w, height: h, pixels: output)
    }
}

// MARK: - Auto-Balance Image Analysis
public enum AutoBalancer {
    private static func linear(_ byte: UInt8) -> Double {
        let value = Double(byte) / 255
        return value <= 0.04045 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4)
    }

    /// Conservative local correction of the unedited source. It cannot infer a
    /// scene's intended mood, so avoid turning every scene into middle gray or
    /// treating a dominant subject color as a white-balance reference.
    public static func analyze(_ image: RasterImage, baseSettings: Settings = Settings()) -> Settings {
        let totalPixels = image.width * image.height
        guard totalPixels > 0, image.pixels.count == totalPixels * 4 else { return baseSettings }
        let sampleStep = max(1, totalPixels / 10000)
        var luminances: [Double] = []
        var channelPeaks: [Double] = []
        var encodedLuminances: [Double] = []
        var neutralLogRatios: [Double] = []
        luminances.reserveCapacity(totalPixels / sampleStep + 1)

        for pixel in stride(from: 0, to: totalPixels, by: sampleStep) {
            let idx = pixel * 4
            guard image.pixels[idx + 3] >= 16 else { continue }
            let r = Double(image.pixels[idx])
            let g = Double(image.pixels[idx + 1])
            let b = Double(image.pixels[idx + 2])
            let lr = linear(image.pixels[idx])
            let lg = linear(image.pixels[idx + 1])
            let lb = linear(image.pixels[idx + 2])
            let luminance = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
            luminances.append(luminance)
            channelPeaks.append(max(lr, max(lg, lb)))
            encodedLuminances.append(0.2126 * r + 0.7152 * g + 0.0722 * b)

            let maxC = max(r, max(g, b)), minC = min(r, min(g, b))
            let saturation = maxC > 0 ? (maxC - minC) / maxC : 0
            // Midtone, nearly neutral pixels offer evidence of a cast. Red
            // flowers, blue skies, and clipped highlights do not.
            if saturation <= 0.18, luminance > 0.06, luminance < 0.8, lr > 0, lb > 0 {
                neutralLogRatios.append(log(lr / lb))
            }
        }
        guard !luminances.isEmpty else { return baseSettings }
        luminances.sort()
        channelPeaks.sort()
        encodedLuminances.sort()
        let median = luminances[luminances.count / 2]
        let p95 = channelPeaks[Int(Double(channelPeaks.count) * 0.95)]
        let encodedMedian = encodedLuminances[encodedLuminances.count / 2]
        let encodedP5 = encodedLuminances[Int(Double(encodedLuminances.count) * 0.05)]
        let encodedP95 = encodedLuminances[Int(Double(encodedLuminances.count) * 0.95)]
        let isHighKey = encodedP5 >= 180
        var adjusted = baseSettings

        // Exposure is a power-of-two gain in linear light in ColorEngine.
        // A bright background can dominate the median while the subject is
        // already dark. A high median alone is never evidence of overexposure:
        // only lift dark midtones, and protect the existing highlight headroom.
        var exposure = max(0, min(1.2, log2(0.14 / max(median, 0.0001))))
        if exposure > 0, p95 > 0 {
            // Leave room for the subsequent highlight/shadow tone adjustments.
            exposure = min(exposure, max(0, log2(0.90 / p95)))
        }
        adjusted.exposure = (exposure * 10).rounded(.towardZero) / 10

        if isHighKey {
            adjusted.highlight = 0
        } else if encodedP95 >= 254 {
            // Restrain genuinely clipped whites without pulling down global
            // exposure or treating detailed, bright backgrounds as blown out.
            adjusted.highlight = -0.5
        } else if encodedP95 < 180 && encodedMedian < 110 {
            adjusted.highlight = 0.5
        } else {
            adjusted.highlight = 0
        }
        // Lift dark subjects even against bright backgrounds. Automatic
        // balancing must not add contrast by deepening the user's shadows.
        adjusted.shadow = encodedP5 < 40 ? -0.5 : 0

        // Temperature changes the linear R/B ratio by exp(temperature * 0.004).
        // Require neutral coverage, and use its median so colored outliers do
        // not set the correction. Without evidence, retain the user's WB.
        if neutralLogRatios.count >= max(1, luminances.count / 20) {
            neutralLogRatios.sort()
            let ratio = neutralLogRatios[neutralLogRatios.count / 2]
            adjusted.temperature = max(-15, min(15, (-ratio / 0.004).rounded()))
        }
        // Color, clarity and sharpness are creative choices, not evidence of
        // exposure or white-balance errors. Preserve the existing values.
        return adjusted
    }
}
