package com.plantora.billing

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.config.ToastConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.sender.HttpSender

@HiltAndroidApp
class PlantoraApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Self-hosted crash reporting: uncaught exceptions are POSTed as JSON to
        // our own backend (/crash-reports on the same host the app talks to), so
        // no crash data ever goes to Google/Sentry. Must be initialised here, in
        // attachBaseContext, so ACRA installs its handler before anything runs.
        val builder = CoreConfigurationBuilder()
            .withBuildConfigClass(BuildConfig::class.java)
            .withReportFormat(StringFormat.JSON)
            .withPluginConfigurations(
                HttpSenderConfigurationBuilder()
                    .withUri(BuildConfig.DEFAULT_BASE_URL + "crash-reports")
                    .withHttpMethod(HttpSender.Method.POST)
                    .build(),
                // A calm, plain-language notice for the (often elderly) user — no
                // scary stack trace, no dialog to dismiss.
                ToastConfigurationBuilder()
                    .withText("The app hit a problem and restarted. We've been notified.")
                    .build(),
            )
        ACRA.init(this, builder)
    }
}
