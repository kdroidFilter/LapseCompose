package dev.lapse.di

import dev.lapse.app.AppViewModel
import dev.lapse.data.AppStore
import dev.lapse.platform.PlatformBridge
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph(AppScope::class)
interface AppGraph {
    val viewModelFactory: AppViewModel.Factory
    val store: AppStore
    val platform: PlatformBridge
}

fun createAppGraph(): AppGraph = createGraph()
