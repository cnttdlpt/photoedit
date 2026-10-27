import SwiftUI
import UIKit

// MARK: - Dynamic UIColor Helper for Dark / Light Theming
extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1.0) {
        let r = CGFloat((hex >> 16) & 0xff) / 255.0
        let g = CGFloat((hex >> 8) & 0xff) / 255.0
        let b = CGFloat(hex & 0xff) / 255.0
        self.init(red: r, green: g, blue: b, alpha: alpha)
    }

    static func dynamic(light: UInt32, dark: UInt32) -> UIColor {
        return UIColor { traitCollection in
            traitCollection.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        }
    }
}

// MARK: - Design System: IRO Playful Classic Claymorphism (Synchronized with DESIGN.md & Web studio.css)
enum Studio {
    // Semantic surfaces matching DESIGN.md and Web studio.css
    static let background = Color(uiColor: .dynamic(light: 0xEBF1FA, dark: 0x1E1F29))
    static let surface = Color(uiColor: .dynamic(light: 0xFFFFFF, dark: 0x282A37))
    static let surfaceContainer = Color(uiColor: .dynamic(light: 0xF5F8FD, dark: 0x323546))
    static let stageGround = Color(uiColor: .dynamic(light: 0xE4EBF5, dark: 0x16171F))
    static let line = Color(uiColor: .dynamic(light: 0xD2DBE8, dark: 0x3D4256))
    static let ink = Color(uiColor: .dynamic(light: 0x2C3E50, dark: 0xF8F9FD))
    static let muted = Color(uiColor: .dynamic(light: 0x7F8C8D, dark: 0xA0A6B8))
    static let accent = Color(uiColor: .dynamic(light: 0xFF6B8B, dark: 0xFF7597))
    static let accentHover = Color(uiColor: .dynamic(light: 0xFF5277, dark: 0xFF8FA9))
    static let focus = Color(uiColor: .dynamic(light: 0xFF6B8B, dark: 0xFF7597))

    // Playful Candy Accents
    static let clayCoral = Color(uiColor: .dynamic(light: 0xFF6B8B, dark: 0xFF7597))
    static let claySky = Color(uiColor: .dynamic(light: 0x48DBFB, dark: 0x54E1FF))
    static let clayMint = Color(uiColor: .dynamic(light: 0x1DD1A1, dark: 0x2EE6B5))
    static let clayButter = Color(uiColor: .dynamic(light: 0xFECA57, dark: 0xFED36E))
    static let clayLilac = Color(uiColor: .dynamic(light: 0x9B59B6, dark: 0xB368D4))

    static let m3SurfaceLow = surface
    static let m3SurfaceHigh = surfaceContainer
    static let m3Amber = clayButter
    static let m3AmberContainer = surfaceContainer
    static let accentContainer = surfaceContainer
    static let onAccentContainer = ink
    static let darkPill = ink
    static let warmAmber = clayButter
    static let warmAmberLight = surfaceContainer
    static let warmAmberGlow = clayButter.opacity(0.18)
    static let porcelain = background
    static let cardBorder = line

    // Functional RGB Channel Colors (Histograms & Tone Curves)
    static let googleBlue = claySky
    static let googleRed = clayCoral
    static let googleYellow = clayButter
    static let googleGreen = clayMint

    // Material Design 3 Expressive Spring Animations
    static let m3Spring = Animation.spring(response: 0.28, dampingFraction: 0.72, blendDuration: 0)
    static let m3GentleSpring = Animation.spring(response: 0.34, dampingFraction: 0.82, blendDuration: 0)
    static let m3Emphasized = Animation.timingCurve(0.2, 0.0, 0.0, 1.0, duration: 0.28)
}

enum Haptics {
    #if targetEnvironment(macCatalyst)
    @MainActor static func light() {}
    @MainActor static func medium() {}
    @MainActor static func selection() {}
    #else
    @MainActor private static let lightGenerator = UIImpactFeedbackGenerator(style: .light)
    @MainActor private static let mediumGenerator = UIImpactFeedbackGenerator(style: .medium)
    @MainActor private static let selectionGenerator = UISelectionFeedbackGenerator()
    @MainActor static func light() { lightGenerator.impactOccurred() }
    @MainActor static func medium() { mediumGenerator.impactOccurred() }
    @MainActor static func selection() { selectionGenerator.selectionChanged() }
    #endif
}

enum EditorToolTab: String, CaseIterable, Identifiable {
    case filters = "Màu phim"
    case adjust = "Tinh chỉnh"
    case frame = "Khung ảnh"
    case lightcraft = "Nâng cao"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .filters: return "camera.filters"
        case .adjust: return "slider.horizontal.3"
        case .frame: return "photo.artframe"
        case .lightcraft: return "point.topleft.down.curvedto.point.bottomright.up"
        }
    }
}

// MARK: - Modifiers
extension View {
    @ViewBuilder
    func symbolMorphReplace() -> some View {
        if #available(iOS 17.0, *) {
            self.contentTransition(.symbolEffect(.replace))
        } else {
            self
        }
    }

    func minTouchTarget() -> some View {
        self.frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
    }
}

struct M3ExpressiveButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var scaleAmount: CGFloat = 0.96

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(!reduceMotion && configuration.isPressed ? scaleAmount : 1.0)
            .offset(y: !reduceMotion && configuration.isPressed ? 1.5 : 0)
            .animation(reduceMotion ? nil : Studio.m3Spring, value: configuration.isPressed)
    }
}

struct AntigravityBackground: View {
    var body: some View {
        ZStack {
            Studio.background.ignoresSafeArea()
            RadialGradient(
                colors: [Studio.clayCoral.opacity(0.16), Color.clear],
                center: .topTrailing,
                startRadius: 20,
                endRadius: 360
            )
            .ignoresSafeArea()
            RadialGradient(
                colors: [Studio.claySky.opacity(0.14), Color.clear],
                center: .bottomLeading,
                startRadius: 20,
                endRadius: 320
            )
            .ignoresSafeArea()
        }
    }
}

extension View {
    func liquidGlassCard(cornerRadius: CGFloat = 24) -> some View {
        self
            .background(Studio.surface)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(
                        LinearGradient(
                            colors: [Color.white.opacity(0.85), Studio.line.opacity(0.35)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.5
                    )
            )
            .shadow(color: Color(red: 144/255, green: 160/255, blue: 184/255).opacity(0.38), radius: 14, x: 7, y: 10)
            .shadow(color: Color.white.opacity(0.75), radius: 10, x: -5, y: -5)
    }

    func liquidGlassPill() -> some View {
        self
            .background(Studio.surface)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .strokeBorder(
                        LinearGradient(
                            colors: [Color.white.opacity(0.8), Studio.line.opacity(0.3)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.5
                    )
            )
            .shadow(color: Color(red: 144/255, green: 160/255, blue: 184/255).opacity(0.32), radius: 10, x: 4, y: 6)
            .shadow(color: Color.white.opacity(0.7), radius: 6, x: -3, y: -3)
    }

    func liquidGlassCircle(size: CGFloat = 36) -> some View {
        self
            .frame(width: max(44, size), height: max(44, size))
            .background(Studio.surface)
            .overlay(
                Circle()
                    .strokeBorder(
                        LinearGradient(
                            colors: [Color.white.opacity(0.8), Studio.line.opacity(0.3)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.5
                    )
            )
            .clipShape(Circle())
            .shadow(color: Color(red: 144/255, green: 160/255, blue: 184/255).opacity(0.32), radius: 8, x: 3, y: 5)
            .shadow(color: Color.white.opacity(0.7), radius: 5, x: -2, y: -2)
    }

    func liquidDarkPill() -> some View {
        self
            .background(Studio.ink)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .strokeBorder(Studio.ink, lineWidth: 1.0)
            )
            .shadow(color: Color.black.opacity(0.2), radius: 6, x: 2, y: 4)
    }
}

// MARK: - Landing Screen (Synchronized with Web Landing & Bento Cards)
struct EmptyPhoto: View {
    @EnvironmentObject private var editor: EditorModel
    var onPickPhoto: () -> Void = {}
    var onImportFile: () -> Void = {}
    var onOpenRecipes: () -> Void = {}
    var onOpenLuts: () -> Void = {}
    var onOpenOnline: () -> Void = {}
    var onOpenHelp: () -> Void = {}
    var onLoadSample: (String?) -> Void = { _ in }
    var onSelectFrameSample: () -> Void = {}

    @State private var selectedSensor: String = "Tất cả"
    @State private var searchQuery: String = ""

    private let sensors = ["Tất cả", "X-Trans V", "X-Trans IV", "X-Trans III", "Bayer / GFX"]

    private var filteredRecipes: [Recipe] {
        let list = editor.recipes
        return list.filter { recipe in
            let matchesSensor: Bool
            if selectedSensor == "Tất cả" {
                matchesSensor = true
            } else if selectedSensor == "Bayer / GFX" {
                matchesSensor = recipe.sensors.contains(where: { $0.contains("Bayer") || $0.contains("GFX") })
            } else {
                matchesSensor = recipe.sensors.contains(selectedSensor)
            }
            let q = searchQuery.trimmingCharacters(in: .whitespaces)
            let matchesQuery = q.isEmpty ||
                recipe.name.localizedStandardContains(q) ||
                recipe.film.localizedStandardContains(q)
            return matchesSensor && matchesQuery
        }
    }

    var body: some View {
        GeometryReader { geo in
            let isWide = geo.size.width >= 700
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 20) {
                    topBrandHeader

                    heroBanner(isWide: isWide)

                    sensorBentoCard(isWide: isWide)

                    framesRibbonCard

                    Spacer(minLength: 24)
                }
                .padding(.horizontal, isWide ? 24 : 16)
                .padding(.vertical, 14)
            }
        }
    }

    // 1. Top Brand Header
    private var topBrandHeader: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                Text("IRO")
                    .font(.system(size: 20, weight: .black, design: .rounded))
                    .foregroundStyle(Studio.ink)
                Text("Studio")
                    .font(.system(size: 11, weight: .bold, design: .rounded))
                    .foregroundStyle(Studio.accent)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Studio.accent.opacity(0.12))
                    .clipShape(Capsule())
                Text("Phòng màu ảnh")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Studio.muted)
            }

            Spacer()

            Menu {
                Button("Mở ảnh từ Thư viện", systemImage: "photo.badge.plus") { onPickPhoto() }
                Button("Mở ảnh từ Tệp", systemImage: "folder") { onImportFile() }
                Button("Dùng thử ảnh mẫu Studio", systemImage: "sparkles") { onLoadSample(nil) }
                Divider()
                Button("Kho 421 công thức màu", systemImage: "camera.filters") { onOpenRecipes() }
                Button("Quản lý LUT", systemImage: "square.stack.3d.up") { onOpenLuts() }
                Button("Kho LUT trực tuyến", systemImage: "globe") { onOpenOnline() }
                Divider()
                Button("Hướng dẫn & Phím tắt", systemImage: "info.circle") { onOpenHelp() }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Studio.ink)
                    .liquidGlassCircle(size: 38)
            }
        }
    }

    // 2. Hero Banner Card (Parity with Web Hero)
    private func heroBanner(isWide: Bool) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Một bức ảnh. Màu của bạn.")
                    .font(.system(size: isWide ? 28 : 22, weight: .bold, design: .rounded))
                    .foregroundStyle(Studio.ink)
                    .tracking(-0.5)

                Text("Mở ảnh, chọn màu phim và tinh chỉnh theo ý bạn. Xuất ảnh khi đã ưng ý.")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(Studio.muted)
                    .lineLimit(2)
            }

            // Action Buttons
            HStack(spacing: 10) {
                Button {
                    Haptics.medium()
                    onPickPhoto()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "photo.badge.plus")
                            .font(.system(size: 13, weight: .bold))
                        Text("Chọn ảnh từ thiết bị")
                            .font(.system(size: 13.5, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(Studio.background)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 11)
                    .background(Studio.ink)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(M3ExpressiveButtonStyle())

                Button {
                    Haptics.light()
                    onImportFile()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "folder")
                            .font(.system(size: 13))
                        Text("Tệp tin")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                    }
                    .foregroundStyle(Studio.ink)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(Studio.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Studio.line, lineWidth: 1.0))
                }
                .buttonStyle(M3ExpressiveButtonStyle())

                Button {
                    Haptics.medium()
                    onLoadSample(nil)
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 12))
                        Text("Thử ảnh mẫu")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                    }
                    .foregroundStyle(Studio.ink)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(Studio.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Studio.line, lineWidth: 1.0))
                }
                .buttonStyle(M3ExpressiveButtonStyle())
            }

            // Workflow Steps
            HStack(spacing: 12) {
                ForEach(["Chọn ảnh", "Chọn màu", "Tinh chỉnh", "Xuất ảnh"], id: \.self) { step in
                    HStack(spacing: 6) {
                        Text(step)
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundStyle(Studio.muted)
                        if step != "Xuất ảnh" {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundStyle(Studio.muted.opacity(0.5))
                        }
                    }
                }
            }
            .padding(.top, 2)
        }
        .padding(isWide ? 22 : 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .liquidGlassCard(cornerRadius: 16)
    }

    // 3. Sensor Bento Card with Direct Recipe Grid
    private func sensorBentoCard(isWide: Bool) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Khám phá màu phim")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                    Text("Tìm công thức theo tên màu hoặc dòng film.")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(Studio.muted)
                }

                Spacer()

                Button {
                    onOpenRecipes()
                } label: {
                    Text("Quản lý thư viện ›")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.accent)
                }
                .buttonStyle(.plain)
            }

            // Sensor Filter Chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(sensors, id: \.self) { sensor in
                        let isSelected = selectedSensor == sensor
                        Button {
                            Haptics.selection()
                            selectedSensor = sensor
                        } label: {
                            Text(sensor)
                                .font(.system(size: 12, weight: isSelected ? .bold : .medium, design: .rounded))
                                .foregroundStyle(isSelected ? Studio.ink : Studio.muted)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .fill(isSelected ? Studio.surfaceContainer : Studio.surface)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                .strokeBorder(isSelected ? Studio.ink : Studio.line, lineWidth: isSelected ? 1.2 : 1.0)
                                        )
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            // Search Bar
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Studio.muted)

                TextField("Tìm tên công thức, film…", text: $searchQuery)
                    .font(.system(size: 13, design: .rounded))
                    .textFieldStyle(.plain)

                if !searchQuery.isEmpty {
                    Button {
                        searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(Studio.muted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Studio.surfaceContainer)
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Studio.line, lineWidth: 1.0))
            )

            // Direct Recipe Grid (Showing top 18 matches)
            let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: isWide ? 3 : 2)
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(filteredRecipes.prefix(18)) { recipe in
                    Button {
                        Haptics.selection()
                        Task {
                            await editor.openStudioSample(filmKeyword: recipe.film)
                            await editor.apply(recipe)
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(recipe.name)
                                .font(.system(size: 12.5, weight: .bold, design: .rounded))
                                .foregroundStyle(Studio.ink)
                                .lineLimit(1)

                            HStack(spacing: 4) {
                                Text(recipe.film)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundStyle(Studio.muted)
                                    .lineLimit(1)

                                Spacer()

                                if let s = recipe.sensors.first {
                                    Text(s)
                                        .font(.system(size: 9.5, weight: .semibold))
                                        .foregroundStyle(Studio.accent)
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 1.5)
                                        .background(Studio.accent.opacity(0.12))
                                        .clipShape(RoundedRectangle(cornerRadius: 4))
                                }
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Studio.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(Studio.line, lineWidth: 1.0)
                        )
                    }
                    .buttonStyle(M3ExpressiveButtonStyle())
                }
            }

            if filteredRecipes.isEmpty {
                Text("Không tìm thấy công thức màu phù hợp.")
                    .font(.system(size: 13))
                    .foregroundStyle(Studio.muted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 16)
            }
        }
        .padding(isWide ? 20 : 16)
        .liquidGlassCard(cornerRadius: 16)
    }

    // 4. Frames Ribbon Card
    private var framesRibbonCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("30 Mẫu Khung Triển Lãm & EXIF")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                    Text("Trang trí tác phẩm với thông số máy ảnh & chữ ký nghệ thuật")
                        .font(.system(size: 11.5))
                        .foregroundStyle(Studio.muted)
                }
                Spacer()
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(["Polaroid Classic", "Film Strip 35mm", "Triển lãm Bảo tàng", "Tạp chí Thời trang", "Tem Bưu Chính", "Tối giản Đen Tuyền", "Hồ sơ Lưu trữ", "Góc Cài Cổ Điển"].enumerated()), id: \.offset) { idx, name in
                        Button {
                            Haptics.selection()
                            onSelectFrameSample()
                        } label: {
                            Text(name)
                                .font(.system(size: 12, weight: .medium, design: .rounded))
                                .foregroundStyle(Studio.ink)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(Studio.surface)
                                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(Studio.line, lineWidth: 1.0))
                        }
                        .buttonStyle(M3ExpressiveButtonStyle())
                    }
                }
            }
        }
        .padding(16)
        .liquidGlassCard(cornerRadius: 16)
    }
}
