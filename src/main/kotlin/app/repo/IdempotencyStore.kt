package app.repo

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import app.model.PolicyRequest
import app.model.PolicyResponse

/**
 * Enkel in-memory lagring for idempotente POST-kall.
 *
 * Hensikt:
 * - Sikrer at samme "Idempotency-Key" + identisk request-body gir samme respons ved gjentatte kall.
 *
 * Data/kontrakt:
 * - store: Map<idempotencyKey, Entry>
 * - Entry: lagrer hash av PolicyRequest, selve PolicyResponse og tidspunkt (createdAt).
 *
 * Metoder:
 * - get(key): hent tidligere resultat (null hvis ukjent nøkkel).
 * - put(key, req, resp): lagre resultat for nøkkelen (bruker req.hashCode() for sammenligning).
 * - validateSameRequest(key, req): true hvis nøkkelen er ny eller payload-hash matcher; ellers false
 *   (brukes for å detektere konflikt ved gjenbruk av nøkkel med annen payload).
 *
 * Viktige valg/implikasjoner:
 * - Sammenligning via req.hashCode(): rask og enkel likhetssjekk av payload.
 *   Kollisjoner er svært usannsynlige i praksis for disse små DTO-ene, men teoretisk mulig.
 * - Trådsikkerhet: ConcurrentHashMap gir trygg bruk ved parallellkall.
 *
 * Begrensninger (bevisst):
 * - Kun i minnet (forsvinner ved restart) og ingen TTL/utløp → mulig minnevekst over tid.
 * - Ingen persistens/deling mellom flere noder.
 *
 * Mulige utvidelser:
 * - TTL + periodisk opprydding.
 * - Persistens (H2/Postgres) eller distribuert key/value (Redis) for deling på tvers av instanser.
 * - Lagre full request for revisjon/feilsøking.
 */


class IdempotencyStore {
    data class Entry(val requestHash: Int, val response: PolicyResponse, val createdAt: Instant)

    private val store = ConcurrentHashMap<String, Entry>()

    fun get(key: String): Entry? = store[key]

    fun put(key: String, req: PolicyRequest, resp: PolicyResponse): Entry {
        val entry = Entry(req.hashCode(), resp, Instant.now())
        store[key] = entry
        return entry
    }

    fun validateSameRequest(key: String, req: PolicyRequest): Boolean {
        val existing = store[key] ?: return true
        return existing.requestHash == req.hashCode()
    }
}