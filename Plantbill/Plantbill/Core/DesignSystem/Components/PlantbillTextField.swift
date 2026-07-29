import SwiftUI

/// Large, legible text field — 48pt+ tall, clear border, obvious focus state.
struct PlantbillTextField: View {
    let label: LocalizedStringKey
    @Binding var text: String
    var placeholder: LocalizedStringKey = ""
    var isSecure: Bool = false
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType? = nil
    var errorMessage: String? = nil

    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
            Text(label)
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)

            Group {
                if isSecure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .font(PlantbillTypography.body)
            .foregroundStyle(PlantbillColor.textPrimary)
            .keyboardType(keyboardType)
            .textContentType(textContentType)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .focused($isFocused)
            .padding(.horizontal, PlantbillSpacing.md)
            .frame(height: PlantbillSpacing.minTouchTarget + 8)
            .background(
                RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                    .fill(PlantbillColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                    .stroke(borderColor, lineWidth: isFocused ? 2 : 1)
            )
            // Same fix as buttons: without this, tapping the padding around
            // the actual text (top/bottom of the 56pt-tall box) doesn't
            // focus the field — only tapping the glyphs themselves does.
            .contentShape(Rectangle())
            .onTapGesture { isFocused = true }

            if let errorMessage {
                InlineErrorText(message: LocalizedStringKey(errorMessage))
            }
        }
    }

    private var borderColor: Color {
        if errorMessage != nil { return PlantbillColor.error }
        return isFocused ? PlantbillColor.green : PlantbillColor.border
    }
}
