package com.jarvis.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class ConversationTimeTest {

    private val zone = ZoneId.of("America/Chicago")
    private val now = ms(2026, 9, 22, 23, 50)

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun label(timeMs: Long) = ConversationTime.label(timeMs, now, zone, Locale.US)

    @Test
    fun todayShowsTheTime() {
        assertEquals("11:35 PM", label(ms(2026, 9, 22, 23, 35)).replace('\u202F', ' '))
    }

    @Test
    fun previousCalendarDayIsYesterdayEvenWithinTwentyFourHours() {
        assertEquals("Yesterday", label(ms(2026, 9, 21, 23, 55)))
    }

    @Test
    fun withinAWeekShowsTheWeekday() {
        assertEquals("Friday", label(ms(2026, 9, 18, 10, 0)))
    }

    @Test
    fun olderShowsTheDate() {
        assertEquals("Sep 1, 2026", label(ms(2026, 9, 1, 10, 0)))
    }
}
