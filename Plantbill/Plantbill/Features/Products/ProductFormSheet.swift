import SwiftUI
import PhotosUI

struct ProductFormSheet: View {
    /// nil = create mode.
    let product: Product?
    let viewModel: ProductsViewModel

    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var category: String
    @State private var priceText: String
    @State private var isActive: Bool

    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var pendingPhotoData: Data?
    @State private var currentPhotoUrl: String?
    @State private var isSaving = false
    @State private var isRemovingPhoto = false
    @State private var isDeleting = false
    @State private var validationMessage: String?
    @State private var showingDeleteConfirm = false

    init(product: Product?, viewModel: ProductsViewModel) {
        self.product = product
        self.viewModel = viewModel
        _name = State(initialValue: product?.name ?? "")
        _category = State(initialValue: product?.category ?? "")
        _priceText = State(initialValue: product.map { $0.price.toInput() } ?? "")
        _isActive = State(initialValue: product?.isActive ?? true)
        _currentPhotoUrl = State(initialValue: product?.photoUrl)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: PlantbillSpacing.lg) {
                    photoPicker

                    VStack(spacing: PlantbillSpacing.md) {
                        PlantbillTextField(label: "Name", text: $name, placeholder: "e.g. Money Plant")
                        PlantbillTextField(label: "Category (optional)", text: $category, placeholder: "e.g. Indoor")
                        PlantbillTextField(label: "Price (₹)", text: $priceText, placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)

                        if product != nil {
                            Toggle(isOn: $isActive) {
                                Text("Active (shown in billing)")
                                    .font(PlantbillTypography.body)
                                    .fontWeight(.medium)
                                    .foregroundStyle(PlantbillColor.textPrimary)
                            }
                            .tint(PlantbillColor.green)
                            .frame(minHeight: PlantbillSpacing.minTouchTarget)
                        }

                        if let validationMessage {
                            InlineErrorText(message: LocalizedStringKey(validationMessage))
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        if let mutationError = viewModel.mutationError {
                            InlineErrorText(message: LocalizedStringKey(mutationError))
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    PrimaryButton(title: "Save", isLoading: isSaving, isDisabled: isSaving, action: save)

                    if product != nil {
                        SecondaryButton(
                            title: "Delete product",
                            tint: PlantbillColor.error,
                            isLoading: isDeleting,
                            isDisabled: isDeleting
                        ) {
                            showingDeleteConfirm = true
                        }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationTitle(product == nil ? "New product" : "Edit product")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
            .confirmationDialog(
                "Delete product?",
                isPresented: $showingDeleteConfirm,
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) { deleteProduct() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Remove it from your catalog? Past bills are unaffected.")
            }
        }
    }

    private var photoPicker: some View {
        VStack(spacing: PlantbillSpacing.sm) {
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                ZStack {
                    RoundedRectangle(cornerRadius: PlantbillSpacing.cardCornerRadius)
                        .fill(PlantbillColor.greenTint)
                        .frame(width: 100, height: 100)
                    if let pendingPhotoData, let uiImage = UIImage(data: pendingPhotoData) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 100, height: 100)
                            .clipShape(RoundedRectangle(cornerRadius: PlantbillSpacing.cardCornerRadius))
                    } else if let currentPhotoUrl, let url = MediaURL.resolve(currentPhotoUrl) {
                        AsyncImage(url: url) { phase in
                            if let image = phase.image {
                                image.resizable().scaledToFill()
                            } else {
                                Image(systemName: "camera.fill").foregroundStyle(PlantbillColor.green)
                            }
                        }
                        .frame(width: 100, height: 100)
                        .clipShape(RoundedRectangle(cornerRadius: PlantbillSpacing.cardCornerRadius))
                    } else {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(PlantbillColor.green)
                    }
                }
            }
            .onChange(of: selectedPhotoItem) { _, newItem in
                Task {
                    if let data = try? await newItem?.loadTransferable(type: Data.self) {
                        pendingPhotoData = data
                    }
                }
            }

            if currentPhotoUrl != nil || pendingPhotoData != nil {
                Button("Remove photo", role: .destructive) {
                    pendingPhotoData = nil
                    selectedPhotoItem = nil
                    if product != nil { removePhoto() }
                    currentPhotoUrl = nil
                }
                .font(PlantbillTypography.caption)
                .disabled(isRemovingPhoto)
            }
        }
    }

    private func save() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            validationMessage = "Please enter a product name."
            return
        }
        guard let priceDecimal = Decimal(string: priceText, locale: Locale(identifier: "en_US_POSIX")), priceDecimal >= 0 else {
            validationMessage = "Please enter a valid price (0 or more)."
            return
        }
        validationMessage = nil
        let price = Money(amount: priceDecimal)

        Task {
            isSaving = true
            defer { isSaving = false }

            let savedProduct: Product?
            if let product {
                savedProduct = await viewModel.update(id: product.id, name: trimmedName, category: category, price: price, isActive: isActive)
            } else {
                savedProduct = await viewModel.create(name: trimmedName, category: category, price: price)
            }

            guard let savedProduct else { return }

            if let pendingPhotoData {
                _ = await viewModel.uploadImage(id: savedProduct.id, data: pendingPhotoData, mimeType: "image/jpeg")
            }
            dismiss()
        }
    }

    private func removePhoto() {
        guard let product else { return }
        Task {
            isRemovingPhoto = true
            defer { isRemovingPhoto = false }
            _ = await viewModel.deleteImage(id: product.id)
        }
    }

    private func deleteProduct() {
        guard let product else { return }
        Task {
            isDeleting = true
            defer { isDeleting = false }
            let success = await viewModel.delete(id: product.id)
            if success { dismiss() }
        }
    }
}
