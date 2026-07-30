import Foundation
import Observation

private func generatePassword() -> String {
    let chars = Array("ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789")
    return String((0..<10).map { _ in chars.randomElement()! })
}

/// Result of a create/reset action — shows the one-time credentials.
struct CredentialResult: Equatable, Identifiable {
    let email: String
    let password: String
    let isReset: Bool
    var id: String { email + password }
}

struct CreateForm: Equatable {
    var email: String = ""
    var password: String = generatePassword()
    var saving = false
    var error: String?

    var canSave: Bool { email.contains("@") && password.count >= 8 && !saving }
}

/// Reset a specific salesperson's password to a chosen (or generated) value.
struct ResetForm: Equatable {
    let sp: Salesperson
    var password: String = generatePassword()
    var saving = false
    var error: String?

    var canSave: Bool { password.count >= 8 && !saving }
}

@Observable
@MainActor
final class StaffViewModel {
    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var staff: [Salesperson] = []

    var createForm: CreateForm?
    var resetForm: ResetForm?
    var credentials: CredentialResult?
    var message: String?

    func load() async {
        isLoading = true
        loadError = nil
        do {
            staff = try await APIClient.shared.send(Endpoint(path: "shop/users"))
            isLoading = false
        } catch let error as APIError {
            isLoading = false
            loadError = error.userMessage
        } catch {
            isLoading = false
            loadError = APIError.unknown.userMessage
        }
    }

    func openCreate() { createForm = CreateForm() }
    func closeCreate() { createForm = nil }
    func regeneratePassword() { createForm?.password = generatePassword(); createForm?.error = nil }

    func createStaff() async {
        guard var form = createForm, form.canSave else { return }
        form.saving = true
        createForm = form
        do {
            let body = try APIClient.shared.encode(SalespersonCreateRequest(email: form.email.trimmingCharacters(in: .whitespacesAndNewlines), password: form.password))
            let _: Salesperson = try await APIClient.shared.send(Endpoint(path: "shop/users", method: .post, body: body))
            credentials = CredentialResult(email: form.email.trimmingCharacters(in: .whitespacesAndNewlines), password: form.password, isReset: false)
            createForm = nil
            await load()
        } catch let error as APIError {
            form.saving = false
            form.error = error.userMessage
            createForm = form
        } catch {
            form.saving = false
            form.error = APIError.unknown.userMessage
            createForm = form
        }
    }

    func toggleActive(_ sp: Salesperson) async {
        do {
            let body = try APIClient.shared.encode(SalespersonActivateRequest(isActive: !sp.isActive))
            let _: Salesperson = try await APIClient.shared.send(Endpoint(path: "shop/users/\(sp.id)", method: .patch, body: body))
            message = sp.isActive ? "\(sp.email) deactivated." : "\(sp.email) activated."
            await load()
        } catch let error as APIError {
            message = error.userMessage
        } catch {
            message = APIError.unknown.userMessage
        }
    }

    func openReset(_ sp: Salesperson) { resetForm = ResetForm(sp: sp) }
    func closeReset() { resetForm = nil }
    func regenerateResetPassword() { resetForm?.password = generatePassword(); resetForm?.error = nil }

    func confirmReset() async {
        guard var form = resetForm, form.canSave else { return }
        form.saving = true
        resetForm = form
        do {
            let body = try APIClient.shared.encode(SalespersonResetPasswordRequest(newPassword: form.password))
            let _: Salesperson = try await APIClient.shared.send(Endpoint(path: "shop/users/\(form.sp.id)/reset-password", method: .post, body: body))
            credentials = CredentialResult(email: form.sp.email, password: form.password, isReset: true)
            resetForm = nil
        } catch let error as APIError {
            form.saving = false
            form.error = error.userMessage
            resetForm = form
        } catch {
            form.saving = false
            form.error = APIError.unknown.userMessage
            resetForm = form
        }
    }

    func deleteStaff(_ sp: Salesperson) async {
        do {
            try await APIClient.shared.sendNoContent(Endpoint(path: "shop/users/\(sp.id)", method: .delete))
            message = "\(sp.email) removed."
            await load()
        } catch let error as APIError {
            message = error.userMessage
        } catch {
            message = APIError.unknown.userMessage
        }
    }

    func dismissCredentials() { credentials = nil }
    func dismissMessage() { message = nil }
}
