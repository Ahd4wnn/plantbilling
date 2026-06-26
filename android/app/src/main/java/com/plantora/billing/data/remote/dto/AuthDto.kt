package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class CurrentUserDto(
    val id: String,
    val email: String,
    val role: String,
    @SerialName("shop_id") val shopId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("shop_name") val shopName: String? = null,
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("business_upi") val businessUpi: String? = null,
)
