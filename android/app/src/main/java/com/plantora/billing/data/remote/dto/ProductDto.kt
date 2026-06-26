package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val category: String? = null,
    @SerialName("retail_price") val retailPrice: String,
    @SerialName("last_wholesale_price") val lastWholesalePrice: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ProductCreateDto(
    val name: String,
    val category: String? = null,
    @SerialName("retail_price") val retailPrice: String,
    @SerialName("last_wholesale_price") val lastWholesalePrice: String? = null,
)

@Serializable
data class ProductUpdateDto(
    val name: String? = null,
    val category: String? = null,
    @SerialName("retail_price") val retailPrice: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class BulkPhotosResponseDto(
    val detail: String,
    val matched: Int = 0,
    val errors: List<String> = emptyList(),
)
