import SwiftUI

/// Parity with Android's UnsupportedRoleScreen — admin accounts (and any
/// role this build doesn't recognize) use the web app instead.
struct UnsupportedRoleView: View {
    @Environment(AuthSession.self) private var session

    var body: some View {
        VStack(spacing: PlantbillSpacing.lg) {
            Image(systemName: "desktopcomputer")
                .font(.system(size: 44))
                .foregroundStyle(PlantbillColor.textSecondary)
            Text("This account isn't set up for the app")
                .font(PlantbillTypography.headline)
                .foregroundStyle(PlantbillColor.textPrimary)
                .multilineTextAlignment(.center)
            Text("Please use the Plantbill web dashboard for this account type.")
                .font(PlantbillTypography.body)
                .foregroundStyle(PlantbillColor.textSecondary)
                .multilineTextAlignment(.center)
            SecondaryButton(title: "Log out") { session.logout() }
                .frame(maxWidth: 260)
                .padding(.top, PlantbillSpacing.sm)
        }
        .padding(PlantbillSpacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PlantbillColor.background)
    }
}

#Preview {
    UnsupportedRoleView()
        .environment(AuthSession())
}
