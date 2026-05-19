package ru.pricetagparser

internal data class PricesFile(
    val name: String,
    val csvName: String,
    val hasCsv: Boolean,
    val uploadProgress: Int? = null,
    val uploadFailed: Boolean = false,
)
