import SwiftUI

struct ProductsListView: View {
    let canManage: Bool

    @State private var viewModel = ProductsViewModel()
    @State private var showingAddSheet = false
    @State private var editingProduct: Product?
    @State private var showingBulkImport = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                filterBar
                content
            }
            .navigationTitle("Products")
            .background(PlantbillColor.background)
            .searchable(text: $viewModel.searchText, prompt: Text("Search products"))
            .task { await viewModel.load() }
            .notificationBell()
            .safeAreaInset(edge: .bottom) {
                if canManage {
                    bottomActionBar
                }
            }
            .sheet(isPresented: $showingAddSheet) {
                ProductFormSheet(product: nil, viewModel: viewModel)
            }
            .sheet(item: $editingProduct) { product in
                ProductFormSheet(product: product, viewModel: viewModel)
            }
            .sheet(isPresented: $showingBulkImport) {
                BulkImportSheet(viewModel: viewModel)
            }
        }
    }

    @ViewBuilder
    private var filterBar: some View {
        if !viewModel.categories.isEmpty || canManage {
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
                    if canManage {
                        FilterChip(title: "Include inactive", isSelected: viewModel.includeInactive) {
                            viewModel.includeInactive.toggle()
                        }
                    }
                }
                .padding(.horizontal, PlantbillSpacing.md)
                .padding(.vertical, PlantbillSpacing.sm)
            }
        }
    }

    private var bottomActionBar: some View {
        HStack(spacing: PlantbillSpacing.sm) {
            SecondaryButton(title: "Bulk import") { showingBulkImport = true }
            PrimaryButton(title: "Add product") { showingAddSheet = true }
        }
        .padding(.horizontal, PlantbillSpacing.md)
        .padding(.top, PlantbillSpacing.sm)
        .padding(.bottom, PlantbillSpacing.sm)
        .background(.bar)
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingStateView(message: "Loading your products…")
        case .empty:
            EmptyStateView(
                icon: "leaf",
                title: "No products yet",
                message: canManage ? "Tap \"Add product\" to create your first one." : "Products you add will show up here."
            )
        case .error(let message):
            ErrorStateView(message: LocalizedStringKey(message)) {
                Task { await viewModel.load() }
            }
        case .loaded(let products):
            List(products) { product in
                ProductRow(product: product)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: PlantbillSpacing.xs, leading: PlantbillSpacing.md, bottom: PlantbillSpacing.xs, trailing: PlantbillSpacing.md))
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if canManage { editingProduct = product }
                    }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable { await viewModel.load() }
        }
    }
}

private struct ProductRow: View {
    let product: Product

    var body: some View {
        PlantbillCard {
            HStack(spacing: PlantbillSpacing.md) {
                thumbnail

                VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                    Text(product.name)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                        .lineLimit(1)
                    if let category = product.category, !category.isEmpty {
                        Text(category)
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    if !product.isActive {
                        Text("Inactive")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.warning)
                    }
                }

                Spacer()

                Text(product.price.format())
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.green)
            }
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius)
            .fill(PlantbillColor.greenTint)
            .frame(width: 52, height: 52)
            .overlay {
                if let url = product.resolvedPhotoURL {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image {
                            image.resizable().scaledToFill()
                        } else {
                            placeholderIcon
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius))
                } else {
                    placeholderIcon
                }
            }
            .clipped()
    }

    private var placeholderIcon: some View {
        Image(systemName: "leaf.fill")
            .foregroundStyle(PlantbillColor.green)
    }
}

#Preview {
    ProductsListView(canManage: true)
        .environment(AuthSession())
        .environment(NotificationsStore())
}
