import Foundation

/// Backend `photo_url` fields are relative paths ("/media/products/xyz.jpg"),
/// served by Nginx alongside the API — not absolute URLs. Mirrors Android's
/// `resolveMediaUrl` (data/Mappers.kt): absolute/data/blob URLs pass through
/// unchanged, anything else is resolved against the API base URL. Without
/// this, every product photo silently fails to load.
enum MediaURL {
    static func resolve(_ raw: String?) -> URL? {
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") || raw.hasPrefix("data:") || raw.hasPrefix("blob:") {
            return URL(string: raw)
        }
        let base = BaseURLStore.current.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let path = raw.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return URL(string: "\(base)/\(path)")
    }
}
