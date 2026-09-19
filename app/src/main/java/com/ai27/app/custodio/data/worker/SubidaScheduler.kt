package com.ai27.app.custodio.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

// Encola el trabajo de subida. Cada medio y cada reporte tienen un nombre único de
// cola: un reintento reemplaza la tarea anterior en vez de duplicarla (PLAN §5.4).
@Singleton
class SubidaScheduler @Inject constructor(
    context: Context,
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
) {
    private val workManager = WorkManager.getInstance(context)

    fun encolarSubidaMedio(reporteId: String, medioId: String) {
        val request = OneTimeWorkRequestBuilder<WorkerSubidaMedio>()
            .setInputData(workDataOf(REPORTE_ID to reporteId, MEDIO_ID to medioId))
            .setConstraints(restriccionRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(NOMBRE_SUBIDA + medioId, ExistingWorkPolicy.REPLACE, request)
    }

    fun sincronizarReporte(reporteId: String) {
        val request = OneTimeWorkRequestBuilder<WorkerSyncReporte>()
            .setInputData(workDataOf(REPORTE_ID to reporteId))
            .setConstraints(restriccionRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(NOMBRE_SYNC + reporteId, ExistingWorkPolicy.REPLACE, request)
    }

    // Repone la cola al arrancar la app: re-encola reportes y medios que quedaron
    // pendientes de la sesión anterior (el worker espera a que haya red).
    suspend fun reconciliar() = withContext(Dispatchers.IO) {
        reporteRepository.obtenerPendientesSubida().forEach { sincronizarReporte(it.id) }
        medioRepository.obtenerPendientes().forEach { encolarSubidaMedio(it.reporteId, it.id) }
    }

    private val restriccionRed = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val REPORTE_ID = "reporteId"
        const val MEDIO_ID = "medioId"
        const val NOMBRE_SUBIDA = "subida_medio_"
        const val NOMBRE_SYNC = "sync_reporte_"
    }
}