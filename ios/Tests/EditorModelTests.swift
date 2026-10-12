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

    private func withEditor(_ body: (EditorModel, URL) async throws -> Void) async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("IRO-editor-tests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let editor = EditorModel(applicationDirectory: directory)
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
}
