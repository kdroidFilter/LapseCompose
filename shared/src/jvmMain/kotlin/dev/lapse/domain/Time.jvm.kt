package dev.lapse.domain

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun monotonicTimeMs(): Long = System.nanoTime() / 1_000_000L
