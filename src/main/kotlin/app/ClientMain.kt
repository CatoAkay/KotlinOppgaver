package app

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import app.model.Coverage
import app.model.Customer
import app.model.PolicyRequest

/**
 * Enkel, headless "systemtest"-klient som kjører mot lokal server for å verifisere
 * orkestrering, idempotens og konflikt-håndtering i REST-API-et.
 *
 * Hvorfor denne klienten:
 * - Rask verifikasjon uten GUI/eksterne testverktøy.
 * - Dekker viktige scenarier: transient feil + idempotens + konflikt.
 *
 * Flyt:
 * 1) base = http://localhost:8080  (forventer at serveren kjører lokalt).
 * 2) Simulerer én midlertidig feil i downstream ved å kalle
 *    POST /api/agreements/_simulate/sentFailures/1
 *    (Why: tvinger retry-logikken i serveren til å trigges minst én gang).
 * 3) Bygger PolicyRequest og serialiserer til JSON (Jackson Kotlin).
 * 4) Genererer en Idempotency-Key (UUID).
 * 5) Kall #1: POST /api/agreements med nøkkel + body -> oppretter avtale (happy path).
 * 6) Kall #2: POST med *samme* nøkkel + *samme* payload -> forventer idempotent svar
 *    (samme resultat som #1, uten å opprette ny avtale).
 * 7) Kall #3: POST med *samme* nøkkel, men *endret* payload -> forventer konflikt (409)
 *    (Why: samme idempotency-key må brukes med identisk payload).
 *
 * I/O og nett:
 * - httpPost(...) bruker HttpURLConnection direkte for å minimere avhengigheter.
 * - Setter nødvendige headers (Content-Type, Idempotency-Key).
 * - Leser enten inputStream (2xx) eller errorStream (ikke-2xx) og returnerer (status, body).
 *
 * Output:
 * - Skriver HTTP-koder og responstekst til stdout for enkel manuell inspeksjon.
 *
 *
 * Idempotens = samme operasjon, med samme input, kan gjentas uten å endre resultatet utover første gang.
 */

object ClientMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val base = "http://localhost:8080"
        val mapper = jacksonObjectMapper()

        // 1) simuler én midlertidig feil på "SENT"
        httpPost("$base/api/agreements/_simulate/sentFailures/1", "{}")

        val request = PolicyRequest(
            productCode = "HOME-STD",
            customer = Customer("12059012345", "Ola", "Nordmann", "ola@example.com"),
            coverages = listOf(Coverage("BUILDING", 2_000_000, 10_000)),
            startDate = "2025-10-01"
        )
        val body = mapper.writeValueAsString(request)

        val idemKey = UUID.randomUUID().toString()
        val (status1, resp1) = httpPost(
            "$base/api/agreements", body,
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json", "Idempotency-Key" to idemKey)
        )
        println("Create #1 -> HTTP $status1\n$resp1\n")

        // 2) idempotent gjentakelse med samme payload
        val (status2, resp2) = httpPost(
            "$base/api/agreements", body,
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json", "Idempotency-Key" to idemKey)
        )
        println("Create #2 (idempotent) -> HTTP $status2\n$resp2\n")

        // 3) idempotent konflikttest med ulik payload under samme nøkkel
        val modified = request.copy(productCode = "HOME-PLUS")
        val (status3, resp3) = httpPost(
            "$base/api/agreements", mapper.writeValueAsString(modified),
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json", "Idempotency-Key" to idemKey)
        )
        println("Create #3 (conflict) -> HTTP $status3\n$resp3\n")
    }

    private fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            conn.outputStream.use { os -> os.write(body.toByteArray()) }

            val code = conn.responseCode
            val text = readResponseSafely(conn)
            return code to text
        } finally {
            conn.disconnect() // hvorfor: unngå å holde sokler åpne hvis unntak oppstår
        }
    }

    private fun readResponseSafely(conn: HttpURLConnection): String {
        // Hvorfor: errorStream kan være null for enkelte statuskoder; beskytt mot NPE.
        val inStream = runCatching { conn.inputStream }.getOrNull()
        val errStream = runCatching { conn.errorStream }.getOrNull()
        val stream = if (conn.responseCode in 200..299) inStream else (errStream ?: inStream)
        return if (stream != null) {
            stream.bufferedReader().use { it.readText() }
        } else {
            "" // ingen body
        }
    }
}