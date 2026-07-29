import Foundation

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case patch = "PATCH"
    case delete = "DELETE"
}

/// Backend paths are flat — no `/api/v1` prefix, e.g. `auth/login`,
/// `products`, `bills/summary/today`.
struct Endpoint {
    var path: String
    var method: HTTPMethod = .get
    var queryItems: [URLQueryItem] = []
    var body: Data? = nil
    var requiresAuth: Bool = true
}
