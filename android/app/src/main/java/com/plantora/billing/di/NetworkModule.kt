package com.plantora.billing.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.plantora.billing.data.remote.AuthInterceptor
import com.plantora.billing.data.remote.HostSelectionInterceptor
import com.plantora.billing.data.remote.UnauthorizedInterceptor
import com.plantora.billing.data.remote.api.AuthApi
import com.plantora.billing.data.remote.api.BillsApi
import com.plantora.billing.data.remote.api.CustomersApi
import com.plantora.billing.data.remote.api.ExpensesApi
import com.plantora.billing.data.remote.api.ProductsApi
import com.plantora.billing.data.remote.api.SalespeopleApi
import com.plantora.billing.data.remote.api.ShopApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        hostSelection: HostSelectionInterceptor,
        auth: AuthInterceptor,
        unauthorized: UnauthorizedInterceptor,
        networkReporting: com.plantora.billing.data.remote.NetworkReportingInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(networkReporting)
            .addInterceptor(hostSelection)
            .addInterceptor(auth)
            .addInterceptor(unauthorized)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        // Placeholder base URL; HostSelectionInterceptor swaps it at runtime.
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideProductsApi(retrofit: Retrofit): ProductsApi = retrofit.create(ProductsApi::class.java)

    @Provides
    @Singleton
    fun provideBillsApi(retrofit: Retrofit): BillsApi = retrofit.create(BillsApi::class.java)

    @Provides
    @Singleton
    fun provideExpensesApi(retrofit: Retrofit): ExpensesApi = retrofit.create(ExpensesApi::class.java)

    @Provides
    @Singleton
    fun provideShopApi(retrofit: Retrofit): ShopApi = retrofit.create(ShopApi::class.java)

    @Provides
    @Singleton
    fun provideCustomersApi(retrofit: Retrofit): CustomersApi = retrofit.create(CustomersApi::class.java)

    @Provides
    @Singleton
    fun provideSalespeopleApi(retrofit: Retrofit): SalespeopleApi = retrofit.create(SalespeopleApi::class.java)

    @Provides
    @Singleton
    fun provideOwnerApi(retrofit: Retrofit): com.plantora.billing.data.remote.api.OwnerApi =
        retrofit.create(com.plantora.billing.data.remote.api.OwnerApi::class.java)
}
