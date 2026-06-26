package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShopSettingsDto(
    val id: String,
    val name: String,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("owner_phone") val ownerPhone: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("business_address") val businessAddress: String? = null,
    @SerialName("business_phone") val businessPhone: String? = null,
    @SerialName("business_email") val businessEmail: String? = null,
    @SerialName("business_upi") val businessUpi: String? = null,
)

@Serializable
data class ShopSettingsUpdateDto(
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("business_address") val businessAddress: String? = null,
    @SerialName("business_phone") val businessPhone: String? = null,
    @SerialName("business_email") val businessEmail: String? = null,
    @SerialName("business_upi") val businessUpi: String? = null,
)
