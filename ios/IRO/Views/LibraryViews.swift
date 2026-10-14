import SwiftUI

struct RecipeLibraryView: View {
    @EnvironmentObject var editor: EditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var sensor = "Tất cả"
    @State private var details: Recipe?

    private var sensors: [String] { ["Tất cả"] + Array(Set(editor.recipes.flatMap(\.sensors))).sorted() }
    private var filtered: [Recipe] {
        editor.recipes.filter {
            (sensor == "Tất cả" || $0.sensors.contains(sensor)) &&
            (query.isEmpty || ($0.name + " " + $0.film).localizedStandardContains(query))
        }
    }

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                let isWide = geo.size.width >= 540
                VStack(spacing: 0) {
                    // Sensor filter bar
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(sensors, id: \.self) { item in
                                let selected = sensor == item
                                Button {
                                    Haptics.selection()
                                    sensor = item
                                } label: {
                                    Text(item)
                                        .font(.system(size: 12, weight: selected ? .bold : .medium, design: .rounded))
                                        .foregroundStyle(selected ? Color.white : Studio.ink)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(
                                            Capsule()
                                                .fill(selected ? Studio.ink : Studio.surface)
                                                .overlay(Capsule().strokeBorder(selected ? Color.clear : Studio.line, lineWidth: 1))
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }

                    HStack {
                        Text("Hiển thị \(filtered.count) màu film")
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(Studio.muted)
                        Spacer()
                        Button {
                            Task { await editor.originalLook(); dismiss() }
                        } label: {
                            Label("Về ảnh gốc", systemImage: "circle.lefthalf.filled")
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                                .foregroundStyle(Studio.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(Studio.surface).overlay(Capsule().strokeBorder(Studio.line, lineWidth: 1)))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 6)

                    if isWide {
                        ScrollView {
                            LazyVGrid(
                                columns: [GridItem(.adaptive(minimum: 155, maximum: 220), spacing: 12)],
                                spacing: 12
                            ) {
                                ForEach(filtered) { recipe in
                                    let isSelected = editor.recipe?.id == recipe.id
                                    let hasLUT = editor.filmBindings[recipe.film] != nil
                                    VStack(alignment: .leading, spacing: 8) {
                                        Button {
                                            Haptics.selection()
                                            Task { await editor.apply(recipe); dismiss() }
                                        } label: {
                                            VStack(alignment: .leading, spacing: 8) {
                                                ZStack(alignment: .topTrailing) {
                                                    RecipeGridThumbnail(recipe: recipe)
                                                    if isSelected {
                                                        Image(systemName: "checkmark.circle.fill")
                                                            .foregroundStyle(Studio.ink)
                                                            .background(Circle().fill(Color.white))
                                                            .font(.title3)
                                                            .padding(6)
                                                    }
                                                }
                                                Text(recipe.name)
                                                    .font(.system(size: 13, weight: .bold, design: .rounded))
                                                    .foregroundStyle(Studio.ink)
                                                    .lineLimit(1)
                                                Text(recipe.film)
                                                    .font(.system(size: 11, weight: .medium, design: .rounded))
                                                    .foregroundStyle(Studio.muted)
                                                    .lineLimit(1)
                                            }
                                            .contentShape(Rectangle())
                                        }
                                        .buttonStyle(.plain)

                                        HStack {
                                            Text(hasLUT ? "Đã gắn LUT" : "Màu Recipe")
                                                .font(.system(size: 10, weight: .semibold, design: .rounded))
                                                .foregroundStyle(hasLUT ? Studio.onAccentContainer : Studio.muted)
                                                .padding(.horizontal, 7)
                                                .padding(.vertical, 2)
                                                .background(
                                                    Capsule().fill(hasLUT ? Studio.accentContainer : Studio.surfaceContainer)
                                                )
                                            Spacer()
                                            Button { details = recipe } label: {
                                                Image(systemName: "info.circle")
                                                    .font(.system(size: 13))
                                                    .foregroundStyle(Studio.muted)
                                            }
                                            .buttonStyle(.plain)
                                            .accessibilityLabel("Thông số \(recipe.name)")
                                        }
                                    }
                                    .padding(10)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                                            .fill(Studio.surface)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                                    .strokeBorder(isSelected ? Studio.ink : Studio.line, lineWidth: isSelected ? 2.0 : 1.0)
                                            )
                                    )
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                        }
                    } else {
                        List {
                            ForEach(filtered) { recipe in
                                HStack(spacing: 12) {
                                    Button {
                                        Task { await editor.apply(recipe); dismiss() }
                                    } label: {
                                        HStack(spacing: 12) {
                                            RecipeThumbnail(recipe: recipe)
                                            VStack(alignment: .leading, spacing: 5) {
                                                Text(recipe.name).font(.subheadline.weight(.medium)).foregroundStyle(Studio.ink)
                                                Text(recipe.film).font(.caption).foregroundStyle(Studio.muted).lineLimit(1)
                                                let hasLUT = editor.filmBindings[recipe.film] != nil
                                                Text(hasLUT ? "Đã gắn LUT" : "Chưa có LUT")
                                                    .font(.caption2.weight(.medium))
                                                    .foregroundStyle(hasLUT ? Studio.onAccentContainer : Studio.muted)
                                                    .padding(.horizontal, 8)
                                                    .padding(.vertical, 2)
                                                    .background(
                                                        Capsule().fill(hasLUT ? Studio.accentContainer : Studio.surfaceContainer)
                                                    )
                                            }
                                            Spacer(minLength: 0)
                                            if editor.recipe?.id == recipe.id {
                                                Image(systemName: "checkmark.circle.fill")
                                                    .foregroundStyle(Studio.accent)
                                                    .font(.title3)
                                            }
                                        }.contentShape(Rectangle())
                                    }.buttonStyle(.plain)
                                    Button { details = recipe } label: { Image(systemName: "info.circle").frame(width: 30, height: 44) }
                                        .buttonStyle(.borderless).accessibilityLabel("Thông số \(recipe.name)")
                                }.listRowBackground(Studio.surface)
                            }
                        }.scrollContentBackground(.hidden)
                    }

                    Text("Nhóm cảm biến theo thư viện nguồn. Recipe là thông số máy ảnh; màu nền cần LUT sRGB phù hợp.")
                        .font(.caption2).foregroundStyle(Studio.muted).padding(.horizontal).padding(.vertical, 6)
                }
            }
            .background(Studio.background)
            .navigationTitle("Chọn màu (\(filtered.count))").navigationBarTitleDisplayMode(.inline)
            .searchable(text: $query, prompt: "Tên recipe hoặc film simulation")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
            .sheet(item: $details) { RecipeDetailsView(recipe: $0, notes: []) }
        }
        .frame(minWidth: 760, idealWidth: 920, minHeight: 580, idealHeight: 680)
    }
}

private struct RecipeGridThumbnail: View {
    @EnvironmentObject var editor: EditorModel
    let recipe: Recipe
    @State private var thumbnail: UIImage?
    var body: some View {
        Group {
            if let thumbnail {
                Image(uiImage: thumbnail)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "photo")
                    .foregroundStyle(Studio.muted)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Studio.surfaceContainer)
            }
        }
        .frame(height: 112)
        .frame(maxWidth: .infinity)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Studio.line, lineWidth: 1))
        .accessibilityHidden(true)
        .task(id: "\(recipe.id)-\(editor.thumbnailVersion)") { thumbnail = await editor.recipeThumbnail(recipe) }
    }
}

private struct RecipeThumbnail: View {
    @EnvironmentObject var editor: EditorModel
    let recipe: Recipe
    @State private var thumbnail: UIImage?
    var body: some View {
        Group {
            if let thumbnail { Image(uiImage: thumbnail).resizable().scaledToFill() }
            else {
                Image(systemName: "photo")
                    .foregroundStyle(Studio.muted)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Studio.surfaceContainer)
            }
        }
        .frame(width: 66, height: 72)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Studio.line, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
        .accessibilityHidden(true)
        .task(id: "\(recipe.id)-\(editor.thumbnailVersion)") { thumbnail = await editor.recipeThumbnail(recipe) }
    }
}

struct RecipeDetailsView: View {
    @Environment(\.dismiss) private var dismiss
    let recipe: Recipe?
    let notes: [String]
    private var mappedNotes: [String] {
        if !notes.isEmpty { return notes }
        var messages: [String] = []
        _ = recipe?.adjustments(notes: &messages)
        return messages
    }
    var body: some View {
        NavigationStack {
            List {
                if let recipe {
                    Section(recipe.name) {
                        LabeledContent("Film", value: recipe.film)
                        LabeledContent("Nhóm", value: recipe.sensors.joined(separator: " / "))
                    }
                    Section("Thông số nguồn") {
                        ForEach(recipe.rawSettings.keys.sorted(), id: \.self) { key in
                            LabeledContent(key, value: recipe.rawSettings[key] ?? "")
                        }
                    }
                    if !mappedNotes.isEmpty {
                        Section("Cách áp dụng trên ảnh") { ForEach(mappedNotes, id: \.self) { Text($0).font(.subheadline) } }
                    }
                    if let url = URL(string: recipe.sourceUrl), url.scheme == "https" {
                        Section { Link("Mở bài nguồn để đối chiếu", destination: url) }
                    }
                } else { Text("Chưa chọn recipe. Các thanh chỉnh tay đang áp dụng trực tiếp lên ảnh.") }
            }.navigationTitle("Thông số recipe").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Đóng") { dismiss() } } }
        }
    }
}

struct RecipeEditView: View {
    @EnvironmentObject var editor: EditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var film = ""
    @State private var raw: [String: String] = [:]
    var body: some View {
        NavigationStack {
            Form {
                Section("Bản riêng của bạn") {
                    TextField("Tên recipe", text: $name)
                    TextField("Film simulation", text: $film)
                }
                Section("Thông số") {
                    ForEach(raw.keys.sorted(), id: \.self) { key in
                        VStack(alignment: .leading) {
                            Text(key).font(.caption).foregroundStyle(.secondary)
                            TextField(key, text: Binding(get: { raw[key] ?? "" }, set: { raw[key] = String($0.prefix(500)) }))
                        }
                    }
                }
            }.navigationTitle("Sửa recipe").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Hủy") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Lưu") {
                            guard var edited = editor.recipe else { return }
                            edited.id = "custom-\(UUID().uuidString)"; edited.name = String(name.prefix(300)); edited.film = String(film.prefix(300)); edited.rawSettings = raw
                            Task { if await editor.saveRecipe(edited) { dismiss() } }
                        }.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || film.isEmpty)
                    }
                }
                .onAppear { name = (editor.recipe?.name ?? "Recipe") + " · bản riêng"; film = editor.recipe?.film ?? "Original"; raw = editor.recipe?.rawSettings ?? [:] }
        }
    }
}

struct LUTLibraryView: View {
    @EnvironmentObject var editor: EditorModel
    @Environment(\.dismiss) private var dismiss
    let importAction: () -> Void
    var body: some View {
        NavigationStack {
            List {
                Section("Film: \(editor.settings.film)") {
                    Text(editor.lutName)
                    Text("Nhập LUT 3D .cube nhận đầu vào sRGB (2–65 điểm mỗi chiều). LUT được gắn riêng cho film đang chọn.")
                        .font(.caption).foregroundStyle(.secondary)
                    if let before = editor.originalImage, let after = editor.editedImage {
                        HStack {
                            VStack { Image(uiImage: before).resizable().scaledToFit(); Text("Ảnh gốc") }
                            VStack { Image(uiImage: after).resizable().scaledToFit(); Text("Đang chỉnh") }
                        }.frame(height: 170).font(.caption)
                    }
                }
                Section {
                    Button("Nhập LUT .cube", systemImage: "plus") { importAction() }
                    Button("Bỏ LUT của film này", systemImage: "minus.circle") { Task { await editor.assign(nil) } }
                }
                Section("LUT đã lưu · dùng được offline") {
                    ForEach(editor.localLUTs) { lut in
                        Button { Task { if await editor.assign(lut) { dismiss() } } } label: {
                            HStack {
                                Text(lut.name)
                                Spacer()
                                if editor.filmBindings[editor.settings.film] == lut.id { Image(systemName: "checkmark") }
                            }
                        }
                    }
                    if editor.localLUTs.isEmpty { Text("Chưa có LUT. Nhập tệp hoặc vào LUT trực tuyến.").foregroundStyle(.secondary) }
                }
            }.navigationTitle("LUT của tôi").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
        }
    }
}

struct OnlineLibraryView: View {
    @EnvironmentObject var editor: EditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    private var filtered: [OnlineLUT] { editor.onlineItems.filter { query.isEmpty || $0.name.localizedStandardContains(query) || $0.path.localizedStandardContains(query) } }
    var body: some View {
        NavigationStack {
            List {
                Section("Kho GitHub công khai") {
                    TextField("owner/repository", text: $editor.onlineRepository).textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button { Task { await editor.refreshOnline() } } label: {
                        HStack { Label("Tìm / cập nhật", systemImage: "arrow.clockwise"); if editor.onlineBusy { Spacer(); ProgressView() } }
                    }.disabled(editor.onlineBusy)
                    Toggle("Tự kiểm tra mỗi ngày khi mở app", isOn: Binding(get: { editor.automaticUpdates }, set: { value in Task { await editor.toggleAutomaticUpdates(value) } }))
                    Text(editor.onlineStatus).font(.caption).foregroundStyle(.secondary)
                }
                Section {
                    Text("Nguồn mặc định: LUMIX Original Looks, MIT, dành cho Standard/sRGB. Đây là màu sáng tạo, không phải LUT Fuji chính thức. Với kho khác, kiểm tra giấy phép và loại ảnh đầu vào của tác giả.")
                        .font(.caption).foregroundStyle(.secondary)
                    Text("Ảnh của bạn không gửi lên mạng. Bản LUT mới được lưu riêng; LUT đang gắn được giữ nguyên khi tự cập nhật.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section("\(filtered.count) LUT") {
                    ForEach(filtered) { item in
                        Button { Task { await editor.download(item) } } label: {
                            VStack(alignment: .leading, spacing: 5) {
                                Text(item.name).font(.subheadline.weight(.medium))
                                Text(item.path).font(.caption2).foregroundStyle(.secondary).lineLimit(2)
                                Text("Giấy phép: \(item.license) · \(item.input)").font(.caption2).foregroundStyle(.secondary)
                                Label("Tải / dùng LUT", systemImage: "arrow.down.circle").font(.caption)
                            }.frame(maxWidth: .infinity, alignment: .leading)
                        }.disabled(editor.onlineBusy)
                    }
                }
            }.navigationTitle("LUT trực tuyến").navigationBarTitleDisplayMode(.inline)
                .searchable(text: $query, prompt: "Tìm trong kho đã chọn")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
                .task { await editor.loadOnlineCache() }
        }
    }
}

struct GeminiSettingsView: View {
    @EnvironmentObject var editor: EditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var keyInput: String = GeminiAutoService.apiKey
    @State private var selectedModel: String = GeminiAutoService.selectedModel
    @State private var showKey: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Trạng thái Gemini AI Auto") {
                    HStack(spacing: 10) {
                        Image(systemName: keyInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "cpu" : "sparkles")
                            .font(.title3)
                            .foregroundStyle(Studio.ink)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(keyInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                 ? "Đang dùng Auto nội bộ (Offline)"
                                 : "Đã bật Gemini Vision AI Auto")
                                .font(.subheadline.weight(.bold))
                            Text(keyInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                 ? "Nhập Gemini API Key để cân bằng ánh sáng và màu tự nhiên từ ảnh gốc. Auto thay các lớp chỉnh màu hiện tại; Hoàn tác để khôi phục."
                                 : "Khi bấm Auto AI, ảnh gốc thu nhỏ tối đa 384px được gửi tới Google Gemini. Auto thay các lớp chỉnh màu hiện tại; bạn có thể Hoàn tác.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }

                Section("Gemini API Key") {
                    HStack {
                        if showKey {
                            TextField("AIzaSy...", text: $keyInput)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .font(.system(.body, design: .monospaced))
                        } else {
                            SecureField("Dán Gemini API Key (AIzaSy...)", text: $keyInput)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                        }
                        Button {
                            showKey.toggle()
                        } label: {
                            Image(systemName: showKey ? "eye.slash" : "eye")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }

                    if !keyInput.isEmpty {
                        Button("Tắt AI, dùng Auto nội bộ", role: .destructive) {
                            keyInput = ""
                        }
                    }

                    if let url = URL(string: "https://aistudio.google.com/apikey") {
                        Link(destination: url) {
                            Label("Lấy Gemini API Key tại Google AI Studio", systemImage: "arrow.up.right.square")
                                .font(.footnote.weight(.medium))
                        }
                    }
                }

                Section("Mô hình AI") {
                    Picker("Mô hình", selection: $selectedModel) {
                        ForEach(GeminiAutoService.availableModels, id: \.id) { item in
                            Text(item.title).tag(item.id)
                        }
                    }
                    Text("Thời gian phân tích phụ thuộc mô hình và kết nối. Nếu dịch vụ lỗi, ứng dụng báo rõ và dùng Auto nội bộ.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if editor.hasPhoto && !keyInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Section {
                        Button {
                            GeminiAutoService.apiKey = keyInput
                            GeminiAutoService.selectedModel = selectedModel
                            dismiss()
                            Task { await editor.autoBalance() }
                        } label: {
                            Label("Lưu & Chạy Gemini AI Auto cho ảnh hiện tại", systemImage: "wand.and.stars")
                                .font(.body.weight(.semibold))
                        }
                    }
                }
            }
            .navigationTitle("Gemini AI Auto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Hủy") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Lưu") {
                        GeminiAutoService.apiKey = keyInput
                        GeminiAutoService.selectedModel = selectedModel
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        #if targetEnvironment(macCatalyst)
        .frame(minWidth: 520, idealWidth: 580, minHeight: 460, idealHeight: 520)
        #endif
    }
}
