package com.iptvplayer.app

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Télécharge et analyse une playlist M3U/M3U8. */
class M3uParser(private val c: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Result(val categories: List<Category>, val items: List<Item>)

    fun load(): Result {
        val url = Prefs.m3u(c)
        val text = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            r.body?.string() ?: ""
        }
        return parse(text)
    }

    private fun parse(text: String): Result {
        val items = ArrayList<Item>()
        val groups = LinkedHashSet<String>()
        var name = ""
        var logo: String? = null
        var group = ""
        var idx = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    name = line.substringAfterLast(',', "").trim()
                    logo = attr(line, "tvg-logo")
                    group = attr(line, "group-title") ?: ""
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    if (group.isNotEmpty()) groups.add(group)
                    items.add(
                        Item(
                            id = "m${idx++}",
                            name = name.ifBlank { "Chaîne ${idx}" },
                            logo = logo,
                            kind = Kind.LIVE,
                            categoryId = group,
                            streamUrl = line
                        )
                    )
                    name = ""; logo = null; group = ""
                }
            }
        }
        val cats = ArrayList<Category>()
        cats.add(Category("", "Tout"))
        groups.forEach { cats.add(Category(it, it)) }
        return Result(cats, items)
    }

    private fun attr(line: String, key: String): String? {
        val re = Regex("$key=\"([^\"]*)\"")
        return re.find(line)?.groupValues?.get(1)?.ifBlank { null }
    }
}
