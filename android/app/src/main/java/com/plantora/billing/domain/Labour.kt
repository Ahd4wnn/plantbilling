package com.plantora.billing.domain

/** A worker on the shop's labour roster. */
data class Labourer(
    val id: String,
    val name: String,
    val phone: String?,
    val gender: String,          // "male" | "female"
    val defaultWage: Money,
    val overtimeRate: Money,     // per hour
    val isActive: Boolean,
    val totalPaid: Money,
    val outstandingDue: Money,
    val createdAt: String,
)

/** A recorded payment to a worker. */
data class LabourPayment(
    val id: String,
    val labourerId: String?,
    val labourerName: String,
    val gender: String,
    val kind: String,            // "wage" | "due_clear"
    val wageAmount: Money,
    val overtimeHours: String,   // kept as text — may be fractional (e.g. "1.5")
    val overtimeRate: Money,
    val overtimeAmount: Money,
    val totalAmount: Money,
    val cashAmount: Money,
    val upiAmount: Money,
    val dueAmount: Money,
    val paymentMethod: PaymentMethod,
    val note: String?,
    val recordedByEmail: String?,
    val createdAt: String,
)

/** A worker's attendance for a day. */
data class Attendance(
    val id: String,
    val labourerId: String,
    val labourerName: String,
    val day: String,
    val status: String,          // "present" | "absent" | "half_day"
    val overtimeHours: String,
    val createdAt: String,
)
