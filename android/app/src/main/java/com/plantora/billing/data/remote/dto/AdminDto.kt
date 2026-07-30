package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Dashboard / overview (platform health — counts, never shop money) ─────────
@Serializable
data class AdminShopRowDto(
    @SerialName("shop_id") val shopId: String,
    @SerialName("shop_name") val shopName: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("owner_email") val ownerEmail: String? = null,
    @SerialName("owner_count") val ownerCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("bills_in_period") val billsInPeriod: Int = 0,
    @SerialName("staff_count") val staffCount: Int = 0,
    @SerialName("last_bill_at") val lastBillAt: String? = null,
)

@Serializable
data class AdminTrendPointDto(
    val date: String,
    val bills: Int = 0,
    @SerialName("new_shops") val newShops: Int = 0,
)

@Serializable
data class AdminAttentionDto(
    @SerialName("shop_id") val shopId: String,
    @SerialName("shop_name") val shopName: String,
    val kind: String,
    val detail: String,
)

@Serializable
data class AdminOverviewDto(
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("total_shops") val totalShops: Int = 0,
    @SerialName("active_shops") val activeShops: Int = 0,
    @SerialName("inactive_shops") val inactiveShops: Int = 0,
    @SerialName("new_shops") val newShops: Int = 0,
    @SerialName("total_staff") val totalStaff: Int = 0,
    @SerialName("total_owners") val totalOwners: Int = 0,
    @SerialName("total_bills") val totalBills: Int = 0,
    val shops: List<AdminShopRowDto> = emptyList(),
    val trend: List<AdminTrendPointDto> = emptyList(),
    val attention: List<AdminAttentionDto> = emptyList(),
)

@Serializable
data class AdminActivityDto(
    @SerialName("created_at") val createdAt: String,
    @SerialName("salesperson_email") val salespersonEmail: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
)

@Serializable
data class AdminShopDetailDto(
    @SerialName("shop_id") val shopId: String,
    @SerialName("shop_name") val shopName: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String,
    @SerialName("business_name") val businessName: String? = null,
    @SerialName("business_address") val businessAddress: String? = null,
    @SerialName("business_phone") val businessPhone: String? = null,
    @SerialName("business_email") val businessEmail: String? = null,
    @SerialName("business_upi") val businessUpi: String? = null,
    @SerialName("owner_email") val ownerEmail: String? = null,
    @SerialName("owner_emails") val ownerEmails: List<String> = emptyList(),
    @SerialName("staff_count") val staffCount: Int = 0,
    @SerialName("products_count") val productsCount: Int = 0,
    @SerialName("customers_count") val customersCount: Int = 0,
    @SerialName("bills_7") val bills7: Int = 0,
    @SerialName("bills_30") val bills30: Int = 0,
    @SerialName("last_bill_at") val lastBillAt: String? = null,
    @SerialName("recent_activity") val recentActivity: List<AdminActivityDto> = emptyList(),
)

// ── Notifications composer ────────────────────────────────────────────────────
@Serializable
data class AdminNotificationCreateDto(
    val title: String,
    val body: String,
    @SerialName("action_url") val actionUrl: String? = null,
    val target: String,                              // "all" | "shops"
    @SerialName("shop_ids") val shopIds: List<String> = emptyList(),
)

@Serializable
data class AdminNotificationDto(
    val id: String,
    val title: String,
    val body: String,
    @SerialName("action_url") val actionUrl: String? = null,
    val target: String,
    @SerialName("shop_count") val shopCount: Int = 0,
    @SerialName("read_count") val readCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AdminNotificationListDto(
    val items: List<AdminNotificationDto> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

// ── Platform own books: sales & expenses ──────────────────────────────────────
@Serializable
data class AdminSaleDto(
    val id: String,
    val title: String,
    val amount: String,
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("upi_amount") val upiAmount: String,
    @SerialName("due_amount") val dueAmount: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    val note: String? = null,
    @SerialName("occurred_on") val occurredOn: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AdminSaleListDto(
    val items: List<AdminSaleDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class AdminSaleCreateDto(
    val title: String,
    val amount: String,
    @SerialName("cash_amount") val cashAmount: String,
    @SerialName("upi_amount") val upiAmount: String,
    @SerialName("due_amount") val dueAmount: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    val note: String? = null,
    @SerialName("occurred_on") val occurredOn: String? = null,
)

@Serializable
data class AdminSaleUpdateDto(
    val title: String? = null,
    val amount: String? = null,
    @SerialName("cash_amount") val cashAmount: String? = null,
    @SerialName("upi_amount") val upiAmount: String? = null,
    @SerialName("due_amount") val dueAmount: String? = null,
    val note: String? = null,
)

@Serializable
data class AdminExpenseDto(
    val id: String,
    val reason: String,
    val amount: String,
    @SerialName("payment_method") val paymentMethod: String,
    val note: String? = null,
    @SerialName("occurred_on") val occurredOn: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AdminExpenseListDto(
    val items: List<AdminExpenseDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class AdminExpenseCreateDto(
    val reason: String,
    val amount: String,
    @SerialName("payment_method") val paymentMethod: String,
    val note: String? = null,
    @SerialName("occurred_on") val occurredOn: String? = null,
)

@Serializable
data class AdminExpenseUpdateDto(
    val reason: String? = null,
    val amount: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val note: String? = null,
)

@Serializable
data class LedgerTrendPointDto(
    val date: String,
    val sales: String,
    val expenses: String,
)

@Serializable
data class LedgerSummaryDto(
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    @SerialName("total_sales") val totalSales: String,
    @SerialName("sales_count") val salesCount: Int = 0,
    @SerialName("cash_collected") val cashCollected: String,
    @SerialName("upi_collected") val upiCollected: String,
    @SerialName("outstanding_due") val outstandingDue: String,
    @SerialName("total_expenses") val totalExpenses: String,
    @SerialName("expenses_count") val expensesCount: Int = 0,
    @SerialName("net_collected") val netCollected: String,
    val trend: List<LedgerTrendPointDto> = emptyList(),
)
