package org.cloudfoundry.promregator.lite.discovery

import kotlinx.coroutines.*
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

private val log = mu.KotlinLogging.logger { }

@Suppress("DeferredResultUnused")
class RateLimiter(
        val maxRequestsPerMinute: Int = 60,
        private val clock: Clock = Clock.systemUTC(),
) : CoroutineScope {

    val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext get() = supervisorJob

    private val _availableTokens = AtomicInteger(maxRequestsPerMinute)
    private val isRefilling = AtomicBoolean(false)
    private val _totalWaitTimeMs = AtomicLong(0)
    private val _waitQueue = AtomicInteger(0)
    private val refillRate = maxRequestsPerMinute / 60
    val availableTokens: Int get() = _availableTokens.get()
    val totalWaitTimeMs: Long get() = _totalWaitTimeMs.get()
    val waitQueue: Int get() = _waitQueue.get()

    init {
        async {
            while (isActive) {
                delay(1000)
                refillOnePassOfTokens()
            }
        }
    }

    internal fun refillOnePassOfTokens() {
        val avail = availableTokens
        if (avail < maxRequestsPerMinute) {
            val newTokenCount = _availableTokens.addAndGet(refillRate)
            if (newTokenCount > maxRequestsPerMinute) {
                _availableTokens.set(maxRequestsPerMinute)
            }
            log.debug { "Refill: $availableTokens" }
        } else {
            isRefilling.set(false)
        }
    }

    suspend fun <T> whenTokenAvailable(block: suspend () -> T): T? {
        var tokenAcquired = false
        while (!tokenAcquired) {
            if (availableTokens > 0) {
                val currentCount = _availableTokens.decrementAndGet()
                tokenAcquired = currentCount >= 0
            }
            if (tokenAcquired) {
                return block()
            } else {
                _waitQueue.incrementAndGet()
                delay(1000)
                _totalWaitTimeMs.addAndGet(1000)
                _waitQueue.decrementAndGet()
            }
        }
        return null
    }
}

//fun main() {
//    val bucket = RateLimiter(120)
//
//    runBlocking {
//        (1..140).forEach { count ->
//            val response = bucket.whenTokenAvailable { "Got $count" }
//            println(response)
//        }
//
//    }
//}
