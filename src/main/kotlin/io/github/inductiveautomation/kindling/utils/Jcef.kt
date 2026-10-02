package io.github.inductiveautomation.kindling.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import kotlin.io.path.Path

/**
 * Lazily installs (downloading Chromium natives on first use, ~150MB) and initializes JCEF.
 */
object Jcef {
    private val baseDir = Path(System.getProperty("user.home"), ".kindling")

    private val _progress = MutableStateFlow(EnumProgress.LOCATING to EnumProgress.NO_ESTIMATION)
    val progress: StateFlow<Pair<EnumProgress, Float>> = _progress.asStateFlow()

    private val builder = CefAppBuilder().apply {
        setInstallDir(baseDir.resolve("jcef").toFile())
        setProgressHandler { state, percent -> _progress.value = state to percent }
        cefSettings.windowless_rendering_enabled = false
        cefSettings.root_cache_path = baseDir.resolve("jcef-cache").toString()
        setAppHandler(object : MavenCefAppHandlerAdapter() {})
    }

    /**
     * Blocks until JCEF is installed and initialized - do not call from the EDT.
     */
    val app: CefApp by lazy { builder.build() }
}
