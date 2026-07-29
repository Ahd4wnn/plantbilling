import SwiftUI

struct LoginView: View {
    @Environment(AuthSession.self) private var session
    @State private var viewModel = LoginViewModel()
    @FocusState private var focusedField: Field?

    private enum Field { case email, password }

    var body: some View {
        ScrollView {
            VStack(spacing: PlantbillSpacing.xl) {
                header

                VStack(spacing: PlantbillSpacing.md) {
                    PlantbillTextField(
                        label: "Email",
                        text: $viewModel.email,
                        placeholder: "you@example.com",
                        keyboardType: .emailAddress,
                        textContentType: .username
                    )
                    .focused($focusedField, equals: .email)
                    .submitLabel(.next)
                    .onSubmit { focusedField = .password }

                    PlantbillTextField(
                        label: "Password",
                        text: $viewModel.password,
                        placeholder: "Your password",
                        isSecure: true,
                        textContentType: .password
                    )
                    .focused($focusedField, equals: .password)
                    .submitLabel(.go)
                    .onSubmit { submit() }

                    if let errorMessage = viewModel.errorMessage {
                        InlineErrorText(message: LocalizedStringKey(errorMessage))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }

                PrimaryButton(
                    title: "Sign in",
                    isLoading: viewModel.isSubmitting,
                    isDisabled: !viewModel.canSubmit,
                    action: submit
                )

                supportFooter
            }
            .padding(PlantbillSpacing.lg)
            .padding(.top, PlantbillSpacing.xxl)
        }
        .background(PlantbillColor.background)
        .scrollDismissesKeyboard(.interactively)
    }

    private var header: some View {
        VStack(spacing: PlantbillSpacing.sm) {
            Image(systemName: "leaf.fill")
                .font(.system(size: 44))
                .foregroundStyle(PlantbillColor.green)
            Text("Plantbill")
                .font(PlantbillTypography.largeTitle)
                .foregroundStyle(PlantbillColor.textPrimary)
            Text("Sign in to your shop")
                .font(PlantbillTypography.body)
                .foregroundStyle(PlantbillColor.textSecondary)
        }
    }

    private var supportFooter: some View {
        VStack(spacing: PlantbillSpacing.sm) {
            Text("Need help signing in?")
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)
            HStack(spacing: PlantbillSpacing.lg) {
                Link(destination: URL(string: "tel:+917975402266")!) {
                    Label("Call support", systemImage: "phone.fill")
                }
                Link(destination: URL(string: "mailto:support@dofida.in")!) {
                    Label("Email support", systemImage: "envelope.fill")
                }
            }
            .font(PlantbillTypography.caption)
            .foregroundStyle(PlantbillColor.green)
        }
        .padding(.top, PlantbillSpacing.lg)
    }

    private func submit() {
        Task { await viewModel.submit(session: session) }
    }
}

#Preview {
    LoginView()
        .environment(AuthSession())
}
