package ru.pricetagparser

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentDisposition.Parameters.FileName
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val MAX_UPLOAD_SIZE_BYTES = 500L * 1024L * 1024L
private const val MAX_MULTIPART_OVERHEAD_BYTES = 2L * 1024L * 1024L
private const val APP_ROLE_SITE = "site"
private const val APP_ROLE_API = "api"
private const val FILES_DIR_ENV = "FILES_DIR"
private const val DEFAULT_FILES_DIR = "files"
private const val PROJECT_SERVER_FILES_DIR = "server/files"
private val appRole = System.getenv("APP_ROLE") ?: APP_ROLE_API
private val filesDirectory = resolveFilesDirectory()
private val processingLogsFile = filesDirectory.resolve(PRICE_FILE_PROCESSOR_LOG_FILE_NAME)
private val webDirectory = File(System.getenv("WEB_DIR") ?: "web")
private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val backendManager = YandexBackendManager()

fun main() {
    val serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    filesDirectory.mkdirs()
    PriceFileProcessor.configureLogFile(processingLogsFile)
    println("[Server] started: role=$appRole, filesDirectory=${filesDirectory.absolutePath}, webDirectory=${webDirectory.absolutePath}")

    install(Compression) {
        gzip {
            minimumSize(1024)
        }
        deflate {
            minimumSize(1024)
        }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    routing {
        get("/") {
            println("[Server] GET /: indexFile=${webDirectory.resolve("index.html").absolutePath}")
            val indexFile = webDirectory.resolve("index.html")
            if (indexFile.exists()) {
                println("[Server] GET /: respond index.html")
                call.respond(LocalFileContent(indexFile, ContentType.Text.Html))
            } else {
                println("[Server] GET /: index.html not found, respond greeting")
                call.respondText("Ktor: ${Greeting().greet()}")
            }
        }

        get(BACKEND_STATUS_API_PATH) {
            try {
                val statusJson = backendManager.statusJson()
                println("[Server] GET $BACKEND_STATUS_API_PATH: respond $statusJson")
                call.respondText(
                    text = statusJson,
                    contentType = ContentType.Application.Json,
                )
            } catch (throwable: Throwable) {
                println("[Server] GET $BACKEND_STATUS_API_PATH: failed ${throwable.message}")
                call.respond(HttpStatusCode.BadGateway)
            }
        }

        post(BACKEND_START_API_PATH) {
            if (!appRole.isSiteRole()) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            try {
                val statusJson = backendManager.startJson()
                println("[Server] POST $BACKEND_START_API_PATH: respond $statusJson")
                call.respondText(
                    text = statusJson,
                    contentType = ContentType.Application.Json,
                )
            } catch (throwable: Throwable) {
                println("[Server] POST $BACKEND_START_API_PATH: failed ${throwable.message}")
                call.respond(HttpStatusCode.BadGateway)
            }
        }

        post(BACKEND_STOP_API_PATH) {
            if (!appRole.isSiteRole()) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            try {
                val statusJson = backendManager.stopJson()
                println("[Server] POST $BACKEND_STOP_API_PATH: respond $statusJson")
                call.respondText(
                    text = statusJson,
                    contentType = ContentType.Application.Json,
                )
            } catch (throwable: Throwable) {
                println("[Server] POST $BACKEND_STOP_API_PATH: failed ${throwable.message}")
                call.respond(HttpStatusCode.BadGateway)
            }
        }

        get(FILES_API_PATH) {
            val filesJson = filesDirectory.priceFilesJson()
            println("[Server] GET $FILES_API_PATH: respond ${filesDirectory.uploadedFilesCount()} files, jsonLength=${filesJson.length}")
            call.respondText(
                text = filesJson,
                contentType = ContentType.Application.Json,
            )
        }

        get(GET_LOGS_API_PATH) {
            val logs = withContext(Dispatchers.IO) {
                PriceFileProcessor.readLogs()
            }
            println("[Server] GET $GET_LOGS_API_PATH: respond logLength=${logs.length}")
            call.respondText(
                text = logs,
                contentType = ContentType.Text.Plain,
            )
        }

        get("/api/files/{name}/download") {
            val rawName = call.parameters["name"].orEmpty()
            val name = rawName.safeFileName()
            val csvFile = filesDirectory.resolve(name).expectedCsvFile()
            println("[Server] GET /api/files/{name}/download: rawName=$rawName, safeName=$name, csv=${csvFile.absolutePath}")
            if (!csvFile.exists()) {
                println("[Server] GET /api/files/{name}/download: respond 404, csv not found")
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(FileName, csvFile.name).toString(),
            )
            println("[Server] GET /api/files/{name}/download: respond 200, size=${csvFile.length()}")
            call.respond(LocalFileContent(csvFile, ContentType.Text.CSV))
        }

        post("/api/upload") {
            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            println("[Server] POST /api/upload: received, contentLength=${contentLength ?: "unknown"}")
            if (contentLength != null && contentLength > MAX_UPLOAD_SIZE_BYTES + MAX_MULTIPART_OVERHEAD_BYTES) {
                println("[Server] POST /api/upload: respond 413, contentLength=$contentLength exceeds limit")
                call.respond(HttpStatusCode.PayloadTooLarge)
                return@post
            }

            var uploadedFileName = ""
            var uploadTooLarge = false
            call.receiveMultipart(formFieldLimit = MAX_UPLOAD_SIZE_BYTES).forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val originalFileName = part.originalFileName.orEmpty()
                        val fileName = originalFileName.safeFileName()
                        println("[Server] POST /api/upload: file part originalName=$originalFileName, safeName=$fileName")
                        if (fileName.isNotBlank()) {
                            val targetFile = filesDirectory.resolve(fileName)
                            val expectedCsvFile = targetFile.expectedCsvFile()
                            try {
                                targetFile.outputStream().use { output ->
                                    part.provider().copyToWithLimit(output, MAX_UPLOAD_SIZE_BYTES)
                                }
                                if (expectedCsvFile.exists()) {
                                    check(expectedCsvFile.delete()) { "Could not delete stale CSV: ${expectedCsvFile.absolutePath}" }
                                    println("[Server] POST /api/upload: deleted stale csv=${expectedCsvFile.absolutePath}")
                                }
                                uploadedFileName = fileName
                                processingScope.launch {
                                    processUploadedFile(targetFile, expectedCsvFile)
                                }
                                println("[Server] POST /api/upload: saved file=${targetFile.absolutePath}, expectedCsv=${expectedCsvFile.absolutePath}, size=${targetFile.length()}")
                            } catch (_: UploadTooLargeException) {
                                uploadTooLarge = true
                                targetFile.delete()
                                println("[Server] POST /api/upload: file too large, deleted partial file=${targetFile.absolutePath}")
                            } catch (throwable: Throwable) {
                                targetFile.delete()
                                println("[Server] POST /api/upload: save failed for ${targetFile.absolutePath}: ${throwable.message}")
                                throw throwable
                            }
                        } else {
                            println("[Server] POST /api/upload: skip file part because filename is blank")
                        }
                    }

                    else -> println("[Server] POST /api/upload: non-file multipart part type=${part::class.simpleName}")
                }
                part.dispose()
            }

            if (uploadTooLarge) {
                println("[Server] POST /api/upload: respond 413")
                call.respond(HttpStatusCode.PayloadTooLarge)
            } else if (uploadedFileName.isBlank()) {
                println("[Server] POST /api/upload: respond 400, no uploaded file")
                call.respond(HttpStatusCode.BadRequest)
            } else {
                println("[Server] POST /api/upload: respond 200, uploadedFileName=$uploadedFileName")
                call.respondText(
                    text = "{\"name\":\"${uploadedFileName.escapeJson()}\"}",
                    contentType = ContentType.Application.Json,
                )
            }
        }

        get("/{path...}") {
            val path = call.parameters.getAll("path").orEmpty().joinToString("/")
            val requestedFile = webDirectory.resolve(path).canonicalFile
            val rootDirectory = webDirectory.canonicalFile
            println("[Server] GET /$path: static lookup file=${requestedFile.absolutePath}")
            if (requestedFile.isFile && requestedFile.toPath().startsWith(rootDirectory.toPath())) {
                println("[Server] GET /$path: respond static 200, size=${requestedFile.length()}")
                call.respond(LocalFileContent(requestedFile))
            } else {
                println("[Server] GET /$path: respond static 404")
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

private class YandexBackendManager(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val environmentProvider: () -> YandexEnvironment = ::loadYandexEnvironment,
) {
    fun statusJson(): String {
        val environment = environmentProvider()
        val request = authorizedRequest(
            uri = "$BACKEND_STATUS_API_BASE_URL$BACKEND_INSTANCE_API_PATH/${environment.instanceId}",
            environment = environment,
        ).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        ensureSuccess(response)
        return response.body().backendStatusJson()
    }

    fun startJson(): String {
        performAction(BACKEND_START_ACTION)
        return BackendPowerStatus.Starting.toStatusJson()
    }

    fun stopJson(): String {
        performAction(BACKEND_STOP_ACTION)
        return BackendPowerStatus.Stopping.toStatusJson()
    }

    private fun performAction(action: String) {
        val environment = environmentProvider()
        val request = authorizedRequest(
            uri = "$BACKEND_STATUS_API_BASE_URL$BACKEND_INSTANCE_API_PATH/${environment.instanceId}$action",
            environment = environment,
        ).POST(HttpRequest.BodyPublishers.noBody()).build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        ensureSuccess(response)
    }

    private fun authorizedRequest(
        uri: String,
        environment: YandexEnvironment,
    ): HttpRequest.Builder = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .header(HttpHeaders.Authorization, "Bearer ${environment.iamToken}")
        .header(HttpHeaders.Accept, ContentType.Application.Json.toString())

    private fun ensureSuccess(response: HttpResponse<String>) {
        check(response.statusCode() in 200..299) {
            "Yandex Compute API failed with status ${response.statusCode()}"
        }
    }
}

private class UploadTooLargeException : RuntimeException()

private suspend fun ByteReadChannel.copyToWithLimit(
    output: OutputStream,
    limit: Long,
) {
    withContext(Dispatchers.IO) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val bytes = readAvailable(buffer)
            if (bytes < 0) break
            copied += bytes
            if (copied > limit) throw UploadTooLargeException()
            output.write(buffer, 0, bytes)
        }
    }
}

private fun String.isSiteRole(): Boolean =
    equals(APP_ROLE_SITE, ignoreCase = true)

private fun String.backendStatusJson(): String {
    val yandexStatus = Json.parseToJsonElement(this)
        .jsonObject["status"]
        ?.jsonPrimitive
        ?.contentOrNull
        .orEmpty()
    return yandexStatusToBackendPowerStatus(yandexStatus).toStatusJson()
}

private fun BackendPowerStatus.toStatusJson(): String =
    "{\"powerStatus\":\"$name\"}"

private fun resolveFilesDirectory(): File {
    val configuredDirectory = System.getenv(FILES_DIR_ENV)
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)

    if (configuredDirectory != null) {
        return configuredDirectory
    }

    val projectServerFilesDirectory = File(PROJECT_SERVER_FILES_DIR)
    return if (projectServerFilesDirectory.parentFile?.isDirectory == true) {
        projectServerFilesDirectory
    } else {
        File(DEFAULT_FILES_DIR)
    }
}

private suspend fun processUploadedFile(sourceFile: File, expectedCsvFile: File) {
    val canonicalSourceFile = sourceFile.canonicalFile
    val canonicalExpectedCsvFile = expectedCsvFile.canonicalFile
    println("[Server] POST /api/upload: processing started file=${canonicalSourceFile.absolutePath}, expectedCsv=${canonicalExpectedCsvFile.absolutePath}")

    try {
        val actualCsvFile = PriceFileProcessor.process(canonicalSourceFile).canonicalFile
        if (actualCsvFile != canonicalExpectedCsvFile) {
            actualCsvFile.copyTo(canonicalExpectedCsvFile, overwrite = true)
            println("[Server] POST /api/upload: copied csv from ${actualCsvFile.absolutePath} to expected path ${canonicalExpectedCsvFile.absolutePath}")
        }
        check(canonicalExpectedCsvFile.isFile) { "Expected CSV was not created: ${canonicalExpectedCsvFile.absolutePath}" }
        println("[Server] POST /api/upload: processing finished csv=${canonicalExpectedCsvFile.absolutePath}, size=${canonicalExpectedCsvFile.length()}")
    } catch (throwable: Throwable) {
        println("[Server] POST /api/upload: processing failed file=${canonicalSourceFile.absolutePath}, expectedCsv=${canonicalExpectedCsvFile.absolutePath}: ${throwable.stackTraceToString()}")
    }
}

private fun File.expectedCsvFile(): File =
    (parentFile ?: File(DEFAULT_FILES_DIR)).resolve("$nameWithoutExtension.csv")

private fun File.uploadedFilesCount(): Int =
    listFiles()
        .orEmpty()
        .count { it.isUploadedSourceFile() }

private fun File.priceFilesJson(): String {
    val csvNames = listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
        .map { it.nameWithoutExtension }
        .toSet()

    return listFiles()
        .orEmpty()
        .filter { it.isUploadedSourceFile() }
        .sortedBy { it.name.lowercase() }
        .joinToString(prefix = "[", postfix = "]") { file ->
            val csvName = "${file.nameWithoutExtension}.csv"
            "{" +
                    "\"name\":\"${file.name.escapeJson()}\"," +
                    "\"csvName\":\"${csvName.escapeJson()}\"," +
                    "\"hasCsv\":${file.nameWithoutExtension in csvNames}" +
                    "}"
        }
}

private fun File.isUploadedSourceFile(): Boolean =
    isFile &&
            name != PRICE_FILE_PROCESSOR_LOG_FILE_NAME &&
            !extension.equals("csv", ignoreCase = true)

private fun String.safeFileName(): String =
    File(this).name

private fun String.escapeJson(): String =
    buildString {
        this@escapeJson.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
