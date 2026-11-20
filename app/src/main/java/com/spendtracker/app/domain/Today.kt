package com.spendtracker.app.domain

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Longest wait between date checks, so a device that slept through midnight catches up quickly. */
const val TODAY_MAX_CHECK_INTERVAL_MS: Long = 60_000L

/**
 * Milliseconds to wait before checking the date again: until just after the next local midnight,
 * but never longer than [TODAY_MAX_CHECK_INTERVAL_MS] (coroutine delays do not advance in deep sleep).
 */
fun millisUntilNextDateCheck(now: ZonedDateTime): Long {
    val nextMidnight: ZonedDateTime = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    val untilMidnight: Long = Duration.between(now, nextMidnight).toMillis() + 50L
    return untilMidnight.coerceIn(1L, TODAY_MAX_CHECK_INTERVAL_MS)
}

/**
 * Emits today's date now and again whenever the date changes (midnight, month change, time zone
 * change), so screens never keep a stale "today" or "current month".
 */
fun todayFlow(clock: Clock? = null): Flow<LocalDate> = flow {
    while (true) {
        // Without an explicit clock, re-read the system zone each time so zone changes are picked up.
        val now: ZonedDateTime = if (clock != null) ZonedDateTime.now(clock) else ZonedDateTime.now()
        emit(now.toLocalDate())
        delay(millisUntilNextDateCheck(now))
    }
}.distinctUntilChanged()
