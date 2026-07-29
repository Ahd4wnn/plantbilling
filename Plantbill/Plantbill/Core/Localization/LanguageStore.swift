import Foundation
import Observation

@Observable
@MainActor
final class LanguageStore {
    private static let key = "app_language"

    private(set) var current: AppLanguage

    init() {
        if let raw = UserDefaults.standard.string(forKey: Self.key), let language = AppLanguage(rawValue: raw) {
            current = language
        } else {
            current = .en
        }
    }

    func set(_ language: AppLanguage) {
        current = language
        UserDefaults.standard.set(language.rawValue, forKey: Self.key)
    }
}
