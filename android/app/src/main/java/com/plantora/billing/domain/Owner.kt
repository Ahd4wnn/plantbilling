package com.plantora.billing.domain

/** A shop owned by the signed-in multi-shop owner. */
data class OwnerShop(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val businessName: String?,
    val businessAddress: String?,
    val businessPhone: String?,
    val businessEmail: String?,
    val businessUpi: String?,
)

/** One shop's takings within the overview period. */
data class ShopOverviewRow(
    val shopId: String,
    val shopName: String,
    val totalSales: Money,
    val billCount: Int,
    val cashTotal: Money,
    val upiTotal: Money,
    val dueTotal: Money,
    val totalExpenses: Money,
    val netSales: Money,
)

/** A staff member's sales across the owner's shops (leaderboard row). */
data class StaffPerf(
    val userId: String?,
    val email: String?,
    val shopId: String,
    val shopName: String,
    val role: String,
    val totalSales: Money,
    val billCount: Int,
)

/** Aggregate across all owned shops + per-shop breakdown + staff leaderboard. */
data class OwnerOverview(
    val startDate: String,
    val endDate: String,
    val shopCount: Int,
    val totalSales: Money,
    val billCount: Int,
    val cashTotal: Money,
    val upiTotal: Money,
    val dueTotal: Money,
    val totalExpenses: Money,
    val netSales: Money,
    val shops: List<ShopOverviewRow>,
    val staff: List<StaffPerf>,
)

/** One saved bill in an owned shop — who sold it, when, to whom, how paid. */
data class OwnerBill(
    val id: String,
    val createdAt: String,
    val total: Money,
    val dueAmount: Money,
    val paymentMethod: String,
    val customerName: String?,
    val customerPhone: String?,
    val salespersonEmail: String?,
    val salespersonRole: String?,
    val itemCount: Int,
)

data class OwnerStaff(
    val id: String,
    val email: String,
    val role: String,
    val isActive: Boolean,
    val shopId: String?,
)

/** Cash in the drawer for an owned shop: running (all-time carry-over) or just this day's. */
data class OwnerCashInHand(
    val running: Money,
    val today: Money,
)
