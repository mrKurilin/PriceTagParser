package ru.pricetagparser

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform