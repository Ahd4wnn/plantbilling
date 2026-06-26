package com.plantora.billing.data

import com.plantora.billing.data.remote.api.ExpensesApi
import com.plantora.billing.data.remote.dto.ExpenseCreateDto
import com.plantora.billing.domain.Expense
import com.plantora.billing.domain.Money
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val api: ExpensesApi,
) {
    suspend fun add(amount: Money, reason: String): Expense =
        api.create(ExpenseCreateDto(amount = amount.toWire(), reason = reason.trim())).toDomain()

    suspend fun update(id: String, amount: Money, reason: String): Expense =
        api.update(id, ExpenseCreateDto(amount = amount.toWire(), reason = reason.trim())).toDomain()

    suspend fun delete(id: String) = api.delete(id)
}
