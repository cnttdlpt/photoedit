import XCTest
@testable import IRO

final class GeminiAutoTests: XCTestCase {
    private var savedAPIKey: Any?
    private var savedModel: Any?
    private var savedModelV2: Any?
    private var sessions: [(URLSession, String)] = []

    override func setUpWithError() throws {
        savedAPIKey = UserDefaults.standard.object(forKey: "iro.gemini.apiKey")
        savedModel = UserDefaults.standard.object(forKey: "iro.gemini.model")
        savedModelV2 = UserDefaults.standard.object(forKey: "iro.gemini.model.v2")
        GeminiAutoService.apiKey = "test-key-not-a-real-credential"
        UserDefaults.standard.removeObject(forKey: "iro.gemini.model")
        UserDefaults.standard.removeObject(forKey: "iro.gemini.model.v2")
    }

    override func tearDownWithError() throws {
        for (session, id) in sessions {
            session.invalidateAndCancel()
            GeminiStubProtocol.remove(id)
        }
        sessions.removeAll()
        UserDefaults.standard.set(savedAPIKey, forKey: "iro.gemini.apiKey")
        UserDefaults.standard.set(savedModel, forKey: "iro.gemini.model")
        UserDefaults.standard.set(savedModelV2, forKey: "iro.gemini.model.v2")
    }

    func testExplicitlyClearedAPIKeyFailsWithoutStartingRequest() async throws {
        GeminiAutoService.apiKey = " \n "
        let (session, stub) = makeSession { _ in .http(200, Data()) }
        XCTAssertFalse(GeminiAutoService.hasAPIKey)
        do {
            _ = try await analyze(session)
            XCTFail("An explicitly cleared key must disable remote analysis")
        } catch GeminiAutoError.missingAPIKey { }
        XCTAssertTrue(stub.requests.isEmpty)
    }

    func testRequestUsesAPIKeyHeaderAndSuppliedImage() async throws {
        let expected = neutralResult()
        let body = try response(parts: [["text": encoded(expected)]])
        let (session, stub) = makeSession { _ in .http(200, body) }
        let actual = try await analyze(session)
        XCTAssertEqual(actual, expected)
        let request = try XCTUnwrap(stub.requests.first)
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.value(forHTTPHeaderField: "x-goog-api-key"), "test-key-not-a-real-credential")
        XCTAssertNil(URLComponents(url: try XCTUnwrap(request.url), resolvingAgainstBaseURL: false)?.query)
        let requestJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: requestBody(request)) as? [String: Any])
        let contents = try XCTUnwrap(requestJSON["contents"] as? [[String: Any]])
        let parts = try XCTUnwrap(contents.first?["parts"] as? [[String: Any]])
        let inlineImage = try XCTUnwrap(parts.compactMap { $0["inline_data"] as? [String: Any] }.first)
        XCTAssertEqual(inlineImage["mime_type"] as? String, "image/jpeg")
        XCTAssertEqual(inlineImage["data"] as? String, Data([0xff, 0xd8, 0xff, 0xd9]).base64EncodedString())
        XCTAssertEqual(stub.requests.count, 1)
    }

    func testJoinsAnswerPartsAndIgnoresThoughtText() async throws {
        var expected = neutralResult()
        expected.summary = "Giữ màu da tự nhiên"
        let json = try encoded(expected)
        let middle = json.index(json.startIndex, offsetBy: json.count / 2)
        let body = try response(parts: [
            ["thought": true, "text": "This is private reasoning, not JSON"],
            ["text": String(json[..<middle])],
            ["text": String(json[middle...])]
        ])
        let (session, _) = makeSession { _ in .http(200, body) }
        let actual = try await analyze(session)
        XCTAssertEqual(actual, expected)
    }

    func testThoughtOnlyResponseCannotBeAppliedAsColorSettings() async throws {
        let body = try response(parts: [["thought": true, "text": encoded(neutralResult())]])
        let (session, _) = makeSession { _ in .http(200, body) }
        await assertRejected(session)
    }

    func testRejectsUnfinishedOrBlockedCandidateEvenWithCompleteJSON() async throws {
        for finishReason in ["MAX_TOKENS", "SAFETY", ""] {
            let body = try response(parts: [["text": encoded(neutralResult())]], finishReason: finishReason)
            let (session, _) = makeSession { _ in .http(200, body) }
            await assertRejected(session)
        }
    }

    func testRejectsWrongMixerBandCount() async throws {
        for field in ["mixerHue", "mixerSat", "mixerLum"] {
            for count in [7, 9] {
                var object = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(neutralResult())) as? [String: Any])
                object[field] = Array(repeating: 0, count: count)
                let json = String(decoding: try JSONSerialization.data(withJSONObject: object), as: UTF8.self)
                let body = try response(parts: [["text": json]])
                let (session, _) = makeSession { _ in .http(200, body) }
                await assertRejected(session)
            }
        }
    }

    func testRejectsNonFiniteNumericResponse() async throws {
        // JSON has no NaN literal. A finite Double can still overflow a Float
        // during decoding, so exercise both numeric representations.
        for field in ["exposure", "paramShadows", "shadowsHue"] {
            var object = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(neutralResult())) as? [String: Any])
            object[field] = "overflow-placeholder"
            let json = String(decoding: try JSONSerialization.data(withJSONObject: object), as: UTF8.self)
                .replacingOccurrences(of: "\"overflow-placeholder\"", with: field == "exposure" ? "1e400" : "1e100")
            let body = try response(parts: [["text": json]])
            let (session, _) = makeSession { _ in .http(200, body) }
            await assertRejected(session)
        }
    }

    func testAuthenticationFailureDoesNotRetryOtherModels() async throws {
        for status in [401, 403] {
            let (session, stub) = makeSession { _ in
                .http(status, Data("{\"error\":{\"message\":\"Invalid test credential\"}}".utf8))
            }
            await assertRejected(session)
            XCTAssertEqual(stub.requests.count, 1, "HTTP \(status) cannot be fixed by switching models")
        }
    }

    func testCancelledTransportDoesNotRetryOtherModels() async throws {
        let (session, stub) = makeSession { _ in .failure(URLError(.cancelled)) }
        do {
            _ = try await analyze(session)
            XCTFail("Cancelled analysis must not produce settings")
        } catch is CancellationError {
        } catch let error as URLError {
            XCTAssertEqual(error.code, .cancelled)
        }
        XCTAssertEqual(stub.requests.count, 1)
    }

    func testUnavailableModelRetriesAndReturnsFallbackAnswer() async throws {
        let expected = neutralResult()
        let body = try response(parts: [["text": encoded(expected)]])
        let (session, stub) = makeSession { index in
            index == 0 ? .http(503, Data()) : .http(200, body)
        }
        let actual = try await analyze(session)
        XCTAssertEqual(actual, expected)
        XCTAssertEqual(stub.requests.count, 2)
        if stub.requests.count == 2 {
            XCTAssertNotEqual(stub.requests[0].url?.path, stub.requests[1].url?.path)
        }
    }

    func testExposureKeepsAICorrectionInsideMeasuredSafetyRange() {
        var result = neutralResult()
        var baseline = Settings()
        baseline.exposure = 0.5
        result.exposure = 0.4
        XCTAssertEqual(result.appliedSettings(base: Settings(), baseline: baseline).exposure, 0.4, accuracy: 0.000001)
        result.exposure = -1.5
        XCTAssertEqual(result.appliedSettings(base: Settings(), baseline: baseline).exposure, 0.2, accuracy: 0.000001)
        result.exposure = 1.5
        XCTAssertEqual(result.appliedSettings(base: Settings(), baseline: baseline).exposure, 0.8, accuracy: 0.000001)
    }

    func testValidationRejectsInvalidNumbersBeforeApplyingSettings() {
        let invalidChanges: [(inout GeminiAutoResult) -> Void] = [
            { $0.exposure = .nan },
            { $0.temperature = .infinity },
            { $0.paramShadows = -.infinity },
            { $0.shadowsHue = .nan },
            { $0.mixerHue[0] = .nan },
            { $0.mixerSat[3] = .infinity },
            { $0.mixerLum[7] = -.infinity },
            { $0.dr = 300 }
        ]
        for change in invalidChanges {
            var result = neutralResult()
            change(&result)
            XCTAssertThrowsError(try result.validated())
        }
        XCTAssertNoThrow(try neutralResult().validated())
    }

    func testNeutralAIResultKeepsPixelsUnchanged() throws {
        let result = neutralResult()
        let settings = result.appliedSettings(base: Settings(), baseline: Settings())
        XCTAssertEqual(settings, Settings(), "A neutral answer must not force exposure, color, or sharpening")
        let source = try RasterImage(width: 2, height: 1, pixels: [80, 110, 140, 255, 180, 150, 120, 100])
        let protected = try AutoBalanceGuard.protect(source, proposed: settings)
        XCTAssertEqual(protected, settings)
        XCTAssertEqual(try ColorEngine.render(source, settings: protected), source)
    }

    func testAutoGuardPreventsCompoundedDarkeningFromAISettings() throws {
        var result = neutralResult()
        result.exposure = -0.3; result.dr = 200; result.highlight = -1; result.shadow = 0.5
        let proposal = result.appliedSettings(base: Settings(), baseline: Settings())
        let fixtures: [[[UInt8]]] = [
            [[128, 128, 128, 255]],
            [[80, 80, 80, 255], [128, 128, 128, 255], [180, 180, 180, 255],
             [188, 135, 105, 255], [70, 145, 205, 255]]
        ]
        for colors in fixtures {
            let source = try brightnessFixture(colors)
            let before = brightnessStats(source, source: source)
            let protected = try AutoBalanceGuard.protect(source, proposed: proposal)
            let output = try ColorEngine.render(source, settings: protected)
            let after = brightnessStats(output, source: source)
            // Before protection, the mixed fixture's mean fell 133.10 -> 116.35
            // and its source-weighted midtones fell 132.86 -> 116.32.
            XCTAssertGreaterThanOrEqual(after.mean, before.mean - 1.000001)
            XCTAssertGreaterThanOrEqual(after.midtones, before.midtones - 1.000001)
            XCTAssertLessThanOrEqual(after.clippedCount, before.clippedCount + source.width * source.height / 100)
        }
    }

    func testAutoGuardCanRecoverBrightHighlightsWithoutDarkeningMidtones() throws {
        let source = try brightnessFixture([
            [90, 90, 90, 255], [128, 128, 128, 255], [160, 160, 160, 255],
            [240, 240, 240, 255], [250, 250, 250, 255], [255, 255, 255, 255]
        ])
        var result = neutralResult()
        result.highlight = -1
        let proposal = result.appliedSettings(base: Settings(), baseline: Settings())
        let protected = try AutoBalanceGuard.protect(source, proposed: proposal)
        let output = try ColorEngine.render(source, settings: protected)
        let before = brightnessStats(source, source: source)
        let after = brightnessStats(output, source: source)
        XCTAssertGreaterThanOrEqual(after.midtones, before.midtones - 1.000001)
        XCTAssertLessThan(after.highlights, before.highlights - 1,
                          "The brightness guard must still allow useful highlight recovery")
        XCTAssertLessThanOrEqual(after.clippedCount, before.clippedCount + source.width * source.height / 100)
    }

    func testAutoGuardIgnoresFullyTransparentPixels() throws {
        let visible = try brightnessFixture([[90, 90, 90, 255], [128, 128, 128, 255], [180, 180, 180, 255]])
        let transparentPixel: [UInt8] = [255, 255, 255, 0]
        let transparent = Array(repeating: transparentPixel, count: visible.width * visible.height * 2).flatMap { $0 }
        let padded = try RasterImage(width: visible.width * 3, height: visible.height,
                                     pixels: visible.pixels + transparent)
        var proposal = Settings()
        proposal.exposure = -0.3; proposal.dr = 200; proposal.highlight = -1; proposal.shadow = 0.5
        XCTAssertEqual(try AutoBalanceGuard.protect(visible, proposed: proposal),
                       try AutoBalanceGuard.protect(padded, proposed: proposal))
    }

    func testAutoGuardRespondsToCancellation() async throws {
        let source = try brightnessFixture([[128, 128, 128, 255]])
        let task = Task.detached {
            withUnsafeCurrentTask { $0?.cancel() }
            return try AutoBalanceGuard.protect(source, proposed: Settings())
        }
        do {
            _ = try await task.value
            XCTFail("Cancelled brightness assessment must stop before applying settings")
        } catch is CancellationError { }
    }

    func testNaturalAutoBalanceClearsColoredGrading() {
        var result = neutralResult()
        result.shadowsHue = 220; result.shadowsSat = 20; result.shadowsLum = -10
        result.midtonesHue = 80; result.midtonesSat = 20; result.midtonesLum = 10
        result.highlightsHue = 40; result.highlightsSat = 20; result.highlightsLum = 10
        var base = LightCraftState()
        base.shadowsWheel = GradingWheel(hue: 180, sat: 50, lum: 20)
        base.midtonesWheel = GradingWheel(hue: 120, sat: 50, lum: 20)
        base.highlightsWheel = GradingWheel(hue: 60, sat: 50, lum: 20)
        let applied = result.appliedLightCraft(base: base)
        XCTAssertTrue(applied.shadowsWheel.isNeutral)
        XCTAssertTrue(applied.midtonesWheel.isNeutral)
        XCTAssertTrue(applied.highlightsWheel.isNeutral)
    }

    private func analyze(_ session: URLSession) async throws -> GeminiAutoResult {
        try await GeminiAutoService.analyze(jpegData: Data([0xff, 0xd8, 0xff, 0xd9]),
                                           histogram: RgbHistogramData(), fallbackBaseline: Settings(), session: session)
    }

    private func assertRejected(_ session: URLSession, file: StaticString = #filePath, line: UInt = #line) async {
        do {
            _ = try await analyze(session)
            XCTFail("Invalid AI response must not be applied", file: file, line: line)
        } catch { }
    }

    private func encoded(_ result: GeminiAutoResult) throws -> String {
        String(decoding: try JSONEncoder().encode(result), as: UTF8.self)
    }

    private func response(parts: [[String: Any]], finishReason: String = "STOP") throws -> Data {
        try JSONSerialization.data(withJSONObject: [
            "candidates": [["finishReason": finishReason, "content": ["parts": parts]]]
        ])
    }

    private func requestBody(_ request: URLRequest) throws -> Data {
        if let data = request.httpBody { return data }
        let stream = try XCTUnwrap(request.httpBodyStream)
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count < 0 { throw stream.streamError ?? URLError(.cannotDecodeRawData) }
            if count == 0 { break }
            data.append(contentsOf: buffer[..<count])
        }
        return data
    }

    private func neutralResult() -> GeminiAutoResult {
        GeminiAutoResult(sceneType: "neutral", summary: "Giữ màu tự nhiên", recommendedFilm: nil,
                         exposure: 0, dr: 100, highlight: 0, shadow: 0, color: 0, colorChrome: 0, chromeBlue: 0,
                         temperature: 0, red: 0, blue: 0, sharpness: 0, clarity: 0, grain: 0, nr: -4,
                         paramShadows: 0, paramDarks: 0, paramLights: 0, paramHighlights: 0,
                         shadowsHue: 0, shadowsSat: 0, shadowsLum: 0,
                         midtonesHue: 0, midtonesSat: 0, midtonesLum: 0,
                         highlightsHue: 0, highlightsSat: 0, highlightsLum: 0,
                         mixerHue: Array(repeating: 0, count: 8), mixerSat: Array(repeating: 0, count: 8),
                         mixerLum: Array(repeating: 0, count: 8))
    }

    private func brightnessFixture(_ colors: [[UInt8]]) throws -> RasterImage {
        try RasterImage(width: colors.count * 16, height: 8,
                        pixels: Array(repeating: colors.flatMap { $0 }, count: 128).flatMap { $0 })
    }

    private struct BrightnessStats {
        var mean: Double
        var midtones: Double
        var highlights: Double
        var clippedCount: Int
    }

    /// Select and weight tonal regions from the SOURCE. Selecting output pixels
    /// would hide darkening when pixels fall out of the midtone range.
    private func brightnessStats(_ image: RasterImage, source: RasterImage) -> BrightnessStats {
        func luminance(_ pixels: [UInt8], _ i: Int) -> Double {
            0.2126 * Double(pixels[i]) + 0.7152 * Double(pixels[i + 1]) + 0.0722 * Double(pixels[i + 2])
        }
        var total = 0.0, visibleWeight = 0.0, midtones = 0.0, midtoneWeight = 0.0
        var highlights = 0.0, highlightWeight = 0.0, clipped = 0
        for i in stride(from: 0, to: source.pixels.count, by: 4) {
            let alpha = Double(source.pixels[i + 3]) / 255
            guard alpha > 0 else { continue }
            let sourceY = luminance(source.pixels, i), outputY = luminance(image.pixels, i)
            let weight = max(0, 1 - abs(sourceY - 128) / 83) * alpha
            total += outputY * alpha; visibleWeight += alpha
            midtones += outputY * weight; midtoneWeight += weight
            if sourceY >= 230 {
                highlights += outputY * alpha; highlightWeight += alpha
            }
            if max(image.pixels[i], max(image.pixels[i + 1], image.pixels[i + 2])) >= 254 { clipped += 1 }
        }
        return BrightnessStats(mean: total / max(visibleWeight, 1), midtones: midtones / max(midtoneWeight, 1),
                               highlights: highlights / max(highlightWeight, 1), clippedCount: clipped)
    }

    private func makeSession(_ respond: @escaping (Int) -> GeminiStubProtocol.Reply) -> (URLSession, GeminiStubProtocol.Stub) {
        let id = UUID().uuidString
        let stub = GeminiStubProtocol.Stub(respond: respond)
        GeminiStubProtocol.register(stub, id: id)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [GeminiStubProtocol.self]
        configuration.httpAdditionalHeaders = ["X-IRO-Test-ID": id]
        let session = URLSession(configuration: configuration)
        sessions.append((session, id))
        return (session, stub)
    }
}

/// All requests are intercepted, including unexpected ones, so these tests
/// cannot upload an image or consume a real Gemini API quota.
private final class GeminiStubProtocol: URLProtocol, @unchecked Sendable {
    enum Reply {
        case http(Int, Data)
        case failure(URLError)
    }

    final class Stub: @unchecked Sendable {
        private let lock = NSLock()
        private var recorded: [URLRequest] = []
        private let respond: (Int) -> Reply

        init(respond: @escaping (Int) -> Reply) { self.respond = respond }

        var requests: [URLRequest] {
            lock.lock(); defer { lock.unlock() }
            return recorded
        }

        func reply(to request: URLRequest) -> Reply {
            lock.lock()
            let index = recorded.count
            recorded.append(request)
            lock.unlock()
            return respond(index)
        }
    }

    private static let lock = NSLock()
    private static var stubs: [String: Stub] = [:]

    static func register(_ stub: Stub, id: String) {
        lock.lock(); defer { lock.unlock() }
        stubs[id] = stub
    }

    static func remove(_ id: String) {
        lock.lock(); defer { lock.unlock() }
        stubs.removeValue(forKey: id)
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        let stub = request.value(forHTTPHeaderField: "X-IRO-Test-ID").flatMap { Self.stubs[$0] }
        Self.lock.unlock()
        guard let stub else {
            client?.urlProtocol(self, didFailWithError: URLError(.unsupportedURL))
            return
        }
        switch stub.reply(to: request) {
        case .http(let status, let data):
            guard let url = request.url,
                  let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil,
                                                 headerFields: ["Content-Type": "application/json"]) else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        case .failure(let error):
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() { }
}
