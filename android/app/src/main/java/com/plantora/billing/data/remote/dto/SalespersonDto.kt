package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SalespersonDto(
    val id: String,
    val email: String,
    val role: String = "salesperson",
    @SerialName("shop_id") val shopId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class SalespersonCreateDto(
    val email: String,
    val password: String,
)

@Serializable
data class SalespersonActivateDto(
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class SalespersonResetPasswordDto(
    @SerialName("new_password") val newPassword: String,
)
