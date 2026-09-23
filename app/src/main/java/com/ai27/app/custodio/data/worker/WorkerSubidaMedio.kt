package com.ai27.app.custodio.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.pipeline.SubidaMedioProceso
import com.ai27.app.custodio.data.remote.aTextoPersistible
import com.ai27.app.custodio.data.remote.esReintentable
import com.ai27.app.custodio.domain.Result as DominioResult
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.repository.MedioRepository

// Sube un medio al backend. Con la clasificación del hallazgo C5:
//  - fallo **transitorio** (SinConexion/ServidorIndisponible) → `retry()` sin tope; la
//    constraint de red y el backoff exponencial evitan el spin.
//  - fallo **permanente** (PermisoDenegado/ArchivoLocalAusente/Inesperado) → `failure()`
//    de inmediato: no se gastan reintentos en lo que no va a cambiar. Queda `FALLIDA`
//    local para el reintento manual, se persiste `ultimoError`/`intentosSubida` y se
//    reporta a Crashlytics como no fatal para diagnosticarlo desde la consola.
class WorkerSubidaMedio(
    appContext: Context,
    params: WorkerParameters,
    private val proceso: SubidaMedioProceso,
    private val medioRepository: MedioRepository,
    private val registroDeFallos: RegistroDeFallos,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reporteId = inputData.getString(SubidaScheduler.REPORTE_ID)
            ?: return Result.failure()
        val medioId = inputData.getString(SubidaScheduler.MEDIO_ID)
            ?: return Result.failure()

        return when (val resultado = proceso.ejecutar(reporteId, medioId)) {
            is DominioResult.Success -> Result.success()
            is DominioResult.Failure -> {
                val error = resultado.error
                if (esReintentable(error)) {
                    Result.retry()
                } else {
                    // Un fallo permanente llega aquí con el medio ya FALLIDA (lo marcó el
                    // pipeline). Aquí solo se deja el rastro para diagnóstico.
                    medioRepository.registrarFallo(medioId, error, runAttemptCount + 1)
                    registroDeFallos.registrar("subida_medio_permanente", causaDe(error))
                    Result.failure()
                }
            }
        }
    }

    private fun causaDe(error: DominioError): Throwable = when (error) {
        is DominioError.Inesperado -> error.causa
        else -> IllegalStateException(error.aTextoPersistible())
    }
}