package app.api

import java.util.UUID

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider

/**
 * Korrelasjons-ID filter (JAX-RS).
 *
 * Hvorfor:
 * - Én unik "X-Correlation-ID" per request gjør det lett å følge et kall gjennom logger
 *   og videre til nedstrøms systemer (trace over flere tjenester).
 *
 * Hva skjer (viktigste valg):
 * - Inngående: Hvis klienten ikke sender "X-Correlation-ID", genereres en UUID v4.
 *   (Why: sikre at alle kall kan spores; vi aksepterer klientens ID om den finnes.)
 * - Propagering: ID’en lagres i requestContext properties ("correlationId") slik at
 *   ressurslag/service kan logge samme verdi gjennom hele flyten.
 * - Utgående: Samme ID legges alltid på svaret som "X-Correlation-ID" (echo).
 *
 * Rammeverk:
 * - @Provider + ContainerRequestFilter/ContainerResponseFilter gjør at Jersey/JAX-RS
 *   automatisk kjører filteret før/etter alle ressurser.
 *
 * Sikkerhet/robusthet (mulige utvidelser):
 * - Valider/normaliser innkommende header (lengde/format), evt. alltid overskriv med ny UUID.
 * - Legg ID i MDC (SLF4J/Logback) for automatisk korrelasjon i logger.
 */


@Provider
class CorrelationFilter : ContainerRequestFilter, ContainerResponseFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val cid = requestContext.headers.getFirst("X-Correlation-ID") ?: UUID.randomUUID().toString()
        requestContext.headers.putSingle("X-Correlation-ID", cid)
        requestContext.setProperty("correlationId", cid)
    }

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val cid = requestContext.getProperty("correlationId") as? String
        if (cid != null) responseContext.headers.putSingle("X-Correlation-ID", cid)
    }
}