import Foundation
import Observation

enum ProductActiveFilter: String {
    case active = "true"
    case inactive = "false"
    case all
}

@Observable
@MainActor
final class ProductsViewModel {
    enum LoadState {
        case loading
        case loaded([Product])
        case empty
        case error(String)
    }

    private(set) var state: LoadState = .loading
    var searchText: String = "" {
        didSet { scheduleReload() }
    }
    var selectedCategory: String? = nil {
        didSet { Task { await load() } }
    }
    var includeInactive: Bool = false {
        didSet { Task { await load() } }
    }

    /// Distinct categories seen across the last successful load, for the
    /// filter chips.
    private(set) var categories: [String] = []

    private(set) var isMutating = false
    private(set) var mutationError: String?

    private var searchDebounceTask: Task<Void, Never>?

    private func scheduleReload() {
        searchDebounceTask?.cancel()
        searchDebounceTask = Task {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            await load()
        }
    }

    func load() async {
        state = .loading
        do {
            var query: [URLQueryItem] = []
            let trimmedSearch = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmedSearch.isEmpty { query.append(URLQueryItem(name: "q", value: trimmedSearch)) }
            if let selectedCategory { query.append(URLQueryItem(name: "category", value: selectedCategory)) }
            query.append(URLQueryItem(name: "active", value: includeInactive ? ProductActiveFilter.all.rawValue : ProductActiveFilter.active.rawValue))

            let products: [Product] = try await APIClient.shared.send(Endpoint(path: "products", queryItems: query))
            categories = Array(Set(products.compactMap { $0.category?.isEmpty == false ? $0.category : nil })).sorted()
            state = products.isEmpty ? .empty : .loaded(products)
        } catch let error as APIError {
            state = .error(error.userMessage)
        } catch {
            state = .error(APIError.unknown.userMessage)
        }
    }

    /// Returns the created product (so a freshly-picked photo can be
    /// uploaded against its id) or nil on failure.
    func create(name: String, category: String?, price: Money) async -> Product? {
        await mutate {
            let body = try APIClient.shared.encode(
                ProductCreateRequest(
                    name: name,
                    category: category?.isEmpty == false ? category : nil,
                    retailPrice: price.toWire(),
                    lastWholesalePrice: nil
                )
            )
            return try await APIClient.shared.send(
                Endpoint(path: "products", method: .post, body: body)
            )
        }
    }

    func update(id: UUID, name: String, category: String?, price: Money, isActive: Bool) async -> Product? {
        await mutate {
            var request = ProductUpdateRequest()
            request.name = name
            request.retailPrice = price.toWire()
            request.isActive = isActive
            request.category = category?.isEmpty == false ? category : .some(nil)
            let body = try APIClient.shared.encode(request)
            return try await APIClient.shared.send(
                Endpoint(path: "products/\(id)", method: .patch, body: body)
            )
        }
    }

    @discardableResult
    func delete(id: UUID) async -> Bool {
        await mutate {
            let _: ProductDeleteResponse = try await APIClient.shared.send(
                Endpoint(path: "products/\(id)", method: .delete)
            )
            return true
        } != nil
    }

    @discardableResult
    func uploadImage(id: UUID, data: Data, mimeType: String) async -> Bool {
        await mutate {
            let product: Product = try await APIClient.shared.upload(
                path: "products/\(id)/image",
                file: MultipartFile(fieldName: "file", filename: "photo.jpg", mimeType: mimeType, data: data)
            )
            return product
        } != nil
    }

    @discardableResult
    func deleteImage(id: UUID) async -> Bool {
        await mutate {
            let product: Product = try await APIClient.shared.send(
                Endpoint(path: "products/\(id)/image", method: .delete)
            )
            return product
        } != nil
    }

    @discardableResult
    func bulkUpload(fileData: Data, filename: String, mimeType: String) async -> Bool {
        await mutate {
            let products: [Product] = try await APIClient.shared.upload(
                path: "products/bulk-upload",
                file: MultipartFile(fieldName: "file", filename: filename, mimeType: mimeType, data: fileData)
            )
            return products
        } != nil
    }

    func bulkPhotos(zipData: Data, filename: String) async -> BulkPhotosResponse? {
        do {
            let result: BulkPhotosResponse = try await APIClient.shared.upload(
                path: "products/bulk-photos",
                file: MultipartFile(fieldName: "file", filename: filename, mimeType: "application/zip", data: zipData)
            )
            await load()
            return result
        } catch let error as APIError {
            mutationError = error.userMessage
            return nil
        } catch {
            mutationError = APIError.unknown.userMessage
            return nil
        }
    }

    func downloadSampleFile() async -> Data? {
        try? await APIClient.shared.download(Endpoint(path: "products/sample-file"))
    }

    func clearMutationError() {
        mutationError = nil
    }

    /// Runs a mutation, reloads the list on success, and surfaces a
    /// plain-language error via `mutationError` on failure. Returns the
    /// operation's result, or nil on failure, so the caller (a sheet) knows
    /// whether to dismiss and — for create/update — can chain a photo
    /// upload against the returned product's id.
    @discardableResult
    private func mutate<T>(_ operation: () async throws -> T) async -> T? {
        isMutating = true
        mutationError = nil
        defer { isMutating = false }
        do {
            let result = try await operation()
            await load()
            return result
        } catch let error as APIError {
            mutationError = error.userMessage
            return nil
        } catch {
            mutationError = APIError.unknown.userMessage
            return nil
        }
    }
}
