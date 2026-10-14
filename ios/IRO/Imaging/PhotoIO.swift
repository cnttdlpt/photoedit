import Foundation
import UIKit
import ImageIO
import UniformTypeIdentifiers
import os
import Darwin

enum ExportSize: String, CaseIterable, Identifiable, Sendable {
    case standard, original, double
    var id: String { rawValue }
    var title: String {
        switch self {
        case .standard: return "Tiêu chuẩn · tối đa 12 MP"
        case .original: return "Giữ kích thước gốc"
        case .double: return "Phóng lớn 2×"
        }
    }
    var scale: Int { self == .double ? 2 : 1 }
}

enum ExportFormat: String, CaseIterable, Identifiable, Sendable {
    case jpeg100, jpeg95, png
    var id: String { rawValue }
    var title: String {
        switch self {
        case .jpeg100: return "JPEG · chất lượng 100%"
        case .jpeg95: return "JPEG · chất lượng 95%"
        case .png: return "PNG · không mất dữ liệu"
        }
    }
    var fileExtension: String { self == .png ? "png" : "jpg" }
    var contentType: UTType { self == .png ? .png : .jpeg }
    var quality: Double { self == .jpeg95 ? 0.95 : 1 }
}

enum PhotoIOError: LocalizedError {
    case unreadable, unsupportedFormat, invalidDimensions, allocation, insufficientMemory, decodeSizeMismatch, encoding
    var errorDescription: String? {
        switch self {
        case .unreadable: return "Không đọc được ảnh. Hãy chọn một ảnh JPEG, HEIC hoặc PNG hợp lệ."
        case .unsupportedFormat: return "IRO chưa hỗ trợ ảnh RAW. Hãy xuất ảnh sang JPEG, HEIC hoặc PNG trước khi mở."
        case .invalidDimensions: return "Kích thước ảnh không hợp lệ hoặc vượt giới hạn xử lý."
        case .allocation: return "Không tạo được bộ đệm xử lý ảnh."
        case .insufficientMemory: return "Không đủ bộ nhớ cho độ phân giải này. Hãy chọn kích thước gốc hoặc tiêu chuẩn; ứng dụng không tự giảm kích thước gốc/2×."
        case .decodeSizeMismatch: return "Bộ giải mã không giữ được kích thước ảnh gốc. Ứng dụng đã dừng xuất để tránh tự giảm độ phân giải."
        case .encoding: return "Không ghi được ảnh xuất. Hãy kiểm tra dung lượng trống."
        }
    }
}

/// ImageIO normalizes EXIF orientation once. All engine buffers use straight-alpha sRGB RGBA8.
enum PhotoIO {
    static let previewEdge = 1280
    private static let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!

    private static func availableMemoryBytes() -> UInt64 {
        let reported = UInt64(os_proc_available_memory())
        #if targetEnvironment(macCatalyst)
        if reported == 0 {
            return max(ProcessInfo.processInfo.physicalMemory / 2, 4 * 1_024 * 1_024 * 1_024)
        }
        #elseif targetEnvironment(simulator)
        // Simulator processes have no iOS jetsam allowance and may report zero even for a 2 px image.
        // Use a conservative host allowance after subtracting this process's resident footprint.
        // This fallback is never compiled into a device build.
        if reported == 0 {
            var info = mach_task_basic_info()
            var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<natural_t>.size)
            let result = withUnsafeMutablePointer(to: &info) { pointer in
                pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                    task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
                }
            }
            guard result == KERN_SUCCESS else { return 0 }
            return simulatorMemoryHeadroom(physical: ProcessInfo.processInfo.physicalMemory, resident: UInt64(info.resident_size))
        }
        #else
        if reported == 0 && ProcessInfo.processInfo.isiOSAppOnMac {
            return max(ProcessInfo.processInfo.physicalMemory / 2, 4 * 1_024 * 1_024 * 1_024)
        }
        #endif
        return reported
    }

    /// Pure budget calculation lets tests exercise low-memory rejection without exhausting the host.
    static func simulatorMemoryHeadroom(physical: UInt64, resident: UInt64) -> UInt64 {
        let allowance = min(physical / 4, 1_024 * 1_024 * 1_024)
        return resident < allowance ? allowance - resident : 0
    }

    private static func source(url: URL) throws -> CGImageSource {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary),
              CGImageSourceGetCount(source) > 0 else { throw PhotoIOError.unreadable }
        guard let identifier = CGImageSourceGetType(source), supportsSourceType(identifier as String) else {
            throw PhotoIOError.unsupportedFormat
        }
        return source
    }

    /// ImageIO can decode RAW previews; accepting those would misrepresent source-size editing.
    static func supportsSourceType(_ identifier: String) -> Bool {
        guard let type = UTType(identifier) else { return false }
        return type.conforms(to: .image) && !type.conforms(to: .rawImage)
    }

    private static func dimensions(source: CGImageSource) throws -> (width: Int, height: Int) {
        guard let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? NSNumber,
              let height = properties[kCGImagePropertyPixelHeight] as? NSNumber,
              width.intValue > 0, height.intValue > 0 else { throw PhotoIOError.invalidDimensions }
        let orientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1
        return (5...8).contains(orientation) ? (height.intValue, width.intValue) : (width.intValue, height.intValue)
    }

    static func sourceDimensions(url: URL) throws -> (width: Int, height: Int) {
        try dimensions(source: source(url: url))
    }

    static func decodePreview(url: URL) throws -> RasterImage {
        let source = try source(url: url)
        let size = try dimensions(source: source)
        let ratio = min(1, Double(previewEdge) / Double(max(size.width, size.height)))
        return try decode(source: source, width: max(1, Int(floor(Double(size.width) * ratio))),
                          height: max(1, Int(floor(Double(size.height) * ratio))), exactSource: false)
    }

    private static func decode(source: CGImageSource, width: Int, height: Int, exactSource: Bool) throws -> RasterImage {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: max(width, height),
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceShouldAllowFloat: false
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { throw PhotoIOError.unreadable }
        if exactSource && (image.width != width || image.height != height) { throw PhotoIOError.decodeSizeMismatch }
        return try raster(image, width: width, height: height)
    }

    /// The original source is reopened for every export, never the 1280 px preview.
    static func export(sourceURL: URL, settings: Settings, lut: CubeLut?,
                       lightCraft: LightCraftState = LightCraftState(),
                       size: ExportSize, format: ExportFormat, info: PhotoInfo, frameStyle: Int,
                       destinationURL: URL) throws -> (width: Int, height: Int) {
        try autoreleasepool {
            let source = try source(url: sourceURL)
            let original = try dimensions(source: source)
            // Reserve space for the engine's float buffers, decoding, upscaling, frame, and encoder.
            // Available memory is advisory and must be queried for each export (Apple os API).
            let available = availableMemoryBytes()
            let reserve: UInt64 = 48 * 1024 * 1024
            guard available > reserve else { throw PhotoIOError.insufficientMemory }
            let usable = (available - reserve) * 2 / 3
            let bytesPerSourcePixel = UInt64(24 + (frameStyle > 0 ? 16 : 12) * size.scale * size.scale)
            let pixelBudget = Int(min(usable / bytesPerSourcePixel, UInt64(Int.max)))
            guard pixelBudget > 0 else { throw PhotoIOError.insufficientMemory }
            let target = try ExportSizing.dimensions(width: original.width, height: original.height,
                scale: size.scale, fullSize: size != .standard, pixelBudget: pixelBudget)
            var output = try ColorEngine.render(decode(source: source, width: target.width, height: target.height,
                exactSource: size != .standard), settings: settings, lut: lut)
            output = try LightCraftEngine.render(output, state: lightCraft, includeMaskOverlay: false)
            if size.scale == 2 {
                output = try resized(output, width: target.width * 2, height: target.height * 2)
            }
            output = try PhotoFrame.render(output, info: info.sanitized(), style: frameStyle)
            try write(output, format: format, url: destinationURL)
            return (output.width, output.height)
        }
    }

    static func write(_ image: RasterImage, format: ExportFormat, url: URL) throws {
        var rendered = image
        if format != .png {
            // JPEG has no alpha; use a white background for transparent source pixels.
            for index in stride(from: 0, to: rendered.pixels.count, by: 4) {
                let alpha = Int(rendered.pixels[index + 3])
                if alpha != 255 {
                    for channel in 0..<3 {
                        rendered.pixels[index + channel] = UInt8((Int(rendered.pixels[index + channel]) * alpha + 255 * (255 - alpha) + 127) / 255)
                    }
                    rendered.pixels[index + 3] = 255
                }
            }
        }
        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, format.contentType.identifier as CFString, 1, nil) else {
            throw PhotoIOError.encoding
        }
        let properties: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: format.quality,
            kCGImagePropertyOrientation: 1]
        CGImageDestinationAddImage(destination, try cgImage(rendered), properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw PhotoIOError.encoding }
    }

    static func cgImage(_ image: RasterImage) throws -> CGImage {
        let data = Data(image.pixels)
        guard let provider = CGDataProvider(data: data as CFData),
              let result = CGImage(width: image.width, height: image.height, bitsPerComponent: 8, bitsPerPixel: 32,
                bytesPerRow: image.width * 4, space: colorSpace,
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue).union(.byteOrder32Big),
                provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent) else { throw PhotoIOError.allocation }
        return result
    }

    static func uiImage(_ image: RasterImage) throws -> UIImage { UIImage(cgImage: try cgImage(image)) }

    static func resized(_ image: RasterImage, width: Int, height: Int) throws -> RasterImage {
        try raster(cgImage(image), width: width, height: height)
    }

    static func resized(_ image: RasterImage, maxDimension: Int) throws -> RasterImage {
        guard maxDimension > 0 else { throw PhotoIOError.invalidDimensions }
        let scale = min(1, Double(maxDimension) / Double(max(image.width, image.height)))
        if scale == 1 { return image }
        return try resized(image, width: max(1, Int(floor(Double(image.width) * scale))),
                           height: max(1, Int(floor(Double(image.height) * scale))))
    }

    /// Xoay ảnh RasterImage 90 độ (theo chiều kim đồng hồ hoặc ngược chiều).
    static func rotated90(_ image: RasterImage, clockwise: Bool = true) throws -> RasterImage {
        let w = image.width, h = image.height
        var rotatedPixels = [UInt8](repeating: 0, count: w * h * 4)
        let pixels = image.pixels
        if clockwise {
            // (x, y) -> (h - 1 - y, x)
            for y in 0..<h {
                let row = y * w
                for x in 0..<w {
                    let srcIdx = (row + x) * 4
                    let dstX = h - 1 - y
                    let dstY = x
                    let dstIdx = (dstY * h + dstX) * 4
                    rotatedPixels[dstIdx]     = pixels[srcIdx]
                    rotatedPixels[dstIdx + 1] = pixels[srcIdx + 1]
                    rotatedPixels[dstIdx + 2] = pixels[srcIdx + 2]
                    rotatedPixels[dstIdx + 3] = pixels[srcIdx + 3]
                }
            }
        } else {
            // (x, y) -> (y, w - 1 - x)
            for y in 0..<h {
                let row = y * w
                for x in 0..<w {
                    let srcIdx = (row + x) * 4
                    let dstX = y
                    let dstY = w - 1 - x
                    let dstIdx = (dstY * h + dstX) * 4
                    rotatedPixels[dstIdx]     = pixels[srcIdx]
                    rotatedPixels[dstIdx + 1] = pixels[srcIdx + 1]
                    rotatedPixels[dstIdx + 2] = pixels[srcIdx + 2]
                    rotatedPixels[dstIdx + 3] = pixels[srcIdx + 3]
                }
            }
        }
        return try RasterImage(width: h, height: w, pixels: rotatedPixels)
    }

    /// Xoay tệp ảnh gốc trên đĩa lưu trữ 90 độ mà không làm mất chất lượng EXIF / độ phân giải cao.
    static func rotateFile(url: URL, clockwise: Bool = true) throws {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let img = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw PhotoIOError.unreadable
        }
        let w = img.width, h = img.height
        guard let ctx = CGContext(
            data: nil,
            width: h,
            height: w,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
        ) else {
            throw PhotoIOError.allocation
        }

        if clockwise {
            ctx.translateBy(x: CGFloat(h), y: 0)
            ctx.rotate(by: .pi / 2)
        } else {
            ctx.translateBy(x: 0, y: CGFloat(w))
            ctx.rotate(by: -.pi / 2)
        }
        ctx.draw(img, in: CGRect(x: 0, y: 0, width: w, height: h))

        guard let rotatedCG = ctx.makeImage(),
              let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else {
            throw PhotoIOError.encoding
        }
        let options: [CFString: Any] = [
            kCGImageDestinationLossyCompressionQuality: 0.96,
            kCGImagePropertyOrientation: 1
        ]
        CGImageDestinationAddImage(dest, rotatedCG, options as CFDictionary)
        guard CGImageDestinationFinalize(dest) else {
            throw PhotoIOError.encoding
        }
    }

    private static func raster(_ image: CGImage, width: Int, height: Int) throws -> RasterImage {
        try bitmap(width: width, height: height) { context in
            context.interpolationQuality = .high
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    /// A top-left UIKit canvas with an explicit sRGB RGBA8 backing store.
    static func canvas(width: Int, height: Int, draw: (CGContext) -> Void) throws -> RasterImage {
        try bitmap(width: width, height: height) { context in
            context.translateBy(x: 0, y: CGFloat(height))
            context.scaleBy(x: 1, y: -1)
            UIGraphicsPushContext(context)
            defer { UIGraphicsPopContext() }
            draw(context)
        }
    }

    private static func bitmap(width: Int, height: Int, draw: (CGContext) -> Void) throws -> RasterImage {
        let (count, overflow) = width.multipliedReportingOverflow(by: height)
        guard width > 0, height > 0, !overflow, count <= Int.max / 4 else { throw PhotoIOError.invalidDimensions }
        let available = availableMemoryBytes()
        // Reject very wide panoramas whose decorative canvas could dwarf the source.
        // Keep room for another raster and the platform's backing buffer while drawing.
        guard UInt64(count) <= available / 12 else { throw PhotoIOError.insufficientMemory }
        var bytes = [UInt8](repeating: 0, count: count * 4)
        try bytes.withUnsafeMutableBytes { buffer in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4, space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue) else {
                throw PhotoIOError.allocation
            }
            draw(context)
        }
        // CoreGraphics renders premultiplied pixels; the shared color engine expects straight alpha.
        for index in stride(from: 0, to: bytes.count, by: 4) {
            let alpha = Int(bytes[index + 3])
            if alpha > 0 && alpha < 255 {
                for channel in 0..<3 { bytes[index + channel] = UInt8(min(255, (Int(bytes[index + channel]) * 255 + alpha / 2) / alpha)) }
            }
        }
        return try RasterImage(width: width, height: height, pixels: bytes)
    }

    static func readMetadata(url: URL) -> PhotoInfo {
        guard let source = try? source(url: url),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else { return PhotoInfo() }
        let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any] ?? [:]
        let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any] ?? [:]
        func text(_ value: Any?) -> String { PhotoInfo.clean(value as? String ?? "") }
        func number(_ value: Any?) -> Double { (value as? NSNumber)?.doubleValue ?? 0 }
        func formatted(_ value: Double) -> String {
            let string = String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
            return string.hasSuffix(".0") ? String(string.dropLast(2)) : string
        }
        var info = PhotoInfo()
        let make = text(tiff[kCGImagePropertyTIFFMake]), model = text(tiff[kCGImagePropertyTIFFModel])
        info.camera = model.lowercased().hasPrefix(make.lowercased()) ? model : "\(make) \(model)".trimmingCharacters(in: .whitespaces)
        var focal = number(exif[kCGImagePropertyExifFocalLenIn35mmFilm])
        let equivalent = focal > 0
        if !equivalent { focal = number(exif[kCGImagePropertyExifFocalLength]) }
        if focal.isFinite && focal > 0 { info.focal = "\(formatted(focal)) mm" + (equivalent ? " (eq.)" : "") }
        let aperture = number(exif[kCGImagePropertyExifFNumber])
        if aperture.isFinite && aperture > 0 { info.aperture = "f/\(formatted(aperture))" }
        let time = number(exif[kCGImagePropertyExifExposureTime])
        if time.isFinite && time > 0 {
            info.shutter = time < 0.5 ? "1/\(formatted((1 / time).rounded())) s" : "\(formatted(time)) s"
        }
        let iso = (exif[kCGImagePropertyExifISOSpeedRatings] as? [NSNumber])?.first?.intValue ?? 0
        if iso > 0 { info.iso = "ISO \(iso)" }
        info.date = text(exif[kCGImagePropertyExifDateTimeOriginal])
        if info.date.isEmpty { info.date = text(tiff[kCGImagePropertyTIFFDateTime]) }
        if info.date.count >= 10 { info.date = info.date.prefix(10).replacingOccurrences(of: ":", with: ".") + info.date.dropFirst(10) }
        info.author = text(tiff[kCGImagePropertyTIFFArtist])
        return info.sanitized()
    }

    /// Generates the 1280×860 Golden Hour + Mountain Ridge + Color Chart studio sample image matching Android.
    static func generateStudioSample() throws -> (Data, PhotoInfo) {
        let w = 1280, h = 860
        let raster = try canvas(width: w, height: h) { ctx in
            let rect = CGRect(x: 0, y: 0, width: w, height: h)
            // 1. Warm Sunset / Golden Hour Sky Gradient
            let skyColors = [
                UIColor(red: 28/255, green: 52/255, blue: 88/255, alpha: 1).cgColor,
                UIColor(red: 74/255, green: 98/255, blue: 138/255, alpha: 1).cgColor,
                UIColor(red: 214/255, green: 132/255, blue: 92/255, alpha: 1).cgColor,
                UIColor(red: 246/255, green: 192/255, blue: 120/255, alpha: 1).cgColor,
                UIColor(red: 252/255, green: 224/255, blue: 168/255, alpha: 1).cgColor
            ] as CFArray
            let skyLocs: [CGFloat] = [0.0, 0.28, 0.56, 0.78, 1.0]
            if let grad = CGGradient(colorsSpace: colorSpace, colors: skyColors, locations: skyLocs) {
                ctx.drawLinearGradient(grad, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 0, y: h), options: [])
            } else {
                ctx.setFillColor(UIColor(red: 214/255, green: 132/255, blue: 92/255, alpha: 1).cgColor)
                ctx.fill(rect)
            }

            // 2. Warm Glowing Sun
            let sunCenter = CGPoint(x: CGFloat(w) * 0.68, y: CGFloat(h) * 0.42)
            let sunColors = [
                UIColor(red: 1, green: 250/255, blue: 225/255, alpha: 1).cgColor,
                UIColor(red: 1, green: 210/255, blue: 135/255, alpha: 0.78).cgColor,
                UIColor(red: 1, green: 160/255, blue: 90/255, alpha: 0).cgColor
            ] as CFArray
            let sunLocs: [CGFloat] = [0.0, 0.35, 1.0]
            if let sunGrad = CGGradient(colorsSpace: colorSpace, colors: sunColors, locations: sunLocs) {
                ctx.drawRadialGradient(sunGrad, startCenter: sunCenter, startRadius: 0, endCenter: sunCenter, endRadius: CGFloat(w) * 0.24, options: [])
            }

            // 3. Far Mountain Ridge
            let farRidge = UIBezierPath()
            farRidge.move(to: CGPoint(x: 0, y: CGFloat(h) * 0.66))
            farRidge.addLine(to: CGPoint(x: CGFloat(w) * 0.22, y: CGFloat(h) * 0.50))
            farRidge.addLine(to: CGPoint(x: CGFloat(w) * 0.46, y: CGFloat(h) * 0.61))
            farRidge.addLine(to: CGPoint(x: CGFloat(w) * 0.74, y: CGFloat(h) * 0.46))
            farRidge.addLine(to: CGPoint(x: CGFloat(w), y: CGFloat(h) * 0.58))
            farRidge.addLine(to: CGPoint(x: CGFloat(w), y: CGFloat(h)))
            farRidge.addLine(to: CGPoint(x: 0, y: CGFloat(h)))
            farRidge.close()
            UIColor(red: 62/255, green: 56/255, blue: 78/255, alpha: 1).setFill()
            farRidge.fill()

            // 4. Near Mountain Ridge
            let nearRidge = UIBezierPath()
            nearRidge.move(to: CGPoint(x: 0, y: CGFloat(h) * 0.74))
            nearRidge.addLine(to: CGPoint(x: CGFloat(w) * 0.32, y: CGFloat(h) * 0.59))
            nearRidge.addLine(to: CGPoint(x: CGFloat(w) * 0.61, y: CGFloat(h) * 0.71))
            nearRidge.addLine(to: CGPoint(x: CGFloat(w) * 0.88, y: CGFloat(h) * 0.60))
            nearRidge.addLine(to: CGPoint(x: CGFloat(w), y: CGFloat(h) * 0.68))
            nearRidge.addLine(to: CGPoint(x: CGFloat(w), y: CGFloat(h)))
            nearRidge.addLine(to: CGPoint(x: 0, y: CGFloat(h)))
            nearRidge.close()
            UIColor(red: 32/255, green: 36/255, blue: 52/255, alpha: 1).setFill()
            nearRidge.fill()

            // 5. Subtle Color Reference Swatches at Bottom-Left
            let swatches: [UIColor] = [
                UIColor(red: 216/255, green: 92/255, blue: 74/255, alpha: 1),
                UIColor(red: 232/255, green: 168/255, blue: 76/255, alpha: 1),
                UIColor(red: 92/255, green: 164/255, blue: 116/255, alpha: 1),
                UIColor(red: 76/255, green: 138/255, blue: 196/255, alpha: 1),
                UIColor(red: 198/255, green: 148/255, blue: 122/255, alpha: 1),
                UIColor(red: 238/255, green: 236/255, blue: 230/255, alpha: 1)
            ]
            let startX: CGFloat = 44
            let startY: CGFloat = CGFloat(h) - 72
            for (idx, swatchColor) in swatches.enumerated() {
                let r = CGRect(x: startX + CGFloat(idx) * 38, y: startY, width: 30, height: 30)
                let rounded = UIBezierPath(roundedRect: r, cornerRadius: 6)
                swatchColor.setFill()
                rounded.fill()
            }
        }
        let uiImg = try uiImage(raster)
        guard let jpegData = uiImg.jpegData(compressionQuality: 0.96) else {
            throw PhotoIOError.encoding
        }
        let sampleInfo = PhotoInfo(
            camera: "FUJIFILM X-T5",
            focal: "35 mm",
            aperture: "f/2.0",
            shutter: "1/500 s",
            iso: "ISO 160",
            date: "2026.10.12",
            author: "IRO STUDIO"
        )
        return (jpegData, sampleInfo)
    }
}
