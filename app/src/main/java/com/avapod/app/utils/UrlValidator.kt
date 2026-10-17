package com.avapod.app.utils

import java.net.URI

object UrlValidator {
    fun isWebUrl(value: String?): Boolean = try {
        val uri = URI(value.orEmpty())
        uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }
}
