import XCTest
import UIKit
@testable import IRO

@MainActor
final class EditorModelTests: XCTestCase {
    @MainActor private final class LUTGate {
        var onLoad: (() -> Void)?
        var pending: CheckedContinuation<CubeLut?, Error>?
        func load(_ film: String) async throws -> CubeLut? {
            guard let onLoad else { return nil }
            return try await withCheckedThrowingContinuation { continuation in
                pending = continuation
                onLoad()
            }
        }
    }

    @MainActor private final class AutoGate {
        var onLoad: (() -> Void)?
        var pending: [CheckedContinuation<GeminiAutoResult, Error>] = []
        func analyze(_ data: Data, _ histogram: RgbHistogramData, _ baseline: Settings) async throws -> GeminiAutoResult {
            try await withCheckedThrowingContinuation { continuation in
                pending.append(continuation)
                onLoad?()
            }
        }
    }

    private func autoResult() -> GeminiAutoResult {
        GeminiAutoResult(sceneType: "Portrait", summary: "Cân bằng nhẹ", recommendedFilm: "Classic Chrome",
                         exposure: 0.3, dr: 100, highlight: -0.5, shadow: -0.5, color: 0,
                         colorChrome: 0, chromeBlue: 0, temperature: 0, red: 0, blue: 0,
                         sharpness: 0.5, clarity: 0, grain: 0, nr: -4,
                         paramShadows: 2, paramDarks: 0, paramLights: 0, paramHighlights: -2,
                         shadowsHue: 0, shadowsSat: 0, shadowsLum: 0,
                         midtonesHue: 0, midtonesSat: 0, midtonesLum: 0,
                         highlightsHue: 0, highlightsSat: 0, highlightsLum: 0,
                         mixerHue: Array(repeating: 0, count: 8),
                         mixerSat: Array(repeating: 0, count: 8),
                         mixerLum: Array(repeating: 0, count: 8))
    }

    private func withEditor(autoAnalyzer: (@MainActor (Data, RgbHistogramData, Settings) async throws -> GeminiAutoResult)? = nil,
                            _ body: (EditorModel, URL) async throws -> Void) async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("IRO-editor-tests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let editor = EditorModel(applicationDirectory: directory, autoAnalyzer: autoAnalyzer)
        await editor.start()
        XCTAssertTrue(editor.ready, editor.errorMessage ?? "Editor did not start")
        try await body(editor, directory)
        editor.clearPhoto()
    }

    private func photo(in directory: URL) throws -> URL {
        let raster = try RasterImage(width: 32, height: 16,
                                     pixels: Array(repeating: [UInt8(90), 120, 150, 255], count: 512).flatMap { $0 })
        let url = directory.appendingPathComponent("fixture.png")
        try PhotoIO.write(raster, format: .png, url: url)
        return url
    }

    private func waitForPreview(_ editor: EditorModel) async throws {
        let deadline = Date().addingTimeInterval(10)
        while editor.isRendering && Date() < deadline { try await Task.sleep(for: .milliseconds(10)) }
        XCTAssertFalse(editor.isRendering, "Preview did not finish")
    }

    private func assertAutomaticBrightnessPreserved(
        autoAnalyzer: @escaping @MainActor (Data, RgbHistogramData, Settings) async throws -> GeminiAutoResult
    ) async throws {
        let fixtures: [(name: String, levels: [UInt8])] = [
            ("dim-interior", [30, 60, 85, 110]),
            ("balanced-midtones", [96, 136, 164, 192]),
            ("bright-scene", [170, 190, 210, 235]),
            ("backlit-scene", [35, 70, 200, 225])
        ]
        func meanLuminance(_ image: RasterImage) -> Double {
            let pixels = image.pixels
            let total = stride(from: 0, to: pixels.count, by: 4).reduce(0.0) { sum, offset in
                sum + 0.2126 * Double(pixels[offset])
                    + 0.7152 * Double(pixels[offset + 1])
                    + 0.0722 * Double(pixels[offset + 2])
            }
            return total / Double(image.width * image.height)
        }

        for fixture in fixtures {
            try await withEditor(autoAnalyzer: autoAnalyzer) { editor, directory in
                // Grayscale stripes isolate brightness from subjective white balance/color decisions.
                let width = 64, height = 32
                var pixels: [UInt8] = []
                for _ in 0..<height {
                    for x in 0..<width {
                        let value = fixture.levels[x / (width / fixture.levels.count)]
                        pixels.append(contentsOf: [value, value, value, 255])
                    }
                }
                let input = try RasterImage(width: width, height: height, pixels: pixels)
                let sourceURL = directory.appendingPathComponent("\(fixture.name).png")
                try PhotoIO.write(input, format: .png, url: sourceURL)
                await editor.importFile(sourceURL, kind: .image)
                try await waitForPreview(editor)
                let originalURL = directory.appendingPathComponent("\(fixture.name)-original.png")
                try XCTUnwrap(editor.originalImage?.pngData()).write(to: originalURL)
                let original = try PhotoIO.decodePreview(url: originalURL)
                let originalBrightness = meanLuminance(original)

                await editor.autoBalance()
                try await waitForPreview(editor)
                XCTAssertTrue(editor.isAutoBalanced, fixture.name)
                let previewURL = directory.appendingPathComponent("\(fixture.name)-preview.png")
                try XCTUnwrap(editor.editedImage?.pngData()).write(to: previewURL)
                let preview = try PhotoIO.decodePreview(url: previewURL)
                XCTAssertGreaterThanOrEqual(meanLuminance(preview), originalBrightness - 2,
                                            "\(fixture.name): Auto must not turn an existing exposure into a darker preview")

                await editor.export(size: .original, format: .png)
                let exportedURL = try XCTUnwrap(editor.exportedURL, editor.errorMessage ?? "No output")
                defer { try? FileManager.default.removeItem(at: exportedURL) }
                let output = try PhotoIO.decodePreview(url: exportedURL)
                XCTAssertGreaterThanOrEqual(meanLuminance(output), originalBrightness - 2,
                                            "\(fixture.name): the saved image must retain the same brightness protection")
                XCTAssertEqual(output.pixels, preview.pixels,
                               "\(fixture.name): preview and original-size PNG must use the same protected corrections")
            }
        }
    }

    func testResetIsOneUndoStepAndRestoresRecipeNotesAndAutomaticState() async throws {
        try await withEditor { editor, _ in
            editor.settings.exposure = 0.8
            editor.recipe = Recipe(id: "look", name: "Look", rawSettings: ["Color": "+2"])
            let recipe = editor.recipe
            editor.notes = ["Preserve this recipe note"]
            editor.frameStyle = 3
            editor.isAutoBalanced = true
            await editor.reset()
            XCTAssertEqual(editor.undoStack.count, 1)
            XCTAssertEqual(editor.frameStyle, 0)
            XCTAssertEqual(editor.settings, Settings())
            await editor.undo()
            XCTAssertFalse(editor.canUndo)
            XCTAssertEqual(editor.settings.exposure, 0.8)
            XCTAssertEqual(editor.frameStyle, 3)
            XCTAssertEqual(editor.recipe, recipe)
            XCTAssertEqual(editor.notes, ["Preserve this recipe note"])
            XCTAssertTrue(editor.isAutoBalanced)
            await editor.redo()
            XCTAssertEqual(editor.settings, Settings())
            XCTAssertEqual(editor.frameStyle, 0)
            XCTAssertFalse(editor.isAutoBalanced)
        }
    }

    func testFrameUndoRedoRestoresEditedMetadata() async throws {
        try await withEditor { editor, _ in
            let original = PhotoInfo(camera: "Camera", author: "First author")
            editor.photoInfo = original
            editor.applyFrame(style: 7, info: PhotoInfo(camera: "  New camera  ", author: "Second author"))
            XCTAssertEqual(editor.photoInfo.camera, "New camera")
            await editor.undo()
            XCTAssertEqual(editor.photoInfo, original)
            XCTAssertEqual(editor.frameStyle, 0)
            editor.applyFrame(style: 0, info: original)
            XCTAssertTrue(editor.canRedo, "Applying an unchanged frame must preserve Redo")
            await editor.redo()
            XCTAssertEqual(editor.photoInfo.author, "Second author")
            XCTAssertEqual(editor.frameStyle, 7)
        }
    }

    func testClosingPhotoClearsLUTAndOpeningPhotoResolvesOriginalBinding() async throws {
        try await withEditor { editor, directory in
            let file = directory.appendingPathComponent("swap.cube")
            try Data("LUT_3D_SIZE 2\n0 0 0\n0 0 1\n0 1 0\n0 1 1\n1 0 0\n1 0 1\n1 1 0\n1 1 1\n".utf8).write(to: file)
            editor.settings.film = "Acros"
            await editor.importFile(file, kind: .lut)
            XCTAssertNotNil(editor.activeLUT)
            editor.clearPhoto()
            XCTAssertNil(editor.activeLUT)
            XCTAssertEqual(editor.settings.film, "Original")
            await editor.importFile(try photo(in: directory), kind: .image)
            XCTAssertTrue(editor.hasPhoto)
            XCTAssertNil(editor.activeLUT, "A previous film's LUT must not affect the new Original image")
            try await waitForPreview(editor)
        }
    }

    func testExportFinishesPendingPreviewWithoutInvalidatingOutput() async throws {
        try await withEditor { editor, directory in
            await editor.importFile(try photo(in: directory), kind: .image)
            editor.settings.exposure = 0.8
            editor.changed()
            XCTAssertTrue(editor.isRendering)
            await editor.export(size: .original, format: .png)
            let exported = try XCTUnwrap(editor.exportedURL, editor.errorMessage ?? "No output")
            defer { try? FileManager.default.removeItem(at: exported) }
            try await waitForPreview(editor)
            XCTAssertEqual(editor.exportedURL, exported)
            let previewURL = directory.appendingPathComponent("preview.png")
            try XCTUnwrap(editor.editedImage?.pngData()).write(to: previewURL)
            let preview = try PhotoIO.decodePreview(url: previewURL)
            let output = try PhotoIO.decodePreview(url: exported)
            XCTAssertEqual(preview.pixels, output.pixels)
        }
    }

    func testFailedSessionSaveDoesNotDeletePreviouslySavedPhoto() async throws {
        try await withEditor { editor, directory in
            let image = try photo(in: directory)
            await editor.importFile(image, kind: .image)
            try await waitForPreview(editor)
            let session = directory.appendingPathComponent("Editor/session.json")
            let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: session)) as? [String: Any])
            let oldName = try XCTUnwrap(json["photoName"] as? String)
            let previous = directory.appendingPathComponent("Editor/\(oldName)")
            try FileManager.default.removeItem(at: session)
            try FileManager.default.createDirectory(at: session, withIntermediateDirectories: false)
            await editor.importFile(image, kind: .image)
            try await waitForPreview(editor)
            XCTAssertTrue(FileManager.default.fileExists(atPath: previous.path))
        }
    }

    func testFailedRecipeSaveReportsFailureAndKeepsCurrentLook() async throws {
        try await withEditor { editor, _ in
            let invalid = Recipe(id: "invalid", name: "Invalid", film: String(repeating: "x", count: 301))
            let saved = await editor.saveRecipe(invalid)
            XCTAssertFalse(saved)
            XCTAssertNotNil(editor.errorMessage)
            XCTAssertNil(editor.recipe)
            XCTAssertEqual(editor.settings, Settings())
        }
    }

    func testChangingAutomaticUpdatesPreservesRepositoryBeingEdited() async throws {
        try await withEditor { editor, _ in
            editor.onlineRepository = "owner/new-looks"
            await editor.toggleAutomaticUpdates(true)
            XCTAssertEqual(editor.onlineRepository, "owner/new-looks")
            XCTAssertTrue(editor.automaticUpdates)
        }
    }

    func testClosingPhotoDiscardsPendingRecipeAndItsError() async throws {
        let gate = LUTGate()
        let editor = EditorModel(boundLUTLoader: gate.load)
        let requested = expectation(description: "LUT requested")
        gate.onLoad = { requested.fulfill() }
        let pending = Task { await editor.apply(Recipe(id: "late", name: "Late look", film: "Acros")) }
        await fulfillment(of: [requested], timeout: 10)
        editor.clearPhoto()
        gate.pending?.resume(throwing: EditorFailure.message("Stale LUT failure"))
        await pending.value
        XCTAssertEqual(editor.settings, Settings())
        XCTAssertNil(editor.recipe)
        XCTAssertNil(editor.errorMessage)
        XCTAssertFalse(editor.canUndo)
    }

    func testManualEditSupersedesPendingRecipeSelection() async throws {
        let gate = LUTGate()
        let editor = EditorModel(boundLUTLoader: gate.load)
        let requested = expectation(description: "LUT requested")
        gate.onLoad = { requested.fulfill() }
        let pending = Task { await editor.apply(Recipe(id: "late", name: "Late look", film: "Acros")) }
        await fulfillment(of: [requested], timeout: 10)
        editor.recordUndo()
        editor.settings.exposure = 1
        editor.changed()
        gate.pending?.resume(returning: nil)
        await pending.value
        XCTAssertEqual(editor.settings.exposure, 1)
        XCTAssertEqual(editor.settings.film, "Original")
        XCTAssertNil(editor.recipe)
        XCTAssertEqual(editor.undoStack.count, 1)
    }

    func testStartupLUTReloadCannotCancelNewerRecipeSelection() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("IRO-startup-tests-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: directory) }
        let originalRequested = expectation(description: "Startup LUT")
        let lookRequested = expectation(description: "Selected LUT")
        var requests: [String: CheckedContinuation<CubeLut?, Error>] = [:]
        let editor = EditorModel(applicationDirectory: directory, boundLUTLoader: { film in
            try await withCheckedThrowingContinuation { continuation in
                requests[film] = continuation
                if film == "Original" { originalRequested.fulfill() }
                else { lookRequested.fulfill() }
            }
        })
        let startup = Task { await editor.start() }
        await fulfillment(of: [originalRequested], timeout: 10)
        let selection = Task { await editor.apply(Recipe(id: "newer", name: "Newer look", film: "Acros")) }
        await fulfillment(of: [lookRequested], timeout: 10)
        requests["Original"]?.resume(returning: nil)
        await startup.value
        requests["Acros"]?.resume(returning: nil)
        await selection.value
        XCTAssertEqual(editor.recipe?.id, "newer")
        XCTAssertEqual(editor.settings.film, "Acros")
        XCTAssertNil(editor.errorMessage)
    }

    func testManualEditDiscardsPendingAutomaticBalance() async throws {
        let gate = AutoGate()
        let result = autoResult()
        try await withEditor(autoAnalyzer: gate.analyze) { editor, directory in
            await editor.importFile(try photo(in: directory), kind: .image)
            let requested = expectation(description: "AI requested")
            gate.onLoad = { requested.fulfill() }
            let pending = Task { await editor.autoBalance() }
            await fulfillment(of: [requested], timeout: 10)
            editor.recordUndo()
            editor.settings.exposure = -1.2
            editor.changed()
            gate.pending.removeFirst().resume(returning: result)
            await pending.value
            XCTAssertEqual(editor.settings.exposure, -1.2)
            XCTAssertFalse(editor.isAutoBalanced)
            XCTAssertNil(editor.aiAutoInsight)
            XCTAssertEqual(editor.undoStack.count, 1)
        }
    }

    func testClosingPhotoDiscardsPendingAutomaticFallbackAndError() async throws {
        let gate = AutoGate()
        try await withEditor(autoAnalyzer: gate.analyze) { editor, directory in
            await editor.importFile(try photo(in: directory), kind: .image)
            let requested = expectation(description: "AI requested")
            gate.onLoad = { requested.fulfill() }
            let pending = Task { await editor.autoBalance() }
            await fulfillment(of: [requested], timeout: 10)
            editor.clearPhoto()
            gate.pending.removeFirst().resume(throwing: EditorFailure.message("Stale AI failure"))
            await pending.value
            XCTAssertEqual(editor.settings, Settings())
            XCTAssertFalse(editor.isAutoBalanced)
            XCTAssertFalse(editor.isAutoAnalyzing)
            XCTAssertFalse(editor.canUndo)
            XCTAssertNil(editor.errorMessage)
        }
    }

    func testOldAutomaticResponseCannotFinishNewPhotosAnalysis() async throws {
        let gate = AutoGate()
        let result = autoResult()
        try await withEditor(autoAnalyzer: gate.analyze) { editor, directory in
            let file = try photo(in: directory)
            await editor.importFile(file, kind: .image)
            let firstRequested = expectation(description: "First AI request")
            gate.onLoad = { firstRequested.fulfill() }
            let first = Task { await editor.autoBalance() }
            await fulfillment(of: [firstRequested], timeout: 10)
            editor.clearPhoto()
            await editor.importFile(file, kind: .image)
            let secondRequested = expectation(description: "Second AI request")
            gate.onLoad = { secondRequested.fulfill() }
            let second = Task { await editor.autoBalance() }
            await fulfillment(of: [secondRequested], timeout: 10)
            gate.pending.removeFirst().resume(returning: result)
            await first.value
            XCTAssertTrue(editor.isAutoAnalyzing)
            XCTAssertNil(editor.aiAutoInsight)
            gate.pending.removeFirst().resume(returning: result)
            await second.value
            XCTAssertFalse(editor.isAutoAnalyzing)
            var appliedInsight = try XCTUnwrap(editor.aiAutoInsight)
            XCTAssertFalse(appliedInsight.summary.isEmpty)
            // Brightness protection may replace the summary while preserving the AI response fields.
            appliedInsight.summary = result.summary
            XCTAssertEqual(appliedInsight, result)
        }
    }

    func testAutomaticBalanceReplacesExistingLookAndUndoRestoresIt() async throws {
        let result = autoResult()
        var baselines: [Settings] = []
        try await withEditor(autoAnalyzer: { _, _, baseline in baselines.append(baseline); return result }) { editor, directory in
            let sourceURL = try photo(in: directory)
            let source = try PhotoIO.decodePreview(url: sourceURL)
            await editor.importFile(sourceURL, kind: .image)
            let lutURL = directory.appendingPathComponent("look.cube")
            try Data("LUT_3D_SIZE 2\n0 0 0\n0 0 1\n0 1 0\n0 1 1\n1 0 0\n1 0 1\n1 1 0\n1 1 1\n".utf8).write(to: lutURL)
            await editor.importFile(lutURL, kind: .lut)
            XCTAssertNotNil(editor.activeLUT)
            editor.settings.temperature = 80
            editor.settings.red = 8
            editor.settings.dr = 400
            editor.recipe = Recipe(id: "previous", name: "Previous recipe")
            editor.notes = ["Previous recipe note"]
            editor.lightCraft.maskMode = .radial
            editor.lightCraft.maskExposure = 2
            editor.lightCraft.masterCurve = [CurvePoint(x: 0, y: 0.5), CurvePoint(x: 1, y: 1)]
            let previousSettings = editor.settings
            let previousLightCraft = editor.lightCraft
            let proposed = result.appliedSettings(base: Settings(), baseline: AutoBalancer.analyze(source))
            let guarded = try AutoBalanceGuard.protect(source, proposed: proposed)
            var expectedSettings = guarded
            expectedSettings.lutAmount = 0
            await editor.autoBalance()
            let balancedSettings = editor.settings
            let balancedLightCraft = editor.lightCraft
            let balancedInsight = try XCTUnwrap(editor.aiAutoInsight)
            XCTAssertEqual(balancedSettings, expectedSettings)
            if guarded != proposed {
                XCTAssertTrue(balancedInsight.summary.contains("giữ độ sáng"))
            } else {
                XCTAssertEqual(balancedInsight.summary, result.summary)
            }
            var originalResponseFields = balancedInsight
            originalResponseFields.summary = result.summary
            XCTAssertEqual(originalResponseFields, result)
            XCTAssertEqual(editor.undoStack.count, 1)
            XCTAssertEqual(editor.settings.lutAmount, 0)
            XCTAssertNil(editor.activeLUT)
            XCTAssertNil(editor.recipe)
            XCTAssertTrue(editor.notes.isEmpty)
            XCTAssertEqual(editor.lightCraft.maskMode, .none)
            XCTAssertTrue(editor.isAutoBalanced)
            await editor.undo()
            XCTAssertEqual(editor.settings, previousSettings)
            XCTAssertEqual(editor.lightCraft, previousLightCraft)
            XCTAssertNotNil(editor.activeLUT)
            XCTAssertEqual(editor.recipe?.id, "previous")
            XCTAssertEqual(editor.notes, ["Previous recipe note"])
            XCTAssertNil(editor.aiAutoInsight)
            await editor.redo()
            XCTAssertEqual(editor.settings, balancedSettings)
            XCTAssertEqual(editor.lightCraft, balancedLightCraft)
            XCTAssertEqual(editor.aiAutoInsight, balancedInsight)
            await editor.autoBalance()
            XCTAssertEqual(editor.settings, balancedSettings)
            XCTAssertEqual(editor.lightCraft, balancedLightCraft)
            XCTAssertEqual(editor.aiAutoInsight, balancedInsight)
            XCTAssertEqual(baselines.count, 2)
            XCTAssertEqual(baselines.first, baselines.last)
            XCTAssertEqual(baselines.first?.red, 0)
            XCTAssertEqual(baselines.first?.dr, 100)
        }
    }

    func testAutomaticFallbackAlsoClearsExistingMaskAndColorCast() async throws {
        try await withEditor(autoAnalyzer: { _, _, _ in throw EditorFailure.message("Offline fixture") }) { editor, directory in
            await editor.importFile(try photo(in: directory), kind: .image)
            editor.settings.red = 9
            editor.settings.blue = -9
            editor.settings.dr = 400
            editor.lightCraft.maskMode = .radial
            editor.lightCraft.maskTemp = 100
            await editor.autoBalance()
            XCTAssertEqual(editor.settings.red, 0)
            XCTAssertEqual(editor.settings.blue, 0)
            XCTAssertEqual(editor.settings.dr, 100)
            XCTAssertEqual(editor.settings.lutAmount, 0)
            XCTAssertEqual(editor.lightCraft, LightCraftState())
            XCTAssertTrue(editor.isAutoBalanced)
            XCTAssertNil(editor.aiAutoInsight)
        }
    }

    func testRecommendedFilmPreservesBalanceInsteadOfChoosingAnArbitraryRecipe() async throws {
        let lut = try CubeLut.parse("LUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n")
        let editor = EditorModel(boundLUTLoader: { film in film == "Classic Chrome" ? lut : nil })
        editor.recipes = [
            Recipe(id: "wrong", name: "Classic look", film: "Classic Negative"),
            Recipe(id: "strong", name: "Strong cast", film: "Classic Chrome", rawSettings: ["WB Shift": "R: +9, B: -9"])
        ]
        editor.settings.exposure = 0.4
        editor.settings.temperature = -8
        editor.lightCraft.paramShadows = 3
        await editor.applyRecommendedFilm("Classic Chrome")
        XCTAssertEqual(editor.settings.film, "Classic Chrome")
        XCTAssertEqual(editor.settings.exposure, 0.4)
        XCTAssertEqual(editor.settings.temperature, -8)
        XCTAssertEqual(editor.settings.red, 0)
        XCTAssertEqual(editor.lightCraft.paramShadows, 3)
        XCTAssertNil(editor.recipe)
        XCTAssertNotNil(editor.activeLUT)
        let settings = editor.settings
        await editor.applyRecommendedFilm("Classic")
        XCTAssertEqual(editor.settings, settings, "An incomplete film name must not select Classic Negative")
        XCTAssertNotNil(editor.errorMessage)
    }

    func testCancelledAutomaticRequestDoesNotApplyOfflineFallback() async throws {
        try await withEditor(autoAnalyzer: { _, _, _ in throw URLError(.cancelled) }) { editor, directory in
            await editor.importFile(try photo(in: directory), kind: .image)
            editor.settings.temperature = 15
            editor.changed(immediate: true)
            try await waitForPreview(editor)
            let previousSettings = editor.settings
            let previousStatus = editor.status
            await editor.autoBalance()
            XCTAssertEqual(editor.settings, previousSettings)
            XCTAssertEqual(editor.status, previousStatus)
            XCTAssertFalse(editor.isAutoAnalyzing)
            XCTAssertFalse(editor.isAutoBalanced)
            XCTAssertFalse(editor.canUndo)
            XCTAssertNil(editor.errorMessage)
        }
    }

    func testFrameThumbnailIncludesLightCraftAndMatchesExportWithoutMaskOverlay() async throws {
        try await withEditor { editor, directory in
            await editor.importFile(try photo(in: directory), kind: .image)
            editor.lightCraft.midtonesWheel = GradingWheel(hue: 25, sat: 25, lum: 10)
            editor.lightCraft.maskMode = .radial
            editor.lightCraft.maskExposure = 1
            editor.changed(immediate: true)
            try await waitForPreview(editor)
            await editor.export(size: .original, format: .png)
            let exportedURL = try XCTUnwrap(editor.exportedURL, editor.errorMessage ?? "No output")
            defer { try? FileManager.default.removeItem(at: exportedURL) }
            let exported = try PhotoIO.decodePreview(url: exportedURL)
            let previewURL = directory.appendingPathComponent("lightcraft-preview.png")
            try XCTUnwrap(editor.editedImage?.pngData()).write(to: previewURL)
            XCTAssertEqual(try PhotoIO.decodePreview(url: previewURL).pixels, exported.pixels)
            let originalURL = directory.appendingPathComponent("original-preview.png")
            try XCTUnwrap(editor.originalImage?.pngData()).write(to: originalURL)
            XCTAssertNotEqual(try PhotoIO.decodePreview(url: originalURL).pixels, exported.pixels)

            // A frame tile represents the exported image; the editor's red mask overlay is UI only.
            editor.lightCraft.showMaskOverlay = true
            let thumbnail = await editor.frameThumbnail(style: 0, info: editor.photoInfo)
            let thumbnailURL = directory.appendingPathComponent("lightcraft-frame-thumbnail.png")
            try XCTUnwrap(thumbnail?.pngData()).write(to: thumbnailURL)
            XCTAssertEqual(try PhotoIO.decodePreview(url: thumbnailURL).pixels, exported.pixels)
        }
    }

    func testDarkAIResponseCannotUnderexposePreviewOrExportAcrossLightingConditions() async throws {
        var darkResponse = autoResult()
        darkResponse.sceneType = "Landscape"
        darkResponse.exposure = -1.5
        darkResponse.dr = 400
        darkResponse.highlight = -1.5
        darkResponse.shadow = 0.5
        darkResponse.color = -0.5
        darkResponse.temperature = -12
        try await assertAutomaticBrightnessPreserved { _, _, _ in darkResponse }
    }

    func testOfflineAutoCannotUnderexposePreviewOrExportAcrossLightingConditions() async throws {
        try await assertAutomaticBrightnessPreserved { _, _, _ in
            throw EditorFailure.message("Offline brightness fixture")
        }
    }
}
