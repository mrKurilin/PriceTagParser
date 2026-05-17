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
import java.io.File
import java.io.OutputStream

private const val MAX_UPLOAD_SIZE_BYTES = 500L * 1024L * 1024L
private const val MAX_MULTIPART_OVERHEAD_BYTES = 2L * 1024L * 1024L
private val filesDirectory = File("files")
private val webDirectory = File(System.getenv("WEB_DIR") ?: "web")
private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun main() {
    val serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    filesDirectory.mkdirs()
    println("[Server] started: filesDirectory=${filesDirectory.absolutePath}, webDirectory=${webDirectory.absolutePath}")

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

        get("/api/files") {
            val filesJson = filesDirectory.priceFilesJson()
            println("[Server] GET /api/files: respond ${filesDirectory.uploadedFilesCount()} files, jsonLength=${filesJson.length}")
            call.respondText(
                text = filesJson,
                contentType = ContentType.Application.Json,
            )
        }

        get("/api/files/{name}/download") {
            val rawName = call.parameters["name"].orEmpty()
            val name = rawName.safeFileName()
            val csvFile = filesDirectory.resolve(name.substringBeforeLast('.') + ".csv")
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
                            try {
                                targetFile.outputStream().use { output ->
                                    part.provider().copyToWithLimit(output, MAX_UPLOAD_SIZE_BYTES)
                                }
                                uploadedFileName = fileName
                                processingScope.launch {
                                    PriceFileProcessor.process(targetFile)
                                }
                                println("[Server] POST /api/upload: saved file=${targetFile.absolutePath}, size=${targetFile.length()}")
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

private fun File.uploadedFilesCount(): Int =
    listFiles()
        .orEmpty()
        .count { it.isFile && !it.extension.equals("csv", ignoreCase = true) }

private fun File.priceFilesJson(): String {
    val csvNames = listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
        .map { it.nameWithoutExtension }
        .toSet()

    return listFiles()
        .orEmpty()
        .filter { it.isFile && !it.extension.equals("csv", ignoreCase = true) }
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
