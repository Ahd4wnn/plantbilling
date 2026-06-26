package com.plantora.billing.domain

enum class DiscountType(val wire: String) {
    FLAT("flat"), PERCENT("percent");

    companion object {
        fun from(raw: String?) = if (raw == "percent") PERCENT else FLAT
    }
}

enum class PaymentMethod {
    CASH, UPI, SPLIT, DUE, NONE;

    val label: String
        get() = when (this) {
            CASH -> "Cash"; UPI -> "UPI"; SPLIT -> "Split"; DUE -> "Due"; NONE -> "—"
        }

    companion object {
        fun from(raw: String?) = when (raw) {
            "cash" -> CASH; "upi" -> UPI; "split" -> SPLIT; "due" -> DUE; else -> NONE
        }
    }
}

data class BillItem(
    val productId: String?,
    val productName: String,
    val unitPrice: Money,
    val quantity: Int,
    val lineTotal: Money,
)

/** The created-bill result (server-authoritative totals). */
data class Bill(
    val id: String,
    val subtotal: Money,
    val discountType: DiscountType,
    val discountValue: Money,
    val discountAmount: Money,
    val total: Money,
    val cashAmount: Money,
    val upiAmount: Money,
    val dueAmount: Money,
    val customerName: String?,
    val createdAt: String,
    val items: List<BillItem>,
    val idempotentReplay: Boolean,
)

/** A complete, self-contained bill for the detail / receipt / reprint surface. */
data class BillDetail(
    val id: String,
    val shopName: String?,
    val businessName: String?,
    val businessAddress: String?,
    val businessPhone: String?,
    val subtotal: Money,
    val discountType: DiscountType,
    val discountValue: Money,
    val discountAmount: Money,
    val total: Money,
    val cashAmount: Money,
    val upiAmount: Money,
    val dueAmount: Money,
    val paymentMethod: PaymentMethod,
    val customerName: String?,
    val customerPhone: String?,
    val salespersonEmail: String?,
    val remarks: String?,
    val isEdited: Boolean,
    val createdAt: String,
    val items: List<BillItem>,
)
