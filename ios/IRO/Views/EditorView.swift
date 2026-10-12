import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

enum ImportKind { case image, lut, recipe }
private enum EditorSheet: String, Identifiable {
    case recipes, luts, online, frameDetails, export, details, help, editRecipe
    var id: String { rawValue }
}

struct RecipeDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data = Data()) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

// Adjust Parameters in single clean list
enum AdjustParam: String, CaseIterable, Identifiable {
    case exposure = "Phơi sáng"
    case highlight = "Vùng sáng"
    case shadow = "Vùng tối"
    case color = "Độ đậm"
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
        case .highlight: return "sun.haze"
        case .shadow: return "moon"
        case .color: return "paintpalette"
        case .temp: return "thermometer.medium"
        case .red: return "r.circle"
        case .blue: return "b.circle"
        case .grain: return "circle.dotted"
        case .sharpness: return "triangle"
        case .clarity: return "aqi.medium"
        case .nr: return "shield.lefthalf.filled"
        }
    }
}

// MARK: - Precision Tick Ruler Slider
struct TickRulerSlider: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double
    let onEditingChanged: ((Bool) -> Void)?
    
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
                // Background subtle glass surface
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.white.opacity(0.65))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder(Color.black.opacity(0.05), lineWidth: 0.8)
                    )
                
                // Tick lines canvas
                Canvas { context, size in
                    guard span > 0 && step > 0 else { return }
                    let totalSteps = span / step
                    let currentStepIndex = (value - range.lowerBound) / step
                    let centerOffset = CGFloat(currentStepIndex) * tickSpacing
                    
                    let minStep = max(0, Int((centerOffset - centerX) / tickSpacing) - 2)
                    let maxStep = min(Int(totalSteps), Int((centerOffset + centerX) / tickSpacing) + 2)
                    
                    guard minStep <= maxStep else { return }
                    
                    for i in minStep...maxStep {
                        let x = centerX + (CGFloat(i) * tickSpacing - centerOffset)
                        let isMajor = (i % majorTickInterval == 0)
                        let isZero = abs(Double(i) * step + range.lowerBound) < (step * 0.4)
                        
                        var path = Path()
                        let tickHeight: CGFloat = isMajor ? 16 : 9
                        let startY: CGFloat = isMajor ? 12 : 16
                        path.move(to: CGPoint(x: x, y: startY))
                        path.addLine(to: CGPoint(x: x, y: startY + tickHeight))
                        
                        let color: Color = isZero ? Studio.warmAmber : (isMajor ? Studio.ink.opacity(0.65) : Studio.muted.opacity(0.4))
                        let lineWidth: CGFloat = isZero ? 1.8 : (isMajor ? 1.3 : 0.9)
                        context.stroke(path, with: .color(color), lineWidth: lineWidth)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                
                // Needle Indicator at top center (Downward Triangle ▼)
                VStack(spacing: 0) {
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 7.5))
                        .foregroundStyle(Studio.warmAmber)
                    
                    Rectangle()
                        .fill(Studio.warmAmber)
                        .frame(width: 1.5, height: 6)
                }
                .offset(y: 2)
            }
            .contentShape(Rectangle())
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
    @State private var isComparing = false
    @State private var showPhotoPicker = false
    @State private var showImporter = false
    @State private var importKind: ImportKind = .image
    @State private var pendingImport: ImportKind?
    @State private var recipeDocument = RecipeDocument()
    @State private var showRecipeExporter = false
    @State private var showDeleteConfirmation = false
    @State private var sliderUndoRecorded = false

    var body: some View {
        ZStack {
            AntigravityBackground()
            
            VStack(spacing: 8) {
                if editor.hasPhoto {
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
                } else {
                    EmptyPhoto(
                        onPickPhoto: { showPhotoPicker = true },
                        onImportFile: { requestImport(.image) },
                        onOpenLuts: { sheet = .luts },
                        onOpenOnline: { sheet = .online },
                        onOpenHelp: { sheet = .help }
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                }
            }
        }
        .foregroundStyle(Studio.ink)
        .photosPicker(isPresented: $showPhotoPicker, selection: $selectedPhoto, matching: .images, preferredItemEncoding: .current)
        .onChange(of: selectedPhoto) { _, item in
            if let item { Task { await editor.openPhoto(item); selectedPhoto = nil; isComparing = false } }
        }
        .onChange(of: editor.recipe?.id) { _, _ in isComparing = false }
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
    }

    // MARK: - Header (Back, Undo, Redo, Export, Menu)
    private var header: some View {
        HStack(spacing: 12) {
            // Left Action / Back Button
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
            
            // Center: Undo and Redo Buttons
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
            
            // Right: Export & Menu
            HStack(spacing: 8) {
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
                            .fill(Studio.warmAmber)
                            .shadow(color: Studio.warmAmber.opacity(0.35), radius: 6, x: 0, y: 2)
                    )
                }
                .buttonStyle(.plain)
                .disabled(!editor.hasPhoto || editor.busy)
                
                Menu {
                    Button("Tự động cân bằng ảnh", systemImage: "wand.and.stars") {
                        Haptics.medium()
                        Task { await editor.autoBalance() }
                    }
                    Divider()
                    Button("Mở ảnh mới", systemImage: "photo.badge.plus") { showPhotoPicker = true }
                    Button("Mở ảnh từ Tệp", systemImage: "folder") { requestImport(.image) }
                    Divider()
                    Button("Đặt lại ảnh gốc", systemImage: "arrow.counterclockwise") {
                        Haptics.light()
                        Task { await editor.reset() }
                    }
                    Button("Đóng ảnh hiện tại", systemImage: "trash", role: .destructive) {
                        showDeleteConfirmation = true
                    }
                    Divider()
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
        }
        .frame(height: 42)
    }

    // MARK: - Sub-Header Floating Mode Switcher Pill
    private var modeSwitcherPill: some View {
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
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                    .background(
                        Group {
                            if isSelected {
                                Capsule()
                                    .fill(Color.white)
                                    .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
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
                .fill(Color.black.opacity(0.04))
                .overlay(Capsule().strokeBorder(Color.black.opacity(0.04), lineWidth: 0.8))
        )
    }

    // MARK: - Spacious Photo Stage (Hold to Compare)
    private var stage: some View {
        ZStack(alignment: .topTrailing) {
            if let image = isComparing ? editor.originalImage : (editor.editedImage ?? editor.originalImage) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .shadow(color: Color.black.opacity(0.08), radius: 20, x: 0, y: 8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { _ in
                                if !isComparing { Haptics.light(); isComparing = true }
                            }
                            .onEnded { _ in
                                if isComparing { Haptics.light(); isComparing = false }
                            }
                    )
                
                if isComparing {
                    Text("GỐC")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .overlay(Capsule().fill(Color.black.opacity(0.55)))
                        )
                        .overlay(
                            Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.4), .clear], startPoint: .top, endPoint: .bottom), lineWidth: 0.8)
                        )
                        .shadow(color: Color.black.opacity(0.2), radius: 8, x: 0, y: 3)
                        .padding(12)
                }
            }
            
            if editor.isLoading {
                ProgressView()
                    .tint(Studio.ink)
                    .padding(16)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Liquid Glass Bottom Controls Dock
    private var cleanBottomControls: some View {
        VStack(spacing: 12) {
            switch selectedTab {
            case .adjust:
                adjustSection
            case .filters:
                filtersSection
            case .frame:
                framesSection
            }
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 14)
        .liquidGlassCard(cornerRadius: 30)
    }

    // MARK: - 1. Adjust Section (Tick Ruler Slider + Circular Tool Strip)
    private var adjustSection: some View {
        VStack(spacing: 10) {
            // Upper: Active Tool Header & Value Readout
            HStack {
                HStack(spacing: 5) {
                    Image(systemName: selectedAdjust.icon)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Studio.warmAmber)
                    Text(selectedAdjust.rawValue)
                        .font(.system(size: 12.5, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                }
                
                Spacer()
                
                // Formatted Value Badge
                Text(formattedValue(for: selectedAdjust))
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(isModified(selectedAdjust) ? Studio.warmAmber : Studio.muted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(
                        Capsule()
                            .fill(isModified(selectedAdjust) ? Studio.warmAmberLight : Color.white.opacity(0.8))
                            .overlay(Capsule().strokeBorder(isModified(selectedAdjust) ? Studio.warmAmber.opacity(0.3) : Color.black.opacity(0.06), lineWidth: 0.8))
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
            .padding(.horizontal, 4)
            
            // Center: Precision Tick Ruler Slider
            TickRulerSlider(
                value: binding(for: selectedAdjust),
                range: range(for: selectedAdjust),
                step: step(for: selectedAdjust),
                onEditingChanged: { _ in sliderUndoRecorded = false }
            )
            
            // Lower: Horizontal Circular Tool Strip
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
                                    .fill(editor.isAutoBalanced ? Studio.warmAmber : Color.white.opacity(0.85))
                                    .frame(width: 48, height: 48)
                                    .overlay(
                                        Circle().strokeBorder(editor.isAutoBalanced ? Color.clear : Color.black.opacity(0.06), lineWidth: 0.8)
                                    )
                                    .shadow(color: editor.isAutoBalanced ? Studio.warmAmber.opacity(0.35) : Color.black.opacity(0.04), radius: 5, y: 2)
                                
                                Image(systemName: "wand.and.stars")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(editor.isAutoBalanced ? Color.white : Studio.warmAmber)
                            }
                            
                            Text("Tự động")
                                .font(.system(size: 11, weight: editor.isAutoBalanced ? .bold : .medium, design: .rounded))
                                .foregroundStyle(editor.isAutoBalanced ? Studio.warmAmber : Studio.muted)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    
                    // Adjustment Parameter Circles
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
                                        .fill(isSelected ? Studio.warmAmber : Color.white.opacity(0.85))
                                        .frame(width: 48, height: 48)
                                        .overlay(
                                            Circle().strokeBorder(isSelected ? Color.clear : Color.black.opacity(0.06), lineWidth: 0.8)
                                        )
                                        .shadow(color: isSelected ? Studio.warmAmber.opacity(0.35) : Color.black.opacity(0.04), radius: 5, y: 2)
                                    
                                    if isSelected && modified {
                                        Text(formattedValue(for: param))
                                            .font(.system(size: 12, weight: .bold, design: .rounded))
                                            .foregroundStyle(Color.white)
                                            .minimumScaleFactor(0.7)
                                            .lineLimit(1)
                                            .padding(.horizontal, 4)
                                    } else {
                                        Image(systemName: param.icon)
                                            .font(.system(size: 17, weight: .medium))
                                            .foregroundStyle(isSelected ? Color.white : Studio.ink)
                                    }
                                    
                                    if !isSelected && modified {
                                        Circle()
                                            .fill(Studio.warmAmber)
                                            .frame(width: 5, height: 5)
                                            .offset(x: 16, y: -16)
                                    }
                                }
                                
                                Text(param.rawValue)
                                    .font(.system(size: 11, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(isSelected ? Studio.warmAmber : Studio.muted)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.85)
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
                        .foregroundStyle(Studio.warmAmber)
                    Text(editor.recipe?.name ?? "Nguyên bản")
                        .font(.system(size: 12.5, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                }
                
                Spacer()
                
                Button { sheet = .recipes } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "plus")
                            .font(.system(size: 10, weight: .bold))
                        Text("Kho 391+ màu")
                            .font(.system(size: 11.5, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(Studio.warmAmber)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Studio.warmAmberLight)
                            .overlay(Capsule().strokeBorder(Studio.warmAmber.opacity(0.25), lineWidth: 0.8))
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
                                    .fill(editor.recipe == nil ? Studio.warmAmber : Color.white.opacity(0.85))
                                    .frame(width: 48, height: 48)
                                    .overlay(
                                        Circle().strokeBorder(editor.recipe == nil ? Color.clear : Color.black.opacity(0.06), lineWidth: 0.8)
                                    )
                                    .shadow(color: editor.recipe == nil ? Studio.warmAmber.opacity(0.35) : Color.black.opacity(0.04), radius: 5, y: 2)
                                
                                Image(systemName: "circle.lefthalf.filled")
                                    .font(.system(size: 17))
                                    .foregroundStyle(editor.recipe == nil ? Color.white : Studio.ink)
                            }
                            
                            Text("Gốc")
                                .font(.system(size: 11, weight: editor.recipe == nil ? .bold : .medium, design: .rounded))
                                .foregroundStyle(editor.recipe == nil ? Studio.warmAmber : Studio.muted)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    
                    // Curated Recipes (Live Thumbnails)
                    ForEach(editor.recipes.prefix(25)) { recipe in
                        let isSelected = editor.recipe?.id == recipe.id
                        Button {
                            Haptics.selection()
                            Task { await editor.apply(recipe) }
                        } label: {
                            VStack(spacing: 5) {
                                FilterThumbView(recipe: recipe, isSelected: isSelected)
                                
                                Text(recipe.name)
                                    .font(.system(size: 11, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(isSelected ? Studio.warmAmber : Studio.ink)
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

    // MARK: - 3. Frames Section (Ratio & Frame Styling)
    private var framesSection: some View {
        VStack(spacing: 10) {
            HStack {
                HStack(spacing: 5) {
                    Image(systemName: "photo.artframe")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Studio.warmAmber)
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
                    .foregroundStyle(Studio.warmAmber)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Studio.warmAmberLight)
                            .overlay(Capsule().strokeBorder(Studio.warmAmber.opacity(0.25), lineWidth: 0.8))
                    )
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 4)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(0..<min(25, PhotoFrame.styles.count), id: \.self) { idx in
                        let isSelected = editor.frameStyle == idx
                        Button {
                            Haptics.selection()
                            editor.applyFrame(style: idx, info: editor.photoInfo)
                        } label: {
                            VStack(spacing: 5) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(isSelected ? Studio.warmAmber : Color.white.opacity(0.85))
                                        .frame(width: 52, height: 48)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                                .strokeBorder(isSelected ? Color.clear : Color.black.opacity(0.06), lineWidth: 0.8)
                                        )
                                        .shadow(color: isSelected ? Studio.warmAmber.opacity(0.35) : Color.black.opacity(0.04), radius: 5, y: 2)
                                    
                                    Image(systemName: frameIcon(for: idx))
                                        .font(.system(size: 16, weight: .medium))
                                        .foregroundStyle(isSelected ? Color.white : Studio.ink)
                                }
                                
                                Text(frameLabel(for: idx))
                                    .font(.system(size: 10.5, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(isSelected ? Studio.warmAmber : Studio.muted)
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
        case .highlight: key = \.highlight
        case .shadow: key = \.shadow
        case .color: key = \.color
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
        case .highlight: return -2.0...4.0
        case .shadow: return -2.0...4.0
        case .color: return -4.0...4.0
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
        case .highlight, .shadow: return 0.5
        default: return 1.0
        }
    }

    private func formattedValue(for param: AdjustParam) -> String {
        let v = binding(for: param).wrappedValue
        switch param {
        case .exposure: return String(format: "%+.1f", v)
        case .highlight, .shadow: return String(format: "%+.1f", v)
        case .grain: return String(format: "%.0f", v)
        default: return String(format: "%+.0f", v)
        }
    }

    private func isModified(_ param: AdjustParam) -> Bool {
        let v = binding(for: param).wrappedValue
        switch param {
        case .nr: return v != -4.0
        case .grain: return v != 0.0
        default: return v != 0.0
        }
    }

    private func resetValue(for param: AdjustParam) {
        switch param {
        case .exposure: editor.settings.exposure = 0.0
        case .highlight: editor.settings.highlight = 0.0
        case .shadow: editor.settings.shadow = 0.0
        case .color: editor.settings.color = 0.0
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
                .fill(.ultraThinMaterial)
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color.white.opacity(0.4), lineWidth: 0.8))
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
                    .stroke(Studio.warmAmber, lineWidth: 2.2)
                    .frame(width: 58, height: 48)
            } else {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Color.black.opacity(0.06), lineWidth: 0.8)
                    .frame(width: 58, height: 48)
            }
        }
        .task(id: "\(recipe.id)-\(editor.thumbnailVersion)") {
            thumbnail = await editor.recipeThumbnail(recipe)
        }
    }
}
