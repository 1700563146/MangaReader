package io.github.saonte.mangareader.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ShelfRoute

@Serializable
data object HistoryRoute

@Serializable
data object BrowseRoute

@Serializable
data object SettingsRoute

/**
 * 后续里程碑的二级页面（M1/M3 逐步接入）。
 */
@Serializable
data class SearchRoute(val keyword: String = "")

@Serializable
data class DetailRoute(val sourceType: Long, val cid: String)

@Serializable
data class ReaderRoute(val comicId: Long, val chapterPath: String)
