import SwiftUI

/// Type scale for the shop-owner app. Base size is 17pt+ everywhere, weights
/// are always medium or heavier (never light/thin) per the elderly-friendly
/// legibility bar — near-black text, no tiny low-contrast labels.
enum PlantbillTypography {
    static let largeTitle = Font.system(size: 34, weight: .bold, design: .rounded)
    static let title = Font.system(size: 26, weight: .semibold, design: .rounded)
    static let headline = Font.system(size: 21, weight: .semibold)
    static let body = Font.system(size: 17, weight: .medium)
    static let bodyEmphasized = Font.system(size: 17, weight: .semibold)
    static let button = Font.system(size: 18, weight: .semibold)
    static let caption = Font.system(size: 15, weight: .medium)
}

extension View {
    func plantbillText(_ style: Font) -> some View {
        font(style).foregroundStyle(PlantbillColor.textPrimary)
    }
}
