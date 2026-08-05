package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    @SerialName("whatsapp_eligible") val whatsappEligible: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CustomerCreateDto(
    val name: String,
    val phone: String? = null,
)

@Serializable
data class CustomerLookupDto(
    val found: Boolean = false,
    val name: String? = null,
    @SerialName("visit_count") val visitCount: Int = 0,
)
