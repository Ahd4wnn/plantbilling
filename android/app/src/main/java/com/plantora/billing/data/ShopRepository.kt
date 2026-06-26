package com.plantora.billing.data

import com.plantora.billing.data.remote.api.ShopApi
import com.plantora.billing.data.remote.dto.ShopSettingsDto
import com.plantora.billing.data.remote.dto.ShopSettingsUpdateDto
import com.plantora.billing.domain.ShopSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShopRepository @Inject constructor(
    private val api: ShopApi,
) {
    suspend fun get(): ShopSettings = api.get().toDomain()

    suspend fun update(
        businessName: String?,
        businessAddress: String?,
        businessPhone: String?,
        businessEmail: String?,
        businessUpi: String?,
    ): ShopSettings = api.update(
        ShopSettingsUpdateDto(
            businessName = businessName?.trim(),
            businessAddress = businessAddress?.trim(),
            businessPhone = businessPhone?.trim(),
            businessEmail = businessEmail?.trim(),
            businessUpi = businessUpi?.trim(),
        ),
    ).toDomain()
}

private fun ShopSettingsDto.toDomain() = ShopSettings(
    name = name,
    businessName = businessName,
    businessAddress = businessAddress,
    businessPhone = businessPhone,
    businessEmail = businessEmail,
    businessUpi = businessUpi,
)
