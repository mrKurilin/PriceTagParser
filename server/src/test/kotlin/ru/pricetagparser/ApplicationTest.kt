package ru.pricetagparser

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun parsesYandexInstanceStatusWithFormattedJson() {
        val response = """
            {
             "instances": [
              {
               "id": "epd0j7jvi4buujhjr5c9",
               "status": "STOPPED"
              }
             ]
            }
        """.trimIndent()

        assertEquals("Stopped", response.instancePowerStatus("epd0j7jvi4buujhjr5c9"))
    }

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ktor: ${Greeting().greet()}", response.bodyAsText())
    }
}