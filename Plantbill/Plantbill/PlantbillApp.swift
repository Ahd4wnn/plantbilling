import SwiftUI

@main
struct PlantbillApp: App {
    @State private var session = AuthSession()
    @State private var languageStore = LanguageStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(languageStore)
                .environment(\.locale, languageStore.current.locale)
                // Forces the whole tree to rebuild on language change —
                // mirrors Android's activity-recreation-on-language-switch,
                // since SwiftUI otherwise won't re-evaluate already-resolved
                // LocalizedStringKey text on a plain environment change.
                .id(languageStore.current)
        }
    }
}
