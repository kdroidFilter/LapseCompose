package dev.lapse.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveTimeAccumulatorTest {
    @Test
    fun accumulatesOnlyActiveIntervalsThroughIdleAndLock() {
        val clock = MutableClock()
        val accumulator = ActiveTimeAccumulator(0, clock)
        accumulator.transitionTo(UserActivityState.Active)
        clock.advance(12 * 60_000)
        accumulator.transitionTo(UserActivityState.Idle)
        clock.advance(4 * 60_000)
        accumulator.transitionTo(UserActivityState.Active)
        clock.advance(3 * 60_000)
        accumulator.transitionTo(UserActivityState.Locked)
        clock.advance(60 * 60_000)
        accumulator.transitionTo(UserActivityState.Active)
        clock.advance(20_000)
        assertEquals(15 * 60_000 + 20_000, accumulator.currentMs)
    }

    @Test
    fun reconstructsDisplayFromPersistedDuration() {
        val clock = MutableClock()
        val accumulator = ActiveTimeAccumulator(2 * 3_600_000 + 4 * 60_000, clock)
        accumulator.transitionTo(UserActivityState.Active)
        clock.advance(6 * 60_000 + 30_000)
        assertEquals(2 * 3_600_000 + 10 * 60_000 + 30_000, accumulator.currentMs)
    }
}
