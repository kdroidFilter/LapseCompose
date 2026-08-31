package dev.lapse.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationUsageAccumulatorTest {
    private fun app(name: String) = ForegroundApplication(
        processId = name.hashCode(),
        executablePath = "C:\\Apps\\$name.exe",
        executableName = "$name.exe",
        displayName = name,
        observedAtMs = 0,
    )

    @Test
    fun countsForegroundAppsOnlyAcrossActiveIntervals() {
        val clock = MutableClock()
        val tracker = ApplicationUsageAccumulator(clock)
        tracker.observe(app("VS Code"))
        tracker.setActive(true)
        clock.advance(20 * 60_000)
        tracker.observe(app("Browser"))
        clock.advance(15 * 60_000)
        tracker.setActive(false)
        clock.advance(15 * 60_000)
        tracker.setActive(true)
        clock.advance(10 * 60_000)
        tracker.setActive(false)
        val usage = tracker.snapshot().associate { it.displayName to it.activeDurationMs }
        assertEquals(20 * 60_000, usage["VS Code"])
        assertEquals(25 * 60_000, usage["Browser"])
    }

    @Test
    fun pauseAndNullForegroundAddNoTime() {
        val clock = MutableClock()
        val tracker = ApplicationUsageAccumulator(clock)
        tracker.observe(app("Editor"))
        tracker.setActive(true)
        clock.advance(5 * 60_000)
        tracker.setActive(false)
        clock.advance(20 * 60_000)
        tracker.setActive(true)
        tracker.observe(null)
        clock.advance(10 * 60_000)
        tracker.observe(app("Browser"))
        clock.advance(3 * 60_000)
        tracker.observe(null)
        clock.advance(4 * 60_000)
        val usage = tracker.snapshot().associate { it.displayName to it.activeDurationMs }
        assertEquals(5 * 60_000, usage["Editor"])
        assertEquals(3 * 60_000, usage["Browser"])
    }

    @Test
    fun replacesGenericEngineMetadata() {
        val tracker = ApplicationUsageAccumulator(
            clock = MutableClock(),
            persisted = listOf(
                ApplicationUsage(
                    applicationId = """c:\fortniteclient-win64-shipping.exe""",
                    displayName = "Unreal Engine",
                    executableName = "FortniteClient-Win64-Shipping.exe",
                    activeDurationMs = 14 * 60_000,
                ),
            ),
        )
        assertEquals("Fortnite", tracker.snapshot().single().displayName)
        assertEquals(
            "Hogwarts Legacy",
            applicationDisplayName(
                executableName = "HogwartsLegacy-Win64-Shipping.exe",
                reportedDisplayName = "Unreal Engine",
                windowTitle = "Hogwarts Legacy",
            ),
        )
        assertEquals(
            "My Unity Game",
            applicationDisplayName(
                executableName = "MyUnityGame.exe",
                reportedDisplayName = "Unity Player",
                windowTitle = "My Unity Game",
            ),
        )
    }

    @Test
    fun keepsReliableProductNames() {
        assertEquals(
            "Visual Studio Code",
            applicationDisplayName(
                executableName = "Code.exe",
                reportedDisplayName = "Visual Studio Code",
                windowTitle = "main.dart - lapse - Visual Studio Code",
            ),
        )
        assertEquals(
            "Snipping Tool",
            applicationDisplayName(
                executableName = "SnippingTool.exe",
                reportedDisplayName = "Microsoft® Windows® Operating System",
                windowTitle = "Snipping Tool",
            ),
        )
    }
}
