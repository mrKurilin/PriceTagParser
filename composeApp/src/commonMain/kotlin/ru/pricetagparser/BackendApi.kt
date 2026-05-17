package ru.pricetagparser

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
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
