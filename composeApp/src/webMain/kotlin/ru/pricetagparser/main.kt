package ru.pricetagparser

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

private const val COMPOSE_ROOT_ID = "composeAppRoot"

/**
 * Compose Web bootstrap.
 *
 * `index.html` загружает `composeApp.js` напрямую, а стартовый экран и переход
 * к основной логике теперь управляются внутри Compose.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = COMPOSE_ROOT_ID) {
        App()
    }
}
