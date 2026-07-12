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

private fun LabourerDto.toDomain() = Labourer(
    id = id,
    name = name,
    phone = phone,
    gender = gender,
    defaultWage = Money.parse(defaultWage),
    overtimeRate = Money.parse(overtimeRate),
    isActive = isActive,
    totalPaid = Money.parse(totalPaid),
    outstandingDue = Money.parse(outstandingDue),
    createdAt = createdAt,
)

private fun LabourPaymentDto.toDomain() = LabourPayment(
    id = id,
    labourerId = labourerId,
    labourerName = labourerName,
    gender = gender,
    kind = kind,
    wageAmount = Money.parse(wageAmount),
    overtimeHours = overtimeHours,
    overtimeRate = Money.parse(overtimeRate),
    overtimeAmount = Money.parse(overtimeAmount),
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
    overtimeHours = overtimeHours,
    createdAt = createdAt,
)

@Singleton
class LabourRepository @Inject constructor(
    private val api: LabourApi,
) {
    suspend fun labourers(): List<Labourer> = api.listLabourers().map { it.toDomain() }

    suspend fun addLabourer(
        name: String, phone: String?, gender: String, defaultWage: Money, overtimeRate: Money,
    ): Labourer = api.createLabourer(
        LabourerCreateDto(
            name = name.trim(), phone = phone?.trim()?.ifBlank { null }, gender = gender,
            defaultWage = defaultWage.toWire(), overtimeRate = overtimeRate.toWire(),
        ),
    ).toDomain()

    suspend fun updateLabourer(
        id: String, name: String, phone: String?, gender: String, defaultWage: Money, overtimeRate: Money,
    ): Labourer = api.updateLabourer(
        id,
        LabourerUpdateDto(
            name = name.trim(), phone = phone?.trim() ?: "", gender = gender,
            defaultWage = defaultWage.toWire(), overtimeRate = overtimeRate.toWire(),
        ),
    ).toDomain()

    suspend fun deleteLabourer(id: String) = api.deleteLabourer(id)

    suspend fun payments(labourerId: String? = null): List<LabourPayment> =
        api.listPayments(labourerId = labourerId).map { it.toDomain() }

    suspend fun recordPayment(
        labourerId: String, wage: Money, overtimeHours: String,
        cash: Money, upi: Money, due: Money, note: String?,
    ): LabourPayment = api.createPayment(
        LabourPaymentCreateDto(
            labourerId = labourerId, wageAmount = wage.toWire(), overtimeHours = overtimeHours.ifBlank { "0" },
            cashAmount = cash.toWire(), upiAmount = upi.toWire(), dueAmount = due.toWire(),
            note = note?.trim()?.ifBlank { null },
        ),
    ).toDomain()

    suspend fun updatePayment(
        id: String, wage: Money, overtimeHours: String, cash: Money, upi: Money, due: Money, note: String?,
    ): LabourPayment = api.updatePayment(
        id,
        LabourPaymentUpdateDto(
            wageAmount = wage.toWire(), overtimeHours = overtimeHours.ifBlank { "0" },
            cashAmount = cash.toWire(), upiAmount = upi.toWire(), dueAmount = due.toWire(),
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

    suspend fun markAttendance(labourerId: String, day: String, status: String, overtimeHours: String): Attendance =
        api.markAttendance(
            AttendanceMarkDto(labourerId = labourerId, day = day, status = status, overtimeHours = overtimeHours.ifBlank { "0" }),
        ).toDomain()
}
