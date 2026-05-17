package ru.pricetagparser

enum class BackendPowerStatus {
    Running,
    Stopped,
    Starting,
    Stopping,
    Unknown,
}

data class BackendStatus(
    val powerStatus: BackendPowerStatus,
)

fun yandexStatusToBackendPowerStatus(yandexStatus: String): BackendPowerStatus = when (yandexStatus) {
    "RUNNING" -> BackendPowerStatus.Running
    "STOPPED" -> BackendPowerStatus.Stopped
    "STARTING", "PROVISIONING" -> BackendPowerStatus.Starting
    "STOPPING" -> BackendPowerStatus.Stopping
    else -> BackendPowerStatus.Unknown
}
