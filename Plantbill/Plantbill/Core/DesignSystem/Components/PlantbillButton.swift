import SwiftUI

/// Full-width, 56pt, solid-fill primary action button. Unmistakably
/// pressable, with a real pressed-state depth change — one clear primary
/// action per screen.
struct PrimaryButton: View {
    let title: LocalizedStringKey
    var isLoading: Bool = false
    var isDisabled: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                } else {
                    Text(title)
                        .font(PlantbillTypography.button)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: PlantbillSpacing.primaryActionHeight)
            .foregroundStyle(.white)
            .background(
                RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                    .fill(isDisabled ? PlantbillColor.green.opacity(0.4) : PlantbillColor.green)
            )
            // `.background()` alone only draws behind the label — it doesn't
            // extend the tappable area, so taps on the padding around the
            // text (anywhere but the glyphs themselves) silently miss.
            // `.contentShape` makes the whole frame register.
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
        .disabled(isDisabled || isLoading)
        .shadow(color: PlantbillColor.green.opacity(0.25), radius: 10, y: 4)
    }
}

/// Outline-style secondary action — used sparingly, never competes visually
/// with the primary action on the same screen. `tint` defaults to the
/// botanical green but can be overridden (e.g. red for a destructive
/// delete) — set it here rather than via an external `.foregroundStyle`,
/// which an inner `Text`'s own style would just override.
struct SecondaryButton: View {
    let title: LocalizedStringKey
    var tint: Color = PlantbillColor.green
    var isLoading: Bool = false
    var isDisabled: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(tint)
                } else {
                    Text(title)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(tint)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: PlantbillSpacing.primaryActionHeight)
            .background(
                RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                    .stroke(tint, lineWidth: 1.5)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle())
        .disabled(isDisabled || isLoading)
    }
}

private struct PressableButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
