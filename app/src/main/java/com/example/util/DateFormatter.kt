package com.example.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatter {
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val dayMonthYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)

    private val fullDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH)

    private val monthAbbrFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM", Locale.FRENCH)

    fun formatDayMonthYear(timestampMs: Long): String {
        return dayMonthYearFormatter.format(Instant.ofEpochMilli(timestampMs).atZone(zoneId))
    }

    fun formatFullDate(timestampMs: Long): String {
        return fullDateFormatter.format(Instant.ofEpochMilli(timestampMs).atZone(zoneId))
    }

    fun formatMonthAbbreviation(timestampMs: Long): String {
        return monthAbbrFormatter.format(Instant.ofEpochMilli(timestampMs).atZone(zoneId))
    }
}
