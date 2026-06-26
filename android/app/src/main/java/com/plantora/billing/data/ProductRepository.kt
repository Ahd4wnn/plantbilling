package com.plantora.billing.data

import com.plantora.billing.data.remote.BaseUrlProvider
import com.plantora.billing.data.remote.api.ProductsApi
import com.plantora.billing.data.remote.dto.ProductCreateDto
import com.plantora.billing.data.remote.dto.ProductUpdateDto
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a spreadsheet/photo bulk import, surfaced to the user. */
data class BulkResult(val title: String, val detail: String)

@Singleton
class ProductRepository @Inject constructor(
    private val api: ProductsApi,
    private val baseUrlProvider: BaseUrlProvider,
    private val fileDownloader: FileDownloader,
) {
    suspend fun list(query: String? = null, category: String? = null, active: String = "true"): List<Product> {
        val base = baseUrlProvider.get()
        return api.list(query?.takeIf { it.isNotBlank() }, category, active).map { it.toDomain(base) }
    }

    suspend fun create(name: String, retailPrice: Money, category: String?): Product =
        api.create(
            ProductCreateDto(
                name = name.trim(),
                category = category?.takeIf { it.isNotBlank() }?.trim(),
                retailPrice = retailPrice.toWire(),
            ),
        ).toDomain(baseUrlProvider.get())

    suspend fun update(
        id: String,
        name: String? = null,
        retailPrice: Money? = null,
        category: String? = null,
        isActive: Boolean? = null,
    ): Product = api.update(
        id,
        ProductUpdateDto(
            name = name?.trim(),
            category = category?.trim(),
            retailPrice = retailPrice?.toWire(),
            isActive = isActive,
        ),
    ).toDomain(baseUrlProvider.get())

    suspend fun delete(id: String) = api.delete(id)

    suspend fun uploadImage(id: String, bytes: ByteArray, fileName: String, mimeType: String): Product {
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        return api.uploadImage(id, part).toDomain(baseUrlProvider.get())
    }

    private fun filePart(bytes: ByteArray, fileName: String, mime: String): MultipartBody.Part =
        MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull()))

    /** Import products from an Excel/CSV spreadsheet. Returns how many were added. */
    suspend fun bulkUpload(bytes: ByteArray, fileName: String, mime: String): BulkResult {
        val created = api.bulkUpload(filePart(bytes, fileName, mime))
        return BulkResult("Spreadsheet imported", "${created.size} product(s) added or updated.")
    }

    /** Match a ZIP of photos to products by file name. */
    suspend fun bulkPhotos(bytes: ByteArray, fileName: String, mime: String): BulkResult {
        val res = api.bulkPhotos(filePart(bytes, fileName, mime))
        val errors = if (res.errors.isEmpty()) "" else " ${res.errors.size} skipped."
        return BulkResult("Photos uploaded", "${res.matched} matched.$errors")
    }

    /** Download the Excel template to Downloads. Returns the saved file name. */
    suspend fun downloadSample(): String {
        val body = api.sampleFile()
        return fileDownloader.saveToDownloads(
            "plantora_products_template.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            body.bytes(),
        )
    }
}
