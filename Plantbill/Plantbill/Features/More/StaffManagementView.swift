import SwiftUI

struct StaffManagementView: View {
    @State private var viewModel = StaffViewModel()
    @State private var pendingDelete: Salesperson?

    var body: some View {
        content
            .navigationTitle("Salespeople")
            .navigationBarTitleDisplayMode(.inline)
            .background(PlantbillColor.background)
            .task { await viewModel.load() }
            .safeAreaInset(edge: .bottom) {
                PrimaryButton(title: "Add salesperson") { viewModel.openCreate() }
                    .padding(.horizontal, PlantbillSpacing.md)
                    .padding(.vertical, PlantbillSpacing.sm)
                    .background(.bar)
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { viewModel.message != nil },
                set: { if !$0 { viewModel.dismissMessage() } }
            )) {
                Button("OK") { viewModel.dismissMessage() }
            } message: {
                Text(viewModel.message ?? "")
            }
            .sheet(isPresented: Binding(get: { viewModel.createForm != nil }, set: { if !$0 { viewModel.closeCreate() } })) {
                CreateStaffSheet(viewModel: viewModel)
            }
            .sheet(isPresented: Binding(get: { viewModel.resetForm != nil }, set: { if !$0 { viewModel.closeReset() } })) {
                ResetPasswordSheet(viewModel: viewModel)
            }
            .alert(item: Binding(get: { viewModel.credentials }, set: { if $0 == nil { viewModel.dismissCredentials() } })) { cred in
                Alert(
                    title: Text(cred.isReset ? "Password reset" : "Account created"),
                    message: Text("Share these sign-in details securely. They won't be shown again.\n\nEmail: \(cred.email)\nPassword: \(cred.password)"),
                    dismissButton: .default(Text("Close")) { viewModel.dismissCredentials() }
                )
            }
            .sheet(item: $pendingDelete) { sp in
                DeleteStaffConfirmSheet(salesperson: sp) {
                    Task { await viewModel.deleteStaff(sp) }
                    pendingDelete = nil
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading staff…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else if viewModel.staff.isEmpty {
            EmptyStateView(icon: "person.2", title: "No salespeople yet", message: "Add your first salesperson account below.")
        } else {
            List(viewModel.staff) { sp in
                StaffRow(
                    sp: sp,
                    onToggle: { Task { await viewModel.toggleActive(sp) } },
                    onReset: { viewModel.openReset(sp) },
                    onDelete: { pendingDelete = sp }
                )
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable { await viewModel.load() }
        }
    }
}

private struct StaffRow: View {
    let sp: Salesperson
    let onToggle: () -> Void
    let onReset: () -> Void
    let onDelete: () -> Void

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(sp.email)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text(sp.isActive ? "Active" : "Inactive")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(sp.isActive ? PlantbillColor.green : PlantbillColor.error)
                }
                HStack(spacing: PlantbillSpacing.sm) {
                    SecondaryButton(title: sp.isActive ? "Deactivate" : "Activate", action: onToggle)
                    SecondaryButton(title: "Reset password", action: onReset)
                }
                SecondaryButton(title: "Remove salesperson", tint: PlantbillColor.error, action: onDelete)
            }
        }
    }
}

private struct CreateStaffSheet: View {
    @Bindable var viewModel: StaffViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text("Add salesperson")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    PlantbillTextField(label: "Login email", text: Binding(get: { form.email }, set: { viewModel.createForm?.email = $0; viewModel.createForm?.error = nil }), placeholder: "you@example.com", keyboardType: .emailAddress)
                    PlantbillTextField(label: "Password", text: Binding(get: { form.password }, set: { viewModel.createForm?.password = $0; viewModel.createForm?.error = nil }))

                    Button("Generate a password") { viewModel.regeneratePassword() }
                        .font(PlantbillTypography.caption)

                    if let error = form.error {
                        InlineErrorText(message: LocalizedStringKey(error))
                    }

                    PrimaryButton(title: "Create account", isLoading: form.saving, isDisabled: !form.canSave) {
                        Task { await viewModel.createStaff() }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closeCreate() }
                }
            }
        }
    }

    private var form: CreateForm { viewModel.createForm ?? CreateForm() }
}

private struct ResetPasswordSheet: View {
    @Bindable var viewModel: StaffViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text("Reset password")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text(form.sp.email)
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.textSecondary)

                    PlantbillTextField(label: "New password", text: Binding(get: { form.password }, set: { viewModel.resetForm?.password = $0; viewModel.resetForm?.error = nil }))

                    Button("Generate a password") { viewModel.regenerateResetPassword() }
                        .font(PlantbillTypography.caption)

                    if let error = form.error {
                        InlineErrorText(message: LocalizedStringKey(error))
                    }

                    PrimaryButton(title: "Reset password", isLoading: form.saving, isDisabled: !form.canSave) {
                        Task { await viewModel.confirmReset() }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closeReset() }
                }
            }
        }
    }

    private var form: ResetForm { viewModel.resetForm ?? ResetForm(sp: Salesperson(id: UUID(), email: "", isActive: true)) }
}

private struct DeleteStaffConfirmSheet: View {
    let salesperson: Salesperson
    let onConfirm: () -> Void

    @State private var typedEmail = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                Text("Remove this salesperson?")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Text("This permanently deletes \(salesperson.email)'s account. To confirm, type their email address below.")
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)

                PlantbillTextField(label: "Email", text: $typedEmail, placeholder: LocalizedStringKey(salesperson.email))

                PrimaryButton(title: "Delete", isDisabled: typedEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() != salesperson.email.lowercased()) {
                    onConfirm()
                    dismiss()
                }

                Spacer()
            }
            .padding(PlantbillSpacing.lg)
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
