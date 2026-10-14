import Foundation

/// एक điểm điều khiển trên đường cong Tone Curve trong miền chuẩn hóa `[0.0 .. 1.0]`.
public struct CurvePoint: Codable, Equatable, Sendable {
    public var x: Float
    public var y: Float

    public init(x: Float, y: Float) {
        self.x = max(0, min(1, x))
        self.y = max(0, min(1, y))
    }
}

/// Một bánh xe màu trong hệ thống 3-Way Color Grading (Shadows / Midtones / Highlights).
public struct GradingWheel: Codable, Equatable, Sendable {
    /// Góc màu Hue (`0..360` độ).
    public var hue: Float = 0
    /// Độ bão hòa Saturation (`0..100%`).
    public var sat: Float = 0
    /// Độ sáng vùng Luminance (`-100..+100`).
    public var lum: Float = 0

    public init(hue: Float = 0, sat: Float = 0, lum: Float = 0) {
        self.hue = hue
        self.sat = sat
        self.lum = lum
    }

    public mutating func reset() {
        hue = 0
        sat = 0
        lum = 0
    }

    public var isNeutral: Bool {
        abs(sat) < 0.5 && abs(lum) < 0.5
    }
}

/// Chế độ Mặt nạ cục bộ (Local Masking) từ `crates/pipeline/src/masks.rs`.
public enum MaskMode: String, Codable, CaseIterable, Identifiable, Equatable, Sendable {
    case none = "NONE"
    case radial = "RADIAL"
    case linear = "LINEAR"
    case sky = "SKY"
    case subject = "SUBJECT"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .none: return "Tắt Mask"
        case .radial: return "Radial (Elip)"
        case .linear: return "Linear (Chuyển sắc)"
        case .sky: return "AI Bầu trời"
        case .subject: return "AI Chủ thể"
        }
    }
}

/// Toàn bộ thông số chỉnh sửa chuyên sâu của LightCraft gắn với phiên làm việc hiện tại.
public struct LightCraftState: Codable, Equatable, Sendable {
    // 1. Tone Curve (Đường cong Điểm 4 kênh + Đường cong Tham số 4 vùng)
    public var masterCurve: [CurvePoint] = Self.defaultCurvePoints
    public var redCurve: [CurvePoint] = Self.defaultCurvePoints
    public var greenCurve: [CurvePoint] = Self.defaultCurvePoints
    public var blueCurve: [CurvePoint] = Self.defaultCurvePoints
    public var paramShadows: Float = 0    // -100..+100
    public var paramDarks: Float = 0      // -100..+100
    public var paramLights: Float = 0     // -100..+100
    public var paramHighlights: Float = 0 // -100..+100

    // 2. 3-Way Color Grading Wheels (Shadows, Midtones, Highlights trong không gian OkLab)
    public var shadowsWheel = GradingWheel()
    public var midtonesWheel = GradingWheel()
    public var highlightsWheel = GradingWheel()
    public var gradingBlending: Float = 50 // 0..100 (mặc định 50 giống LightCraft)
    public var gradingBalance: Float = 0   // -100..+100

    // 3. 8-Band Color Mixer (Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta)
    public var mixerHue: [Float] = Array(repeating: 0, count: 8)
    public var mixerSat: [Float] = Array(repeating: 0, count: 8)
    public var mixerLum: [Float] = Array(repeating: 0, count: 8)

    // 4. Local Masking (Radial, Linear, Sky, Subject)
    public var maskMode: MaskMode = .none
    public var showMaskOverlay: Bool = false
    public var maskInvert: Bool = false
    // Tọa độ chuẩn hóa (0..1) cho Radial Mask
    public var radialCenterX: Float = 0.5
    public var radialCenterY: Float = 0.45
    public var radialRx: Float = 0.30
    public var radialRy: Float = 0.24
    public var radialFeather: Float = 65 // 0..100
    // Tọa độ chuẩn hóa (0..1) cho Linear Gradient Mask
    public var linearStartX: Float = 0.5
    public var linearStartY: Float = 0.18
    public var linearEndX: Float = 0.5
    public var linearEndY: Float = 0.58
    // Thông số điều chỉnh cục bộ bên trong Mask
    public var maskExposure: Float = 0   // -2.0..+2.0 EV
    public var maskTemp: Float = 0       // -100..+100
    public var maskClarity: Float = 0    // -100..+100
    public var maskSaturation: Float = 0 // -100..+100

    // Preset LightCraft đang chọn (nếu có)
    public var activePresetId: String = ""

    public init() {}

    public static var defaultCurvePoints: [CurvePoint] {
        [CurvePoint(x: 0, y: 0), CurvePoint(x: 1, y: 1)]
    }

    public static func isCurveIdentity(_ pts: [CurvePoint]) -> Bool {
        guard pts.count >= 2 else { return true }
        // A diagonal curve with moved endpoints still clamps the black/white
        // points. Adding a third control point must not silently disable it.
        guard let first = pts.min(by: { $0.x < $1.x }),
              let last = pts.max(by: { $0.x < $1.x }),
              abs(first.x) < 1e-3, abs(first.y) < 1e-3,
              abs(last.x - 1) < 1e-3, abs(last.y - 1) < 1e-3 else { return false }
        for p in pts where abs(p.x - p.y) > 1e-3 {
            return false
        }
        return true
    }

    public var hasCurveEdits: Bool {
        abs(paramShadows) > 0.5
            || abs(paramDarks) > 0.5
            || abs(paramLights) > 0.5
            || abs(paramHighlights) > 0.5
            || !Self.isCurveIdentity(masterCurve)
            || !Self.isCurveIdentity(redCurve)
            || !Self.isCurveIdentity(greenCurve)
            || !Self.isCurveIdentity(blueCurve)
    }

    public var hasGradingEdits: Bool {
        !shadowsWheel.isNeutral || !midtonesWheel.isNeutral || !highlightsWheel.isNeutral
    }

    public var hasMixerEdits: Bool {
        for i in 0..<8 {
            if i < mixerHue.count && abs(mixerHue[i]) > 0.5 { return true }
            if i < mixerSat.count && abs(mixerSat[i]) > 0.5 { return true }
            if i < mixerLum.count && abs(mixerLum[i]) > 0.5 { return true }
        }
        return false
    }

    public var hasMaskEdits: Bool {
        guard maskMode != .none else { return false }
        return showMaskOverlay
            || abs(maskExposure) > 0.02
            || abs(maskTemp) > 0.5
            || abs(maskClarity) > 0.5
            || abs(maskSaturation) > 0.5
    }

    public var isIdentity: Bool {
        !hasCurveEdits && !hasGradingEdits && !hasMixerEdits && !hasMaskEdits
    }

    public mutating func resetCurves() {
        masterCurve = Self.defaultCurvePoints
        redCurve = Self.defaultCurvePoints
        greenCurve = Self.defaultCurvePoints
        blueCurve = Self.defaultCurvePoints
        paramShadows = 0
        paramDarks = 0
        paramLights = 0
        paramHighlights = 0
    }

    public mutating func resetGrading() {
        shadowsWheel.reset()
        midtonesWheel.reset()
        highlightsWheel.reset()
        gradingBlending = 50
        gradingBalance = 0
    }

    public mutating func resetMixer() {
        mixerHue = Array(repeating: 0, count: 8)
        mixerSat = Array(repeating: 0, count: 8)
        mixerLum = Array(repeating: 0, count: 8)
    }

    public mutating func resetMask() {
        maskMode = .none
        showMaskOverlay = false
        maskInvert = false
        radialCenterX = 0.5
        radialCenterY = 0.45
        radialRx = 0.30
        radialRy = 0.24
        radialFeather = 65
        linearStartX = 0.5
        linearStartY = 0.18
        linearEndX = 0.5
        linearEndY = 0.58
        maskExposure = 0
        maskTemp = 0
        maskClarity = 0
        maskSaturation = 0
    }

    public mutating func resetAll() {
        resetCurves()
        resetGrading()
        resetMixer()
        resetMask()
        activePresetId = ""
    }
}

/// Dữ liệu biểu đồ tần suất màu RGB 64 cột (Live RGB Waveform & Histogram).
public struct RgbHistogramData: Equatable, Sendable {
    public static let bins = 64
    public var r: [Float]
    public var g: [Float]
    public var b: [Float]
    public var avgR: Int
    public var avgG: Int
    public var avgB: Int

    public init(
        r: [Float] = Array(repeating: 0, count: bins),
        g: [Float] = Array(repeating: 0, count: bins),
        b: [Float] = Array(repeating: 0, count: bins),
        avgR: Int = 128,
        avgG: Int = 128,
        avgB: Int = 128
    ) {
        self.r = r
        self.g = g
        self.b = b
        self.avgR = avgR
        self.avgG = avgG
        self.avgB = avgB
    }

    public static func analyze(_ image: RasterImage) -> RgbHistogramData {
        let w = image.width, h = image.height
        guard w > 0, h > 0, image.pixels.count == w * h * 4 else { return RgbHistogramData() }

        var rawR = [Int](repeating: 0, count: bins)
        var rawG = [Int](repeating: 0, count: bins)
        var rawB = [Int](repeating: 0, count: bins)

        let stepX = max(1, w / 64)
        let stepY = max(1, h / 64)
        var sumR = 0
        var sumG = 0
        var sumB = 0
        var count = 0

        image.pixels.withUnsafeBufferPointer { buf in
            guard let ptr = buf.baseAddress else { return }
            var y = 0
            while y < h {
                let rowOffset = y * w
                var x = 0
                while x < w {
                    let idx = (rowOffset + x) * 4
                    let a = Int(ptr[idx + 3])
                    if a >= 16 {
                        let rv = Int(ptr[idx])
                        let gv = Int(ptr[idx + 1])
                        let bv = Int(ptr[idx + 2])
                        rawR[rv >> 2] += 1
                        rawG[gv >> 2] += 1
                        rawB[bv >> 2] += 1
                        sumR += rv
                        sumG += gv
                        sumB += bv
                        count += 1
                    }
                    x += stepX
                }
                y += stepY
            }
        }

        guard count > 0 else { return RgbHistogramData() }

        var targetR = [Float](repeating: 0, count: bins)
        var targetG = [Float](repeating: 0, count: bins)
        var targetB = [Float](repeating: 0, count: bins)
        var maxVal: Float = 1

        for i in 0..<bins {
            let prev = max(0, i - 1)
            let next = min(bins - 1, i + 1)
            let sr = Float(rawR[prev] + rawR[i] * 2 + rawR[next]) * 0.25
            let sg = Float(rawG[prev] + rawG[i] * 2 + rawG[next]) * 0.25
            let sb = Float(rawB[prev] + rawB[i] * 2 + rawB[next]) * 0.25
            targetR[i] = sr
            targetG[i] = sg
            targetB[i] = sb
            if i > 1 && i < bins - 2 {
                maxVal = max(maxVal, max(sr, max(sg, sb)))
            }
        }

        for i in 0..<bins {
            targetR[i] = min(1, targetR[i] / maxVal)
            targetG[i] = min(1, targetG[i] / maxVal)
            targetB[i] = min(1, targetB[i] / maxVal)
        }

        return RgbHistogramData(
            r: targetR,
            g: targetG,
            b: targetB,
            avgR: sumR / count,
            avgG: sumG / count,
            avgB: sumB / count
        )
    }
}

/// Lõi xử lý hình ảnh & màu sắc chuyên sâu LightCraft Engine (`LightCraftEngine`).
/// Chuyển đổi nguyên bản từ `LightCraftEngine.java` và dự án `cnttdlpt/lightcraft`.
public enum LightCraftEngine {
    /// 8 tâm góc sắc độ (độ HSV) của bộ trộn màu 8 kênh.
    public static let mixerHuesDeg: [Float] = [0, 30, 60, 120, 180, 225, 275, 320]

    /// Tên tiếng Việt của 8 dải màu HSL.
    public static let mixerBandNames: [String] = [
        "Đỏ", "Cam", "Vàng", "Lục", "Lam ngọc", "Lam", "Tím", "Hồng"
    ]

    /// Mã màu hiển thị chuẩn của 8 dải màu HSL (`0xRRGGBB`).
    public static let mixerBandColorsHex: [UInt32] = [
        0xDD3333, // Red
        0xE67E22, // Orange
        0xD4AC0D, // Yellow
        0x27AE60, // Green
        0x16A085, // Aqua
        0x2980B9, // Blue
        0x8E44AD, // Purple
        0xC2185B  // Magenta
    ]

    /// Đường cong bậc ba đơn điệu Fritsch–Carlson (`MonotoneCurve`).
    public struct MonotoneCurve: Sendable {
        private let xs: [Double]
        private let ys: [Double]
        private let ms: [Double]

        public init(points: [CurvePoint]) {
            var clean: [(Double, Double)] = []
            for pt in points where pt.x.isFinite && pt.y.isFinite {
                clean.append((
                    max(0.0, min(1.0, Double(pt.x))),
                    max(0.0, min(1.0, Double(pt.y)))
                ))
            }
            clean.sort { $0.0 < $1.0 }

            var dedup: [(Double, Double)] = []
            for pt in clean {
                if let last = dedup.last {
                    if abs(pt.0 - last.0) >= 1e-6 {
                        dedup.append(pt)
                    }
                } else {
                    dedup.append(pt)
                }
            }
            if dedup.count < 2 {
                dedup = [(0.0, 0.0), (1.0, 1.0)]
            }

            let n = dedup.count
            var xs = [Double](repeating: 0, count: n)
            var ys = [Double](repeating: 0, count: n)
            var ms = [Double](repeating: 0, count: n)
            for i in 0..<n {
                xs[i] = dedup[i].0
                ys[i] = dedup[i].1
            }

            var d = [Double](repeating: 0, count: n - 1)
            for i in 0..<(n - 1) {
                d[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i])
            }

            ms[0] = d[0]
            ms[n - 1] = d[n - 2]
            if n > 2 {
                for i in 1..<(n - 1) {
                    ms[i] = (d[i - 1] * d[i] <= 0.0) ? 0.0 : (d[i - 1] + d[i]) * 0.5
                }
            }

            // Fritsch–Carlson limiter
            for i in 0..<(n - 1) {
                if d[i] == 0.0 {
                    ms[i] = 0.0
                    ms[i + 1] = 0.0
                    continue
                }
                let a = ms[i] / d[i]
                let b = ms[i + 1] / d[i]
                let s = a * a + b * b
                if s > 9.0 {
                    let t = 3.0 / s.squareRoot()
                    ms[i] = t * a * d[i]
                    ms[i + 1] = t * b * d[i]
                }
            }

            self.xs = xs
            self.ys = ys
            self.ms = ms
        }

        public func eval(_ x: Double) -> Double {
            let n = xs.count
            if x <= xs[0] { return ys[0] }
            if x >= xs[n - 1] { return ys[n - 1] }

            var lo = 0
            var hi = n - 1
            while lo <= hi {
                let mid = (lo + hi) >> 1
                let v = xs[mid]
                if v < x {
                    lo = mid + 1
                } else if v > x {
                    hi = mid - 1
                } else {
                    return ys[mid]
                }
            }
            let i = max(0, lo - 1)
            let h = xs[i + 1] - xs[i]
            let t = (x - xs[i]) / h
            let t2 = t * t
            let t3 = t2 * t
            let out = (2.0 * t3 - 3.0 * t2 + 1.0) * ys[i]
                + (t3 - 2.0 * t2 + t) * h * ms[i]
                + (-2.0 * t3 + 3.0 * t2) * ys[i + 1]
                + (t3 - t2) * h * ms[i + 1]
            return max(0.0, min(1.0, out))
        }

        public func toLut(_ n: Int) -> [Float] {
            guard n > 1 else { return [0] }
            var lut = [Float](repeating: 0, count: n)
            for i in 0..<n {
                lut[i] = Float(eval(Double(i) / Double(n - 1)))
            }
            return lut
        }
    }

    /// Định nghĩa một Preset phòng tối tích hợp sẵn của LightCraft.
    public struct BuiltinPreset: Identifiable, Sendable {
        public let id: String
        public let group: String
        public let name: String
        public let subtitle: String
        public let badgeColorHex: UInt32
        private let applier: @Sendable (inout LightCraftState, inout Settings) -> Void

        public init(
            id: String,
            group: String,
            name: String,
            subtitle: String,
            badgeColorHex: UInt32,
            applier: @escaping @Sendable (inout LightCraftState, inout Settings) -> Void
        ) {
            self.id = id
            self.group = group
            self.name = name
            self.subtitle = subtitle
            self.badgeColorHex = badgeColorHex
            self.applier = applier
        }

        public func apply(to lc: inout LightCraftState, settings: inout Settings) {
            lc.resetCurves()
            lc.resetGrading()
            lc.resetMixer()
            lc.activePresetId = id
            applier(&lc, &settings)
        }
    }

    /// Danh sách 12 Preset đặc trưng chuyển thể từ `LightCraftEngine.java`.
    public static let presets: [BuiltinPreset] = [
        BuiltinPreset(id: "lc.teal-orange", group: "Color", name: "Teal & Orange", subtitle: "Shadows 195° · Highlights 35°", badgeColorHex: 0x1A73E8) { lc, s in
            lc.shadowsWheel.hue = 195
            lc.shadowsWheel.sat = 28
            lc.highlightsWheel.hue = 35
            lc.highlightsWheel.sat = 22
            lc.gradingBalance = 10
            lc.paramDarks = -12
            lc.paramLights = 12
            s.color = 1.2
        },
        BuiltinPreset(id: "lc.golden-hour", group: "Landscape", name: "Golden Hour", subtitle: "Nắng vàng ấm · Highlights 38°", badgeColorHex: 0xF29900) { lc, s in
            lc.highlightsWheel.hue = 38
            lc.highlightsWheel.sat = 26
            lc.highlightsWheel.lum = 6
            lc.paramHighlights = -25
            lc.paramShadows = 18
            s.red = 2.0
            s.blue = -2.0
            s.highlight = -1.5
            s.shadow = -1.0
        },
        BuiltinPreset(id: "lc.cinematic", group: "Style", name: "Cinematic", subtitle: "Màn ảnh rộng · S-Curve & Split Grade", badgeColorHex: 0xEA4335) { lc, s in
            lc.paramShadows = 20
            lc.paramDarks = -14
            lc.paramLights = 14
            lc.paramHighlights = -12
            lc.shadowsWheel.hue = 190
            lc.shadowsWheel.sat = 25
            lc.highlightsWheel.hue = 30
            lc.highlightsWheel.sat = 18
            s.clarity = 1.5
        },
        BuiltinPreset(id: "lc.faded-matte", group: "Film", name: "Faded Matte", subtitle: "Mờ sương cổ điển · Nâng vùng tối", badgeColorHex: 0x8E44AD) { lc, s in
            lc.paramShadows = 40
            lc.paramHighlights = -15
            lc.masterCurve = [
                CurvePoint(x: 0.0, y: 0.09),
                CurvePoint(x: 0.25, y: 0.24),
                CurvePoint(x: 0.75, y: 0.78),
                CurvePoint(x: 1.0, y: 0.94)
            ]
            s.color = -1.2
            s.grain = 8.0
        },
        BuiltinPreset(id: "lc.warm-film", group: "Film", name: "Warm Film", subtitle: "Phim nhựa ấm · Hạt mịn", badgeColorHex: 0xE67E22) { lc, s in
            lc.paramShadows = 22
            lc.highlightsWheel.hue = 45
            lc.highlightsWheel.sat = 16
            s.red = 2.0
            s.grain = 10.0
        },
        BuiltinPreset(id: "lc.crisp-landscape", group: "Landscape", name: "Crisp Landscape", subtitle: "Phong cảnh sắc nét · Tách mây", badgeColorHex: 0x34A853) { lc, s in
            lc.paramHighlights = -35
            lc.paramShadows = 24
            lc.mixerSat[3] = 18 // Lục
            lc.mixerSat[5] = 22 // Lam
            lc.mixerLum[5] = -14
            s.clarity = 2.2
            s.color = 2.0
        },
        BuiltinPreset(id: "lc.blue-hour", group: "Landscape", name: "Blue Hour Boost", subtitle: "Chạng vạng xanh thẫm", badgeColorHex: 0x2980B9) { lc, s in
            lc.mixerSat[5] = 28 // Blue
            lc.mixerSat[6] = 18 // Purple
            lc.shadowsWheel.hue = 220
            lc.shadowsWheel.sat = 16
            s.blue = 2.0
            s.shadow = -1.5
        },
        BuiltinPreset(id: "lc.soft-skin", group: "Portrait", name: "Soft Skin", subtitle: "Chân dung da mịn màng", badgeColorHex: 0xC2185B) { lc, s in
            lc.mixerSat[1] = -10 // Orange
            lc.mixerLum[1] = 14  // Làm sáng nhẹ tông da cam
            lc.paramShadows = 14
            s.clarity = -1.2
            s.shadow = -1.0
        },
        BuiltinPreset(id: "lc.bright-airy", group: "Portrait", name: "Bright & Airy", subtitle: "Trong trẻo & Thoáng sáng", badgeColorHex: 0x16A085) { lc, s in
            lc.paramShadows = 32
            lc.paramLights = 16
            lc.paramHighlights = -25
            s.exposure = 0.4
            s.color = -0.6
        },
        BuiltinPreset(id: "lc.cool-morning", group: "Color", name: "Cool Morning", subtitle: "Sương sớm lạnh · Shadows 215°", badgeColorHex: 0x3B6FE0) { lc, s in
            lc.shadowsWheel.hue = 215
            lc.shadowsWheel.sat = 15
            lc.paramHighlights = -20
            s.blue = 1.0
        },
        BuiltinPreset(id: "lc.selenium", group: "B&W", name: "Selenium Tone", subtitle: "Đơn sắc ánh tím Selen", badgeColorHex: 0x6E7480) { lc, s in
            lc.shadowsWheel.hue = 285
            lc.shadowsWheel.sat = 20
            lc.highlightsWheel.hue = 40
            lc.highlightsWheel.sat = 10
            s.color = -4.0
            s.clarity = 2.0
        },
        BuiltinPreset(id: "lc.moody", group: "Style", name: "Moody Darkroom", subtitle: "Trầm mặc · Tương phản chiều sâu", badgeColorHex: 0x1B1C1E) { lc, s in
            lc.shadowsWheel.hue = 200
            lc.shadowsWheel.sat = 16
            lc.shadowsWheel.lum = -8
            lc.paramHighlights = -28
            lc.paramDarks = -16
            s.exposure = -0.25
            s.color = -1.8
        }
    ]

    // MARK: - Tone Curve LUTs

    /// Xây dựng 4 bảng tra cứu LUT (`[rLut, gLut, bLut, baseLut]`) cho Tone Curve.
    public static func buildToneCurveLuts(_ lc: LightCraftState, lutSize: Int = 256) -> [[Float]] {
        var base = [Float](repeating: 0, count: lutSize)
        let s1: Float = 0.25, s2: Float = 0.50, s3: Float = 0.75
        let regions: [(Float, Float, Float)] = [
            (0.0, s1, lc.paramShadows),
            (s1, s2, lc.paramDarks),
            (s2, s3, lc.paramLights),
            (s3, 1.0, lc.paramHighlights)
        ]

        for i in 0..<lutSize {
            let x = Float(i) / Float(lutSize - 1)
            var d: Float = 0
            for reg in regions {
                let amt = reg.2
                if abs(amt) < 1e-4 { continue }
                let a = reg.0, b = reg.1
                let ctr = (a + b) * 0.5
                let half = (b - a) * 0.75 + 0.05
                let t = clamp((x - ctr) / half, -1.0, 1.0)
                let win = 0.5 + 0.5 * Float(cos(Double(t) * .pi))
                d += (amt / 100.0) * 0.22 * win
            }
            base[i] = clamp(x + d * 4.0 * x * (1.0 - x), 0.0, 1.0)
        }

        for i in 1..<lutSize where base[i] < base[i - 1] {
            base[i] = base[i - 1]
        }

        if !LightCraftState.isCurveIdentity(lc.masterCurve) {
            let masterLut = MonotoneCurve(points: lc.masterCurve).toLut(lutSize)
            base = composeLut(outer: masterLut, inner: base)
        }

        let rLut = !LightCraftState.isCurveIdentity(lc.redCurve)
            ? composeLut(outer: MonotoneCurve(points: lc.redCurve).toLut(lutSize), inner: base)
            : base
        let gLut = !LightCraftState.isCurveIdentity(lc.greenCurve)
            ? composeLut(outer: MonotoneCurve(points: lc.greenCurve).toLut(lutSize), inner: base)
            : base
        let bLut = !LightCraftState.isCurveIdentity(lc.blueCurve)
            ? composeLut(outer: MonotoneCurve(points: lc.blueCurve).toLut(lutSize), inner: base)
            : base

        return [rLut, gLut, bLut, base]
    }

    private static func composeLut(outer: [Float], inner: [Float]) -> [Float] {
        let n = inner.count
        var out = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let x = clamp(inner[i], 0, 1) * Float(n - 1)
            let idx = min(n - 2, Int(x))
            let t = x - Float(idx)
            out[i] = outer[idx] + (outer[idx + 1] - outer[idx]) * t
        }
        return out
    }

    // MARK: - OkLab / OkLCh Perceptual Color Space

    private static func srgbLinearToOklab(_ r: Float, _ g: Float, _ b: Float) -> (Float, Float, Float) {
        let l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        let m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        let s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        let l_ = Float(cbrt(Double(max(0, l))))
        let m_ = Float(cbrt(Double(max(0, m))))
        let s_ = Float(cbrt(Double(max(0, s))))

        let L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
        let A = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
        let B = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
        return (L, A, B)
    }

    private static func oklabToSrgbLinear(_ L: Float, _ a: Float, _ b: Float) -> (Float, Float, Float) {
        let l_ = L + 0.3963377774 * a + 0.2158037573 * b
        let m_ = L - 0.1055613458 * a - 0.0638541728 * b
        let s_ = L - 0.0894841775 * a - 1.2914855480 * b

        let l = l_ * l_ * l_
        let m = m_ * m_ * m_
        let s = s_ * s_ * s_

        let r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        let g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        let bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return (r, g, bl)
    }

    public static func hsvToRgb(_ hDeg: Float, _ s: Float, _ v: Float) -> (Float, Float, Float) {
        let c = v * s
        let hp = ((hDeg.truncatingRemainder(dividingBy: 360)) + 360).truncatingRemainder(dividingBy: 360) / 60
        let x = c * (1 - abs(hp.truncatingRemainder(dividingBy: 2) - 1))
        let sector = Int(hp)
        let r: Float, g: Float, b: Float
        switch sector {
        case 0: (r, g, b) = (c, x, 0)
        case 1: (r, g, b) = (x, c, 0)
        case 2: (r, g, b) = (0, c, x)
        case 3: (r, g, b) = (0, x, c)
        case 4: (r, g, b) = (x, 0, c)
        default: (r, g, b) = (c, 0, x)
        }
        let m = v - c
        return (r + m, g + m, b + m)
    }

    private static func srgbToLinear(_ c: Float) -> Float {
        c <= 0.04045 ? c / 12.92 : Float(pow(Double((c + 0.055) / 1.055), 2.4))
    }

    private static func linearToSrgb(_ c: Float) -> Float {
        let v = max(0, c)
        return v <= 0.0031308 ? v * 12.92 : Float(1.055 * pow(Double(v), 1.0 / 2.4) - 0.055)
    }

    private static func oklchHueOfSrgbHue(_ deg: Float) -> Float {
        let (r, g, b) = hsvToRgb(deg, 1, 1)
        let (_, labA, labB) = srgbLinearToOklab(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
        return atan2(labB, labA)
    }

    private static let bandOklchHues: [Float] = mixerHuesDeg.map { oklchHueOfSrgbHue($0) }

    private static func wrapRad(_ a: Float) -> Float {
        let tau = Float.pi * 2
        var r = (a + .pi).truncatingRemainder(dividingBy: tau)
        if r < 0 { r += tau }
        return r - .pi
    }

    private static func modTau(_ a: Float) -> Float {
        let tau = Float.pi * 2
        let r = a.truncatingRemainder(dividingBy: tau)
        return r < 0 ? r + tau : r
    }

    private static func smoothstep(_ e0: Float, _ e1: Float, _ x: Float) -> Float {
        let t = clamp((x - e0) / (e1 - e0), 0, 1)
        return t * t * (3 - 2 * t)
    }

    private static func clamp(_ v: Float, _ lo: Float, _ hi: Float) -> Float {
        max(lo, min(hi, v))
    }

    private static func evalLut256(_ lut: [Float], _ x: Float) -> Float {
        let f = clamp(x, 0, 1) * 255
        let i = min(254, Int(f))
        let t = f - Float(i)
        return lut[i] + (lut[i + 1] - lut[i]) * t
    }

    // MARK: - Full Pipeline Render on RasterImage

    public static func render(
        _ image: RasterImage,
        state lc: LightCraftState,
        includeMaskOverlay: Bool = true
    ) throws -> RasterImage {
        let w = image.width, h = image.height
        guard w > 0, h > 0, image.pixels.count == w * h * 4 else {
            throw CoreError.invalidValue("Kích thước ảnh không hợp lệ")
        }
        try Task.checkCancellation()
        let showOverlay = includeMaskOverlay && lc.showMaskOverlay && lc.maskMode != .none
        if lc.isIdentity && !showOverlay {
            return image
        }

        let hasCurves = lc.hasCurveEdits
        let hasGrading = lc.hasGradingEdits
        let hasMixer = lc.hasMixerEdits
        let hasMask = lc.hasMaskEdits || showOverlay

        let curveLuts: [[Float]]? = hasCurves ? buildToneCurveLuts(lc, lutSize: 256) : nil

        var shA: Float = 0, shB: Float = 0, shLum: Float = 0
        var midA: Float = 0, midB: Float = 0, midLum: Float = 0
        var hiA: Float = 0, hiB: Float = 0, hiLum: Float = 0
        let blendWidth: Float = 0.15 + (lc.gradingBlending / 100) * 0.5
        let balanceMid: Float = 0.5 - (lc.gradingBalance / 100) * 0.25
        if hasGrading {
            let hSh = oklchHueOfSrgbHue(lc.shadowsWheel.hue)
            let sSh = (lc.shadowsWheel.sat / 100) * 0.09
            shA = sSh * cos(hSh)
            shB = sSh * sin(hSh)
            shLum = (lc.shadowsWheel.lum / 100) * 0.12

            let hMid = oklchHueOfSrgbHue(lc.midtonesWheel.hue)
            let sMid = (lc.midtonesWheel.sat / 100) * 0.09
            midA = sMid * cos(hMid)
            midB = sMid * sin(hMid)
            midLum = (lc.midtonesWheel.lum / 100) * 0.12

            let hHi = oklchHueOfSrgbHue(lc.highlightsWheel.hue)
            let sHi = (lc.highlightsWheel.sat / 100) * 0.09
            hiA = sHi * cos(hHi)
            hiB = sHi * sin(hHi)
            hiLum = (lc.highlightsWheel.lum / 100) * 0.12
        }

        var mixHue = [Float](repeating: 0, count: 8)
        var mixSat = [Float](repeating: 0, count: 8)
        var mixLum = [Float](repeating: 0, count: 8)
        if hasMixer {
            for i in 0..<8 {
                mixHue[i] = (i < lc.mixerHue.count ? lc.mixerHue[i] : 0) / 100 * 0.5
                mixSat[i] = (i < lc.mixerSat.count ? lc.mixerSat[i] : 0) / 100
                mixLum[i] = (i < lc.mixerLum.count ? lc.mixerLum[i] : 0) / 100 * 0.18
            }
        }

        let maskExpGain = Float(pow(2.0, Double(lc.maskExposure)))
        let maskTempShift = lc.maskTemp / 100
        let maskClarShift = lc.maskClarity / 100
        let maskSatShift = lc.maskSaturation / 100
        let ldx = lc.linearEndX - lc.linearStartX
        let ldy = lc.linearEndY - lc.linearStartY
        let lLen2 = max(1e-6, ldx * ldx + ldy * ldy)
        let radFeather = clamp(lc.radialFeather / 100, 0.05, 1.0)
        let rx = max(0.02, lc.radialRx)
        let ry = max(0.02, lc.radialRy)

        let invW: Float = 1.0 / Float(max(1, w - 1))
        let invH: Float = 1.0 / Float(max(1, h - 1))
        let bandHues = bandOklchHues

        let pixels = image.pixels
        var output = pixels

        try pixels.withUnsafeBufferPointer { inBuf in
            try output.withUnsafeMutableBufferPointer { outBuf in
                guard let inPtr = inBuf.baseAddress, let outPtr = outBuf.baseAddress else { return }
                try ColorEngine.forEachPixelBatch(count: w * h) { range in
                    for pixel in range {
                        let y = pixel / w
                        let x = pixel % w
                        let nx = Float(x) * invW
                        let ny = Float(y) * invH
                        let offset = pixel * 4

                        var sr = Float(inPtr[offset]) / 255.0
                        var sg = Float(inPtr[offset + 1]) / 255.0
                        var sb = Float(inPtr[offset + 2]) / 255.0

                        var maskAlpha: Float = 0
                        if hasMask {
                            switch lc.maskMode {
                            case .radial:
                                let dx = (nx - lc.radialCenterX) / rx
                                let dy = (ny - lc.radialCenterY) / ry
                                let dist = (dx * dx + dy * dy).squareRoot()
                                maskAlpha = 1.0 - smoothstep(1.0 - radFeather, 1.0, dist)
                            case .linear:
                                let t = ((nx - lc.linearStartX) * ldx + (ny - lc.linearStartY) * ldy) / lLen2
                                maskAlpha = 1.0 - smoothstep(0.0, 1.0, t)
                            case .sky:
                                let luma = 0.2126 * sr + 0.7152 * sg + 0.0722 * sb
                                let blueness = max(0, sb - sr) / (sb + 1e-4)
                                let topWeight = 1.0 - smoothstep(0.22, 0.68, ny)
                                maskAlpha = topWeight * smoothstep(0.20, 0.55, luma)
                                    * (0.4 + 0.6 * max(smoothstep(0.0, 0.28, blueness), smoothstep(0.55, 0.90, luma)))
                            case .subject:
                                let dx = (nx - 0.5) / 0.36
                                let dy = (ny - 0.52) / 0.40
                                let d = (dx * dx + dy * dy).squareRoot()
                                maskAlpha = 1.0 - smoothstep(0.55, 1.05, d)
                            case .none:
                                break
                            }
                            if lc.maskInvert {
                                maskAlpha = 1.0 - maskAlpha
                            }
                            maskAlpha = clamp(maskAlpha, 0, 1)

                            if maskAlpha > 1e-3 {
                                let gain = 1.0 + maskAlpha * (maskExpGain - 1.0)
                                sr = clamp(sr * gain, 0, 1)
                                sg = clamp(sg * gain, 0, 1)
                                sb = clamp(sb * gain, 0, 1)

                                if abs(maskTempShift) > 1e-3 {
                                    sr = clamp(sr + maskAlpha * maskTempShift * 0.14, 0, 1)
                                    sb = clamp(sb - maskAlpha * maskTempShift * 0.14, 0, 1)
                                }
                                if abs(maskClarShift) > 1e-3 {
                                    let luma = 0.299 * sr + 0.587 * sg + 0.114 * sb
                                    let midFactor = 4.0 * luma * (1.0 - luma)
                                    let cDelta = maskAlpha * maskClarShift * 0.28 * (luma - 0.5) * midFactor
                                    sr = clamp(sr + cDelta, 0, 1)
                                    sg = clamp(sg + cDelta, 0, 1)
                                    sb = clamp(sb + cDelta, 0, 1)
                                }
                            }
                        }

                        let localSat = hasMask ? (maskAlpha * maskSatShift) : 0
                        if hasMixer || hasGrading || abs(localSat) > 1e-3 {
                            let lr = srgbToLinear(sr)
                            let lg = srgbToLinear(sg)
                            let lb = srgbToLinear(sb)
                            var (labL, labA, labB) = srgbLinearToOklab(lr, lg, lb)

                            if hasMixer || abs(localSat) > 1e-3 {
                                var L = labL
                                var C = hypot(labA, labB)
                                var H = atan2(labB, labA)

                                if hasMixer && C > 1e-4 {
                                    var dh: Float = 0, ds: Float = 0, dl: Float = 0
                                    for i in 0..<8 {
                                        let a = bandHues[i]
                                        let b = bandHues[(i + 1) % 8]
                                        let span = modTau(wrapRad(b - a))
                                        let d = modTau(wrapRad(H - a))
                                        if d <= span && span > 1e-5 {
                                            let t = d / span
                                            let s = 0.5 - 0.5 * Float(cos(Double(t) * .pi))
                                            let w0 = 1.0 - s
                                            let w1 = s
                                            let next = (i + 1) % 8
                                            dh = w0 * mixHue[i] + w1 * mixHue[next]
                                            ds = w0 * mixSat[i] + w1 * mixSat[next]
                                            dl = w0 * mixLum[i] + w1 * mixLum[next]
                                            break
                                        }
                                    }
                                    let chromaW = min(1.0, C / 0.12)
                                    H += dh * chromaW
                                    C = max(0, C * (1.0 + ds))
                                    L = clamp(L + dl * chromaW * max(0.05, L).squareRoot(), 0, 1)
                                }
                                if abs(localSat) > 1e-3 {
                                    C = max(0, C * (1.0 + localSat))
                                }
                                labL = L
                                labA = C * cos(H)
                                labB = C * sin(H)
                            }

                            if hasGrading {
                                let ws = 1.0 - smoothstep(balanceMid - blendWidth, balanceMid + blendWidth * 0.25, labL)
                                let wh = smoothstep(balanceMid - blendWidth * 0.25, balanceMid + blendWidth, labL)
                                let wm = max(0, 1.0 - ws - wh)

                                labA += shA * ws + midA * wm + hiA * wh
                                labB += shB * ws + midB * wm + hiB * wh
                                labL = clamp(labL + shLum * ws + midLum * wm + hiLum * wh, 0, 1)
                            }

                            let (linR, linG, linB) = oklabToSrgbLinear(labL, labA, labB)
                            sr = clamp(linearToSrgb(linR), 0, 1)
                            sg = clamp(linearToSrgb(linG), 0, 1)
                            sb = clamp(linearToSrgb(linB), 0, 1)
                        }

                        if let curveLuts {
                            sr = evalLut256(curveLuts[0], sr)
                            sg = evalLut256(curveLuts[1], sg)
                            sb = evalLut256(curveLuts[2], sb)
                        }

                        if showOverlay && maskAlpha > 0.01 {
                            let overlayStrength = maskAlpha * 0.48
                            sr = sr * (1 - overlayStrength) + 0.95 * overlayStrength
                            sg = sg * (1 - overlayStrength) + 0.18 * overlayStrength
                            sb = sb * (1 - overlayStrength) + 0.22 * overlayStrength
                        }

                        outPtr[offset]     = UInt8(min(255, max(0, Int((sr * 255).rounded()))))
                        outPtr[offset + 1] = UInt8(min(255, max(0, Int((sg * 255).rounded()))))
                        outPtr[offset + 2] = UInt8(min(255, max(0, Int((sb * 255).rounded()))))
                    }
                }
            }
        }

        try Task.checkCancellation()
        return try RasterImage(width: w, height: h, pixels: output)
    }
}
