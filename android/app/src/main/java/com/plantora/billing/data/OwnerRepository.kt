package com.plantora.billing.data

import com.plantora.billing.data.remote.api.OwnerApi
import com.plantora.billing.data.remote.dto.OwnerOverviewDto
import com.plantora.billing.data.remote.dto.OwnerShopDto
import com.plantora.billing.data.remote.dto.OwnerShopUpdateDto
import com.plantora.billing.data.remote.dto.OwnerStaffCreateDto
import com.plantora.billing.data.remote.dto.OwnerStaffDto
import com.plantora.billing.data.remote.dto.ShopOverviewRowDto
import com.plantora.billing.data.remote.dto.StaffPerfDto
import com.plantora.billing.domain.DetailedReport
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.OwnerOverview
import com.plantora.billing.domain.OwnerShop
import com.plantora.billing.domain.OwnerStaff
import com.plantora.billing.domain.ShopOverviewRow
import com.plantora.billing.domain.StaffPerf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OwnerRepository @Inject constructor(
    private val api: OwnerApi,
) {
    suspend fun shops(): List<OwnerShop> = api.shops().map { it.toDomain() }

    suspend fun overview(dateFrom: String?, dateTo: String?): OwnerOverview =
        api.overview(dateFrom, dateTo).toDomain()

    suspend fun report(shopId: String, dateFrom: String?, dateTo: String?): DetailedReport =
        api.report(shopId, dateFrom, dateTo).toDomain()

    suspend fun staff(shopId: String): List<OwnerStaff> = api.staff(shopId).map { it.toDomain() }

    suspend fun createStaff(shopId: String, email: String, password: String, role: String): OwnerStaff =
        api.createStaff(shopId, OwnerStaffCreateDto(email.trim(), password, role)).toDomain()

    suspend fun resetStaffPassword(shopId: String, userId: String, newPassword: String) =
        api.resetStaffPassword(shopId, userId, mapOf("new_password" to newPassword))

    suspend fun deleteStaff(shopId: String, userId: String) = api.deleteStaff(shopId, userId)

    suspend fun updateShop(
        shopId: String,
        businessName: String?,
        businessAddress: String?,
        businessPhone: String?,
        businessEmail: String?,
        businessUpi: String?,
    ): OwnerShop = api.updateShop(
        shopId,
        OwnerShopUpdateDto(businessName, businessAddress, businessPhone, businessEmail, businessUpi),
    ).toDomain()
}

private fun OwnerShopDto.toDomain() = OwnerShop(
    id = id, name = name, isActive = isActive,
    businessName = businessName, businessAddress = businessAddress,
    businessPhone = businessPhone, businessEmail = businessEmail, businessUpi = businessUpi,
)

private fun ShopOverviewRowDto.toDomain() = ShopOverviewRow(
    shopId = shopId, shopName = shopName, totalSales = Money.parse(totalSales), billCount = billCount,
    cashTotal = Money.parse(cashTotal), upiTotal = Money.parse(upiTotal), dueTotal = Money.parse(dueTotal),
    totalExpenses = Money.parse(totalExpenses), netSales = Money.parse(netSales),
)

private fun StaffPerfDto.toDomain() = StaffPerf(
    userId = userId, email = email, shopId = shopId, shopName = shopName, role = role,
    totalSales = Money.parse(totalSales), billCount = billCount,
)

private fun OwnerOverviewDto.toDomain() = OwnerOverview(
    startDate = startDate, endDate = endDate, shopCount = shopCount,
    totalSales = Money.parse(totalSales), billCount = billCount, cashTotal = Money.parse(cashTotal),
    upiTotal = Money.parse(upiTotal), dueTotal = Money.parse(dueTotal), totalExpenses = Money.parse(totalExpenses),
    netSales = Money.parse(netSales), shops = shops.map { it.toDomain() }, staff = staff.map { it.toDomain() },
)

private fun OwnerStaffDto.toDomain() = OwnerStaff(
    id = id, email = email, role = role, isActive = isActive, shopId = shopId,
)
