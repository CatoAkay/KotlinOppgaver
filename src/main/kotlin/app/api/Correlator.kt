package app.api

import java.util.UUID

/**
 * Enkel generator for korrelasjons-ID.
 *
 * Hensikt:
 * - Sørger for at hvert innkommende kall får en unik "X-Correlation-ID" når klienten ikke sender en.
 * - Brukes av filter/resource for å spore et kall gjennom hele flyten og i logger.
 *
 * Valg:
 * - UUID v4 som streng (tilfeldig, kollisjonsrisiko praktisk talt neglisjerbar).
 *
 * Mulige utvidelser:
 * - Bytt til ULID/KSUID for tids-sorterbare ID-er.
 * - Legg ID i MDC (logging) for automatisk korrelasjon i alle logglinjer.
 * - Valider/normaliser ID hvis den kommer utenfra (lengde/format).
 */


object Correlator {
    fun current(): String = UUID.randomUUID().toString()
}