package com.plantora.billing.domain

/** A worker on the shop's labour roster. */
data class Labourer(
    val id: String,
    val name: String,
    val phone: String?,
    val aadhaar: String?,        // optional Aadhaar number
    val gender: String,          // "male" | "female"
    val defaultWage: Money,      // wage per day
    val isActive: Boolean,
    val daysWorked: String,      // present + ½·half-day (from attendance)
    val totalPaid: Money,
    val earned: Money,           // wage_per_day × days_worked
    val balanceToPay: Money,     // earned − paid (negative = paid ahead / advance)
    val createdAt: String,
)

/** A recorded payment to a worker. */
data class LabourPayment(
    val id: String,
    val labourerId: String?,
    val labourerName: String,
    val gender: String,
    val kind: String,            // "wage" | "advance" | "due_clear"
    val wageAmount: Money,
    val days: String?,           // days this wage payment covers (wage kind only)
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
    val createdAt: String,
)
