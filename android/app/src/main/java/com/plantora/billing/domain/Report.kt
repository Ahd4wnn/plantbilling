package com.plantora.billing.domain

data class CategorySales(
    val category: String,
    val quantity: Int,
    val totalSales: Money,
)

data class ProductSales(
    val productName: String,
    val quantity: Int,
    val totalSales: Money,
)

/** A point in the daily sales trend (client-bucketed from bills in range). */
data class TrendPoint(
    val date: String,   // yyyy-MM-dd
    val sales: Money,
)

data class DetailedReport(
    val startDate: String,
    val endDate: String,
    val totalSales: Money,
    val billCount: Int,
    val cashTotal: Money,
    val upiTotal: Money,
    val dueTotal: Money,
    val averageBillValue: Money,
    val totalExpenses: Money,
    val netSales: Money,
    val expenses: List<Expense>,
    val categories: List<CategorySales>,
    val topProducts: List<ProductSales>,
    /** Optional daily trend (empty for single-day reports). */
    val trend: List<TrendPoint> = emptyList(),
)
