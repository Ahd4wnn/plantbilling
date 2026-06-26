package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategorySalesDto(
    val category: String? = null,
    val quantity: Int,
    @SerialName("total_sales") val totalSales: String,
)

@Serializable
data class ProductSalesDto(
    @SerialName("product_name") val productName: String,
    val quantity: Int,
    @SerialName("total_sales") val totalSales: String,
)

@Serializable
data class DetailedReportDto(
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("total_sales") val totalSales: String,
    @SerialName("bill_count") val billCount: Int,
    @SerialName("cash_total") val cashTotal: String,
    @SerialName("upi_total") val upiTotal: String,
    @SerialName("due_total") val dueTotal: String = "0",
    @SerialName("average_bill_value") val averageBillValue: String,
    @SerialName("total_expenses") val totalExpenses: String = "0",
    @SerialName("net_sales") val netSales: String = "0",
    val expenses: List<ExpenseDto> = emptyList(),
    val categories: List<CategorySalesDto> = emptyList(),
    @SerialName("top_products") val topProducts: List<ProductSalesDto> = emptyList(),
)
