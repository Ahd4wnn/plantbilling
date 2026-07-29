import SwiftUI

struct BillView: View {
    @State private var viewModel = BillingViewModel()
    @State private var showingCartReview = false
    @State private var showingQuickAdd = false
    @State private var showingHeldBills = false

    var body: some View {
        NavigationStack {
            Group {
                if case .success(let bill) = viewModel.checkoutState {
                    BillSuccessView(bill: bill) {
                        viewModel.startNewBill()
                    }
                } else {
                    browsingContent
                }
            }
            .navigationTitle("Bill")
            .background(PlantbillColor.background)
            .searchable(text: $viewModel.searchText, prompt: Text("Search products"))
            .task { await viewModel.loadProducts() }
            .notificationBell()
            .sheet(isPresented: $showingCartReview) {
                CartReviewSheet(viewModel: viewModel)
            }
            .sheet(isPresented: $showingQuickAdd) {
                QuickAddSheet(viewModel: viewModel)
            }
            .sheet(isPresented: $showingHeldBills) {
                HeldBillsSheet(viewModel: viewModel) { held in
                    viewModel.resume(held)
                    showingHeldBills = false
                    showingCartReview = true
                }
            }
        }
    }

    private var browsingContent: some View {
        VStack(spacing: 0) {
            utilityRow
            filterBar
            productGrid
        }
        .safeAreaInset(edge: .bottom) {
            if !viewModel.cartLines.isEmpty {
                reviewAndPayBar
            }
        }
    }

    private var utilityRow: some View {
        HStack(spacing: PlantbillSpacing.sm) {
            Button {
                showingQuickAdd = true
            } label: {
                Label("Quick add item", systemImage: "plus.circle.fill")
                    .font(PlantbillTypography.caption)
                    .fontWeight(.medium)
            }

            Spacer()

            if !viewModel.heldBills.isEmpty {
                Button {
                    showingHeldBills = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "tray.and.arrow.down.fill")
                        Text("Held bills")
                        Text("\(viewModel.heldBills.count)")
                            .font(PlantbillTypography.caption.bold())
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1)
                            .background(Capsule().fill(PlantbillColor.green))
                            .foregroundStyle(.white)
                    }
                    .font(PlantbillTypography.caption)
                    .fontWeight(.medium)
                }
            }
        }
        .foregroundStyle(PlantbillColor.green)
        .padding(.horizontal, PlantbillSpacing.md)
        .padding(.top, PlantbillSpacing.sm)
    }

    @ViewBuilder
    private var filterBar: some View {
        if !viewModel.categories.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: PlantbillSpacing.sm) {
                    FilterChip(title: "All", isSelected: viewModel.selectedCategory == nil) {
                        viewModel.selectedCategory = nil
                    }
                    ForEach(viewModel.categories, id: \.self) { category in
                        FilterChip(title: LocalizedStringKey(category), isSelected: viewModel.selectedCategory == category) {
                            viewModel.selectedCategory = viewModel.selectedCategory == category ? nil : category
                        }
                    }
                }
                .padding(.horizontal, PlantbillSpacing.md)
                .padding(.vertical, PlantbillSpacing.sm)
            }
        }
    }

    @ViewBuilder
    private var productGrid: some View {
        switch viewModel.productState {
        case .loading:
            LoadingStateView(message: "Loading your products…")
        case .empty:
            if viewModel.searchText.isEmpty {
                EmptyStateView(
                    icon: "leaf",
                    title: "No products",
                    message: "Add products in the Products tab, or use Quick add."
                )
            } else {
                EmptyStateView(
                    icon: "leaf",
                    title: "No products",
                    message: "No products match \"\(viewModel.searchText)\"."
                )
            }
        case .error(let message):
            ErrorStateView(message: LocalizedStringKey(message)) {
                Task { await viewModel.loadProducts() }
            }
        case .loaded(let products):
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: PlantbillSpacing.sm)], spacing: PlantbillSpacing.sm) {
                    ForEach(products) { product in
                        Button {
                            addToCartAndReview(product)
                        } label: {
                            ProductGridCell(product: product, quantityInCart: quantityInCart(for: product.id))
                        }
                    }
                }
                .padding(PlantbillSpacing.md)
                .padding(.bottom, viewModel.cartLines.isEmpty ? 0 : PlantbillSpacing.xxl)
            }
        }
    }

    private func quantityInCart(for productId: UUID) -> Int {
        viewModel.cartLines.filter { $0.productId == productId }.reduce(0) { $0 + $1.quantity }
    }

    private func addToCartAndReview(_ product: Product) {
        viewModel.addToCart(product)
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        showingCartReview = true
    }

    private var reviewAndPayBar: some View {
        VStack(spacing: 0) {
            Divider()
            Button {
                showingCartReview = true
            } label: {
                HStack {
                    Text("Review & pay")
                        .font(PlantbillTypography.button)
                    Spacer()
                    Text("\(viewModel.cartLines.reduce(0) { $0 + $1.quantity }) items · \(viewModel.total.format())")
                        .font(PlantbillTypography.button)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, PlantbillSpacing.lg)
                .frame(height: PlantbillSpacing.primaryActionHeight)
                .background(PlantbillColor.green)
                .clipShape(RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius))
                .contentShape(Rectangle())
                .padding(.horizontal, PlantbillSpacing.md)
                .padding(.vertical, PlantbillSpacing.sm)
            }
            .background(.bar)
        }
    }
}

private struct ProductGridCell: View {
    let product: Product
    let quantityInCart: Int

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
                        .fill(PlantbillColor.greenTint)
                        .frame(height: 90)
                        .overlay {
                            if let url = product.resolvedPhotoURL {
                                AsyncImage(url: url) { phase in
                                    if let image = phase.image {
                                        image.resizable().scaledToFill()
                                    } else {
                                        Image(systemName: "leaf.fill").foregroundStyle(PlantbillColor.green)
                                    }
                                }
                                .frame(height: 90)
                                .frame(maxWidth: .infinity)
                                .clipShape(RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius))
                            } else {
                                Image(systemName: "leaf.fill")
                                    .font(.system(size: 28))
                                    .foregroundStyle(PlantbillColor.green)
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .clipped()

                    if quantityInCart > 0 {
                        Text("\(quantityInCart)")
                            .font(PlantbillTypography.caption.bold())
                            .foregroundStyle(.white)
                            .padding(6)
                            .background(Circle().fill(PlantbillColor.green))
                            .padding(6)
                    }
                }

                Text(product.name)
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                    .lineLimit(1)
                Text(product.price.format())
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.green)
            }
        }
    }
}

#Preview {
    BillView()
        .environment(AuthSession())
        .environment(NotificationsStore())
}
