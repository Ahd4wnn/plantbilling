package com.plantora.billing.domain

/** A worker on the shop's labour roster. */
data class Labourer(
    val id: String,
    val name: String,
    val phone: String?,
    val aadhaar: String?,        // optional Aadhaar number
    val gender: String,          // "male" | "female"
    val wageType: String,        // "daily" | "monthly"
    val defaultWage: Money,      // wage per day    (daily workers)
    val monthlyWage: Money,      // wage per month  (monthly workers)
    val paidLeavesPerMonth: Int, // leaves allowed per month before pay is cut
    val isActive: Boolean,
    val daysWorked: String,      // present + ½·half-day (from attendance)
    val totalPaid: Money,        // every rupee handed over (wage + advance)
    // Daily:   wage_per_day × days_worked.
    // Monthly: salary, part months pro-rated at monthly_wage/30 per day, minus
    //          monthly_wage/30 per leave beyond that month's allowance.
    // Computed by the server only — never recalculate it on the device.
    val earned: Money,
    val balanceToPay: Money,     // earned − paid (negative = paid ahead / advance)
    val joinedOn: String?,       // yyyy-MM-dd; null only against a pre-0.1.40 backend
    val leavesThisMonth: String,        // this calendar month, absent=1 half=½
    val unpaidLeavesThisMonth: String,  // the part of those that cost the worker pay
    val createdAt: String,
)

/** True when this worker is on a monthly salary rather than a daily wage. */
val Labourer.isMonthly: Boolean get() = wageType == "monthly"

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
