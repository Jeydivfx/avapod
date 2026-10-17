package com.avapod.app.utils

import java.util.Locale

object StringUtils {
    fun normalizeSearch(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace(Regex("[\\s\\u200C\\u200D\\u0640\\u064B-\\u065F]+"), "")
        .lowercase(Locale.ROOT)

    fun matchesSearch(value: String?, query: String): Boolean =
        value != null && normalizeSearch(value).contains(normalizeSearch(query))


    fun toPersianNumber(input: String?): String {
        if (input == null) return ""
        val persianDigits = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
        return input.map {
            if (it.digitToIntOrNull() != null) persianDigits[it.digitToInt()] else it
        }.joinToString("")
    }

    fun formatTime(value: String?): String {
        if (value.isNullOrEmpty() || value == "0") return toPersianNumber("۰۰:۰۰")

        if (value.contains(":")) {
            return toPersianNumber(value)
        }

        val totalSeconds = (value.toLongOrNull() ?: 0L).coerceAtLeast(0)

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val result = if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

        return toPersianNumber(result)
    }

    fun timeStringToMs(time: String?): Long {
        val parts = time?.trim()?.split(":") ?: return 0L
        if (parts.size !in 1..3) return 0L
        var seconds = 0L
        for ((index, part) in parts.withIndex()) {
            val value = part.toLongOrNull() ?: return 0L
            if (value < 0 || (index > 0 && value >= 60)) return 0L
            if (seconds > (Long.MAX_VALUE / 1000 - value) / 60 || value > Long.MAX_VALUE / 1000) return 0L
            seconds = seconds * 60 + value
        }
        return seconds * 1000
    }
}
