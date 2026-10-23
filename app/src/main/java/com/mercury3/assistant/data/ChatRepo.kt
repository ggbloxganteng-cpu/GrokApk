package com.mercury3.assistant.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

data class Msg(val role: String, val content: String)
data class Req(
    val model: String = "inception/mercury-3",
    val messages: List<Msg>,
    val stream: Boolean = true,
    val temperature: Float = 0.3f,
    @SerializedName("max_tokens") val maxTokens: Int = 1024
)
data class Delta(val content: String? = null)
data class Choice(val delta: Delta? = null)
data class Chunk(val choices: List<Choice>? = null)

class ChatRepo(private val prefs: SecurePrefs) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun stream(question: String, context: String?): Flow<String> = callbackFlow {
        val key = prefs.apiKey
        if (key.isBlank()) {
            trySend("Error: API key belum diatur. Buka Settings.")
            close()
            return@callbackFlow
        }

        val system = buildString {
            append("Kamu adalah Grok Build, asisten AI kontekstual. Jawab jelas dan dalam bahasa yang sama dengan pertanyaan.")
            if (!context.isNullOrBlank()) {
                append("\n\nKonteks layar:\n```\n${context.take(10000)}\n```")
            }
        }

        val body = gson.toJson(
            Req(
                messages = listOf(Msg("system", system), Msg("user", question)),
                temperature = prefs.temperature,
                maxTokens = prefs.maxTokens
            )
        ).toRequestBody("application/json".toMediaType())

        val url = prefs.baseUrl.trimEnd('/') + "/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") { close(); return }
                try {
                    val chunk = gson.fromJson(data, Chunk::class.java)
                    val text = chunk.choices?.firstOrNull()?.delta?.content
                    if (!text.isNullOrEmpty()) trySend(text)
                } catch (_: Exception) {}
            }
            override fun onFailure(es: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val msg = when (response?.code) {
                    401 -> "Error: API key tidak valid"
                    429 -> "Error: Rate limit"
                    else -> "Error: ${t?.message ?: response?.code ?: "gagal"}"
                }
                trySend(msg)
                close()
            }
            override fun onClosed(es: EventSource) { close() }
        }

        val es = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { es.cancel() }
    }.flowOn(Dispatchers.IO)
}
