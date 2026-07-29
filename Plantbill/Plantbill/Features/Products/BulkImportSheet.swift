import SwiftUI
import UniformTypeIdentifiers

struct BulkImportSheet: View {
    let viewModel: ProductsViewModel

    @Environment(\.dismiss) private var dismiss

    @State private var isDownloading = false
    @State private var isUploadingSheet = false
    @State private var isUploadingPhotos = false
    @State private var shareURL: URL?
    @State private var showingShareSheet = false
    @State private var showingSpreadsheetImporter = false
    @State private var showingZipImporter = false
    @State private var resultMessage: String?
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                Text("Add many products at once from a spreadsheet, then attach photos by file name.")
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)

                VStack(spacing: PlantbillSpacing.md) {
                    importRow(
                        icon: "arrow.down.doc.fill",
                        title: "Download Excel template",
                        isLoading: isDownloading,
                        action: downloadSample
                    )
                    importRow(
                        icon: "doc.badge.plus",
                        title: "Upload spreadsheet (.xlsx/.csv)",
                        isLoading: isUploadingSheet,
                        action: { showingSpreadsheetImporter = true }
                    )
                    importRow(
                        icon: "photo.stack.fill",
                        title: "Upload photos (.zip)",
                        isLoading: isUploadingPhotos,
                        action: { showingZipImporter = true }
                    )
                }

                if let resultMessage {
                    Text(LocalizedStringKey(resultMessage))
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.green)
                }
                if let errorMessage {
                    InlineErrorText(message: LocalizedStringKey(errorMessage))
                }

                Spacer()
            }
            .padding(PlantbillSpacing.lg)
            .background(PlantbillColor.background)
            .navigationTitle("Bulk import")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: $showingShareSheet) {
                if let shareURL {
                    ActivityView(items: [shareURL])
                }
            }
            .fileImporter(
                isPresented: $showingSpreadsheetImporter,
                allowedContentTypes: spreadsheetTypes,
                onCompletion: { handleSpreadsheetImport($0) }
            )
            .fileImporter(
                isPresented: $showingZipImporter,
                allowedContentTypes: [.zip],
                onCompletion: { handleZipImport($0) }
            )
        }
    }

    private var spreadsheetTypes: [UTType] {
        var types: [UTType] = [.commaSeparatedText]
        if let xlsx = UTType(filenameExtension: "xlsx") { types.append(xlsx) }
        return types
    }

    @ViewBuilder
    private func importRow(icon: String, title: LocalizedStringKey, isLoading: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                    .foregroundStyle(PlantbillColor.green)
                    .frame(width: 28)
                Text(title)
                    .font(PlantbillTypography.body)
                    .fontWeight(.medium)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Spacer()
                if isLoading {
                    ProgressView()
                } else {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
            }
            .frame(minHeight: PlantbillSpacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .disabled(isLoading)
    }

    private func downloadSample() {
        resultMessage = nil
        errorMessage = nil
        isDownloading = true
        Task {
            defer { isDownloading = false }
            guard let data = await viewModel.downloadSampleFile() else {
                errorMessage = "Couldn't download the sample file."
                return
            }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("plantbill-products-sample.csv")
            do {
                try data.write(to: url, options: .atomic)
                shareURL = url
                showingShareSheet = true
            } catch {
                errorMessage = "Couldn't download the sample file."
            }
        }
    }

    private func handleSpreadsheetImport(_ result: Result<URL, Error>) {
        resultMessage = nil
        errorMessage = nil
        guard case .success(let url) = result else { return }

        isUploadingSheet = true
        Task {
            defer { isUploadingSheet = false }
            // Reading a multi-MB spreadsheet is a blocking disk read — do it
            // off the main actor so the UI doesn't hitch while it happens.
            guard let data = await Self.readSecurityScopedFile(at: url) else {
                errorMessage = "Couldn't read that file."
                return
            }
            let mimeType = url.pathExtension.lowercased() == "csv" ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            let success = await viewModel.bulkUpload(fileData: data, filename: url.lastPathComponent, mimeType: mimeType)
            if success {
                resultMessage = "Products imported."
            } else {
                errorMessage = viewModel.mutationError
            }
        }
    }

    private func handleZipImport(_ result: Result<URL, Error>) {
        resultMessage = nil
        errorMessage = nil
        guard case .success(let url) = result else { return }

        isUploadingPhotos = true
        Task {
            defer { isUploadingPhotos = false }
            // A photo ZIP can be up to 50MB — same off-main-actor read.
            guard let data = await Self.readSecurityScopedFile(at: url) else {
                errorMessage = "Couldn't read that file."
                return
            }
            if let response = await viewModel.bulkPhotos(zipData: data, filename: url.lastPathComponent) {
                resultMessage = response.detail
            } else {
                errorMessage = viewModel.mutationError
            }
        }
    }

    private nonisolated static func readSecurityScopedFile(at url: URL) async -> Data? {
        await Task.detached(priority: .userInitiated) {
            guard url.startAccessingSecurityScopedResource() else { return nil }
            defer { url.stopAccessingSecurityScopedResource() }
            return try? Data(contentsOf: url)
        }.value
    }
}
