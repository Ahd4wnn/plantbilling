package com.plantora.billing.domain

data class ShopSettings(
    val name: String,
    val businessName: String?,
    val businessAddress: String?,
    val businessPhone: String?,
    val businessEmail: String?,
    val businessUpi: String?,
)
