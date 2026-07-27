package com.plantora.billing

import android.os.Handler
import android.os.Looper
import org.acra.ACRA

/**
 * Lightweight main-thread ANR ("freeze") detector. Every [timeoutMs] it posts a
 * ping to the main-thread [Handler] and waits; if the main thread doesn't run the
 * ping within the window it is wedged (an infinite loop, a deadlock, or a long
 * synchronous call), so we grab the **main thread's current stack trace** and send
 * it through the app's existing self-hosted crash reporter (ACRA → /crash-reports).
 *
 * This exists because a hard freeze (unlike a crash) throws no exception, so it is
 * invisible to normal crash reporting — which is why the "Review screen freezes"
 * reports could never be pinned down. With this, the next real occurrence uploads
 * the exact frame that is looping.
 *
 * Deliberately dependency-free and conservative: one report per distinct wedge
 * (not per poll), a daemon thread, and a 5s threshold so it never fires for normal
 * work (all real I/O in this app is off the main thread).
 */
class AnrWatchdog(private val timeoutMs: Long = 5_000L) : Thread("plantora-anr-watchdog") {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Bumped on the main thread each time it processes our ping. */
    @Volatile
    private var completedTicks = 0L

    init {
        isDaemon = true
    }

    override fun run() {
        var lastReportedTick = -1L
        while (!isInterrupted) {
            val scheduledTick = completedTicks
            mainHandler.post { completedTicks++ }
            try {
                sleep(timeoutMs)
            } catch (e: InterruptedException) {
                return
            }
            val mainResponded = completedTicks != scheduledTick
            if (!mainResponded && scheduledTick != lastReportedTick) {
                // Main thread has been blocked for >= timeoutMs — report once.
                lastReportedTick = scheduledTick
                reportAnr()
            }
        }
    }

    private fun reportAnr() {
        if (!ACRA.isInitialised) return
        val mainThread = Looper.getMainLooper().thread
        val anr = AppNotRespondingException(
            "App not responding: main thread blocked for >= ${timeoutMs}ms",
        ).apply {
            // Point the report at where the main thread is actually stuck.
            stackTrace = mainThread.stackTrace
        }
        runCatching { ACRA.errorReporter.handleSilentException(anr) }
    }
}

/** Marker exception carrying the frozen main thread's stack for crash reports. */
class AppNotRespondingException(message: String) : RuntimeException(message)
