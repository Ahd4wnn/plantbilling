package com.plantora.billing.domain

data class Customer(
    val id: String,
    val name: String,
    val phone: String?,
    val whatsappEligible: Boolean,
    val createdAt: String,
)
