package app.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.ws.rs.ext.ContextResolver
import jakarta.ws.rs.ext.Provider

/**
 * Global Jackson-konfig for Jersey (JAX-RS).
 *
 * Hensikt:
 * - Gi én felles ObjectMapper for hele API-et (JSON inn/ut).
 *
 * Valg:
 * - registerKotlinModule(): forstå Kotlin-typer (data classes, nullability, defaultverdier).
 * - registerModule(JavaTimeModule()): støtte for java.time (Instant/LocalDate/LocalDateTime).
 * - disable(WRITE_DATES_AS_TIMESTAMPS): skriv datoer som ISO-8601 (f.eks. "2025-01-01T12:34:56Z") i stedet for epoch.
 * - disable(FAIL_ON_UNKNOWN_PROPERTIES): ignorer ukjente felter ved deserialisering (mer robust mot endringer).
 *
 * Integrasjon:
 * - @Provider + ContextResolver<ObjectMapper> gjør at Jersey automatisk bruker denne mapperen
 *   for alle JSON-requests/responser uten ekstra oppsett per resource.
 */


@Provider
class JacksonConfig : ContextResolver<ObjectMapper> {
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule()) // Why: støtte for Instant/LocalDateTime
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // ISO-8601, ikke epoch
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    override fun getContext(type: Class<*>?): ObjectMapper = mapper
}
