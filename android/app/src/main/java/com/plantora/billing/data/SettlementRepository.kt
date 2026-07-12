package com.plantora.billing.data

import com.plantora.billing.data.remote.api.SettlementsApi
import com.plantora.billing.data.remote.dto.SettlementCreateDto
import com.plantora.billing.data.remote.dto.SettlementDto
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.PendingSettlement
import javax.inject.Inject
import javax.inject.Singleton

private fun SettlementDto.toDomain() = PendingSettlement(
    id = id,
    billId = billId,
    cashAmount = Money.parse(cashAmount),
    upiAmount = Money.parse(upiAmount),
    billTotal = Money.parse(billTotal),
    customerName = customerName,
    customerPhone = customerPhone,
    requestedByEmail = requestedByEmail,
    createdAt = createdAt,
)

@Singleton
class SettlementRepository @Inject constructor(
    private val api: SettlementsApi,
) {
    /**
     * Collect a due, split across cash + UPI. Returns the resulting status:
     * "approved" (a manager applied it immediately) or "pending" (a salesperson's
     * request now waiting for a manager to approve).
     */
    suspend fun collect(billId: String, cash: Money, upi: Money): String =
        api.create(
            SettlementCreateDto(billId = billId, cashAmount = cash.toWire(), upiAmount = upi.toWire())
        ).status

    suspend fun pending(): List<PendingSettlement> = api.pending().items.map { it.toDomain() }

    suspend fun approve(id: String) { api.approve(id) }

    suspend fun reject(id: String) { api.reject(id) }
}
