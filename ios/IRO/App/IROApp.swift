import SwiftUI

@main
struct IROApp: App {
    @StateObject private var editor = EditorModel()

    var body: some Scene {
        WindowGroup {
            EditorView()
                .environmentObject(editor)
                .preferredColorScheme(.light)
                .tint(Studio.accent)
                .task { await editor.start() }
        }
        .defaultSize(width: 1280, height: 820)
    }
}
