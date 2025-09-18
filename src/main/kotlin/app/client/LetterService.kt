package app.client

/**
 * Enkel mock av brevtjenesten.
 *
 * Hensikt:
 * - Simulere utsendelse av velkomstbrev etter at en avtale er opprettet.
 * - Brukes som "best effort": brevet forsøkes sendt, men eventuelle feil
 *   skal ikke velte hovedflyten (opprettelse av avtale).
 *
 * Kontrakt:
 * - sendWelcomeLetter(agreementId, email) returnerer alltid true i denne mocken.
 *   (I en ekte implementasjon ville vi håndtert nettverksfeil, timeouts,
 *    retry, og logget/alertert ved permanente feil.)
 *
 * Avgrensninger:
 * - Ingen faktisk e-post/utsendelse; ingen persistens eller kø.
 * - Passer for demo/test. For prod: integrer mot e-post/tjeneste (SMTP,
 *   meldingskø, tredjeparts API) og gjør operasjonen idempotent.
 */

interface LetterService {
    fun sendWelcomeLetter(agreementId: String, email: String): Boolean
}

/** Prod-implementasjon (best effort). */
class DefaultLetterService : LetterService {
    override fun sendWelcomeLetter(agreementId: String, email: String): Boolean {
        // best effort-mock: kan "feile" uten å gi beskjed
        return true
    }
}