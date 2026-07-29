import SwiftUI

struct QuickAddSheet: View {
    let viewModel: BillingViewModel

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var priceText = ""
    @State private var quantity = 1
    @State private var isSaving = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: PlantbillSpacing.lg) {
                VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                    Text("Quick add custom item")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text("Adds a one-off item to this bill (saved under \"Quick Add\").")
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                PlantbillTextField(label: "Item name", text: $name, placeholder: "e.g. Ad-hoc plant, pot, soil")
                PlantbillTextField(label: "Price (₹)", text: $priceText, placeholder: "0", keyboardType: .numberPad)

                HStack {
                    Text("Quantity")
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Spacer()
                    Stepper(value: $quantity, in: 1...999) {
                        Text("\(quantity)")
                            .font(PlantbillTypography.bodyEmphasized)
                            .frame(minWidth: 32)
                    }
                    .fixedSize()
                }

                if let errorMessage {
                    InlineErrorText(message: LocalizedStringKey(errorMessage))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                PrimaryButton(title: "Add to bill", isLoading: isSaving, isDisabled: isSaving || !canSave, action: save)

                Spacer()
            }
            .padding(PlantbillSpacing.lg)
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func save() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            errorMessage = "Please enter an item name."
            return
        }
        guard let priceDecimal = Decimal(string: priceText, locale: Locale(identifier: "en_US_POSIX")), priceDecimal >= 0 else {
            errorMessage = "Please enter a valid price (0 or more)."
            return
        }
        errorMessage = nil

        Task {
            isSaving = true
            defer { isSaving = false }
            let success = await viewModel.quickAdd(name: trimmedName, price: Money(amount: priceDecimal), quantity: quantity)
            if success {
                dismiss()
            } else {
                errorMessage = "Couldn't add the item."
            }
        }
    }
}
