package app.service

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory


/**
 * Minimal, trådsikker audit-logg for demo/utvikling.
 *
 * Hvorfor:
 * - Enkelt å spore hendelser per request via correlationId uten ekstern logginfrastruktur.
 *
 * Design:
 * - Entry er uforanderlig (data class) og tidsstemples med Instant.now() ved innsetting.
 * - CopyOnWriteArrayList gir trådsikker skriv/les uten eksplisitt låsing
 *   (Why: lav skrivefrekvens, høy lesefrekvens; enkelhet > rå ytelse her).
 *
 * API:
 * - add(correlationId, agreementId, event): registrerer en hendelse.
 * - all(): returnerer en snapshot-liste (kopi) for trygg iterasjon/eksport.
 *
 * Begrensninger (bevisst):
 * - Prosess-lokal, ikke persistent; tømmes ved restart.
 * - Ingen indekser/søk/rotasjon – bruk kun til enkel tracing i dev/test.
 * - For produksjon: bruk strukturert logging (MDC for correlationId) eller observability-stack.
 */


object AuditLog {
    data class Entry(
        val correlationId: String,
        val agreementId: String?,
        val event: String,
        val at: Instant = Instant.now()
    )

    private val log = LoggerFactory.getLogger(AuditLog::class.java)
    private val entries = CopyOnWriteArrayList<Entry>()

    fun add(correlationId: String, agreementId: String?, event: String) {
        val e = Entry(correlationId, agreementId, event)
        entries += e
        log.info("audit cid={} agreementId={} event='{}' at={}", e.correlationId, e.agreementId, e.event, e.at) // why: synlig i runtime-logg
    }

    fun all(): List<Entry> = entries.toList()
}
