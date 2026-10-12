import SwiftUI
import UIKit

enum Studio {
    // Liquid Glass & Clean minimal warm palette
    static let background = Color.white
    static let surface = Color(red: 246/255, green: 247/255, blue: 249/255)       // #F6F7F9
    static let surfaceContainer = Color(red: 238/255, green: 240/255, blue: 244/255) // #EEF0F4
    static let line = Color(red: 230/255, green: 232/255, blue: 236/255)          // #E6E8EC
    static let ink = Color(red: 22/255, green: 24/255, blue: 28/255)              // #16181C
    static let muted = Color(red: 138/255, green: 142/255, blue: 152/255)         // #8A8E98
    static let accent = Color(red: 224/255, green: 122/255, blue: 59/255)         // #E07A3B (Warm Amber / Terracotta)
    static let accentContainer = Color(red: 254/255, green: 243/255, blue: 236/255)
    static let onAccentContainer = Color(red: 224/255, green: 122/255, blue: 59/255)
    static let darkPill = Color(red: 20/255, green: 22/255, blue: 26/255)         // #14161A
    
    // Warm Mockup Specific
    static let warmAmber = Color(red: 224/255, green: 122/255, blue: 59/255)      // #E07A3B (Active tool circle)
    static let warmAmberLight = Color(red: 254/255, green: 243/255, blue: 236/255) // #FEF3EC
    static let warmAmberGlow = Color(red: 224/255, green: 122/255, blue: 59/255).opacity(0.35)
    static let porcelain = Color(red: 249/255, green: 249/255, blue: 251/255)     // #F9F9FB
    static let cardBorder = Color.black.opacity(0.06)

    // Google Quad Colors
    static let googleBlue = Color(red: 66/255, green: 133/255, blue: 244/255)
    static let googleRed = Color(red: 234/255, green: 67/255, blue: 53/255)
    static let googleYellow = Color(red: 251/255, green: 188/255, blue: 4/255)
    static let googleGreen = Color(red: 52/255, green: 168/255, blue: 83/255)
}

enum Haptics {
    @MainActor static func light() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    @MainActor static func medium() { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    @MainActor static func selection() { UISelectionFeedbackGenerator().selectionChanged() }
}

enum EditorToolTab: String, CaseIterable, Identifiable {
    case adjust = "Tinh chỉnh"
    case filters = "Màu phim"
    case frame = "Khung ảnh"
    
    var id: String { rawValue }
    
    var icon: String {
        switch self {
        case .adjust: return "slider.horizontal.3"
        case .filters: return "sparkles"
        case .frame: return "photo.artframe"
        }
    }
}

// MARK: - Liquid Glass Background (Strictly bounded to screen geometry, zero overflow)
struct AntigravityBackground: View {
    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color(red: 249/255, green: 249/255, blue: 251/255)
                
                // Top Right: Warm Amber / Dawn Glow
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color(red: 255/255, green: 232/255, blue: 216/255).opacity(0.40),
                                Color(red: 255/255, green: 242/255, blue: 234/255).opacity(0.12),
                                Color.clear
                            ],
                            center: .center,
                            startRadius: 20,
                            endRadius: proxy.size.width * 0.45
                        )
                    )
                    .frame(width: proxy.size.width * 0.8, height: proxy.size.width * 0.8)
                    .position(x: proxy.size.width * 0.85, y: proxy.size.height * 0.12)
                    .blur(radius: 40)
                
                // Bottom Left: Subtle Cool Mint / Sky Glow
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color(red: 220/255, green: 240/255, blue: 255/255).opacity(0.35),
                                Color.clear
                            ],
                            center: .center,
                            startRadius: 20,
                            endRadius: proxy.size.width * 0.45
                        )
                    )
                    .frame(width: proxy.size.width * 0.75, height: proxy.size.width * 0.75)
                    .position(x: proxy.size.width * 0.15, y: proxy.size.height * 0.88)
                    .blur(radius: 45)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .clipped()
        }
        .ignoresSafeArea()
    }
}

// MARK: - Liquid Glass View Modifiers
extension View {
    /// iOS 18 / VisionOS Liquid Frosted Glass Card with deep continuous curves
    func liquidGlassCard(cornerRadius: CGFloat = 34) -> some View {
        self
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay(
                        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                            .fill(Color.white.opacity(0.42))
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(
                        LinearGradient(
                            stops: [
                                .init(color: .white.opacity(0.95), location: 0.0),
                                .init(color: .white.opacity(0.35), location: 0.35),
                                .init(color: .white.opacity(0.06), location: 0.65),
                                .init(color: .white.opacity(0.40), location: 1.0)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.2
                    )
            )
            .shadow(color: Color(red: 20/255, green: 30/255, blue: 60/255).opacity(0.08), radius: 22, x: 0, y: 10)
            .shadow(color: Color.black.opacity(0.03), radius: 5, x: 0, y: 2)
    }

    /// Liquid Glass Capsule Pill
    func liquidGlassPill() -> some View {
        self
            .background(
                Capsule()
                    .fill(.ultraThinMaterial)
                    .overlay(Capsule().fill(Color.white.opacity(0.45)))
            )
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .strokeBorder(
                        LinearGradient(
                            colors: [.white.opacity(0.92), .white.opacity(0.25)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.0
                    )
            )
            .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
    }

    /// Liquid Glass Circular Button
    func liquidGlassCircle(size: CGFloat = 36) -> some View {
        self
            .frame(width: size, height: size)
            .background(
                Circle()
                    .fill(.ultraThinMaterial)
                    .overlay(Circle().fill(Color.white.opacity(0.42)))
            )
            .overlay(
                Circle()
                    .strokeBorder(
                        LinearGradient(
                            colors: [.white.opacity(0.92), .white.opacity(0.28)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.0
                    )
            )
            .shadow(color: Color.black.opacity(0.04), radius: 8, x: 0, y: 3)
    }

    /// Liquid Dark Frosted Glass Pill
    func liquidDarkPill() -> some View {
        self
            .background(
                Capsule()
                    .fill(Color(red: 20/255, green: 22/255, blue: 26/255))
                    .overlay(
                        LinearGradient(
                            colors: [Color.white.opacity(0.24), Color.clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
            )
            .overlay(
                Capsule()
                    .strokeBorder(Color.white.opacity(0.22), lineWidth: 0.8)
            )
            .shadow(color: Color.black.opacity(0.18), radius: 8, x: 0, y: 4)
    }
}

// MARK: - Clean Liquid Glass Landing Empty View
// MARK: - Clean Liquid Glass Landing Empty View (Matches Reference Mockup)
struct EmptyPhoto: View {
    var onPickPhoto: () -> Void = {}
    var onImportFile: () -> Void = {}
    var onOpenLuts: () -> Void = {}
    var onOpenOnline: () -> Void = {}
    var onOpenHelp: () -> Void = {}
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 16) {
                // 1. Top Search Bar Row (Mockup Style with Heart & Menu)
                HStack(spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Studio.muted)
                        
                        Text("Tìm kiếm ảnh, màu phim, giả lập...")
                            .font(.system(size: 13, weight: .regular))
                            .foregroundStyle(Studio.muted)
                            .lineLimit(1)
                        
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .strokeBorder(Color.black.opacity(0.05), lineWidth: 0.8)
                            )
                            .shadow(color: Color.black.opacity(0.03), radius: 6, x: 0, y: 2)
                    )
                    
                    Button {
                        Haptics.light()
                        onPickPhoto()
                    } label: {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(Studio.warmAmber)
                            .liquidGlassCircle(size: 38)
                    }
                    .buttonStyle(.plain)
                    
                    Menu {
                        Button("Mở ảnh mới", systemImage: "photo.badge.plus") { onPickPhoto() }
                        Button("Mở ảnh từ Tệp", systemImage: "folder") { onImportFile() }
                        Divider()
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
                
                // 2. Albums Section Header
                HStack {
                    Text("Albums & Màu phim")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                    
                    Spacer()
                    
                    Button {
                        Haptics.light()
                        onPickPhoto()
                    } label: {
                        Text("+ Mở ảnh")
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(Studio.warmAmber)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 4)
                
                // 3. 2x2 Albums Grid
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                    AlbumCard(
                        title: "Fuji Chrome",
                        subtitle: "391 công thức",
                        icon: "camera.filters",
                        accentColor: Studio.warmAmber,
                        action: onPickPhoto
                    )
                    AlbumCard(
                        title: "Kodak Portra",
                        subtitle: "Màu da ấm áp",
                        icon: "sparkles",
                        accentColor: Color(red: 234/255, green: 140/255, blue: 60/255),
                        action: onPickPhoto
                    )
                    AlbumCard(
                        title: "Đen Trắng",
                        subtitle: "Tri-X & Acros",
                        icon: "circle.lefthalf.filled",
                        accentColor: Color(red: 70/255, green: 75/255, blue: 85/255),
                        action: onPickPhoto
                    )
                    AlbumCard(
                        title: "Khung & EXIF",
                        subtitle: "30+ viền ảnh",
                        icon: "photo.artframe",
                        accentColor: Studio.warmAmber,
                        action: onPickPhoto
                    )
                }
                
                // 4. Primary CTA Hero Card
                VStack(spacing: 14) {
                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(Studio.warmAmberLight)
                                .frame(width: 44, height: 44)
                            Image(systemName: "photo.badge.plus")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(Studio.warmAmber)
                        }
                        
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Bắt đầu chỉnh màu ngay")
                                .font(.system(size: 14, weight: .bold, design: .rounded))
                                .foregroundStyle(Studio.ink)
                            Text("Chọn ảnh từ Thư viện hoặc Tệp tin RAW")
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
                            .padding(.vertical, 10)
                            .foregroundStyle(Color.white)
                            .background(
                                Capsule()
                                    .fill(Studio.warmAmber)
                                    .shadow(color: Studio.warmAmber.opacity(0.35), radius: 6, x: 0, y: 3)
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
                                    .font(.system(size: 13, weight: .medium, design: .rounded))
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .foregroundStyle(Studio.ink)
                            .liquidGlassPill()
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
                .background(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .strokeBorder(Color.black.opacity(0.05), lineWidth: 0.8)
                        )
                        .shadow(color: Color.black.opacity(0.04), radius: 10, x: 0, y: 3)
                )
                
                // 5. Film Simulation Pills
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(["Classic Chrome", "Portra 400", "Kodak Gold", "Tri-X 400", "Velvia 50", "Natura 1600"], id: \.self) { film in
                            Text(film)
                                .font(.system(size: 11, weight: .medium, design: .rounded))
                                .foregroundStyle(Studio.muted)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule()
                                        .fill(Color.white.opacity(0.9))
                                        .overlay(Capsule().strokeBorder(Color.black.opacity(0.05), lineWidth: 0.8))
                                )
                        }
                    }
                }
            }
            .padding(.vertical, 10)
        }
    }
}

private struct AlbumCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let accentColor: Color
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
                            .fill(accentColor.opacity(0.12))
                            .frame(width: 32, height: 32)
                        Image(systemName: icon)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(accentColor)
                    }
                    Spacer()
                }
                
                Spacer(minLength: 4)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(Studio.ink)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.system(size: 11, weight: .regular))
                        .foregroundStyle(Studio.muted)
                        .lineLimit(1)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .strokeBorder(Color.black.opacity(0.05), lineWidth: 0.8)
                    )
                    .shadow(color: Color.black.opacity(0.03), radius: 6, x: 0, y: 2)
            )
        }
        .buttonStyle(.plain)
    }
}
