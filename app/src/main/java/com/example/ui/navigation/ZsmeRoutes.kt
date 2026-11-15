package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface ZsmeRoute {
    @Serializable
    data object Substitutions : ZsmeRoute

    @Serializable
    data object Timetable : ZsmeRoute

    @Serializable
    data object Messenger : ZsmeRoute

    @Serializable
    data object Settings : ZsmeRoute

    @Serializable
    data class PortalWebView(
        val url: String,
        val title: String,
        val autoFillKey: String? = null
    ) : ZsmeRoute
}
