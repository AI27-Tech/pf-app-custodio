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
import com.ai27.app.custodio.domain.util.fechaDesdeUltimos30Dias
import java.time.Instant
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
            .addTag(SubidaScheduler.TAG_SUBIDA)
            .build()
        workManager.enqueueUniqueWork(SubidaScheduler.NOMBRE_SUBIDA + medioId, ExistingWorkPolicy.REPLACE, request)
    }

    override fun sincronizarReporte(reporteId: String) {
        val request = OneTimeWorkRequestBuilder<WorkerSyncReporte>()
            .setInputData(workDataOf(SubidaScheduler.REPORTE_ID to reporteId))
            .setConstraints(restriccionRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(SubidaScheduler.TAG_SUBIDA)
            .build()
        workManager.enqueueUniqueWork(SubidaScheduler.NOMBRE_SYNC + reporteId, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelarTodo() {
        // Por etiqueta y no con cancelAllWork(): así no se arrastra trabajo ajeno que se
        // añada al proyecto más adelante.
        workManager.cancelAllWorkByTag(SubidaScheduler.TAG_SUBIDA)
    }

    override suspend fun reconciliar(custodioUid: String) = withContext(Dispatchers.IO) {
        // La ventana de 30 días evita que el backlog histórico se reencole entero en cada
        // arranque: sin cota, a los meses son cientos de trabajos únicos por lanzamiento.
        val fechaDesde = fechaDesdeUltimos30Dias(Instant.now())
        reporteRepository.obtenerPendientesSubida(custodioUid, fechaDesde).forEach { sincronizarReporte(it.id) }
        medioRepository.obtenerPendientes(custodioUid).forEach { encolarSubidaMedio(it.reporteId, it.id) }
    }

    private val restriccionRed = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
