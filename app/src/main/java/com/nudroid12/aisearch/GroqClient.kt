package com.nudroid12.aisearch

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SearchReply(
    val text: String,
    val sourceCount: Int
)

class GroqClient {

    fun answer(
        apiKey: String,
        searchBundle: WebSearchBundle
    ): SearchReply {
        val today = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())

        val sourceText = buildString {
            searchBundle.results.forEachIndexed { index, result ->
                append("[SOURCE ${index + 1}]\n")
                append("Title: ${result.title}\n")
                append("URL: ${result.url}\n")
                append("Content: ${result.content}\n\n")
            }
        }

        val systemPrompt = """
            You are AISearch, a concise TV search assistant.
            Current date: $today.

            The web search has already been performed for you.

            STRICT RULES:
            1. Answer using ONLY the supplied LIVE WEB SOURCES.
            2. Do not use stored model knowledge to add current facts.
            3. If the sources do not prove the answer, say:
               "I cannot confirm this from the current search results."
            4. For changing facts such as sports squads, office holders,
               prices, schedules, scores, news and releases, prefer the
               newest and most authoritative supplied source.
            5. Never replace current source information with memory.
            6. Keep the answer easy to read on a television.
            7. Reply in the user's language.
            8. End with a short Sources section containing only URLs
               actually relied on.
            9. Do not invent URLs or citations.
        """.trimIndent()

        val userPrompt = """
            QUESTION:
            ${searchBundle.query}

            LIVE WEB SOURCES:
            $sourceText
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "openai/gpt-oss-120b")
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        }
                    )
                }
            )
            put("temperature", 0.1)
            put("max_completion_tokens", 900)
        }

        return requestWithTransientRetry(
            apiKey = apiKey,
            body = body,
            sourceCount = searchBundle.results.size
        )
    }

    private fun requestWithTransientRetry(
        apiKey: String,
        body: JSONObject,
        sourceCount: Int
    ): SearchReply {
        var lastError: Exception? = null

        repeat(2) { attempt ->
            try {
                return requestOnce(
                    apiKey = apiKey,
                    body = body,
                    sourceCount = sourceCount
                )
            } catch (error: RetryableGroqException) {
                lastError = error
                if (attempt == 0) {
                    Thread.sleep(error.retryAfterMs)
                }
            }
        }

        throw lastError
            ?: IllegalStateException("Groq request failed.")
    }

    private fun requestOnce(
        apiKey: String,
        body: JSONObject,
        sourceCount: Int
    ): SearchReply {
        val connection =
            URL("https://api.groq.com/openai/v1/chat/completions")
                .openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 12_000
        connection.readTimeout = 45_000
        connection.doOutput = true
        connection.setRequestProperty(
            "Authorization",
            "Bearer $apiKey"
        )
        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )

        connection.outputStream.use { stream ->
            stream.write(
                body.toString().toByteArray(Charsets.UTF_8)
            )
        }

        val status = connection.responseCode
        val input = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val raw = BufferedReader(input.reader()).use {
            it.readText()
        }

        if (status !in 200..299) {
            val message = try {
                JSONObject(raw)
                    .optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }

            if (
                status == 429 ||
                status in 500..599
            ) {
                val retrySeconds =
                    connection
                        .getHeaderField("retry-after")
                        ?.toLongOrNull()
                        ?.coerceIn(1L, 5L)
                        ?: 2L

                throw RetryableGroqException(
                    message
                        ?: "Groq is temporarily unavailable.",
                    retrySeconds * 1000L
                )
            }

            throw IllegalStateException(
                message
                    ?: "Groq request failed: HTTP $status"
            )
        }

        val response = JSONObject(raw)
        val content = response
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
            .trim()

        if (content.isBlank()) {
            throw IllegalStateException(
                "Groq returned an empty answer."
            )
        }

        return SearchReply(
            text = content,
            sourceCount = sourceCount
        )
    }

    private class RetryableGroqException(
        message: String,
        val retryAfterMs: Long
    ) : Exception(message)
}
