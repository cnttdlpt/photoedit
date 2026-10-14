import Foundation

/// Checks the rendered result, because EV, DR and tone controls can compound.
/// Used only by natural Auto; manual edits and film looks remain unrestricted.
public enum AutoBalanceGuard {
    public static func protect(_ image: RasterImage, proposed: Settings) throws -> Settings {
        try Task.checkCancellation()
        _ = try proposed.validated()
        guard image.pixels.count == image.width * image.height * 4 else {
            throw CoreError.invalidValue("Kích thước ảnh không hợp lệ")
        }
        let count = min(8_192, image.width * image.height)
        var pixels: [UInt8] = []
        pixels.reserveCapacity(count * 4)
        for i in 0..<count {
            let offset = (i * (image.width * image.height) / count) * 4
            if image.pixels[offset + 3] >= 16 {
                pixels.append(contentsOf: image.pixels[offset..<(offset + 4)])
            }
        }
        guard !pixels.isEmpty else { return Settings() }
        let sample = try RasterImage(width: pixels.count / 4, height: 1, pixels: pixels)
        let reference = Meter(source: sample)

        // Natural Auto must not lower global exposure to turn a bright scene gray.
        // Highlight recovery is still allowed, provided the subject stays bright.
        var candidate = proposed
        candidate.exposure = max(0, candidate.exposure)
        candidate.shadow = min(0, candidate.shadow)
        candidate.grain = 0
        candidate.nr = -4
        candidate.sharpness = 0
        candidate.clarity = 0
        for amount in [1.0, 0.75, 0.5, 0.25, 0.125, 0.0] {
            try Task.checkCancellation()
            var settings = attenuated(candidate, amount: amount)
            var assessment = reference.assess(try ColorEngine.render(sample, settings: settings))
            if !assessment.brightnessOK {
                // Compensate tone compression with the smallest exposure change.
                // If that clips highlights, reduce the proposed look and try again.
                var lower = settings.exposure
                var upper = min(1.5, lower + 0.6)
                var brightened = settings
                brightened.exposure = upper
                if reference.assess(try ColorEngine.render(sample, settings: brightened)).brightnessOK {
                    for _ in 0..<10 {
                        let middle = (lower + upper) / 2
                        brightened.exposure = middle
                        if reference.assess(try ColorEngine.render(sample, settings: brightened)).brightnessOK {
                            upper = middle
                        } else {
                            lower = middle
                        }
                    }
                    settings.exposure = upper
                    assessment = reference.assess(try ColorEngine.render(sample, settings: settings))
                }
            }
            if assessment.brightnessOK && assessment.highlightsOK { return settings }
        }
        // Identity is preferable to an AI suggestion that makes the photo worse.
        return Settings()
    }

    private static func attenuated(_ input: Settings, amount: Double) -> Settings {
        var s = input
        s.exposure *= amount
        s.highlight *= amount
        s.shadow *= amount
        s.temperature *= amount
        s.red *= amount
        s.blue *= amount
        s.color *= amount
        s.colorChrome *= amount
        s.chromeBlue *= amount
        if amount < 0.75 { s.dr = 100 }
        return s
    }

    private struct Meter {
        let source: RasterImage
        let luminances: [Double]
        let alpha: [Double]
        let hasBrightHighlights: Bool

        init(source: RasterImage) {
            self.source = source
            luminances = stride(from: 0, to: source.pixels.count, by: 4).map {
                Self.luminance(source.pixels, at: $0)
            }
            alpha = stride(from: 3, to: source.pixels.count, by: 4).map { Double(source.pixels[$0]) / 255 }
            let total = alpha.reduce(0, +)
            let highlights = zip(luminances, alpha).reduce(0) { $0 + ($1.0 >= 245 ? $1.1 : 0) }
            hasBrightHighlights = highlights >= total * 0.10
        }

        func assess(_ rendered: RasterImage) -> (brightnessOK: Bool, highlightsOK: Bool) {
            // Bands are selected from the source, never from the adjusted image:
            // moving a darkened face into another band cannot fool this check.
            var weights = [Double](repeating: 0, count: 5)
            var differences = weights
            var newClipping = 0.0
            var highlightGain = 0.0
            var highlightWeight = 0.0
            for i in luminances.indices {
                let offset = i * 4
                let before = luminances[i]
                let after = Self.luminance(rendered.pixels, at: offset)
                let weight = alpha[i]
                var groups = [1.0, 0.0, 0.0, 0.0, 0.0]
                if before >= 16 && before < 64 { groups[1] = 1 }
                if before >= 64 && before < 128 { groups[2] = 1 }
                if before >= 128 && before <= 220 { groups[3] = 1 }
                if before >= 45 && before <= 210 {
                    groups[4] = max(0, 1 - abs(before - 128) / 83)
                }
                for band in groups.indices {
                    weights[band] += weight * groups[band]
                    differences[band] += (after - before) * weight * groups[band]
                }
                let sourcePeak = max(source.pixels[offset], source.pixels[offset + 1], source.pixels[offset + 2])
                let outputPeak = max(rendered.pixels[offset], rendered.pixels[offset + 1], rendered.pixels[offset + 2])
                if sourcePeak < 254 && outputPeak >= 254 { newClipping += weight }
                if before >= 245 {
                    highlightGain += (after - before) * weight
                    highlightWeight += weight
                }
            }
            let brightnessOK = weights.indices.allSatisfy {
                ($0 == 0 && hasBrightHighlights) || weights[$0] == 0 || differences[$0] / weights[$0] >= -0.75
            }
            let highlightsOK = newClipping <= weights[0] * 0.01
                && (highlightWeight == 0 || highlightGain / highlightWeight <= 0.75)
            return (brightnessOK, highlightsOK)
        }

        private static func luminance(_ bytes: [UInt8], at offset: Int) -> Double {
            0.2126 * Double(bytes[offset]) + 0.7152 * Double(bytes[offset + 1]) + 0.0722 * Double(bytes[offset + 2])
        }
    }
}
