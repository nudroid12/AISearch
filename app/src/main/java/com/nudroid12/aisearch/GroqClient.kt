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
    val usedWebSearch: Boolean
)

class GroqClient {

    fun search(apiKey: String, query: String): SearchReply {
        val connection =
            URL("https://api.groq.com/openai/v1/chat/completions")
                .openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.doOutput = true
        connection.setRequestProperty(
            "Authorization",
            "Bearer $apiKey"
        )
        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )
        connection.setRequestProperty(
            "Groq-Model-Version",
            "latest"
        )

        val today = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())

        val systemPrompt = """
            You are AISearch, a live web search assistant for a TV.
            Current device date: $today.

            Mandatory rules:
            1. Use web_search before answering EVERY user query.
            2. Never use model memory as the source for facts that may
               have changed.
            3. Current squads, people in roles, prices, schedules,
               releases, news, weather, scores and availability must
               be verified from current web results.
            4. Prefer recent authoritative sources.
            5. If current information cannot be verified, say so.
            6. Keep answers concise and easy to read on a television.
            7. Reply in the user's language.
            8. Include useful source links or citations when available.
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "groq/compound-mini")
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
                            put("content", query)
                        }
                    )
                }
            )
            put(
                "compound_custom",
                JSONObject().apply {
                    put(
                        "tools",
                        JSONObject().apply {
                            put(
                                "enabled_tools",
                                JSONArray().apply {
                                    put("web_search")
                                }
                            )
                        }
                    )
                }
            )
        }

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

            throw IllegalStateException(
                message ?: "Groq request failed: HTTP $status"
            )
        }

        val response = JSONObject(raw)
        val message = response
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")

        val content = message.optString("content").trim()
        val executedTools =
            message.optJSONArray("executed_tools")

        var usedWebSearch = false

        if (executedTools != null) {
            for (index in 0 until executedTools.length()) {
                val item =
                    executedTools.optJSONObject(index) ?: continue

                if (
                    item.optString("type")
                        .contains("search", ignoreCase = true)
                ) {
                    usedWebSearch = true
                    break
                }
            }
        }

        val finalText = buildString {
            if (!usedWebSearch) {
                append(
                    "⚠ Live web search was not confirmed. " +
                        "Treat this answer as unverified.\n\n"
                )
            }
            append(
                if (content.isNotBlank()) {
                    content
                } else {
                    "No answer returned."
                }
            )
        }

        return SearchReply(
            text = finalText,
            usedWebSearch = usedWebSearch
        )
    }
}
