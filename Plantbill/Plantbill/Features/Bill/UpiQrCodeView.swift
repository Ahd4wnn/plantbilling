import SwiftUI
import CoreImage.CIFilterBuiltins

/// Offline-generated UPI QR (CoreImage's built-in QR filter — no network
/// call, matching Android's on-device ZXing generation) from a `upi://pay`
/// deep link. Debounced against rapid amount typing (e.g. split-payment
/// cash/UPI fields), same 180ms as Android.
struct UpiQrCodeView: View {
    let payeeVpa: String
    let payeeName: String
    let amount: Money

    @State private var qrImage: UIImage?
    @State private var debounceTask: Task<Void, Never>?

    var body: some View {
        Group {
            if let qrImage {
                Image(uiImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
            } else {
                ProgressView()
            }
        }
        .frame(width: 180, height: 180)
        .padding(PlantbillSpacing.sm)
        .background(
            RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                .fill(Color.white)
        )
        .task(id: "\(payeeVpa)-\(amount.toWire())") {
            debounceTask?.cancel()
            debounceTask = Task {
                try? await Task.sleep(for: .milliseconds(180))
                guard !Task.isCancelled else { return }
                qrImage = Self.render(uri: Self.upiUri(payeeVpa: payeeVpa, payeeName: payeeName, amount: amount))
            }
            await debounceTask?.value
        }
    }

    private static func upiUri(payeeVpa: String, payeeName: String, amount: Money) -> String {
        func encode(_ value: String) -> String {
            value.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) ?? value
        }
        return "upi://pay?pa=\(encode(payeeVpa))&pn=\(encode(payeeName))&am=\(encode(amount.toWire()))&cu=INR"
    }

    private static func render(uri: String) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(uri.utf8)
        filter.correctionLevel = "M"

        guard let outputImage = filter.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

private extension CharacterSet {
    static let urlQueryValueAllowed: CharacterSet = {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return allowed
    }()
}
