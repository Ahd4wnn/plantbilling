package com.plantora.billing.data

import com.plantora.billing.data.remote.api.AdminApi
import com.plantora.billing.data.remote.dto.AdminActivityDto
import com.plantora.billing.data.remote.dto.AdminAttentionDto
import com.plantora.billing.data.remote.dto.AdminExpenseCreateDto
import com.plantora.billing.data.remote.dto.AdminExpenseDto
import com.plantora.billing.data.remote.dto.AdminExpenseUpdateDto
import com.plantora.billing.data.remote.dto.AdminNotificationCreateDto
import com.plantora.billing.data.remote.dto.AdminNotificationDto
import com.plantora.billing.data.remote.dto.AdminOverviewDto
import com.plantora.billing.data.remote.dto.AdminSaleCreateDto
import com.plantora.billing.data.remote.dto.AdminSaleDto
import com.plantora.billing.data.remote.dto.AdminSaleUpdateDto
import com.plantora.billing.data.remote.dto.AdminShopDetailDto
import com.plantora.billing.data.remote.dto.AdminShopRowDto
import com.plantora.billing.data.remote.dto.AdminTrendPointDto
import com.plantora.billing.data.remote.dto.LedgerSummaryDto
import com.plantora.billing.data.remote.dto.LedgerTrendPointDto
import com.plantora.billing.domain.AdminActivity
import com.plantora.billing.domain.AdminAttention
import com.plantora.billing.domain.AdminExpense
import com.plantora.billing.domain.AdminNotification
import com.plantora.billing.domain.AdminOverview
import com.plantora.billing.domain.AdminSale
import com.plantora.billing.domain.AdminShopDetail
import com.plantora.billing.domain.AdminShopRow
import com.plantora.billing.domain.AdminTrendPoint
import com.plantora.billing.domain.LedgerSummary
import com.plantora.billing.domain.LedgerTrendPoint
import com.plantora.billing.domain.Money
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val api: AdminApi,
) {
    // Dashboard
    suspend fun overview(dateFrom: String?, dateTo: String?): AdminOverview =
        api.overview(dateFrom, dateTo).toDomain()

    suspend fun shopDetail(shopId: String): AdminShopDetail = api.shopDetail(shopId).toDomain()

    // Notifications
    suspend fun notifications(): List<AdminNotification> = api.notifications().items.map { it.toDomain() }

    suspend fun sendNotification(title: String, body: String, shopIds: List<String>): AdminNotification {
        val target = if (shopIds.isEmpty()) "all" else "shops"
        return api.createNotification(
            AdminNotificationCreateDto(title = title.trim(), body = body.trim(), target = target, shopIds = shopIds),
        ).toDomain()
    }

    suspend fun deleteNotification(id: String) = api.deleteNotification(id)

    // Books
    suspend fun ledgerSummary(dateFrom: String?, dateTo: String?): LedgerSummary =
        api.ledgerSummary(dateFrom, dateTo).toDomain()

    suspend fun sales(dateFrom: String?, dateTo: String?): List<AdminSale> =
        api.sales(dateFrom, dateTo).items.map { it.toDomain() }

    suspend fun expenses(dateFrom: String?, dateTo: String?): List<AdminExpense> =
        api.expenses(dateFrom, dateTo).items.map { it.toDomain() }

    /** Record a platform sale. The full [amount] lands in the chosen bucket so the
     *  server invariant cash + upi + due == amount always holds. */
    suspend fun createSale(title: String, amount: Money, method: String, note: String?): AdminSale {
        val zero = Money.ZERO.toWire()
        val amt = amount.toWire()
        val body = AdminSaleCreateDto(
            title = title.trim(),
            amount = amt,
            cashAmount = if (method == "cash") amt else zero,
            upiAmount = if (method == "upi") amt else zero,
            dueAmount = if (method == "due") amt else zero,
            note = note?.trim()?.ifBlank { null },
        )
        return api.createSale(body).toDomain()
    }

    /** Edit a platform sale. Same single-bucket split as [createSale] so the
     *  server invariant cash + upi + due == amount holds after the change. */
    suspend fun updateSale(id: String, title: String, amount: Money, method: String, note: String?): AdminSale {
        val zero = Money.ZERO.toWire()
        val amt = amount.toWire()
        val body = AdminSaleUpdateDto(
            title = title.trim(),
            amount = amt,
            cashAmount = if (method == "cash") amt else zero,
            upiAmount = if (method == "upi") amt else zero,
            dueAmount = if (method == "due") amt else zero,
            note = note?.trim().orEmpty(),
        )
        return api.updateSale(id, body).toDomain()
    }

    suspend fun deleteSale(id: String) = api.deleteSale(id)

    suspend fun createExpense(reason: String, amount: Money, method: String, note: String?): AdminExpense {
        val body = AdminExpenseCreateDto(
            reason = reason.trim(),
            amount = amount.toWire(),
            paymentMethod = method,
            note = note?.trim()?.ifBlank { null },
        )
        return api.createExpense(body).toDomain()
    }

    suspend fun updateExpense(id: String, reason: String, amount: Money, method: String, note: String?): AdminExpense {
        val body = AdminExpenseUpdateDto(
            reason = reason.trim(),
            amount = amount.toWire(),
            paymentMethod = method,
            note = note?.trim().orEmpty(),
        )
        return api.updateExpense(id, body).toDomain()
    }

    suspend fun deleteExpense(id: String) = api.deleteExpense(id)
}

// ── DTO → domain ──────────────────────────────────────────────────────────────
private fun AdminShopRowDto.toDomain() = AdminShopRow(
    shopId = shopId, shopName = shopName, isActive = isActive, ownerEmail = ownerEmail,
    ownerCount = ownerCount, billsInPeriod = billsInPeriod, staffCount = staffCount, lastBillAt = lastBillAt,
)

private fun AdminTrendPointDto.toDomain() = AdminTrendPoint(date = date, bills = bills, newShops = newShops)

private fun AdminAttentionDto.toDomain() = AdminAttention(shopId = shopId, shopName = shopName, kind = kind, detail = detail)

private fun AdminOverviewDto.toDomain() = AdminOverview(
    totalShops = totalShops, activeShops = activeShops, inactiveShops = inactiveShops, newShops = newShops,
    totalStaff = totalStaff, totalOwners = totalOwners, totalBills = totalBills,
    shops = shops.map { it.toDomain() }, trend = trend.map { it.toDomain() }, attention = attention.map { it.toDomain() },
)

private fun AdminActivityDto.toDomain() = AdminActivity(createdAt = createdAt, salespersonEmail = salespersonEmail, itemCount = itemCount)

private fun AdminShopDetailDto.toDomain() = AdminShopDetail(
    shopId = shopId, shopName = shopName, isActive = isActive, createdAt = createdAt,
    businessName = businessName, businessAddress = businessAddress, businessPhone = businessPhone,
    businessEmail = businessEmail, businessUpi = businessUpi, ownerEmail = ownerEmail, ownerEmails = ownerEmails,
    staffCount = staffCount, productsCount = productsCount, customersCount = customersCount,
    bills7 = bills7, bills30 = bills30, lastBillAt = lastBillAt, recentActivity = recentActivity.map { it.toDomain() },
)

private fun AdminNotificationDto.toDomain() = AdminNotification(
    id = id, title = title, body = body, target = target, shopCount = shopCount, readCount = readCount, createdAt = createdAt,
)

private fun AdminSaleDto.toDomain() = AdminSale(
    id = id, title = title, amount = Money.parse(amount), cashAmount = Money.parse(cashAmount),
    upiAmount = Money.parse(upiAmount), dueAmount = Money.parse(dueAmount), customerName = customerName,
    customerPhone = customerPhone, note = note, occurredOn = occurredOn, createdAt = createdAt,
)

private fun AdminExpenseDto.toDomain() = AdminExpense(
    id = id, reason = reason, amount = Money.parse(amount), paymentMethod = paymentMethod,
    note = note, occurredOn = occurredOn, createdAt = createdAt,
)

private fun LedgerTrendPointDto.toDomain() = LedgerTrendPoint(date = date, sales = Money.parse(sales), expenses = Money.parse(expenses))

private fun LedgerSummaryDto.toDomain() = LedgerSummary(
    totalSales = Money.parse(totalSales), salesCount = salesCount, cashCollected = Money.parse(cashCollected),
    upiCollected = Money.parse(upiCollected), outstandingDue = Money.parse(outstandingDue),
    totalExpenses = Money.parse(totalExpenses), expensesCount = expensesCount, netCollected = Money.parse(netCollected),
    trend = trend.map { it.toDomain() },
)
