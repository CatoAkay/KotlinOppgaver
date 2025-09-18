package app.api

import app.model.PolicyRequest
import app.service.IntegrationService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status
import jakarta.ws.rs.core.UriBuilder

/**
 * REST-resource for opprettelse av forsikringsavtale.
 *
 * Hvorfor slik:
 * - POST kan trigge dupliserte kall (retries/dobbelklikk) → idempotens via "Idempotency-Key".
 * - Korrelasjons-ID på alle svar for enkel sporing på tvers av systemer.
 * - Klart skille mellom klientfeil (4xx) og downstream-feil (502).
 *
 * Endepunkter:
 * - POST /api/agreements
 *   Inndata:
 *     - Header "Idempotency-Key": Påkrevd. Samme nøkkel + samme payload → samme svar.
 *     - Header "X-Correlation-ID": Valgfri. Genereres dersom mangler.
 *     - Body: PolicyRequest (JSON).
 *   Flyt:
 *     1) Validerer at Idempotency-Key og body finnes (ellers 400).
 *     2) Kaller IntegrationService.createAgreement(...):
 *        - Oppretter avtale første gang og cacher resultatet på nøkkelen.
 *        - Returnerer fra cache ved gjentakelse med identisk payload.
 *        - Kaster IllegalStateException ved samme nøkkel + *annen* payload.
 *     3) Svar:
 *        - 201 Created + Location: /api/agreements/{id} når ny avtale er laget.
 *        - 200 OK + samme payload når resultat hentes fra idempotency-cache.
 *        - 409 Conflict når nøkkel gjenbrukes med avvikende payload.
 *        - 502 Bad Gateway når downstream feiler etter retries/kompensasjon.
 *     4) X-Correlation-ID header settes alltid på responsen.
 *
 * - POST /api/agreements/_simulate/sentFailures/{n}
 *   Test-hook for å simulere at "oppdater status til SENT" feiler de neste n gangene (trigger retry-logikk).
 *
 * Viktige annotasjoner:
 * - @Path("/api/agreements"): Basepath for resource.
 * - @Produces/@Consumes(JSON): JSON inn/ut.
 * - @POST på create(): Håndterer opprettelse (ikke idempotent by default → vi gjør den idempotent via nøkkel).
 * - @HeaderParam / @PathParam: Binder HTTP-headers og path-variabler til parametere.
 */


@Path("/api/agreements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class IntegrationResource(private val service: IntegrationService) {

    @POST
    fun create(
        @HeaderParam("Idempotency-Key") idemKey: String?,
        @HeaderParam("X-Correlation-ID") correlationIdHeader: String?,
        req: PolicyRequest?
    ): Response {
        val correlationId = correlationIdHeader ?: (Correlator.current())
        if (idemKey.isNullOrBlank()) {
            return Response.status(Status.BAD_REQUEST).entity(mapOf("error" to "Missing Idempotency-Key")).build()
        }
        if (req == null) {
            return Response.status(Status.BAD_REQUEST).entity(mapOf("error" to "Missing body")).build()
        }
        return try {
            val result = service.createAgreement(req, correlationId, idemKey)
            val location = UriBuilder.fromPath("/api/agreements/${result.response.agreementId}").build()
            val status = if (result.fromCache) Status.OK else Status.CREATED
            Response.status(status)
                .location(location)
                .entity(result.response)
                .header("X-Correlation-ID", correlationId)
                .build()
        } catch (e: IllegalStateException) {
            Response.status(Status.CONFLICT).entity(mapOf("error" to e.message, "correlationId" to correlationId)).build()
        } catch (e: Exception) {
            Response.status(Status.BAD_GATEWAY).entity(mapOf("error" to "Downstream error: ${e.message}", "correlationId" to correlationId)).build()
        }
    }

    @POST
    @Path("/_simulate/sentFailures/{n}")
    fun simulateFailures(@PathParam("n") n: Int): Response {
        service.domain.failSentStatusTimes = n
        return Response.ok(mapOf("willFailTimes" to n)).build()
    }
}
