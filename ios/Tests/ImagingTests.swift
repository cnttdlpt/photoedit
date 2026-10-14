import XCTest
import UIKit
import ImageIO
import UniformTypeIdentifiers
@testable import IRO

/// Run in the iOS app-hosted test target: UIKit and os_proc_available_memory require an app process.
final class ImagingTests: XCTestCase {
    private var folder: URL!

    override func setUpWithError() throws {
        folder = FileManager.default.temporaryDirectory.appendingPathComponent("IRO-imaging-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        if let folder { try FileManager.default.removeItem(at: folder) }
    }

    func testAllEightEXIFOrientationsAreAppliedExactlyOnce() throws {
        let colors: [[UInt8]] = [[240, 20, 30, 255], [30, 230, 40, 255], [20, 40, 240, 255],
                                 [230, 220, 20, 255], [230, 20, 220, 255], [20, 220, 230, 255]]
        var pixels: [UInt8] = []
        for y in 0..<16 { for x in 0..<24 { pixels += colors[(y / 8) * 3 + x / 8] } }
        let original = try RasterImage(width: 24, height: 16, pixels: pixels)
        // Color blocks in reading order after each standard EXIF transform.
        let expected = [[0, 1, 2, 3, 4, 5], [2, 1, 0, 5, 4, 3], [5, 4, 3, 2, 1, 0], [3, 4, 5, 0, 1, 2],
                        [0, 3, 1, 4, 2, 5], [3, 0, 4, 1, 5, 2], [5, 2, 4, 1, 3, 0], [2, 5, 1, 4, 0, 3]]
        for orientation in 1...8 {
            let url = try fixture(original, name: "orientation-\(orientation)", properties: [kCGImagePropertyOrientation: orientation])
            let output = try PhotoIO.decodePreview(url: url)
            let columns = orientation >= 5 ? 2 : 3, rows = orientation >= 5 ? 3 : 2
            XCTAssertEqual(output.width, columns * 8, "orientation \(orientation)")
            XCTAssertEqual(output.height, rows * 8, "orientation \(orientation)")
            let dimensions = try PhotoIO.sourceDimensions(url: url)
            XCTAssertEqual(dimensions.width, output.width); XCTAssertEqual(dimensions.height, output.height)
            for index in 0..<6 {
                XCTAssertEqual(pixel(output, x: index % columns * 8 + 4, y: index / columns * 8 + 4),
                    colors[expected[orientation - 1][index]], "orientation \(orientation), block \(index)")
            }
        }
    }

    func testPreviewIsBoundedButOriginalAndDoubleExportReopenSource() throws {
        let original = try solid(width: 1600, height: 800)
        let source = try fixture(original, name: "large-source")
        let preview = try PhotoIO.decodePreview(url: source)
        XCTAssertEqual(preview.width, 1280); XCTAssertEqual(preview.height, 640)
        for size in [ExportSize.standard, .original, .double] {
            let destination = folder.appendingPathComponent("export-\(size.rawValue).png")
            let dimensions = try PhotoIO.export(sourceURL: source, settings: Settings(), lut: nil,
                size: size, format: .png, info: PhotoInfo(), frameStyle: 0, destinationURL: destination)
            let scale = size == .double ? 2 : 1
            XCTAssertEqual(dimensions.width, 1600 * scale); XCTAssertEqual(dimensions.height, 800 * scale)
            let encoded = try PhotoIO.sourceDimensions(url: destination)
            XCTAssertEqual(encoded.width, dimensions.width); XCTAssertEqual(encoded.height, dimensions.height)
        }
    }

    func testReadsOnlySourceMetadataAndLeavesMissingFieldsEmpty() throws {
        let image = try solid(width: 24, height: 16)
        let empty = try fixture(image, name: "no-camera")
        XCTAssertEqual(PhotoIO.readMetadata(url: empty), PhotoInfo())
        let properties: [CFString: Any] = [
            kCGImagePropertyTIFFDictionary: [kCGImagePropertyTIFFMake: "FUJIFILM", kCGImagePropertyTIFFModel: "FUJIFILM X-T5", kCGImagePropertyTIFFArtist: "Source author"],
            kCGImagePropertyExifDictionary: [kCGImagePropertyExifFocalLenIn35mmFilm: 35, kCGImagePropertyExifFocalLength: 23.0,
                kCGImagePropertyExifFNumber: 2.8, kCGImagePropertyExifExposureTime: 0.004,
                kCGImagePropertyExifISOSpeedRatings: [400], kCGImagePropertyExifDateTimeOriginal: "2024:06:03 12:30:00"]
        ]
        let source = try fixture(image, name: "camera", properties: properties)
        let info = PhotoIO.readMetadata(url: source)
        XCTAssertEqual(info.camera, "FUJIFILM X-T5")
        XCTAssertEqual(info.focal, "35 mm (eq.)"); XCTAssertEqual(info.aperture, "f/2.8")
        XCTAssertEqual(info.shutter, "1/250 s"); XCTAssertEqual(info.iso, "ISO 400")
        XCTAssertEqual(info.date, "2024.06.03 12:30:00"); XCTAssertEqual(info.author, "Source author")
        XCTAssertEqual(info.settings, "35 mm (eq.)  ·  f/2.8  ·  1/250 s  ·  ISO 400")
        let exported = folder.appendingPathComponent("metadata-stripped.png")
        try PhotoIO.write(image, format: .png, url: exported)
        XCTAssertEqual(PhotoIO.readMetadata(url: exported), PhotoInfo())
    }

    func testExportAppliesLightCraftButOmitsMaskOverlay() throws {
        let original = try solid(width: 24, height: 16)
        let source = try fixture(original, name: "lightcraft-source")
        var settings = Settings(); settings.exposure = 0.3
        var state = LightCraftState()
        state.paramShadows = 25
        state.maskMode = .radial
        state.maskExposure = 0.5
        state.showMaskOverlay = true
        let base = try ColorEngine.render(original, settings: settings)
        let expected = try LightCraftEngine.render(base, state: state, includeMaskOverlay: false)
        XCTAssertNotEqual(expected, base)
        XCTAssertNotEqual(expected, try LightCraftEngine.render(base, state: state, includeMaskOverlay: true))
        let destination = folder.appendingPathComponent("lightcraft-export.png")
        _ = try PhotoIO.export(sourceURL: source, settings: settings, lut: nil, lightCraft: state,
            size: .original, format: .png, info: PhotoInfo(), frameStyle: 0, destinationURL: destination)
        XCTAssertEqual(try PhotoIO.decodePreview(url: destination), expected)
    }

    func testAlphaIsStraightInEngineAndPNGButJPEGFlattensToWhite() throws {
        let image = try RasterImage(width: 2, height: 1, pixels: [200, 100, 50, 128, 12, 34, 56, 255])
        let png = folder.appendingPathComponent("alpha.png")
        try PhotoIO.write(image, format: .png, url: png)
        let decoded = try PhotoIO.decodePreview(url: png)
        for channel in 0..<3 { XCTAssertLessThanOrEqual(abs(Int(decoded.pixels[channel]) - Int(image.pixels[channel])), 1) }
        XCTAssertEqual(decoded.pixels[3], 128)
        XCTAssertEqual(Array(decoded.pixels[4..<8]), [12, 34, 56, 255])
        let transparent = try RasterImage(width: 16, height: 16, pixels: Array(repeating: [UInt8(255), 0, 0, 0], count: 256).flatMap { $0 })
        for format in [ExportFormat.jpeg100, .jpeg95] {
            let url = folder.appendingPathComponent("alpha-\(format.rawValue).jpg")
            try PhotoIO.write(transparent, format: format, url: url)
            let output = try PhotoIO.decodePreview(url: url)
            XCTAssertEqual(pixel(output, x: 8, y: 8), [255, 255, 255, 255])
        }
    }

    func testAllThirtyFrameIDsPreserveAndroidCanvasDimensions() throws {
        let source = try solid(width: 400, height: 300)
        // Fixtures from the Android PhotoFrame formulas for a 400 × 300 source.
        let expected: [(Int, Int)] = [
            (400, 300), (400, 350), (540, 320), (400, 300), (400, 356), (400, 356), (472, 432), (472, 432),
            (440, 432), (440, 432), (444, 404), (400, 396), (440, 400), (440, 400), (440, 400), (432, 412),
            (440, 400), (400, 300), (400, 300), (456, 424), (456, 424), (456, 424), (456, 424), (456, 424),
            (456, 424), (456, 424), (456, 424), (456, 424), (456, 424), (456, 458), (456, 424)
        ]
        XCTAssertEqual(PhotoFrame.styles.count, expected.count)
        for style in 0..<expected.count {
            let rendered = try PhotoFrame.render(source, info: PhotoInfo(camera: "Source camera", date: "2024.06.03", author: "Source author"), style: style)
            XCTAssertEqual(rendered.width, expected[style].0, "style \(style)")
            XCTAssertEqual(rendered.height, expected[style].1, "style \(style)")
            XCTAssertEqual(pixel(rendered, x: rendered.width / 2, y: rendered.height / 2), [40, 90, 140, 255], "foreground remains intact for style \(style)")
        }
        XCTAssertEqual(try PhotoFrame.render(source, info: PhotoInfo(), style: 0), source)
    }

    func testEditedFrameTextMatchesTextUsedForExport() throws {
        let source = try solid(width: 400, height: 300)
        let edited = PhotoInfo(camera: "   ", focal: "  35 mm\t", date: " 2024.06.03 ", author: " Alice\rBob  ")
        let exportInfo = PhotoInfo(camera: "", focal: "35 mm", date: "2024.06.03", author: "Alice Bob")
        // Basic, extended, overlay and decorative families must apply the same cleanup.
        for style in [1, 5, 18, 19] {
            XCTAssertEqual(try PhotoFrame.render(source, info: edited, style: style),
                           try PhotoFrame.render(source, info: exportInfo, style: style), "style \(style)")
        }
    }

    func testRAWSourceTypesAreRejected() {
        XCTAssertTrue(PhotoIO.supportsSourceType(UTType.jpeg.identifier))
        XCTAssertTrue(PhotoIO.supportsSourceType(UTType.png.identifier))
        XCTAssertTrue(PhotoIO.supportsSourceType(UTType.heic.identifier))
        XCTAssertFalse(PhotoIO.supportsSourceType(UTType.rawImage.identifier))
        XCTAssertFalse(PhotoIO.supportsSourceType("com.adobe.raw-image"))
        XCTAssertFalse(PhotoIO.supportsSourceType("public.plain-text"))
    }

    func testSimulatorMemoryFallbackIsCappedAndSubtractsCurrentUsage() {
        let mb: UInt64 = 1_024 * 1_024
        // Large hosts never grant the editor more than 1 GB; smaller hosts grant at most a quarter.
        XCTAssertEqual(PhotoIO.simulatorMemoryHeadroom(physical: 16 * 1_024 * mb, resident: 300 * mb), 724 * mb)
        XCTAssertEqual(PhotoIO.simulatorMemoryHeadroom(physical: 2 * 1_024 * mb, resident: 300 * mb), 212 * mb)
        XCTAssertEqual(PhotoIO.simulatorMemoryHeadroom(physical: 2 * 1_024 * mb, resident: 600 * mb), 0)
        XCTAssertEqual(PhotoIO.simulatorMemoryHeadroom(physical: 0, resident: 0), 0)
    }

    func testMalformedImagesAreRejected() throws {
        let url = folder.appendingPathComponent("broken.jpg")
        try Data("not an image".utf8).write(to: url)
        XCTAssertThrowsError(try PhotoIO.decodePreview(url: url))
        XCTAssertThrowsError(try PhotoIO.sourceDimensions(url: url))
    }

    private func solid(width: Int, height: Int) throws -> RasterImage {
        var pixels = [UInt8](repeating: 255, count: width * height * 4)
        for index in stride(from: 0, to: pixels.count, by: 4) {
            pixels[index] = 40; pixels[index + 1] = 90; pixels[index + 2] = 140
        }
        return try RasterImage(width: width, height: height, pixels: pixels)
    }

    private func pixel(_ image: RasterImage, x: Int, y: Int) -> [UInt8] {
        let offset = (y * image.width + x) * 4
        return Array(image.pixels[offset..<(offset + 4)])
    }

    private func fixture(_ image: RasterImage, name: String, properties: [CFString: Any] = [:]) throws -> URL {
        let url = folder.appendingPathComponent(name).appendingPathExtension("tiff")
        let destination = try XCTUnwrap(CGImageDestinationCreateWithURL(url as CFURL, UTType.tiff.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, try PhotoIO.cgImage(image), properties as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        return url
    }
}
