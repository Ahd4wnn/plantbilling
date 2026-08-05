package com.plantora.billing.domain

data class Customer(
    val id: String,
    val name: String,
    val phone: String?,
    val whatsappEligible: Boolean,
    val createdAt: String,
)

/** Returning-customer hint for the billing phone field (this shop only). */
data class CustomerLookup(
    val found: Boolean,
    val name: String?,
    val visitCount: Int,
)
