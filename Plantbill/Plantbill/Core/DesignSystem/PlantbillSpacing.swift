import CoreGraphics

/// Generous, intentional spacing scale — "minimal done with craft," not
/// minimal-as-absence.
enum PlantbillSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48

    /// Minimum touch target (hard floor per elderly-friendly requirement).
    static let minTouchTarget: CGFloat = 48
    /// Primary action height — full-width, unmistakably pressable.
    static let primaryActionHeight: CGFloat = 56

    static let cardCornerRadius: CGFloat = 16
    static let controlCornerRadius: CGFloat = 14
}
