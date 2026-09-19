package com.ai27.app.custodio.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai27.app.custodio.data.pipeline.ReporteSyncProceso
import com.ai27.app.custodio.domain.Result as DominioResult

// Asegura que el documento del reporte exista en Firestore y aplica su transición
// de ciclo y contadores según el estado local. Idempotente.
class WorkerSyncReporte(
    appContext: Context,
    params: WorkerParameters,
    private val proceso: ReporteSyncProceso,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reporteId = inputData.getString(SubidaScheduler.REPORTE_ID)
            ?: return Result.failure()

        return when (proceso.ejecutar(reporteId)) {
            is DominioResult.Success -> Result.success()
            is DominioResult.Failure -> if (runAttemptCount >= MAX_INTENTOS) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val MAX_INTENTOS = 4
    }
}