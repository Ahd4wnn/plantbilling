package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.AdminExpenseCreateDto
import com.plantora.billing.data.remote.dto.AdminExpenseDto
import com.plantora.billing.data.remote.dto.AdminExpenseListDto
import com.plantora.billing.data.remote.dto.AdminNotificationCreateDto
import com.plantora.billing.data.remote.dto.AdminNotificationDto
import com.plantora.billing.data.remote.dto.AdminNotificationListDto
import com.plantora.billing.data.remote.dto.AdminOverviewDto
import com.plantora.billing.data.remote.dto.AdminExpenseUpdateDto
import com.plantora.billing.data.remote.dto.AdminSaleCreateDto
import com.plantora.billing.data.remote.dto.AdminSaleDto
import com.plantora.billing.data.remote.dto.AdminSaleListDto
import com.plantora.billing.data.remote.dto.AdminSaleUpdateDto
import com.plantora.billing.data.remote.dto.AdminShopDetailDto
import com.plantora.billing.data.remote.dto.LedgerSummaryDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Admin-only endpoints (require the admin JWT; enforced server-side by require_admin). */
interface AdminApi {
    // Dashboard
    @GET("/admin/overview")
    suspend fun overview(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): AdminOverviewDto

    @GET("/admin/shops/{id}/detail")
    suspend fun shopDetail(@Path("id") id: String): AdminShopDetailDto

    // Notifications composer
    @GET("/admin/notifications")
    suspend fun notifications(): AdminNotificationListDto

    @POST("/admin/notifications")
    suspend fun createNotification(@Body body: AdminNotificationCreateDto): AdminNotificationDto

    @DELETE("/admin/notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: String)

    // Platform books — summary + sales + expenses
    @GET("/admin/ledger/summary")
    suspend fun ledgerSummary(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): LedgerSummaryDto

    @GET("/admin/ledger/sales")
    suspend fun sales(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("limit") limit: Int = 100,
    ): AdminSaleListDto

    @POST("/admin/ledger/sales")
    suspend fun createSale(@Body body: AdminSaleCreateDto): AdminSaleDto

    @PATCH("/admin/ledger/sales/{id}")
    suspend fun updateSale(@Path("id") id: String, @Body body: AdminSaleUpdateDto): AdminSaleDto

    @DELETE("/admin/ledger/sales/{id}")
    suspend fun deleteSale(@Path("id") id: String)

    @GET("/admin/ledger/expenses")
    suspend fun expenses(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("limit") limit: Int = 100,
    ): AdminExpenseListDto

    @POST("/admin/ledger/expenses")
    suspend fun createExpense(@Body body: AdminExpenseCreateDto): AdminExpenseDto

    @PATCH("/admin/ledger/expenses/{id}")
    suspend fun updateExpense(@Path("id") id: String, @Body body: AdminExpenseUpdateDto): AdminExpenseDto

    @DELETE("/admin/ledger/expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String)
}
