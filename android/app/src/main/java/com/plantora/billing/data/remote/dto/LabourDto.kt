package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Labourer ─────────────────────────────────────────────────────────────────
@Serializable
data class LabourerDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val gender: String,
    @SerialName("default_wage") val defaultWage: String,
    @SerialName("overtime_rate") val overtimeRate: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class LabourerCreateDto(
    val name: String,
    val phone: String? = null,
    val gender: String,
    @SerialName("default_wage") val defaultWage: String = "0",
    @SerialName("overtime_rate") val overtimeRate: String = "0",
)

@Serializable
data class LabourerUpdateDto(
    val name: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    @SerialName("default_wage") val defaultWage: String? = null,
    @SerialName("overtime_rate") val overtimeRate: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

// ── Payment ──────────────────────────────────────────────────────────────────
@Serializable
data class LabourPaymentDto(
    val id: String,
    @SerialName("labourer_id") val labourerId: String? = null,
    @SerialName("labourer_name") val labourerName: String,
    val gender: String,
    @SerialName("wage_amount") val wageAmount: String,
    @SerialName("overtime_hours") val overtimeHours: String,
    @SerialName("overtime_rate") val overtimeRate: String,
    @SerialName("overtime_amount") val overtimeAmount: String,
    @SerialName("total_amount") val totalAmount: String,
    val note: String? = null,
    @SerialName("recorded_by_email") val recordedByEmail: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class LabourPaymentCreateDto(
    @SerialName("labourer_id") val labourerId: String,
    @SerialName("wage_amount") val wageAmount: String = "0",
    @SerialName("overtime_hours") val overtimeHours: String = "0",
    val note: String? = null,
)

@Serializable
data class LabourPaymentUpdateDto(
    @SerialName("wage_amount") val wageAmount: String? = null,
    @SerialName("overtime_hours") val overtimeHours: String? = null,
    val note: String? = null,
)
