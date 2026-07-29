import Foundation
import Observation

@Observable
@MainActor
final class LoginViewModel {
    var email: String = ""
    var password: String = ""
    private(set) var isSubmitting: Bool = false
    private(set) var errorMessage: String?

    var canSubmit: Bool {
        !isSubmitting
            && !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !password.isEmpty
    }

    func submit(session: AuthSession) async {
        guard canSubmit else { return }
        isSubmitting = true
        errorMessage = nil

        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)

        do {
            try await session.login(email: trimmedEmail, password: trimmedPassword)
        } catch let error as APIError {
            errorMessage = error.userMessage
        } catch {
            errorMessage = APIError.unknown.userMessage
        }
        isSubmitting = false
    }
}
