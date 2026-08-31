package dev.lapse.di

import dev.lapse.data.AppStore
import dev.lapse.data.createAppStore
import dev.lapse.domain.MonotonicClock
import dev.lapse.domain.StopwatchClock
import dev.lapse.platform.PlatformBridge
import dev.lapse.platform.createPlatformBridge
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@ContributesTo(AppScope::class)
@BindingContainer
object AppBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideStore(): AppStore = createAppStore()

    @Provides
    @SingleIn(AppScope::class)
    fun providePlatform(): PlatformBridge = createPlatformBridge()

    @Provides
    fun provideDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun provideClock(): MonotonicClock = StopwatchClock()
}
