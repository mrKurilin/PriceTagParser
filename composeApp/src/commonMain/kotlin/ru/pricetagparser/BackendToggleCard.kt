package ru.pricetagparser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BackendToggleCard(
    status: BackendStatus,
    isActionRunning: Boolean,
    errorMessage: String?,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val isRunning = status.powerStatus == BackendPowerStatus.Running
    val isChanging = isActionRunning ||
            status.powerStatus == BackendPowerStatus.Starting ||
            status.powerStatus == BackendPowerStatus.Stopping
    val statusText = when (status.powerStatus) {
        BackendPowerStatus.Running -> "Бэк включен"
        BackendPowerStatus.Stopped -> "Бэк выключен"
        BackendPowerStatus.Starting -> "Бэк запускается"
        BackendPowerStatus.Stopping -> "Бэк останавливается"
        BackendPowerStatus.Unknown -> "Статус бэка неизвестен"
    }

    Card(
        modifier = Modifier.Companion.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentCardContainerColor),
    ) {
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Companion.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.Companion.weight(1f)) {
                Text(
                    text = "Бэк обработки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Companion.Bold,
                )

                Spacer(modifier = Modifier.Companion.height(4.dp))

                Text(
                    text = errorMessage ?: statusText,
                    color = if (errorMessage == null) Color(0xFF5F6368) else MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.Companion.width(16.dp))

            if (isChanging) {
                CircularProgressIndicator(
                    modifier = Modifier.Companion.size(28.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Switch(
                    checked = isRunning,
                    onCheckedChange = onEnabledChanged,
                    enabled = status.powerStatus != BackendPowerStatus.Unknown,
                )
            }
        }
    }
}