package ru.pricetagparser

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.xhr.FormData
import kotlin.js.Json

internal actual suspend fun fetchFiles(): List<PriceFile> {
    val response = window.fetch("/api/files").await()
    if (!response.ok) error("Files request failed")
    val payload = response.json().await().unsafeCast<Array<Json>>()
    return payload.map { item ->
        PriceFile(
            name = item["name"] as String,
            csvName = item["csvName"] as String,
            hasCsv = item["hasCsv"] as Boolean,
        )
    }
}

internal actual fun downloadCsv(fileName: String) {
    window.location.href = "/api/files/$fileName/download"
}

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
                    val response = window.fetch("/api/upload", js("({ method: 'POST', body: formData })")).await()
                    if (!response.ok) error("Upload failed")
                    onUploaded()
                } catch (_: Throwable) {
                    onError()
                } finally {
                    onUploadingChanged(false)
                }
            }
        }
        null
    }
    input.click()
}
