import Foundation
import Security

/// JWT storage in the iOS Keychain. The Security framework's SecItem* calls
/// are synchronous — every call here is routed through `Task.detached` so
/// it never runs on the caller's actor (almost always @MainActor, since
/// APIClient is driven from MainActor view models). Skipping that would
/// block the main thread on every single network call while it reads the
/// token, which is exactly the ANR-style bug Android hit with its Keystore
/// access before its "lazy Keystore" fix — same failure mode, different
/// platform API.
enum KeychainStore {
    private nonisolated static let service = "com.dofida.Plantbill"
    private nonisolated static let account = "jwt"

    static func saveToken(_ token: String) async {
        await Task.detached(priority: .userInitiated) { saveTokenSync(token) }.value
    }

    static func loadToken() async -> String? {
        await Task.detached(priority: .userInitiated) { loadTokenSync() }.value
    }

    static func deleteToken() async {
        await Task.detached(priority: .userInitiated) { deleteTokenSync() }.value
    }

    // Explicitly `nonisolated` — the project defaults every declaration to
    // @MainActor, which would otherwise pin these back onto the main actor
    // and defeat the whole point of running them inside `Task.detached`.
    private nonisolated static func saveTokenSync(_ token: String) {
        let data = Data(token.utf8)
        var query = baseQuery()
        query[kSecValueData as String] = data

        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let attributesToUpdate = [kSecValueData as String: data]
            SecItemUpdate(baseQuery() as CFDictionary, attributesToUpdate as CFDictionary)
        }
    }

    private nonisolated static func loadTokenSync() -> String? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private nonisolated static func deleteTokenSync() {
        SecItemDelete(baseQuery() as CFDictionary)
    }

    private nonisolated static func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
