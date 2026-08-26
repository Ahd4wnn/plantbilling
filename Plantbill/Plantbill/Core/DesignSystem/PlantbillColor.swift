import SwiftUI

/// Orange, Apple/Stripe-calm palette. All colors are asset-catalog
/// backed (Assets.xcassets) with light/dark variants baked in.
///
/// The two brand entries are still *named* green — the asset names are load-
/// bearing strings used across every view, and renaming them can't be verified
/// without a Mac to build on. Only their values changed (#F05B01 light /
/// #FF8534 dark), matching Brand.Orange in the Android app's ui/theme/Color.kt.
enum PlantbillColor {
    /// The brand colour. Orange despite the name — see the note above.
    static let green = Color("PlantbillGreen")
    static let greenTint = Color("PlantbillGreenTint")

    static let textPrimary = Color("PlantbillTextPrimary")
    static let textSecondary = Color("PlantbillTextSecondary")

    static let background = Color("PlantbillBackground")
    static let surface = Color("PlantbillSurface")
    static let border = Color("PlantbillBorder")

    static let success = Color("PlantbillSuccess")
    static let error = Color("PlantbillError")
    static let warning = Color("PlantbillWarning")
}
