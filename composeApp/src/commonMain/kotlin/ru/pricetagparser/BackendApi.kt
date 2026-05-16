package ru.pricetagparser

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val apiHttpClient = HttpClient {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            },
        )
    }
}

@Serializable
private data class PriceFileResponse(
    val name: String,
    val csvName: String,
    val hasCsv: Boolean,
)

@Serializable
private data class BackendStatusResponse(
    val powerStatus: String,
)

internal suspend fun fetchBackendFiles(baseUrl: String): List<PricesFile> = apiHttpClient
    .get("$baseUrl/api/files")
    .body<List<PriceFileResponse>>()
    .map { file ->
        PricesFile(
            name = file.name,
            csvName = file.csvName,
            hasCsv = file.hasCsv,
        )
    }

internal suspend fun fetchBackendPowerStatus(baseUrl: String): BackendStatus = apiHttpClient
    .get("$baseUrl/api/backend/status")
    .body<BackendStatusResponse>()
    .toBackendStatus()

internal suspend fun startBackendProcessing(baseUrl: String): BackendStatus = apiHttpClient
    .post("$baseUrl/api/backend/start")
    .body<BackendStatusResponse>()
    .toBackendStatus()

internal suspend fun stopBackendProcessing(baseUrl: String): BackendStatus = apiHttpClient
    .post("$baseUrl/api/backend/stop")
    .body<BackendStatusResponse>()
    .toBackendStatus()

private fun BackendStatusResponse.toBackendStatus(): BackendStatus = BackendStatus(
    powerStatus = when (powerStatus) {
        "Running" -> BackendPowerStatus.Running
        "Stopped" -> BackendPowerStatus.Stopped
        "Starting" -> BackendPowerStatus.Starting
        "Stopping" -> BackendPowerStatus.Stopping
        else -> BackendPowerStatus.Unknown
    },
)
