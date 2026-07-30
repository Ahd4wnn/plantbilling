import Foundation

/// A Bluetooth Low Energy peripheral discovered while scanning for a printer.
struct DiscoveredPrinter: Identifiable, Hashable {
    let id: UUID
    let name: String
    let rssi: Int
}

enum PrinterConnectionState: Equatable {
    case poweredOff
    case unauthorized
    case unsupported
    case idle
    case scanning
    case connecting
    case connected(DiscoveredPrinter)
    case ready(DiscoveredPrinter)
}

enum PrinterTransportError: LocalizedError {
    case bluetoothUnsupported
    case bluetoothOff
    case bluetoothUnauthorized
    case connectionFailed
    case notConnected
    case noWritableCharacteristic

    var errorDescription: String? {
        switch self {
        case .bluetoothUnsupported: return "This device doesn't support Bluetooth Low Energy."
        case .bluetoothOff: return "Turn on Bluetooth to connect to a printer."
        case .bluetoothUnauthorized: return "Allow Bluetooth access in Settings to connect to a printer."
        case .connectionFailed: return "Couldn't connect to the printer. Make sure it's on and nearby."
        case .notConnected: return "No printer connected."
        case .noWritableCharacteristic: return "This device didn't offer anything Plantbill can print to over Bluetooth."
        }
    }
}

/// Swappable printing transport. The live implementation (`BLEPrinterManager`)
/// is CoreBluetooth-based — a classic-Bluetooth (SPP) printer, which is what
/// the Android app uses today, cannot be reached this way. iOS has no public
/// API for SPP without MFi hardware certification, so BLE is the only route,
/// and only works if the printer model itself supports a BLE print mode. This
/// protocol exists so a different transport (e.g. a confirmed-working vendor
/// SDK, once the hardware is known) can be swapped in later without touching
/// call sites elsewhere in the app.
@MainActor
protocol PrinterTransport: AnyObject {
    var state: PrinterConnectionState { get }
    var discoveredPrinters: [DiscoveredPrinter] { get }

    func startScan()
    func stopScan()
    func connect(_ printer: DiscoveredPrinter) async throws
    func write(_ data: Data) async throws
    func disconnect()
}
