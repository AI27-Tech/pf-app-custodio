package com.ai27.app.custodio

import android.app.Application
import androidx.work.Configuration
import com.ai27.app.custodio.data.worker.CustodioWorkerFactory
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
}