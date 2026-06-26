package com.plantora.billing.data

import com.plantora.billing.data.remote.api.BillsApi
import com.plantora.billing.data.remote.dto.BillCreateDto
import com.plantora.billing.data.remote.dto.BillItemInDto
import com.plantora.billing.data.remote.dto.NewCustomerDto
import com.plantora.billing.domain.Bill
import com.plantora.billing.domain.BillDetail
import com.plantora.billing.domain.BillPage
import com.plantora.billing.domain.DaySummary
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import javax.inject.Inject
import javax.inject.Singleton

/** Parameters for a checkout. Money is sent as wire strings; the server is the
 *  authority on the final totals. */
data class CheckoutRequest(
    val idempotencyKey: String,
    val items: List<CheckoutItem>,
    val discountType: DiscountType,
    val discountValue: Money,
    val cashAmount: Money,
    val upiAmount: Money,
    val dueAmount: Money,
    val remarks: String?,
    val customerName: String?,
    val customerPhone: String?,
)

data class CheckoutItem(
    val productId: String,
    val quantity: Int,
    val unitPrice: Money,
)

@Singleton
class BillRepository @Inject constructor(
    private val api: BillsApi,
) {
    suspend fun checkout(req: CheckoutRequest): Bill {
        val customer = req.customerName?.takeIf { it.isNotBlank() }?.let {
            NewCustomerDto(name = it.trim(), phone = req.customerPhone?.takeIf { p -> p.isNotBlank() }?.trim())
        }
        val dto = BillCreateDto(
            idempotencyKey = req.idempotencyKey,
            items = req.items.map {
                BillItemInDto(it.productId, it.quantity, it.unitPrice.toWire())
            },
            discountType = req.discountType.wire,
            discountValue = req.discountValue.toWire(),
            cashAmount = req.cashAmount.toWire(),
            upiAmount = req.upiAmount.toWire(),
            dueAmount = req.dueAmount.toWire(),
            remarks = req.remarks?.takeIf { it.isNotBlank() }?.trim(),
            newCustomer = customer,
        )
        return api.create(dto).toDomain()
    }

    suspend fun detail(id: String): BillDetail = api.detail(id).toDomain()

    suspend fun summary(date: String? = null, createdBy: String? = null): DaySummary =
        api.summary(date, createdBy).toDomain()

    suspend fun list(date: String?, createdBy: String? = null, limit: Int = 20, offset: Int = 0): BillPage {
        val dto = api.list(dateFrom = date, dateTo = date, createdBy = createdBy, limit = limit, offset = offset)
        return BillPage(
            items = dto.items.map { it.toDomain() },
            offset = dto.offset,
            hasMore = dto.hasMore,
        )
    }

    suspend fun listByCustomer(customerId: String, limit: Int = 100, offset: Int = 0): BillPage {
        val dto = api.list(customerId = customerId, limit = limit, offset = offset)
        return BillPage(
            items = dto.items.map { it.toDomain() },
            offset = dto.offset,
            hasMore = dto.hasMore,
        )
    }

    suspend fun delete(id: String) = api.delete(id)
}
