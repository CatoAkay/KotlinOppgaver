package app.model

import java.time.Instant

/**
 * Domenemodeller brukt av API-et.
 *
 * Strukturer:
 * - Customer: enkel kundeinfo (fødselsnr., navn, e-post).
 * - Coverage: én dekning i avtalen (type, forsikringssum, egenandel).
 * - PolicyRequest: innkommende bestilling av avtale (produktkode, kunde, dekninger, startdato).
 *   - startDate som ISO-8601 streng (f.eks. "2025-10-01") for enkel JSON-kompatibilitet.
 * - AgreementStatus: livssyklus for avtalen (DRAFT → PRICED → ACTIVE → SENT/CANCELLED).
 * - PolicyResponse: svar ved opprettelse (id, status, beregnet premie, korrelasjons-ID, ev. advarsler).
 *   - createdAt settes til nå (Instant) og serialiseres som ISO-8601 via Jackson-konfig.
 *
 * Valg:
 * - Kotlin data classes for uforanderlige, lette DTO-er med auto-genererte equals/hash/toString.
 * - Long for beløp (øre/fradrag) for å unngå flyttallsavrunding.
 * - warnings default tom liste for å slippe null-håndtering i klient.
 *
 * Bruk:
 * - PolicyRequest mottas i POST /api/agreements.
 * - PolicyResponse sendes tilbake med 201/200.
 */


data class Customer(
    val nationalId: String,
    val firstName: String,
    val lastName: String,
    val email: String
)

data class Coverage(
    val type: String,
    val sumInsured: Long,
    val deductible: Long
)

data class PolicyRequest(
    val productCode: String,
    val customer: Customer,
    val coverages: List<Coverage>,
    val startDate: String
)

enum class AgreementStatus { DRAFT, PRICED, ACTIVE, SENT, CANCELLED }

data class PolicyResponse(
    val agreementId: String,
    val status: AgreementStatus,
    val pricedPremium: Long,
    val correlationId: String,
    val warnings: List<String> = emptyList(),
    val createdAt: Instant = Instant.now()
)