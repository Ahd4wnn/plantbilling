import SwiftUI

struct CartReviewSheet: View {
    @Bindable var viewModel: BillingViewModel

    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedField: Field?

    private enum Field { case name, phone, remarks }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: PlantbillSpacing.lg) {
                    linesSection
                    discountSection
                    upiQrSection
                    paymentSection
                    customerSection
                    remarksSection

                    if case .error(let message) = viewModel.checkoutState {
                        InlineErrorText(message: LocalizedStringKey(message))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    actionButtons
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationTitle("Review bill")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close review") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Clear cart", role: .destructive) {
                        viewModel.clearCart()
                        dismiss()
                    }
                    .font(PlantbillTypography.caption)
                }
            }
            .onChange(of: viewModel.checkoutState) { _, newState in
                if case .success = newState { dismiss() }
            }
        }
    }

    // MARK: Lines

    private var linesSection: some View {
        VStack(spacing: PlantbillSpacing.sm) {
            ForEach(viewModel.cartLines) { line in
                CartLineRow(
                    line: line,
                    onQuantityChange: { viewModel.updateQuantity(lineId: line.id, quantity: $0) },
                    onPriceChange: { viewModel.updatePrice(lineId: line.id, price: $0) },
                    onRemove: { viewModel.removeLine(lineId: line.id) }
                )
            }

            SecondaryButton(title: "Add another plant") { dismiss() }

            HStack {
                Text("Subtotal")
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
                Spacer()
                Text(viewModel.subtotal.format())
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
            }
        }
    }

    // MARK: Discount

    private var discountSection: some View {
        VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
            Text("Discount")
                .font(PlantbillTypography.bodyEmphasized)
                .foregroundStyle(PlantbillColor.textPrimary)

            HStack(spacing: PlantbillSpacing.sm) {
                FilterChip(title: "₹ Flat", isSelected: viewModel.discountType == .flat) {
                    viewModel.discountType = .flat
                }
                FilterChip(title: "% Percent", isSelected: viewModel.discountType == .percent) {
                    viewModel.discountType = .percent
                }
            }

            PlantbillTextField(
                label: viewModel.discountType == .flat ? "Amount" : "Percent",
                text: $viewModel.discountValueText,
                placeholder: "0",
                keyboardType: .numberPad,
                selectAllOnFocus: true
            )

            HStack {
                Text("Total")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Spacer()
                Text(viewModel.total.format())
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.green)
            }
            .padding(.top, PlantbillSpacing.xs)
        }
    }

    // MARK: UPI QR (informational — shows the customer what they'd scan)

    @ViewBuilder
    private var upiQrSection: some View {
        if viewModel.paymentMode != .cash, viewModel.upiAmount.isPositive, let upi = businessUpi, !upi.isEmpty {
            VStack(spacing: PlantbillSpacing.sm) {
                UpiQrCodeView(payeeVpa: upi, payeeName: "Plantbill", amount: viewModel.upiAmount)
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: Payment

    private var paymentSection: some View {
        VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
            Text("Payment")
                .font(PlantbillTypography.bodyEmphasized)
                .foregroundStyle(PlantbillColor.textPrimary)

            HStack(spacing: PlantbillSpacing.sm) {
                FilterChip(title: "Cash", isSelected: viewModel.paymentMode == .cash) {
                    viewModel.paymentMode = .cash
                }
                FilterChip(title: "UPI", isSelected: viewModel.paymentMode == .upi) {
                    viewModel.paymentMode = .upi
                }
                FilterChip(title: "Split", isSelected: viewModel.paymentMode == .split) {
                    viewModel.paymentMode = .split
                }
            }

            if viewModel.paymentMode == .split {
                PlantbillTextField(label: "Cash part", text: $viewModel.cashPartText, placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)

                HStack {
                    Text("UPI part")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                    Spacer()
                    Text(viewModel.upiAmount.format())
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
            }

            PlantbillTextField(
                label: "Due (owed later, optional)",
                text: $viewModel.dueAmountText,
                placeholder: "0",
                keyboardType: .numberPad,
                selectAllOnFocus: true
            )
        }
    }

    // MARK: Customer

    private var customerSection: some View {
        VStack(spacing: PlantbillSpacing.sm) {
            PlantbillTextField(
                label: viewModel.requiresCustomerPhone ? "Customer (required for due)" : "Customer (optional)",
                text: $viewModel.customerName,
                placeholder: "Customer name"
            )
            .focused($focusedField, equals: .name)

            PlantbillTextField(
                label: viewModel.requiresCustomerPhone ? "Phone (required — money owed)" : "Phone (for receipts)",
                text: $viewModel.customerPhone,
                placeholder: "10-digit phone",
                keyboardType: .phonePad
            )
            .focused($focusedField, equals: .phone)
        }
    }

    private var remarksSection: some View {
        PlantbillTextField(label: "Remarks (optional)", text: $viewModel.remarks, placeholder: "")
            .focused($focusedField, equals: .remarks)
    }

    // MARK: Actions

    private var actionButtons: some View {
        VStack(spacing: PlantbillSpacing.sm) {
            PrimaryButton(
                title: "Save bill • \(viewModel.total.format())",
                isLoading: viewModel.checkoutState == .submitting,
                isDisabled: viewModel.checkoutState == .submitting
            ) {
                Task { await viewModel.checkout() }
            }

            SecondaryButton(title: "Hold bill — serve another customer") {
                viewModel.holdCurrentBill()
                dismiss()
            }
        }
    }

    private var businessUpi: String? {
        // Populated from the signed-in user's shop profile.
        BusinessProfile.shared.upi
    }
}

private struct CartLineRow: View {
    let line: CartLine
    let onQuantityChange: (Int) -> Void
    let onPriceChange: (Money) -> Void
    let onRemove: () -> Void

    @State private var priceText: String
    @State private var quantityText: String

    init(line: CartLine, onQuantityChange: @escaping (Int) -> Void, onPriceChange: @escaping (Money) -> Void, onRemove: @escaping () -> Void) {
        self.line = line
        self.onQuantityChange = onQuantityChange
        self.onPriceChange = onPriceChange
        self.onRemove = onRemove
        _priceText = State(initialValue: line.unitPrice.toInput())
        _quantityText = State(initialValue: "\(line.quantity)")
    }

    var body: some View {
        PlantbillCard {
            VStack(spacing: PlantbillSpacing.sm) {
                HStack {
                    Text(line.productName)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Spacer()
                    Button {
                        onRemove()
                    } label: {
                        Image(systemName: "trash")
                            .foregroundStyle(PlantbillColor.error)
                            .frame(width: PlantbillSpacing.minTouchTarget, height: PlantbillSpacing.minTouchTarget)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("Remove \(line.productName)")
                }

                HStack(spacing: PlantbillSpacing.md) {
                    boxedField(label: "Price", text: $priceText, width: 76) { newValue in
                        onPriceChange(Money.parse(newValue))
                    }

                    Spacer()

                    boxedField(label: "Quantity", text: $quantityText, width: 60) { newValue in
                        if let qty = Int(newValue), qty > 0 {
                            onQuantityChange(qty)
                        }
                    }
                }

                HStack {
                    Spacer()
                    Text(line.lineTotal.format())
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
            }
        }
    }

    /// A small bordered box — same idea as `PlantbillTextField` but compact,
    /// so price/quantity clearly read as editable text boxes rather than
    /// plain inline numbers.
    @ViewBuilder
    private func boxedField(label: LocalizedStringKey, text: Binding<String>, width: CGFloat, onChange: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)
            SelectAllTextField(text: text, placeholder: "0", keyboardType: .numberPad, textAlignment: .center)
                .frame(width: width, height: PlantbillSpacing.minTouchTarget)
                .background(
                    RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                        .fill(PlantbillColor.background)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                        .stroke(PlantbillColor.border, lineWidth: 1)
                )
                .onChange(of: text.wrappedValue) { _, newValue in
                    onChange(newValue)
                }
        }
    }
}
