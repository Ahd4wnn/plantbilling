import Foundation

/// Day boundaries and display formatting follow the shop timezone
/// (Asia/Kolkata), matching the backend — mirrors Android's `DateTimeFmt.kt`.
enum ShopCalendar {
    static let timeZone = TimeZone(identifier: "Asia/Kolkata")!

    static var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        return cal
    }

    static func today() -> Date {
        calendar.startOfDay(for: Date())
    }

    static func isToday(_ date: Date) -> Bool {
        calendar.isDate(date, inSameDayAs: Date())
    }

    /// Whole days between a date and today (shop zone), never negative.
    static func daysSince(_ date: Date) -> Int {
        let start = calendar.startOfDay(for: date)
        let end = calendar.startOfDay(for: Date())
        return max(0, calendar.dateComponents([.day], from: start, to: end).day ?? 0)
    }

    /// Query-param date, e.g. "2026-07-30".
    static func apiDateString(_ date: Date) -> String {
        apiDateFormatter.string(from: date)
    }

    /// Header display, e.g. "Thu, 30 Jul 2026".
    static func displayDateString(_ date: Date) -> String {
        displayDateFormatter.string(from: date)
    }

    /// Bill-row timestamp, e.g. "30 Jul, 2:30 PM".
    static func billTime(_ date: Date) -> String {
        timeFormatter.string(from: date)
    }

    private static let apiDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = timeZone
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    private static let displayDateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "EEE, d MMM yyyy"
        f.timeZone = timeZone
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "d MMM, h:mm a"
        f.timeZone = timeZone
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()
}
