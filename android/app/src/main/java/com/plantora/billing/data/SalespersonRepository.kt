package com.plantora.billing.data

import com.plantora.billing.data.remote.api.SalespeopleApi
import com.plantora.billing.data.remote.dto.SalespersonActivateDto
import com.plantora.billing.data.remote.dto.SalespersonCreateDto
import com.plantora.billing.data.remote.dto.SalespersonDto
import com.plantora.billing.data.remote.dto.SalespersonResetPasswordDto
import com.plantora.billing.domain.Salesperson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalespersonRepository @Inject constructor(
    private val api: SalespeopleApi,
) {
    suspend fun list(): List<Salesperson> = api.list().map { it.toDomain() }

    suspend fun create(email: String, password: String): Salesperson =
        api.create(SalespersonCreateDto(email.trim(), password)).toDomain()

    suspend fun setActive(id: String, active: Boolean): Salesperson =
        api.setActive(id, SalespersonActivateDto(active)).toDomain()

    suspend fun resetPassword(id: String, newPassword: String): Salesperson =
        api.resetPassword(id, SalespersonResetPasswordDto(newPassword)).toDomain()

    suspend fun delete(id: String) = api.delete(id)
}

private fun SalespersonDto.toDomain() = Salesperson(
    id = id,
    email = email,
    isActive = isActive,
)
