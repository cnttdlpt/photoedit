import XCTest
@testable import IRO

final class LibraryTests: XCTestCase {
    private var directories: [URL] = []

    override func tearDownWithError() throws {
        for directory in directories { try FileManager.default.removeItem(at: directory) }
        directories.removeAll()
    }

    private func temporaryDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("IRO-LibraryTests-" + UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        directories.append(url)
        return url
    }

    private func cube(_ title: String = "Identity", swapped: Bool = false) -> Data {
        let rows = swapped
            ? "0 0 0\n0 0 1\n0 1 0\n0 1 1\n1 0 0\n1 0 1\n1 1 0\n1 1 1"
            : "0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1"
        return Data("TITLE \"\(title)\"\nLUT_3D_SIZE 2\n\(rows)\n".utf8)
    }

    private func onlineItem(_ data: Data, path: String = "Looks/test.cube", revision: String = String(repeating: "a", count: 40)) -> OnlineLUT {
        OnlineLUT(repository: "owner/looks", path: path, name: "Test look",
                  sha: OnlineLUTService.blobHash(data), revision: revision,
                  byteCount: data.count, license: "MIT", input: "sRGB")
    }

    func testGitBlobChecksumAndRepositoryValidation() throws {
        XCTAssertEqual(OnlineLUTService.blobHash(Data("hello\n".utf8)), "ce013625030ba8dba906f756967f9e9ca394464a")
        XCTAssertEqual(try OnlineLUTService.normalizeRepository(" https://github.com/t0saki/lumix-original-looks/ \n"),
                       OnlineLUTService.defaultRepository)
        for input in ["https://example.com/owner/repo", "http://github.com/owner/repo", "owner/repo/tree/main",
                      "owner/..", "owner/.", "owner/repo?token=secret", "owner/repo#part", "../repo", "owner//repo"] {
            XCTAssertThrowsError(try OnlineLUTService.normalizeRepository(input), input)
        }
        let data = cube()
        for path in ["../test.cube", "/test.cube", "Looks//test.cube", "Looks/./test.cube", "Looks\\test.cube", "script.js"] {
            XCTAssertThrowsError(try onlineItem(data, path: path).validate(), path)
        }
    }

    func testLocalLUTAndFilmAssignmentsSurviveReopening() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let original = try await store.importLUT(data: cube(), name: "Identity", film: "Classic Chrome")
        let other = try await store.importLUT(data: cube("Swap", swapped: true), name: "Swap", film: "Acros")
        let reopened = try LibraryStore(directory: directory)
        let snapshot = await reopened.snapshot()
        XCTAssertEqual(snapshot.filmBindings["Classic Chrome"], original.id)
        XCTAssertEqual(snapshot.filmBindings["Acros"], other.id)
        let lut = try await reopened.boundLUT(for: "Classic Chrome")
        XCTAssertEqual(try XCTUnwrap(lut).sample(0.2, 0.4, 0.8).red, 0.2, accuracy: 0.000001)
        try await reopened.removeLUTBinding(for: "Classic Chrome")
        let finalStore = try LibraryStore(directory: directory)
        let removed = try await finalStore.boundLUT(for: "Classic Chrome")
        let retained = try await finalStore.boundLUT(for: "Acros")
        XCTAssertNil(removed)
        XCTAssertEqual(try XCTUnwrap(retained).sample(0.2, 0.4, 0.8).red, 0.8, accuracy: 0.000001)
    }

    func testRejectedImportPreservesAssignedLUT() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let old = try await store.importLUT(data: cube(), name: "Good", film: "Original")
        do {
            _ = try await store.importLUT(data: Data("LUT_3D_SIZE 2\n0 0 0".utf8), name: "Broken", film: "Original")
            XCTFail("Incomplete LUT should be rejected")
        } catch { }
        let snapshot = await store.snapshot()
        XCTAssertEqual(snapshot.filmBindings["Original"], old.id)
        XCTAssertEqual(snapshot.localLUTs.count, 1)
    }

    func testOnlineUpdateRetainsExactAssignedVersion() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let firstData = cube("Version 1")
        let secondData = cube("Version 2", swapped: true)
        let first = onlineItem(firstData)
        let second = onlineItem(secondData, revision: String(repeating: "b", count: 40))
        let assigned = try await store.installOnline(first, data: firstData, film: "Classic Chrome")
        let updated = try await store.installOnline(second, data: secondData, film: nil)
        let reopened = try LibraryStore(directory: directory)
        let snapshot = await reopened.snapshot()
        let installedVersion = await reopened.installedVersion(for: first)
        XCTAssertEqual(installedVersion, second.sha)
        XCTAssertEqual(snapshot.filmBindings["Classic Chrome"], assigned.id)
        XCTAssertEqual(Set(snapshot.localLUTs.map(\.id)), Set([assigned.id, updated.id]))
        let bound = try await reopened.boundLUT(for: "Classic Chrome")
        XCTAssertEqual(bound?.title, "Version 1")
        try await reopened.assignLUT(updated, to: "Classic Chrome")
        let changed = try await reopened.boundLUT(for: "Classic Chrome")
        XCTAssertEqual(changed?.title, "Version 2")
    }

    func testOnlineChecksumFailurePreservesVersionAndBinding() async throws {
        let store = try LibraryStore(directory: temporaryDirectory())
        let data = cube()
        let item = onlineItem(data)
        let installed = try await store.installOnline(item, data: data, film: "Original")
        do {
            _ = try await store.installOnline(item, data: cube("Tampered"), film: "Original")
            XCTFail("Content that does not match the tree hash should be rejected")
        } catch { }
        let snapshot = await store.snapshot()
        let version = await store.installedVersion(for: item)
        XCTAssertEqual(version, item.sha)
        XCTAssertEqual(snapshot.filmBindings["Original"], installed.id)
        XCTAssertEqual(snapshot.localLUTs.count, 1)
    }

    func testCachedOnlineLUTRejectsChangedBytes() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let data = cube()
        let item = onlineItem(data)
        let installed = try await store.installOnline(item, data: data, film: nil)
        let initial = try await store.cachedLUT(for: item)
        XCTAssertEqual(initial?.id, installed.id)
        try cube("Changed").write(to: directory.appendingPathComponent(installed.fileName))
        let corrupted = try await store.cachedLUT(for: item)
        XCTAssertNil(corrupted, "Even an already parsed LUT must pass the content hash before download cache reuse")
    }

    func testAndroidEditingPackageRoundTrip() async throws {
        let store = try LibraryStore(directory: temporaryDirectory())
        let android = Data("""
        {"app":"IRO_ANDROID","version":1,"settings":{"film":"Classic Chrome","exposure":-0.5,"red":3,"blue":-2,"dr":200,"grain":8},
         "recipe":{"id":"android-recipe","name":"Android look","film":"Classic Chrome","sourceUrl":"https://fujixweekly.com/recipe/",
                   "sensors":["X-Trans V"],"rawSettings":{"ISO":"Auto, up to ISO 6400","White Balance":"Daylight, +3 Red & -2 Blue"}}}
        """.utf8)
        guard case .editing(let imported) = try await store.importRecipe(data: android) else {
            return XCTFail("Android package should import as editor state")
        }
        XCTAssertEqual(imported.settings.exposure, -0.5)
        XCTAssertEqual(imported.settings.red, 3)
        XCTAssertEqual(imported.settings.dr, 200)
        XCTAssertEqual(imported.recipe?.rawSettings["ISO"], "Auto, up to ISO 6400")
        let exported = try await store.exportRecipe(settings: imported.settings, recipe: imported.recipe)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: exported) as? [String: Any])
        XCTAssertEqual(json["app"] as? String, "IRO_ANDROID", "Android's existing importer recognizes this discriminator")
        XCTAssertEqual(json["version"] as? Int, 1)
        XCTAssertEqual(json["platform"] as? String, "iOS")
        XCTAssertNotNil(json["lutNote"])
        XCTAssertNil(json["lut"])
        guard case .editing(let roundTrip) = try await store.importRecipe(data: exported) else {
            return XCTFail("Exported package should round trip")
        }
        XCTAssertEqual(roundTrip.settings, imported.settings)
        XCTAssertEqual(roundTrip.recipe, imported.recipe)
    }

    func testRecipeCatalogPersistsAndDuplicateIDsStayUnique() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let data = Data("""
        {"recipes":[{"id":"same","name":"First","film":"Acros","rawSettings":{"Color":"-2"}},
                    {"id":"same","name":"Second","film":"Classic Chrome","rawSettings":{"Color":"+1"}}]}
        """.utf8)
        guard case .recipes(let imported) = try await store.importRecipe(data: data) else {
            return XCTFail("Catalog should return recipes")
        }
        XCTAssertEqual(imported.count, 2)
        XCTAssertEqual(Set(imported.map(\.id)).count, 2)
        let reopened = try LibraryStore(directory: directory)
        let snapshot = await reopened.snapshot()
        XCTAssertEqual(snapshot.recipes, imported)
    }

    func testInvalidRecipePackageDoesNotAlterLibrary() async throws {
        let store = try LibraryStore(directory: temporaryDirectory())
        let recipe = Recipe(id: "saved", name: "Saved", rawSettings: ["Color": "+1"])
        try await store.saveRecipe(recipe)
        for json in ["{\"app\":\"IRO_ANDROID\",\"version\":1,\"settings\":{\"exposure\":4}}",
                     "{\"app\":\"IRO_ANDROID\",\"version\":2,\"settings\":{}}",
                     "{\"recipes\":[{\"name\":\"Missing raw settings\"}]}"] {
            do { _ = try await store.importRecipe(data: Data(json.utf8)); XCTFail("Invalid recipe accepted") }
            catch { }
        }
        let snapshot = await store.snapshot()
        XCTAssertEqual(snapshot.recipes, [recipe])
    }

    func testAutomaticChecksDisabledByDefaultAndPreferencesPersist() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let initial = await store.snapshot()
        XCTAssertFalse(initial.onlinePreferences.automaticUpdates)
        XCTAssertEqual(initial.onlinePreferences.repository, OnlineLUTService.defaultRepository)
        let service = OnlineLUTService(store: store)
        let skipped = await service.checkOnOpen()
        XCTAssertNil(skipped)
        let afterCheck = await store.snapshot()
        XCTAssertNil(afterCheck.onlinePreferences.lastAttempt, "Disabled checks must not start a request")
        try await store.setAutomaticUpdates(true)
        try await store.setRepository("https://github.com/owner/looks/")
        let reopened = try LibraryStore(directory: directory)
        let preferences = await reopened.snapshot().onlinePreferences
        XCTAssertTrue(preferences.automaticUpdates)
        XCTAssertEqual(preferences.repository, "owner/looks")
    }

    func testAutomaticChecksRespectDailyAndFailedAttemptCooldowns() async throws {
        let store = try LibraryStore(directory: temporaryDirectory())
        try await store.setAutomaticUpdates(true)
        try await store.saveCatalog([], repository: OnlineLUTService.defaultRepository, at: Date())
        let service = OnlineLUTService(store: store)
        let dailySkip = await service.checkOnOpen()
        XCTAssertNil(dailySkip)
        let afterDailySkip = await store.snapshot()
        XCTAssertNil(afterDailySkip.onlinePreferences.lastAttempt)
        // Changing source clears successful-check history. A recent failed attempt still blocks retry.
        try await store.setRepository("owner/other")
        let attempt = Date()
        try await store.recordAttempt(attempt)
        let hourlySkip = await service.checkOnOpen()
        XCTAssertNil(hourlySkip)
        let afterHourlySkip = await store.snapshot()
        XCTAssertEqual(afterHourlySkip.onlinePreferences.lastAttempt, attempt)
        XCTAssertNil(afterHourlySkip.onlinePreferences.lastChecked)
    }

    func testOversizedDocumentsAreRejectedBeforeParsing() async throws {
        let store = try LibraryStore(directory: temporaryDirectory())
        do {
            _ = try await store.importLUT(data: Data(repeating: 0, count: LibraryStore.maximumLUTBytes + 1),
                                          name: "Oversized", film: "Original")
            XCTFail("Oversized LUT must fail")
        } catch LibraryError.tooLarge { }
        do {
            _ = try await store.importRecipe(data: Data(repeating: 0, count: LibraryStore.maximumJSONBytes + 1))
            XCTFail("Oversized recipe must fail")
        } catch LibraryError.tooLarge { }
        let snapshot = await store.snapshot()
        XCTAssertTrue(snapshot.localLUTs.isEmpty)
        XCTAssertTrue(snapshot.recipes.isEmpty)
        XCTAssertTrue(snapshot.filmBindings.isEmpty)
    }

    func testFailedManifestWriteRollsBackNewLUTFiles() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let originalData = cube("Original")
        let original = try await store.importLUT(data: originalData, name: "Original", film: "Classic Chrome")
        let manifest = directory.appendingPathComponent("library.json")
        let backup = directory.appendingPathComponent("library-backup.json")
        try FileManager.default.moveItem(at: manifest, to: backup)
        try FileManager.default.createDirectory(at: manifest, withIntermediateDirectories: true)
        defer {
            try? FileManager.default.removeItem(at: manifest)
            try? FileManager.default.moveItem(at: backup, to: manifest)
        }
        do {
            _ = try await store.importLUT(data: cube("New local"), name: "New", film: "Classic Chrome")
            XCTFail("Import must fail when the manifest cannot be written")
        } catch { }
        let downloaded = cube("New online")
        do {
            _ = try await store.installOnline(onlineItem(downloaded), data: downloaded, film: "Classic Chrome")
            XCTFail("Download installation must fail when the manifest cannot be written")
        } catch { }
        // Reimporting an existing version must never delete the previously saved file on failure.
        do {
            _ = try await store.importLUT(data: originalData, name: "Original", film: "Acros")
            XCTFail("Reimport must fail when the manifest cannot be written")
        } catch { }
        let files = try FileManager.default.contentsOfDirectory(atPath: directory.path).filter { $0.hasSuffix(".cube") }
        XCTAssertEqual(files, [original.fileName], "Failed imports must not leave unreachable files")
        let snapshot = await store.snapshot()
        XCTAssertEqual(snapshot.localLUTs, [original])
        XCTAssertEqual(snapshot.filmBindings, ["Classic Chrome": original.id])
        let bound = try await store.boundLUT(for: "Classic Chrome")
        XCTAssertEqual(bound?.title, "Original")
    }

    func testAssignmentRejectsChangedFileAndKeepsExistingBinding() async throws {
        let directory = try temporaryDirectory()
        let store = try LibraryStore(directory: directory)
        let original = try await store.importLUT(data: cube("Original"), name: "Original", film: "Classic Chrome")
        let candidate = try await store.importLUT(data: cube("Candidate"), name: "Candidate")
        // Still a valid cube, but it no longer matches the saved content-addressed version.
        try cube("Changed", swapped: true).write(to: directory.appendingPathComponent(candidate.fileName), options: [.atomic])
        do {
            try await store.assignLUT(candidate, to: "Classic Chrome")
            XCTFail("A changed file must not replace the user's working LUT")
        } catch { }
        let snapshot = await store.snapshot()
        XCTAssertEqual(snapshot.filmBindings["Classic Chrome"], original.id)
        let reopened = try LibraryStore(directory: directory)
        do {
            _ = try await reopened.lut(candidate)
            XCTFail("Cold loads must also reject altered content")
        } catch { }
        let bound = try await reopened.boundLUT(for: "Classic Chrome")
        XCTAssertEqual(bound?.title, "Original")
    }

    func testExportRejectsRecipeThatCannotBeImportedAgain() async throws {
        let store = try LibraryStore(directory: temporaryDirectory())
        let invalid = Recipe(name: "Invalid", rawSettings: ["White Balance": String(repeating: "x", count: 501)])
        do {
            _ = try await store.exportRecipe(settings: Settings(), recipe: invalid)
            XCTFail("Export should enforce the same recipe validation as import")
        } catch { }
    }
}
