package ru.pricetagparser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val YANDEX_COMPUTE_API_BASE_URL = "https://compute.api.cloud.yandex.net/compute/v1"

private val filesDirectory = File("files")
private val httpClient = HttpClient.newHttpClient()

internal actual suspend fun fetchFiles(): List<PricesFile> = withContext(Dispatchers.IO) {
    filesDirectory.mkdirs()
    val csvNames = filesDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
        .map { it.nameWithoutExtension }
        .toSet()

    filesDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && !it.extension.equals("csv", ignoreCase = true) }
        .sortedBy { it.name.lowercase() }
        .map { file ->
            PricesFile(
                name = file.name,
                csvName = "${file.nameWithoutExtension}.csv",
                hasCsv = file.nameWithoutExtension in csvNames,
            )
        }
}

internal actual suspend fun fetchBackendStatus(): BackendStatus = withContext(Dispatchers.IO) {
    val instanceId = yandexEnv("YANDEX_INSTANCE_ID")
    val folderId = yandexEnv("YANDEX_FOLDER_ID")
    val response = sendYandexRequest(
        uri = "$YANDEX_COMPUTE_API_BASE_URL/instances?folderId=$folderId",
    )
    BackendStatus(response.body().instancePowerStatus(instanceId))
}

internal actual suspend fun startBackend(): BackendStatus = withContext(Dispatchers.IO) {
    val instanceId = yandexEnv("YANDEX_INSTANCE_ID")
    sendYandexRequest(
        uri = "$YANDEX_COMPUTE_API_BASE_URL/instances/$instanceId:start",
        method = "POST",
    )
    BackendStatus(BackendPowerStatus.Starting)
}

internal actual suspend fun stopBackend(): BackendStatus = withContext(Dispatchers.IO) {
    val instanceId = yandexEnv("YANDEX_INSTANCE_ID")
    sendYandexRequest(
        uri = "$YANDEX_COMPUTE_API_BASE_URL/instances/$instanceId:stop",
        method = "POST",
    )
    BackendStatus(BackendPowerStatus.Stopping)
}

@Composable
internal actual fun CompletedFileActions(file: PricesFile) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { openCsvFile(file.name) }) {
            Text("Открыть файл")
        }

        OutlinedButton(onClick = { openCsvParentDirectory(file.name) }) {
            Text("Открыть папку")
        }
    }
}

internal actual fun downloadCsv(fileName: String) {
    openCsvFile(fileName)
}

internal actual fun pickAndUploadFile(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onUploadStarted: (String) -> Unit,
    onUploadProgress: (String, Int) -> Unit,
    onUploaded: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val selectedFile = pickFile() ?: return
    val fileName = selectedFile.name
    scope.launchSafely(
        fileName = fileName,
        onUploadingChanged = onUploadingChanged,
        onError = onError,
    ) {
        onUploadStarted(fileName)
        val targetFile = copyToFilesDirectory(
            source = selectedFile,
            onProgress = { progress -> onUploadProgress(fileName, progress) },
        )
        scope.launch(Dispatchers.IO) {
            PriceFileProcessor.process(targetFile)
        }
        onUploaded(fileName)
    }
}

private fun sendYandexRequest(uri: String, method: String = "GET"): HttpResponse<String> {
    val request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .header("Authorization", "Bearer ${yandexEnv("YANDEX_IAM_TOKEN")}")
        .method(method, HttpRequest.BodyPublishers.noBody())
        .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() !in 200..299) error("Yandex Compute API request failed")
    return response
}

private fun yandexEnv(name: String): String =
    checkNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name is not configured" }

private fun String.instancePowerStatus(instanceId: String): BackendPowerStatus {
    val idIndex = indexOf("\"id\":\"$instanceId\"")
        .takeIf { it >= 0 }
        ?: indexOf("\"id\" : \"$instanceId\"")
            .takeIf { it >= 0 }
        ?: return BackendPowerStatus.Unknown
    val yandexStatus = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"")
        .find(substring(idIndex))
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    return when (yandexStatus) {
        "RUNNING" -> BackendPowerStatus.Running
        "STOPPED" -> BackendPowerStatus.Stopped
        "STARTING", "PROVISIONING" -> BackendPowerStatus.Starting
        "STOPPING" -> BackendPowerStatus.Stopping
        else -> BackendPowerStatus.Unknown
    }
}

private fun openCsvFile(fileName: String) {
    val csvFile = csvFileFor(fileName)
    if (csvFile.exists()) {
        Desktop.getDesktop().open(csvFile)
    }
}

private fun openCsvParentDirectory(fileName: String) {
    val csvFile = csvFileFor(fileName)
    val directory = csvFile.parentFile ?: filesDirectory
    if (directory.exists()) {
        Desktop.getDesktop().open(directory)
    }
}

private fun csvFileFor(fileName: String): File {
    val safeName = File(fileName).name
    return filesDirectory.resolve("${safeName.substringBeforeLast('.')}.csv")
}

private fun pickFile(): File? {
    val frame = Frame()
    return try {
        val dialog = FileDialog(frame, "Выберите файл", FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.file ?: return null
        File(dialog.directory, file)
    } finally {
        frame.dispose()
    }
}

private suspend fun copyToFilesDirectory(
    source: File,
    onProgress: (Int) -> Unit,
): File = withContext(Dispatchers.IO) {
    filesDirectory.mkdirs()
    val target = filesDirectory.resolve(source.name)
    if (source.canonicalPath == target.canonicalPath) {
        onProgress(100)
        return@withContext target
    }

    val totalBytes = source.length().coerceAtLeast(1L)
    var copiedBytes = 0L
    source.inputStream().use { input ->
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytes = input.read(buffer)
                if (bytes < 0) break
                output.write(buffer, 0, bytes)
                copiedBytes += bytes
                onProgress(((copiedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99))
            }
        }
    }
    onProgress(100)
    target
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
