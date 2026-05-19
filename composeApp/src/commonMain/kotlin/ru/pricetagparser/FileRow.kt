package ru.pricetagparser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

@Composable
internal fun FileRow(index: Int, file: PricesFile) {
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Companion.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilePreviewIcon(fileName = file.name)

        Spacer(modifier = Modifier.Companion.width(12.dp))

        Text(
            modifier = Modifier.Companion.weight(1f),
            text = "$index. ${file.name}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Companion.Medium,
        )

        Spacer(modifier = Modifier.Companion.width(16.dp))

        when {
            file.uploadFailed -> Text(
                text = "Ошибка загрузки",
                color = MaterialTheme.colorScheme.error,
            )

            file.uploadProgress != null -> Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.Companion.size(22.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.Companion.width(8.dp))
                Text("Загрузка ${file.uploadProgress}%")
            }

            file.hasCsv -> CompletedFileActions(file)

            else -> {
                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.Companion.size(22.dp),
                        strokeWidth = 2.dp,
                    )

                    Spacer(modifier = Modifier.Companion.width(8.dp))

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
