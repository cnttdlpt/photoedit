import UIKit

/// The IDs, proportions and labels match the Android PhotoFrame renderer (0 = no frame).
enum PhotoFrame {
    static let styles = ["Không chèn thông tin", "Classic · dải trắng", "White · cột thông tin", "Glass · chữ trên ảnh", "Dark · dải đen", "Centered · thông số giữa", "Gallery · viền trắng rộng", "Noir · viền đen rộng", "Instant · ảnh lấy liền", "Vintage · giấy kem", "Film 35 · viền lỗ phim", "Cinema · thanh đen", "Blur · nền ảnh mờ", "Palette · bảng màu ảnh", "Postcard · bưu thiếp", "Editorial · trang tạp chí", "Double · viền kép", "Date · dấu ngày màu cam", "Signature · chữ ký tối giản", "Float · ảnh nổi đổ bóng", "Soft · viền bo tròn", "Torn · giấy xé", "Tape · băng dính scrapbook", "Wood · khung gỗ", "Gold · viền kim loại", "Neon · ánh sáng viền", "Notebook · giấy kẻ ô", "Ticket · vé kỷ niệm", "Blueprint · bản vẽ xanh", "Arch · vòm triển lãm", "Corners · góc giữ ảnh"]
    static let groups = ["Tất cả", "Thông số máy", "Tối giản", "Film / hoài cổ", "Sáng tạo", "Mẫu mới"]

    static func group(_ style: Int) -> Int {
        if [19, 20, 30, 3, 6, 7, 16, 18].contains(style) { return 2 }
        if [21, 22, 23, 27, 8, 9, 10, 11, 17].contains(style) { return 3 }
        if [1, 2, 4, 5].contains(style) { return 1 }
        return 4
    }

    static func render(_ image: RasterImage, info: PhotoInfo, style: Int) throws -> RasterImage {
        guard styles.indices.contains(style), style != 0 else { return image }
        // Preview and export share this boundary; text edited in the frame sheet may still
        // contain whitespace/control characters that source metadata has already cleaned.
        let info = info.sanitized()
        let photo = try PhotoIO.uiImage(image)
        if style >= 19 { return try decorative(photo, info: info, style: style) }
        if style > 3 { return try extended(photo, raster: image, info: info, style: style) }
        let w = CGFloat(image.width), h = CGFloat(image.height)
        let pad = max(1, (w * 0.025).rounded()), footer = max(1, (w * 0.125).rounded()), side = max(1, (w * 0.30).rounded())
        let ow = style == 2 ? w + side + pad * 2 : w
        let oh = style == 1 ? h + footer : style == 2 ? h + pad * 2 : h
        return try PhotoIO.canvas(width: Int(ow), height: Int(oh)) { c in
            let d = Drawing(c)
            d.rect(0, 0, ow, oh, 0xffffffff)
            photo.draw(at: CGPoint(x: style == 2 ? pad : 0, y: style == 2 ? pad : 0))
            let title = info.camera.isEmpty ? "PHOTO" : info.camera
            if style == 1 {
                d.text(title, pad, h + footer * 0.37, w * 0.43, w * 0.028, 0xff202520, true)
                d.text(info.author, pad, h + footer * 0.70, w * 0.43, w * 0.018, 0xff687068)
                d.text(info.settings, w * 0.49, h + footer * 0.37, w * 0.485, w * 0.020, 0xff353b35)
                d.text(info.date, w * 0.49, h + footer * 0.70, w * 0.485, w * 0.018, 0xff687068)
            } else if style == 2 {
                let x = w + pad * 2, width = side - pad, center = oh * 0.5, unit = min(w, h * 1.7)
                d.text(title, x, center - unit * 0.07, width, unit * 0.032, 0xff202520, true)
                var y = center - unit * 0.025
                for row in [info.focal, join(info.aperture, info.shutter), info.iso, info.date, info.author] where !row.isEmpty {
                    d.text(row, x, y, width, unit * 0.019, 0xff687068)
                    y += unit * 0.032
                }
            } else {
                let bar = min(h * 0.35, w * 0.15), top = h - bar, unit = bar / 0.15
                d.gradient(CGRect(x: 0, y: top, width: w, height: bar), colors: [0x08000000, 0xc9000000], start: CGPoint(x: 0, y: top), end: CGPoint(x: 0, y: h))
                d.text(title, pad, top + bar * 0.39, w - pad * 2, unit * 0.031, 0xffffffff, true)
                d.text(info.settings, pad, top + bar * 0.65, w - pad * 2, unit * 0.020, 0xffeeeeee)
                d.text(join(info.date, info.author), pad, top + bar * 0.87, w - pad * 2, unit * 0.018, 0xffdddddd)
            }
        }
    }

    private static func extended(_ photo: UIImage, raster: RasterImage, info: PhotoInfo, style: Int) throws -> RasterImage {
        let w = photo.size.width, h = photo.size.height, unit = min(w, h * 1.7)
        var pad = max(1, (w * 0.05).rounded()), foot = max(1, (w * 0.15).rounded())
        let overlay = style == 17 || style == 18
        if style == 4 || style == 5 { pad = 0; foot = max(1, (w * 0.14).rounded()) }
        if style == 6 || style == 7 { pad = max(1, (w * 0.09).rounded()) }
        if style == 8 || style == 9 { foot = max(1, (w * 0.23).rounded()) }
        if style == 10 { pad = max(1, (w * 0.055).rounded()) }
        if style == 11 { pad = 0; foot = max(1, (w * 0.12).rounded()) }
        if style == 15 { pad = max(1, (w * 0.04).rounded()); foot = max(1, (w * 0.20).rounded()) }
        if overlay { pad = 0; foot = 0 }
        let top = style == 11 ? foot : pad, ow = w + pad * 2, oh = h + top + pad + foot
        let dark = [4, 7, 10, 11, 12].contains(style)
        let bg: UInt32 = dark ? 0xff151716 : (style == 9 || style == 14 ? 0xfff1eadb : 0xffffffff)
        let ink: UInt32 = dark ? 0xfff2f0e7 : 0xff252b28, muted: UInt32 = dark ? 0xffb6bcb4 : 0xff68716a
        // Reproduce Android's small, linearly interpolated background without blurring the foreground.
        let tiny = style == 12 ? try PhotoIO.uiImage(PhotoIO.resized(raster, width: 18, height: max(1, Int((18 * h / w).rounded())))) : nil
        return try PhotoIO.canvas(width: Int(ow), height: Int(oh)) { c in
            let d = Drawing(c)
            d.rect(0, 0, ow, oh, bg)
            if let tiny {
                c.interpolationQuality = .high
                tiny.draw(in: CGRect(x: 0, y: 0, width: ow, height: oh))
                d.rect(0, 0, ow, oh, 0x60000000)
                d.rect(pad - w * 0.008, top - w * 0.008, pad + w + w * 0.008, top + h + w * 0.018, 0x55000000)
            }
            photo.draw(at: CGPoint(x: pad, y: top))
            let title = info.camera.isEmpty ? "PHOTO" : info.camera, details = info.settings, bottom = join(info.date, info.author)
            if overlay {
                let margin = w * 0.035, base = h - unit * 0.035
                let text = style == 17 ? info.date : (info.author.isEmpty ? title : info.author)
                c.saveGState()
                c.setShadow(offset: CGSize(width: 0, height: unit * 0.002), blur: unit * 0.006, color: Drawing.color(0xaa000000).cgColor)
                d.text(text, margin, base, w - margin * 2, unit * (style == 17 ? 0.036 : 0.028), style == 17 ? 0xffffaa54 : 0xffffffff, style == 17)
                c.restoreGState()
                return
            }
            let x = max(pad, w * 0.025), y = top + h + foot * 0.35
            let area = ow - x * 2 - (style == 13 ? w * 0.25 : 0)
            if style == 8 || style == 9 {
                d.text(info.author.isEmpty ? title : info.author, x, top + h + foot * 0.40, area, w * 0.038, ink, true)
                d.text(details, x, top + h + foot * 0.65, area, w * 0.020, muted)
                d.text(info.date, x, top + h + foot * 0.82, area, w * 0.018, muted)
            } else if style == 14 {
                d.line(ow * 0.62, top + h + foot * 0.15, ow * 0.62, oh - pad * 0.3, 0xffb9ad98, max(1, w * 0.001))
                d.text(title, x, y, ow * 0.55, w * 0.030, ink, true)
                d.text(info.author, x, y + foot * 0.27, ow * 0.55, w * 0.019, muted)
                d.text(details, ow * 0.65, y, ow * 0.31, w * 0.017, muted)
                d.text(info.date, ow * 0.65, y + foot * 0.27, ow * 0.31, w * 0.017, muted)
            } else if style == 15 {
                d.text("PHOTOGRAPH / " + title, x, top + h + foot * 0.30, area, w * 0.032, ink, true)
                d.rect(x, top + h + foot * 0.43, ow - x, top + h + foot * 0.436, ink)
                d.text(details, x, top + h + foot * 0.64, area, w * 0.020, muted)
                d.text(bottom, x, top + h + foot * 0.84, area, w * 0.018, muted)
            } else {
                let center = [5, 6, 7, 11, 12, 16].contains(style)
                d.text(title, center ? ow * 0.5 : x, y, area, w * (center ? 0.027 : 0.028), ink, true, centered: center)
                d.text(details, center ? ow * 0.5 : x, y + foot * 0.25, area, w * (center ? 0.019 : 0.020), muted, centered: center)
                d.text(bottom, center ? ow * 0.5 : x, y + foot * 0.47, area, w * 0.017, muted, centered: center)
            }
            if style == 10 {
                let holeW = pad * 0.45, holeH = w * 0.025, step = max(3, holeH * 1.8)
                for yy in stride(from: top, to: top + h - holeH, by: step) {
                    d.roundRect(CGRect(x: pad * 0.25, y: yy, width: holeW, height: holeH), radius: holeW * 0.2, color: 0xffdad4c5)
                    d.roundRect(CGRect(x: ow - pad * 0.70, y: yy, width: pad * 0.45, height: holeH), radius: holeW * 0.2, color: 0xffdad4c5)
                }
            }
            if style == 13 {
                let swatch = w * 0.032, gap = w * 0.007, start = ow - pad - 5 * swatch - 4 * gap
                for i in 0..<5 {
                    let pixelX = min(raster.width - 1, (i * 2 + 1) * raster.width / 10)
                    let index = (raster.height / 2 * raster.width + pixelX) * 4
                    let color = UInt32(raster.pixels[index + 3]) << 24 | UInt32(raster.pixels[index]) << 16 | UInt32(raster.pixels[index + 1]) << 8 | UInt32(raster.pixels[index + 2])
                    let sx = start + CGFloat(i) * (swatch + gap), sy = top + h + foot * 0.2
                    d.rect(sx, sy, sx + swatch, sy + swatch, color)
                }
            }
            if style == 16 {
                let inset = pad * 0.4
                d.stroke(CGRect(x: inset, y: inset, width: ow - inset * 2, height: oh - inset * 2), color: 0xffbebeb5, width: max(1, w * 0.001))
            }
        }
    }

    private static func decorative(_ photo: UIImage, info: PhotoInfo, style: Int) throws -> RasterImage {
        let w = photo.size.width, h = photo.size.height, pad = max(1, w * 0.07), foot = max(1, w * 0.17)
        let top = style == 29 ? pad * 2.2 : pad
        let ow = (w + pad * 2).rounded(), oh = (h + top + pad + foot).rounded(), dark = style == 25 || style == 28
        let backgrounds: [Int: UInt32] = [23: 0xff9d7750, 24: 0xfff2eee5, 25: 0xff101122, 28: 0xff153553, 26: 0xfff7f3e7, 21: 0xffded6c7, 22: 0xffded6c7, 27: 0xffded6c7]
        let bg = backgrounds[style] ?? 0xffe9edf0, ink: UInt32 = dark ? 0xffeef5ff : 0xff2e3433, muted: UInt32 = dark ? 0xffb8cde2 : 0xff707572
        return try PhotoIO.canvas(width: Int(ow), height: Int(oh)) { c in
            let d = Drawing(c)
            d.rect(0, 0, ow, oh, bg)
            if style == 23 {
                for i in 0..<24 {
                    let x = (CGFloat(i) + 0.5) * ow / 24
                    let grain = UIBezierPath()
                    grain.move(to: CGPoint(x: x, y: 0))
                    grain.addCurve(to: CGPoint(x: x, y: oh), controlPoint1: CGPoint(x: x + pad * 0.4, y: oh * 0.33), controlPoint2: CGPoint(x: x - pad * 0.3, y: oh * 0.66))
                    Drawing.color(i % 2 == 0 ? 0x30804524 : 0x25ffe6aa).setStroke()
                    grain.lineWidth = max(1, w * 0.0015)
                    grain.stroke()
                }
            }
            if style == 26 || style == 28 {
                let color: UInt32 = style == 28 ? 0x3048c8ff : 0x284c8190, step = max(4, w * 0.035)
                for x in stride(from: CGFloat(0), to: ow, by: step) { d.line(x, 0, x, oh, color, max(1, w * 0.001)) }
                for y in stride(from: CGFloat(0), to: oh, by: step) { d.line(0, y, ow, y, color, max(1, w * 0.001)) }
            }
            let paper = CGRect(x: pad * 0.5, y: top - pad * 0.5, width: w + pad, height: oh - pad * 0.45 - top + pad * 0.5)
            if style == 19 {
                for i in stride(from: 10, through: 1, by: -1) {
                    let spread = pad * CGFloat(i) * 0.035
                    d.roundRect(paper.offsetBy(dx: 0, dy: pad * 0.2).insetBy(dx: -spread, dy: -spread), radius: pad * 0.15, color: 0x04000000)
                }
            }
            let paperColor: UInt32 = dark ? 0xff112334 : 0xffffffff
            if style == 21 {
                let torn = UIBezierPath(), step = max(2, w * 0.022)
                torn.move(to: paper.origin)
                for x in stride(from: paper.minX, to: paper.maxX, by: step) { torn.addLine(to: CGPoint(x: x, y: paper.minY + pad * 0.09 * sin(x * 1.3))) }
                torn.addLine(to: CGPoint(x: paper.maxX, y: paper.maxY))
                for x in stride(from: paper.maxX, to: paper.minX, by: -step) { torn.addLine(to: CGPoint(x: x, y: paper.maxY + pad * 0.09 * sin(x * 0.7))) }
                torn.close(); Drawing.color(paperColor).setFill(); torn.fill()
            } else if style == 20 {
                d.roundRect(paper, radius: pad * 0.42, color: paperColor)
            } else if style == 29 {
                let arch = UIBezierPath()
                arch.move(to: CGPoint(x: paper.minX, y: paper.maxY))
                arch.addLine(to: CGPoint(x: paper.minX, y: top))
                arch.addCurve(to: CGPoint(x: paper.maxX, y: top), controlPoint1: CGPoint(x: paper.minX, y: paper.minY - pad * 0.8), controlPoint2: CGPoint(x: paper.maxX, y: paper.minY - pad * 0.8))
                arch.addLine(to: CGPoint(x: paper.maxX, y: paper.maxY)); arch.close()
                Drawing.color(paperColor).setFill(); arch.fill()
            } else { d.rect(paper.minX, paper.minY, paper.maxX, paper.maxY, paperColor) }
            if style == 25 {
                let glow = CGRect(x: pad - pad * 0.12, y: top - pad * 0.12, width: w + pad * 0.24, height: h + pad * 0.24)
                for i in stride(from: 8, through: 1, by: -1) { d.stroke(glow, color: 0x0e69dcff, width: max(1, w * 0.0015 * CGFloat(i))) }
                d.stroke(glow, color: 0xffb7f0ff, width: max(1, w * 0.002))
            }
            photo.draw(at: CGPoint(x: pad, y: top))
            if style == 24 {
                c.saveGState()
                c.setLineWidth(max(1, w * 0.008)); c.addRect(paper); c.replacePathWithStrokedPath(); c.clip()
                d.gradient(CGRect(x: 0, y: 0, width: ow, height: oh), colors: [0xff9c7637, 0xffefd99b, 0xffae8742], start: .zero, end: CGPoint(x: ow, y: oh))
                c.restoreGState()
            }
            if style == 22 {
                for side in 0..<2 {
                    let cx = side == 0 ? pad * 1.15 : pad + w - pad * 0.15
                    c.saveGState(); c.translateBy(x: cx, y: top); c.rotate(by: (side == 0 ? -24 : 23) * .pi / 180)
                    d.rect(-pad * 0.58, -pad * 0.22, pad * 0.58, pad * 0.22, 0xbde1c791)
                    c.restoreGState()
                }
            }
            if style == 30 {
                for x in 0..<2 { for y in 0..<2 {
                    let cx = pad + CGFloat(x) * w, cy = top + CGFloat(y) * h, dx: CGFloat = x == 0 ? 1 : -1, dy: CGFloat = y == 0 ? 1 : -1, length = pad * 0.55
                    let corner = UIBezierPath()
                    corner.move(to: CGPoint(x: cx, y: cy)); corner.addLine(to: CGPoint(x: cx + dx * length, y: cy)); corner.addLine(to: CGPoint(x: cx, y: cy + dy * length)); corner.close()
                    Drawing.color(0xff3d4944).setFill(); corner.fill()
                } }
            }
            if style == 27 {
                let y = top + h + foot * 0.12
                for x in stride(from: pad, to: pad + w, by: max(3, w * 0.024)) { d.line(x, y, min(x + w * 0.012, pad + w), y, 0xffa69c8a, max(1, w * 0.002)) }
                for x in [paper.minX, paper.maxX] { d.circle(x, y, pad * 0.18, bg) }
            }
            let title = info.camera.isEmpty ? "PHOTO" : info.camera, baseline = top + h + foot * 0.39
            d.text(title, pad + w * 0.5, baseline, w, w * 0.028, ink, true, centered: true)
            d.text(info.settings, pad + w * 0.5, baseline + foot * 0.24, w, w * 0.019, muted, centered: true)
            d.text(join(info.date, info.author), pad + w * 0.5, baseline + foot * 0.47, w, w * 0.017, muted, centered: true)
        }
    }

    private static func join(_ a: String, _ b: String) -> String { a.isEmpty ? b : b.isEmpty ? a : a + "  ·  " + b }

    private final class Drawing {
        let context: CGContext
        init(_ context: CGContext) { self.context = context }
        static func color(_ argb: UInt32) -> UIColor {
            UIColor(red: CGFloat((argb >> 16) & 255) / 255, green: CGFloat((argb >> 8) & 255) / 255,
                    blue: CGFloat(argb & 255) / 255, alpha: CGFloat((argb >> 24) & 255) / 255)
        }
        func rect(_ left: CGFloat, _ top: CGFloat, _ right: CGFloat, _ bottom: CGFloat, _ color: UInt32) {
            context.setFillColor(Self.color(color).cgColor)
            context.fill(CGRect(x: left, y: top, width: right - left, height: bottom - top))
        }
        func roundRect(_ rect: CGRect, radius: CGFloat, color: UInt32) {
            Self.color(color).setFill(); UIBezierPath(roundedRect: rect, cornerRadius: radius).fill()
        }
        func circle(_ x: CGFloat, _ y: CGFloat, _ radius: CGFloat, _ color: UInt32) {
            context.setFillColor(Self.color(color).cgColor)
            context.fillEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
        }
        func stroke(_ rect: CGRect, color: UInt32, width: CGFloat) {
            context.setStrokeColor(Self.color(color).cgColor); context.setLineWidth(width); context.stroke(rect)
        }
        func line(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat, _ color: UInt32, _ width: CGFloat) {
            context.setStrokeColor(Self.color(color).cgColor); context.setLineWidth(width)
            context.move(to: CGPoint(x: x1, y: y1)); context.addLine(to: CGPoint(x: x2, y: y2)); context.strokePath()
        }
        func gradient(_ rect: CGRect, colors: [UInt32], start: CGPoint, end: CGPoint) {
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map { Self.color($0).cgColor } as CFArray, locations: nil) else { return }
            context.saveGState(); context.clip(to: rect); context.drawLinearGradient(gradient, start: start, end: end, options: []); context.restoreGState()
        }
        func text(_ value: String, _ x: CGFloat, _ baseline: CGFloat, _ width: CGFloat, _ size: CGFloat, _ color: UInt32, _ bold: Bool = false, centered: Bool = false) {
            guard !value.isEmpty, width > 0 else { return }
            let weight: UIFont.Weight = bold ? .bold : .regular
            var font = UIFont.systemFont(ofSize: max(1, size), weight: weight)
            let measured = (value as NSString).size(withAttributes: [.font: font]).width
            // Android centers using the original measured width, then shrinks/truncates.
            let left = centered ? x - min(width, measured) / 2 : x
            if measured > width { font = UIFont.systemFont(ofSize: max(1, max(size * 0.65, size * width / measured)), weight: weight) }
            let paragraph = NSMutableParagraphStyle(); paragraph.lineBreakMode = .byTruncatingTail
            let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: Self.color(color), .paragraphStyle: paragraph]
            (value as NSString).draw(in: CGRect(x: left, y: baseline - font.ascender, width: width, height: font.lineHeight * 1.1), withAttributes: attributes)
        }
    }
}
