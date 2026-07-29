import Foundation

/// In-app language override (Settings → Language), independent of the
/// system language — mirrors Android's LocaleManager. Native names are
/// always shown in their own script regardless of the current UI language,
/// same as Android's lang_en/lang_ml/... resources.
enum AppLanguage: String, CaseIterable, Identifiable {
    case en
    case ml
    // hi/ta/kn are drafted on Android but not yet verified — added here
    // once their translations are ported.

    var id: String { rawValue }

    var nativeName: String {
        switch self {
        case .en: return "English"
        case .ml: return "മലയാളം"
        }
    }

    var locale: Locale { Locale(identifier: rawValue) }
}
