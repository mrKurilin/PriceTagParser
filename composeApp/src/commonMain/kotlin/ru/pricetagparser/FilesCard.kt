package ru.pricetagparser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FilesCard(
    modifier: Modifier = Modifier.Companion,
    files: List<PricesFile>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentCardContainerColor),
    ) {
        when {
            isLoading && files.isEmpty() -> LoadingState()
            errorMessage != null && files.isEmpty() -> ErrorState(
                message = errorMessage,
                onRetry = onRetry,
            )

            files.isEmpty() -> EmptyState()
            else -> FileList(
                modifier = Modifier.Companion.fillMaxSize(),
                files = files,
            )
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
