import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

enum ImportKind { case image, lut, recipe }
private enum EditorSheet: String, Identifiable {
    case recipes, luts, online, frameDetails, export, details, help, editRecipe, geminiSettings
    var id: String { rawValue }
}

struct RecipeDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data = Data()) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

// All 14 Adjust Parameters matching Android AdjustParam
enum AdjustParam: String, CaseIterable, Identifiable {
    case exposure = "Phơi sáng"
    case lutAmount = "Độ mạnh LUT"
    case highlight = "Vùng sáng"
    case shadow = "Vùng tối"
    case color = "Độ đậm"
    case colorChrome = "Chrome Đỏ"
    case chromeBlue = "Chrome Xanh"
    case temp = "Nhiệt độ"
    case red = "Shift Đỏ"
    case blue = "Shift Xanh"
    case grain = "Hạt Grain"
    case sharpness = "Độ nét"
    case clarity = "Độ trong"
    case nr = "Khử nhiễu"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .exposure: return "sun.max"
        case .lutAmount: return "camera.filters"
        case .highlight: return "sun.haze"
        case .shadow: return "moon"
        case .color: return "paintpalette"
        case .colorChrome: return "drop.halffull"
        case .chromeBlue: return "drop"
        case .temp: return "thermometer.medium"
        case .red: return "r.circle"
        case .blue: return "b.circle"
        case .grain: return "circle.dotted"
        case .sharpness: return "triangle"
        case .clarity: return "aqi.medium"
        case .nr: return "shield.lefthalf.filled"
        }
    }

    var defaultValue: Double {
        switch self {
        case .lutAmount: return 100.0
        case .nr: return -4.0
        default: return 0.0
        }
    }
}

enum LightCraftSubTab: Int, CaseIterable, Identifiable {
    case curve = 0
    case wheels = 1
    case hsl = 2
    case mask = 3

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .curve: return "Đường cong"
        case .wheels: return "Pha màu"
        case .hsl: return "HSL 8 kênh"
        case .mask: return "Mặt nạ & Preset"
        }
    }
}

// MARK: - Precision Tick Ruler Slider (Matches Android TickRulerView)
struct TickRulerSlider: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double
    var defaultValue: Double = 0.0
    let onEditingChanged: ((Bool) -> Void)?
    var onDoubleTapReset: (() -> Void)? = nil

    @State private var dragStartValue: Double = 0
    @State private var isDragging: Bool = false

    private let tickSpacing: CGFloat = 8.5
    private let majorTickInterval: Int = 5

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let centerX = width / 2
            let span = range.upperBound - range.lowerBound

            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder(Studio.line, lineWidth: 1.0)
                    )

                Canvas { context, size in
                    guard span > 0 && step > 0 else { return }
                    let totalSteps = span / step
                    let currentStepIndex = (value - range.lowerBound) / step
                    let defaultStepIndex = (defaultValue - range.lowerBound) / step
                    let centerOffset = CGFloat(currentStepIndex) * tickSpacing

                    let minStep = max(0, Int((centerOffset - centerX) / tickSpacing) - 2)
                    let maxStep = min(Int(totalSteps), Int((centerOffset + centerX) / tickSpacing) + 2)

                    guard minStep <= maxStep else { return }

                    let activeLo = min(currentStepIndex, defaultStepIndex) - 0.1
                    let activeHi = max(currentStepIndex, defaultStepIndex) + 0.1

                    for i in minStep...maxStep {
                        let x = centerX + (CGFloat(i) * tickSpacing - centerOffset)
                        let isMajor = (i % majorTickInterval == 0)
                        let stepVal = Double(i) * step + range.lowerBound
                        let isZero = abs(stepVal - defaultValue) < (step * 0.4)
                        let inActiveSpan = Double(i) >= activeLo && Double(i) <= activeHi && abs(value - defaultValue) >= (step * 0.4)

                        var path = Path()
                        let tickHeight: CGFloat = isMajor ? 16 : 9
                        let startY: CGFloat = isMajor ? 12 : 16
                        path.move(to: CGPoint(x: x, y: startY))
                        path.addLine(to: CGPoint(x: x, y: startY + tickHeight))

                        let color: Color
                        if isZero || inActiveSpan {
                            color = Studio.ink
                        } else if isMajor {
                            color = Studio.ink.opacity(0.55)
                        } else {
                            color = Studio.muted.opacity(0.35)
                        }
                        let lineWidth: CGFloat = isZero ? 1.8 : (isMajor ? 1.3 : 0.9)
                        context.stroke(path, with: .color(color), lineWidth: lineWidth)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                // Needle Indicator at top center
                VStack(spacing: 0) {
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 7.5))
                        .foregroundStyle(Studio.ink)

                    Rectangle()
                        .fill(Studio.ink)
                        .frame(width: 1.5, height: 6)
                }
                .offset(y: 2)
            }
            .contentShape(Rectangle())
            .onTapGesture(count: 2) {
                Haptics.light()
                onDoubleTapReset?()
            }
            .gesture(
                DragGesture(minimumDistance: 1)
                    .onChanged { gesture in
                        if !isDragging {
                            isDragging = true
                            dragStartValue = value
                            onEditingChanged?(true)
                        }
                        let stepsDelta = -Double(gesture.translation.width / tickSpacing)
                        var newVal = dragStartValue + stepsDelta * step
                        newVal = max(range.lowerBound, min(range.upperBound, newVal))
                        newVal = (newVal / step).rounded() * step

                        if abs(newVal - value) >= (step * 0.8) {
                            Haptics.selection()
                            value = newVal
                        }
                    }
                    .onEnded { _ in
                        isDragging = false
                        onEditingChanged?(false)
                    }
            )
        }
        .frame(height: 38)
    }
}

struct EditorView: View {
    @EnvironmentObject private var editor: EditorModel
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var sheet: EditorSheet?
    @State private var selectedTab: EditorToolTab = .adjust
    @State private var selectedAdjust: AdjustParam = .exposure
    @State private var lightCraftSubTab: LightCraftSubTab = .curve
    @State private var curveChannel: ToneCurveChannel = .parametric
    @State private var selectedHslBand: Int = 0
    @State private var isComparing = false
    @State private var showPhotoPicker = false
    @State private var showImporter = false
    @State private var importKind: ImportKind = .image
    @State private var pendingImport: ImportKind?
    @State private var recipeDocument = RecipeDocument()
    @State private var showRecipeExporter = false
    @State private var showDeleteConfirmation = false
    @State private var sliderUndoRecorded = false
    @State private var inlineFilterQuery = ""
    @State private var inlineFilmChip = "Tất cả"

    // Zoom & Pan tương tác trên Trackpad và màn hình cảm ứng
    @State private var zoomScale: CGFloat = 1.0
    @State private var panOffset: CGSize = .zero
    @GestureState private var magnifyGestureScale: CGFloat = 1.0
    @GestureState private var dragGestureOffset: CGSize = .zero

    private static let hslBandNames = [
        "Đỏ", "Cam", "Vàng", "Lục", "Lam ngọc", "Xanh dương", "Tím", "Hồng"
    ]
    private static let hslBandColors: [Color] = [
        Color(red: 234/255, green: 67/255, blue: 53/255),
        Color(red: 250/255, green: 138/255, blue: 42/255),
        Color(red: 251/255, green: 188/255, blue: 4/255),
        Color(red: 52/255, green: 168/255, blue: 83/255),
        Color(red: 36/255, green: 182/255, blue: 198/255),
        Color(red: 66/255, green: 133/255, blue: 244/255),
        Color(red: 142/255, green: 92/255, blue: 232/255),
        Color(red: 228/255, green: 84/255, blue: 164/255)
    ]

    var body: some View {
        GeometryReader { geo in
            let isWideStudio = geo.size.width >= 700
            ZStack {
                AntigravityBackground()

                if editor.hasPhoto {
                    if isWideStudio {
                        wideStudioLayout
                    } else {
                        compactPortraitLayout
                    }
                } else {
                    EmptyPhoto(
                        onPickPhoto: { showPhotoPicker = true },
                        onImportFile: { requestImport(.image) },
                        onOpenRecipes: { sheet = .recipes },
                        onOpenLuts: { sheet = .luts },
                        onOpenOnline: { sheet = .online },
                        onOpenHelp: { sheet = .help },
                        onLoadSample: { film in
                            Task { await editor.openStudioSample(filmKeyword: film) }
                        },
                        onSelectFrameSample: {
                            selectedTab = .frame
                            Task { await editor.openStudioSample(defaultFrameStyle: 1) }
                        }
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                }
            }
        }
        .foregroundStyle(Studio.ink)
        .photosPicker(isPresented: $showPhotoPicker, selection: $selectedPhoto, matching: .images, preferredItemEncoding: .current)
        .onChange(of: selectedPhoto) { _, item in
            if let item { Task { await editor.openPhoto(item); selectedPhoto = nil; isComparing = false; zoomScale = 1.0; panOffset = .zero } }
        }
        .onChange(of: editor.recipe?.id) { _, _ in isComparing = false }
        .onChange(of: editor.previewSize) { _, _ in
            zoomScale = 1.0
            panOffset = .zero
        }
        .onChange(of: editor.hasPhoto) { _, hasPhoto in
            if !hasPhoto {
                zoomScale = 1.0
                panOffset = .zero
            }
        }
        .confirmationDialog("Đóng ảnh hiện tại?", isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
            Button("Đóng ảnh", role: .destructive) {
                Haptics.medium()
                isComparing = false
                editor.clearPhoto()
            }
            Button("Hủy", role: .cancel) { }
        } message: {
            Text("Ảnh đang chỉnh sửa sẽ được đóng và quay về màn hình ban đầu.")
        }
        .sheet(item: $sheet, onDismiss: {
            if let pending = pendingImport { pendingImport = nil; importKind = pending; showImporter = true }
        }) { item in
            Group {
                switch item {
                case .recipes: RecipeLibraryView()
                case .luts: LUTLibraryView(importAction: { requestImport(.lut) })
                case .online: OnlineLibraryView()
                case .frameDetails: FrameEditorView()
                case .export: ExportView()
                case .details: RecipeDetailsView(recipe: editor.recipe, notes: editor.notes)
                case .editRecipe: RecipeEditView()
                case .help: HelpView()
                case .geminiSettings: GeminiSettingsView()
                }
            }
            .alert("IRO", isPresented: Binding(get: { editor.errorMessage != nil }, set: { if !$0 { editor.errorMessage = nil } })) {
                Button("Đóng", role: .cancel) { editor.errorMessage = nil }
            } message: { Text(editor.errorMessage ?? "") }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: importKind == .image ? [.image] : [.data], allowsMultipleSelection: false) { result in
            switch result {
            case .success(let urls): if let url = urls.first { Task { await editor.importFile(url, kind: importKind) } }
            case .failure(let error): editor.show(error)
            }
        }
        .fileExporter(isPresented: $showRecipeExporter, document: recipeDocument, contentType: .json, defaultFilename: "IRO-recipe") { result in
            if case .failure(let error) = result { editor.show(error) }
        }
        .alert("IRO", isPresented: Binding(get: { editor.errorMessage != nil && sheet == nil }, set: { if !$0 { editor.errorMessage = nil } })) {
            Button("Đóng", role: .cancel) { editor.errorMessage = nil }
        } message: { Text(editor.errorMessage ?? "") }
        .dropDestination(for: URL.self) { urls, _ in
            guard let url = urls.first else { return false }
            let ext = url.pathExtension.lowercased()
            let kind: ImportKind = ext == "cube" ? .lut : (ext == "json" ? .recipe : .image)
            Task { await editor.importFile(url, kind: kind) }
            return true
        }
    }

    // MARK: - Compact Portrait Layout (Phones)
    private var compactPortraitLayout: some View {
        VStack(spacing: 8) {
            header
                .padding(.horizontal, 16)
                .padding(.top, 4)

            modeSwitcherPill
                .padding(.horizontal, 16)

            stage
                .padding(.horizontal, 14)

            cleanBottomControls
                .disabled(editor.busy)
                .padding(.horizontal, 14)
                .padding(.bottom, 6)
        }
    }

    // MARK: - Wide 3-Zone Studio Layout (Tablets / Landscape)
    private var wideStudioLayout: some View {
        HStack(spacing: 12) {
            // Zone 1: Left Navigation Rail
            VStack(spacing: 10) {
                Button {
                    showDeleteConfirmation = true
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Studio.ink)
                        .liquidGlassCircle(size: 38)
                }
                .buttonStyle(.plain)
                .disabled(editor.busy)

                HStack(spacing: 6) {
                    Button {
                        Haptics.light()
                        Task { await editor.undo() }
                    } label: {
                        Image(systemName: "arrow.uturn.backward")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(editor.canUndo ? Studio.ink : Studio.muted.opacity(0.35))
                            .liquidGlassCircle(size: 32)
                    }
                    .buttonStyle(.plain)
                    .keyboardShortcut("z", modifiers: [.command])
                    .disabled(!editor.canUndo || editor.busy)

                    Button {
                        Haptics.light()
                        Task { await editor.redo() }
                    } label: {
                        Image(systemName: "arrow.uturn.forward")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(editor.canRedo ? Studio.ink : Studio.muted.opacity(0.35))
                            .liquidGlassCircle(size: 32)
                    }
                    .buttonStyle(.plain)
                    .keyboardShortcut("z", modifiers: [.command, .shift])
                    .disabled(!editor.canRedo || editor.busy)
                }

                Divider()
                    .padding(.horizontal, 8)

                ForEach(EditorToolTab.allCases) { tab in
                    let isSelected = selectedTab == tab
                    Button {
                        Haptics.light()
                        withAnimation(.spring(response: 0.24, dampingFraction: 0.8)) {
                            selectedTab = tab
                        }
                    } label: {
                        VStack(spacing: 4) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(isSelected ? Studio.ink : Color.white)
                                    .frame(width: 46, height: 36)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .strokeBorder(isSelected ? Color.clear : Studio.line, lineWidth: 1.0)
                                    )
                                Image(systemName: tab.icon)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundStyle(isSelected ? Color.white : Studio.ink)
                            }
                            Text(tab.rawValue)
                                .font(.system(size: 10, weight: isSelected ? .bold : .medium, design: .rounded))
                                .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                    }
                    .buttonStyle(.plain)
                }

                Spacer()

                Button {
                    Haptics.medium()
                    Task { await editor.autoBalance() }
                } label: {
                    ZStack {
                        Circle()
                            .fill(editor.isAutoBalanced ? Studio.ink : Color.white)
                            .frame(width: 38, height: 38)
                            .overlay(Circle().strokeBorder(Studio.line, lineWidth: 1.0))
                        if editor.isAutoAnalyzing {
                            ProgressView()
                                .controlSize(.small)
                                .tint(editor.isAutoBalanced ? Color.white : Studio.ink)
                        } else {
                            Image(systemName: "wand.and.stars")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(editor.isAutoBalanced ? Color.white : Studio.ink)
                        }
                    }
                }
                .buttonStyle(.plain)
                .disabled(editor.isAutoAnalyzing)

                overflowMenuButton
            }
            .padding(.vertical, 12)
            .frame(width: 82)
            .liquidGlassCard(cornerRadius: 24)

            // Zone 2: Center Photo Stage + Top Status Bar
            VStack(spacing: 8) {
                HStack {
                    Text(editor.status)
                        .font(.system(size: 11.5, weight: .medium, design: .rounded))
                        .foregroundStyle(Studio.muted)
                        .lineLimit(1)

                    Spacer()

                    exportPillButton
                }
                .padding(.horizontal, 8)

                stage
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Zone 3: Right Docked Inspector Panel
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 12) {
                    switch selectedTab {
                    case .adjust:
                        adjustSection
                    case .filters:
                        wideFiltersSection
                    case .frame:
                        wideFramesSection
                    case .lightcraft:
                        lightCraftSection
                    }
                }
                .padding(14)
            }
            .frame(width: 390)
            .liquidGlassCard(cornerRadius: 24)
            .disabled(editor.busy)
        }
        .padding(12)
    }

    // MARK: - Header (Back, Undo, Redo, Export, Menu)
    private var header: some View {
        HStack(spacing: 12) {
            Button {
                showDeleteConfirmation = true
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Studio.ink)
                    .liquidGlassCircle(size: 38)
            }
            .buttonStyle(.plain)
            .disabled(editor.busy)

            Spacer()

            HStack(spacing: 8) {
                Button {
                    Haptics.light()
                    Task { await editor.undo() }
                } label: {
                    Image(systemName: "arrow.uturn.backward")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(editor.canUndo ? Studio.ink : Studio.muted.opacity(0.35))
                        .liquidGlassCircle(size: 36)
                }
                .buttonStyle(.plain)
                .disabled(!editor.canUndo || editor.busy)

                Button {
                    Haptics.light()
                    Task { await editor.redo() }
                } label: {
                    Image(systemName: "arrow.uturn.forward")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(editor.canRedo ? Studio.ink : Studio.muted.opacity(0.35))
                        .liquidGlassCircle(size: 36)
                }
                .buttonStyle(.plain)
                .disabled(!editor.canRedo || editor.busy)
            }

            Spacer()

            HStack(spacing: 8) {
                Button {
                    Haptics.medium()
                    Task { await editor.rotateClockwise() }
                } label: {
                    Image(systemName: "rotate.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(editor.hasPhoto ? Studio.ink : Studio.muted.opacity(0.35))
                        .liquidGlassCircle(size: 36)
                }
                .buttonStyle(.plain)
                .disabled(!editor.hasPhoto || editor.busy)
                .help("Xoay ảnh 90° theo chiều kim đồng hồ (⌘R)")
                .keyboardShortcut("r", modifiers: .command)

                exportPillButton
                overflowMenuButton
            }
        }
        .frame(height: 42)
    }

    private var exportPillButton: some View {
        Button { sheet = .export } label: {
            HStack(spacing: 4) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 11, weight: .semibold))
                Text("Xuất")
                    .font(.system(size: 12.5, weight: .bold, design: .rounded))
            }
            .foregroundStyle(Color.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
            .background(
                Capsule()
                    .fill(Studio.ink)
            )
        }
        .buttonStyle(.plain)
        .disabled(!editor.hasPhoto || editor.busy)
    }

    private var overflowMenuButton: some View {
        Menu {
            Button("Xoay 90° sang phải", systemImage: "rotate.right") {
                Haptics.medium()
                Task { await editor.rotateClockwise() }
            }.disabled(!editor.hasPhoto || editor.busy)
            Button("Xoay 90° sang trái", systemImage: "rotate.left") {
                Haptics.medium()
                Task { await editor.rotateCounterClockwise() }
            }.disabled(!editor.hasPhoto || editor.busy)
            Divider()
            Button("Tự động cân bằng ảnh", systemImage: "wand.and.stars") {
                Haptics.medium()
                Task { await editor.autoBalance() }
            }
            Button("Cài đặt Gemini AI Auto…", systemImage: "sparkles") {
                sheet = .geminiSettings
            }
            Divider()
            Button("Mở ảnh mới", systemImage: "photo.badge.plus") { showPhotoPicker = true }
            Button("Mở ảnh từ Tệp", systemImage: "folder") { requestImport(.image) }
            Button("Dùng thử ảnh mẫu Studio", systemImage: "sparkles") {
                Task { await editor.openStudioSample() }
            }
            Divider()
            Button("Đặt lại ảnh gốc", systemImage: "arrow.counterclockwise") {
                Haptics.light()
                Task { await editor.reset() }
            }
            Button("Đóng ảnh hiện tại", systemImage: "trash", role: .destructive) {
                showDeleteConfirmation = true
            }
            Divider()
            Button("Kho 392 công thức màu", systemImage: "camera.filters") { sheet = .recipes }
            Button("Quản lý LUT", systemImage: "square.stack.3d.up") { sheet = .luts }
            Button("Kho LUT trực tuyến", systemImage: "globe") { sheet = .online }
            Divider()
            Button("Thông số Recipe", systemImage: "list.bullet.rectangle") { sheet = .details }
            Button("Sửa bản riêng Recipe", systemImage: "pencil") { sheet = .editRecipe }.disabled(editor.recipe == nil)
            Button("Lưu công thức JSON", systemImage: "square.and.arrow.up") {
                Task { do { recipeDocument = RecipeDocument(data: try await editor.recipeData()); showRecipeExporter = true } catch { editor.show(error) } }
            }
            Button("Nhập công thức JSON", systemImage: "square.and.arrow.down") { requestImport(.recipe) }
            Divider()
            Button("Hướng dẫn", systemImage: "info.circle") { sheet = .help }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Studio.ink)
                .liquidGlassCircle(size: 36)
        }
        .disabled(!editor.ready || editor.busy)
    }

    // MARK: - Sub-Header Mode Switcher Pill (4 Tabs)
    private var modeSwitcherPill: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(EditorToolTab.allCases) { tab in
                    let isSelected = selectedTab == tab
                    Button {
                        Haptics.light()
                        withAnimation(.spring(response: 0.24, dampingFraction: 0.8)) {
                            selectedTab = tab
                        }
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: tab.icon)
                                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                            Text(tab.rawValue)
                                .font(.system(size: 12, weight: isSelected ? .bold : .medium, design: .rounded))
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                        .background(
                            Group {
                                if isSelected {
                                    Capsule()
                                        .fill(Color.white)
                                        .shadow(color: Color.black.opacity(0.06), radius: 4, x: 0, y: 1)
                                } else {
                                    Color.clear
                                }
                            }
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(
                Capsule()
                    .fill(Studio.surface)
                    .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
            )
        }
    }

    // MARK: - Spacious Photo Stage (Zoom, Pan, Compare & Mask Overlay)
    private var stage: some View {
        let maskOverlayActive = selectedTab == .lightcraft
            && editor.frameStyle == 0
            && (editor.lightCraft.maskMode == .radial || editor.lightCraft.maskMode == .linear)

        let effectiveScale = max(1.0, min(5.0, zoomScale * magnifyGestureScale))
        let effectiveOffset = CGSize(
            width: panOffset.width + dragGestureOffset.width,
            height: panOffset.height + dragGestureOffset.height
        )

        return GeometryReader { geo in
            ZStack(alignment: .top) {
                if let image = isComparing ? editor.originalImage : (editor.editedImage ?? editor.originalImage) {
                    ZStack {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .shadow(color: Color.black.opacity(0.07), radius: 16, x: 0, y: 6)
                            .scaleEffect(effectiveScale)
                            .offset(effectiveOffset)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)

                        if maskOverlayActive {
                            StageMaskOverlayView(
                                state: $editor.lightCraft,
                                imageSize: editor.previewSize,
                                interactiveEnabled: maskOverlayActive,
                                onEditingBegan: { editor.recordUndo() },
                                onMaskGeometryChanged: { editor.changed() }
                            )
                        }
                    }
                    .contentShape(Rectangle())
                    .simultaneousGesture(
                        // Magnification gesture (pinch to zoom on trackpad or screen)
                        MagnificationGesture()
                            .updating($magnifyGestureScale) { value, state, _ in
                                if !maskOverlayActive {
                                    state = value
                                }
                            }
                            .onEnded { value in
                                guard !maskOverlayActive else { return }
                                let newScale = max(1.0, min(5.0, zoomScale * value))
                                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                    zoomScale = newScale
                                    if zoomScale <= 1.05 {
                                        zoomScale = 1.0
                                        panOffset = .zero
                                    }
                                }
                            }
                    )
                    .simultaneousGesture(
                        // Drag gesture to pan when zoomed in
                        DragGesture()
                            .updating($dragGestureOffset) { value, state, _ in
                                if !maskOverlayActive && zoomScale > 1.05 {
                                    state = value.translation
                                }
                            }
                            .onEnded { value in
                                guard !maskOverlayActive && zoomScale > 1.05 else { return }
                                let maxPanX = (geo.size.width * (zoomScale - 1.0)) / 2.0 + 40
                                let maxPanY = (geo.size.height * (zoomScale - 1.0)) / 2.0 + 40
                                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                    panOffset.width = min(maxPanX, max(-maxPanX, panOffset.width + value.translation.width))
                                    panOffset.height = min(maxPanY, max(-maxPanY, panOffset.height + value.translation.height))
                                }
                            }
                    )
                    .onTapGesture(count: 2) {
                        guard !maskOverlayActive else { return }
                        withAnimation(.spring(response: 0.26, dampingFraction: 0.8)) {
                            if zoomScale > 1.15 {
                                zoomScale = 1.0
                                panOffset = .zero
                            } else {
                                zoomScale = 2.5
                                panOffset = .zero
                            }
                        }
                    }

                    // Floating Controls Overlay (Top)
                    HStack {
                        // Nút So sánh gốc
                        Button {
                            Haptics.light()
                            isComparing.toggle()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: isComparing ? "eye.slash.fill" : "eye.fill")
                                    .font(.system(size: 11, weight: .semibold))
                                Text(isComparing ? "Ảnh gốc" : "So sánh")
                                    .font(.system(size: 11.5, weight: .bold, design: .rounded))
                            }
                            .foregroundStyle(isComparing ? Studio.googleRed : Studio.ink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(
                                Capsule()
                                    .fill(Color.white.opacity(0.92))
                                    .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
                            )
                        }
                        .buttonStyle(.plain)

                        Spacer()

                        // Pill Phóng to / Thu nhỏ khi scale > 1.05
                        if zoomScale > 1.05 {
                            Button {
                                withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                    zoomScale = 1.0
                                    panOffset = .zero
                                }
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "arrow.down.right.and.arrow.up.left")
                                        .font(.system(size: 9.5, weight: .bold))
                                    Text("\(Int(round(effectiveScale * 100)))% · Về vừa khung")
                                        .font(.system(size: 11, weight: .bold, design: .rounded))
                                }
                                .foregroundStyle(Color.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(
                                    Capsule()
                                        .fill(Color.black.opacity(0.75))
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(12)
                }

                if editor.isLoading {
                    ProgressView()
                        .tint(Studio.ink)
                        .padding(16)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
        }
    }

    // MARK: - Bottom Controls Card
    private var cleanBottomControls: some View {
        VStack(spacing: 10) {
            switch selectedTab {
            case .adjust:
                adjustSection
            case .filters:
                filtersSection
            case .frame:
                framesSection
            case .lightcraft:
                lightCraftSection
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 14)
        .liquidGlassCard(cornerRadius: 26)
    }

    // MARK: - 1. Adjust Section (RGB Histogram + DR Toggle + Tick Ruler + 14 Tool Strip)
    private var adjustSection: some View {
        VStack(spacing: 8) {
            // Live 64-Bin RGB Histogram
            RgbHistogramView(data: editor.histogram)
                .frame(height: 60)

            // Gemini AI Scene Insight Banner (shown after AI Auto runs)
            if let insight = editor.aiAutoInsight {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Studio.ink)
                        .padding(.top, 2)

                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 6) {
                            Text("Gemini AI · \(insight.sceneType)")
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(Studio.ink)
                            Spacer()
                            if let recFilm = insight.recommendedFilm, !recFilm.isEmpty {
                                Button {
                                    Haptics.selection()
                                    Task { await editor.applyRecommendedFilm(recFilm) }
                                } label: {
                                    HStack(spacing: 3) {
                                        Image(systemName: "camera.filters")
                                            .font(.system(size: 9.5, weight: .bold))
                                        Text("Thử \(recFilm)")
                                            .font(.system(size: 10, weight: .bold, design: .rounded))
                                    }
                                    .foregroundStyle(Color.white)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Capsule().fill(Studio.ink))
                                }
                                .buttonStyle(.plain)
                            }
                            Button {
                                editor.aiAutoInsight = nil
                            } label: {
                                Image(systemName: "xmark")
                                    .font(.system(size: 9.5, weight: .bold))
                                    .foregroundStyle(Studio.muted)
                                    .frame(width: 18, height: 18)
                            }
                            .buttonStyle(.plain)
                        }

                        if !insight.summary.isEmpty {
                            Text(insight.summary)
                                .font(.system(size: 11, weight: .medium, design: .rounded))
                                .foregroundStyle(Studio.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Studio.surface)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(Studio.line, lineWidth: 1.0)
                        )
                )
            }

            // Upper: Active Tool Header, AI Pill, DR Badge & Value Readout
            HStack(spacing: 6) {
                HStack(spacing: 5) {
                    Image(systemName: selectedAdjust.icon)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Studio.ink)
                    Text(selectedAdjust.rawValue)
                        .font(.system(size: 12.5, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                }

                Spacer()

                // Gemini AI Key Config Badge Button
                Button {
                    Haptics.selection()
                    sheet = .geminiSettings
                } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 9.5, weight: .bold))
                        Text(GeminiAutoService.hasAPIKey ? "AI Bật" : "AI")
                            .font(.system(size: 10.5, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(GeminiAutoService.hasAPIKey ? Color.white : Studio.muted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(
                        Capsule()
                            .fill(GeminiAutoService.hasAPIKey ? Studio.ink : Studio.surface)
                            .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                    )
                }
                .buttonStyle(.plain)

                // Dynamic Range DR100 / DR200 / DR400 Badge Button
                Button {
                    Haptics.selection()
                    editor.recordUndo()
                    let nextDr = editor.settings.dr == 100 ? 200 : (editor.settings.dr == 200 ? 400 : 100)
                    editor.settings.dr = nextDr
                    editor.isAutoBalanced = false
                    editor.changed(immediate: true)
                } label: {
                    Text("DR\(editor.settings.dr)")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(editor.settings.dr != 100 ? Color.white : Studio.muted)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            Capsule()
                                .fill(editor.settings.dr != 100 ? Studio.ink : Studio.surface)
                                .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                        )
                }
                .buttonStyle(.plain)

                // Formatted Value Badge
                Text(formattedValue(for: selectedAdjust))
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(isModified(selectedAdjust) ? Studio.ink : Studio.muted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(
                        Capsule()
                            .fill(isModified(selectedAdjust) ? Studio.surface : Color.white)
                            .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                    )

                if isModified(selectedAdjust) {
                    Button {
                        Haptics.light()
                        editor.recordUndo()
                        resetValue(for: selectedAdjust)
                    } label: {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 9.5, weight: .bold))
                            .foregroundStyle(Studio.muted)
                            .liquidGlassCircle(size: 24)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 2)

            // Center: Precision Tick Ruler Slider
            TickRulerSlider(
                value: binding(for: selectedAdjust),
                range: range(for: selectedAdjust),
                step: step(for: selectedAdjust),
                defaultValue: selectedAdjust.defaultValue,
                onEditingChanged: { _ in sliderUndoRecorded = false },
                onDoubleTapReset: {
                    if isModified(selectedAdjust) {
                        editor.recordUndo()
                        resetValue(for: selectedAdjust)
                    }
                }
            )

            // Lower: Horizontal Circular Tool Strip (Auto + 14 Params)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    // Auto Balance Magic Wand Circle
                    Button {
                        Haptics.medium()
                        Task { await editor.autoBalance() }
                    } label: {
                        VStack(spacing: 5) {
                            ZStack {
                                Circle()
                                    .fill(editor.isAutoBalanced ? Studio.ink : Color.white)
                                    .frame(width: 46, height: 46)
                                    .overlay(
                                        Circle().strokeBorder(editor.isAutoBalanced ? Color.clear : Studio.line, lineWidth: 1.0)
                                    )

                                if editor.isAutoAnalyzing {
                                    ProgressView()
                                        .controlSize(.small)
                                        .tint(editor.isAutoBalanced ? Color.white : Studio.ink)
                                } else {
                                    Image(systemName: GeminiAutoService.hasAPIKey ? "sparkles" : "wand.and.stars")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(editor.isAutoBalanced ? Color.white : Studio.ink)
                                }
                            }

                            Text(editor.isAutoAnalyzing ? "Đang AI…" : (GeminiAutoService.hasAPIKey ? "Auto AI" : "Tự động"))
                                .font(.system(size: 10.5, weight: editor.isAutoBalanced ? .bold : .medium, design: .rounded))
                                .foregroundStyle(editor.isAutoBalanced ? Studio.ink : Studio.muted)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(editor.isAutoAnalyzing)

                    // All 14 Adjustment Parameter Circles
                    ForEach(AdjustParam.allCases) { param in
                        let isSelected = selectedAdjust == param
                        let modified = isModified(param)
                        Button {
                            Haptics.selection()
                            selectedAdjust = param
                        } label: {
                            VStack(spacing: 5) {
                                ZStack {
                                    Circle()
                                        .fill(isSelected ? Studio.ink : Color.white)
                                        .frame(width: 46, height: 46)
                                        .overlay(
                                            Circle().strokeBorder(isSelected ? Color.clear : Studio.line, lineWidth: 1.0)
                                        )

                                    if isSelected && modified {
                                        Text(formattedValue(for: param))
                                            .font(.system(size: 11, weight: .bold, design: .rounded))
                                            .foregroundStyle(Color.white)
                                            .minimumScaleFactor(0.65)
                                            .lineLimit(1)
                                            .padding(.horizontal, 4)
                                    } else {
                                        Image(systemName: param.icon)
                                            .font(.system(size: 16, weight: .medium))
                                            .foregroundStyle(isSelected ? Color.white : Studio.ink)
                                    }

                                    if !isSelected && modified {
                                        Circle()
                                            .fill(Studio.ink)
                                            .frame(width: 5, height: 5)
                                            .offset(x: 15, y: -15)
                                    }
                                }

                                Text(param.rawValue)
                                    .font(.system(size: 10.5, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 2)
            }
        }
    }

    // MARK: - 2. Filters Section (Film Look Carousel)
    private var filtersSection: some View {
        VStack(spacing: 10) {
            HStack {
                HStack(spacing: 5) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Studio.ink)
                    Text(editor.recipe?.name ?? "Nguyên bản")
                        .font(.system(size: 12.5, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                        .lineLimit(1)
                }

                Spacer()

                Button { sheet = .recipes } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "plus")
                            .font(.system(size: 10, weight: .bold))
                        Text("Kho 392 màu")
                            .font(.system(size: 11.5, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(Studio.ink)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Studio.surface)
                            .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                    )
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 4)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    // "Gốc" (Original Look)
                    Button {
                        Haptics.selection()
                        Task { await editor.originalLook() }
                    } label: {
                        VStack(spacing: 5) {
                            ZStack {
                                Circle()
                                    .fill(editor.recipe == nil ? Studio.ink : Color.white)
                                    .frame(width: 48, height: 48)
                                    .overlay(
                                        Circle().strokeBorder(editor.recipe == nil ? Color.clear : Studio.line, lineWidth: 1.0)
                                    )

                                Image(systemName: "circle.lefthalf.filled")
                                    .font(.system(size: 17))
                                    .foregroundStyle(editor.recipe == nil ? Color.white : Studio.ink)
                            }

                            Text("Gốc")
                                .font(.system(size: 11, weight: editor.recipe == nil ? .bold : .medium, design: .rounded))
                                .foregroundStyle(editor.recipe == nil ? Studio.ink : Studio.muted)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)

                    // Curated Recipes (Live Thumbnails)
                    ForEach(editor.recipes.prefix(30)) { recipe in
                        let isSelected = editor.recipe?.id == recipe.id
                        Button {
                            Haptics.selection()
                            Task { await editor.apply(recipe) }
                        } label: {
                            VStack(spacing: 5) {
                                FilterThumbView(recipe: recipe, isSelected: isSelected)

                                Text(recipe.name)
                                    .font(.system(size: 11, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.75)
                                    .frame(width: 58)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 2)
            }
        }
    }

    // MARK: - 3. Frames Section (All 30 Styles + EXIF Editor)
    private var framesSection: some View {
        VStack(spacing: 10) {
            HStack {
                HStack(spacing: 5) {
                    Image(systemName: "photo.artframe")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Studio.ink)
                    Text(editor.frameStyle == 0 ? "Khung: Không viền" : "\(PhotoFrame.styles[editor.frameStyle].components(separatedBy: " · ").first ?? "Khung #\(editor.frameStyle)")")
                        .font(.system(size: 12.5, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .foregroundStyle(Studio.ink)
                }

                Spacer()

                Button { sheet = .frameDetails } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "pencil")
                            .font(.system(size: 10, weight: .bold))
                        Text("Sửa EXIF")
                            .font(.system(size: 11.5, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(Studio.ink)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Studio.surface)
                            .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                    )
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 4)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(0..<PhotoFrame.styles.count, id: \.self) { idx in
                        let isSelected = editor.frameStyle == idx
                        Button {
                            Haptics.selection()
                            editor.applyFrame(style: idx, info: editor.photoInfo)
                        } label: {
                            VStack(spacing: 5) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(isSelected ? Studio.ink : Color.white)
                                        .frame(width: 52, height: 48)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                                .strokeBorder(isSelected ? Color.clear : Studio.line, lineWidth: 1.0)
                                        )

                                    Image(systemName: frameIcon(for: idx))
                                        .font(.system(size: 16, weight: .medium))
                                        .foregroundStyle(isSelected ? Color.white : Studio.ink)
                                }

                                Text(frameLabel(for: idx))
                                    .font(.system(size: 10.5, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                                    .lineLimit(1)
                                    .frame(width: 54)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 2)
            }
        }
    }

    // MARK: - Wide Studio Filters & Frames (Multi-Column Grid for Mac & iPad)
    private static let quickFilmChips = [
        "Tất cả", "Classic Chrome", "Velvia", "Classic Neg", "Astia", "Acros", "Nostalgic Neg", "Eterna", "Pro Neg"
    ]

    private var wideFilteredRecipes: [Recipe] {
        editor.recipes.filter { recipe in
            let matchesChip = inlineFilmChip == "Tất cả" ||
                recipe.film.localizedStandardContains(inlineFilmChip) ||
                recipe.name.localizedStandardContains(inlineFilmChip)
            let matchesQuery = inlineFilterQuery.trimmingCharacters(in: .whitespaces).isEmpty ||
                (recipe.name + " " + recipe.film).localizedStandardContains(inlineFilterQuery)
            return matchesChip && matchesQuery
        }
    }

    private var wideFiltersSection: some View {
        let filteredList = wideFilteredRecipes
        let columns = [
            GridItem(.flexible(), spacing: 8),
            GridItem(.flexible(), spacing: 8),
            GridItem(.flexible(), spacing: 8)
        ]
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 5) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Studio.ink)
                    Text(editor.recipe?.name ?? "Nguyên bản")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                        .lineLimit(1)
                }

                Spacer()

                Button { sheet = .recipes } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 10, weight: .bold))
                        Text("Mở rộng (\(editor.recipes.count))")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(Studio.ink)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Studio.surface)
                            .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                    )
                }
                .buttonStyle(.plain)
            }

            // Search bar inside inspector
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Studio.muted)
                TextField("Tìm trong 392 màu film…", text: $inlineFilterQuery)
                    .font(.system(size: 12, design: .rounded))
                    .textFieldStyle(.plain)
                if !inlineFilterQuery.isEmpty {
                    Button {
                        inlineFilterQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(Studio.muted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.white)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Studio.line, lineWidth: 1))
            )

            // Quick Film Simulation Chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Self.quickFilmChips, id: \.self) { chip in
                        let selected = inlineFilmChip == chip
                        Button {
                            Haptics.selection()
                            inlineFilmChip = chip
                        } label: {
                            Text(chip)
                                .font(.system(size: 11, weight: selected ? .bold : .medium, design: .rounded))
                                .foregroundStyle(selected ? Color.white : Studio.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(
                                    Capsule()
                                        .fill(selected ? Studio.ink : Color.white)
                                        .overlay(Capsule().strokeBorder(selected ? Color.clear : Studio.line, lineWidth: 1))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            HStack {
                Text("Hiển thị \(filteredList.count) màu film")
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(Studio.muted)
                Spacer()
                if editor.recipe != nil {
                    Button {
                        Haptics.selection()
                        Task { await editor.originalLook() }
                    } label: {
                        Text("Về ảnh gốc")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(Studio.ink)
                    }
                    .buttonStyle(.plain)
                }
            }

            LazyVGrid(columns: columns, spacing: 10) {
                // Original look tile
                Button {
                    Haptics.selection()
                    Task { await editor.originalLook() }
                } label: {
                    VStack(spacing: 5) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(editor.recipe == nil ? Studio.ink : Color.white)
                                .frame(height: 76)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .strokeBorder(editor.recipe == nil ? Studio.ink : Studio.line, lineWidth: editor.recipe == nil ? 2.0 : 1.0)
                                )
                            Image(systemName: "circle.lefthalf.filled")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(editor.recipe == nil ? Color.white : Studio.ink)
                        }
                        Text("Nguyên bản")
                            .font(.system(size: 11, weight: editor.recipe == nil ? .bold : .medium, design: .rounded))
                            .foregroundStyle(editor.recipe == nil ? Studio.ink : Studio.muted)
                            .lineLimit(1)
                        Text("Gốc")
                            .font(.system(size: 9.5, weight: .regular, design: .rounded))
                            .foregroundStyle(Studio.muted)
                            .lineLimit(1)
                    }
                }
                .buttonStyle(.plain)

                ForEach(filteredList) { recipe in
                    let isSelected = editor.recipe?.id == recipe.id
                    Button {
                        Haptics.selection()
                        Task { await editor.apply(recipe) }
                    } label: {
                        WideFilterCardView(recipe: recipe, isSelected: isSelected)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var wideFramesSection: some View {
        let columns = [
            GridItem(.flexible(), spacing: 8),
            GridItem(.flexible(), spacing: 8),
            GridItem(.flexible(), spacing: 8)
        ]
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 5) {
                    Image(systemName: "photo.artframe")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Studio.ink)
                    Text(editor.frameStyle == 0 ? "Khung: Không viền" : "\(PhotoFrame.styles[editor.frameStyle].components(separatedBy: " · ").first ?? "Khung #\(editor.frameStyle)")")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .foregroundStyle(Studio.ink)
                }

                Spacer()

                Button { sheet = .frameDetails } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "pencil")
                            .font(.system(size: 10, weight: .bold))
                        Text("Sửa EXIF")
                            .font(.system(size: 11.5, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(Studio.ink)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Studio.surface)
                            .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                    )
                }
                .buttonStyle(.plain)
            }

            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(0..<PhotoFrame.styles.count, id: \.self) { idx in
                    let isSelected = editor.frameStyle == idx
                    Button {
                        Haptics.selection()
                        editor.applyFrame(style: idx, info: editor.photoInfo)
                    } label: {
                        VStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(isSelected ? Studio.ink : Color.white)
                                    .frame(height: 60)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .strokeBorder(isSelected ? Color.clear : Studio.line, lineWidth: 1.0)
                                    )

                                Image(systemName: frameIcon(for: idx))
                                    .font(.system(size: 18, weight: .medium))
                                    .foregroundStyle(isSelected ? Color.white : Studio.ink)
                            }

                            Text(frameLabel(for: idx))
                                .font(.system(size: 11, weight: isSelected ? .bold : .medium, design: .rounded))
                                .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - 4. LightCraft Pro Darkroom Section (Curves, 3-Way Wheels, 8-Band HSL, Mask & Presets)
    private var lightCraftSection: some View {
        VStack(spacing: 10) {
            // Sub-tab switcher strip
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(LightCraftSubTab.allCases) { sub in
                        let active = lightCraftSubTab == sub
                        Button {
                            Haptics.selection()
                            lightCraftSubTab = sub
                        } label: {
                            Text(sub.title)
                                .font(.system(size: 11.5, weight: active ? .bold : .medium, design: .rounded))
                                .foregroundStyle(active ? Color.white : Studio.ink)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule()
                                        .fill(active ? Studio.ink : Studio.surface)
                                        .overlay(Capsule().strokeBorder(active ? Color.clear : Studio.line, lineWidth: 1.0))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            switch lightCraftSubTab {
            case .curve:
                lightCraftCurvePanel
            case .wheels:
                lightCraftWheelsPanel
            case .hsl:
                lightCraftHslPanel
            case .mask:
                lightCraftMaskAndPresetsPanel
            }
        }
    }

    private var lightCraftCurvePanel: some View {
        VStack(spacing: 8) {
            HStack(spacing: 6) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 5) {
                        ForEach(ToneCurveChannel.allCases) { ch in
                            let active = curveChannel == ch
                            Button {
                                Haptics.selection()
                                curveChannel = ch
                            } label: {
                                Text(ch.rawValue)
                                    .font(.system(size: 10.5, weight: active ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(active ? Color.white : Studio.ink)
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 4.5)
                                    .background(
                                        Capsule()
                                            .fill(active ? ch.color : Color.white)
                                            .overlay(Capsule().strokeBorder(active ? Color.clear : Studio.line, lineWidth: 1.0))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Spacer()

                if editor.lightCraft.hasCurveEdits {
                    Button {
                        Haptics.light()
                        editor.recordUndo()
                        editor.lightCraft.resetCurves()
                        editor.changed(immediate: true)
                    } label: {
                        Text("Đặt lại")
                            .font(.system(size: 10.5, weight: .bold, design: .rounded))
                            .foregroundStyle(Studio.muted)
                    }
                    .buttonStyle(.plain)
                }
            }

            ToneCurveGraphView(
                state: $editor.lightCraft,
                channel: curveChannel,
                onEditingBegan: { editor.recordUndo() },
                onCurveChanged: { editor.changed() }
            )
            .frame(height: 152)

            if curveChannel == .parametric {
                VStack(spacing: 6) {
                    HStack(spacing: 10) {
                        compactLightCraftSlider(title: "Tối sâu", value: lightCraftBinding(\.paramShadows), range: -100...100)
                        compactLightCraftSlider(title: "Bóng tối", value: lightCraftBinding(\.paramDarks), range: -100...100)
                    }
                    HStack(spacing: 10) {
                        compactLightCraftSlider(title: "Vùng sáng", value: lightCraftBinding(\.paramLights), range: -100...100)
                        compactLightCraftSlider(title: "Sáng gắt", value: lightCraftBinding(\.paramHighlights), range: -100...100)
                    }
                }
            } else {
                Text("Chạm để thêm điểm · Kéo để uốn đường cong · Chạm đúp điểm giữa để xóa")
                    .font(.system(size: 10.5, weight: .regular))
                    .foregroundStyle(Studio.muted)
                    .lineLimit(1)
            }
        }
    }

    private var lightCraftWheelsPanel: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Pha màu 3 vùng (OkLCh)")
                    .font(.system(size: 11.5, weight: .bold, design: .rounded))
                    .foregroundStyle(Studio.ink)
                Spacer()
                if editor.lightCraft.hasGradingEdits {
                    Button {
                        Haptics.light()
                        editor.recordUndo()
                        editor.lightCraft.resetGrading()
                        editor.changed(immediate: true)
                    } label: {
                        Text("Đặt lại")
                            .font(.system(size: 10.5, weight: .bold, design: .rounded))
                            .foregroundStyle(Studio.muted)
                    }
                    .buttonStyle(.plain)
                }
            }

            GradingWheelsView(
                state: $editor.lightCraft,
                onEditingBegan: { editor.recordUndo() },
                onGradingChanged: { editor.changed() }
            )
            .frame(height: 132)

            HStack(spacing: 8) {
                compactLightCraftSlider(title: "Sáng Tối", value: lightCraftBinding(\.shadowsWheel.lum), range: -50...50)
                compactLightCraftSlider(title: "Sáng Trung", value: lightCraftBinding(\.midtonesWheel.lum), range: -50...50)
                compactLightCraftSlider(title: "Sáng Cao", value: lightCraftBinding(\.highlightsWheel.lum), range: -50...50)
            }

            HStack(spacing: 10) {
                compactLightCraftSlider(title: "Hòa trộn", value: lightCraftBinding(\.gradingBlending), range: 0...100, defaultValue: 50)
                compactLightCraftSlider(title: "Cân bằng", value: lightCraftBinding(\.gradingBalance), range: -100...100)
            }
        }
    }

    private var lightCraftHslPanel: some View {
        VStack(spacing: 8) {
            HStack {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(0..<8, id: \.self) { idx in
                            let active = selectedHslBand == idx
                            let bandModified = abs(editor.lightCraft.mixerHue[idx]) > 0.5
                                || abs(editor.lightCraft.mixerSat[idx]) > 0.5
                                || abs(editor.lightCraft.mixerLum[idx]) > 0.5
                            Button {
                                Haptics.selection()
                                selectedHslBand = idx
                            } label: {
                                HStack(spacing: 5) {
                                    Circle()
                                        .fill(Self.hslBandColors[idx])
                                        .frame(width: 8, height: 8)
                                    Text(Self.hslBandNames[idx])
                                        .font(.system(size: 11, weight: active ? .bold : .medium, design: .rounded))
                                }
                                .foregroundStyle(active ? Color.white : Studio.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(
                                    Capsule()
                                        .fill(active ? Studio.ink : Color.white)
                                        .overlay(
                                            Capsule().strokeBorder(bandModified && !active ? Studio.ink : Studio.line, lineWidth: 1.0)
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                if editor.lightCraft.hasMixerEdits {
                    Button {
                        Haptics.light()
                        editor.recordUndo()
                        editor.lightCraft.resetMixer()
                        editor.changed(immediate: true)
                    } label: {
                        Text("Đặt lại")
                            .font(.system(size: 10.5, weight: .bold, design: .rounded))
                            .foregroundStyle(Studio.muted)
                    }
                    .buttonStyle(.plain)
                }
            }

            VStack(spacing: 6) {
                compactLightCraftSlider(
                    title: "Đổi màu (Hue)",
                    value: hslBandBinding(kind: 0, index: selectedHslBand),
                    range: -100...100
                )
                compactLightCraftSlider(
                    title: "Độ bão hòa (Sat)",
                    value: hslBandBinding(kind: 1, index: selectedHslBand),
                    range: -100...100
                )
                compactLightCraftSlider(
                    title: "Độ sáng (Lum)",
                    value: hslBandBinding(kind: 2, index: selectedHslBand),
                    range: -100...100
                )
            }
        }
    }

    private var lightCraftMaskAndPresetsPanel: some View {
        VStack(spacing: 8) {
            // Mask Mode Selector
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(MaskMode.allCases) { mode in
                        let active = editor.lightCraft.maskMode == mode
                        Button {
                            Haptics.selection()
                            editor.recordUndo()
                            editor.lightCraft.maskMode = mode
                            editor.changed(immediate: true)
                        } label: {
                            Text(mode.label)
                                .font(.system(size: 10.5, weight: active ? .bold : .medium, design: .rounded))
                                .foregroundStyle(active ? Color.white : Studio.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(
                                    Capsule()
                                        .fill(active ? Studio.ink : Color.white)
                                        .overlay(Capsule().strokeBorder(active ? Color.clear : Studio.line, lineWidth: 1.0))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if editor.lightCraft.maskMode != .none {
                HStack(spacing: 6) {
                    Button {
                        Haptics.selection()
                        editor.recordUndo()
                        editor.lightCraft.maskInvert.toggle()
                        editor.changed(immediate: true)
                    } label: {
                        Text(editor.lightCraft.maskInvert ? "Đảo ngược: Bật" : "Đảo ngược")
                            .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                            .foregroundStyle(editor.lightCraft.maskInvert ? Color.white : Studio.ink)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(
                                Capsule()
                                    .fill(editor.lightCraft.maskInvert ? Studio.ink : Studio.surface)
                                    .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                            )
                    }
                    .buttonStyle(.plain)

                    Button {
                        Haptics.selection()
                        editor.lightCraft.showMaskOverlay.toggle()
                        editor.changed(immediate: true)
                    } label: {
                        Text(editor.lightCraft.showMaskOverlay ? "Vùng đỏ: Bật" : "Hiện vùng đỏ")
                            .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                            .foregroundStyle(editor.lightCraft.showMaskOverlay ? Color.white : Studio.ink)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 4)
                            .background(
                                Capsule()
                                    .fill(editor.lightCraft.showMaskOverlay ? Studio.googleRed : Studio.surface)
                                    .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                            )
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Button {
                        Haptics.light()
                        editor.recordUndo()
                        let currentMode = editor.lightCraft.maskMode
                        editor.lightCraft.resetMask()
                        editor.lightCraft.maskMode = currentMode
                        editor.changed(immediate: true)
                    } label: {
                        Text("Đặt lại")
                            .font(.system(size: 10.5, weight: .bold, design: .rounded))
                            .foregroundStyle(Studio.muted)
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: 8) {
                    compactLightCraftSlider(title: "Phơi sáng", value: lightCraftBinding(\.maskExposure), range: -2.0...2.0, step: 0.1)
                    compactLightCraftSlider(title: "Độ trong", value: lightCraftBinding(\.maskClarity), range: -100...100)
                }
                HStack(spacing: 8) {
                    compactLightCraftSlider(title: "Độ ấm", value: lightCraftBinding(\.maskTemp), range: -100...100)
                    compactLightCraftSlider(title: "Bão hòa", value: lightCraftBinding(\.maskSaturation), range: -100...100)
                }
            }

            // 12 Built-in Darkroom Presets
            VStack(alignment: .leading, spacing: 5) {
                Text("12 Preset Buồng tối LightCraft")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(Studio.muted)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        let isOriginalPreset = editor.lightCraft.activePresetId.isEmpty
                        Button {
                            Haptics.selection()
                            editor.recordUndo()
                            editor.lightCraft.resetAll()
                            editor.changed(immediate: true)
                        } label: {
                            Text("Gốc")
                                .font(.system(size: 11, weight: isOriginalPreset ? .bold : .medium, design: .rounded))
                                .foregroundStyle(isOriginalPreset ? Color.white : Studio.ink)
                                .padding(.horizontal, 11)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule()
                                        .fill(isOriginalPreset ? Studio.ink : Color.white)
                                        .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                                )
                        }
                        .buttonStyle(.plain)

                        ForEach(LightCraftEngine.presets) { preset in
                            let active = editor.lightCraft.activePresetId == preset.id
                            Button {
                                Haptics.selection()
                                editor.recordUndo()
                                preset.apply(to: &editor.lightCraft, settings: &editor.settings)
                                editor.changed(immediate: true)
                            } label: {
                                Text("\(preset.name) · \(preset.subtitle)")
                                    .font(.system(size: 11, weight: active ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(active ? Color.white : Studio.ink)
                                    .padding(.horizontal, 11)
                                    .padding(.vertical, 6)
                                    .background(
                                        Capsule()
                                            .fill(active ? Studio.ink : Color.white)
                                            .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    private func compactLightCraftSlider(
        title: String,
        value: Binding<Double>,
        range: ClosedRange<Double>,
        step: Double = 1.0,
        defaultValue: Double = 0.0
    ) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(title)
                    .font(.system(size: 10.5, weight: .semibold, design: .rounded))
                    .foregroundStyle(Studio.ink)
                Spacer()
                Text(step < 1 ? String(format: "%+.1f", value.wrappedValue) : String(format: "%+.0f", value.wrappedValue))
                    .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                    .foregroundStyle(abs(value.wrappedValue - defaultValue) > 0.05 ? Studio.ink : Studio.muted)
            }
            Slider(
                value: value,
                in: range,
                onEditingChanged: { editing in
                    if !editing { sliderUndoRecorded = false }
                }
            )
            .tint(Studio.ink)
        }
    }

    private func lightCraftBinding(_ keyPath: WritableKeyPath<LightCraftState, Float>) -> Binding<Double> {
        Binding(
            get: { Double(editor.lightCraft[keyPath: keyPath]) },
            set: { newVal in
                guard !editor.busy else { return }
                if !sliderUndoRecorded {
                    editor.recordUndo()
                    sliderUndoRecorded = true
                }
                editor.lightCraft[keyPath: keyPath] = Float(newVal)
                editor.changed()
            }
        )
    }

    private func hslBandBinding(kind: Int, index: Int) -> Binding<Double> {
        let safeIdx = max(0, min(7, index))
        return Binding(
            get: {
                switch kind {
                case 0: return Double(editor.lightCraft.mixerHue[safeIdx])
                case 1: return Double(editor.lightCraft.mixerSat[safeIdx])
                default: return Double(editor.lightCraft.mixerLum[safeIdx])
                }
            },
            set: { newVal in
                guard !editor.busy else { return }
                if !sliderUndoRecorded {
                    editor.recordUndo()
                    sliderUndoRecorded = true
                }
                let fVal = Float(newVal)
                switch kind {
                case 0: editor.lightCraft.mixerHue[safeIdx] = fVal
                case 1: editor.lightCraft.mixerSat[safeIdx] = fVal
                default: editor.lightCraft.mixerLum[safeIdx] = fVal
                }
                editor.changed()
            }
        )
    }

    private func frameIcon(for idx: Int) -> String {
        switch idx {
        case 0: return "slash.circle"
        case 1: return "square"
        case 2: return "rectangle.portrait"
        case 3: return "rectangle"
        default: return "photo.artframe"
        }
    }

    private func frameLabel(for idx: Int) -> String {
        if idx == 0 { return "Tắt" }
        let raw = PhotoFrame.styles[idx]
        if let firstPart = raw.components(separatedBy: " · ").first {
            return firstPart
        }
        return "#\(idx)"
    }

    // MARK: - Helpers for Adjust Parameters
    private func binding(for param: AdjustParam) -> Binding<Double> {
        let key: WritableKeyPath<Settings, Double>
        switch param {
        case .exposure: key = \.exposure
        case .lutAmount: key = \.lutAmount
        case .highlight: key = \.highlight
        case .shadow: key = \.shadow
        case .color: key = \.color
        case .colorChrome: key = \.colorChrome
        case .chromeBlue: key = \.chromeBlue
        case .temp: key = \.temperature
        case .red: key = \.red
        case .blue: key = \.blue
        case .grain: key = \.grain
        case .sharpness: key = \.sharpness
        case .clarity: key = \.clarity
        case .nr: key = \.nr
        }
        return Binding(get: { editor.settings[keyPath: key] }, set: { value in
            guard !editor.busy, editor.settings[keyPath: key] != value else { return }
            if !sliderUndoRecorded { editor.recordUndo(); sliderUndoRecorded = true }
            editor.settings[keyPath: key] = value
            editor.isAutoBalanced = false
            editor.changed()
        })
    }

    private func range(for param: AdjustParam) -> ClosedRange<Double> {
        switch param {
        case .exposure: return -3.0...3.0
        case .lutAmount: return 0.0...100.0
        case .highlight: return -2.0...4.0
        case .shadow: return -2.0...4.0
        case .color: return -4.0...4.0
        case .colorChrome, .chromeBlue: return 0.0...2.0
        case .temp: return -100.0...100.0
        case .red: return -9.0...9.0
        case .blue: return -9.0...9.0
        case .grain: return 0.0...30.0
        case .sharpness: return -4.0...4.0
        case .clarity: return -5.0...5.0
        case .nr: return -4.0...4.0
        }
    }

    private func step(for param: AdjustParam) -> Double {
        switch param {
        case .exposure: return 0.1
        case .lutAmount: return 5.0
        case .highlight, .shadow: return 0.5
        default: return 1.0
        }
    }

    private func formattedValue(for param: AdjustParam) -> String {
        let v = binding(for: param).wrappedValue
        switch param {
        case .exposure, .highlight, .shadow: return String(format: "%+.1f", v)
        case .lutAmount: return String(format: "%.0f%%", v)
        case .colorChrome, .chromeBlue:
            let iv = Int(v.rounded())
            return iv == 0 ? "Tắt" : (iv == 1 ? "Nhẹ" : "Mạnh")
        case .grain: return String(format: "%.0f", v)
        default: return String(format: "%+.0f", v)
        }
    }

    private func isModified(_ param: AdjustParam) -> Bool {
        let v = binding(for: param).wrappedValue
        return abs(v - param.defaultValue) > 0.01
    }

    private func resetValue(for param: AdjustParam) {
        switch param {
        case .exposure: editor.settings.exposure = 0.0
        case .lutAmount: editor.settings.lutAmount = 100.0
        case .highlight: editor.settings.highlight = 0.0
        case .shadow: editor.settings.shadow = 0.0
        case .color: editor.settings.color = 0.0
        case .colorChrome: editor.settings.colorChrome = 0
        case .chromeBlue: editor.settings.chromeBlue = 0
        case .temp: editor.settings.temperature = 0.0
        case .red: editor.settings.red = 0.0
        case .blue: editor.settings.blue = 0.0
        case .grain: editor.settings.grain = 0.0
        case .sharpness: editor.settings.sharpness = 0.0
        case .clarity: editor.settings.clarity = 0.0
        case .nr: editor.settings.nr = -4.0
        }
        editor.isAutoBalanced = false
        editor.changed()
    }

    private func requestImport(_ kind: ImportKind) {
        if sheet != nil { pendingImport = kind; sheet = nil }
        else { importKind = kind; showImporter = true }
    }
}

// MARK: - Filter Thumbnail
private struct FilterThumbView: View {
    @EnvironmentObject var editor: EditorModel
    let recipe: Recipe
    let isSelected: Bool
    @State private var thumbnail: UIImage?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Studio.surface)
                .frame(width: 58, height: 48)

            if let thumbnail {
                Image(uiImage: thumbnail)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 58, height: 48)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            if isSelected {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Studio.ink, lineWidth: 2.2)
                    .frame(width: 58, height: 48)
            } else {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Studio.line, lineWidth: 1.0)
                    .frame(width: 58, height: 48)
            }
        }
        .task(id: "\(recipe.id)-\(editor.thumbnailVersion)") {
            thumbnail = await editor.recipeThumbnail(recipe)
        }
    }
}

private struct WideFilterCardView: View {
    @EnvironmentObject var editor: EditorModel
    let recipe: Recipe
    let isSelected: Bool
    @State private var thumbnail: UIImage?

    var body: some View {
        VStack(spacing: 5) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Studio.surface)
                    .frame(height: 76)

                if let thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .frame(height: 76)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                } else {
                    Image(systemName: "photo")
                        .font(.system(size: 16))
                        .foregroundStyle(Studio.muted)
                }

                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(isSelected ? Studio.ink : Studio.line, lineWidth: isSelected ? 2.2 : 1.0)
                    .frame(height: 76)
            }

            Text(recipe.name)
                .font(.system(size: 11, weight: isSelected ? .bold : .medium, design: .rounded))
                .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.8)

            Text(recipe.film)
                .font(.system(size: 9.5, weight: .regular, design: .rounded))
                .foregroundStyle(Studio.muted)
                .lineLimit(1)
        }
        .task(id: "\(recipe.id)-\(editor.thumbnailVersion)") {
            thumbnail = await editor.recipeThumbnail(recipe)
        }
    }
}
