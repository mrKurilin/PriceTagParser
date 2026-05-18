package ru.pricetagparser

import java.io.File

object PriceFileProcessor {

    fun process(file: File): File {
        val sourceFile = file.canonicalFile
        require(sourceFile.isFile) { "Source file does not exist: ${sourceFile.absolutePath}" }
        require(!sourceFile.extension.equals("csv", ignoreCase = true)) { "CSV files are not source files: ${sourceFile.absolutePath}" }

        val outputFile = sourceFile.parentFile.resolve("${sourceFile.nameWithoutExtension}.csv")
        val scriptFile = findScriptFile(sourceFile)
        val process = ProcessBuilder(
            scriptFile.absolutePath,
            sourceFile.absolutePath,
        )
            .directory(scriptFile.parentFile)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Price file processing failed with exit code $exitCode: $output" }
        check(outputFile.isFile) { "Price file processing did not create CSV: ${outputFile.absolutePath}. Output: $output" }

        return outputFile
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

    private fun File.requireExecutableScript(): File {
        require(isFile) { "Processing script not found: $absolutePath" }
        require(canExecute()) { "Processing script is not executable: $absolutePath" }
        return canonicalFile
    }

    private const val SCRIPT_NAME = "generate_csv.sh"
    private const val SCRIPT_PATH_ENV = "PRICE_TAG_PARSER_SCRIPT"
}
