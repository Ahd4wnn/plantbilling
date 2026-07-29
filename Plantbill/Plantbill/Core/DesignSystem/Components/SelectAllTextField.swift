import SwiftUI
import UIKit

/// A UITextField-backed field that selects all its existing text the
/// moment it gains focus — so typing a new value replaces the old one
/// instead of inserting into it. Plain SwiftUI `TextField` has no public
/// "select all on focus" API, which is why editing a price of "0" by
/// typing "89" produced "890" (the digits were inserted before the
/// existing "0") instead of replacing it.
struct SelectAllTextField: UIViewRepresentable {
    @Binding var text: String
    var placeholder: String = ""
    var keyboardType: UIKeyboardType = .numberPad
    var textAlignment: NSTextAlignment = .left
    var font: UIFont = .preferredFont(forTextStyle: .body)
    var onFocusChange: ((Bool) -> Void)? = nil

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.delegate = context.coordinator
        field.keyboardType = keyboardType
        field.textAlignment = textAlignment
        field.font = font
        field.placeholder = placeholder
        field.text = text
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        field.addTarget(context.coordinator, action: #selector(Coordinator.textChanged(_:)), for: .editingChanged)
        return field
    }

    func updateUIView(_ uiView: UITextField, context: Context) {
        if uiView.text != text {
            uiView.text = text
        }
        uiView.keyboardType = keyboardType
        uiView.textAlignment = textAlignment
        context.coordinator.onFocusChange = onFocusChange
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text, onFocusChange: onFocusChange)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var text: Binding<String>
        var onFocusChange: ((Bool) -> Void)?

        init(text: Binding<String>, onFocusChange: ((Bool) -> Void)?) {
            self.text = text
            self.onFocusChange = onFocusChange
        }

        @objc func textChanged(_ sender: UITextField) {
            text.wrappedValue = sender.text ?? ""
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            onFocusChange?(true)
            DispatchQueue.main.async {
                textField.selectAll(nil)
            }
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            onFocusChange?(false)
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            return true
        }
    }
}
