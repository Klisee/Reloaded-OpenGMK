import SwiftUI

@main
struct ReloadedOpenGMKApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}
