package ru.pricetagparser

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class PriceFile(
    val name: String,
    val csvName: String,
    val hasCsv: Boolean,
)

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        var files by remember { mutableStateOf(emptyList<PriceFile>()) }
        var isLoading by remember { mutableStateOf(true) }
        var isUploading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        when {
                            isLoading -> LoadingState()
                            errorMessage != null -> ErrorState(
                                message = errorMessage.orEmpty(),
                                onRetry = {
                                    scope.launchSafely {
                                        loadFiles()
                                    }
                                },
                            )

                            files.isEmpty() -> EmptyState()
                            else -> FileList(files = files)
                        }
                    }
                }

                FilledIconButton(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(64.dp),
                    enabled = !isUploading,
                    onClick = {
                        pickAndUploadFile(
                            scope = scope,
                            onUploadingChanged = { isUploading = it },
                            onUploaded = {
                                scope.launchSafely {
                                    loadFiles()
                                }
                            },
                            onError = {
                                errorMessage = "Не удалось загрузить файл"
                            },
                        )
                    },
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
        }
    }
}

@Composable
private fun FileList(files: List<PriceFile>) {
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

@Composable
private fun FileRow(index: Int, file: PriceFile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "$index. ${file.name}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.width(16.dp))
        if (file.hasCsv) {
            Button(onClick = { downloadCsv(file.name) }) {
                Text("Скачать")
            }
        } else {
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

internal expect suspend fun fetchFiles(): List<PriceFile>

internal expect fun downloadCsv(fileName: String)

internal expect fun pickAndUploadFile(
    scope: CoroutineScope,
    onUploadingChanged: (Boolean) -> Unit,
    onUploaded: () -> Unit,
    onError: () -> Unit,
)
