package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val dayMonthYearFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
        }
    }

    private val fullDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
        }
    }

    private val monthAbbrFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("MMM", Locale.FRENCH)
        }
    }

    fun formatDayMonthYear(timestampMs: Long): String {
        return dayMonthYearFormat.get()!!.format(Date(timestampMs))
    }

    fun formatFullDate(timestampMs: Long): String {
        return fullDateFormat.get()!!.format(Date(timestampMs))
    }

    fun formatMonthAbbreviation(timestampMs: Long): String {
        return monthAbbrFormat.get()!!.format(Date(timestampMs))
    }
}
