package app.client

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import app.model.AgreementStatus

/**
 * Enkel mock av fagsystemet som holder avtaler i minnet.
 *
 * Hensikt:
 * - Simulere livssyklusen til en avtale uten ekte backend/DB.
 * - Brukes i integrasjonstesten for å verifisere orkestrering, idempotens og retry.
 *
 * Data/lagring:
 * - `db`: ConcurrentHashMap<id, Agreement> (trådsikkert for enkel parallell kjøring).
 * - `Agreement`: status + beregnet premie (muterbar for enkel demo).
 *
 * Flyt/metoder:
 * - createDraft(): oppretter kladd (DRAFT) og returnerer ny UUID.
 * - enrichUnderwriting(id): no-op (mock) – plassholder for risikovurdering.
 * - priceOffer(id): genererer tilfeldig premie [5000..20000], setter status=PRICED.
 * - activateAgreement(id): aktiverer avtale (ACTIVE).
 * - updateStatusToSent(id): setter status=SENT, men kan bevisst feile noen ganger
 *   for å simulere ustabilt nedstrømskall (styrt av `failSentStatusTimes`).
 * - cancelActivation(id): kompenserer ved å sette status=CANCELLED.
 * - getStatus/getPremium: lesende hjelpemetoder.
 *
 * Feilsimulering:
 * - `@Volatile var failSentStatusTimes`: antall ganger neste kall til
 *   updateStatusToSent(...) skal kaste en transient feil. Brukes for å teste
 *   retry/backoff i IntegrationService.
 *
 * Avgrensninger:
 * - Ingen persistens; alt forsvinner ved restart.
 * - Ingen validering utover `require(...)`/`error(...)` for å holde koden enkel.
 */


class DomainSystemClient {
    data class Agreement(var id: String, var status: AgreementStatus, var premium: Long = 0)

    private val db = ConcurrentHashMap<String, Agreement>()

    fun createDraft(): String {
        val id = UUID.randomUUID().toString()
        db[id] = Agreement(id, AgreementStatus.DRAFT)
        return id
    }

    fun enrichUnderwriting(id: String) {
        require(db.containsKey(id)) { "Agreement not found" }
        // gjør ingenting (mock/placeholder)
    }

    fun priceOffer(id: String): Long {
        val ag = db[id] ?: error("Agreement not found")
        ag.premium = Random.nextLong(5000, 20000)
        ag.status = AgreementStatus.PRICED
        return ag.premium
    }

    fun activateAgreement(id: String) {
        val ag = db[id] ?: error("Agreement not found")
        ag.status = AgreementStatus.ACTIVE
    }

    /**
     * Hvorfor: simulere ustabilt nedstrømskall i det kritiske "SENT"-steget.
     */
    @Volatile
    var failSentStatusTimes: Int = 0
    fun updateStatusToSent(id: String) {
        val ag = db[id] ?: error("Agreement not found")
        if (failSentStatusTimes > 0) {
            failSentStatusTimes--
            throw RuntimeException("Transient failure when setting SENT")
        }
        ag.status = AgreementStatus.SENT
    }

    fun cancelActivation(id: String) {
        val ag = db[id] ?: error("Agreement not found")
        ag.status = AgreementStatus.CANCELLED
    }

    fun getStatus(id: String): AgreementStatus? = db[id]?.status
    fun getPremium(id: String): Long? = db[id]?.premium
}