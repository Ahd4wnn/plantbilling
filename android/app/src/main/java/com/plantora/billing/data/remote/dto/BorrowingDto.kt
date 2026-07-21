package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BorrowingDto(
    val id: String,
    @SerialName("lender_name") val lenderName: String,
    @SerialName("lender_phone") val lenderPhone: String? = null,
    val amount: String,
    @SerialName("cash_amount") val cashAmount: String = "0.00",
    @SerialName("upi_amount") val upiAmount: String = "0.00",
    val method: String = "none",
    val remarks: String? = null,
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("paid_cash_amount") val paidCashAmount: String = "0.00",
    @SerialName("paid_upi_amount") val paidUpiAmount: String = "0.00",
    @SerialName("paid_method") val paidMethod: String = "none",
    val outstanding: String = "0.00",
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BorrowingListDto(
    val items: List<BorrowingDto> = emptyList(),
    @SerialName("total_outstanding") val totalOutstanding: String = "0.00",
)

@Serializable
data class BorrowingCreateDto(
    @SerialName("lender_name") val lenderName: String,
    @SerialName("lender_phone") val lenderPhone: String? = null,
    val amount: String,
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("upi_amount") val upiAmount: String,
    val remarks: String? = null,
)

@Serializable
data class BorrowingPayDto(
    @SerialName("paid_cash_amount") val paidCashAmount: String,
    @SerialName("paid_upi_amount") val paidUpiAmount: String,
)
