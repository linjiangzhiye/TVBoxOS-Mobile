package com.github.tvbox.osc.util.live

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

/**
 * 已解析直播源的小型磁盘缓存。
 *
 * 只缓存分组/频道/URL 结构，不缓存网络请求本身。直播页先展示缓存，再静默刷新，
 * 刷新结果只更新缓存，不打断当前正在播放的频道。
 */
object LiveSourceCache {
    private const val CACHE_DIR = "live-source-cache"
    private const val MAX_STALE_MS = 7L * 24L * 60L * 60L * 1000L

    data class Entry(val groups: JsonArray, val savedAt: Long)

    @JvmStatic
    fun load(context: Context, sourceUrl: String?): Entry? {
        if (sourceUrl.isNullOrBlank()) return null
        val file = cacheFile(context, sourceUrl)
        if (!file.isFile) return null
        synchronized(this) {
            return try {
                val root = JsonParser.parseString(file.readText(Charsets.UTF_8))
                if (!root.isJsonObject) return null
                val obj = root.asJsonObject
                val savedAt = if (obj.has("savedAt")) obj.get("savedAt").asLong else file.lastModified()
                if (savedAt <= 0L || System.currentTimeMillis() - savedAt > MAX_STALE_MS) {
                    file.delete()
                    return null
                }
                val groups = obj.getAsJsonArray("groups")
                if (groups == null || groups.size() == 0) null else Entry(groups, savedAt)
            } catch (_: Throwable) {
                file.delete()
                null
            }
        }
    }

    @JvmStatic
    fun save(context: Context, sourceUrl: String?, groups: JsonArray?) {
        if (sourceUrl.isNullOrBlank() || groups == null || groups.size() == 0) return
        val file = cacheFile(context, sourceUrl)
        val root = JsonObject().apply {
            addProperty("savedAt", System.currentTimeMillis())
            add("groups", groups)
        }
        synchronized(this) {
            try {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(root.toString(), Charsets.UTF_8)
                if (!tmp.renameTo(file)) {
                    file.writeText(root.toString(), Charsets.UTF_8)
                    tmp.delete()
                }
            } catch (_: Throwable) {
                // 缓存是纯加速项，失败绝不能影响直播播放。
            }
        }
    }

    private fun cacheFile(context: Context, sourceUrl: String): File {
        val dir = File(context.applicationContext.cacheDir, CACHE_DIR)
        return File(dir, sha256(sourceUrl) + ".json")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
