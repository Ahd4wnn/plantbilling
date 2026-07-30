import Foundation

/// A minimal ESC/POS byte sequence for a visual test print — just enough to
/// prove bytes sent over a BLE write characteristic actually reach paper.
/// Full receipt rendering (the bitmap/raster approach Android uses for
/// non-Latin scripts) is deferred until a working transport is confirmed.
enum EscPosTestPrint {
    static func bytes(shopName: String) -> Data {
        var data = Data()
        data.append(contentsOf: [0x1B, 0x40]) // ESC @ — initialize printer
        let text = "\(shopName)\nPlantbill test print\nIf you can read this,\nBluetooth printing works.\n\n\n"
        data.append(text.data(using: .ascii) ?? Data())
        data.append(contentsOf: [0x1D, 0x56, 0x00]) // GS V 0 — full cut (ignored if unsupported)
        return data
    }
}
