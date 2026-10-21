import SwiftUI
import UIKit

enum Studio {
    // Semantic surfaces follow the device appearance and accessibility contrast.
    static let background = Color(uiColor: .systemGroupedBackground)
    static let surface = Color(uiColor: .secondarySystemGroupedBackground)
    static let surfaceContainer = Color(uiColor: .tertiarySystemGroupedBackground)
    static let m3SurfaceLow = surface
    static let m3SurfaceHigh = surfaceContainer
    static let line = Color(uiColor: .separator)
    static let ink = Color.primary
    static let muted = Color.secondary
    static let m3Amber = Color(uiColor: .systemGreen)
    static let m3AmberContainer = Color(uiColor: .tertiarySystemFill)
    static let accent = Color(uiColor: .systemGreen)
    static let accentContainer = surfaceContainer
    static let onAccentContainer = ink
    static let darkPill = ink
    static let warmAmber = accent
    static let warmAmberLight = surfaceContainer
    static let warmAmberGlow = Color.clear
    static let porcelain = background
    static let cardBorder = line

    // Functional RGB Channel Colors (Histograms & Tone Curves)
    static let googleBlue = Color(red: 66/255, green: 133/255, blue: 244/255)        // #4285F4
    static let googleRed = Color(red: 234/255, green: 67/255, blue: 53/255)          // #EA4335
    static let googleYellow = Color(red: 251/255, green: 188/255, blue: 4/255)       // #FBBC04
    static let googleGreen = Color(red: 52/255, green: 168/255, blue: 83/255)        // #34A853

    // Material Design 3 Expressive Spring Animations (Matches MotionStudio.java)
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
        case .adjust: return "slider.horizontal.3"
        case .filters: return "sparkles"
        case .frame: return "photo.artframe"
        case .lightcraft: return "point.topleft.down.curvedto.point.bottomright.up"
        }
    }
}


// MARK: - Impeccable & Taste Skill Modifiers
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

// MARK: - Material Design 3 Expressive Spring Button Style
struct M3ExpressiveButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var scaleAmount: CGFloat = 0.95

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(!reduceMotion && configuration.isPressed ? scaleAmount : 1.0)
            .animation(reduceMotion ? nil : Studio.m3Spring, value: configuration.isPressed)
    }
}

// MARK: - Material Design 3 Ambient Tonal Studio Background
struct AntigravityBackground: View {
    var body: some View { Studio.background.ignoresSafeArea() }
}

// MARK: - Minimal Studio View Modifiers
extension View {
    /// Clean Minimal Studio Card (M3 Surface Container Lowest + Outline Variant)
    func liquidGlassCard(cornerRadius: CGFloat = 24) -> some View {
        self
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(Studio.surface)
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(Studio.line, lineWidth: 1.0)
            )
            .shadow(color: Color.black.opacity(0.04), radius: 10, x: 0, y: 3)
    }

    /// Clean Minimal Studio Capsule Pill
    func liquidGlassPill() -> some View {
        self
            .background(
                Capsule()
                    .fill(Studio.surface)
            )
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .strokeBorder(Studio.line, lineWidth: 1.0)
            )
            .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }

    /// Clean Minimal Studio Circular Button
    func liquidGlassCircle(size: CGFloat = 36) -> some View {
        self
            .frame(width: max(44, size), height: max(44, size))
            .background(
                Circle()
                    .fill(Studio.surface)
            )
            .overlay(
                Circle()
                    .strokeBorder(Studio.line, lineWidth: 1.0)
            )
            .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }

    /// Dark Ink Pill
    func liquidDarkPill() -> some View {
        self
            .background(
                Capsule()
                    .fill(Studio.ink)
            )
            .overlay(
                Capsule()
                    .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.8)
            )
            .shadow(color: Color.black.opacity(0.12), radius: 6, x: 0, y: 2)
    }
}

// MARK: - Minimal Studio Landing Empty View (Adaptive Dual-Pane on Wide Screens)
struct EmptyPhoto: View {
    var onPickPhoto: () -> Void = {}
    var onImportFile: () -> Void = {}
    var onOpenRecipes: () -> Void = {}
    var onOpenLuts: () -> Void = {}
    var onOpenOnline: () -> Void = {}
    var onOpenHelp: () -> Void = {}
    var onLoadSample: (String?) -> Void = { _ in }
    var onSelectFrameSample: () -> Void = {}

    private let filmChips = [
        "Classic Chrome", "Portra 400", "Kodak Gold", "Tri-X 400",
        "Velvia 50", "Natura 1600", "Astia Soft", "Eterna Cinema"
    ]

    var body: some View {
        GeometryReader { geo in
            let isWide = geo.size.width >= 640
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 16) {
                    topSearchBarRow

                    if isWide {
                        HStack(alignment: .top, spacing: 18) {
                            VStack(spacing: 14) {
                                heroPrimaryCard
                                filmSimulationChipStrip
                            }
                            .frame(maxWidth: .infinity)

                            VStack(spacing: 12) {
                                albumsHeaderRow
                                albumsGrid
                            }
                            .frame(maxWidth: .infinity)
                        }
                    } else {
                        heroPrimaryCard
                        filmSimulationChipStrip
                        DisclosureGroup("Thư viện & công cụ") {
                            albumsHeaderRow
                            albumsGrid
                        }
                    }
                }
                .padding(.vertical, 10)
            }
        }
    }

    // 1. Top Search Bar Row
    private var topSearchBarRow: some View {
        HStack(spacing: 8) {
            Button {
                Haptics.light()
                onOpenRecipes()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Studio.muted)

                    Text("Tìm kiếm công thức màu phim…")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(Studio.muted)
                        .lineLimit(1)

                    Spacer()
                }
                .padding(.horizontal, 13)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(Studio.surface)
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .strokeBorder(Studio.line, lineWidth: 1.0)
                        )
                )
            }
            .buttonStyle(M3ExpressiveButtonStyle(scaleAmount: 0.98))

            Button {
                Haptics.light()
                onOpenRecipes()
            } label: {
                Image(systemName: "heart.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(Studio.ink)
                    .liquidGlassCircle(size: 38)
            }
            .buttonStyle(M3ExpressiveButtonStyle())

            Menu {
                Button("Mở ảnh từ Thư viện", systemImage: "photo.badge.plus") { onPickPhoto() }
                Button("Mở ảnh từ Tệp", systemImage: "folder") { onImportFile() }
                Button("Dùng thử ảnh mẫu Studio", systemImage: "sparkles") { onLoadSample(nil) }
                Divider()
                Button("Kho Công thức màu màu", systemImage: "camera.filters") { onOpenRecipes() }
                Button("Quản lý LUT", systemImage: "square.stack.3d.up") { onOpenLuts() }
                Button("Kho LUT trực tuyến", systemImage: "globe") { onOpenOnline() }
                Divider()
                Button("Hướng dẫn", systemImage: "info.circle") { onOpenHelp() }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Studio.ink)
                    .liquidGlassCircle(size: 38)
            }
        }
    }

    // 2. Albums Section Header
    private var albumsHeaderRow: some View {
        HStack(spacing: 8) {
            Text("Albums & Màu phim")
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundStyle(Studio.ink)

            Text("392")
                .font(.system(size: 10.5, weight: .bold, design: .rounded))
                .foregroundStyle(Studio.ink)
                .padding(.horizontal, 8)
                .padding(.vertical, 2.5)
                .background(
                    Capsule()
                        .fill(Studio.surface)
                        .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                )

            Spacer()

            Button {
                Haptics.light()
                onPickPhoto()
            } label: {
                Text("+ Mở ảnh")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(Studio.ink)
            }
            .buttonStyle(M3ExpressiveButtonStyle())
        }
        .padding(.horizontal, 4)
    }

    // 3. 2x2 Albums Grid
    private var albumsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
            AlbumCard(
                title: "Fuji Chrome",
                subtitle: "Công thức màu",
                icon: "camera.filters",
                action: onOpenRecipes
            )
            AlbumCard(
                title: "Kodak Portra",
                subtitle: "Màu da ấm áp",
                icon: "sparkles",
                action: onOpenRecipes
            )
            AlbumCard(
                title: "Đen Trắng",
                subtitle: "Tri-X & Acros",
                icon: "circle.lefthalf.filled",
                action: onOpenRecipes
            )
            AlbumCard(
                title: "Khung & EXIF",
                subtitle: "30+ viền ảnh",
                icon: "photo.artframe",
                action: onSelectFrameSample
            )
        }
    }

    // 4. Primary CTA Hero Card
    private var heroPrimaryCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Studio.surface)
                        .frame(width: 44, height: 44)
                        .overlay(Circle().strokeBorder(Studio.line, lineWidth: 1.0))
                    Image(systemName: "photo.badge.plus")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Studio.ink)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Bắt đầu chỉnh màu ngay")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                    Text("Chọn ảnh từ Thư viện, Tệp tin hoặc thử ảnh mẫu")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(Studio.muted)
                }

                Spacer()
            }

            HStack(spacing: 10) {
                Button {
                    Haptics.medium()
                    onPickPhoto()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 12, weight: .semibold))
                        Text("Thư viện ảnh")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .foregroundStyle(Color.white)
                    .background(
                        Capsule()
                            .fill(Studio.ink)
                    )
                }
                .buttonStyle(M3ExpressiveButtonStyle())

                Button {
                    Haptics.light()
                    onImportFile()
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "folder")
                            .font(.system(size: 12))
                        Text("Tệp tin")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .foregroundStyle(Studio.ink)
                    .liquidGlassPill()
                }
                .buttonStyle(M3ExpressiveButtonStyle())
            }

            Button {
                Haptics.medium()
                onLoadSample(nil)
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 11.5, weight: .bold))
                    Text("Dùng thử ngay với ảnh mẫu Studio (Golden Hour)")
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .foregroundStyle(Studio.ink)
                .background(
                    Capsule()
                        .fill(Studio.surface)
                        .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                )
            }
            .buttonStyle(M3ExpressiveButtonStyle())
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Studio.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(Studio.line, lineWidth: 1.0)
                )
                .shadow(color: Color.black.opacity(0.03), radius: 8, x: 0, y: 2)
        )
    }

    // 5. Film Simulation Pills
    private var filmSimulationChipStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filmChips, id: \.self) { film in
                    Button {
                        Haptics.selection()
                        onLoadSample(film)
                    } label: {
                        Text(film)
                            .font(.system(size: 11.5, weight: .semibold, design: .rounded))
                            .foregroundStyle(Studio.ink)
                            .padding(.horizontal, 13)
                            .padding(.vertical, 7)
                            .background(
                                Capsule()
                                    .fill(Studio.surface)
                                    .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                            )
                    }
                    .buttonStyle(M3ExpressiveButtonStyle())
                }
            }
        }
    }
}

private struct AlbumCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.light()
            action()
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    ZStack {
                        Circle()
                            .fill(Studio.surface)
                            .frame(width: 34, height: 34)
                            .overlay(Circle().strokeBorder(Studio.line, lineWidth: 1.0))
                        Image(systemName: icon)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Studio.ink)
                    }
                    Spacer()
                }

                Spacer(minLength: 4)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13.5, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.system(size: 11.5, weight: .regular))
                        .foregroundStyle(Studio.muted)
                        .lineLimit(1)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Studio.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(Studio.line, lineWidth: 1.0)
                    )
                    .shadow(color: Color.black.opacity(0.02), radius: 5, x: 0, y: 2)
            )
        }
        .buttonStyle(M3ExpressiveButtonStyle())
    }
}
