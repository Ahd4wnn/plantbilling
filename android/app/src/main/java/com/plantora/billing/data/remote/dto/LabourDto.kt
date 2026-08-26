package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Labourer ─────────────────────────────────────────────────────────────────
@Serializable
data class LabourerDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val aadhaar: String? = null,
    val gender: String,
    @SerialName("default_wage") val defaultWage: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("days_worked") val daysWorked: String = "0",
    @SerialName("total_paid") val totalPaid: String = "0",
    @SerialName("earned") val earned: String = "0",
    @SerialName("balance_to_pay") val balanceToPay: String = "0",
    @SerialName("joined_on") val joinedOn: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class LabourerCreateDto(
    val name: String,
    val phone: String? = null,
    val aadhaar: String? = null,
    val gender: String,
    @SerialName("default_wage") val defaultWage: String = "0",
    // Omitted (null) means "today" — the server fills it in IST.
    @SerialName("joined_on") val joinedOn: String? = null,
)

@Serializable
data class LabourerUpdateDto(
    val name: String? = null,
    val phone: String? = null,
    val aadhaar: String? = null,
    val gender: String? = null,
    @SerialName("default_wage") val defaultWage: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("joined_on") val joinedOn: String? = null,
)

// ── Payment ──────────────────────────────────────────────────────────────────
@Serializable
data class LabourPaymentDto(
    val id: String,
    @SerialName("labourer_id") val labourerId: String? = null,
    @SerialName("labourer_name") val labourerName: String,
    val gender: String,
    val kind: String = "wage",
    @SerialName("wage_amount") val wageAmount: String,
    val days: String? = null,
    @SerialName("total_amount") val totalAmount: String,
    @SerialName("cash_amount") val cashAmount: String = "0",
    @SerialName("upi_amount") val upiAmount: String = "0",
    @SerialName("due_amount") val dueAmount: String = "0",
    @SerialName("payment_method") val paymentMethod: String = "cash",
    val note: String? = null,
    @SerialName("recorded_by_email") val recordedByEmail: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class LabourPaymentCreateDto(
    @SerialName("labourer_id") val labourerId: String,
    val kind: String = "wage",
    @SerialName("wage_amount") val wageAmount: String = "0",
    val days: String? = null,
    @SerialName("cash_amount") val cashAmount: String = "0",
    @SerialName("upi_amount") val upiAmount: String = "0",
    @SerialName("due_amount") val dueAmount: String = "0",
    val note: String? = null,
)

@Serializable
data class LabourPaymentUpdateDto(
    @SerialName("wage_amount") val wageAmount: String? = null,
    val days: String? = null,
    @SerialName("cash_amount") val cashAmount: String? = null,
    @SerialName("upi_amount") val upiAmount: String? = null,
    @SerialName("due_amount") val dueAmount: String? = null,
    val note: String? = null,
)

@Serializable
data class LabourDueClearDto(
    @SerialName("labourer_id") val labourerId: String,
    @SerialName("cash_amount") val cashAmount: String = "0",
    @SerialName("upi_amount") val upiAmount: String = "0",
    val note: String? = null,
)

// ── Attendance ────────────────────────────────────────────────────────────────
@Serializable
data class AttendanceDto(
    val id: String,
    @SerialName("labourer_id") val labourerId: String,
    @SerialName("labourer_name") val labourerName: String,
    val day: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AttendanceMarkDto(
    @SerialName("labourer_id") val labourerId: String,
    val day: String,
    val status: String,
)
