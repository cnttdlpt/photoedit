import SwiftUI

// MARK: - 1. Live Interactive RGB Waveform & Histogram
struct RgbHistogramView: View {
    let data: RgbHistogramData
    var onChannelSelected: ((Int) -> Void)? = nil

    @State private var displayMode: Int = 0 // 0 = RGB, 1 = R, 2 = G, 3 = B

    private var modeLabel: String {
        switch displayMode {
        case 1: return "Kênh R"
        case 2: return "Kênh G"
        case 3: return "Kênh B"
        default: return "RGB"
        }
    }

    var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Studio.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Studio.line, lineWidth: 1)
                )

            Canvas { context, size in
                let graphTop: CGFloat = 22
                let graphBottom: CGFloat = max(graphTop + 10, size.height - 6)
                let graphLeft: CGFloat = 10
                let graphRight: CGFloat = max(graphLeft + 10, size.width - 10)
                let graphW = graphRight - graphLeft
                let graphH = graphBottom - graphTop

                // 4-part vertical grid lines
                for i in 1...3 {
                    let gx = graphLeft + graphW * CGFloat(i) / 4.0
                    var line = Path()
                    line.move(to: CGPoint(x: gx, y: graphTop))
                    line.addLine(to: CGPoint(x: gx, y: graphBottom))
                    context.stroke(line, with: .color(Studio.ink.opacity(0.08)), lineWidth: 1)
                }

                if displayMode == 0 || displayMode == 3 {
                    drawChannel(
                        context: &context,
                        values: data.b,
                        left: graphLeft,
                        top: graphTop,
                        width: graphW,
                        height: graphH,
                        color: Studio.googleBlue
                    )
                }
                if displayMode == 0 || displayMode == 2 {
                    drawChannel(
                        context: &context,
                        values: data.g,
                        left: graphLeft,
                        top: graphTop,
                        width: graphW,
                        height: graphH,
                        color: Studio.googleGreen
                    )
                }
                if displayMode == 0 || displayMode == 1 {
                    drawChannel(
                        context: &context,
                        values: data.r,
                        left: graphLeft,
                        top: graphTop,
                        width: graphW,
                        height: graphH,
                        color: Studio.googleRed
                    )
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            HStack {
                Text("Histogram · R \(data.avgR)  G \(data.avgG)  B \(data.avgB)")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Studio.muted)
                Spacer()
                Text(modeLabel)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Studio.ink)
            }
            .padding(.horizontal, 10)
            .padding(.top, 5)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            Haptics.selection()
            displayMode = (displayMode + 1) % 4
            onChannelSelected?(displayMode)
        }
        .animation(.easeInOut(duration: 0.18), value: data)
    }

    private func drawChannel(
        context: inout GraphicsContext,
        values: [Float],
        left: CGFloat,
        top: CGFloat,
        width: CGFloat,
        height: CGFloat,
        color: Color
    ) {
        let count = values.count
        guard count > 1 else { return }
        let bottom = top + height

        var curve = Path()
        var fill = Path()
        fill.move(to: CGPoint(x: left, y: bottom))

        for i in 0..<count {
            let x = left + width * CGFloat(i) / CGFloat(count - 1)
            let y = bottom - CGFloat(values[i]) * height * 0.92
            if i == 0 {
                curve.move(to: CGPoint(x: x, y: y))
                fill.addLine(to: CGPoint(x: x, y: y))
            } else {
                let prevX = left + width * CGFloat(i - 1) / CGFloat(count - 1)
                let prevY = bottom - CGFloat(values[i - 1]) * height * 0.92
                let midX = (prevX + x) * 0.5
                curve.addCurve(
                    to: CGPoint(x: x, y: y),
                    control1: CGPoint(x: midX, y: prevY),
                    control2: CGPoint(x: midX, y: y)
                )
                fill.addCurve(
                    to: CGPoint(x: x, y: y),
                    control1: CGPoint(x: midX, y: prevY),
                    control2: CGPoint(x: midX, y: y)
                )
            }
        }

        fill.addLine(to: CGPoint(x: left + width, y: bottom))
        fill.closeSubpath()

        context.fill(
            fill,
            with: .linearGradient(
                Gradient(colors: [color.opacity(0.16), color.opacity(0.02)]),
                startPoint: CGPoint(x: left, y: top),
                endPoint: CGPoint(x: left, y: bottom)
            )
        )
        context.stroke(curve, with: .color(color.opacity(0.72)), lineWidth: 1.8)
    }
}

// MARK: - 2. Tone Curve Graph View (Monotone Cubic Hermite Spline + 4-Zone Parametric)
enum ToneCurveChannel: String, CaseIterable, Identifiable {
    case parametric = "Tham số"
    case master = "RGB"
    case red = "Đỏ"
    case green = "Lục"
    case blue = "Lam"

    var id: String { rawValue }

    var color: Color {
        switch self {
        case .parametric, .master: return Studio.ink
        case .red: return Studio.googleRed
        case .green: return Studio.googleGreen
        case .blue: return Studio.googleBlue
        }
    }
}

struct ToneCurveGraphView: View {
    @Binding var state: LightCraftState
    let channel: ToneCurveChannel
    var onEditingBegan: () -> Void = {}
    var onCurveChanged: () -> Void = {}

    @State private var draggingPointIndex: Int? = nil
    @State private var lastTapTime: Date = .distantPast
    @State private var lastTapLocation: CGPoint = .zero
    @State private var didRecordDragUndo: Bool = false

    private var activePoints: [CurvePoint] {
        get {
            switch channel {
            case .red: return state.redCurve
            case .green: return state.greenCurve
            case .blue: return state.blueCurve
            default: return state.masterCurve
            }
        }
    }

    private func setActivePoints(_ pts: [CurvePoint]) {
        switch channel {
        case .red: state.redCurve = pts
        case .green: state.greenCurve = pts
        case .blue: state.blueCurve = pts
        default: state.masterCurve = pts
        }
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let pad: CGFloat = 10
            let plotRect = CGRect(x: pad, y: pad, width: max(1, w - pad * 2), height: max(1, h - pad * 2))

            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Studio.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(Studio.line, lineWidth: 1)
                    )

                Canvas { context, _ in
                    // 4x4 grid
                    for i in 1..<4 {
                        let f = CGFloat(i) / 4.0
                        let gx = plotRect.minX + plotRect.width * f
                        let gy = plotRect.minY + plotRect.height * f
                        var vLine = Path()
                        vLine.move(to: CGPoint(x: gx, y: plotRect.minY))
                        vLine.addLine(to: CGPoint(x: gx, y: plotRect.maxY))
                        context.stroke(vLine, with: .color(Studio.ink.opacity(0.09)), lineWidth: 0.8)

                        var hLine = Path()
                        hLine.move(to: CGPoint(x: plotRect.minX, y: gy))
                        hLine.addLine(to: CGPoint(x: plotRect.maxX, y: gy))
                        context.stroke(hLine, with: .color(Studio.ink.opacity(0.09)), lineWidth: 0.8)
                    }

                    if channel == .parametric {
                        for split in [CGFloat(0.25), 0.50, 0.75] {
                            let sx = plotRect.minX + plotRect.width * split
                            var splitLine = Path()
                            splitLine.move(to: CGPoint(x: sx, y: plotRect.minY))
                            splitLine.addLine(to: CGPoint(x: sx, y: plotRect.maxY))
                            context.stroke(
                                splitLine,
                                with: .color(Studio.ink.opacity(0.16)),
                                style: StrokeStyle(lineWidth: 0.9, dash: [5, 5])
                            )
                        }
                    }

                    // Reference diagonal y = x
                    var diag = Path()
                    diag.move(to: CGPoint(x: plotRect.minX, y: plotRect.maxY))
                    diag.addLine(to: CGPoint(x: plotRect.maxX, y: plotRect.minY))
                    context.stroke(diag, with: .color(Studio.ink.opacity(0.16)), lineWidth: 0.9)

                    // Background R/G/B curves
                    let luts = LightCraftEngine.buildToneCurveLuts(state, lutSize: 128)
                    let bgColors: [Color] = [
                        Studio.googleRed.opacity(0.24),
                        Studio.googleGreen.opacity(0.24),
                        Studio.googleBlue.opacity(0.24)
                    ]
                    for ch in 0..<3 {
                        let p = lutPath(luts[ch], in: plotRect)
                        context.stroke(p, with: .color(bgColors[ch]), lineWidth: 1.1)
                    }

                    // Active main curve
                    let mainLut: [Float]
                    switch channel {
                    case .red: mainLut = luts[0]
                    case .green: mainLut = luts[1]
                    case .blue: mainLut = luts[2]
                    case .parametric, .master: mainLut = luts[3]
                    }

                    let mainPath = lutPath(mainLut, in: plotRect)
                    var fillPath = mainPath
                    fillPath.addLine(to: CGPoint(x: plotRect.maxX, y: plotRect.maxY))
                    fillPath.addLine(to: CGPoint(x: plotRect.minX, y: plotRect.maxY))
                    fillPath.closeSubpath()

                    context.fill(
                        fillPath,
                        with: .linearGradient(
                            Gradient(colors: [channel.color.opacity(0.11), .clear]),
                            startPoint: CGPoint(x: plotRect.minX, y: plotRect.minY),
                            endPoint: CGPoint(x: plotRect.minX, y: plotRect.maxY)
                        )
                    )
                    context.stroke(mainPath, with: .color(channel.color), lineWidth: 2.0)

                    // Control points
                    if channel != .parametric {
                        let pts = activePoints
                        for (idx, pt) in pts.enumerated() {
                            let cx = plotRect.minX + CGFloat(pt.x) * plotRect.width
                            let cy = plotRect.maxY - CGFloat(pt.y) * plotRect.height
                            let rad: CGFloat = (idx == draggingPointIndex) ? 6.0 : 4.4
                            let circleRect = CGRect(x: cx - rad, y: cy - rad, width: rad * 2, height: rad * 2)
                            context.fill(Path(ellipseIn: circleRect), with: .color(.white))
                            context.stroke(Path(ellipseIn: circleRect), with: .color(channel.color), lineWidth: 1.8)
                        }
                    }
                }

                // Readout / Hint label
                Text(readoutText)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Studio.muted)
                    .padding(.leading, pad + 6)
                    .padding(.top, pad + 3)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        guard channel != .parametric, plotRect.width > 1, plotRect.height > 1 else { return }
                        var pts = activePoints
                        let loc = value.location
                        let nx = Float(max(0, min(1, (loc.x - plotRect.minX) / plotRect.width)))
                        let ny = Float(max(0, min(1, (plotRect.maxY - loc.y) / plotRect.height)))

                        if draggingPointIndex == nil {
                            let now = Date()
                            let nearest = findNearestPoint(pts: pts, location: loc, in: plotRect, maxDist: 22)

                            // Check double-tap on an interior point to delete it
                            if now.timeIntervalSince(lastTapTime) < 0.32,
                               hypot(loc.x - lastTapLocation.x, loc.y - lastTapLocation.y) < 24,
                               let idx = nearest, idx > 0 && idx < pts.count - 1 {
                                onEditingBegan()
                                pts.remove(at: idx)
                                setActivePoints(pts)
                                Haptics.selection()
                                onCurveChanged()
                                lastTapTime = .distantPast
                                return
                            }
                            lastTapTime = now
                            lastTapLocation = loc

                            if let idx = nearest {
                                draggingPointIndex = idx
                            } else if pts.count < 12 {
                                onEditingBegan()
                                didRecordDragUndo = true
                                let insertAt = pts.firstIndex(where: { $0.x > nx }) ?? pts.count
                                pts.insert(CurvePoint(x: nx, y: ny), at: insertAt)
                                setActivePoints(pts)
                                draggingPointIndex = insertAt
                                Haptics.selection()
                                onCurveChanged()
                                return
                            }
                        }

                        if let idx = draggingPointIndex, idx >= 0, idx < pts.count {
                            if !didRecordDragUndo {
                                onEditingBegan()
                                didRecordDragUndo = true
                            }
                            let lo: Float = (idx == 0) ? 0 : (pts[idx - 1].x + 0.02)
                            let hi: Float = (idx == pts.count - 1) ? 1 : (pts[idx + 1].x - 0.02)
                            let clampedX: Float
                            if idx == 0 {
                                clampedX = min(nx, hi)
                            } else if idx == pts.count - 1 {
                                clampedX = max(nx, lo)
                            } else {
                                clampedX = max(lo, min(hi, nx))
                            }
                            pts[idx] = CurvePoint(x: clampedX, y: ny)
                            setActivePoints(pts)
                            onCurveChanged()
                        }
                    }
                    .onEnded { _ in
                        draggingPointIndex = nil
                        didRecordDragUndo = false
                    }
            )
        }
    }

    private var readoutText: String {
        if channel == .parametric {
            return "Đường cong 4 vùng tham số"
        }
        let pts = activePoints
        if let idx = draggingPointIndex, idx >= 0, idx < pts.count {
            let p = pts[idx]
            return "In \(Int((p.x * 255).rounded())) → Out \(Int((p.y * 255).rounded()))"
        }
        return "Chạm thêm điểm · Chạm đúp xóa"
    }

    private func lutPath(_ lut: [Float], in rect: CGRect) -> Path {
        var path = Path()
        let n = lut.count
        guard n > 1 else { return path }
        for i in 0..<n {
            let x = rect.minX + (CGFloat(i) / CGFloat(n - 1)) * rect.width
            let y = rect.maxY - CGFloat(lut[i]) * rect.height
            if i == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }
        return path
    }

    private func findNearestPoint(pts: [CurvePoint], location: CGPoint, in rect: CGRect, maxDist: CGFloat) -> Int? {
        var bestIdx: Int? = nil
        var bestDist = maxDist
        for (i, pt) in pts.enumerated() {
            let px = rect.minX + CGFloat(pt.x) * rect.width
            let py = rect.maxY - CGFloat(pt.y) * rect.height
            let d = hypot(location.x - px, location.y - py)
            if d < bestDist {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }
}

// MARK: - 3. 3-Way Color Grading Wheels (OkLab / OkLCh)
struct GradingWheelsView: View {
    @Binding var state: LightCraftState
    var onEditingBegan: () -> Void = {}
    var onGradingChanged: () -> Void = {}

    @State private var activeWheelIndex: Int? = nil
    @State private var lastTapTime: Date = .distantPast
    @State private var lastTappedWheel: Int = -1
    @State private var didRecordDragUndo: Bool = false

    private let labels = ["Vùng tối (Shadows)", "Trung tính (Mid)", "Vùng sáng (High)"]
    private let sweepColors: [Color] = [
        Color(red: 0.88, green: 0.32, blue: 0.32),
        Color(red: 0.88, green: 0.60, blue: 0.32),
        Color(red: 0.88, green: 0.87, blue: 0.32),
        Color(red: 0.32, green: 0.88, blue: 0.41),
        Color(red: 0.32, green: 0.87, blue: 0.88),
        Color(red: 0.32, green: 0.49, blue: 0.88),
        Color(red: 0.77, green: 0.32, blue: 0.88),
        Color(red: 0.88, green: 0.32, blue: 0.32)
    ]

    private func wheel(at idx: Int) -> GradingWheel {
        switch idx {
        case 0: return state.shadowsWheel
        case 1: return state.midtonesWheel
        default: return state.highlightsWheel
        }
    }

    private func setWheel(_ w: GradingWheel, at idx: Int) {
        switch idx {
        case 0: state.shadowsWheel = w
        case 1: state.midtonesWheel = w
        default: state.highlightsWheel = w
        }
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let cellW = w / 3.0
            let radius = max(24, min(cellW * 0.41, (h - 38) * 0.45))
            let centerY: CGFloat = 18 + radius

            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Studio.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(Studio.line, lineWidth: 1)
                    )

                ForEach(0..<3, id: \.self) { idx in
                    let cx = cellW * (CGFloat(idx) + 0.5)
                    let wh = wheel(at: idx)
                    let radAngle = CGFloat(wh.hue) * .pi / 180.0
                    let dist = CGFloat(wh.sat / 100.0) * radius
                    let dotX = cx + cos(radAngle) * dist
                    let dotY = centerY - sin(radAngle) * dist

                    ZStack {
                        // Hue Sweep + Center Desaturation
                        Circle()
                            .fill(
                                AngularGradient(
                                    gradient: Gradient(colors: sweepColors),
                                    center: .center
                                )
                            )
                            .scaleEffect(x: 1, y: -1)
                            .overlay(
                                Circle().fill(
                                    RadialGradient(
                                        stops: [
                                            .init(color: Color(red: 0.957, green: 0.961, blue: 0.969), location: 0.0),
                                            .init(color: Color(red: 0.957, green: 0.961, blue: 0.969).opacity(0.6), location: 0.48),
                                            .init(color: .clear, location: 1.0)
                                        ],
                                        center: .center,
                                        startRadius: 0,
                                        endRadius: radius
                                    )
                                )
                            )
                            .overlay(
                                Circle()
                                    .strokeBorder(
                                        Studio.ink.opacity(0.16),
                                        style: StrokeStyle(lineWidth: 0.8, dash: [4, 4])
                                    )
                                    .scaleEffect(0.45)
                            )
                            .overlay(
                                Circle()
                                    .strokeBorder(
                                        Studio.ink.opacity(0.16),
                                        style: StrokeStyle(lineWidth: 0.8, dash: [4, 4])
                                    )
                                    .scaleEffect(0.75)
                            )
                            .overlay(
                                Circle()
                                    .strokeBorder(activeWheelIndex == idx ? Studio.ink : Studio.line, lineWidth: 1.2)
                            )
                            .frame(width: radius * 2, height: radius * 2)
                            .position(x: cx, y: centerY)

                        // Line from center to handle
                        Path { path in
                            path.move(to: CGPoint(x: cx, y: centerY))
                            path.addLine(to: CGPoint(x: dotX, y: dotY))
                        }
                        .stroke(Studio.ink.opacity(0.55), lineWidth: 1.1)

                        // Center dot
                        Circle()
                            .fill(Studio.ink.opacity(0.4))
                            .frame(width: 4, height: 4)
                            .position(x: cx, y: centerY)

                        // Draggable handle
                        Circle()
                            .fill(Color.white)
                            .frame(width: 10.5, height: 10.5)
                            .overlay(Circle().strokeBorder(Studio.ink, lineWidth: 1.6))
                            .position(x: dotX, y: dotY)

                        // Top H° · S% readout
                        Text("\(Int(wh.hue.rounded()))° · \(Int(wh.sat.rounded()))%")
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundStyle(wh.sat > 0.5 ? Studio.ink : Studio.muted)
                            .position(x: cx, y: centerY - radius - 8)

                        // Bottom region label
                        Text(labels[idx])
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Studio.ink)
                            .lineLimit(1)
                            .position(x: cx, y: centerY + radius + 10)
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let loc = value.location
                        if activeWheelIndex == nil {
                            var hit = 0
                            var bestD = CGFloat.greatestFiniteMagnitude
                            for i in 0..<3 {
                                let cx = cellW * (CGFloat(i) + 0.5)
                                let d = hypot(loc.x - cx, loc.y - centerY)
                                if d < bestD {
                                    bestD = d
                                    hit = i
                                }
                            }
                            activeWheelIndex = hit
                            let now = Date()
                            if hit == lastTappedWheel && now.timeIntervalSince(lastTapTime) < 0.32 {
                                onEditingBegan()
                                var resetWh = wheel(at: hit)
                                resetWh.reset()
                                setWheel(resetWh, at: hit)
                                Haptics.selection()
                                onGradingChanged()
                                lastTapTime = .distantPast
                                return
                            }
                            lastTappedWheel = hit
                            lastTapTime = now
                            Haptics.selection()
                        }

                        if let hit = activeWheelIndex {
                            if !didRecordDragUndo {
                                onEditingBegan()
                                didRecordDragUndo = true
                            }
                            let cx = cellW * (CGFloat(hit) + 0.5)
                            let vx = loc.x - cx
                            let vy = loc.y - centerY
                            let deg = Float(((atan2(-vy, vx) * 180.0 / .pi) + 360.0).truncatingRemainder(dividingBy: 360.0))
                            let sat = Float(max(0, min(100, (hypot(vx, vy) / max(1, radius)) * 100.0)))
                            var updated = wheel(at: hit)
                            updated.hue = deg
                            updated.sat = sat
                            setWheel(updated, at: hit)
                            onGradingChanged()
                        }
                    }
                    .onEnded { _ in
                        activeWheelIndex = nil
                        didRecordDragUndo = false
                    }
            )
        }
    }
}

// MARK: - 4. Stage Mask Overlay View (Interactive Radial & Linear Gradient Pins on Photo Stage)
struct StageMaskOverlayView: View {
    @Binding var state: LightCraftState
    let imageSize: CGSize
    let interactiveEnabled: Bool
    var onEditingBegan: () -> Void = {}
    var onMaskGeometryChanged: () -> Void = {}

    @State private var dragHandle: Int = 0 // 0: none, 1: center/start, 2: radius/end
    @State private var didRecordDragUndo: Bool = false

    private func fitBounds(in container: CGSize) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0, container.width > 0, container.height > 0 else {
            return CGRect(origin: .zero, size: container)
        }
        let scale = min(container.width / imageSize.width, container.height / imageSize.height)
        let drawW = imageSize.width * scale
        let drawH = imageSize.height * scale
        let left = (container.width - drawW) * 0.5
        let top = (container.height - drawH) * 0.5
        return CGRect(x: left, y: top, width: drawW, height: drawH)
    }

    var body: some View {
        GeometryReader { geo in
            let bounds = fitBounds(in: geo.size)
            let bw = bounds.width
            let bh = bounds.height

            ZStack {
                if interactiveEnabled && bw > 1 && bh > 1 {
                    if state.maskMode == .radial {
                        let cx = bounds.minX + CGFloat(state.radialCenterX) * bw
                        let cy = bounds.minY + CGFloat(state.radialCenterY) * bh
                        let rx = CGFloat(state.radialRx) * bw
                        let ry = CGFloat(state.radialRy) * bh
                        let innerScale = CGFloat(1.0 - max(0.05, min(0.95, state.radialFeather / 100.0)))

                        Canvas { context, _ in
                            let outerRect = CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2)
                            context.stroke(Path(ellipseIn: outerRect), with: .color(Studio.googleRed.opacity(0.88)), lineWidth: 2)

                            let irx = rx * innerScale
                            let iry = ry * innerScale
                            let innerRect = CGRect(x: cx - irx, y: cy - iry, width: irx * 2, height: iry * 2)
                            context.stroke(
                                Path(ellipseIn: innerRect),
                                with: .color(.white.opacity(0.65)),
                                style: StrokeStyle(lineWidth: 1.2, dash: [8, 6])
                            )
                        }

                        pinView(color: Studio.googleRed)
                            .position(x: cx, y: cy)
                        pinView(color: Studio.googleBlue)
                            .position(x: cx + rx, y: cy)

                    } else if state.maskMode == .linear {
                        let sx = bounds.minX + CGFloat(state.linearStartX) * bw
                        let sy = bounds.minY + CGFloat(state.linearStartY) * bh
                        let ex = bounds.minX + CGFloat(state.linearEndX) * bw
                        let ey = bounds.minY + CGFloat(state.linearEndY) * bh

                        Canvas { context, _ in
                            var mainLine = Path()
                            mainLine.move(to: CGPoint(x: sx, y: sy))
                            mainLine.addLine(to: CGPoint(x: ex, y: ey))
                            context.stroke(
                                mainLine,
                                with: .color(.white.opacity(0.85)),
                                style: StrokeStyle(lineWidth: 1.8, dash: [8, 6])
                            )

                            let dx = ex - sx
                            let dy = ey - sy
                            let len = hypot(dx, dy)
                            if len > 1 {
                                let px = -dy / len * 64
                                let py = dx / len * 64

                                var startPerp = Path()
                                startPerp.move(to: CGPoint(x: sx - px, y: sy - py))
                                startPerp.addLine(to: CGPoint(x: sx + px, y: sy + py))
                                context.stroke(startPerp, with: .color(Studio.googleRed.opacity(0.82)), lineWidth: 1.8)

                                var endPerp = Path()
                                endPerp.move(to: CGPoint(x: ex - px, y: ey - py))
                                endPerp.addLine(to: CGPoint(x: ex + px, y: ey + py))
                                context.stroke(endPerp, with: .color(Studio.googleBlue.opacity(0.82)), lineWidth: 1.8)
                            }
                        }

                        pinView(color: Studio.googleRed)
                            .position(x: sx, y: sy)
                        pinView(color: Studio.googleBlue)
                            .position(x: ex, y: ey)
                    }
                }
            }
            .contentShape(Rectangle())
            .allowsHitTesting(interactiveEnabled && (state.maskMode == .radial || state.maskMode == .linear))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        guard interactiveEnabled, bw > 1, bh > 1 else { return }
                        let loc = value.location
                        let nx = Float(max(0.02, min(0.98, (loc.x - bounds.minX) / bw)))
                        let ny = Float(max(0.02, min(0.98, (loc.y - bounds.minY) / bh)))

                        if dragHandle == 0 {
                            if !didRecordDragUndo {
                                onEditingBegan()
                                didRecordDragUndo = true
                            }
                            if state.maskMode == .radial {
                                let cx = bounds.minX + CGFloat(state.radialCenterX) * bw
                                let cy = bounds.minY + CGFloat(state.radialCenterY) * bh
                                let rxHandleX = cx + CGFloat(state.radialRx) * bw
                                if hypot(loc.x - rxHandleX, loc.y - cy) < 32 {
                                    dragHandle = 2
                                } else {
                                    dragHandle = 1
                                    state.radialCenterX = nx
                                    state.radialCenterY = ny
                                }
                            } else if state.maskMode == .linear {
                                let sx = bounds.minX + CGFloat(state.linearStartX) * bw
                                let sy = bounds.minY + CGFloat(state.linearStartY) * bh
                                let ex = bounds.minX + CGFloat(state.linearEndX) * bw
                                let ey = bounds.minY + CGFloat(state.linearEndY) * bh
                                if hypot(loc.x - ex, loc.y - ey) < hypot(loc.x - sx, loc.y - sy) {
                                    dragHandle = 2
                                    state.linearEndX = nx
                                    state.linearEndY = ny
                                } else {
                                    dragHandle = 1
                                    state.linearStartX = nx
                                    state.linearStartY = ny
                                }
                            }
                            Haptics.selection()
                            onMaskGeometryChanged()
                            return
                        }

                        if state.maskMode == .radial {
                            if dragHandle == 2 {
                                state.radialRx = max(0.06, min(0.65, abs(nx - state.radialCenterX)))
                                state.radialRy = max(0.06, min(0.65, state.radialRx * 0.82))
                            } else {
                                state.radialCenterX = nx
                                state.radialCenterY = ny
                            }
                        } else if state.maskMode == .linear {
                            if dragHandle == 2 {
                                state.linearEndX = nx
                                state.linearEndY = ny
                            } else {
                                state.linearStartX = nx
                                state.linearStartY = ny
                            }
                        }
                        onMaskGeometryChanged()
                    }
                    .onEnded { _ in
                        dragHandle = 0
                        didRecordDragUndo = false
                    }
            )
        }
    }

    private func pinView(color: Color) -> some View {
        ZStack {
            Circle()
                .fill(Color.white)
                .frame(width: 15, height: 15)
                .shadow(color: .black.opacity(0.25), radius: 2, y: 1)
            Circle()
                .fill(color)
                .frame(width: 10.4, height: 10.4)
        }
    }
}
