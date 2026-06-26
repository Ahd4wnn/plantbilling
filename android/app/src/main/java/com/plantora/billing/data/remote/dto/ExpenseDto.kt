package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("shop_id") val shopId: String? = null,
    val amount: String,
    val reason: String,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ExpenseCreateDto(
    val amount: String,
    val reason: String,
)
