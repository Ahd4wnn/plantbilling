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
                    OwnerDashboardView(email: user.email) { session.logout() }
                }
            }
        }
        .task { await session.bootstrap() }
    }
}

#Preview {
    RootView()
        .environment(AuthSession())
}
