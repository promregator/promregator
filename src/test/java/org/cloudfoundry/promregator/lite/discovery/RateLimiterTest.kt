package org.cloudfoundry.promregator.lite.discovery

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeEach

@ExperimentalCoroutinesApi
internal class RateLimiterTest {
    lateinit var sut: RateLimiter

    @BeforeEach
    fun setup() {
        sut = RateLimiter(60)
    }

    @Test
    fun `getAvailableTokens empties when used and refills over time`() = runBlocking {
        // It starts full
        sut.availableTokens shouldBe 60

        // After using a few it goes down
        sut.doSlowWork(times = 20, delayMs = 100).awaitAll()

        delay(10)
        sut.availableTokens shouldBe 40

        // Use up the rest goes to zero
        sut.doSlowWork(times = 40)
        delay(10)
        sut.availableTokens shouldBe 0

        // Pretend 5 seconds have passed and it fills up
//        repeat(20) { sut.refillOnePassOfTokens() }
        delay(5_000) // Slow test is gross due to delays. TODO replace with non-delay specific code
        sut.availableTokens shouldBe 5
    }

    @Test
    fun getTotalWaitTimeMs() = runBlocking {
        sut.totalWaitTimeMs shouldBe 0

        // Use up all the tokens
        async { sut.doSlowWork(times = 60, delayMs = 2000) }
        sut.totalWaitTimeMs shouldBe 0

        // After using a few it goes down
        async { sut.doSlowWork(times = 1, delayMs = 2000) }
        delay(3000)
        sut.totalWaitTimeMs shouldBe 1000
    }

    @Test
    fun getWaitQueue() = runBlocking {
        sut.waitQueue shouldBe 0

        // After using a few it goes down
        sut.doSlowWork(times = 70, delayMs = 1100)
        delay(1000)

        sut.waitQueue shouldBeGreaterThan 2
    }

    private suspend fun RateLimiter.doSlowWork(times: Int = 1, delayMs: Long = 100): List<Deferred<Unit?>> {
        return (0 until times).map {
            async {
                this@doSlowWork.whenTokenAvailable {
                    delay(delayMs)
                }
            }
        }
    }
}
