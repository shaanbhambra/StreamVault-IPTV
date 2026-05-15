package com.streamvault.app.debug

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-only DAO using raw SQLiteDatabase. Avoids Room dependency conflicts.
 */
class DebugDao(private val db: SQLiteDatabase) {

    fun getActiveProvider(): JSONObject? {
        return db.rawQuery(
            "SELECT id, name, server_url, status, max_connections, expiration_date, last_synced_at FROM providers WHERE is_active = 1 LIMIT 1",
            null
        ).use {
            if (it.moveToFirst()) JSONObject().apply {
                put("id", it.getLong(0))
                put("name", it.getString(1))
                put("server_url", it.getString(2))
                put("status", it.getString(3))
                put("max_connections", it.getInt(4))
                put("expiration_date", it.getLong(5))
                put("last_synced_at", it.getLong(6))
            } else null
        }
    }

    fun getFirstProviderId(): Long? {
        return db.rawQuery("SELECT id FROM providers ORDER BY is_active DESC LIMIT 1", null).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    fun getCount(table: String, providerId: Long): Int {
        return db.rawQuery("SELECT COUNT(*) FROM $table WHERE provider_id = ?", arrayOf(providerId.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun searchChannels(providerId: Long, query: String, limit: Int): JSONArray {
        // Exclude PPV, NO EVENT, and placeholder channels; sort real channels first
        return db.rawQuery(
            """SELECT id, stream_id, name, category_id, category_name, logo_url, epg_channel_id, number
               FROM channels
               WHERE provider_id = ? AND name LIKE ?
                 AND name NOT LIKE '%PPV%'
                 AND name NOT LIKE '%NO EVENT%'
                 AND name NOT LIKE '-%'
                 AND name NOT LIKE '(FLSP%'
                 AND name NOT LIKE '##%'
               ORDER BY name LIMIT ?""",
            arrayOf(providerId.toString(), "%$query%", limit.toString())
        ).toChannelArray()
    }

    fun getChannelsByCategory(providerId: Long, categoryId: Long, limit: Int, offset: Int): JSONArray {
        return db.rawQuery(
            "SELECT id, stream_id, name, category_id, category_name, logo_url, epg_channel_id, number FROM channels WHERE provider_id = ? AND category_id = ? ORDER BY number LIMIT ? OFFSET ?",
            arrayOf(providerId.toString(), categoryId.toString(), limit.toString(), offset.toString())
        ).toChannelArray()
    }

    fun getAllChannels(providerId: Long, limit: Int, offset: Int): JSONArray {
        return db.rawQuery(
            "SELECT id, stream_id, name, category_id, category_name, logo_url, epg_channel_id, number FROM channels WHERE provider_id = ? ORDER BY number LIMIT ? OFFSET ?",
            arrayOf(providerId.toString(), limit.toString(), offset.toString())
        ).toChannelArray()
    }

    fun getChannelById(channelId: Long): JSONObject? {
        return db.rawQuery(
            "SELECT id, stream_id, name, category_id, category_name, logo_url, stream_url, epg_channel_id, number, is_adult FROM channels WHERE id = ?",
            arrayOf(channelId.toString())
        ).use {
            if (it.moveToFirst()) JSONObject().apply {
                put("id", it.getLong(0)); put("stream_id", it.getLong(1))
                put("name", it.getString(2))
                put("category_id", if (it.isNull(3)) 0 else it.getLong(3))
                put("category_name", it.getString(4) ?: "")
                put("logo_url", it.getString(5) ?: "")
                put("stream_url", it.getString(6))
                put("epg_channel_id", it.getString(7) ?: "")
                put("number", it.getInt(8)); put("is_adult", it.getInt(9))
            } else null
        }
    }

    fun getCategoriesByType(providerId: Long, type: String): JSONArray {
        // Include channel count per category
        return db.rawQuery(
            """SELECT c.id, c.category_id, c.name, c.type,
                      (SELECT COUNT(*) FROM channels ch WHERE ch.provider_id = c.provider_id AND ch.category_id = c.category_id) as channel_count
               FROM categories c WHERE c.provider_id = ? AND c.type = ? ORDER BY c.name""",
            arrayOf(providerId.toString(), type)
        ).use {
            val arr = JSONArray()
            while (it.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("id", it.getLong(0)); put("category_id", it.getLong(1))
                    put("name", it.getString(2)); put("type", it.getString(3))
                    put("channel_count", it.getInt(4))
                })
            }
            arr
        }
    }

    fun getFavorites(providerId: Long): JSONArray {
        return db.rawQuery(
            "SELECT f.id, f.content_id, f.content_type, f.position, c.name, c.stream_id, c.category_name FROM favorites f LEFT JOIN channels c ON c.id = f.content_id WHERE f.provider_id = ? ORDER BY f.position",
            arrayOf(providerId.toString())
        ).use {
            val arr = JSONArray()
            while (it.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("favorite_id", it.getLong(0)); put("channel_id", it.getLong(1))
                    put("content_type", it.getString(2)); put("position", it.getInt(3))
                    put("channel_name", it.getString(4) ?: "unknown")
                    put("stream_id", if (it.isNull(5)) 0 else it.getLong(5))
                    put("category_name", it.getString(6) ?: "")
                })
            }
            arr
        }
    }

    fun addFavorite(providerId: Long, channelId: Long): Boolean {
        val maxPos = db.rawQuery(
            "SELECT COALESCE(MAX(position), 0) FROM favorites WHERE provider_id = ?",
            arrayOf(providerId.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        db.execSQL(
            "INSERT OR IGNORE INTO favorites (provider_id, content_id, content_type, position, group_id, group_key, added_at) VALUES (?, ?, 'LIVE', ?, NULL, 0, ?)",
            arrayOf(providerId, channelId, maxPos + 1024, System.currentTimeMillis())
        )
        return true
    }

    fun removeFavorite(providerId: Long, channelId: Long): Boolean {
        db.execSQL(
            "DELETE FROM favorites WHERE provider_id = ? AND content_id = ? AND content_type = 'LIVE'",
            arrayOf(providerId, channelId)
        )
        return true
    }

    /**
     * Get currently airing program for multiple channels at once (batch EPG lookup).
     * Returns a map of channel_id → program title.
     */
    fun getNowPlayingBatch(providerId: Long, channelIds: List<Long>, nowSecs: Long): JSONObject {
        if (channelIds.isEmpty()) return JSONObject()
        // Get epg_channel_id for the requested channels
        val placeholders = channelIds.joinToString(",") { "?" }
        val args = mutableListOf(providerId.toString())
        args.addAll(channelIds.map { it.toString() })
        val channelEpgMap = db.rawQuery(
            "SELECT id, epg_channel_id FROM channels WHERE provider_id = ? AND id IN ($placeholders)",
            args.toTypedArray()
        ).use {
            val map = mutableMapOf<String, Long>() // epg_id → channel_db_id
            while (it.moveToNext()) {
                val chId = it.getLong(0)
                val epgId = it.getString(1) ?: continue
                if (epgId.isNotBlank()) map[epgId] = chId
            }
            map
        }
        if (channelEpgMap.isEmpty()) return JSONObject()

        val result = JSONObject()
        val epgPlaceholders = channelEpgMap.keys.joinToString(",") { "?" }
        val epgArgs = mutableListOf(providerId.toString(), nowSecs.toString(), nowSecs.toString())
        epgArgs.addAll(channelEpgMap.keys)
        db.rawQuery(
            "SELECT channel_id, title FROM programs WHERE provider_id = ? AND start_time <= ? AND end_time > ? AND channel_id IN ($epgPlaceholders) ORDER BY start_time",
            epgArgs.toTypedArray()
        ).use {
            while (it.moveToNext()) {
                val epgId = it.getString(0)
                val title = it.getString(1)
                val chId = channelEpgMap[epgId] ?: continue
                if (!result.has(chId.toString())) {
                    result.put(chId.toString(), title)
                }
            }
        }
        return result
    }

    fun updateProviderServerUrl(providerId: Long, newUrl: String) {
        db.execSQL("UPDATE providers SET server_url = ? WHERE id = ?", arrayOf(newUrl, providerId))
    }

    fun getEpgForChannel(providerId: Long, channelEpgId: String, nowSecs: Long): JSONArray {
        return db.rawQuery(
            "SELECT p.title, p.description, p.start_time, p.end_time, p.genre, p.category " +
            "FROM programs p WHERE p.provider_id = ? AND p.channel_id = ? AND p.end_time > ? ORDER BY p.start_time LIMIT 10",
            arrayOf(providerId.toString(), channelEpgId, nowSecs.toString())
        ).use {
            val arr = JSONArray()
            while (it.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("title", it.getString(0) ?: "")
                    put("description", it.getString(1) ?: "")
                    put("start_time", it.getLong(2))
                    put("end_time", it.getLong(3))
                    put("genre", it.getString(4) ?: "")
                    put("category", it.getString(5) ?: "")
                })
            }
            arr
        }
    }

    fun searchEpg(providerId: Long, query: String, nowSecs: Long): JSONArray {
        return db.rawQuery(
            "SELECT p.title, p.description, p.start_time, p.end_time, p.channel_id, c.name as channel_name, c.id as channel_db_id " +
            "FROM programs p LEFT JOIN channels c ON c.epg_channel_id = p.channel_id AND c.provider_id = p.provider_id " +
            "WHERE p.provider_id = ? AND p.title LIKE ? AND p.end_time > ? ORDER BY p.start_time LIMIT 30",
            arrayOf(providerId.toString(), "%$query%", nowSecs.toString())
        ).use {
            val arr = JSONArray()
            while (it.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("title", it.getString(0) ?: "")
                    put("description", it.getString(1) ?: "")
                    put("start_time", it.getLong(2))
                    put("end_time", it.getLong(3))
                    put("epg_channel_id", it.getString(4) ?: "")
                    put("channel_name", it.getString(5) ?: "")
                    put("channel_id", if (it.isNull(6)) 0 else it.getLong(6))
                })
            }
            arr
        }
    }

    fun getCurrentlyAiring(providerId: Long, nowSecs: Long, limit: Int): JSONArray {
        return db.rawQuery(
            "SELECT p.title, p.description, p.start_time, p.end_time, p.channel_id, p.genre, c.name as channel_name, c.id as channel_db_id " +
            "FROM programs p LEFT JOIN channels c ON c.epg_channel_id = p.channel_id AND c.provider_id = p.provider_id " +
            "WHERE p.provider_id = ? AND p.start_time <= ? AND p.end_time > ? ORDER BY p.start_time LIMIT ?",
            arrayOf(providerId.toString(), nowSecs.toString(), nowSecs.toString(), limit.toString())
        ).use {
            val arr = JSONArray()
            while (it.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("title", it.getString(0) ?: "")
                    put("description", it.getString(1) ?: "")
                    put("start_time", it.getLong(2))
                    put("end_time", it.getLong(3))
                    put("epg_channel_id", it.getString(4) ?: "")
                    put("genre", it.getString(5) ?: "")
                    put("channel_name", it.getString(6) ?: "")
                    put("channel_id", if (it.isNull(7)) 0 else it.getLong(7))
                })
            }
            arr
        }
    }

    private fun Cursor.toChannelArray(): JSONArray {
        return this.use {
            val arr = JSONArray()
            while (it.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("id", it.getLong(0)); put("stream_id", it.getLong(1))
                    put("name", it.getString(2))
                    put("category_id", if (it.isNull(3)) 0 else it.getLong(3))
                    put("category_name", it.getString(4) ?: "")
                    put("logo_url", it.getString(5) ?: "")
                    put("epg_channel_id", it.getString(6) ?: "")
                    put("number", it.getInt(7))
                })
            }
            arr
        }
    }
}
