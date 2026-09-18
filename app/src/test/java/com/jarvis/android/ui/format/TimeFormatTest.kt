package com.jarvis.android.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Presentation rules only — no transport, no contract. */
class TimeFormatTest {

    private val zone = TimeZone.getTimeZone("UTC")
    private val locale = Locale.US

    /** 2026-09-17 15:30:00 UTC. */
    private val now = utc(2026, Calendar.SEPTEMBER, 17, 15, 30)

    @Test
    fun today_isClockTime() {
        val stamp = TimeFormat.conversationStamp(utc(2026, Calendar.SEPTEMBER, 17, 9, 5), now, zone, locale)
        assertEquals("9:05\u202FAM", stamp)
    }

    @Test
    fun previousLocalDay_isYesterday() {
        val stamp = TimeFormat.conversationStamp(utc(2026, Calendar.SEPTEMBER, 16, 23, 50), now, zone, locale)
        assertEquals("Yesterday", stamp)
    }

    @Test
    fun justAfterMidnight_isStillToday() {
        val early = utc(2026, Calendar.SEPTEMBER, 17, 0, 10)
        val stamp = TimeFormat.conversationStamp(early, now, zone, locale)
        assertEquals("12:10\u202FAM", stamp)
    }

    @Test
    fun olderThanYesterday_isMediumDate() {
        val stamp = TimeFormat.conversationStamp(utc(2026, Calendar.SEPTEMBER, 14, 8, 0), now, zone, locale)
        assertEquals("Sep 14, 2026", stamp)
    }

    @Test
    fun futureTimestamp_fallsBackToClockTime() {
        val stamp = TimeFormat.conversationStamp(utc(2026, Calendar.SEPTEMBER, 18, 1, 0), now, zone, locale)
        assertEquals("1:00\u202FAM", stamp)
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            set(year, month, day, hour, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
