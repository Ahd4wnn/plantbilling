package com.plantora.billing.domain

data class Expense(
    val id: String,
    val amount: Money,
    /** Snapshot label (category name at create time, or legacy free text). */
    val reason: String,
    val categoryId: String?,
    val categoryName: String?,
    val note: String?,
    /** How it was paid: "cash" (out of the drawer) or "upi". */
    val paymentMethod: String,
    val createdAt: String,
) {
    /** What to show as the expense's title. */
    val displayName: String get() = categoryName ?: reason
}

/** A reusable, manager-curated expense category (e.g. "Petrol"). */
data class ExpenseCategory(
    val id: String,
    val name: String,
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
    /** Labour paid this day, all methods (for display). */
    val labourTotal: Money,
    /** Labour paid IN CASH this day — the only part that lowers the drawer. */
    val labourCash: Money,
    /** Running (all-time) cash in hand as of this day: baseline + every day's cash. */
    val cashInHandRunning: Money,
    val netSales: Money,
    val expenses: List<Expense>,
) {
    /** Today-only cash left in the drawer (cash sales − cash expenses − labour paid IN
     *  CASH). A UPI labour payment must not lower this — that was the bug. */
    val cashInHandToday: Money get() = cashTotal - cashExpenses - labourCash
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
