package app.util

import kotlin.math.pow
import kotlin.random.Random

/**
 * Generisk retry-hjelper med eksponentiell backoff og valgfri jitter.
 * Som betyr:
 * Et lite verktøy som kjører en operasjon på nytt flere ganger. Ventetiden øker for hvert forsøk
 * (eksponentiell backoff), og du kan legge til litt tilfeldig variasjon (jitter) i ventetiden for å
 * unngå at mange klienter prøver samtidig.
 *
 * Hensikt:
 * - Prøver en kritisk operasjon flere ganger for å tåle midlertidige feil (nettverk, flakye tjenester),
 *   uten å oversvømme nedstrøms systemer.
 *
 * Parametre:
 * - maxAttempts: maks antall forsøk (inkl. første).
 * - initialDelayMs: startforsinkelse før neste forsøk.
 * - factor: multipliserer forsinkelsen per forsøk (eksponentiell backoff).
 * - jitter: hvis true, tilfører tilfeldig variasjon i søvntiden for å unngå thundering herd.
 * - block: lambdaen som kjøres på hver retry.
 *
 * Thundering herd er når mange klienter (eller tråder/prosesser) våkner og prøver samtidig etter en feil/timeout,
 * og overlaster en tjeneste i en ny bølge — så systemet feiler igjen.
 *
 * Virkemåte:
 * 1) Kjør block().
 * 2) Ved feil: lagre feilen, sov (delay eller random mellom delay..delay*2 ved jitter),
 *    øk attempt, øk delay med factor^n, og prøv på nytt.
 * 3) Når maxAttempts er brukt opp: kast siste feil.
 *
 * Viktige valg:
 * - Fanger Throwable for å også håndtere uventede runtime-feil; i prod kan du begrense til Exception.
 * - Bruker Thread.sleep(...) → blokkende.
 *
 * Begrensninger:
 * - Blokkerer tråden; bruk en ikke-blokkerende
 * - Ingen selektiv retry (alle feil behandles likt).
 *
 * Mulige utvidelser:
 * - Filtrere hvilke unntak som skal retries (f.eks. tidsavbrudd/5xx).
 * - Maks total varighet (timeout).
 */


object Retry {
    /**
     * Hvorfor: redusere risiko for midlertidige avvik ved å retry’e kritiske nedstrømsoperasjoner.
     */
    fun <T> run(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 200,
        factor: Double = 2.0,
        jitter: Boolean = true,
        block: () -> T
    ): T {
        var attempt = 1
        var delay = initialDelayMs
        var lastError: Throwable? = null
        while (attempt <= maxAttempts) {
            try {
                return block()
            } catch (t: Throwable) {
                lastError = t
                if (attempt == maxAttempts) break
                val sleep = if (jitter) (delay..(delay * 2)).random() else delay
                Thread.sleep(sleep)
                attempt++
                delay = (initialDelayMs * factor.pow(attempt - 1)).toLong()
            }
        }
        throw lastError ?: IllegalStateException("Retry failed without exception")
    }

    private fun ClosedRange<Long>.random() =
        Random.nextLong(start, endInclusive + 1)
}
