package com.iptvplayer.app

import android.content.Context

/** Stocke la connexion de l'utilisateur localement (aucune donnée envoyée ailleurs). */
object Prefs {
    private const val FILE = "oztv"

    const val TYPE_XTREAM = "xtream"
    const val TYPE_M3U = "m3u"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(c: Context, type: String, host: String, user: String, pass: String, m3u: String) {
        sp(c).edit()
            .putString("type", type)
            .putString("host", host.trim().trimEnd('/'))
            .putString("user", user.trim())
            .putString("pass", pass.trim())
            .putString("m3u", m3u.trim())
            .apply()
    }

    fun type(c: Context) = sp(c).getString("type", null)
    fun host(c: Context) = sp(c).getString("host", "") ?: ""
    fun user(c: Context) = sp(c).getString("user", "") ?: ""
    fun pass(c: Context) = sp(c).getString("pass", "") ?: ""
    fun m3u(c: Context) = sp(c).getString("m3u", "") ?: ""

    fun isLoggedIn(c: Context) = type(c) != null

    fun pin(c: Context) = sp(c).getString("pin", "0000") ?: "0000"
    fun setPin(c: Context, code: String) = sp(c).edit().putString("pin", code).apply()

    fun logout(c: Context) {
        val keepPin = pin(c)
        sp(c).edit().clear().putString("pin", keepPin).apply()
    }

    /** Base Xtream player_api. */
    fun apiBase(c: Context) = "${host(c)}/player_api.php?username=${user(c)}&password=${pass(c)}"
}
