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
    /// Selects all existing text the moment the field gains focus — for
    /// numeric fields (price, amounts) where typing is almost always meant
    /// to replace the whole value, not insert into it. Never combine with
    /// `isSecure`.
    var selectAllOnFocus: Bool = false

    @FocusState private var isFocused: Bool
    @State private var isUIKitFieldFocused = false
    @State private var isPasswordVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
            Text(label)
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)

            HStack(spacing: PlantbillSpacing.xs) {
                Group {
                    if isSecure {
                        // Native TextField/SecureField placeholder text can
                        // render in the system's blue AutoFill-suggestion
                        // color when paired with a username/password
                        // textContentType — passing "" as the real
                        // placeholder and drawing our own colored Text
                        // instead sidesteps that entirely.
                        ZStack(alignment: .leading) {
                            if text.isEmpty {
                                Text(placeholder)
                                    .foregroundStyle(PlantbillColor.textSecondary)
                            }
                            if isPasswordVisible {
                                TextField("", text: $text).focused($isFocused)
                            } else {
                                SecureField("", text: $text).focused($isFocused)
                            }
                        }
                    } else if selectAllOnFocus {
                        // Placeholder is deliberately not threaded through here:
                        // `LocalizedStringKey` has no public API to resolve back
                        // to a plain String, and every current selectAllOnFocus
                        // field is numeric with a "0" placeholder — language-
                        // neutral, so hardcoding it is correct today. Revisit if
                        // a non-numeric selectAllOnFocus field is ever needed.
                        SelectAllTextField(
                            text: $text,
                            placeholder: "0",
                            keyboardType: keyboardType,
                            onFocusChange: { focused in
                                isUIKitFieldFocused = focused
                            }
                        )
                    } else {
                        ZStack(alignment: .leading) {
                            if text.isEmpty {
                                Text(placeholder)
                                    .foregroundStyle(PlantbillColor.textSecondary)
                            }
                            TextField("", text: $text).focused($isFocused)
                        }
                    }
                }
                .font(PlantbillTypography.body)
                .foregroundStyle(PlantbillColor.textPrimary)
                .keyboardType(keyboardType)
                .textContentType(textContentType)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

                if isSecure {
                    Button {
                        isPasswordVisible.toggle()
                    } label: {
                        Image(systemName: isPasswordVisible ? "eye.slash" : "eye")
                            .foregroundStyle(PlantbillColor.textSecondary)
                            .frame(width: PlantbillSpacing.minTouchTarget - PlantbillSpacing.md, height: PlantbillSpacing.minTouchTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(isPasswordVisible ? "Hide password" : "Show password")
                }
            }
            .padding(.horizontal, PlantbillSpacing.md)
            .frame(height: PlantbillSpacing.minTouchTarget + 8)
            .background(
                RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                    .fill(PlantbillColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                    .stroke(borderColor, lineWidth: effectivelyFocused ? 2 : 1)
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

    private var effectivelyFocused: Bool { isFocused || isUIKitFieldFocused }

    private var borderColor: Color {
        if errorMessage != nil { return PlantbillColor.error }
        return effectivelyFocused ? PlantbillColor.green : PlantbillColor.border
    }
}
