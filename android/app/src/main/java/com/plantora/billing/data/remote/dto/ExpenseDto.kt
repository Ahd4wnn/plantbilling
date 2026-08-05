package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("shop_id") val shopId: String? = null,
    val amount: String,
    val reason: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val note: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "cash",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ExpenseCreateDto(
    val amount: String,
    @SerialName("category_id") val categoryId: String,
    val note: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "cash",
)

// ── Expense categories (manager-curated) ──────────────────────────────────────
@Serializable
data class ExpenseCategoryDto(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ExpenseCategoryCreateDto(
    val name: String,
)
