package com.plantora.billing.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Day boundaries follow the shop timezone (Asia/Kolkata), matching the backend. */
val SHOP_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

private val apiDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
// Printed receipts keep the year (a receipt is a record the customer may hold for
// months), e.g. "30 Jul 2026, 3:13 PM".
private val receiptFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
// A plain calendar date with the year, e.g. "12 Mar 2026" — for dates that aren't
// about "when today", like a worker's joining date.
private val plainDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun todayInShopZone(): LocalDate = LocalDate.now(SHOP_ZONE)

fun LocalDate.toApiDate(): String = format(apiDate)

/** Parse a server date (yyyy-MM-dd). Null if absent or unparsable. */
fun parseApiDate(raw: String?): LocalDate? =
    raw?.let { runCatching { LocalDate.parse(it, apiDate) }.getOrNull() }

/** Server date (yyyy-MM-dd) → "12 Mar 2026". Falls back to the raw text. */
fun formatPlainDate(raw: String?): String =
    parseApiDate(raw)?.format(plainDateFmt) ?: (raw ?: "")

fun LocalDate.toDisplay(): String = format(dayFmt)

/** The shop-zone calendar date (yyyy-MM-dd) for a server ISO datetime. */
fun billDateInShopZone(raw: String): LocalDate? {
    return try {
        runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(SHOP_ZONE).toLocalDate() }
            .getOrElse { LocalDateTime.parse(raw).toLocalDate() }
    } catch (e: Exception) {
        null
    }
}

/** Whole days between a server ISO datetime and today (shop zone). Null if unparsable. */
fun daysSince(raw: String): Long? {
    val date = billDateInShopZone(raw) ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(date, todayInShopZone())
}

/** Parse a server ISO datetime (with or without offset) and render in shop time. */
fun formatBillTime(raw: String): String {
    return try {
        val ldt = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(SHOP_ZONE).toLocalDateTime() }
            .getOrElse { LocalDateTime.parse(raw) }
        ldt.format(timeFmt)
    } catch (e: Exception) {
        raw
    }
}

/** Server ISO datetime → shop-time date+time with year, for printed receipts. */
fun formatReceiptDateTime(raw: String): String {
    return try {
        val ldt = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(SHOP_ZONE).toLocalDateTime() }
            .getOrElse { LocalDateTime.parse(raw) }
        ldt.format(receiptFmt)
    } catch (e: Exception) {
        raw
    }
}
