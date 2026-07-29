import Foundation

/// Mirrors backend `ProductOut` (app/schemas/product.py).
struct Product: Decodable, Identifiable, Equatable {
    let id: UUID
    let name: String
    let category: String?
    let retailPrice: String
    let lastWholesalePrice: String?
    let photoUrl: String?
    let isActive: Bool
    let createdAt: Date

    var price: Money { Money.parse(retailPrice) }
    var resolvedPhotoURL: URL? { MediaURL.resolve(photoUrl) }
}
