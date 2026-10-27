package com.datapulse.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Listing 9: Hilt + WorkManager bootstrap. Channels created once (API 26+).
@HiltAndroidApp
class DataPulseApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("pulse_alerts", "Data alerts", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel("pulse_cycle", "Cycle reminders", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel("pulse_anomaly", "Anomalies", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
