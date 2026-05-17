package ru.pricetagparser

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal expect fun loadBackendInstanceId(): String

private val apiHttpClient = HttpClient {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            },
        )
    }

    install(Logging) {
        level = LogLevel.BODY
    }
}

@Serializable
private data class PriceFileResponse(
    val name: String,
    val csvName: String,
    val hasCsv: Boolean,
)

@Serializable
private data class YandexInstanceResponse(
    val status: String,
)

internal suspend fun fetchBackendFiles(): List<PricesFile> = apiHttpClient
    .get("$FILE_PROCESSING_API_BASE_URL$FILES_API_PATH")
    .body<List<PriceFileResponse>>()
    .map { file ->
        PricesFile(
            name = file.name,
            csvName = file.csvName,
            hasCsv = file.hasCsv,
        )
    }

internal suspend fun fetchBackendStatusViaApi(): BackendStatus = apiHttpClient
    .get("$BACKEND_STATUS_API_BASE_URL$BACKEND_INSTANCE_API_PATH/${loadBackendInstanceId()}")
    .body<YandexInstanceResponse>()
    .toBackendStatus()

internal suspend fun startBackendViaApi(): BackendStatus = apiHttpClient
    .post("$BACKEND_STATUS_API_BASE_URL$BACKEND_INSTANCE_API_PATH/${loadBackendInstanceId()}$BACKEND_START_ACTION")
    .body<YandexInstanceResponse>()
    .toBackendStatus()

internal suspend fun stopBackendViaApi(): BackendStatus = apiHttpClient
    .post("$BACKEND_STATUS_API_BASE_URL$BACKEND_INSTANCE_API_PATH/${loadBackendInstanceId()}$BACKEND_STOP_ACTION")
    .body<YandexInstanceResponse>()
    .toBackendStatus()

private fun YandexInstanceResponse.toBackendStatus(): BackendStatus = BackendStatus(
    powerStatus = yandexStatusToBackendPowerStatus(status),
)
