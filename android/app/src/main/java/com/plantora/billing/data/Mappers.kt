package com.plantora.billing.data

import com.plantora.billing.data.remote.dto.BillDetailDto
import com.plantora.billing.data.remote.dto.BillItemOutDto
import com.plantora.billing.data.remote.dto.BillListItemDto
import com.plantora.billing.data.remote.dto.BillOutDto
import com.plantora.billing.data.remote.dto.BillSummaryDto
import com.plantora.billing.data.remote.dto.ExpenseDto
import com.plantora.billing.data.remote.dto.DetailedReportDto
import com.plantora.billing.data.remote.dto.ProductDto
import com.plantora.billing.domain.Bill
import com.plantora.billing.domain.CategorySales
import com.plantora.billing.domain.DetailedReport
import com.plantora.billing.domain.ProductSales
import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.BillItem
import com.plantora.billing.domain.BillListEntry
import com.plantora.billing.domain.DaySummary
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Expense
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.PaymentMethod
import com.plantora.billing.domain.Product

/** Resolve a possibly-relative photo URL to an absolute one (mirrors web getMediaUrl). */
fun resolveMediaUrl(raw: String?, baseUrl: String): String? {
    if (raw.isNullOrBlank()) return null
    if (raw.startsWith("http://") || raw.startsWith("https://") ||
        raw.startsWith("data:") || raw.startsWith("blob:")
    ) return raw
    return baseUrl.trimEnd('/') + "/" + raw.trimStart('/')
}

fun ProductDto.toDomain(baseUrl: String) = Product(
    id = id,
    name = name,
    category = category,
    retailPrice = Money.parse(retailPrice),
    photoUrl = resolveMediaUrl(photoUrl, baseUrl),
    isActive = isActive,
)

private fun BillItemOutDto.toDomain() = BillItem(
    productId = productId,
    productName = productName,
    unitPrice = Money.parse(unitPrice),
    quantity = quantity,
    lineTotal = Money.parse(lineTotal),
)

fun BillOutDto.toDomain() = Bill(
    id = id,
    subtotal = Money.parse(subtotal),
    discountType = DiscountType.from(discountType),
    discountValue = Money.parse(discountValue),
    discountAmount = Money.parse(discountAmount),
    total = Money.parse(total),
    cashAmount = Money.parse(cashAmount),
    upiAmount = Money.parse(upiAmount),
    dueAmount = Money.parse(dueAmount),
    customerName = customerName,
    createdAt = createdAt,
    items = items.map { it.toDomain() },
    idempotentReplay = idempotentReplay,
)

fun ExpenseDto.toDomain() = Expense(
    id = id,
    amount = Money.parse(amount),
    reason = reason,
    createdAt = createdAt,
)

fun BillSummaryDto.toDomain() = DaySummary(
    date = date,
    totalSales = Money.parse(totalSales),
    billCount = billCount,
    cashTotal = Money.parse(cashTotal),
    upiTotal = Money.parse(upiTotal),
    dueTotal = Money.parse(dueTotal),
    totalExpenses = Money.parse(totalExpenses),
    netSales = Money.parse(netSales),
    expenses = expenses.map { it.toDomain() },
)

fun BillListItemDto.toDomain() = BillListEntry(
    id = id,
    createdAt = createdAt,
    total = Money.parse(total),
    dueAmount = Money.parse(dueAmount),
    customerName = customerName,
    itemCount = itemCount,
    paymentMethod = PaymentMethod.from(paymentMethod),
    isEdited = isEdited,
)

fun DetailedReportDto.toDomain() = DetailedReport(
    startDate = startDate,
    endDate = endDate,
    totalSales = Money.parse(totalSales),
    billCount = billCount,
    cashTotal = Money.parse(cashTotal),
    upiTotal = Money.parse(upiTotal),
    dueTotal = Money.parse(dueTotal),
    averageBillValue = Money.parse(averageBillValue),
    totalExpenses = Money.parse(totalExpenses),
    netSales = Money.parse(netSales),
    expenses = expenses.map { it.toDomain() },
    categories = categories.map {
        CategorySales(category = it.category?.takeIf { c -> c.isNotBlank() } ?: "Uncategorised", quantity = it.quantity, totalSales = Money.parse(it.totalSales))
    },
    topProducts = topProducts.map {
        ProductSales(productName = it.productName, quantity = it.quantity, totalSales = Money.parse(it.totalSales))
    },
)

fun BillDetailDto.toDomain() = BillDetail(
    id = id,
    shopName = shopName,
    businessName = businessName,
    businessAddress = businessAddress,
    businessPhone = businessPhone,
    subtotal = Money.parse(subtotal),
    discountType = DiscountType.from(discountType),
    discountValue = Money.parse(discountValue),
    discountAmount = Money.parse(discountAmount),
    total = Money.parse(total),
    cashAmount = Money.parse(cashAmount),
    upiAmount = Money.parse(upiAmount),
    dueAmount = Money.parse(dueAmount),
    paymentMethod = PaymentMethod.from(paymentMethod),
    customerName = customerName,
    customerPhone = customerPhone,
    salespersonEmail = salespersonEmail,
    remarks = remarks,
    isEdited = isEdited,
    createdAt = createdAt,
    items = items.map { it.toDomain() },
)
