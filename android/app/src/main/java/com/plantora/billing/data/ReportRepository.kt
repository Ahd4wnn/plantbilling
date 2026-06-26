package com.plantora.billing.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.plantora.billing.data.remote.api.BillsApi
import com.plantora.billing.domain.DetailedReport
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.TrendPoint
import com.plantora.billing.domain.billDateInShopZone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val api: BillsApi,
    @ApplicationContext private val context: Context,
) {
    /** Generate the aggregate report and, for multi-day ranges, a daily sales trend. */
    suspend fun generate(dateFrom: String, dateTo: String, createdBy: String?): DetailedReport {
        val report = api.report(dateFrom, dateTo, createdBy).toDomain()
        if (dateFrom == dateTo) return report
        val trend = buildTrend(dateFrom, dateTo, createdBy)
        return report.copy(trend = trend)
    }

    /** Bucket bills in [from,to] into per-day totals (shop timezone). Capped fetch. */
    private suspend fun buildTrend(from: String, to: String, createdBy: String?): List<TrendPoint> {
        val totals = linkedMapOf<String, Money>()
        // Seed every day in range with zero so the line is continuous.
        runCatching {
            var d = LocalDate.parse(from)
            val end = LocalDate.parse(to)
            while (!d.isAfter(end)) {
                totals[d.toString()] = Money.ZERO
                d = d.plusDays(1)
            }
        }
        var offset = 0
        val pageSize = 100
        var pages = 0
        while (pages < 12) {
            val page = api.list(dateFrom = from, dateTo = to, createdBy = createdBy, limit = pageSize, offset = offset)
            page.items.forEach { item ->
                val day = billDateInShopZone(item.createdAt)?.toString() ?: return@forEach
                val cur = totals[day] ?: Money.ZERO
                totals[day] = cur + Money.parse(item.total)
            }
            if (!page.hasMore) break
            offset += pageSize
            pages++
        }
        return totals.entries.map { TrendPoint(it.key, it.value) }
    }

    /** Save the server CSV to the public Downloads folder. Returns the file name. */
    suspend fun downloadCsv(dateFrom: String, dateTo: String, createdBy: String?): String = withContext(Dispatchers.IO) {
        val body = api.downloadReport(dateFrom, dateTo, createdBy)
        val fileName = "plantora_report_${dateFrom}_to_${dateTo}.csv"
        val bytes = body.bytes()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Couldn't create the download file.")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeBytes(bytes)
        }
        fileName
    }
}
