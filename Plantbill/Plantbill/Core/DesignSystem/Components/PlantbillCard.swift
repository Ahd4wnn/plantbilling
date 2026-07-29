import SwiftUI

/// Shared surface container — soft shadow + subtle border for depth,
/// "minimal done with craft" rather than a flat gray box.
struct PlantbillCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(PlantbillSpacing.md)
            .background(
                RoundedRectangle(cornerRadius: PlantbillSpacing.cardCornerRadius)
                    .fill(PlantbillColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: PlantbillSpacing.cardCornerRadius)
                    .stroke(PlantbillColor.border, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.06), radius: 12, y: 4)
            // Cards are often used as a Button's whole label (e.g. product
            // grid cells) — without this, only the opaque children (icons,
            // text) are tappable, not the card's padding/background.
            .contentShape(Rectangle())
    }
}
