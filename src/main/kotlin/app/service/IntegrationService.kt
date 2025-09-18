package app.service

import app.client.DomainSystemClient
import app.client.LetterService
import app.model.*
import app.repo.IdempotencyStore
import app.util.Retry

/**
 * Orkestrerer opprettelse og utsendelse av forsikringsavtale mot fagsystem (DomainSystemClient).
 *
 * Hvorfor denne strukturen:
 * - Idempotens: samme Idempotency-Key + identisk payload returnerer samme respons (hindrer duplikater).
 * - Robusthet: kritisk status-endring ("SENT") er flakey → retry m/ backoff; på varig feil forsøkes kompensasjon.
 * - Sporbarhet: enkel AuditLog med correlationId for hvert steg.
 * - Side-effekter: brevtjeneste er best-effort og stopper ikke transaksjonen.
 *
 * Flyt (createAgreement):
 * 1) Idempotens-sjekk:
 *    - Avvis (409) hvis nøkkel tidligere brukt med *annen* payload (Why: beskytter semantikk).
 *    - Returner cached Result(fromCache=true) hvis samme nøkkel allerede er prosessert.
 * 2) Ny avtale:
 *    - createDraft → enrichUnderwriting → priceOffer → activateAgreement.
 * 3) Kritisk status "SENT":
 *    - Retry.run(...) rundt domain.updateStatusToSent (Why: håndtere transiente feil uten å eksponere dem oppover).
 *    - Ved varig feil: logg, forsøk cancelActivation (kompensasjon), marker mulig mismatch og rethrow.
 * 4) Brev:
 *    - letters.sendWelcomeLetter(...) kjøres best-effort (Why: ikke-funksjonell tillegsoperasjon skal ikke velte kjerneflyt).
 * 5) Respons + caching:
 *    - Bygg PolicyResponse (inkl. correlationId), legg i IdempotencyStore, returner Result(fromCache=false).
 *
 * Designvalg:
 * - Result(response, fromCache) lar API-laget velge HTTP-status (201 vs 200) uten å duplisere logikk.
 * - IdempotencyStore er prosess-livslang (må være singleton) for å gi faktisk idempotens.
 * - AuditLog kun for demo; bytt til ekte strukturert logging i prod.
 */


class IntegrationService(
    val domain: DomainSystemClient,
    private val letters: LetterService,
    private val idempotency: IdempotencyStore
) {
    data class Result(val response: PolicyResponse, val fromCache: Boolean)

    fun createAgreement(req: PolicyRequest, correlationId: String, idempotencyKey: String): Result {
        if (!idempotency.validateSameRequest(idempotencyKey, req))
            throw IllegalStateException("Idempotency-Key already used with different payload")

        idempotency.get(idempotencyKey)?.let { return Result(it.response, true) }

        AuditLog.add(correlationId, null, "START createAgreement")
        val agreementId = domain.createDraft()
        AuditLog.add(correlationId, agreementId, "DRAFT created")

        domain.enrichUnderwriting(agreementId)
        AuditLog.add(correlationId, agreementId, "UW enriched")

        val premium = domain.priceOffer(agreementId)
        AuditLog.add(correlationId, agreementId, "PRICED $premium")

        domain.activateAgreement(agreementId)
        AuditLog.add(correlationId, agreementId, "ACTIVE")

        try {
            Retry.run(maxAttempts = 3, initialDelayMs = 200) {
                domain.updateStatusToSent(agreementId)
            }
            AuditLog.add(correlationId, agreementId, "SENT")
        } catch (e: Exception) {
            AuditLog.add(correlationId, agreementId, "SENT failed: ${e.message}")
            try {
                domain.cancelActivation(agreementId)
                AuditLog.add(correlationId, agreementId, "COMPENSATED -> CANCELLED")
            } catch (_: Exception) {
                AuditLog.add(correlationId, agreementId, "COMPENSATION FAILED -> POSSIBLE MISMATCH")
            }
            throw e
        }

        letters.sendWelcomeLetter(agreementId, req.customer.email)

        val resp = PolicyResponse(
            agreementId = agreementId,
            status = domain.getStatus(agreementId) ?: AgreementStatus.DRAFT,
            pricedPremium = premium,
            correlationId = correlationId
        )

        idempotency.put(idempotencyKey, req, resp)
        AuditLog.add(correlationId, agreementId, "END createAgreement")
        return Result(resp, false)
    }
}
