package com.avapod.app.utils

import java.util.Locale

object StringUtils {


    fun toPersianNumber(input: String?): String {
        if (input == null) return ""
        val persianDigits = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
        return input.map {
            if (it.isDigit()) persianDigits[it.toString().toInt()] else it
        }.joinToString("")
    }


    fun formatTime(value: String?): String {
        if (value.isNullOrEmpty() || value == "0") return toPersianNumber("00:00")

        if (value.contains(":")) {
            return toPersianNumber(value)
        }

        val millis = value.toLongOrNull() ?: 0L
        val finalMillis = if (millis in 1..999999) millis * 1000 else millis

        val totalSeconds = finalMillis / 1000
<<<<<<< HEAD
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
=======
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60

        val result = String.format(Locale.US, "%02d:%02d", minutes, seconds)

        return toPersianNumber(result)
    }

>>>>>>> d211ee2b997d4e0d4f8b0e5e734b0f33ab6f3151
    fun timeStringToMs(time: String?): Long {
        if (time == null || time == "0" || time == "00:00") return 0L
        return try {
            if (time.contains(":")) {
                val parts = time.split(":")
                when (parts.size) {
                    3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                    2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                    else -> 0L
                }
            } else {
                val num = time.toLongOrNull() ?: 0L
                if (num in 1..999999) num * 1000 else num
            }
        } catch (e: Exception) { 0L }
    }
}
