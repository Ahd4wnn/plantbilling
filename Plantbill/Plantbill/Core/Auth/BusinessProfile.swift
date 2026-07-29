import Foundation

/// Lightweight cache of the signed-in shop's display info, populated from
/// `CurrentUser` (already fetched at login/bootstrap via `GET /auth/me`) so
/// screens that just need the UPI ID or shop name for display — like the
/// Bill screen's QR code — don't need a separate network round-trip.
final class BusinessProfile {
    static let shared = BusinessProfile()

    private(set) var upi: String?
    private(set) var businessName: String?
    private(set) var shopName: String?

    private init() {}

    func update(from user: CurrentUser) {
        upi = user.businessUpi
        businessName = user.businessName
        shopName = user.shopName
    }

    func clear() {
        upi = nil
        businessName = nil
        shopName = nil
    }
}
