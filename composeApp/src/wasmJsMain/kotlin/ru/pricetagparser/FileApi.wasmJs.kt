package ru.pricetagparser

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.xhr.FormData
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

@OptIn(ExperimentalWasmJsInterop::class)
internal actual suspend fun fetchFiles(): List<PriceFile> {
    val response = window.fetch("/api/files").await<Response>()
    val text = response.text().unsafeCast<Promise<JsAny?>>().await<JsAny?>().toString()
    return text.parseFilesJson()
}

internal actual fun downloadCsv(fileName: String) {
    window.location.href = "/api/files/$fileName/download"
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun pickAndUploadFile(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onUploaded: () -> Unit,
    onError: () -> Unit,
) {
    val input = (document.createElement("input") as HTMLInputElement).apply {
        type = "file"
    }
    input.onchange = {
        val file = input.files?.asList()?.firstOrNull()
        if (file != null) {
            scope.launch {
                onUploadingChanged(true)
                try {
                    val formData = FormData()
                    formData.append("file", file)
                    val response = window.fetch(
                        input = "/api/upload",
                        init = RequestInit(
                            method = "POST",
                            body = formData,
                        ),
                    ).await<Response>()
                    if (!response.ok) error("Upload failed")
                    onUploaded()
                } catch (_: Throwable) {
                    onError()
                } finally {
                    onUploadingChanged(false)
                }
            }
        }
    }
    input.click()
}

private fun String.parseFilesJson(): List<PriceFile> {
    val trimmed = trim()
    if (trimmed.length <= 2) return emptyList()
    return trimmed
        .removePrefix("[")
        .removeSuffix("]")
        .split("},{")
        .map { item -> item.removePrefix("{").removeSuffix("}") }
        .map { item ->
            val values = item
                .split(",")
                .associate { field ->
                    val parts = field.split(":", limit = 2)
                    parts.first().trim('"') to parts.last().trim('"')
                }
            PriceFile(
                name = values.getValue("name"),
                csvName = values.getValue("csvName"),
                hasCsv = values["hasCsv"].toBoolean(),
            )
        }
}
