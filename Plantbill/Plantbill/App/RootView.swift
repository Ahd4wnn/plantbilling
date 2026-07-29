import SwiftUI

struct RootView: View {
    @Environment(AuthSession.self) private var session

    var body: some View {
        Group {
            switch session.state {
            case .loading:
                LoadingStateView()
            case .unauthenticated:
                LoginView()
            case .unsupportedRole:
                UnsupportedRoleView()
            case .authenticated(let user):
                if user.role.usesMainShell {
                    MainTabView(user: user)
                } else {
                    // Multi-shop owner shell lands in Phase 9.
                    OwnerShellPlaceholderView()
                }
            }
        }
        .task { await session.bootstrap() }
    }
}

private struct OwnerShellPlaceholderView: View {
    @Environment(AuthSession.self) private var session

    var body: some View {
        VStack(spacing: PlantbillSpacing.lg) {
            Image(systemName: "chart.bar.fill")
                .font(.system(size: 44))
                .foregroundStyle(PlantbillColor.green)
            Text("Owner dashboard is coming soon")
                .font(PlantbillTypography.headline)
                .foregroundStyle(PlantbillColor.textPrimary)
                .multilineTextAlignment(.center)
            SecondaryButton(title: "Log out") { session.logout() }
                .frame(maxWidth: 260)
        }
        .padding(PlantbillSpacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PlantbillColor.background)
    }
}

#Preview {
    RootView()
        .environment(AuthSession())
}
