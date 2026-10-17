package com.avapod.app.utils

import java.security.MessageDigest

object FileUtils {
    /** A title is display data, not file identity. Unicode and identical titles are safe. */
    fun getSafeFileName(audioUrl: String): String {
        require(audioUrl.isNotBlank())
        return MessageDigest.getInstance("SHA-256").digest(audioUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".mp3"
    }
}
