import Foundation

/// POST /auth/login response.
struct TokenResponse: Decodable {
    let accessToken: String
    let tokenType: String
}

/// GET /auth/me response.
struct CurrentUser: Decodable, Equatable {
    let id: UUID
    let email: String
    let role: Role
    let shopId: UUID?
    let isActive: Bool
    let shopName: String?
    let businessName: String?
    let businessUpi: String?

    static func == (lhs: CurrentUser, rhs: CurrentUser) -> Bool {
        lhs.id == rhs.id && lhs.role == rhs.role
    }
}

struct LoginRequest: Encodable {
    let email: String
    let password: String
}
