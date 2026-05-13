package ru.pricetagparser

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.js.Json

internal actual suspend fun fetchFiles(): List<PricesFile> {
    val response = window.fetch("/api/files").await()
    if (!response.ok) error("Files request failed")
    val payload = response.json().await().unsafeCast<Array<Json>>()
    return payload.map { item ->
        PricesFile(
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
    onUploadStarted: (String) -> Unit,
    onUploadProgress: (String, Int) -> Unit,
    onUploaded: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val input = (document.createElement("input") as HTMLInputElement).apply {
        type = "file"
    }
    input.onchange = {
        val file = input.files?.asList()?.firstOrNull()
        if (file != null) {
            scope.launchSafely(
                fileName = file.name,
                onUploadingChanged = onUploadingChanged,
                onError = onError,
            ) {
                onUploadStarted(file.name)
                uploadFile(
                    file = file,
                    onProgress = { progress -> onUploadProgress(file.name, progress) },
                )
                onUploaded(file.name)
            }
        }
        null
    }
    input.click()
}

private suspend fun uploadFile(
    file: org.w3c.files.File,
    onProgress: (Int) -> Unit,
) {
    suspendCancellableCoroutine { continuation ->
        val request = XMLHttpRequest()
        request.open("POST", "/api/upload")
        request.upload.onprogress = { event ->
            if (event.lengthComputable) {
                val loaded = event.loaded.toDouble()
                val total = event.total.toDouble()
                onProgress(((loaded / total) * 100).toInt().coerceIn(0, 99))
            }
        }
        request.onload = {
            if (request.status in 200..299) {
                onProgress(100)
                continuation.resume(Unit)
            } else {
                continuation.cancel(Throwable("Upload failed"))
            }
        }
        request.onerror = {
            continuation.cancel(Throwable("Upload failed"))
        }
        request.onabort = {
            continuation.cancel(Throwable("Upload aborted"))
        }
        continuation.invokeOnCancellation {
            request.abort()
        }

        val formData = FormData()
        formData.append("file", file)
        request.send(formData)
    }
}

private fun CoroutineScope.launchSafely(
    fileName: String,
    onUploadingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    block: suspend () -> Unit,
) {
    launch {
        onUploadingChanged(true)
        try {
            block()
        } catch (_: Throwable) {
            onError(fileName)
        } finally {
            onUploadingChanged(false)
        }
    }
}
