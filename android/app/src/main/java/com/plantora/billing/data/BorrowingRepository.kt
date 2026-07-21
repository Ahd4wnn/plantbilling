package com.plantora.billing.data

import com.plantora.billing.data.remote.api.BorrowingsApi
import com.plantora.billing.data.remote.dto.BorrowingCreateDto
import com.plantora.billing.data.remote.dto.BorrowingDto
import com.plantora.billing.data.remote.dto.BorrowingPayDto
import com.plantora.billing.domain.Borrowing
import com.plantora.billing.domain.BorrowingList
import com.plantora.billing.domain.Money
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BorrowingRepository @Inject constructor(
    private val api: BorrowingsApi,
) {
    suspend fun list(status: String = "all"): BorrowingList {
        val dto = api.list(status)
        return BorrowingList(
            items = dto.items.map { it.toDomain() },
            totalOutstanding = Money.parse(dto.totalOutstanding),
        )
    }

    suspend fun create(
        lenderName: String,
        lenderPhone: String?,
        amount: Money,
        cash: Money,
        upi: Money,
        remarks: String?,
    ): Borrowing = api.create(
        BorrowingCreateDto(
            lenderName = lenderName.trim(),
            lenderPhone = lenderPhone?.trim()?.ifBlank { null },
            amount = amount.toWire(),
            cashAmount = cash.toWire(),
            upiAmount = upi.toWire(),
            remarks = remarks?.trim()?.ifBlank { null },
        )
    ).toDomain()

    suspend fun pay(id: String, cash: Money, upi: Money): Borrowing =
        api.pay(id, BorrowingPayDto(paidCashAmount = cash.toWire(), paidUpiAmount = upi.toWire())).toDomain()

    suspend fun delete(id: String) = api.delete(id)
}

private fun BorrowingDto.toDomain() = Borrowing(
    id = id,
    lenderName = lenderName,
    lenderPhone = lenderPhone,
    amount = Money.parse(amount),
    method = method,
    remarks = remarks,
    isPaid = isPaid,
    paidMethod = paidMethod,
    outstanding = Money.parse(outstanding),
    paidAt = paidAt,
    createdAt = createdAt,
)
