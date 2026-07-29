import Foundation
import Observation

enum AuthState: Equatable {
    case loading
    case unauthenticated
    /// Role is admin or otherwise not supported by this app (parity with
    /// Android's UnsupportedRoleScreen — admin uses the web app instead).
    case unsupportedRole
    case authenticated(CurrentUser)
}

/// Root session/auth state for the app. Mirrors Android's SessionRepository:
/// bootstraps from a stored token on launch, and a 401 from anywhere forces
/// a global logout back to the login screen.
@Observable
@MainActor
final class AuthSession {
    private(set) var state: AuthState = .loading

    private let apiClient = APIClient.shared

    init() {
        Task { [weak self] in
            for await _ in NotificationCenter.default.notifications(named: .apiUnauthorized) {
                self?.logout()
            }
        }
    }

    func bootstrap() async {
        guard await KeychainStore.loadToken() != nil else {
            state = .unauthenticated
            return
        }
        do {
            let user: CurrentUser = try await apiClient.send(Endpoint(path: "auth/me"))
            state = resolvedState(for: user)
        } catch {
            await KeychainStore.deleteToken()
            state = .unauthenticated
        }
    }

    func login(email: String, password: String) async throws {
        let body = try apiClient.encode(LoginRequest(email: email, password: password))
        let token: TokenResponse = try await apiClient.send(
            Endpoint(path: "auth/login", method: .post, body: body, requiresAuth: false)
        )
        await KeychainStore.saveToken(token.accessToken)

        let user: CurrentUser = try await apiClient.send(Endpoint(path: "auth/me"))
        state = resolvedState(for: user)
    }

    /// Synchronous on purpose — flips the UI back to the login screen
    /// instantly, without waiting on the Keychain delete, which happens in
    /// the background.
    func logout() {
        state = .unauthenticated
        BusinessProfile.shared.clear()
        Task { await KeychainStore.deleteToken() }
    }

    private func resolvedState(for user: CurrentUser) -> AuthState {
        guard user.isActive, user.role.usesMainShell || user.role.usesOwnerShell else {
            return .unsupportedRole
        }
        BusinessProfile.shared.update(from: user)
        return .authenticated(user)
    }
}
