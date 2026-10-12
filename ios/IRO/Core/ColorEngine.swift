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
        let tonDR   = Double(s.dr - 100) / 300 * 0.10
        let ccFactor = s.colorChrome * 0.012
        let cbFactor = s.chromeBlue * 0.04
        let grainSize = Int(max(1, s.grainSize))

        // Workers cover disjoint pixel ranges, so concurrent pointer writes are safe.
        try pixels.withUnsafeBufferPointer { inBuf in
            try output.withUnsafeMutableBufferPointer { outBuf in
                guard let inPtr = inBuf.baseAddress, let outPtr = outBuf.baseAddress else { return }
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
                            // Linearise via lookup table to avoid pow(_, 2.4) per pixel.
                            // Multiplication order: (linearLUT * ev) * expFactor — identical to original.
                            var r = gamma(max(0, linearLUT[Int(inPtr[offset    ])] * ev * rExpFactor))
                            var g = gamma(max(0, linearLUT[Int(inPtr[offset + 1])] * ev))
                            var b = gamma(max(0, linearLUT[Int(inPtr[offset + 2])] * ev * bExpFactor))
                            if let lut {
                                let mapped = lut.sample(r, g, b)
                                r += (mapped.red   - r) * s.lutAmount / 100
                                g += (mapped.green - g) * s.lutAmount / 100
                                b += (mapped.blue  - b) * s.lutAmount / 100
                            }
                            let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
                            let shadow = pow(1 - clamp(luminance), 2), high = pow(clamp(luminance), 2)
                            let chroma = max(r, max(g, b)) - min(r, min(g, b))
                            let blueWeight = max(0, b - max(r, g))
                            let tone = tonHigh * high - tonShad * shadow - tonDR * pow(clamp(luminance), 3)
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
    /// Analyzes the raw pixel data of a RasterImage and returns optimized, balanced Settings.
    public static func analyze(_ image: RasterImage, baseSettings: Settings = Settings()) -> Settings {
        let totalPixels = image.width * image.height
        guard totalPixels > 0, image.pixels.count == totalPixels * 4 else { return baseSettings }
        
        let sampleStep = max(1, totalPixels / 10000)
        let pixels = image.pixels
        
        var luminanceValues: [Double] = []
        luminanceValues.reserveCapacity(totalPixels / sampleStep + 1)
        
        var sumR: Double = 0
        var sumB: Double = 0
        var count: Double = 0
        var sumSat: Double = 0

        var idx = 0
        let totalByteCount = pixels.count
        while idx + 3 < totalByteCount {
            // Transparent padding is not visible content and must not drive exposure/WB.
            guard pixels[idx + 3] > 0 else { idx += sampleStep * 4; continue }
            let r = Double(pixels[idx])
            let g = Double(pixels[idx + 1])
            let b = Double(pixels[idx + 2])
            
            // Rec. 709 perceived luminance
            let lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            luminanceValues.append(lum)
            
            sumR += r
            sumB += b
            
            let maxC = max(r, max(g, b))
            let minC = min(r, min(g, b))
            if maxC > 0 {
                sumSat += (maxC - minC) / maxC
            }
            
            count += 1
            idx += sampleStep * 4
        }
        
        guard count > 0, !luminanceValues.isEmpty else { return baseSettings }
        
        luminanceValues.sort()
        let medianL = luminanceValues[luminanceValues.count / 2]
        let p5 = luminanceValues[Int(Double(luminanceValues.count) * 0.05)]
        let p95 = luminanceValues[Int(Double(luminanceValues.count) * 0.95)]
        
        let avgR = sumR / count
        let avgB = sumB / count
        let avgSat = sumSat / count
        
        var adjusted = baseSettings
        
        // 1. Exposure: target median luminance around 120 (pleasing midtone)
        let targetMedian: Double = 120.0
        let expDiff = (targetMedian - medianL) / 48.0
        adjusted.exposure = round(max(-1.8, min(1.8, expDiff)) * 10) / 10
        
        // 2. Highlights & Shadows (Dynamic range optimization)
        if p95 > 240.0 {
            adjusted.highlight = -1.0 // Recover blown-out skies/highlights
        } else if p95 < 180.0 && medianL < 110.0 {
            adjusted.highlight = 0.5 // Brighten flat highlights
        } else {
            adjusted.highlight = 0.0
        }
        
        if p5 < 20.0 && medianL < 130.0 {
            adjusted.shadow = -1.0 // Negative shadow values lift blacks in ColorEngine.
        } else if p5 > 50.0 {
            adjusted.shadow = 0.5 // Positive shadow values deepen washed-out shadows.
        } else {
            adjusted.shadow = 0.0
        }
        
        // 3. White Balance / Temperature
        let rbDelta = avgR - avgB
        if rbDelta > 14.0 {
            let tempShift = -min(30.0, (rbDelta - 14.0) * 0.8)
            adjusted.temperature = round(tempShift)
        } else if rbDelta < -14.0 {
            let tempShift = min(30.0, (-rbDelta - 14.0) * 0.8)
            adjusted.temperature = round(tempShift)
        } else {
            adjusted.temperature = 0.0
        }
        
        // 4. Color / Saturation
        if avgSat < 0.22 {
            adjusted.color = 1.0
        } else if avgSat > 0.55 {
            adjusted.color = 0.0
        } else {
            adjusted.color = 0.0
        }
        
        // 5. Clarity & Sharpness: subtle crispness (+1.0)
        adjusted.clarity = 1.0
        adjusted.sharpness = 1.0
        
        return adjusted
    }
}
