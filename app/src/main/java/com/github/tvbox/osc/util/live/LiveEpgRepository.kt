package com.github.tvbox.osc.util.live

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Xml
import com.github.tvbox.osc.bean.LiveChannelItem
import com.github.tvbox.osc.util.HCallBack
import com.github.tvbox.osc.util.HeavyTaskUtil
import com.github.tvbox.osc.util.HttpClient
import com.github.tvbox.osc.util.OkGoHelper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * 直播 EPG 的异步加载、解析与缓存。
 *
 * 设计原则：
 * - 不进入直播首帧关键路径，调用方在切台后延迟请求；
 * - 先回调当天磁盘缓存，再请求网络；
 * - 网络刷新失败时保留缓存，不弹错误、不影响播放；
 * - 名称只用于查询，tvg-id 优先用于 XMLTV 匹配。
 */
object LiveEpgRepository {
    private const val DEFAULT_EPG_URL = "http://epg.51zmt.top:8000/api/diyp/?ch={name}&date={date}"
    private const val CACHE_DIR = "live-epg-cache"
    private const val CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    private val MAIN = Handler(Looper.getMainLooper())
    private val EPG_ZONE: TimeZone = TimeZone.getTimeZone("GMT+8:00")

    data class Program(
        val title: String,
        val startMs: Long,
        val endMs: Long
    )

    /** Java 直播页使用的 SAM 回调，避免 Kotlin Function1<Unit> 的返回类型桥接问题。 */
    fun interface Callback {
        fun onPrograms(programs: List<Program>)
    }

    @JvmStatic
    fun load(
        context: Context,
        channel: LiveChannelItem,
        dateMs: Long,
        requestTag: Any,
        callback: Callback
    ) {
        val cacheKey = buildCacheKey(channel, dateMs)
        HeavyTaskUtil.executeBigTask {
            val cached = readCache(context, cacheKey)
            if (cached.isNotEmpty()) MAIN.post { callback.onPrograms(cached) }
        }

        val queryNames = buildQueryNames(channel)
        val urls = buildEpgUrls(channel.epgUrl, queryNames, dateMs)
        if (urls.isEmpty()) {
            MAIN.post { callback.onPrograms(emptyList()) }
            return
        }
        requestCandidate(context, channel, dateMs, cacheKey, queryNames, urls, 0, requestTag, callback)
    }

    private fun requestCandidate(
        context: Context,
        channel: LiveChannelItem,
        dateMs: Long,
        cacheKey: String,
        queryNames: List<String>,
        urls: List<String>,
        index: Int,
        requestTag: Any,
        callback: Callback
    ) {
        if (index >= urls.size) {
            MAIN.post { callback.onPrograms(emptyList()) }
            return
        }
        HeavyTaskUtil.executeBigTask {
            val request = Request.Builder().url(urls[index]).tag(requestTag).build()
            val call = OkGoHelper.getDefaultClient().newCall(request)
            val content = try {
                call.execute().use { response ->
                    if (!response.isSuccessful || response.body == null) "" else {
                        decodeResponse(response.body!!.bytes())
                    }
                }
            } catch (_: Throwable) {
                ""
            }
            if (call.isCanceled()) return@executeBigTask
            if (content.isBlank()) {
                requestCandidate(context, channel, dateMs, cacheKey, queryNames, urls,
                    index + 1, requestTag, callback)
                return@executeBigTask
            }
            val programs = try {
                parseResponse(content, channel, queryNames, dateMs)
            } catch (_: Throwable) {
                emptyList()
            }
            if (programs.isNotEmpty()) {
                saveCache(context, cacheKey, programs)
                MAIN.post { callback.onPrograms(programs) }
            } else {
                requestCandidate(context, channel, dateMs, cacheKey, queryNames, urls,
                    index + 1, requestTag, callback)
            }
        }
    }

    private fun decodeResponse(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }

    @JvmStatic
    fun cancel(requestTag: Any?) {
        if (requestTag != null) HttpClient.cancel(requestTag)
    }

    fun normalizeChannelName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        var value = name.trim()
            .replace('－', '-')
            .replace('—', '-')
            .replace('　', ' ')
            .replace(Regex("\\s+"), " ")
        value = value.replace(Regex("(?i)\\s*(?:FHD|UHD|HD|4K|8K|超清|高清|标清|蓝光)\\s*$"), "")
        value = value.replace(Regex("(?i)\\s*(?:频道|直播)\\s*$"), "")
        val compact = value.replace(" ", "").replace("-", "")
        val cctv = Regex("(?i)^(CCTV\\d+(?:\\+|K)?)(?:[\\u4e00-\\u9fa5].*|$)").find(compact)
        if (cctv != null) return cctv.groupValues[1].uppercase(Locale.ROOT)
        return value.trim()
    }

    private fun preferredChannelName(channel: LiveChannelItem): String {
        val tvgName = channel.tvgName?.trim().orEmpty()
        if (tvgName.isNotEmpty()) return tvgName
        val normalized = normalizeChannelName(channel.channelName)
        return normalized.ifEmpty { channel.channelName?.trim().orEmpty() }
    }

    private fun buildQueryNames(channel: LiveChannelItem): List<String> {
        val names = LinkedHashSet<String>()
        fun add(value: String?) {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isNotEmpty()) names.add(trimmed)
        }
        add(channel.tvgName)
        add(normalizeChannelName(channel.tvgName))
        add(channel.channelName)
        add(normalizeChannelName(channel.channelName))
        add(channel.channelName?.substringBefore(' '))
        if (names.isEmpty()) names.add("")
        return names.toList()
    }

    private fun buildEpgUrls(configured: String?, queryNames: List<String>, dateMs: Long): List<String> {
        val urls = LinkedHashSet<String>()
        val configuredAddress = configured?.trim().orEmpty()
        if (configuredAddress.startsWith("http://") || configuredAddress.startsWith("https://")) {
            if (isXmlAddress(configuredAddress)) urls.add(configuredAddress)
            else for (name in queryNames) urls.add(buildEpgUrl(configuredAddress, name, dateMs))
        }
        for (name in queryNames) urls.add(buildEpgUrl(DEFAULT_EPG_URL, name, dateMs))
        urls.remove("")
        return urls.toList()
    }

    private fun buildEpgUrl(address: String, queryName: String, dateMs: Long): String {
        if (address.isEmpty()) return ""
        return try {
            if (address.contains("{name}") || address.contains("{date}")) {
                address.replace("{name}", encode(queryName))
                    .replace("{date}", formatDay(dateMs))
            } else if (isXmlAddress(address)) {
                address
            } else {
                address + (if (address.contains("?")) "&" else "?") +
                    "ch=" + encode(queryName) + "&date=" + formatDay(dateMs)
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun parseResponse(
        content: String,
        channel: LiveChannelItem,
        queryNames: List<String>,
        dateMs: Long
    ): List<Program> {
        val trimmed = content.trim()
        return if (trimmed.startsWith("<")) parseXml(trimmed, channel, queryNames.firstOrNull().orEmpty())
        else parseJson(trimmed, dateMs)
    }

    private fun parseJson(content: String, dateMs: Long): List<Program> {
        val root = JsonParser.parseString(content)
        if (!root.isJsonObject) return emptyList()
        val obj = root.asJsonObject
        val channelName = stringOf(obj, "channel_name", stringOf(obj, "channel", ""))
        if (unavailable(channelName)) return emptyList()
        val items = findProgramArray(obj) ?: return emptyList()
        val out = ArrayList<Program>()
        for (element in items) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val title = cleanTitle(stringOf(item, "title", stringOf(item, "name", "")))
            if (title.isEmpty() || unavailable(title)) continue
            val start = parseDate(dateMs, stringOf(item, "start",
                stringOf(item, "start_time", stringOf(item, "starttime", ""))))
            var end = parseDate(dateMs, stringOf(item, "end",
                stringOf(item, "end_time", stringOf(item, "endtime", ""))))
            if (start == null || end == null) continue
            if (end <= start) end += TimeUnit.DAYS.toMillis(1)
            out.add(Program(title, start, end))
        }
        return sorted(out)
    }

    private fun findProgramArray(obj: JsonObject): JsonArray? {
        for (key in arrayOf("epg_data", "data", "list")) {
            if (obj.has(key) && obj.get(key).isJsonArray) return obj.getAsJsonArray(key)
        }
        if (obj.has("data") && obj.get("data").isJsonObject) {
            val data = obj.getAsJsonObject("data")
            for (key in arrayOf("epg_data", "list", "programs")) {
                if (data.has(key) && data.get(key).isJsonArray) return data.getAsJsonArray(key)
            }
        }
        return null
    }

    private fun parseXml(content: String, channel: LiveChannelItem, queryName: String): List<Program> {
        val parser = Xml.newPullParser()
        parser.setInput(content.reader())
        val channelNames = LinkedHashMap<String, String>()
        val raw = ArrayList<RawProgram>()
        var channelId = ""
        var inChannel = false
        var inProgramme = false
        var current: RawProgram? = null
        var title = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> {
                        inChannel = true
                        channelId = parser.getAttributeValue(null, "id").orEmpty()
                    }
                    "display-name" -> if (inChannel && channelId.isNotEmpty()) {
                        val text = parser.nextText().trim()
                        if (text.isNotEmpty()) channelNames[channelId] = text
                    }
                    "programme" -> {
                        inProgramme = true
                        current = RawProgram(
                            parser.getAttributeValue(null, "channel").orEmpty(),
                            parseXmlDate(parser.getAttributeValue(null, "start")),
                            parseXmlDate(parser.getAttributeValue(null, "stop"))
                        )
                        title = StringBuilder()
                    }
                    "title" -> if (inProgramme) title.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "channel" -> inChannel = false
                    "programme" -> {
                        val program = current
                        val text = cleanTitle(title.toString())
                        if (program != null && program.startMs > 0L && program.endMs > program.startMs &&
                            text.isNotEmpty() && !unavailable(text)) {
                            raw.add(program.copy(title = text))
                        }
                        current = null
                        inProgramme = false
                    }
                }
            }
            event = parser.next()
        }

        val wantedIds = LinkedHashSet<String>()
        val wantedName = normalizeChannelName(queryName)
        val tvgId = channel.tvgId?.trim().orEmpty()
        if (tvgId.isNotEmpty()) wantedIds.add(tvgId)
        for ((id, displayName) in channelNames) {
            if (normalizeChannelName(displayName) == wantedName) wantedIds.add(id)
        }
        if (wantedIds.isEmpty()) {
            val normalizedChannel = normalizeChannelName(channel.channelName)
            for ((id, displayName) in channelNames) {
                if (normalizeChannelName(displayName) == normalizedChannel) wantedIds.add(id)
            }
        }
        val matched = raw.filter { wantedIds.isEmpty() || wantedIds.contains(it.channelId) }
        return sorted(matched.map { Program(it.title, it.startMs, it.endMs) })
    }

    private data class RawProgram(
        val channelId: String,
        val startMs: Long,
        val endMs: Long,
        val title: String = ""
    )

    private fun parseDate(dayMs: Long, value: String): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null
        val fullPatterns = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm")
        for (pattern in fullPatterns) {
            runCatching {
                SimpleDateFormat(pattern, Locale.ROOT).apply {
                    timeZone = EPG_ZONE
                    isLenient = false
                }.parse(text)?.time
            }.getOrNull()?.let { return it }
        }
        val day = formatDay(dayMs)
        for (pattern in arrayOf("HH:mm:ss", "HH:mm")) {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd " + pattern, Locale.ROOT).apply {
                    timeZone = EPG_ZONE
                    isLenient = false
                }.parse(day + " " + text)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun parseXmlDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        for (pattern in arrayOf("yyyyMMddHHmmss Z", "yyyyMMddHHmm Z", "yyyyMMddHHmmssZ", "yyyyMMddHHmmZ")) {
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = EPG_ZONE
                    isLenient = false
                }.parse(value.trim())?.time
            }.getOrNull()?.takeIf { it > 0L }?.let { return it }
        }
        return 0L
    }

    private fun sorted(programs: List<Program>): List<Program> =
        programs.filter { it.endMs > it.startMs }.sortedBy { it.startMs }

    private fun readCache(context: Context, key: String): List<Program> {
        val file = cacheFile(context, key)
        if (!file.isFile) return emptyList()
        return try {
            val root = JsonParser.parseString(file.readText(Charsets.UTF_8))
            if (!root.isJsonObject) return emptyList()
            val obj = root.asJsonObject
            val savedAt = if (obj.has("savedAt")) obj.get("savedAt").asLong else file.lastModified()
            if (savedAt <= 0L || System.currentTimeMillis() - savedAt > CACHE_MAX_AGE_MS) {
                file.delete()
                return emptyList()
            }
            val list = obj.getAsJsonArray("programs") ?: return emptyList()
            val out = ArrayList<Program>()
            for (element in list) {
                if (!element.isJsonObject) continue
                val p = element.asJsonObject
                out.add(Program(stringOf(p, "title", ""), p.get("start").asLong, p.get("end").asLong))
            }
            sorted(out)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveCache(context: Context, key: String, programs: List<Program>) {
        if (programs.isEmpty()) return
        val file = cacheFile(context, key)
        val arr = JsonArray()
        for (p in programs) {
            arr.add(JsonObject().apply {
                addProperty("title", p.title)
                addProperty("start", p.startMs)
                addProperty("end", p.endMs)
            })
        }
        val root = JsonObject().apply {
            addProperty("savedAt", System.currentTimeMillis())
            add("programs", arr)
        }
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString(), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun cacheFile(context: Context, key: String): File =
        File(File(context.applicationContext.cacheDir, CACHE_DIR), sha256(key) + ".json")

    private fun buildCacheKey(channel: LiveChannelItem, dayMs: Long): String {
        val id = channel.tvgId?.trim().orEmpty()
        val name = preferredChannelName(channel)
        return id + "|" + name + "|" + formatDay(dayMs)
    }

    private fun formatDay(timeMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = EPG_ZONE }.format(Date(timeMs))

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun isXmlAddress(address: String): Boolean {
        val lower = address.lowercase(Locale.ROOT)
        return lower.contains(".xml") || lower.contains(".gz") || lower.contains("xmltv")
    }

    private fun cleanTitle(value: String): String =
        value.replace(" --免费使用", "").replace("--免费使用", "").trim()

    private fun unavailable(value: String): Boolean =
        value.contains("未提供") || value.contains("暂无")

    private fun stringOf(obj: JsonObject, key: String, fallback: String): String {
        val value = obj.get(key) ?: return fallback
        return if (value.isJsonPrimitive) value.asString else fallback
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
