package com.plantora.billing.domain

data class Notification(
    val id: String,
    val title: String,
    val body: String,
    val actionUrl: String?,
    val read: Boolean,
    val createdAt: String,
) {
    /** Server ISO datetime rendered in shop time (Asia/Kolkata). */
    val displayTime: String get() = formatBillTime(createdAt)
}
