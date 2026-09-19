package com.ai27.app.custodio

import android.app.Application
import androidx.work.Configuration
import com.ai27.app.custodio.data.worker.CustodioWorkerFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AppCustodioApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: CustodioWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance()
    }
}