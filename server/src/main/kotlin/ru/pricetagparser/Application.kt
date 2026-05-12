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
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File

private val filesDirectory = File("files")

fun main() {
    val serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    filesDirectory.mkdirs()

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
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
            var uploadedFileName = ""
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName?.safeFileName().orEmpty()
                    if (fileName.isNotBlank()) {
                        part.provider().toInputStream().use { input ->
                            filesDirectory.resolve(fileName).outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        uploadedFileName = fileName
                    }
                }
                part.dispose()
            }

            if (uploadedFileName.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.respondText(
                    text = "{\"name\":\"${uploadedFileName.escapeJson()}\"}",
                    contentType = ContentType.Application.Json,
                )
            }
        }
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
