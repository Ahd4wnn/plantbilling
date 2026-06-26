package com.plantora.billing.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class ReportPeriod { DAILY, WEEKLY, MONTHLY, CUSTOM }

/** Monday→Sunday week containing [date] (mirrors web getWeekBounds). */
fun weekBounds(date: LocalDate): Pair<LocalDate, LocalDate> {
    val monday = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return monday to monday.plusDays(6)
}

/** First→last day of the month of [date]. */
fun monthBounds(date: LocalDate): Pair<LocalDate, LocalDate> {
    val ym = YearMonth.from(date)
    return ym.atDay(1) to ym.atEndOfMonth()
}
