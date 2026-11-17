package com.iptvplayer.app

/**
 * File de lecture partagée entre la grille et le lecteur.
 * Évite de faire transiter une longue liste par un Intent.
 */
object Playlist {
    var items: List<Item> = emptyList()
    var index: Int = 0

    fun current(): Item? = items.getOrNull(index)
}
