package com.avapod.app.utils

object FileUtils {
    fun getSafeFileName(title: String?): String {
        if (title.isNullOrEmpty()) return "unknown_episode.mp3"


        var safeName = title
        safeName = safeName.replace("/", "_")
        safeName = safeName.replace("\\", "_")
        safeName = safeName.replace(":", "_")
        safeName = safeName.replace("*", "_")
        safeName = safeName.replace("?", "_")
        safeName = safeName.replace("\"", "_")
        safeName = safeName.replace("<", "_")
        safeName = safeName.replace(">", "_")
        safeName = safeName.replace("|", "_")
        safeName = safeName.replace(" ", "_")

        safeName = safeName.replace(Regex("[^a-zA-Z0-9_.-]"), "")

        return "$safeName.mp3"
    }
}