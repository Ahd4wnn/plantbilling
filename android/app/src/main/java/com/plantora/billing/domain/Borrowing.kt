package com.plantora.billing.domain

/** Money the shop borrowed from a person (a lender). A plain debt note. */
data class Borrowing(
    val id: String,
    val lenderName: String,
    val lenderPhone: String?,
    val amount: Money,
    val method: String,          // how it was received: "cash" | "upi" | "split" | "none"
    val remarks: String?,
    val isPaid: Boolean,
    val paidMethod: String,      // how it was repaid
    val outstanding: Money,      // how much is still owed (amount − repaid so far)
    val paidAt: String?,
    val createdAt: String,
)

/** A page of borrowings plus the total still owed. */
data class BorrowingList(
    val items: List<Borrowing>,
    val totalOutstanding: Money,
)
