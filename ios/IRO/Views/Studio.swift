import SwiftUI
import UIKit

enum Studio {
    // Minimal Studio Palette — Neutral Grays & Crisp Ink (Matches Android StudioStyle.java)
    static let background = Color(red: 246/255, green: 247/255, blue: 249/255)       // #F6F7F9
    static let surface = Color(red: 239/255, green: 241/255, blue: 245/255)          // #EFF1F5
    static let surfaceContainer = Color(red: 230/255, green: 233/255, blue: 239/255) // #E6E9EF
    static let line = Color(red: 225/255, green: 228/255, blue: 234/255)             // #E1E4EA
    static let ink = Color(red: 24/255, green: 26/255, blue: 31/255)                 // #181A1F
    static let muted = Color(red: 110/255, green: 115/255, blue: 125/255)            // #6E737D

    // Primary Studio Accent — Charcoal Black
    static let accent = ink
    static let accentContainer = Color(red: 234/255, green: 236/255, blue: 239/255)  // #EAECEF
    static let onAccentContainer = ink
    static let darkPill = ink

    // Legacy alias compatibility mapped to Minimal Studio Ink
    static let warmAmber = ink
    static let warmAmberLight = Color(red: 234/255, green: 236/255, blue: 239/255)   // #EAECEF
    static let warmAmberGlow = Color.black.opacity(0.08)
    static let porcelain = Color(red: 246/255, green: 247/255, blue: 249/255)        // #F6F7F9
    static let cardBorder = Color(red: 225/255, green: 228/255, blue: 234/255)       // #E1E4EA

    // Functional RGB Channel Colors (Histograms & Tone Curves)
    static let googleBlue = Color(red: 66/255, green: 133/255, blue: 244/255)        // #4285F4
    static let googleRed = Color(red: 234/255, green: 67/255, blue: 53/255)          // #EA4335
    static let googleYellow = Color(red: 251/255, green: 188/255, blue: 4/255)       // #FBBC04
    static let googleGreen = Color(red: 52/255, green: 168/255, blue: 83/255)        // #34A853
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
    case adjust = "Tinh chỉnh"
    case filters = "Màu phim"
    case frame = "Khung ảnh"
    case lightcraft = "LightCraft"

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

// MARK: - Clean Flat Neutral Studio Background (Zero color cast distraction)
struct AntigravityBackground: View {
    var body: some View {
        Studio.porcelain
            .ignoresSafeArea()
    }
}

// MARK: - Minimal Studio View Modifiers
extension View {
    /// Clean Minimal Studio Card
    func liquidGlassCard(cornerRadius: CGFloat = 24) -> some View {
        self
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(Color.white)
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
                    .fill(Color.white)
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
            .frame(width: size, height: size)
            .background(
                Circle()
                    .fill(Color.white)
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
                        albumsHeaderRow
                        albumsGrid
                        heroPrimaryCard
                        filmSimulationChipStrip
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

                    Text("Tìm kiếm 392 màu phim, giả lập...")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(Studio.muted)
                        .lineLimit(1)

                    Spacer()
                }
                .padding(.horizontal, 13)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .strokeBorder(Studio.line, lineWidth: 1.0)
                        )
                )
            }
            .buttonStyle(.plain)

            Button {
                Haptics.light()
                onOpenRecipes()
            } label: {
                Image(systemName: "heart.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(Studio.ink)
                    .liquidGlassCircle(size: 38)
            }
            .buttonStyle(.plain)

            Menu {
                Button("Mở ảnh từ Thư viện", systemImage: "photo.badge.plus") { onPickPhoto() }
                Button("Mở ảnh từ Tệp", systemImage: "folder") { onImportFile() }
                Button("Dùng thử ảnh mẫu Studio", systemImage: "sparkles") { onLoadSample(nil) }
                Divider()
                Button("Kho 392 công thức màu", systemImage: "camera.filters") { onOpenRecipes() }
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
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 4)
    }

    // 3. 2x2 Albums Grid
    private var albumsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
            AlbumCard(
                title: "Fuji Chrome",
                subtitle: "392 công thức",
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
                .buttonStyle(.plain)

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
                .buttonStyle(.plain)
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
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.white)
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
                                    .fill(Color.white)
                                    .overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1.0))
                            )
                    }
                    .buttonStyle(.plain)
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
                    .fill(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(Studio.line, lineWidth: 1.0)
                    )
                    .shadow(color: Color.black.opacity(0.02), radius: 5, x: 0, y: 2)
            )
        }
        .buttonStyle(.plain)
    }
}
