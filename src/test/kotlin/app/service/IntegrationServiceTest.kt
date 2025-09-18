package app.service

import app.client.DomainSystemClient
import app.client.LetterService
import app.model.*
import app.repo.IdempotencyStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class IntegrationServiceTest {

    private lateinit var domain: DomainSystemClient
    private lateinit var letters: CountingLetterService
    private lateinit var store: IdempotencyStore
    private lateinit var service: IntegrationService

    @BeforeEach
    fun setup() {
        domain = DomainSystemClient()
        letters = CountingLetterService()
        store = IdempotencyStore()
        service = IntegrationService(domain, letters, store)
    }

    private fun req(code: String = "HOME-STD") = PolicyRequest(
        productCode = code,
        customer = Customer("12059012345", "Ola", "Nordmann", "ola@example.com"),
        coverages = listOf(Coverage("BUILDING", 2_000_000, 10_000)),
        startDate = "2025-10-01"
    )

    @Test
    fun `happy path with retry then idempotent repeat`() {
        domain.failSentStatusTimes = 1
        val correlationId = "cid-1"
        val key = "idem-1"

        val first = service.createAgreement(req(), correlationId, key)
        assertFalse(first.fromCache)
        assertEquals(AgreementStatus.SENT, domain.getStatus(first.response.agreementId))
        assertTrue(first.response.pricedPremium > 0)
        assertEquals(1, letters.sentCount.get())

        val second = service.createAgreement(req(), correlationId, key)
        assertTrue(second.fromCache)
        assertEquals(first.response.agreementId, second.response.agreementId)
        assertEquals(1, letters.sentCount.get(), "brev skal ikke sendes på nytt for idempotent svar")
    }

    @Test
    fun `conflict when same key used with different payload`() {
        val correlationId = "cid-2"
        val key = "idem-2"
        service.createAgreement(req("HOME-STD"), correlationId, key)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.createAgreement(req("HOME-PLUS"), correlationId, key)
        }
        assertTrue(ex.message!!.contains("Idempotency-Key"))
    }

    // Enkel fake: bare teller kall
    private class CountingLetterService : LetterService {
        val sentCount = AtomicInteger(0)
        override fun sendWelcomeLetter(agreementId: String, email: String): Boolean {
            sentCount.incrementAndGet()
            return true
        }
    }
}
