package com.plantora.billing.domain

data class Product(
    val id: String,
    val name: String,
    val category: String?,
    val retailPrice: Money,
    /** Absolute, ready-to-load image URL (or null). */
    val photoUrl: String?,
    val isActive: Boolean,
)
