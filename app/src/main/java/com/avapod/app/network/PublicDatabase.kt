package com.avapod.app.network

import com.google.firebase.FirebaseApp
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One-shot reads of public nodes only. Admin writes retain Firebase Auth and the SDK. */
object PublicDatabase {
    private val publicPaths = setOf("podcasts", "categories", "db_config/version", "db_config/podcast_order", "ad_config", "app_messages")
    private const val MAX_BYTES = 8L * 1024 * 1024
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun read(path: String): JsonElement {
        require(path in publicPaths)
        val base = checkNotNull(FirebaseApp.getInstance().options.databaseUrl) { "Missing Firebase database URL" }
        val url = base.toHttpUrl().newBuilder().addPathSegments("$path.json").build()
        require(url.isHttps) { "Public database requires HTTPS" }
        val call = client.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { continuation ->
            // Timeout or leaving the screen cancels the network operation, not just its waiter.
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IOException("Public database $path: ${e.javaClass.simpleName}", e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val json = response.use {
                            if (!it.isSuccessful) throw IOException("Public database $path: HTTP ${it.code}")
                            val body = it.body ?: throw IOException("Empty database response")
                            if (body.contentLength() > MAX_BYTES || body.source().request(MAX_BYTES + 1)) {
                                throw IOException("Database response exceeds size limit")
                            }
                            val content = body.string()
                            if (content.isBlank()) throw IOException("Empty database response")
                            JsonParser.parseString(content)
                        }
                        if (continuation.isActive) continuation.resume(json)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}

/** Firebase REST may encode numeric child keys as an array, including null gaps. */
internal fun JsonElement.databaseChildren(): List<Pair<String, JsonObject>> = when {
    isJsonNull -> emptyList()
    isJsonObject -> asJsonObject.entrySet().filter { !it.value.isJsonNull }.map { it.key to it.value.asJsonObject }
    isJsonArray -> asJsonArray.mapIndexedNotNull { index, item ->
        if (item.isJsonNull) null else index.toString() to item.asJsonObject
    }
    else -> throw IOException("Invalid database collection")
}

internal fun JsonElement.databaseString(key: String): String {
    if (isJsonNull) return ""
    val value = asJsonObject.get(key) ?: return ""
    return if (value.isJsonNull) "" else value.asString
}

internal fun JsonElement.databaseBoolean(key: String): Boolean {
    if (isJsonNull) return false
    val value = asJsonObject.get(key) ?: return false
    return !value.isJsonNull && value.asBoolean
}
