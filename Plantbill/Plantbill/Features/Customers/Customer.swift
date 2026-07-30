import Foundation

/// Mirrors backend `CustomerOut` (app/schemas/customer.py).
struct Customer: Decodable, Identifiable, Equatable {
    let id: UUID
    let name: String
    let phone: String?
    let whatsappEligible: Bool
    let createdAt: Date
}
