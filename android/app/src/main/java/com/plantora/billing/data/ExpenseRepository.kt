package com.plantora.billing.data

import com.plantora.billing.data.remote.api.ExpensesApi
import com.plantora.billing.data.remote.dto.ExpenseCategoryCreateDto
import com.plantora.billing.data.remote.dto.ExpenseCreateDto
import com.plantora.billing.domain.Expense
import com.plantora.billing.domain.ExpenseCategory
import com.plantora.billing.domain.Money
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val api: ExpensesApi,
) {
    suspend fun add(amount: Money, categoryId: String, note: String?, paymentMethod: String): Expense =
        api.create(
            ExpenseCreateDto(
                amount = amount.toWire(),
                categoryId = categoryId,
                note = note?.trim()?.ifBlank { null },
                paymentMethod = paymentMethod,
            )
        ).toDomain()

    suspend fun update(id: String, amount: Money, categoryId: String, note: String?, paymentMethod: String): Expense =
        api.update(
            id,
            ExpenseCreateDto(
                amount = amount.toWire(),
                categoryId = categoryId,
                note = note?.trim()?.ifBlank { null },
                paymentMethod = paymentMethod,
            ),
        ).toDomain()

    suspend fun delete(id: String) = api.delete(id)

    // ── Categories ────────────────────────────────────────────────────────────
    suspend fun listCategories(): List<ExpenseCategory> =
        api.listCategories().map { ExpenseCategory(id = it.id, name = it.name) }

    suspend fun createCategory(name: String): ExpenseCategory =
        api.createCategory(ExpenseCategoryCreateDto(name = name.trim())).let {
            ExpenseCategory(id = it.id, name = it.name)
        }
}
