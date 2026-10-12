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
            VStack(spacing: 0) {
                HStack {
                    Picker("Nhóm cảm biến", selection: $sensor) { ForEach(sensors, id: \.self) { Text($0) } }
                    Spacer()
                    Text("\(filtered.count) recipe").font(.caption).foregroundStyle(Studio.muted)
                }.padding(.horizontal)
                List {
                    Button {
                        Task { await editor.originalLook(); dismiss() }
                    } label: { Label("Nguyên bản", systemImage: "circle.lefthalf.filled") }
                    ForEach(filtered) { recipe in
                        HStack(spacing: 12) {
                            Button {
                                Task { await editor.apply(recipe); dismiss() }
                            } label: {
                                HStack(spacing: 12) {
                                    RecipeThumbnail(recipe: recipe)
                                    VStack(alignment: .leading, spacing: 5) {
                                        Text(recipe.name).font(.subheadline.weight(.medium)).foregroundStyle(Studio.ink)
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
                Text("Nhóm cảm biến theo thư viện nguồn. Recipe là thông số máy ảnh; màu nền cần LUT sRGB phù hợp.")
                    .font(.caption2).foregroundStyle(Studio.muted).padding(.horizontal).padding(.bottom, 8)
            }
            .background(Studio.background)
            .navigationTitle("Chọn màu").navigationBarTitleDisplayMode(.inline)
            .searchable(text: $query, prompt: "Tên recipe hoặc film simulation")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
            .sheet(item: $details) { RecipeDetailsView(recipe: $0, notes: []) }
        }
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
