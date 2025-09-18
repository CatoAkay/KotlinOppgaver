# KotlinOppgaver – Insurance Integration (Kotlin + Jersey)

En liten REST-tjeneste i Kotlin som oppretter forsikringsavtaler og orkestrerer kall mot et mock fagsystem og en enkel brevtjeneste. Løsningen demonstrerer idempotens (Idempotency-Key), korrelasjons-ID (X-Correlation-ID), retry med eksponentiell backoff, og enkel audit-logg.

---

## Forutsetninger
- JDK 17+ (testet på Temurin 21)
- Maven 3.9+

## Bygg og kjøring

### 1) Bygg fat-jar (server)
mvn -q -DskipTests clean package
java -jar target/KotlinOppgaver-1.0.0-all.jar

Du skal se:
Server running at http://0.0.0.0:8080/  ->  POST /api/agreements

### 2) Kjør klient/systemtest i nytt terminalvindu
mvn -q exec:java -Dexec.mainClass=app.ClientMain

Forventet:
- #1 → 201 Created
- #2 → 200 OK (idempotent gjentakelse med samme nøkkel/payload)
- #3 → 409 Conflict (samme nøkkel, annen payload)

---

## Prosjektstruktur (utdrag)
src/
main/kotlin/app/
App.kt                         # Starter Grizzly + Jersey
ClientMain.kt                  # Headless systemtest-klient
api/
IntegrationResource.kt       # POST /api/agreements (+ test-hook)
AuditResource.kt             # GET /api/audit (valgfri)
CorrelationFilter.kt         # Leser/genererer X-Correlation-ID
Correlator.kt                # UUID-generator for korrelasjons-ID
client/
DomainSystemClient.kt        # Mock fagsystem (in-memory)
LetterService.kt             # Interface + DefaultLetterService
config/
JacksonConfig.kt             # ObjectMapper (Kotlin + JavaTime)
model/
Models.kt                    # DTO-er og enums
repo/
IdempotencyStore.kt          # In-memory idempotens-cache
service/
IntegrationService.kt        # Orkestrering, retry, kompensasjon
AuditLog.kt                  # Enkel audit-logg (in-memory)
util/
Retry.kt                     # Generisk retry m/backoff + jitter

---

## API

### POST /api/agreements
Oppretter avtale (idempotent via Idempotency-Key).

Headers
- Content-Type: application/json
- Idempotency-Key: <uuid> (påkrevd)
- X-Correlation-ID: <valgfri> (genereres hvis mangler)

Body (PolicyRequest)
{
"productCode": "HOME-STD",
"customer": {"nationalId":"12059012345","firstName":"Ola","lastName":"Nordmann","email":"ola@example.com"},
"coverages": [{"type":"BUILDING","sumInsured":2000000,"deductible":10000}],
"startDate": "2025-10-01"
}

Respons
- 201 Created ved første opprettelse (Location header settes)
- 200 OK ved idempotent gjentakelse (samme nøkkel + identisk body)
- 409 Conflict hvis samme nøkkel gjenbrukes med annen body
- 502 Bad Gateway hvis nedstrøms feiler permanent

Eksempel
IDK=$(uuidgen)
curl -i -X POST http://localhost:8080/api/agreements \
-H "Content-Type: application/json" \
-H "Idempotency-Key: $IDK" \
-d '{"productCode":"HOME-STD","customer":{"nationalId":"12059012345","firstName":"Ola","lastName":"Nordmann","email":"ola@example.com"},"coverages":[{"type":"BUILDING","sumInsured":2000000,"deductible":10000}],"startDate":"2025-10-01"}'

### Test-hook: POST /api/agreements/_simulate/sentFailures/{n}
Simuler at status SENT-steget feiler de neste n gangene (for å trigge retry).

curl -s -X POST http://localhost:8080/api/agreements/_simulate/sentFailures/1 -H 'Content-Type: application/json' -d '{}'

### (Valgfritt) GET /api/audit?limit=50
Returnerer siste audit-hendelser (krever at AuditResource er med).

---

## Domeneflyt (kort)
1. createDraft → enrichUnderwriting → priceOffer → activateAgreement
2. updateStatusToSent med Retry.run(...) (eksponentiell backoff + jitter)
3. Best effort: send velkomstbrev (feil velter ikke transaksjonen)
4. Idempotens: første svar caches per Idempotency-Key

---

## Idempotens
- Samme nøkkel + identisk payload → samme svar retur (200 på gjentakelse)
- Samme nøkkel + annen payload → 409 Conflict
- In-memory IdempotencyStore kan byttes til Redis/DB for multi-instans og persistens.

---

## Testing
Kjør alle tester:
mvn -q test

Dekning (utdrag):
- IdempotencyStoreTest: put/get, lik/ulik payload
- RetryTest: lykkes etter N-1 feil, feiler etter max
- IntegrationServiceTest: 201 → 200 (cache) → 409 (konflikt), samt retry i SENT

---

## Feilsøking (vanlige)
- WADL warning (JAXB) ved oppstart → ufarlig. Slås av med ServerProperties.WADL_FEATURE_DISABLE=true.
- Jackson Instant: jackson-datatype-jsr310 + JavaTimeModule må være registrert (allerede konfigurert).
- Shade-plugin: hvis “Could not replace original artifact …”, bruk classifier -all (allerede satt).
- Idempotens virker ikke: verifiser at IntegrationResource registreres som singleton med delt IdempotencyStore.

---

## Videre arbeid
- GET /api/agreements/{id} for oppslag.
- Persistens: H2/Postgres/Redis for IdempotencyStore og avtaler.
- Observability: MDC for X-Correlation-ID, metrics, tracing.
- Circuit breaker / rate-limiting.

---

## Lisens
MIT (valgfritt). Legg til LICENSE dersom ønskelig.
