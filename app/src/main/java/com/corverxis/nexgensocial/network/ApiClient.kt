package com.corverxis.nexgensocial.network

import com.corverxis.nexgensocial.data.ApiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(message: String, val statusCode: Int = 0) : Exception(message)

/**
 * Single point of contact with the NexgenSocial API.
 *
 * Deliberately an object with an injected token provider rather than a
 * Retrofit interface: the API returns differently-shaped envelopes per
 * endpoint and needs multipart uploads with several files, both of which
 * are simpler with OkHttp directly than with generated stubs.
 */
object ApiClient {
    const val BASE_URL = "https://nexgensocial-udp.fly.dev"

    /** Set by TokenStore at startup; null when signed out. */
    @Volatile var authToken: String? = null

    val json = Json {
        ignoreUnknownKeys = true      // server may add fields; don't crash on them
        coerceInputValues = true      // null for a non-null field falls back to the default
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Video uploads on mobile data are slow; a short write timeout
        // would abort legitimate posts partway through.
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(path: String, method: String, body: RequestBody?): Request {
        val builder = Request.Builder().url(BASE_URL + path)
        authToken?.let { builder.addHeader("Authorization", "Bearer $it") }
        return when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "PATCH" -> builder.patch(body ?: "{}".toRequestBody(JSON_MEDIA))
            else -> builder.post(body ?: "{}".toRequestBody(JSON_MEDIA))
        }.build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (response.code == 401) {
                // Clear the dead token so the UI can route to sign-in rather
                // than retrying with credentials that will never work.
                authToken = null
                throw ApiException("Your session expired. Please sign in again.", 401)
            }

            if (!response.isSuccessful) {
                // The server's own message is almost always more useful than
                // a bare status code.
                val message = runCatching { json.decodeFromString<ApiError>(text).error }
                    .getOrNull() ?: "Request failed (${response.code})."
                throw ApiException(message, response.code)
            }
            text
        }
    }

    suspend inline fun <reified T> get(path: String): T =
        json.decodeFromString(executePublic(buildRequestPublic(path, "GET", null)))

    suspend inline fun <reified T> post(path: String, body: Map<String, Any?>? = null): T {
        val payload = body?.let { encodeMap(it) } ?: "{}"
        return json.decodeFromString(
            executePublic(buildRequestPublic(path, "POST", payload.toRequestBodyPublic()))
        )
    }

    suspend inline fun <reified T> patch(path: String, body: Map<String, Any?>? = null): T {
        val payload = body?.let { encodeMap(it) } ?: "{}"
        return json.decodeFromString(
            executePublic(buildRequestPublic(path, "PATCH", payload.toRequestBodyPublic()))
        )
    }

    suspend fun delete(path: String) {
        executePublic(buildRequestPublic(path, "DELETE", null))
    }

    /**
     * Multipart upload for posts, reels, listings and message attachments.
     * Written by hand because the field/file mix varies per endpoint.
     */
    suspend inline fun <reified T> upload(
        path: String,
        fields: Map<String, String> = emptyMap(),
        files: List<UploadFile> = emptyList(),
    ): T {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (key, value) -> builder.addFormDataPart(key, value) }
        files.forEach { file ->
            builder.addFormDataPart(
                file.fieldName, file.fileName,
                file.bytes.toRequestBody(file.mimeType.toMediaType())
            )
        }
        return json.decodeFromString(
            executePublic(buildRequestPublic(path, "POST", builder.build()))
        )
    }

    /** Absolute URL for a media path returned by the API. */
    fun mediaUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return if (path.startsWith("http")) path else BASE_URL + path
    }

    // --- Exposed for the inline reified helpers above -----------------------
    // Kotlin requires anything an inline function touches to be public, so
    // these thin wrappers exist purely to keep the real implementations
    // private while still letting the generic helpers work.
    @PublishedApi internal fun buildRequestPublic(path: String, method: String, body: RequestBody?) =
        buildRequest(path, method, body)

    @PublishedApi internal suspend fun executePublic(request: Request) = execute(request)

    @PublishedApi internal fun String.toRequestBodyPublic(): RequestBody =
        this.toRequestBody(JSON_MEDIA)

    @PublishedApi internal fun encodeMap(map: Map<String, Any?>): String =
        map.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val encoded = when (value) {
                null -> "null"
                is Number, is Boolean -> value.toString()
                is List<*> -> value.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
            }
            "\"$key\":$encoded"
        }
}

data class UploadFile(
    val fieldName: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
