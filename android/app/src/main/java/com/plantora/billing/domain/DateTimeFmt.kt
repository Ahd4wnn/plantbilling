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

fun todayInShopZone(): LocalDate = LocalDate.now(SHOP_ZONE)

fun LocalDate.toApiDate(): String = format(apiDate)

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
