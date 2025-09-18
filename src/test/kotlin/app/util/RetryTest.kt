package app.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class RetryTest {

    @Test
    fun `succeeds after transient failures`() {
        val attempts = AtomicInteger(0)
        val result = Retry.run(
            maxAttempts = 3,
            initialDelayMs = 1,
            factor = 1.0,
            jitter = false
        ) {
            val n = attempts.incrementAndGet()
            if (n < 3) error("fail $n")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `throws after max attempts`() {
        val attempts = AtomicInteger(0)
        val ex = assertThrows(RuntimeException::class.java) {
            Retry.run(
                maxAttempts = 2,
                initialDelayMs = 1,
                factor = 1.0,
                jitter = false
            ) {
                attempts.incrementAndGet()
                error("always fails")
            }
        }
        assertTrue(ex.message!!.contains("always fails"))
        assertEquals(2, attempts.get())
    }
}