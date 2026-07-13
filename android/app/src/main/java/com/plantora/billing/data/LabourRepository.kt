package com.plantora.billing.data

import com.plantora.billing.data.remote.api.LabourApi
import com.plantora.billing.data.remote.dto.AttendanceDto
import com.plantora.billing.data.remote.dto.AttendanceMarkDto
import com.plantora.billing.data.remote.dto.LabourDueClearDto
import com.plantora.billing.data.remote.dto.LabourPaymentCreateDto
import com.plantora.billing.data.remote.dto.LabourPaymentDto
import com.plantora.billing.data.remote.dto.LabourPaymentUpdateDto
import com.plantora.billing.data.remote.dto.LabourerCreateDto
import com.plantora.billing.data.remote.dto.LabourerDto
import com.plantora.billing.data.remote.dto.LabourerUpdateDto
import com.plantora.billing.domain.Attendance
import com.plantora.billing.domain.Labourer
import com.plantora.billing.domain.LabourPayment
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.PaymentMethod
import javax.inject.Inject
import javax.inject.Singleton

internal fun LabourerDto.toDomain() = Labourer(
    id = id,
    name = name,
    phone = phone,
    aadhaar = aadhaar,
    gender = gender,
    defaultWage = Money.parse(defaultWage),
    isActive = isActive,
    daysWorked = daysWorked,
    totalPaid = Money.parse(totalPaid),
    earned = Money.parse(earned),
    balanceToPay = Money.parse(balanceToPay),
    createdAt = createdAt,
)

internal fun LabourPaymentDto.toDomain() = LabourPayment(
    id = id,
    labourerId = labourerId,
    labourerName = labourerName,
    gender = gender,
    kind = kind,
    wageAmount = Money.parse(wageAmount),
    days = days,
    totalAmount = Money.parse(totalAmount),
    cashAmount = Money.parse(cashAmount),
    upiAmount = Money.parse(upiAmount),
    dueAmount = Money.parse(dueAmount),
    paymentMethod = PaymentMethod.from(paymentMethod),
    note = note,
    recordedByEmail = recordedByEmail,
    createdAt = createdAt,
)

private fun AttendanceDto.toDomain() = Attendance(
    id = id,
    labourerId = labourerId,
    labourerName = labourerName,
    day = day,
    status = status,
    createdAt = createdAt,
)

@Singleton
class LabourRepository @Inject constructor(
    private val api: LabourApi,
) {
    suspend fun labourers(): List<Labourer> = api.listLabourers().map { it.toDomain() }

    suspend fun addLabourer(
        name: String, phone: String?, aadhaar: String?, gender: String, defaultWage: Money,
    ): Labourer = api.createLabourer(
        LabourerCreateDto(
            name = name.trim(), phone = phone?.trim()?.ifBlank { null }, aadhaar = aadhaar?.trim()?.ifBlank { null },
            gender = gender, defaultWage = defaultWage.toWire(),
        ),
    ).toDomain()

    suspend fun updateLabourer(
        id: String, name: String, phone: String?, aadhaar: String?, gender: String, defaultWage: Money,
    ): Labourer = api.updateLabourer(
        id,
        LabourerUpdateDto(
            name = name.trim(), phone = phone?.trim() ?: "", aadhaar = aadhaar?.trim() ?: "",
            gender = gender, defaultWage = defaultWage.toWire(),
        ),
    ).toDomain()

    suspend fun deleteLabourer(id: String) = api.deleteLabourer(id)

    suspend fun payments(labourerId: String? = null): List<LabourPayment> =
        api.listPayments(labourerId = labourerId).map { it.toDomain() }

    suspend fun recordPayment(
        labourerId: String, kind: String, amount: Money, days: String?,
        cash: Money, upi: Money, note: String?,
    ): LabourPayment = api.createPayment(
        LabourPaymentCreateDto(
            labourerId = labourerId, kind = kind, wageAmount = amount.toWire(),
            days = days?.ifBlank { null },
            cashAmount = cash.toWire(), upiAmount = upi.toWire(), dueAmount = "0",
            note = note?.trim()?.ifBlank { null },
        ),
    ).toDomain()

    suspend fun updatePayment(
        id: String, amount: Money, days: String?, cash: Money, upi: Money, note: String?,
    ): LabourPayment = api.updatePayment(
        id,
        LabourPaymentUpdateDto(
            wageAmount = amount.toWire(), days = days?.ifBlank { null },
            cashAmount = cash.toWire(), upiAmount = upi.toWire(), dueAmount = "0",
            note = note?.trim() ?: "",
        ),
    ).toDomain()

    suspend fun deletePayment(id: String) = api.deletePayment(id)

    suspend fun clearDue(labourerId: String, cash: Money, upi: Money, note: String?): LabourPayment =
        api.clearDue(
            LabourDueClearDto(
                labourerId = labourerId, cashAmount = cash.toWire(), upiAmount = upi.toWire(),
                note = note?.trim()?.ifBlank { null },
            ),
        ).toDomain()

    suspend fun attendance(day: String): List<Attendance> =
        api.listAttendance(day = day).map { it.toDomain() }

    suspend fun markAttendance(labourerId: String, day: String, status: String): Attendance =
        api.markAttendance(
            AttendanceMarkDto(labourerId = labourerId, day = day, status = status),
        ).toDomain()
}
