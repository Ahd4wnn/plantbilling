import Foundation

/// Overridable API base URL, mirroring Android's AppPreferences/BaseUrlProvider
/// pattern. Exposed later in Settings; defaults to production.
enum BaseURLStore {
    private static let key = "base_url"
    static let defaultURL = "https://api.plantbill.in/"

    static var current: URL {
        let stored = UserDefaults.standard.string(forKey: key) ?? defaultURL
        return URL(string: stored) ?? URL(string: defaultURL)!
    }

    static func set(_ urlString: String) {
        var trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { trimmed = defaultURL }
        if !trimmed.hasSuffix("/") { trimmed += "/" }
        UserDefaults.standard.set(trimmed, forKey: key)
    }
}
