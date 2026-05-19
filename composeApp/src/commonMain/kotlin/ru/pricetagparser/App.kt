package ru.pricetagparser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_INTERVAL_SECONDS = 6
private const val REFRESH_PROGRESS_TICK_MILLIS = 1_000L
private const val LOGS_EXPANDED_WEIGHT = 1.5f
private const val APP_TITLE = "PriceTagParser"

private val AppBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF061B3A),
        Color(0xFF0B5CAD),
        Color(0xFF57B7FF),
    ),
)

@Composable
fun App() {
    MaterialTheme {
        MainAppScreen()
    }
}

@Composable
private fun MainAppScreen() {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(emptyList<PricesFile>()) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var backendStatus by remember { mutableStateOf(BackendStatus(BackendPowerStatus.Unknown)) }
    var isBackendActionRunning by remember { mutableStateOf(false) }
    var backendErrorMessage by remember { mutableStateOf<String?>(null) }
    var processingLogs by remember { mutableStateOf("") }
    var isProcessingLogsExpanded by remember { mutableStateOf(true) }
    var processingLogsErrorMessage by remember { mutableStateOf<String?>(null) }
    var refreshProgress by remember { mutableStateOf(0f) }
    val isBackendRunning = backendStatus.powerStatus == BackendPowerStatus.Running

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundGradient),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isBackendRunning) {
                Text(
                    text = APP_TITLE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                RefreshProgressBar(progress = refreshProgress)

                Spacer(modifier = Modifier.height(16.dp))
            }

            BackendToggleCard(
                status = backendStatus,
                isActionRunning = isBackendActionRunning,
                errorMessage = backendErrorMessage,
                onEnabledChanged = { enabled ->
                    changeBackendPower(
                        scope = scope,
                        enabled = enabled,
                        onActionRunningChanged = { isBackendActionRunning = it },
                        onErrorChanged = { backendErrorMessage = it },
                        onStatusChanged = { backendStatus = it },
                    )
                },
            )

            if (isBackendRunning) {
                Spacer(modifier = Modifier.height(24.dp))

                FilesCard(
                    modifier = Modifier.weight(1f),
                    files = files,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onRetry = {
                        scope.launchSafely {
                            loadFiles(
                                onLoadingChanged = { isLoading = it },
                                onFilesLoaded = { files = it },
                                onErrorChanged = { errorMessage = it },
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                UploadButton(
                    isUploading = isUploading,
                    onClick = {
                        startUpload(
                            scope = scope,
                            onUploadingChanged = { isUploading = it },
                            onFileChanged = { file -> files = files.replacingFile(file) },
                            onUploaded = {
                                scope.launchSafely {
                                    loadFiles(
                                        onLoadingChanged = { isLoading = it },
                                        onFilesLoaded = { files = it },
                                        onErrorChanged = { errorMessage = it },
                                    )
                                }
                            },
                            onError = { errorMessage = "Не удалось загрузить файл" },
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                ProcessingLogsCard(
                    logs = processingLogs,
                    isExpanded = isProcessingLogsExpanded,
                    errorMessage = processingLogsErrorMessage,
                    onToggle = { isProcessingLogsExpanded = !isProcessingLogsExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isProcessingLogsExpanded) {
                                Modifier.weight(LOGS_EXPANDED_WEIGHT)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }

    // Загрузочные задачи запускаются только после нажатия «Старт»,
    // поэтому welcome-экран не ждёт сеть и текущую логику приложения.
    LaunchedEffect(Unit) {
        runRefreshLoop(
            onProgressChanged = { refreshProgress = it },
            onRefresh = {
                refreshData(
                    onFilesLoadingChanged = { isLoading = it },
                    onFilesLoaded = { files = it },
                    onFilesErrorChanged = { errorMessage = it },
                    onBackendStatusChanged = { backendStatus = it },
                    onBackendErrorChanged = { backendErrorMessage = it },
                    onProcessingLogsLoaded = { processingLogs = it },
                    onProcessingLogsErrorChanged = { processingLogsErrorMessage = it },
                )
            },
        )
    }
}

private fun List<PricesFile>.replacingFile(file: PricesFile): List<PricesFile> =
    listOf(file) + filterNot { it.name == file.name }

private suspend fun loadFiles(
    onLoadingChanged: (Boolean) -> Unit,
    onFilesLoaded: (List<PricesFile>) -> Unit,
    onErrorChanged: (String?) -> Unit,
) {
    onLoadingChanged(true)
    onErrorChanged(null)
    try {
        onFilesLoaded(fetchFiles())
    } catch (_: Throwable) {
        onErrorChanged("Не удалось загрузить список файлов")
    } finally {
        onLoadingChanged(false)
    }
}

private suspend fun loadProcessingLogs(
    onLogsLoaded: (String) -> Unit,
    onErrorChanged: (String?) -> Unit,
) {
    onErrorChanged(null)
    try {
        onLogsLoaded(fetchProcessingLogs())
    } catch (_: Throwable) {
        onErrorChanged("Не удалось загрузить логи обработки")
    }
}

private suspend fun loadBackendStatus(
    onStatusChanged: (BackendStatus) -> Unit,
    onErrorChanged: (String?) -> Unit,
) {
    onErrorChanged(null)
    try {
        onStatusChanged(fetchBackendStatus())
    } catch (error: Throwable) {
        onStatusChanged(BackendStatus(BackendPowerStatus.Unknown))
        onErrorChanged("Не удалось получить статус бэка:\n${error.message}")
    }
}

private suspend fun refreshData(
    onFilesLoadingChanged: (Boolean) -> Unit,
    onFilesLoaded: (List<PricesFile>) -> Unit,
    onFilesErrorChanged: (String?) -> Unit,
    onBackendStatusChanged: (BackendStatus) -> Unit,
    onBackendErrorChanged: (String?) -> Unit,
    onProcessingLogsLoaded: (String) -> Unit,
    onProcessingLogsErrorChanged: (String?) -> Unit,
) {
    var loadedBackendStatus = BackendStatus(BackendPowerStatus.Unknown)

    loadBackendStatus(
        onStatusChanged = { status ->
            loadedBackendStatus = status
            onBackendStatusChanged(status)
        },
        onErrorChanged = onBackendErrorChanged,
    )

    if (loadedBackendStatus.powerStatus != BackendPowerStatus.Running) {
        onFilesLoadingChanged(false)
        onFilesLoaded(emptyList())
        onFilesErrorChanged(null)
        onProcessingLogsLoaded("")
        onProcessingLogsErrorChanged(null)
        return
    }

    coroutineScope {
        launch {
            loadFiles(
                onLoadingChanged = onFilesLoadingChanged,
                onFilesLoaded = onFilesLoaded,
                onErrorChanged = onFilesErrorChanged,
            )
        }
        launch {
            loadProcessingLogs(
                onLogsLoaded = onProcessingLogsLoaded,
                onErrorChanged = onProcessingLogsErrorChanged,
            )
        }
    }
}

private suspend fun runRefreshLoop(
    onProgressChanged: (Float) -> Unit,
    onRefresh: suspend () -> Unit,
) {
    onRefresh()
    while (true) {
        repeat(REFRESH_INTERVAL_SECONDS) { second ->
            onProgressChanged(second.toFloat() / REFRESH_INTERVAL_SECONDS)
            delay(REFRESH_PROGRESS_TICK_MILLIS)
        }
        onProgressChanged(1f)
        onRefresh()
        onProgressChanged(0f)
    }
}

private fun changeBackendPower(
    scope: CoroutineScope,
    enabled: Boolean,
    onActionRunningChanged: (Boolean) -> Unit,
    onErrorChanged: (String?) -> Unit,
    onStatusChanged: (BackendStatus) -> Unit,
) {
    scope.launchSafely(
        onError = {
            onErrorChanged(
                if (enabled) "Не удалось включить бэк" else "Не удалось выключить бэк",
            )
            onActionRunningChanged(false)
        },
    ) {
        onActionRunningChanged(true)
        onErrorChanged(null)
        onStatusChanged(
            BackendStatus(
                if (enabled) BackendPowerStatus.Starting else BackendPowerStatus.Stopping,
            ),
        )
        onStatusChanged(if (enabled) startBackend() else stopBackend())
        onActionRunningChanged(false)
    }
}

@Composable
private fun RefreshProgressBar(progress: Float) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun startUpload(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onFileChanged: (PricesFile) -> Unit,
    onUploaded: () -> Unit,
    onError: () -> Unit,
) {
    pickAndUploadFile(
        scope = scope,
        onUploadingChanged = onUploadingChanged,
        onUploadStarted = { fileName ->
            onFileChanged(uploadFileState(fileName, uploadProgress = 0))
        },
        onUploadProgress = { fileName, progress ->
            onFileChanged(uploadFileState(fileName, uploadProgress = progress))
        },
        onUploaded = {
            onUploaded()
        },
        onError = { fileName ->
            onError()
            if (fileName.isNotBlank()) {
                onFileChanged(uploadFileState(fileName, uploadFailed = true))
            }
        },
    )
}

private fun uploadFileState(
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
private fun ProcessingLogsCard(
    logs: String,
    isExpanded: Boolean,
    errorMessage: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isExpanded) Modifier.fillMaxSize() else Modifier)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Логи обработки файлов (актуальные сверху)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedButton(onClick = onToggle) {
                    Text(if (isExpanded) "Скрыть" else "Показать")
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    errorMessage != null -> Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )

                    logs.isBlank() -> Text(
                        text = "Логов пока нет",
                        color = Color(0xFF5F6368),
                    )

                    else -> ProcessingLogList(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        logs = logs,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingLogList(
    logs: String,
    modifier: Modifier = Modifier,
) {
    val logText = remember(logs) {
        logs.lines()
            .filter { it.isNotBlank() }
            .asReversed()
            .joinToString(separator = "\n")
    }
    val scrollState = rememberScrollState()

    SelectionContainer(
        modifier = modifier
            .background(
                color = Color(0xFF101418),
                shape = RoundedCornerShape(12.dp),
            )
            .verticalScroll(scrollState)
            .padding(12.dp),
    ) {
        Text(
            text = logText,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFE8EAED),
            fontFamily = FontFamily.Monospace,
        )
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
internal fun LoadingState() {
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
internal fun ErrorState(message: String, onRetry: () -> Unit) {
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

internal expect suspend fun fetchProcessingLogs(): String

internal expect suspend fun fetchBackendStatus(): BackendStatus

internal expect suspend fun startBackend(): BackendStatus

internal expect suspend fun stopBackend(): BackendStatus

@Composable
internal expect fun CompletedFileActions(file: PricesFile)

internal expect fun downloadCsv(fileName: String)

internal expect fun pickAndUploadFile(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onUploadStarted: (String) -> Unit,
    onUploadProgress: (String, Int) -> Unit,
    onUploaded: (String) -> Unit,
    onError: (String) -> Unit,
)
