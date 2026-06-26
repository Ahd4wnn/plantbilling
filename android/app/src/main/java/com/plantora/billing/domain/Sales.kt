package com.plantora.billing.domain

data class Expense(
    val id: String,
    val amount: Money,
    val reason: String,
    val createdAt: String,
)

/** A day's takings + cash book (shop timezone, computed server-side). */
data class DaySummary(
    val date: String,
    val totalSales: Money,
    val billCount: Int,
    val cashTotal: Money,
    val upiTotal: Money,
    val dueTotal: Money,
    val totalExpenses: Money,
    val netSales: Money,
    val expenses: List<Expense>,
)

/** A compact bill row for the history list. */
data class BillListEntry(
    val id: String,
    val createdAt: String,
    val total: Money,
    val dueAmount: Money,
    val customerName: String?,
    val itemCount: Int,
    val paymentMethod: PaymentMethod,
    val isEdited: Boolean,
)

data class BillPage(
    val items: List<BillListEntry>,
    val offset: Int,
    val hasMore: Boolean,
)
