package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SettlementCreateDto(
    @SerialName("bill_id") val billId: String,
    @SerialName("cash_amount") val cashAmount: String = "0",
    @SerialName("upi_amount") val upiAmount: String = "0",
)

/** Result of creating a settlement: status is "approved" (manager applied it now)
 *  or "pending" (a salesperson's request awaiting a manager). */
@Serializable
data class SettlementActionResultDto(
    val id: String,
    @SerialName("bill_id") val billId: String,
    val status: String,
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("upi_amount") val upiAmount: String,
)

/** A row in the manager's approval queue. */
@Serializable
data class SettlementDto(
    val id: String,
    @SerialName("bill_id") val billId: String,
    val status: String,
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("upi_amount") val upiAmount: String,
    @SerialName("bill_total") val billTotal: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("requested_by_email") val requestedByEmail: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SettlementListDto(
    val items: List<SettlementDto> = emptyList(),
)
