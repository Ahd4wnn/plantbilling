package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request ──────────────────────────────────────────────────────────────────
@Serializable
data class BillItemInDto(
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: String,
)

@Serializable
data class NewCustomerDto(
    val name: String,
    val phone: String? = null,
)

@Serializable
data class BillCreateDto(
    @SerialName("idempotency_key") val idempotencyKey: String,
    val items: List<BillItemInDto>,
    @SerialName("discount_type") val discountType: String = "flat",
    @SerialName("discount_value") val discountValue: String = "0",
    @SerialName("cash_amount") val cashAmount: String = "0",
    @SerialName("upi_amount") val upiAmount: String = "0",
    @SerialName("due_amount") val dueAmount: String = "0",
    val remarks: String? = null,
    @SerialName("new_customer") val newCustomer: NewCustomerDto? = null,
)

@Serializable
data class BillUpdateDto(
    @SerialName("cash_amount") val cashAmount: String? = null,
    @SerialName("upi_amount") val upiAmount: String? = null,
    @SerialName("due_amount") val dueAmount: String? = null,
    val remarks: String? = null,
)

// ── Response ─────────────────────────────────────────────────────────────────
@Serializable
data class BillItemOutDto(
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String,
    @SerialName("unit_price") val unitPrice: String,
    val quantity: Int,
    @SerialName("line_total") val lineTotal: String,
)

@Serializable
data class BillOutDto(
    val id: String,
    @SerialName("bill_type") val billType: String,
    val subtotal: String,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: String,
    @SerialName("discount_amount") val discountAmount: String,
    val total: String,
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("upi_amount") val upiAmount: String,
    @SerialName("due_amount") val dueAmount: String = "0",
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("salesperson_email") val salespersonEmail: String? = null,
    val remarks: String? = null,
    @SerialName("is_edited") val isEdited: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    val items: List<BillItemOutDto> = emptyList(),
    @SerialName("idempotent_replay") val idempotentReplay: Boolean = false,
)

/** Full, self-contained bill used for the receipt / reprint surface. */
@Serializable
data class BillDetailDto(
    val id: String,
    @SerialName("shop_name") val shopName: String? = null,
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("business_address") val businessAddress: String? = null,
    @SerialName("business_phone") val businessPhone: String? = null,
    @SerialName("bill_type") val billType: String,
    val subtotal: String,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: String,
    @SerialName("discount_amount") val discountAmount: String,
    val total: String,
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("upi_amount") val upiAmount: String,
    @SerialName("due_amount") val dueAmount: String = "0",
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("salesperson_email") val salespersonEmail: String? = null,
    val remarks: String? = null,
    @SerialName("is_edited") val isEdited: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    val items: List<BillItemOutDto> = emptyList(),
)

// ── Sales list & summary ─────────────────────────────────────────────────────
@Serializable
data class BillListItemDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("bill_type") val billType: String,
    val total: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("item_count") val itemCount: Int,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("is_edited") val isEdited: Boolean = false,
)

@Serializable
data class BillListDto(
    val items: List<BillListItemDto> = emptyList(),
    val limit: Int,
    val offset: Int,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
data class BillSummaryDto(
    val date: String,
    @SerialName("total_sales") val totalSales: String,
    @SerialName("bill_count") val billCount: Int,
    @SerialName("cash_total") val cashTotal: String,
    @SerialName("upi_total") val upiTotal: String,
    @SerialName("due_total") val dueTotal: String = "0",
    @SerialName("total_expenses") val totalExpenses: String = "0",
    @SerialName("net_sales") val netSales: String = "0",
    val expenses: List<ExpenseDto> = emptyList(),
)
