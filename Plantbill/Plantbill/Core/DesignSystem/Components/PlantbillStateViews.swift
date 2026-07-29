import SwiftUI

/// Full-screen loading state — plain, calm, no jargon.
struct LoadingStateView: View {
    var message: LocalizedStringKey = "Loading…"

    var body: some View {
        VStack(spacing: PlantbillSpacing.md) {
            ProgressView()
                .controlSize(.large)
                .tint(PlantbillColor.green)
            Text(message)
                .font(PlantbillTypography.body)
                .foregroundStyle(PlantbillColor.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PlantbillColor.background)
    }
}

/// Full-screen empty state — friendly, explains what's missing and (if
/// given) what to do about it.
struct EmptyStateView: View {
    let icon: String
    let title: LocalizedStringKey
    var message: LocalizedStringKey? = nil
    var actionTitle: LocalizedStringKey? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: PlantbillSpacing.md) {
            Image(systemName: icon)
                .font(.system(size: 44))
                .foregroundStyle(PlantbillColor.textSecondary)
            Text(title)
                .font(PlantbillTypography.headline)
                .foregroundStyle(PlantbillColor.textPrimary)
                .multilineTextAlignment(.center)
            if let message {
                Text(message)
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
                    .multilineTextAlignment(.center)
            }
            if let actionTitle, let action {
                SecondaryButton(title: actionTitle, action: action)
                    .padding(.top, PlantbillSpacing.sm)
                    .frame(maxWidth: 260)
            }
        }
        .padding(PlantbillSpacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PlantbillColor.background)
    }
}

/// Full-screen error state — plain-language message only, never a raw error
/// code or stack trace. Always offers a way forward (retry). `message` is
/// usually a dynamic, already-English string from `APIError.userMessage` —
/// wrapping a `String` in `LocalizedStringKey` with no matching catalog
/// entry just renders it verbatim, which is exactly what we want here.
struct ErrorStateView: View {
    let message: LocalizedStringKey
    var retryTitle: LocalizedStringKey = "Try again"
    var onRetry: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: PlantbillSpacing.md) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44))
                .foregroundStyle(PlantbillColor.warning)
            Text("Something went wrong")
                .font(PlantbillTypography.headline)
                .foregroundStyle(PlantbillColor.textPrimary)
            Text(message)
                .font(PlantbillTypography.body)
                .foregroundStyle(PlantbillColor.textSecondary)
                .multilineTextAlignment(.center)
            if let onRetry {
                PrimaryButton(title: retryTitle, action: onRetry)
                    .padding(.top, PlantbillSpacing.sm)
                    .frame(maxWidth: 280)
            }
        }
        .padding(PlantbillSpacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PlantbillColor.background)
    }
}

/// Inline (non-full-screen) error text — for form field errors, etc.
struct InlineErrorText: View {
    let message: LocalizedStringKey

    var body: some View {
        Text(message)
            .font(PlantbillTypography.caption)
            .foregroundStyle(PlantbillColor.error)
    }
}
