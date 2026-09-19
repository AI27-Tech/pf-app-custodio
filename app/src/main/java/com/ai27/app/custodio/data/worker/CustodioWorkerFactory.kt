package com.ai27.app.custodio.data.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ai27.app.custodio.data.pipeline.ReporteSyncProceso
import com.ai27.app.custodio.data.pipeline.SubidaMedioProceso
import javax.inject.Inject
import javax.inject.Singleton

// Factory propia (sin hilt-work) para construir los workers con el grafo de Hilt.
// Se registra via Configuration.Provider en AppCustodioApplication.
@Singleton
class CustodioWorkerFactory @Inject constructor(
    private val procesoSubida: SubidaMedioProceso,
    private val procesoSync: ReporteSyncProceso,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        WorkerSubidaMedio::class.java.name ->
            WorkerSubidaMedio(appContext, workerParameters, procesoSubida)

        WorkerSyncReporte::class.java.name ->
            WorkerSyncReporte(appContext, workerParameters, procesoSync)

        else -> null
    }
}