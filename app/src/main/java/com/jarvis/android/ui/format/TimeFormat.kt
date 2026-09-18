package com.jarvis.android.ui.format

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Display formatting for chat timestamps. Pure and locale/zone-explicit so the
 * rules are testable without an Android runtime. Never touches transport or
 * contracts — this is presentation only.
 */
object TimeFormat {

    /** Clock time for a bubble footer. The list row already carries the day. */
    fun bubbleTime(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(epochMs))

    /**
     * Conversation-list stamp: clock time for today, "Yesterday" for the
     * previous local day, the medium date otherwise.
     */
    fun conversationStamp(
        epochMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val days = localDayIndex(nowMs, zone) - localDayIndex(epochMs, zone)
        return when {
            days <= 0 -> clock(locale, zone).format(Date(epochMs))
            days == 1 -> "Yesterday"
            else -> date(locale, zone).format(Date(epochMs))
        }
    }

    private fun clock(locale: Locale, zone: TimeZone): DateFormat =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = zone }

    private fun date(locale: Locale, zone: TimeZone): DateFormat =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = zone }

    /** Whole local days since the epoch, so DST shifts don't change the day count. */
    private fun localDayIndex(epochMs: Long, zone: TimeZone): Int {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis / 86_400_000L).toInt()
    }
}
