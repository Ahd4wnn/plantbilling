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

data class OwnerStaff(
    val id: String,
    val email: String,
    val role: String,
    val isActive: Boolean,
    val shopId: String?,
)
