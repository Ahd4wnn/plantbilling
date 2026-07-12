package com.plantora.billing.domain

data class Expense(
    val id: String,
    val amount: Money,
    val reason: String,
    /** How it was paid: "cash" (out of the drawer) or "upi". */
    val paymentMethod: String,
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
    /** Expenses paid in cash — subtract from cash sales for Cash in Hand. */
    val cashExpenses: Money,
    /** Expenses paid via UPI. */
    val upiExpenses: Money,
    /** Labour paid this day — cash out of the drawer. */
    val labourTotal: Money,
    /** Running (all-time) cash in hand as of this day: baseline + every day's cash. */
    val cashInHandRunning: Money,
    val netSales: Money,
    val expenses: List<Expense>,
) {
    /** Today-only cash left in the drawer (cash sales − cash expenses − labour paid). */
    val cashInHandToday: Money get() = cashTotal - cashExpenses - labourTotal
}

/** A compact bill row for the history list. */
data class BillListEntry(
    val id: String,
    val createdAt: String,
    val total: Money,
    val dueAmount: Money,
    val customerName: String?,
    val customerPhone: String?,
    val itemCount: Int,
    val paymentMethod: PaymentMethod,
    val isEdited: Boolean,
    /** A salesperson's collection of this due is waiting for a manager to approve. */
    val pendingSettlement: Boolean = false,
)

data class BillPage(
    val items: List<BillListEntry>,
    val offset: Int,
    val hasMore: Boolean,
)
