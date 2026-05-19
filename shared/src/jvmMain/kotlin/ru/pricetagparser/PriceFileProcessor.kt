package ru.pricetagparser

import java.io.File

object PriceFileProcessor {

    private val logFileLock = Any()
    private var configuredLogFile: File? = null

    fun configureLogFile(file: File) {
        synchronized(logFileLock) {
            configuredLogFile = file
        }
    }

    fun readLogs(): String {
        val logFile = resolveLogFile()
        return if (logFile.isFile) logFile.readText() else ""
    }

    fun process(file: File): File {
        val sourceFile = file.canonicalFile
        require(sourceFile.isFile) { "Source file does not exist: ${sourceFile.absolutePath}" }
        require(!sourceFile.extension.equals("csv", ignoreCase = true)) { "CSV files are not source files: ${sourceFile.absolutePath}" }

        val outputFile = sourceFile.parentFile.resolve("${sourceFile.nameWithoutExtension}.csv")
        val scriptFile = findScriptFile(sourceFile)

        log("Starting price file processing: source=${sourceFile.absolutePath}, expectedCsv=${outputFile.absolutePath}")
        try {
            val process = ProcessBuilder(
                scriptFile.absolutePath,
                sourceFile.absolutePath,
            )
                .directory(scriptFile.parentFile)
                .redirectErrorStream(true)
                .start()

            val output = process.streamOutput()
            val exitCode = process.waitFor()
            check(exitCode == 0) { "Price file processing failed with exit code $exitCode: $output" }
            check(outputFile.isFile) { "Price file processing did not create CSV: ${outputFile.absolutePath}. Output: $output" }

            log("Price file processing completed successfully: csv=${outputFile.absolutePath}")
            return outputFile
        } catch (throwable: Throwable) {
            log("Price file processing completed with error: source=${sourceFile.absolutePath}, error=${throwable.message}")
            throw throwable
        }
    }

    private fun findScriptFile(sourceFile: File): File {
        val configuredScript = System.getenv(SCRIPT_PATH_ENV)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)

        if (configuredScript != null) {
            return configuredScript.requireExecutableScript()
        }

        return generateSequence(sourceFile.parentFile?.canonicalFile) { directory -> directory.parentFile }
            .map { directory -> directory.resolve(SCRIPT_NAME) }
            .firstOrNull { it.isFile }
            ?.requireExecutableScript()
            ?: File(SCRIPT_NAME).absoluteFile.requireExecutableScript()
    }

    private fun Process.streamOutput(): String {
        val recentOutput = ArrayDeque<String>()
        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                log(line)
                if (recentOutput.size == PROCESS_OUTPUT_TAIL_LINES) {
                    recentOutput.removeFirst()
                }
                recentOutput.addLast(line)
            }
        }
        return recentOutput.joinToString(System.lineSeparator())
    }

    @Synchronized
    private fun log(message: String) {
        println("$LOG_PREFIX $message")
        System.out.flush()
        runCatching {
            appendLogLine("$LOG_PREFIX $message")
        }.onFailure { throwable ->
            println("$LOG_PREFIX Failed to write log file: ${throwable.message}")
            System.out.flush()
        }
    }

    private fun appendLogLine(message: String) {
        val logFile = resolveLogFile()
        logFile.parentFile?.mkdirs()
        logFile.appendText(message + System.lineSeparator())
    }

    private fun resolveLogFile(): File = synchronized(logFileLock) {
        configuredLogFile ?: System.getenv(LOG_PATH_ENV)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: File(PRICE_FILE_PROCESSOR_LOG_FILE_NAME)
    }.absoluteFile

    private fun File.requireExecutableScript(): File {
        require(isFile) { "Processing script not found: $absolutePath" }
        require(canExecute()) { "Processing script is not executable: $absolutePath" }
        return canonicalFile
    }

    private const val PROCESS_OUTPUT_TAIL_LINES = 200
    private const val LOG_PREFIX = "[price-file-processor]"
    private const val LOG_PATH_ENV = "PRICE_FILE_PROCESSOR_LOG"
    private const val SCRIPT_NAME = "generate_csv.sh"
    private const val SCRIPT_PATH_ENV = "PRICE_TAG_PARSER_SCRIPT"
}
