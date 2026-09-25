package com.ai27.app.custodio

import android.app.Application
import androidx.work.Configuration
import com.ai27.app.custodio.data.worker.CustodioWorkerFactory
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class EchoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: CustodioWorkerFactory

    @Inject
    lateinit var etiquetaRepository: EtiquetaRepository

    private val scopeAplicacion = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance()
        // Calentamiento del catálogo de etiquetas (A4): se lee aquí al abrir la app (caché
        // local primero, red solo si no hay copia), de modo que cuando el custodio captura
        // la HojaEtiqueta abre con los chips ya cargados sin esperar a Firestore.
        scopeAplicacion.launch { etiquetaRepository.obtener() }
    }
}