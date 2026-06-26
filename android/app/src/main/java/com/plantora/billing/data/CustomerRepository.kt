package com.plantora.billing.data

import com.plantora.billing.data.remote.api.CustomersApi
import com.plantora.billing.data.remote.dto.CustomerCreateDto
import com.plantora.billing.data.remote.dto.CustomerDto
import com.plantora.billing.domain.Customer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    private val api: CustomersApi,
) {
    suspend fun list(): List<Customer> = api.list().map { it.toDomain() }

    suspend fun create(name: String, phone: String?): Customer =
        api.create(CustomerCreateDto(name.trim(), phone?.takeIf { it.isNotBlank() }?.trim())).toDomain()
}

private fun CustomerDto.toDomain() = Customer(
    id = id,
    name = name,
    phone = phone,
    whatsappEligible = whatsappEligible,
    createdAt = createdAt,
)
