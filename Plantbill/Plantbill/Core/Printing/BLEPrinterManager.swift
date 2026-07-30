import Foundation
import CoreBluetooth
import Observation

/// CoreBluetooth-based `PrinterTransport`. This is a hardware-discovery tool
/// as much as a printing path: since it's unconfirmed whether the shop's
/// actual printer model supports Bluetooth Low Energy at all (see
/// `PrinterTransport`'s doc comment), scanning here is intentionally
/// unfiltered by service UUID — thermal printers don't share one standard
/// BLE print profile the way classic-Bluetooth printers share SPP, so the
/// only reliable way to find out is to show everything nearby and let the
/// shop try connecting to whatever looks like their printer by name.
@Observable
@MainActor
final class BLEPrinterManager: NSObject, PrinterTransport {
    private(set) var state: PrinterConnectionState = .idle
    private(set) var discoveredPrinters: [DiscoveredPrinter] = []

    @ObservationIgnored private var central: CBCentralManager!
    @ObservationIgnored private var peripherals: [UUID: CBPeripheral] = [:]
    @ObservationIgnored private var connectedPeripheral: CBPeripheral?
    @ObservationIgnored private var writableCharacteristic: CBCharacteristic?

    @ObservationIgnored private var connectContinuation: CheckedContinuation<Void, Error>?
    @ObservationIgnored private var writeContinuation: CheckedContinuation<Void, Error>?
    @ObservationIgnored private var serviceDiscoveryContinuation: CheckedContinuation<Void, Error>?
    @ObservationIgnored private var pendingServiceCount = 0

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: nil)
    }

    func startScan() {
        discoveredPrinters = []
        peripherals = [:]
        guard central.state == .poweredOn else { return }
        state = .scanning
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func stopScan() {
        central.stopScan()
        if case .scanning = state { state = .idle }
    }

    func connect(_ printer: DiscoveredPrinter) async throws {
        stopScan()
        guard central.state == .poweredOn else { throw PrinterTransportError.bluetoothOff }
        guard let peripheral = peripherals[printer.id] else { throw PrinterTransportError.connectionFailed }

        state = .connecting
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connectContinuation = continuation
            central.connect(peripheral, options: nil)
        }
        connectedPeripheral = peripheral
        peripheral.delegate = self
        state = .connected(printer)

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            serviceDiscoveryContinuation = continuation
            peripheral.discoverServices(nil)
        }
        guard writableCharacteristic != nil else { throw PrinterTransportError.noWritableCharacteristic }
        state = .ready(printer)
    }

    func write(_ data: Data) async throws {
        guard let peripheral = connectedPeripheral, let characteristic = writableCharacteristic else {
            throw PrinterTransportError.notConnected
        }
        let withResponse = characteristic.properties.contains(.write)
        let chunkSize = 180
        var offset = 0
        while offset < data.count {
            let end = min(offset + chunkSize, data.count)
            let chunk = data.subdata(in: offset..<end)
            if withResponse {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    writeContinuation = continuation
                    peripheral.writeValue(chunk, for: characteristic, type: .withResponse)
                }
            } else {
                peripheral.writeValue(chunk, for: characteristic, type: .withoutResponse)
                try? await Task.sleep(for: .milliseconds(20))
            }
            offset = end
        }
    }

    func disconnect() {
        if let peripheral = connectedPeripheral {
            central.cancelPeripheralConnection(peripheral)
        }
        connectedPeripheral = nil
        writableCharacteristic = nil
        state = .idle
    }
}

extension BLEPrinterManager: CBCentralManagerDelegate {
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            switch central.state {
            case .poweredOn:
                if state == .poweredOff || state == .unauthorized || state == .unsupported { state = .idle }
            case .poweredOff:
                state = .poweredOff
            case .unauthorized:
                state = .unauthorized
            case .unsupported:
                state = .unsupported
            default:
                break
            }
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        Task { @MainActor in
            guard !RSSI.isEqual(to: 127) else { return } // 127 == RSSI not available
            let name = peripheral.name ?? (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? "Unnamed device"
            peripherals[peripheral.identifier] = peripheral
            let entry = DiscoveredPrinter(id: peripheral.identifier, name: name, rssi: RSSI.intValue)
            if let index = discoveredPrinters.firstIndex(where: { $0.id == entry.id }) {
                discoveredPrinters[index] = entry
            } else {
                discoveredPrinters.append(entry)
            }
            discoveredPrinters.sort { $0.rssi > $1.rssi }
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        Task { @MainActor in
            connectContinuation?.resume()
            connectContinuation = nil
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            state = .idle
            connectContinuation?.resume(throwing: PrinterTransportError.connectionFailed)
            connectContinuation = nil
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            if connectedPeripheral?.identifier == peripheral.identifier {
                connectedPeripheral = nil
                writableCharacteristic = nil
                state = .idle
            }
        }
    }
}

extension BLEPrinterManager: CBPeripheralDelegate {
    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        Task { @MainActor in
            guard error == nil, let services = peripheral.services, !services.isEmpty else {
                serviceDiscoveryContinuation?.resume(throwing: PrinterTransportError.noWritableCharacteristic)
                serviceDiscoveryContinuation = nil
                return
            }
            pendingServiceCount = services.count
            for service in services {
                peripheral.discoverCharacteristics(nil, for: service)
            }
        }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        Task { @MainActor in
            if writableCharacteristic == nil, let characteristics = service.characteristics {
                writableCharacteristic = characteristics.first {
                    $0.properties.contains(.write) || $0.properties.contains(.writeWithoutResponse)
                }
            }
            pendingServiceCount -= 1
            if pendingServiceCount <= 0 {
                serviceDiscoveryContinuation?.resume()
                serviceDiscoveryContinuation = nil
            }
        }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        Task { @MainActor in
            if let error {
                writeContinuation?.resume(throwing: error)
            } else {
                writeContinuation?.resume()
            }
            writeContinuation = nil
        }
    }
}
