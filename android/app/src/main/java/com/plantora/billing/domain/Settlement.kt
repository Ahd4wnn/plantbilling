package com.plantora.billing.domain

/** A salesperson's due-collection waiting for a manager to approve. */
data class PendingSettlement(
    val id: String,
    val billId: String,
    val cashAmount: Money,
    val upiAmount: Money,
    val billTotal: Money,
    val customerName: String?,
    val customerPhone: String?,
    val requestedByEmail: String?,
    val createdAt: String,
) {
    /** Total being collected across cash + UPI. */
    val amount: Money get() = cashAmount + upiAmount
}
