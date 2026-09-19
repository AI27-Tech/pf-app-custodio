package com.ai27.app.custodio.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai27.app.custodio.data.pipeline.SubidaMedioProceso
import com.ai27.app.custodio.domain.Result as DominioResult

// Sube un medio al backend. Reintenta con backoff exponencial (configurado en el
// Scheduler); tras [MAX_INTENTOS] falla y queda `FALLIDA` local para el reintento manual.
class WorkerSubidaMedio(
    appContext: Context,
    params: WorkerParameters,
    private val proceso: SubidaMedioProceso,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reporteId = inputData.getString(SubidaScheduler.REPORTE_ID)
            ?: return Result.failure()
        val medioId = inputData.getString(SubidaScheduler.MEDIO_ID)
            ?: return Result.failure()

        return when (proceso.ejecutar(reporteId, medioId)) {
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