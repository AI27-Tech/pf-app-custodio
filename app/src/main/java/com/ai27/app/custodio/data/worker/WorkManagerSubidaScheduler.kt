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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Implementación real de [SubidaScheduler] sobre WorkManager.
class WorkManagerSubidaScheduler(
    context: Context,
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
) : SubidaScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun encolarSubidaMedio(reporteId: String, medioId: String) {
        val request = OneTimeWorkRequestBuilder<WorkerSubidaMedio>()
            .setInputData(workDataOf(SubidaScheduler.REPORTE_ID to reporteId, SubidaScheduler.MEDIO_ID to medioId))
            .setConstraints(restriccionRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SubidaScheduler.NOMBRE_SUBIDA + medioId, ExistingWorkPolicy.REPLACE, request)
    }

    override fun sincronizarReporte(reporteId: String) {
        val request = OneTimeWorkRequestBuilder<WorkerSyncReporte>()
            .setInputData(workDataOf(SubidaScheduler.REPORTE_ID to reporteId))
            .setConstraints(restriccionRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SubidaScheduler.NOMBRE_SYNC + reporteId, ExistingWorkPolicy.REPLACE, request)
    }

    override suspend fun reconciliar() = withContext(Dispatchers.IO) {
        reporteRepository.obtenerPendientesSubida().forEach { sincronizarReporte(it.id) }
        medioRepository.obtenerPendientes().forEach { encolarSubidaMedio(it.reporteId, it.id) }
    }

    private val restriccionRed = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
