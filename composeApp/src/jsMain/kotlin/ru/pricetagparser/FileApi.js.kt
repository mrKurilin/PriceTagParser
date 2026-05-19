package ru.pricetagparser

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume

internal actual val backendControlApiBaseUrl: String = ""

internal actual fun loadBackendInstanceId(): String = ""

internal actual fun loadYandexIamToken(): String = ""

internal actual suspend fun fetchFiles(): List<PricesFile> = fetchBackendFiles()

internal actual suspend fun fetchProcessingLogs(): String = fetchBackendProcessingLogs()

internal actual suspend fun fetchBackendStatus(): BackendStatus = fetchBackendStatusViaApi()

internal actual suspend fun startBackend(): BackendStatus = startBackendViaApi()

internal actual suspend fun stopBackend(): BackendStatus = stopBackendViaApi()

@Composable
internal actual fun CompletedFileActions(file: PricesFile) {
    Button(onClick = { downloadCsv(file.name) }) {
        Text("Скачать результат (csv)")
    }
}

internal actual fun downloadCsv(fileName: String) {
    window.location.href = "$FILE_PROCESSING_API_BASE_URL$FILES_API_PATH/$fileName/download"
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
                    url = "$FILE_PROCESSING_API_BASE_URL$FILE_UPLOAD_API_PATH",
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
