package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OwnerShopDto(
    val id: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("business_address") val businessAddress: String? = null,
    @SerialName("business_phone") val businessPhone: String? = null,
    @SerialName("business_email") val businessEmail: String? = null,
    @SerialName("business_upi") val businessUpi: String? = null,
    @SerialName("whatsapp_auto_send") val whatsappAutoSend: Boolean = false,
)

@Serializable
data class ShopOverviewRowDto(
    @SerialName("shop_id") val shopId: String,
    @SerialName("shop_name") val shopName: String,
    @SerialName("total_sales") val totalSales: String,
    @SerialName("bill_count") val billCount: Int,
    @SerialName("cash_total") val cashTotal: String,
    @SerialName("upi_total") val upiTotal: String,
    @SerialName("due_total") val dueTotal: String,
    @SerialName("total_expenses") val totalExpenses: String,
    @SerialName("net_sales") val netSales: String,
)

@Serializable
data class StaffPerfDto(
    @SerialName("user_id") val userId: String? = null,
    val email: String? = null,
    @SerialName("shop_id") val shopId: String,
    @SerialName("shop_name") val shopName: String,
    val role: String,
    @SerialName("total_sales") val totalSales: String,
    @SerialName("bill_count") val billCount: Int,
)

@Serializable
data class OwnerOverviewDto(
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("shop_count") val shopCount: Int,
    @SerialName("total_sales") val totalSales: String,
    @SerialName("bill_count") val billCount: Int,
    @SerialName("cash_total") val cashTotal: String,
    @SerialName("upi_total") val upiTotal: String,
    @SerialName("due_total") val dueTotal: String,
    @SerialName("total_expenses") val totalExpenses: String,
    @SerialName("net_sales") val netSales: String,
    val shops: List<ShopOverviewRowDto> = emptyList(),
    val staff: List<StaffPerfDto> = emptyList(),
)

@Serializable
data class OwnerStaffDto(
    val id: String,
    val email: String,
    val role: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("shop_id") val shopId: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class OwnerStaffCreateDto(
    val email: String,
    val password: String,
    val role: String,
)

@Serializable
data class OwnerShopUpdateDto(
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("business_address") val businessAddress: String? = null,
    @SerialName("business_phone") val businessPhone: String? = null,
    @SerialName("business_email") val businessEmail: String? = null,
    @SerialName("business_upi") val businessUpi: String? = null,
)
