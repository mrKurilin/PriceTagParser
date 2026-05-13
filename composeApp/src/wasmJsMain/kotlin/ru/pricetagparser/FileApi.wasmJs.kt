package ru.pricetagparser

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.fetch.Response
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

@OptIn(ExperimentalWasmJsInterop::class)
internal actual suspend fun fetchFiles(): List<PriceFile> {
    val url = apiUrl("/api/files")
    println("[FileApi] fetchFiles: origin=${window.location.origin}, url=$url")
    val response = window.fetch(url).await<Response>()
    println("[FileApi] fetchFiles: response ok=${response.ok}, status=${response.status}")
    val text = response.text().unsafeCast<Promise<JsAny?>>().await<JsAny?>().toString()
    return text.parseFilesJson()
}

internal actual fun downloadCsv(fileName: String) {
    val url = apiUrl("/api/files/$fileName/download")
    println("[FileApi] downloadCsv: fileName=$fileName, url=$url")
    window.location.href = url
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun pickAndUploadFile(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onUploadStarted: (String) -> Unit,
    onUploadProgress: (String, Int) -> Unit,
    onUploaded: () -> Unit,
    onError: (String) -> Unit,
) {
    println("[FileApi] pickAndUploadFile: create file input")
    val input = (document.createElement("input") as HTMLInputElement).apply {
        type = "file"
    }
    input.onchange = {
        println("[FileApi] pickAndUploadFile: input onchange fired")
        val file = input.files?.asList()?.firstOrNull()
        if (file != null) {
            println("[FileApi] pickAndUploadFile: selected file name=${file.name}, size=${file.size}, type=${file.type}")
            scope.launch {
                println("[FileApi] upload: coroutine started for ${file.name}")
                onUploadingChanged(true)
                onUploadStarted(file.name)
                try {
                    val url = apiUrl("/api/upload")
                    println("[FileApi] upload: xhr start url=$url, origin=${window.location.origin}")
                    uploadFile(
                        url = url,
                        file = file,
                        onProgress = { progress -> onUploadProgress(file.name, progress) },
                    )
                    println("[FileApi] upload: completed for ${file.name}")
                    onUploaded()
                } catch (throwable: Throwable) {
                    println("[FileApi] upload: failed for ${file.name}: ${throwable.message}")
                    onError(file.name)
                } finally {
                    println("[FileApi] upload: uploading=false for ${file.name}")
                    onUploadingChanged(false)
                }
            }
        } else {
            println("[FileApi] pickAndUploadFile: no file selected")
        }
    }
    println("[FileApi] pickAndUploadFile: click input")
    input.click()
}

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun uploadFile(
    url: String,
    file: org.w3c.files.File,
    onProgress: (Int) -> Unit,
) {
    suspendCancellableCoroutine { continuation ->
        val request = XMLHttpRequest()
        request.open("POST", url)
        request.upload.onprogress = { event ->
            if (event.lengthComputable) {
                val loaded = event.loaded.toDouble()
                val total = event.total.toDouble()
                onProgress(((loaded / total) * 100).toInt().coerceIn(0, 99))
            }
        }
        request.onload = {
            println("[FileApi] upload: xhr response status=${request.status}, statusText=${request.statusText}")
            if (request.status in 200..299) {
                onProgress(100)
                continuation.resume(Unit)
            } else {
                continuation.cancel(Throwable("Upload failed with status ${request.status}"))
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

private fun apiUrl(path: String): String =
    if (window.location.port == SERVER_PORT.toString()) {
        path
    } else {
        "http://localhost:$SERVER_PORT$path"
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
