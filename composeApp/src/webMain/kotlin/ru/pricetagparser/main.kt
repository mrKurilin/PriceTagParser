package ru.pricetagparser

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

private const val COMPOSE_ROOT_ID = "composeAppRoot"

/**
 * Compose Web bootstrap.
 *
 * Тяжёлый `composeApp.js` подгружается лениво из `index.html` только после
 * клика на «Старт», поэтому здесь нам нужно лишь смонтировать Compose в
 * заранее подготовленный контейнер. Loader в `index.html` скрывается
 * автоматически по MutationObserver, как только Compose добавит свои узлы.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = COMPOSE_ROOT_ID) {
        App()
    }
}
