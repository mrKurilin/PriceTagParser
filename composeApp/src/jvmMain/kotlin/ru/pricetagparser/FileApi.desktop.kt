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

private val filesDirectory = File("files")

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
