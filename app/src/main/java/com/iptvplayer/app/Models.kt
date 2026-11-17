package com.iptvplayer.app

/** Une catégorie (groupe) de contenu. */
data class Category(val id: String, val name: String)

/** Type de contenu. */
enum class Kind { LIVE, VOD, SERIES }

/**
 * Un élément affiché dans la grille.
 * [streamUrl] est rempli pour les chaînes live et les films (lecture directe).
 * Pour une série, [seriesId] sert à charger les saisons/épisodes.
 */
data class Item(
    val id: String,
    val name: String,
    val logo: String?,
    val kind: Kind,
    val categoryId: String?,
    val streamUrl: String? = null,
    val seriesId: String? = null,
    val containerExt: String? = null
)

/** Un épisode d'une série. */
data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val url: String
)
