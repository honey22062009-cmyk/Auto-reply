package dev.veil.app.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"

/** Deliberately redacted debug strings so accidental logging cannot expose keys or conversations. */
class AiHttpRequest(val body: String, val apiKey: String) {
    val url: String get() = OPENAI_RESPONSES_URL
    override fun toString() = "AiHttpRequest(url=$OPENAI_RESPONSES_URL, content=redacted)"
}
class AiHttpResponse(val status: Int, val body: String, val retryAfter: String? = null) {
    override fun toString() = "AiHttpResponse(status=$status, content=redacted)"
}
fun interface AiHttpTransport { suspend fun post(request: AiHttpRequest): AiHttpResponse }

class UrlConnectionAiTransport : AiHttpTransport {
    override suspend fun post(request: AiHttpRequest): AiHttpResponse = suspendCancellableCoroutine { continuation ->
        val connection = AtomicReference<HttpURLConnection?>()
        val future = try {
            executor.submit {
                if (!continuation.isActive) return@submit
                var http: HttpURLConnection? = null
                try {
                    http = URL(OPENAI_RESPONSES_URL).openConnection() as HttpURLConnection
                    connection.set(http)
                    if (!continuation.isActive) return@submit
                    http.requestMethod = "POST"
                    http.instanceFollowRedirects = false // Never forward Authorization to a redirect target.
                    http.connectTimeout = 15_000
                    http.readTimeout = 30_000
                    http.doOutput = true
                    http.setRequestProperty("Authorization", "Bearer ${request.apiKey}")
                    http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    http.setRequestProperty("Accept", "application/json")
                    val bytes = request.body.toByteArray(Charsets.UTF_8)
                    http.setFixedLengthStreamingMode(bytes.size)
                    http.outputStream.use { it.write(bytes) }
                    val status = http.responseCode
                    val source = if (status in 200..299) http.inputStream else http.errorStream
                    val body = source?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            if (!continuation.isActive) return@submit
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 262_144) throw IOException("AI response exceeded size limit")
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }.orEmpty()
                    val result = AiHttpResponse(status, body, http.getHeaderField("Retry-After"))
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    connection.set(null)
                    http?.disconnect()
                }
            }
        } catch (error: Exception) {
            continuation.resumeWithException(IOException("AI transport is busy", error)); null
        }
        continuation.invokeOnCancellation {
            connection.getAndSet(null)?.disconnect()
            future?.cancel(true)
        }
    }

    companion object {
        private val executor = ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS,
            ArrayBlockingQueue(16), { task -> Thread(task, "veil-ai-request").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }
    }
}
