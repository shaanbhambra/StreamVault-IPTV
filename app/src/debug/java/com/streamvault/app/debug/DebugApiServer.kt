package com.streamvault.app.debug

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

class DebugApiServer(
    private val context: Context,
    db: SQLiteDatabase,
    port: Int = 8585
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "DebugAPI"
    }

    private val dao = DebugDao(db)

    private val companionHtml: String by lazy {
        try { context.assets.open("companion.html").bufferedReader().readText() }
        catch (e: Exception) { "<html><body><h1>Companion not found</h1></body></html>" }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method
        val params = session.parms ?: emptyMap()
        Log.d(TAG, "$method $uri")

        return try {
            val resp = route(uri, method, params, session)
            resp.addHeader("Access-Control-Allow-Origin", "*")
            resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            resp.addHeader("Access-Control-Allow-Headers", "Content-Type")
            resp
        } catch (e: Exception) {
            Log.e(TAG, "Error: $method $uri", e)
            json(500, JSONObject().put("error", e.message ?: "unknown"))
        }
    }

    private fun route(uri: String, method: Method, params: Map<String, String>, session: IHTTPSession): Response = when {
        uri == "/" || uri == "/index.html" -> newFixedLengthResponse(Response.Status.OK, "text/html", companionHtml)
        uri == "/qr" -> handleQr()
        uri == "/manifest.json" -> handleManifest()
        uri == "/sw.js" -> handleServiceWorker()
        uri == "/launch" && method == Method.POST -> handleLaunch()
        uri == "/wake" && method == Method.POST -> handleWake()
        uri == "/status" -> handleStatus()
        uri == "/channels" -> handleChannels(params)
        uri.startsWith("/channel/") -> handleChannelDetail(uri)
        uri == "/categories" -> handleCategories(params)
        uri == "/favorites" && method == Method.GET -> handleFavorites()
        uri == "/favorites/add" && method == Method.POST -> handleAddFav(session)
        uri == "/favorites/remove" && method == Method.POST -> handleRemoveFav(session)
        uri == "/play" && method == Method.POST -> handlePlay(session)
                // play_fast disabled — multi-connection racing risks account ban
                // uri == "/play_fast" && method == Method.POST -> handlePlayFast(session)
                uri == "/quick_switch" && method == Method.POST -> handleQuickSwitch(session)
        uri == "/epg" -> handleEpg(params)
                uri == "/epg/now" && method == Method.POST -> handleEpgNow(session)
        uri == "/clear_prefs" && method == Method.POST -> handleClearPrefs()
                uri == "/show_qr" && method == Method.POST -> handleShowQr()
                uri == "/ai" && method == Method.POST -> handleAi(session)
                uri == "/ai/status" -> handleAiStatus()
                uri == "/settings" -> handleSettings()
                uri == "/vpn/on" && method == Method.POST -> handleVpnOn()
                uri == "/vpn/off" && method == Method.POST -> handleVpnOff()
                uri == "/benchmark" -> handleBenchmark()
                uri == "/precache" && method == Method.POST -> handlePrecache()
        method == Method.OPTIONS -> newFixedLengthResponse(Response.Status.OK, "text/plain", "")
        else -> json(404, JSONObject().put("error", "not found: $uri"))
    }

    private fun handleStatus(): Response {
        val providerId = dao.getFirstProviderId()
        val provider = dao.getActiveProvider()
        return json(200, JSONObject().apply {
            put("provider", provider ?: JSONObject.NULL)
            put("channel_count", providerId?.let { dao.getCount("channels", it) } ?: 0)
            put("category_count", providerId?.let { dao.getCount("categories", it) } ?: 0)
            put("favorite_count", providerId?.let { dao.getCount("favorites", it) } ?: 0)
            put("vpn_active", isVpnActive())
            put("network_available", isNetworkAvailable())
        })
    }

    private fun handleChannels(params: Map<String, String>): Response {
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val search = params["search"]
        val catId = params["category_id"]?.toLongOrNull()
        val limit = params["limit"]?.toIntOrNull() ?: 50
        val offset = params["offset"]?.toIntOrNull() ?: 0
        val channels = when {
            search != null -> dao.searchChannels(pid, search, limit)
            catId != null -> dao.getChannelsByCategory(pid, catId, limit, offset)
            else -> dao.getAllChannels(pid, limit, offset)
        }
        return json(200, JSONObject().put("channels", channels).put("count", channels.length()))
    }

    private fun handleChannelDetail(uri: String): Response {
        val id = uri.removePrefix("/channel/").toLongOrNull() ?: return json(400, JSONObject().put("error", "invalid id"))
        val ch = dao.getChannelById(id) ?: return json(404, JSONObject().put("error", "not found"))
        return json(200, ch)
    }

    private fun handleCategories(params: Map<String, String>): Response {
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val cats = dao.getCategoriesByType(pid, params["type"] ?: "LIVE")
        return json(200, JSONObject().put("categories", cats).put("count", cats.length()))
    }

    private fun handleFavorites(): Response {
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val favs = dao.getFavorites(pid)
        return json(200, JSONObject().put("favorites", favs).put("count", favs.length()))
    }

    private fun handleAddFav(session: IHTTPSession): Response {
        val chId = parseBody(session).optLong("channel_id", -1)
        if (chId == -1L) return json(400, JSONObject().put("error", "channel_id required"))
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        dao.addFavorite(pid, chId)
        return json(200, JSONObject().put("success", true).put("channel_id", chId))
    }

    private fun handleRemoveFav(session: IHTTPSession): Response {
        val chId = parseBody(session).optLong("channel_id", -1)
        if (chId == -1L) return json(400, JSONObject().put("error", "channel_id required"))
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        dao.removeFavorite(pid, chId)
        return json(200, JSONObject().put("success", true).put("removed", chId))
    }

    private fun handlePlay(session: IHTTPSession): Response {
        // VPN enforced at OS level (WireGuard always-on) — no app-level gate needed
        val chId = parseBody(session).optLong("channel_id", -1)
        if (chId == -1L) return json(400, JSONObject().put("error", "channel_id required"))
        val ch = dao.getChannelById(chId) ?: return json(404, JSONObject().put("error", "channel not found"))
        val streamId = ch.getLong("stream_id")
        val name = ch.getString("name")
        val pid = dao.getFirstProviderId() ?: 1L
        val streamUrl = "xtream://$pid/live/$streamId?ext=&src="
        val intent = android.content.Intent(context, Class.forName("com.streamvault.app.MainActivity")).apply {
            action = android.content.Intent.ACTION_VIEW
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("com.streamvault.app.extra.PLAYER_REQUEST",
                com.streamvault.app.navigation.PlayerNavigationRequest(
                    streamUrl = streamUrl, title = name, internalId = chId,
                    providerId = pid, contentType = "LIVE"
                ))
        }
        context.startActivity(intent)
        return json(200, JSONObject().put("success", true).put("playing", name).put("channel_id", chId))
    }

    /**
     * Race multiple server URLs to find the fastest responding one, then play.
     * Uses the same stream_id but tries different server hostnames concurrently.
     */
    private fun handlePlayFast(session: IHTTPSession): Response {
        val body = parseBody(session)
        val chId = body.optLong("channel_id", -1)
        if (chId == -1L) return json(400, JSONObject().put("error", "channel_id required"))
        val ch = dao.getChannelById(chId) ?: return json(404, JSONObject().put("error", "channel not found"))
        val streamId = ch.getLong("stream_id")
        val name = ch.getString("name")
        val pid = dao.getFirstProviderId() ?: 1L

        // Race fast CDN servers only (matrix servers are 5x slower)
        // All servers accept our credentials (tested)
        val servers = listOf(
            "candycloudstrong8k.xyz",     // Fastest (~1.3s, backup but direct)
            "cf.candycloud-8k.men",       // Primary CDN (~1.3s, 302 redirect)
            "pro.candycloud-8k.men"       // Secondary CDN (~1.4s, 302 redirect)
        )

        var fastestServer: String? = null
        val threads = servers.map { server ->
            Thread {
                try {
                    val testUrl = java.net.URL("http://$server/live/b5885330ec/5e46b997af/$streamId.ts")
                    val conn = testUrl.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 2000
                    conn.instanceFollowRedirects = true // Follow 302 redirects
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Range", "bytes=0-0") // Just first byte
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..399) {
                        synchronized(this@DebugApiServer) {
                            if (fastestServer == null) fastestServer = server
                        }
                    }
                } catch (_: Exception) {}
            }.also { it.start() }
        }

        // Wait up to 3 seconds for a winner
        val deadline = System.currentTimeMillis() + 3000
        while (fastestServer == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        val winningServer = fastestServer ?: "cf.candycloud-8k.men"

        // If a different server won, update the provider's server_url for future plays
        val currentProvider = dao.getActiveProvider()
        val currentServer = currentProvider?.optString("server_url", "") ?: ""
        if (winningServer !in currentServer) {
            dao.updateProviderServerUrl(pid, "http://$winningServer")
            android.util.Log.i("DebugAPI", "Switched provider to faster server: $winningServer")
        }

        playChannelById(chId, streamId, name, pid)

        return json(200, JSONObject().apply {
            put("success", true)
            put("playing", name)
            put("channel_id", chId)
            put("fastest_server", winningServer)
            put("servers_tested", servers.size)
        })
    }

    private fun handleQuickSwitch(session: IHTTPSession): Response {
        val body = parseBody(session)
        val query = body.optString("query", "")
        val prefer4k = body.optBoolean("prefer_4k", true)
        if (query.isBlank()) return json(400, JSONObject().put("error", "query required"))

        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val channels = dao.searchChannels(pid, query, 30)

        if (channels.length() == 0) return json(404, JSONObject().put("error", "no channels found for: $query"))

        // Pick best channel — skip PPV, NO EVENT, placeholders
        var best: JSONObject? = null
        for (i in 0 until channels.length()) {
            val ch = channels.getJSONObject(i)
            val name = ch.getString("name")
            if (name.contains("NO EVENT") || name.contains("PPV") ||
                name.startsWith("#") || name.startsWith("-") || name.startsWith("(")) continue
            if (prefer4k && (name.contains("4K") || name.contains("UHD") || name.contains("HD"))) {
                best = ch; break
            }
            if (best == null) best = ch
        }
        if (best == null) {
            // All results were filtered — take first non-placeholder
            for (i in 0 until channels.length()) {
                val ch = channels.getJSONObject(i)
                if (!ch.getString("name").startsWith("#")) { best = ch; break }
            }
        }
        if (best == null) return json(404, JSONObject().put("error", "no playable channels for: $query"))

        val chId = best.getLong("id")
        val streamId = best.getLong("stream_id")
        val name = best.getString("name")

        playChannelById(chId, streamId, name, pid)

        return json(200, JSONObject().apply {
            put("success", true); put("playing", name)
            put("channel_id", chId); put("stream_id", streamId); put("searched", query)
        })
    }

    private fun playChannelById(chId: Long, streamId: Long, name: String, pid: Long) {
        val streamUrl = "xtream://$pid/live/$streamId?ext=&src="
        val intent = android.content.Intent(context, Class.forName("com.streamvault.app.MainActivity")).apply {
            action = android.content.Intent.ACTION_VIEW
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("com.streamvault.app.extra.PLAYER_REQUEST",
                com.streamvault.app.navigation.PlayerNavigationRequest(
                    streamUrl = streamUrl, title = name, internalId = chId,
                    providerId = pid, contentType = "LIVE"
                ))
        }
        context.startActivity(intent)
    }

    private fun handleEpg(params: Map<String, String>): Response {
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val now = System.currentTimeMillis() / 1000
        val arr = when {
            params["channel_id"] != null -> dao.getEpgForChannel(pid, params["channel_id"]!!, now)
            params["search"] != null -> dao.searchEpg(pid, params["search"]!!, now)
            else -> dao.getCurrentlyAiring(pid, now, params["limit"]?.toIntOrNull() ?: 50)
        }
        return json(200, JSONObject().put("programs", arr).put("count", arr.length()))
    }

    private fun handleEpgNow(session: IHTTPSession): Response {
        val body = parseBody(session)
        val channelIds = mutableListOf<Long>()
        val arr = body.optJSONArray("channel_ids")
        if (arr != null) {
            for (i in 0 until arr.length()) channelIds.add(arr.getLong(i))
        }
        if (channelIds.isEmpty()) return json(400, JSONObject().put("error", "channel_ids required"))
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val now = System.currentTimeMillis() / 1000
        val nowPlaying = dao.getNowPlayingBatch(pid, channelIds, now)
        return json(200, JSONObject().put("now_playing", nowPlaying))
    }

    private fun handleManifest(): Response {
        val ip = getDeviceIp()
        val manifest = """{
  "name": "StreamVault Remote",
  "short_name": "StreamVault",
  "description": "Control your TV from your phone",
  "start_url": "http://$ip:8585/",
  "scope": "http://$ip:8585/",
  "display": "standalone",
  "orientation": "portrait",
  "background_color": "#0a0a0f",
  "theme_color": "#6c5ce7",
  "icons": [
    {
      "src": "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512'><rect width='512' height='512' rx='100' fill='%236c5ce7'/><text x='256' y='340' text-anchor='middle' font-size='250' font-weight='bold' fill='white' font-family='system-ui'>SV</text></svg>",
      "sizes": "512x512",
      "type": "image/svg+xml",
      "purpose": "any maskable"
    }
  ]
}"""
        return newFixedLengthResponse(Response.Status.OK, "application/manifest+json", manifest)
    }

    private fun handleServiceWorker(): Response {
        val sw = """
const CACHE_NAME = 'streamvault-v1';
self.addEventListener('install', e => { self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(clients.claim()); });
self.addEventListener('fetch', e => {
  // Network-first for API, cache-first for static
  if (e.request.url.includes('/status') || e.request.url.includes('/channels') ||
      e.request.url.includes('/favorites') || e.request.url.includes('/ai') ||
      e.request.url.includes('/play') || e.request.url.includes('/epg')) {
    e.respondWith(fetch(e.request).catch(() => new Response('{"error":"offline"}', {headers:{'Content-Type':'application/json'}})));
  }
});"""
        return newFixedLengthResponse(Response.Status.OK, "application/javascript", sw)
    }

    private fun handleWake(): Response {
        // Wake the TV screen if it's in standby, then launch StreamVault
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = pm.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "streamvault:wake"
                )
                wakeLock.acquire(3000) // Wake screen for 3 seconds
                wakeLock.release()
            } catch (_: Exception) {}

            // Launch StreamVault to foreground
            val intent = android.content.Intent(context, Class.forName("com.streamvault.app.MainActivity")).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
        return json(200, JSONObject().put("success", true).put("message", "TV woken, StreamVault launched"))
    }

    private fun handleLaunch(): Response {
        // Launch StreamVault to foreground
        val intent = android.content.Intent(context, Class.forName("com.streamvault.app.MainActivity")).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
        return json(200, JSONObject().put("success", true).put("message", "StreamVault launched"))
    }

    private fun handleQr(): Response {
        val ip = getDeviceIp()
        val url = "http://$ip:8585/"
        return newFixedLengthResponse(Response.Status.OK, "text/html", """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>StreamVault - Connect</title>
<style>body{background:#0a0a0f;color:#e8e8f0;font-family:system-ui,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;margin:0;text-align:center}
h1{font-size:28px;margin-bottom:8px;background:linear-gradient(135deg,#6c5ce7,#a29bfe);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
p{color:#8888a0;font-size:16px;margin-bottom:30px}#qr{background:white;padding:20px;border-radius:16px;display:inline-block}.url{font-family:monospace;font-size:18px;margin-top:20px;color:#a29bfe}</style>
<script src="https://cdn.jsdelivr.net/npm/qrcode-generator@1.4.4/qrcode.min.js"></script>
</head><body><h1>StreamVault Remote</h1><p>Scan with your phone to connect</p>
<div id="qr"></div><div class="url">$url</div>
<script>var q=qrcode(0,'M');q.addData('$url');q.make();document.getElementById('qr').innerHTML=q.createSvgTag(8,0)</script>
</body></html>""")
    }

    private fun handleBenchmark(): Response {
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val results = JSONArray()

        // Test category listing
        var start = System.nanoTime()
        val cats = dao.getCategoriesByType(pid, "LIVE")
        val catTime = (System.nanoTime() - start) / 1_000_000.0
        results.put(JSONObject().put("test", "list_categories").put("ms", catTime).put("count", cats.length()))

        // Test channel count
        start = System.nanoTime()
        val count = dao.getCount("channels", pid)
        val countTime = (System.nanoTime() - start) / 1_000_000.0
        results.put(JSONObject().put("test", "channel_count").put("ms", countTime).put("count", count))

        // Test channel search
        start = System.nanoTime()
        val searchResult = dao.searchChannels(pid, "ESPN", 20)
        val searchTime = (System.nanoTime() - start) / 1_000_000.0
        results.put(JSONObject().put("test", "search_ESPN").put("ms", searchTime).put("count", searchResult.length()))

        // Test loading channels by category (first 5 categories)
        for (i in 0 until minOf(5, cats.length())) {
            val cat = cats.getJSONObject(i)
            val catId = cat.getLong("category_id")
            val catName = cat.getString("name")
            start = System.nanoTime()
            val chans = dao.getChannelsByCategory(pid, catId, 100, 0)
            val loadTime = (System.nanoTime() - start) / 1_000_000.0
            results.put(JSONObject().put("test", "load_category: $catName").put("ms", loadTime).put("count", chans.length()))
        }

        // Test favorites
        start = System.nanoTime()
        val favs = dao.getFavorites(pid)
        val favTime = (System.nanoTime() - start) / 1_000_000.0
        results.put(JSONObject().put("test", "favorites").put("ms", favTime).put("count", favs.length()))

        return json(200, JSONObject().put("benchmarks", results))
    }

    private fun handlePrecache(): Response {
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val start = System.nanoTime()

        // Pre-cache: load all categories and their channel counts
        val cats = dao.getCategoriesByType(pid, "LIVE")
        var totalChannels = 0
        for (i in 0 until cats.length()) {
            val catId = cats.getJSONObject(i).getLong("category_id")
            val chans = dao.getChannelsByCategory(pid, catId, 500, 0)
            totalChannels += chans.length()
        }

        val totalTime = (System.nanoTime() - start) / 1_000_000.0
        return json(200, JSONObject().apply {
            put("success", true)
            put("categories", cats.length())
            put("channels_loaded", totalChannels)
            put("total_ms", totalTime)
        })
    }

    private fun handleAiStatus(): Response {
        return json(200, JSONObject().apply {
            put("configured", GeminiClient.isConfigured())
            put("model", "gemini-3.1-flash-lite")
        })
    }

    private fun handleAi(session: IHTTPSession): Response {
        if (!GeminiClient.isConfigured()) {
            return json(400, JSONObject().put("error", "Gemini API key not configured"))
        }

        val body = parseBody(session)
        val query = body.optString("query", "")
        val mode = body.optString("mode", "search") // "search" or "chat"
        if (query.isBlank()) return json(400, JSONObject().put("error", "query required"))

        // Build channel context from favorites + search results
        val pid = dao.getFirstProviderId() ?: return json(404, JSONObject().put("error", "no provider"))
        val favs = dao.getFavorites(pid)
        val searchResults = dao.searchChannels(pid, query.split(" ").first(), 30)

        val contextBuilder = StringBuilder()
        // Add favorites
        for (i in 0 until favs.length()) {
            val f = favs.getJSONObject(i)
            contextBuilder.appendLine("[${f.getLong("channel_id")}] ${f.getString("channel_name")} | ${f.getString("category_name")} (favorite)")
        }
        // Add search results
        for (i in 0 until searchResults.length()) {
            val ch = searchResults.getJSONObject(i)
            contextBuilder.appendLine("[${ch.getLong("id")}] ${ch.getString("name")} | ${ch.getString("category_name")}")
        }

        val aiResponse = if (mode == "chat") {
            GeminiClient.chat(query, contextBuilder.toString())
        } else {
            GeminiClient.findChannels(query, contextBuilder.toString())
        }

        return if (aiResponse != null) {
            // Try to parse as JSON, fall back to raw text
            try {
                val cleaned = aiResponse.trim().removePrefix("```json").removeSuffix("```").trim()
                val parsed = JSONObject(cleaned)
                json(200, JSONObject().put("ai_response", parsed).put("query", query))
            } catch (_: Exception) {
                json(200, JSONObject().put("ai_response", aiResponse).put("query", query))
            }
        } else {
            json(500, JSONObject().put("error", "Gemini API call failed"))
        }
    }

    private fun handleSettings(): Response {
        val ip = getDeviceIp()
        val pid = dao.getFirstProviderId()
        val provider = dao.getActiveProvider()

        return json(200, JSONObject().apply {
            put("device_ip", ip)
            put("companion_url", "http://$ip:8585/")
            put("qr_url", "http://$ip:8585/qr")
            put("vpn_active", isVpnActive())
            put("vpn_app", "com.wireguard.android")
            put("provider", provider ?: JSONObject.NULL)
            put("channel_count", pid?.let { dao.getCount("channels", it) } ?: 0)
            put("favorite_count", pid?.let { dao.getCount("favorites", it) } ?: 0)
            put("ai_configured", GeminiClient.isConfigured())
        })
    }

    private fun handleVpnOn(): Response {
        return try {
            // Run on main thread to avoid cross-thread issues
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val intent = android.content.Intent("com.wireguard.android.action.SET_TUNNEL_UP").apply {
                        setClassName("com.wireguard.android", "com.wireguard.android.model.TunnelManager\$IntentReceiver")
                    }
                    context.sendBroadcast(intent)
                } catch (_: Exception) {
                    // Fallback: launch Mullvad app
                    val mullvadIntent = context.packageManager.getLaunchIntentForPackage("net.mullvad.mullvadvpn")
                    if (mullvadIntent != null) {
                        mullvadIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(mullvadIntent)
                    }
                }
            }
            json(200, JSONObject().put("success", true).put("message", "VPN connect requested"))
        } catch (e: Exception) {
            json(500, JSONObject().put("error", e.message ?: "unknown"))
        }
    }

    private fun handleVpnOff(): Response {
        return try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val intent = android.content.Intent("com.wireguard.android.action.SET_TUNNEL_DOWN").apply {
                    setClassName("com.wireguard.android", "com.wireguard.android.model.TunnelManager\$IntentReceiver")
                }
                context.sendBroadcast(intent)
            }
            json(200, JSONObject().put("success", true).put("message", "VPN disconnect requested"))
        } catch (e: Exception) {
            return json(500, JSONObject().put("error", e.message))
        }
    }

    private fun handleShowQr(): Response {
        // Launch the QR page in a WebView overlay on the TV
        val ip = getDeviceIp()
        val url = "http://$ip:8585/qr"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: show toast with the URL
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Connect phone: http://$ip:8585/", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        return json(200, JSONObject().apply {
            put("success", true)
            put("url", "http://$ip:8585/")
            put("qr_url", url)
        })
    }

    private fun handleClearPrefs(): Response {
        val prefsDir = java.io.File(context.filesDir, "datastore")
        val deleted = prefsDir.listFiles()?.count { it.delete() } ?: 0
        return json(200, JSONObject().put("success", true).put("deleted_files", deleted))
    }

    private fun isVpnActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null
    }

    private fun getDeviceIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) return addr.hostAddress ?: "192.168.2.21"
                }
            }
        } catch (_: Exception) {}
        return "192.168.2.21"
    }

    private fun parseBody(session: IHTTPSession): JSONObject {
        val m = HashMap<String, String>()
        session.parseBody(m)
        return try { JSONObject(m["postData"] ?: "{}") } catch (_: Exception) { JSONObject() }
    }

    private fun json(code: Int, body: JSONObject): Response {
        val status = when (code) { 200 -> Response.Status.OK; 400 -> Response.Status.BAD_REQUEST; 404 -> Response.Status.NOT_FOUND; else -> Response.Status.INTERNAL_ERROR }
        return newFixedLengthResponse(status, "application/json", body.toString(2))
    }
}
