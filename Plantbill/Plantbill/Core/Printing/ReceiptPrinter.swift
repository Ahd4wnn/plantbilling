import SwiftUI
import UIKit

/// Prints via AirPrint — the standard iOS print system. The printer just
/// needs to be AirPrint-capable and on the same Wi-Fi network; no pairing,
/// no vendor app, no extra hardware. The system print sheet itself handles
/// discovering and selecting the printer.
enum ReceiptPrinter {
    @MainActor
    static func print(_ data: ReceiptPrintData) {
        let renderer = ImageRenderer(content: ReceiptPrintView(data: data))
        renderer.scale = 3
        guard let image = renderer.uiImage else { return }

        let info = UIPrintInfo(dictionary: nil)
        info.outputType = .photo
        info.jobName = "Receipt"

        let controller = UIPrintInteractionController.shared
        controller.printInfo = info
        controller.printingItem = image
        controller.present(animated: true, completionHandler: nil)
    }
}
