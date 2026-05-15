package com.streamvault.app.debug

import android.util.Log
import com.streamvault.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini API client for AI-powered channel search. Runs on-device.
 * Supports both Generative AI API (AIza... keys) and Vertex AI (AQ... keys).
 */
object GeminiClient {
    private const val TAG = "GeminiAI"
    private const val MODEL = "gemini-3.1-flash-lite"
    private val API_KEY = BuildConfig.GEMINI_API_KEY

    fun isConfigured(): Boolean = API_KEY.isNotBlank()

    fun findChannels(query: String, channelContext: String): String? {
        if (!isConfigured()) return null

        val prompt = """You are an IPTV channel assistant. The user wants to find something to watch.

Available channels (format: [id] name | category):
$channelContext

User request: "$query"

Respond ONLY with valid JSON (no markdown, no backticks):
{"channels": [{"id": 123, "name": "Channel Name", "reason": "why"}], "message": "friendly response"}

Pick the best 5-10 matching channels. Prefer HD/4K. If asking about a live game, match team/league names."""

        return callGemini(prompt)
    }

    fun chat(message: String, channelContext: String): String? {
        if (!isConfigured()) return null

        val prompt = """You are a helpful IPTV assistant on an Android TV. Help find channels and content.

Available channels (top matches):
$channelContext

User: $message

Respond ONLY with valid JSON (no markdown):
{"channels": [{"id": 123, "name": "Name", "reason": "why"}], "message": "your response"}

If the user wants to watch something, include matching channel IDs. Keep responses concise."""

        return callGemini(prompt)
    }

    private fun callGemini(prompt: String): String? {
        try {
            // Use Generative AI endpoint with API key
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$API_KEY")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 1024)
                    put("responseMimeType", "application/json")
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                Log.e(TAG, "Gemini error $responseCode: $error")
                return """{"message": "AI unavailable (error $responseCode). Try searching manually.", "channels": []}"""
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            return json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed", e)
            return """{"message": "AI error: ${e.message}. Try searching manually.", "channels": []}"""
        }
    }
}
