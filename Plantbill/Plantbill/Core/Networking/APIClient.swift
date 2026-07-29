import Foundation

extension Notification.Name {
    /// Posted on any 401 response — mirrors Android's UnauthorizedInterceptor
    /// forcing a global logout regardless of which screen triggered it.
    static let apiUnauthorized = Notification.Name("APIUnauthorized")
}

final class APIClient {
    static let shared = APIClient()

    private let session: URLSession = .shared

    private lazy var decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let raw = try container.decode(String.self)
            if let date = ISO8601DateFormatter.withFractionalSeconds.date(from: raw) {
                return date
            }
            if let date = ISO8601DateFormatter.standard.date(from: raw) {
                return date
            }
            throw DecodingError.dataCorruptedError(
                in: container, debugDescription: "Unrecognized date format: \(raw)"
            )
        }
        return decoder
    }()

    private lazy var encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        return encoder
    }()

    func encode<T: Encodable>(_ value: T) throws -> Data {
        try encoder.encode(value)
    }

    func send<T: Decodable>(_ endpoint: Endpoint) async throws -> T {
        let data = try await perform(endpoint)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }

    func sendNoContent(_ endpoint: Endpoint) async throws {
        _ = try await perform(endpoint)
    }

    /// Raw bytes back (e.g. the products sample CSV or a report download) —
    /// no JSON decoding.
    func download(_ endpoint: Endpoint) async throws -> Data {
        try await perform(endpoint)
    }

    /// Multipart/form-data upload (product photos, bulk CSV/XLSX import,
    /// bulk photo ZIP) — shares the same auth/error handling as JSON
    /// requests, just a different body/content-type.
    func upload<T: Decodable>(
        path: String,
        method: HTTPMethod = .post,
        formFields: [String: String] = [:],
        file: MultipartFile
    ) async throws -> T {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = try await buildRequest(Endpoint(path: path, method: method))
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = MultipartFormData.build(boundary: boundary, fields: formFields, file: file)

        let data = try await execute(request, requiresAuth: true)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }

    @discardableResult
    private func perform(_ endpoint: Endpoint) async throws -> Data {
        var request = try await buildRequest(endpoint)
        if let body = endpoint.body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        return try await execute(request, requiresAuth: endpoint.requiresAuth)
    }

    private func buildRequest(_ endpoint: Endpoint) async throws -> URLRequest {
        guard var components = URLComponents(
            url: BaseURLStore.current.appendingPathComponent(endpoint.path),
            resolvingAgainstBaseURL: false
        ) else {
            throw APIError.unknown
        }
        if !endpoint.queryItems.isEmpty {
            components.queryItems = endpoint.queryItems
        }
        guard let url = components.url else { throw APIError.unknown }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if endpoint.requiresAuth, let token = await KeychainStore.loadToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private func execute(_ request: URLRequest, requiresAuth: Bool) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.network
        }

        guard let http = response as? HTTPURLResponse else { throw APIError.unknown }

        switch http.statusCode {
        case 200...299:
            return data
        case 401:
            // Only an authenticated request being rejected means the session
            // itself expired — a bad-credentials login attempt (requiresAuth
            // == false) never had a session to end, so it must not trigger
            // the global forced-logout notification, and the backend's own
            // message ("Invalid email or password") is already the right
            // thing to show, not a generic session-expiry notice.
            if requiresAuth {
                NotificationCenter.default.post(name: .apiUnauthorized, object: nil)
                throw APIError.sessionExpired
            }
            throw APIError.invalidCredentials(friendlyMessage(from: data))
        case 403:
            throw APIError.forbidden(friendlyMessage(from: data))
        case 404:
            throw APIError.notFound
        case 409:
            throw APIError.conflict(friendlyMessage(from: data))
        case 400, 422:
            throw APIError.badRequest(friendlyMessage(from: data))
        case 413:
            throw APIError.badRequest("That file is too large.")
        case 500...599:
            throw APIError.server(friendlyMessage(from: data))
        default:
            throw APIError.unknown
        }
    }

    private func friendlyMessage(from data: Data) -> String {
        (try? decoder.decode(APIErrorBody.self, from: data))?.friendlyMessage
            ?? "Please check the information you entered."
    }
}
