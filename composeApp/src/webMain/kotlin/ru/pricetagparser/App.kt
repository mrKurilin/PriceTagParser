package ru.pricetagparser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class PricesFile(
    val name: String,
    val csvName: String,
    val hasCsv: Boolean,
    val uploadProgress: Int? = null,
    val uploadFailed: Boolean = false,
)

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var files by remember { mutableStateOf(emptyList<PricesFile>()) }
        var isLoading by remember { mutableStateOf(true) }
        var isUploading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        fun replaceFile(file: PricesFile) {
            files = listOf(file) + files.filterNot { it.name == file.name }
        }

        suspend fun loadFiles() {
            isLoading = true
            errorMessage = null
            try {
                files = fetchFiles()
            } catch (_: Throwable) {
                errorMessage = "Не удалось загрузить список файлов"
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            loadFiles()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF6F7FB),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "PriceTagParcer",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    FilesCard(
                        files = files,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRetry = {
                            scope.launchSafely {
                                loadFiles()
                            }
                        },
                    )
                }

                UploadButton(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isUploading = isUploading,
                    onClick = {
                        startUpload(
                            scope = scope,
                            onUploadingChanged = { isUploading = it },
                            onFileChanged = ::replaceFile,
                            onUploaded = {
                                scope.launchSafely {
                                    loadFiles()
                                }
                            },
                            onError = { errorMessage = "Не удалось загрузить файл" },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FilesCard(
    files: List<PricesFile>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        when {
            isLoading && files.isEmpty() -> LoadingState()
            errorMessage != null && files.isEmpty() -> ErrorState(
                message = errorMessage,
                onRetry = onRetry,
            )

            files.isEmpty() -> EmptyState()
            else -> FileList(files = files)
        }
    }
}

private fun startUpload(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onFileChanged: (PricesFile) -> Unit,
    onUploaded: () -> Unit,
    onError: () -> Unit,
) {
    fun uploadFileState(
        fileName: String,
        uploadProgress: Int? = null,
        uploadFailed: Boolean = false,
    ) = PricesFile(
        name = fileName,
        csvName = fileName.substringBeforeLast('.') + ".csv",
        hasCsv = false,
        uploadProgress = uploadProgress,
        uploadFailed = uploadFailed,
    )

    pickAndUploadFile(
        scope = scope,
        onUploadingChanged = onUploadingChanged,
        onUploadStarted = { fileName ->
            onFileChanged(uploadFileState(fileName, uploadProgress = 0))
        },
        onUploadProgress = { fileName, progress ->
            onFileChanged(uploadFileState(fileName, uploadProgress = progress))
        },
        onUploaded = { onUploaded() },
        onError = { fileName ->
            onError()
            if (fileName.isNotBlank()) {
                onFileChanged(uploadFileState(fileName, uploadFailed = true))
            }
        },
    )
}

@Composable
private fun UploadButton(
    modifier: Modifier = Modifier,
    isUploading: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        modifier = modifier.size(64.dp),
        enabled = !isUploading,
        onClick = onClick,
    ) {
        if (isUploading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FileList(files: List<PricesFile>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(files) { index, file ->
            FileRow(
                index = index + 1,
                file = file,
            )
        }
    }
}

@Suppress("unused")
@Preview
@Composable
private fun FileListPreview() {
    MaterialTheme {
        Surface(color = Color(0xFFF6F7FB)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                FileList(
                    files = listOf(
                        PricesFile(
                            name = "price-tag-photo.jpg",
                            csvName = "price-tag-photo.csv",
                            hasCsv = true,
                        ),
                        PricesFile(
                            name = "shelf-video.mp4",
                            csvName = "shelf-video.csv",
                            hasCsv = false,
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun FileRow(index: Int, file: PricesFile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilePreviewIcon(fileName = file.name)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            modifier = Modifier.weight(1f),
            text = "$index. ${file.name}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.width(16.dp))

        when {
            file.uploadFailed -> Text(
                text = "Ошибка загрузки",
                color = MaterialTheme.colorScheme.error,
            )

            file.uploadProgress != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Загрузка ${file.uploadProgress}%")
            }

            file.hasCsv -> {
                Button(onClick = { downloadCsv(file.name) }) {
                    Text("Скачать")
                }
            }

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Обработка")
                }
            }
        }
    }
}

@Composable
private fun FilePreviewIcon(fileName: String) {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    val isImage = extension in imageExtensions
    val isVideo = extension in videoExtensions
    val backgroundColor = when {
        isImage -> Color(0xFFE8F5E9)
        isVideo -> Color(0xFFE3F2FD)
        else -> Color(0xFFF1F3F4)
    }
    val borderColor = when {
        isImage -> Color(0xFF66BB6A)
        isVideo -> Color(0xFF42A5F5)
        else -> Color(0xFFB0BEC5)
    }
    val label = when {
        isImage -> "IMG"
        isVideo -> "VID"
        else -> "FILE"
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = borderColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val imageExtensions = setOf(
    "jpg",
    "jpeg",
    "png",
    "gif",
    "webp",
    "bmp",
)

private val videoExtensions = setOf(
    "mp4",
    "mov",
    "avi",
    "mkv",
    "webm",
)

@Suppress("unused")
@Preview
@Composable
private fun CompletedFileRowPreview() {
    MaterialTheme {
        Surface(color = Color.White) {
            FileRow(
                index = 1,
                file = PricesFile(
                    name = "price-tag-photo.jpg",
                    csvName = "price-tag-photo.csv",
                    hasCsv = true,
                ),
            )
        }
    }
}

@Suppress("unused")
@Preview
@Composable
private fun ProcessingFileRowPreview() {
    MaterialTheme {
        Surface(color = Color.White) {
            FileRow(
                index = 2,
                file = PricesFile(
                    name = "shelf-video.mp4",
                    csvName = "shelf-video.csv",
                    hasCsv = false,
                ),
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Загрузка файлов")
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        modifier = Modifier.padding(32.dp),
        text = "В папке files пока нет файлов для обработки",
    )
}

private fun CoroutineScope.launchSafely(
    onError: () -> Unit = {},
    block: suspend () -> Unit,
) {
    launch {
        try {
            block()
        } catch (_: Throwable) {
            onError()
        }
    }
}

internal expect suspend fun fetchFiles(): List<PricesFile>

internal expect fun downloadCsv(fileName: String)

internal expect fun pickAndUploadFile(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onUploadStarted: (String) -> Unit,
    onUploadProgress: (String, Int) -> Unit,
    onUploaded: (String) -> Unit,
    onError: (String) -> Unit,
)
