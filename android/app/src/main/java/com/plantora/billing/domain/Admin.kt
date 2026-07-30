package com.plantora.billing.domain

/**
 * Admin-portal domain models. The platform admin operates across all shops.
 *
 * Two distinct notions of money live here, and they must not be confused:
 *  - The DASHBOARD deliberately carries NO shop money — only counts (bills as an
 *    engagement signal), because a shop's sales are the shop's private data.
 *  - The BOOKS ([AdminSale] / [AdminExpense] / [LedgerSummary]) are the platform's
 *    OWN income and costs (e.g. selling printers/subscriptions to shops).
 */

// ── Dashboard (counts only) ───────────────────────────────────────────────────
data class AdminOverview(
    val totalShops: Int,
    val activeShops: Int,
    val inactiveShops: Int,
    val newShops: Int,
    val totalStaff: Int,
    val totalOwners: Int,
    val totalBills: Int,
    val shops: List<AdminShopRow>,
    val trend: List<AdminTrendPoint>,
    val attention: List<AdminAttention>,
)

data class AdminShopRow(
    val shopId: String,
    val shopName: String,
    val isActive: Boolean,
    val ownerEmail: String?,
    val ownerCount: Int,
    val billsInPeriod: Int,
    val staffCount: Int,
    val lastBillAt: String?,
)

data class AdminTrendPoint(val date: String, val bills: Int, val newShops: Int)

data class AdminAttention(val shopId: String, val shopName: String, val kind: String, val detail: String)

data class AdminActivity(val createdAt: String, val salespersonEmail: String?, val itemCount: Int)

data class AdminShopDetail(
    val shopId: String,
    val shopName: String,
    val isActive: Boolean,
    val createdAt: String,
    val businessName: String?,
    val businessAddress: String?,
    val businessPhone: String?,
    val businessEmail: String?,
    val businessUpi: String?,
    val ownerEmail: String?,
    val ownerEmails: List<String>,
    val staffCount: Int,
    val productsCount: Int,
    val customersCount: Int,
    val bills7: Int,
    val bills30: Int,
    val lastBillAt: String?,
    val recentActivity: List<AdminActivity>,
)

// ── Notifications ─────────────────────────────────────────────────────────────
data class AdminNotification(
    val id: String,
    val title: String,
    val body: String,
    val target: String,       // "all" | "shops"
    val shopCount: Int,       // shops addressed (0 for "all" — every shop)
    val readCount: Int,       // distinct users who have read it
    val createdAt: String,
)

// ── Platform own books ────────────────────────────────────────────────────────
data class AdminSale(
    val id: String,
    val title: String,
    val amount: Money,
    val cashAmount: Money,
    val upiAmount: Money,
    val dueAmount: Money,
    val customerName: String?,
    val customerPhone: String?,
    val note: String?,
    val occurredOn: String,
    val createdAt: String,
)

data class AdminExpense(
    val id: String,
    val reason: String,
    val amount: Money,
    val paymentMethod: String,   // "cash" | "upi"
    val note: String?,
    val occurredOn: String,
    val createdAt: String,
)

data class LedgerTrendPoint(val date: String, val sales: Money, val expenses: Money)

data class LedgerSummary(
    val totalSales: Money,
    val salesCount: Int,
    val cashCollected: Money,
    val upiCollected: Money,
    val outstandingDue: Money,
    val totalExpenses: Money,
    val expensesCount: Int,
    val netCollected: Money,
    val trend: List<LedgerTrendPoint>,
)
