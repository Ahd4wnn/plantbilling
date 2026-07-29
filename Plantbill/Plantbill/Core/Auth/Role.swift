import Foundation
import SwiftUI

/// Matches the backend's actual wire values exactly (`app/models/user.py`).
/// Named unambiguously, unlike Android's `Role` enum where a local variable
/// named `isOwner` actually meant `Role.MANAGER` in several files — here
/// `manager` always means "the shop's day-to-day operator" and `owner`
/// always means "multi-shop overseer," no aliasing.
enum Role: String, Codable, Equatable {
    case admin
    case owner
    case manager
    case salesperson

    /// Any role value the backend might add later that this build doesn't
    /// know about yet — routes to the unsupported-role screen, same as
    /// Android's Role.UNKNOWN.
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = Role(rawValue: raw) ?? .unknown
    }

    /// Full tab-bar shell (Bill/Products/Sales/Customers/More).
    var usesMainShell: Bool { self == .manager || self == .salesperson }

    /// Separate read-only multi-shop dashboard, no bottom tabs.
    var usesOwnerShell: Bool { self == .owner }

    var displayName: LocalizedStringKey {
        switch self {
        case .admin: return "Admin"
        case .owner: return "Owner"
        case .manager: return "Manager"
        case .salesperson: return "Salesperson"
        case .unknown: return "Unknown"
        }
    }
}
