package dev.lapse.domain

interface MonotonicClock {
    val elapsedMs: Long
}

class StopwatchClock : MonotonicClock {
    private val started = currentTimeMs()
    override val elapsedMs: Long get() = currentTimeMs() - started
}

expect fun currentTimeMs(): Long

class MutableClock(var valueMs: Long = 0) : MonotonicClock {
    fun advance(deltaMs: Long) {
        valueMs += deltaMs
    }

    override val elapsedMs: Long get() = valueMs
}

class ActiveTimeAccumulator(
    persistedDurationMs: Long,
    private val clock: MonotonicClock,
) {
    private var accumulated = persistedDurationMs
    private var activeSince: Long? = null

    val currentMs: Long
        get() {
            val since = activeSince ?: return accumulated
            return accumulated + (clock.elapsedMs - since)
        }

    fun transitionTo(state: UserActivityState) {
        val now = clock.elapsedMs
        if (state == UserActivityState.Active) {
            if (activeSince == null) activeSince = now
            return
        }
        val since = activeSince
        if (since != null) {
            accumulated += now - since
            activeSince = null
        }
    }

    fun checkpoint(): Long {
        val now = clock.elapsedMs
        val since = activeSince
        if (since != null) {
            accumulated += now - since
            activeSince = now
        }
        return accumulated
    }
}

class ApplicationUsageAccumulator(
    private val clock: MonotonicClock,
    persisted: List<ApplicationUsage> = emptyList(),
) {
    private val usage = persisted.associateBy { it.applicationId }.mapValues { (_, value) ->
        value.copy(
            displayName = applicationDisplayName(
                executableName = value.executableName,
                reportedDisplayName = value.displayName,
            ),
        )
    }.toMutableMap()
    private var current: ForegroundApplication? = null
    private var activeSince: Long? = null
    private var isActive = false

    fun setActive(active: Boolean) {
        if (active == isActive) return
        commit()
        isActive = active
        if (active && current != null) activeSince = clock.elapsedMs
    }

    fun observe(application: ForegroundApplication?) {
        if (current?.id == application?.id) {
            if (application != null) {
                current = application
                val previous = usage[application.id]
                if (previous != null) {
                    usage[application.id] = previous.copy(
                        displayName = application.resolvedDisplayName,
                        executableName = application.executableName,
                    )
                }
            }
            return
        }
        commit()
        current = application
        if (isActive && application != null) activeSince = clock.elapsedMs
    }

    fun snapshot(): List<ApplicationUsage> {
        val result = usage.toMutableMap()
        val application = current
        val since = activeSince
        if (isActive && application != null && since != null) {
            addTo(result, application, clock.elapsedMs - since)
        }
        return result.values.toList()
    }

    fun checkpoint(): List<ApplicationUsage> {
        commit()
        if (isActive && current != null) activeSince = clock.elapsedMs
        return usage.values.toList()
    }

    private fun commit() {
        val application = current
        val since = activeSince
        if (isActive && application != null && since != null) {
            addTo(usage, application, clock.elapsedMs - since)
        }
        activeSince = null
    }

    private fun addTo(
        target: MutableMap<String, ApplicationUsage>,
        application: ForegroundApplication,
        durationMs: Long,
    ) {
        if (durationMs <= 0) return
        val previous = target[application.id]
        target[application.id] = ApplicationUsage(
            applicationId = application.id,
            displayName = application.resolvedDisplayName,
            executableName = application.executableName,
            activeDurationMs = (previous?.activeDurationMs ?: 0) + durationMs,
        )
    }
}
