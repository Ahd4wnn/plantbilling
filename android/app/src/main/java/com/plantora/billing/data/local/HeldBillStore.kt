package com.plantora.billing.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A single line of a held (parked) bill — captures everything needed to rebuild
 *  the cart line WITHOUT depending on the catalog still containing the product. */
@Serializable
data class HeldLine(
    val lineId: String,
    val productId: String,
    val productName: String,
    val productCategory: String? = null,
    val productPhotoUrl: String? = null,
    val productRetailPrice: String,
    val quantity: Int,
    val unitPrice: String,
)

/** A parked bill the salesperson can resume later. Stored on the device only —
 *  it is not sent to the server until the bill is actually saved (checked out). */
@Serializable
data class HeldBill(
    val id: String,
    val savedAt: Long,
    val label: String,
    val itemCount: Int,
    val total: String,
    val lines: List<HeldLine>,
    val discountType: String,
    val discountInput: String,
    val paymentMode: String,
    val cashInput: String,
    val dueInput: String,
    val customerName: String,
    val customerPhone: String,
    val remarks: String,
)

private val Context.heldBillsStore: DataStore<Preferences> by preferencesDataStore(name = "plantora_held_bills")

/**
 * Device-local storage for held/parked bills. A salesperson mid-bill can "hold"
 * the current cart to serve a ready customer, then resume it afterwards. Stored
 * as a JSON list in DataStore; survives process death and app restarts.
 */
@Singleton
class HeldBillStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.heldBillsStore
    private val json = Json { ignoreUnknownKeys = true }

    val heldBills: Flow<List<HeldBill>> = store.data.map { prefs ->
        val raw = prefs[KEY] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<HeldBill>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.savedAt }
    }

    /** Add (or replace by id) a held bill. */
    suspend fun save(bill: HeldBill) {
        store.edit { prefs ->
            val current = prefs[KEY]?.let { runCatching { json.decodeFromString<List<HeldBill>>(it) }.getOrNull() } ?: emptyList()
            val next = current.filterNot { it.id == bill.id } + bill
            prefs[KEY] = json.encodeToString(next)
        }
    }

    suspend fun remove(id: String) {
        store.edit { prefs ->
            val current = prefs[KEY]?.let { runCatching { json.decodeFromString<List<HeldBill>>(it) }.getOrNull() } ?: emptyList()
            prefs[KEY] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("held_bills_json")
    }
}
