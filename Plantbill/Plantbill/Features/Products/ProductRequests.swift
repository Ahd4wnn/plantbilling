import Foundation

/// Mirrors backend `ProductCreate`. Money fields are sent as wire strings
/// ("120.00") — `MoneyIn` accepts a JSON string or number, and a string
/// avoids any float-encoding ambiguity.
struct ProductCreateRequest: Encodable {
    let name: String
    let category: String?
    let retailPrice: String
    let lastWholesalePrice: String?
}

/// Mirrors backend `ProductUpdate` — all fields optional (partial patch).
/// `name`/`retailPrice`/`isActive` reject an explicit null server-side, so
/// this is only encoded with the fields that actually changed.
struct ProductUpdateRequest: Encodable {
    var name: String?
    var category: String??
    var retailPrice: String?
    var lastWholesalePrice: String??
    var isActive: Bool?

    enum CodingKeys: String, CodingKey {
        case name, category, retailPrice, lastWholesalePrice, isActive
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(name, forKey: .name)
        try container.encodeIfPresent(retailPrice, forKey: .retailPrice)
        try container.encodeIfPresent(isActive, forKey: .isActive)
        if let category { try container.encode(category, forKey: .category) }
        if let lastWholesalePrice { try container.encode(lastWholesalePrice, forKey: .lastWholesalePrice) }
    }
}

struct ProductDeleteResponse: Decodable {
    let id: UUID
    let hardDeleted: Bool
    let detail: String
}

struct BulkPhotosResponse: Decodable {
    let detail: String
    let matched: Int
    let errors: [String]
}
