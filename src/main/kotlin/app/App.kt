package app

import java.net.URI
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import app.api.IntegrationResource
import app.client.DomainSystemClient
import app.client.DefaultLetterService
import app.repo.IdempotencyStore
import app.service.IntegrationService

/**
 * Starter en embedded HTTP-server (Grizzly) og eksponerer JAX-RS (Jersey) ressurser.
 *
 * Hvorfor denne oppsettet:
 * - Embedded server for rask lokal utvikling/testing (ingen appserver).
 * - Lytter på 0.0.0.0:8080 slik at klienter utenfra (f.eks. Docker/localhost) kan nå tjenesten.
 * - ResourceConfig scanner pakken "app" for @Path-annoterte ressurser (REST-endepunkter).
 * - Shutdown hook stopper serveren ryddig ved Ctrl+C/kill (frigjør porter/ressurser).
 * - main-tråden blokkeres (join) så prosessen lever så lenge serveren kjører.
 *
 * Flyt:
 * 1) baseUri = http://0.0.0.0:8080/
 * 2) rc = ResourceConfig().packages("app") -> registrerer JAX-RS ressurser/filters/providers.
 * 3) GrizzlyHttpServerFactory.createHttpServer(baseUri, rc) -> starter HTTP-server.
 * 4) addShutdownHook { server.shutdownNow() } -> sikker nedstenging.
 * 5) println(...) -> enkel oppstartslogg (hint: POST /api/agreements finnes i app.*).
 * 6) Thread.currentThread().join() -> hindrer at main returnerer og stopper serveren.
 */


fun main() {
    val baseUri = URI.create("http://0.0.0.0:8080/")

    val domain = DomainSystemClient()
    val letters = DefaultLetterService()
    val store = IdempotencyStore()
    val service = IntegrationService(domain, letters, store)

    val rc = ResourceConfig()
        .property(ServerProperties.WADL_FEATURE_DISABLE, true)
        .register(IntegrationResource(service))
        .packages("app")

    val server: HttpServer = GrizzlyHttpServerFactory.createHttpServer(baseUri, rc)
    Runtime.getRuntime().addShutdownHook(Thread { server.shutdownNow() })
    println("Server running at $baseUri  ->  POST /api/agreements")
    Thread.currentThread().join()
}