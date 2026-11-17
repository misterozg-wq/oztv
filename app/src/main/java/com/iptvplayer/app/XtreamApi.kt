package com.iptvplayer.app

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Client pour un serveur Xtream Codes (player_api.php). Tout en réseau, à appeler hors du thread UI. */
class XtreamApi(private val c: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(action: String): String {
        val url = "${Prefs.apiBase(c)}&action=$action"
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            return r.body?.string() ?: "[]"
        }
    }

    /** Vérifie les identifiants. Retourne null si OK, sinon un message d'erreur. */
    fun login(): String? = try {
        val url = Prefs.apiBase(c)
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val body = r.body?.string() ?: ""
            val info = JSONObject(body).optJSONObject("user_info")
            when {
                info == null -> "Réponse du serveur invalide"
                info.optString("auth") == "0" -> "Identifiant ou mot de passe incorrect"
                info.optString("status").equals("Expired", true) -> "Abonnement expiré"
                else -> null
            }
        }
    } catch (e: Exception) {
        "Connexion impossible : ${e.message}"
    }

    fun liveCategories() = categories("get_live_categories")
    fun vodCategories() = categories("get_vod_categories")
    fun seriesCategories() = categories("get_series_categories")

    private fun categories(action: String): List<Category> {
        val arr = JSONArray(get(action))
        val out = ArrayList<Category>(arr.length() + 1)
        out.add(Category("", "Tout"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Category(o.optString("category_id"), o.optString("category_name")))
        }
        return out
    }

    fun liveStreams(catId: String): List<Item> {
        val action = "get_live_streams" + if (catId.isNotEmpty()) "&category_id=$catId" else ""
        val arr = JSONArray(get(action))
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("stream_id")
            val url = "${Prefs.host(c)}/live/${Prefs.user(c)}/${Prefs.pass(c)}/$id.ts"
            out.add(Item(id, o.optString("name"), o.optString("stream_icon").ifBlank { null },
                Kind.LIVE, o.optString("category_id"), streamUrl = url))
        }
        return out
    }

    fun vodStreams(catId: String): List<Item> {
        val action = "get_vod_streams" + if (catId.isNotEmpty()) "&category_id=$catId" else ""
        val arr = JSONArray(get(action))
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("stream_id")
            val ext = o.optString("container_extension", "mp4")
            val url = "${Prefs.host(c)}/movie/${Prefs.user(c)}/${Prefs.pass(c)}/$id.$ext"
            out.add(Item(id, o.optString("name"), o.optString("stream_icon").ifBlank { null },
                Kind.VOD, o.optString("category_id"), streamUrl = url, containerExt = ext))
        }
        return out
    }

    fun seriesList(catId: String): List<Item> {
        val action = "get_series" + if (catId.isNotEmpty()) "&category_id=$catId" else ""
        val arr = JSONArray(get(action))
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("series_id")
            out.add(Item(id, o.optString("name"), o.optString("cover").ifBlank { null },
                Kind.SERIES, o.optString("category_id"), seriesId = id))
        }
        return out
    }

    /** Épisodes d'une série, aplatis et triés par saison. */
    fun episodes(seriesId: String): List<Episode> {
        val body = get("get_series_info&series_id=$seriesId")
        val root = JSONObject(body)
        val eps = root.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        val seasons = eps.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
        for (s in seasons) {
            val arr = eps.getJSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val ext = o.optString("container_extension", "mp4")
                val url = "${Prefs.host(c)}/series/${Prefs.user(c)}/${Prefs.pass(c)}/$id.$ext"
                val title = o.optString("title").ifBlank { "Épisode ${o.optString("episode_num")}" }
                out.add(Episode(id, title, s.toIntOrNull() ?: 0, url))
            }
        }
        return out
    }
}
