package com.jarvis.android.ui.chat

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** When a conversation was last active, as a list row shows it. */
object ConversationTime {

    fun label(
        timeMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val then = Instant.ofEpochMilli(timeMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        return when {
            days <= 0L -> then.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            days == 1L -> "Yesterday"
            days < 7L -> then.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            else -> then.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }
}
