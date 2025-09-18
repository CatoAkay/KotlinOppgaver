package app.repo

import app.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IdempotencyStoreTest {

    private fun sampleReq(code: String = "HOME-STD") = PolicyRequest(
        productCode = code,
        customer = Customer("12059012345", "Ola", "Nordmann", "ola@example.com"),
        coverages = listOf(Coverage("BUILDING", 2_000_000, 10_000)),
        startDate = "2025-10-01"
    )

    @Test
    fun `put and get returns same response`() {
        val store = IdempotencyStore()
        val req = sampleReq()
        val resp = PolicyResponse("id-1", AgreementStatus.SENT, 12345, "cid-1")
        store.put("key-1", req, resp)

        val entry = store.get("key-1")
        assertNotNull(entry)
        assertEquals(resp, entry!!.response)
    }

    @Test
    fun `validateSameRequest true for same payload, false for different`() {
        val store = IdempotencyStore()
        val reqA = sampleReq("HOME-STD")
        val reqB = sampleReq("HOME-PLUS")
        val resp = PolicyResponse("id-1", AgreementStatus.SENT, 12345, "cid-1")
        store.put("key-1", reqA, resp)

        assertTrue(store.validateSameRequest("key-1", reqA))
        assertFalse(store.validateSameRequest("key-1", reqB))
    }
}