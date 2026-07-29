import Foundation

/// Every case carries a plain-language `userMessage` — the shop-facing app
/// never shows a raw error code or stack trace (CLAUDE.md: "no raw error
/// codes").
enum APIError: Error {
    /// An authenticated request was rejected — the session itself expired.
    case sessionExpired
    /// A login attempt was rejected (401 with no session ever having
    /// existed) — carries the backend's own message, which is already
    /// plain-language (e.g. "Invalid email or password").
    case invalidCredentials(String)
    case forbidden(String)
    case badRequest(String)
    case notFound
    case conflict(String)
    case server(String)
    case network
    case decoding
    case unknown

    var userMessage: String {
        switch self {
        case .sessionExpired:
            return "Your session has ended. Please sign in again."
        case .invalidCredentials(let detail):
            return detail
        case .forbidden(let detail):
            return detail
        case .badRequest(let detail):
            return detail
        case .notFound:
            return "We couldn't find that. It may have been removed."
        case .conflict(let detail):
            return detail
        case .server:
            return "Something went wrong on our end. Please try again in a moment."
        case .network:
            return "Can't reach the server. Check your internet connection and try again."
        case .decoding, .unknown:
            return "Something went wrong. Please try again."
        }
    }
}

/// Mirrors FastAPI's default error body: `{"detail": "..."}` for plain
/// HTTPExceptions, or `{"detail": [{"loc":[...], "msg":"...", "type":"..."}]}`
/// for 422 Pydantic validation failures.
struct APIErrorBody: Decodable {
    let detail: Detail

    enum Detail: Decodable {
        case message(String)
        case validationErrors([ValidationError])

        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            if let message = try? container.decode(String.self) {
                self = .message(message)
            } else if let errors = try? container.decode([ValidationError].self) {
                self = .validationErrors(errors)
            } else {
                self = .message("Unexpected error")
            }
        }
    }

    struct ValidationError: Decodable {
        let msg: String
    }

    var friendlyMessage: String {
        switch detail {
        case .message(let text):
            return text
        case .validationErrors(let errors):
            return errors.first?.msg ?? "Please check the information you entered."
        }
    }
}
