import SwiftUI

/// Placeholder root screen for tabs not yet built (Bill/Sales/Customers land
/// in later phases). Still a real NavigationStack root with the bell, so the
/// shell is fully wired even before each feature lands.
struct ComingSoonView: View {
    let title: LocalizedStringKey
    let icon: String

    var body: some View {
        NavigationStack {
            EmptyStateView(
                icon: icon,
                title: title,
                message: "This is on its way in a future update."
            )
            .navigationTitle(title)
            .background(PlantbillColor.background)
            .notificationBell()
        }
    }
}
