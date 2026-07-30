import SwiftUI

struct BillEditView: View {
    let billId: UUID
    let onSaved: () -> Void

    @State private var viewModel: BillEditViewModel
    @Environment(\.dismiss) private var dismiss

    init(billId: UUID, onSaved: @escaping () -> Void) {
        self.billId = billId
        self.onSaved = onSaved
        _viewModel = State(initialValue: BillEditViewModel(billId: billId))
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Edit bill")
                .navigationBarTitleDisplayMode(.inline)
                .background(PlantbillColor.background)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Cancel") { dismiss() }
                    }
                }
                .task { await viewModel.load() }
                .onChange(of: viewModel.saved) { _, saved in
                    if saved { onSaved() }
                }
                .sheet(isPresented: Binding(get: { viewModel.showingAddPicker }, set: { viewModel.showingAddPicker = $0 })) {
                    ProductPickerSheet(products: viewModel.products) { viewModel.addProduct($0) }
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading bill…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else {
            EditBody(viewModel: viewModel)
        }
    }
}

private struct EditBody: View {
    @Bindable var viewModel: BillEditViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                Text("Items")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)

                ForEach(viewModel.lines) { line in
                    EditLineRow(
                        line: line,
                        onQuantityChange: { viewModel.setQuantity(lineId: line.id, quantity: $0) },
                        onPriceChange: { viewModel.setUnitPrice(lineId: line.id, price: $0) },
                        onRemove: { viewModel.removeLine(lineId: line.id) }
                    )
                    Divider()
                }

                SecondaryButton(title: "Add another plant") { viewModel.showingAddPicker = true }

                Text("Discount")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                HStack(spacing: PlantbillSpacing.sm) {
                    FilterChip(title: "₹ Flat", isSelected: viewModel.discountType == .flat) { viewModel.discountType = .flat }
                    FilterChip(title: "% Percent", isSelected: viewModel.discountType == .percent) { viewModel.discountType = .percent }
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

                Text("Payment")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                HStack(spacing: PlantbillSpacing.sm) {
                    FilterChip(title: "Cash", isSelected: viewModel.paymentMode == .cash) { viewModel.paymentMode = .cash }
                    FilterChip(title: "UPI", isSelected: viewModel.paymentMode == .upi) { viewModel.paymentMode = .upi }
                    FilterChip(title: "Split", isSelected: viewModel.paymentMode == .split) { viewModel.paymentMode = .split }
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
                PlantbillTextField(label: "Due (owed later, optional)", text: $viewModel.dueAmountText, placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)

                PlantbillTextField(label: "Remarks (optional)", text: $viewModel.remarks, placeholder: "")

                if let saveError = viewModel.saveError {
                    InlineErrorText(message: LocalizedStringKey(saveError))
                }

                PrimaryButton(
                    title: "Save changes • \(viewModel.total.format())",
                    isLoading: viewModel.isSaving,
                    isDisabled: viewModel.isEmpty || viewModel.isSaving
                ) {
                    Task { await viewModel.save() }
                }
            }
            .padding(PlantbillSpacing.lg)
        }
    }
}

private struct EditLineRow: View {
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
        VStack(spacing: PlantbillSpacing.sm) {
            HStack {
                Text(line.productName)
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Spacer()
                Text(line.lineTotal.format())
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Button(action: onRemove) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(PlantbillColor.error)
                        .frame(width: PlantbillSpacing.minTouchTarget, height: PlantbillSpacing.minTouchTarget)
                        .contentShape(Rectangle())
                }
            }
            HStack(spacing: PlantbillSpacing.md) {
                boxedField(label: "Price", text: $priceText, width: 76) { onPriceChange(Money.parse($0)) }
                boxedField(label: "Quantity", text: $quantityText, width: 60) { newValue in
                    if let qty = Int(newValue), qty > 0 { onQuantityChange(qty) }
                }
                Spacer()
            }
        }
    }

    @ViewBuilder
    private func boxedField(label: LocalizedStringKey, text: Binding<String>, width: CGFloat, onChange: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)
            SelectAllTextField(text: text, placeholder: "0", keyboardType: .numberPad, textAlignment: .center)
                .frame(width: width, height: PlantbillSpacing.minTouchTarget)
                .background(RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius).fill(PlantbillColor.background))
                .overlay(RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius).stroke(PlantbillColor.border, lineWidth: 1))
                .onChange(of: text.wrappedValue) { _, newValue in onChange(newValue) }
        }
    }
}

private struct ProductPickerSheet: View {
    let products: [Product]
    let onPick: (Product) -> Void

    @State private var query = ""
    @Environment(\.dismiss) private var dismiss

    private var filtered: [Product] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return products }
        return products.filter { $0.name.localizedCaseInsensitiveContains(trimmed) }
    }

    var body: some View {
        NavigationStack {
            List(filtered) { product in
                Button {
                    onPick(product)
                    dismiss()
                } label: {
                    HStack {
                        Text(product.name)
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Spacer()
                        Text(product.price.format())
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.green)
                    }
                }
            }
            .listStyle(.plain)
            .searchable(text: $query, prompt: Text("Search products"))
            .navigationTitle("Add a plant")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
