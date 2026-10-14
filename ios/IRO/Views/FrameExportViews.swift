import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct FrameEditorView: View {
    @EnvironmentObject var editor: EditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var style = 0
    @State private var info = PhotoInfo()
    @State private var group = 0
    @State private var preview: UIImage?
    private let groups = ["Tất cả", "Thông số máy", "Tối giản", "Film / hoài cổ", "Sáng tạo", "Mẫu mới"]
    private var styles: [Int] { Array(0..<PhotoFrame.styles.count).filter { group == 0 || (group == 5 ? $0 >= 19 : PhotoFrame.group($0) == group) } }
    private var previewKey: String { "\(style)|\(info.camera)|\(info.focal)|\(info.aperture)|\(info.shutter)|\(info.iso)|\(info.date)|\(info.author)" }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if let preview {
                        Image(uiImage: preview).resizable().scaledToFit().frame(height: 200).frame(maxWidth: .infinity)
                    }
                    Picker("Nhóm khung", selection: $group) { ForEach(groups.indices, id: \.self) { Text(groups[$0]).tag($0) } }
                        .pickerStyle(.menu)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 105))], spacing: 12) {
                        ForEach(styles, id: \.self) { value in
                            Button { style = value } label: {
                                FrameTile(style: value, info: info)
                                    .padding(7)
                                    .background(style == value ? Studio.accent.opacity(0.15) : Studio.surface, in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(style == value ? Studio.accent : Studio.line))
                            }.buttonStyle(.plain).accessibilityAddTraits(style == value ? .isSelected : [])
                        }
                    }
                    VStack(spacing: 14) {
                        Text("Thông tin lấy từ ảnh gốc").font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                        field("Máy ảnh", \.camera)
                        field("Tiêu cự", \.focal)
                        field("Khẩu độ", \.aperture)
                        field("Tốc độ", \.shutter)
                        field("ISO", \.iso)
                        field("Ngày chụp", \.date)
                        field("Tác giả / chữ ký", \.author)
                        Text("Trường thiếu EXIF được để trống. Bạn có thể sửa chữ; ảnh gốc không thay đổi. Khung và chữ sẽ được vẽ vào ảnh xuất.")
                            .font(.caption).foregroundStyle(Studio.muted)
                    }.padding(16).background(Studio.surface, in: RoundedRectangle(cornerRadius: 16))
                }.padding()
            }.background(Studio.background)
                .navigationTitle("Khung thông tin ảnh").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Hủy") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Áp dụng") { editor.applyFrame(style: style, info: info); dismiss() }
                    }
                }
                .onAppear { style = editor.frameStyle; info = editor.photoInfo }
                .task(id: previewKey) {
                    do { try await Task.sleep(for: .milliseconds(100)); preview = await editor.frameThumbnail(style: style, info: info) } catch { }
                }
        }
    }

    private func field(_ label: String, _ key: WritableKeyPath<PhotoInfo, String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(Studio.muted)
            TextField(label, text: Binding(get: { info[keyPath: key] }, set: { info[keyPath: key] = String($0.replacingOccurrences(of: "\n", with: " ").prefix(100)) }))
                .textFieldStyle(.roundedBorder)
        }
    }
}

private struct FrameTile: View {
    @EnvironmentObject var editor: EditorModel
    let style: Int
    let info: PhotoInfo
    @State private var image: UIImage?
    var body: some View {
        VStack(spacing: 8) {
            Group {
                if let image { Image(uiImage: image).resizable().scaledToFit() }
                else { Image(systemName: "photo.artframe").foregroundStyle(Studio.muted) }
            }.frame(height: 95)
            Text(PhotoFrame.styles[style].components(separatedBy: " · ").first ?? "Khung")
                .font(.caption).lineLimit(1).foregroundStyle(Studio.ink)
        }.frame(maxWidth: .infinity)
            .task(id: "\(style)-\(info)") { image = await editor.frameThumbnail(style: style, info: info) }
    }
}

struct ExportedImageDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.jpeg, .png] }
    var data: Data
    init(url: URL?) {
        self.data = (url.flatMap { try? Data(contentsOf: $0) }) ?? Data()
    }
    init(configuration: ReadConfiguration) throws {
        self.data = configuration.file.regularFileContents ?? Data()
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

struct ExportView: View {
    @EnvironmentObject var editor: EditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var size: ExportSize = .double
    @State private var format: ExportFormat = .jpeg100
    @State private var showShare = false
    @State private var showFileSaver = false
    @State private var exportDoc = ExportedImageDocument(url: nil)
    @State private var saving = false
    @State private var saved = false
    @State private var savedFile = false
    @State private var initialized = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Kích thước") {
                    Picker("Ảnh xuất", selection: $size) {
                        ForEach(ExportSize.allCases) { Text($0.title).tag($0) }
                    }
                    Text("2× tăng gấp đôi chiều rộng và chiều cao bằng nội suy; không phải AI tái tạo chi tiết. Kích thước gốc và 2× sẽ báo lỗi nếu không đủ bộ nhớ.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section("Định dạng") { Picker("Chất lượng", selection: $format) { ForEach(ExportFormat.allCases) { Text($0.title).tag($0) } } }
                Section {
                    LabeledContent("Khung", value: PhotoFrame.styles[editor.frameStyle])
                    Text("Xuất từ ảnh nguồn, không dùng ảnh xem trước. Đầu ra sRGB 8-bit; không giữ EXIF. Khung trắng làm tăng kích thước canvas.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section {
                    Button {
                        saved = false
                        savedFile = false
                        Task { await editor.export(size: size, format: format) }
                    } label: {
                        HStack {
                            Label("Tạo ảnh xuất", systemImage: "square.and.arrow.up")
                            Spacer()
                            if editor.isExporting { ProgressView() }
                        }
                    }.disabled(editor.isExporting)
                }
                if let url = editor.exportedURL {
                    Section("Đã tạo · \(editor.exportedDimensions)") {
                        Button {
                            exportDoc = ExportedImageDocument(url: url)
                            showFileSaver = true
                        } label: {
                            Label(savedFile ? "Đã lưu vào Finder / Tệp" : "Lưu vào Finder / Tệp…", systemImage: savedFile ? "checkmark.circle" : "folder.badge.plus")
                        }
                        Button {
                            saving = true
                            Task { saved = await editor.saveToPhotos(); saving = false }
                        } label: { Label(saved ? "Đã lưu vào Ảnh" : "Lưu vào Ảnh", systemImage: saved ? "checkmark.circle" : "photo.badge.plus") }
                            .disabled(saving || saved)
                        Button { showShare = true } label: { Label("Chia sẻ…", systemImage: "square.and.arrow.up") }
                        Text(url.lastPathComponent).font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }.disabled(editor.isExporting || saving)
                .navigationTitle("Xuất ảnh").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() }.disabled(editor.isExporting || saving) } }
                .interactiveDismissDisabled(editor.isExporting || saving)
                .sheet(isPresented: $showShare) { if let url = editor.exportedURL { ShareSheet(url: url) } }
                .fileExporter(
                    isPresented: $showFileSaver,
                    document: exportDoc,
                    contentType: format == .png ? .png : .jpeg,
                    defaultFilename: editor.exportedURL?.deletingPathExtension().lastPathComponent ?? "IRO-export"
                ) { result in
                    switch result {
                    case .success: savedFile = true
                    case .failure(let error): editor.show(error)
                    }
                }
                .onAppear {
                    guard !initialized else { return }
                    initialized = true; editor.exportedURL = nil; saved = false; savedFile = false
                }
                .onChange(of: size) { _, _ in editor.exportedURL = nil; saved = false; savedFile = false }
                .onChange(of: format) { _, _ in editor.exportedURL = nil; saved = false; savedFile = false }
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let vc = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        if let popover = vc.popoverPresentationController {
            popover.sourceView = vc.view
            popover.sourceRect = CGRect(x: 160, y: 120, width: 1, height: 1)
            popover.permittedArrowDirections = []
        }
        return vc
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct HelpView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section("Chỉnh màu cùng IRO") {
                    Text("1. Mở ảnh từ Ảnh hoặc Tệp.\n2. Chọn màu từ thư viện recipe.\n3. Gắn LUT sRGB cho film nếu cần màu nền.\n4. Chỉnh tay, chọn Khung rồi Xuất ảnh.")
                }
                Section("Recipe và LUT") {
                    Text("Recipe Fuji là thông số dành cho máy ảnh. IRO chuyển các thông số có thể áp dụng sang hậu kỳ; không tái tạo pipeline độc quyền Fujifilm. App không kèm LUT chính thức.")
                    Text("ISO, WB tuyệt đối và những thông số chưa hỗ trợ được giữ để tham khảo. Red/Blue và nhiệt độ tương đối mới tác động lên cân bằng trắng. Đọc Thông số recipe để xem ghi chú.")
                    Text("Thư viện gồm 391 recipe công khai và 1 recipe tham khảo, sao chép nguyên dữ liệu từ bản Android. Dữ liệu trích tự động; có liên kết nguồn để đối chiếu. Không bao gồm nội dung Patron.")
                }
                Section("Ảnh và quyền riêng tư") {
                    Text("Ảnh được chỉnh ngay trên thiết bị. Chỉ kết nối Internet khi tải LUT hoặc bật tự kiểm tra. Không có tài khoản, quảng cáo hay theo dõi.")
                    Text("Hỗ trợ các ảnh tĩnh ImageIO giải mã được như JPEG, PNG, HEIC. Chưa hỗ trợ RAW hoặc chỉnh Live Photo thành video. Preview tối đa 1280 px; hạt và nét có thể khác khi xuất ở độ phân giải lớn.")
                    Text("Phiên chỉnh sửa và bản sao ảnh gần nhất nằm trong vùng riêng của app. Xuất JPEG/PNG sRGB 8-bit; EXIF chỉ dùng đọc thông tin khung, không chép vào ảnh xuất.")
                }
            }.navigationTitle("Hướng dẫn & giới hạn").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Đóng") { dismiss() } } }
        }
    }
}
