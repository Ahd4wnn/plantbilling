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
    }
}
