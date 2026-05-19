package ru.pricetagparser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FileList(
    files: List<PricesFile>,
    modifier: Modifier = Modifier.Companion,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
