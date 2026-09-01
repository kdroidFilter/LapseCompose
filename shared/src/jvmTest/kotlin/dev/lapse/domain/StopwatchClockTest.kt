package dev.lapse.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class StopwatchClockTest {
    @Test
    fun elapsedMovesForwardOnMonotonicClock() {
        val clock = StopwatchClock()
        val samples = List(4) {
            Thread.sleep(8)
            clock.elapsedMs
        }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(samples.last() >= 20)
    }
}
