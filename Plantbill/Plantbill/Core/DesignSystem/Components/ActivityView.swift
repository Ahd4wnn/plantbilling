import SwiftUI

/// Thin wrapper around UIActivityViewController — used to present the
/// system share sheet for a file that was fetched asynchronously (so it
/// can't just be a `ShareLink`, which needs its item ready at render time).
struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
