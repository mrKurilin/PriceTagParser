package ru.pricetagparser

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentDisposition.Parameters.FileName
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val MAX_UPLOAD_SIZE_BYTES = 500L * 1024L * 1024L
private const val MAX_MULTIPART_OVERHEAD_BYTES = 2L * 1024L * 1024L

private val filesDirectory = File("files")
private val webDirectory = File(System.getenv("WEB_DIR") ?: "web")

fun main() {
    val serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    filesDirectory.mkdirs()

    routing {
        get("/") {
            val indexFile = webDirectory.resolve("index.html")
            if (indexFile.exists()) {
                call.respond(LocalFileContent(indexFile, ContentType.Text.Html))
            } else {
                call.respondText("Ktor: ${Greeting().greet()}")
            }
        }

        get("/api/files") {
            call.respondText(
                text = filesDirectory.priceFilesJson(),
                contentType = ContentType.Application.Json,
            )
        }

        get("/api/files/{name}/download") {
            val name = call.parameters["name"].orEmpty().safeFileName()
            val csvFile = filesDirectory.resolve(name.substringBeforeLast('.') + ".csv")
            if (!csvFile.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(FileName, csvFile.name).toString(),
            )
            call.respond(LocalFileContent(csvFile, ContentType.Text.CSV))
        }

        post("/api/upload") {
            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_UPLOAD_SIZE_BYTES + MAX_MULTIPART_OVERHEAD_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge)
                return@post
            }

            var uploadedFileName = ""
            var uploadTooLarge = false
            call.receiveMultipart(formFieldLimit = MAX_UPLOAD_SIZE_BYTES).forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName?.safeFileName().orEmpty()
                    if (fileName.isNotBlank()) {
                        val targetFile = filesDirectory.resolve(fileName)
                        try {
                            part.provider().toInputStream().use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyToWithLimit(output, MAX_UPLOAD_SIZE_BYTES)
                                }
                            }
                            uploadedFileName = fileName
                        } catch (_: UploadTooLargeException) {
                            uploadTooLarge = true
                            targetFile.delete()
                        }
                    }
                }
                part.dispose()
            }

            if (uploadTooLarge) {
                call.respond(HttpStatusCode.PayloadTooLarge)
            } else if (uploadedFileName.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
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
            if (requestedFile.isFile && requestedFile.toPath().startsWith(rootDirectory.toPath())) {
                call.respond(LocalFileContent(requestedFile))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

private class UploadTooLargeException : RuntimeException()

private fun InputStream.copyToWithLimit(
    output: OutputStream,
    limit: Long,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val bytes = read(buffer)
        if (bytes < 0) break
        copied += bytes
        if (copied > limit) throw UploadTooLargeException()
        output.write(buffer, 0, bytes)
    }
}

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
