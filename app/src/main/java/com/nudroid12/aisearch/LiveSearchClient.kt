package com.nudroid12.aisearch

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WebResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double
)

data class WebSearchBundle(
    val query: String,
    val results: List<WebResult>
)

class LiveSearchClient {

    fun search(query: String): WebSearchBundle {
        val today = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())

        val requestQuery =
            "Current information as of $today. $query"

        val connection =
            URL("https://api.tavily.com/search")
                .openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.doOutput = true
        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )
        connection.setRequestProperty(
            "X-Tavily-Access-Mode",
            "keyless"
        )

        val body = JSONObject().apply {
            put("query", requestQuery)
            put("search_depth", "basic")
            put("max_results", 5)
            put("include_answer", false)
            put("include_raw_content", false)
            put("include_images", false)
            put("auto_parameters", false)
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
                    .optString("detail")
                    .takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }

            throw IllegalStateException(
                message
                    ?: "Live web search failed: HTTP $status"
            )
        }

        val response = JSONObject(raw)
        val array = response.optJSONArray("results")
            ?: throw IllegalStateException(
                "Live web search returned no results."
            )

        val results = buildList {
            for (index in 0 until array.length()) {
                val item =
                    array.optJSONObject(index) ?: continue

                val title =
                    item.optString("title").trim()
                val url =
                    item.optString("url").trim()
                val content =
                    item.optString("content").trim()
                val score =
                    item.optDouble("score", 0.0)

                if (
                    title.isNotBlank() &&
                    url.startsWith("http") &&
                    content.isNotBlank()
                ) {
                    add(
                        WebResult(
                            title = title,
                            url = url,
                            content = content,
                            score = score
                        )
                    )
                }
            }
        }

        if (results.isEmpty()) {
            throw IllegalStateException(
                "No usable live web results were found."
            )
        }

        return WebSearchBundle(
            query = query,
            results = results
        )
    }
}
