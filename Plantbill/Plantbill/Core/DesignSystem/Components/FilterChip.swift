import SwiftUI

struct FilterChip: View {
    let title: LocalizedStringKey
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(PlantbillTypography.caption)
                .fontWeight(.medium)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .padding(.horizontal, PlantbillSpacing.md)
                .padding(.vertical, PlantbillSpacing.sm)
                .foregroundStyle(isSelected ? .white : PlantbillColor.textPrimary)
                .background(
                    Capsule().fill(isSelected ? PlantbillColor.green : PlantbillColor.surface)
                )
                .overlay(
                    Capsule().stroke(isSelected ? Color.clear : PlantbillColor.border, lineWidth: 1)
                )
                .contentShape(Capsule())
        }
        // Without an explicit style, multiple sibling chips inside one List
        // row can mis-route taps to the wrong chip (List applies its own
        // button-handling to unstyled Buttons) — same root cause as the
        // date-selector chevron bug. `.plain` keeps each chip independent.
        .buttonStyle(.plain)
    }
}
