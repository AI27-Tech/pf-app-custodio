package com.ai27.app.custodio.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.pipeline.ReporteSyncProceso
import com.ai27.app.custodio.data.pipeline.SubidaMedioProceso
import com.ai27.app.custodio.data.remote.MedioRemoto
import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.data.remote.RutasRemotas
import com.ai27.app.custodio.data.remote.RutasStorage
import com.ai27.app.custodio.data.repository.RoomMedioRepository
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decisión de reintentos de [WorkerSubidaMedio], con `work-testing`.
 *
 * Cubre el hueco de `data/worker`, que no tenía **ninguna** prueba (hallazgo P2 y tabla de
 * cobertura de `docs/AUDIT.md` §9). Es la capa que decide si la evidencia se reintenta o
 * se abandona, así que un error aquí se traduce directamente en evidencia que no llega.
 *
 * Desde el hallazgo C5 la decisión es por clasificación y no por tope de intentos:
 * transitorio (`SinConexion`/`ServidorIndisponible`) se reintenta solo sin tope;
 * permanente (permiso denegado, archivo ausente, inesperado) falla de inmediato, persiste
 * la causa y se reporta a Crashlytics.
 *
 * Usa Room **real** sobre un archivo temporal y el almacén de archivos **real**; lo único
 * falseado es la capa remota, que son interfaces. Así la prueba ejercita el `doWork()` de
 * verdad, incluidas las escrituras a la base.
 */
@RunWith(AndroidJUnit4::class)
class WorkerSubidaMedioTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppCustodioDatabase
    private lateinit var archivoDb: File
    private lateinit var directorioMedios: File
    private lateinit var reportes: ReporteRepository
    private lateinit var medios: MedioRepository
    private val instante: Instant = Instant.parse("2026-09-21T15:00:00Z")

    /** Remoto que siempre falla de forma transitoria (sin red). */
    private class RemotoReporteQueFalla : ReporteRemoto {
        override suspend fun obtenerCiclo(reporteId: String): Result<Ciclo?> =
            Result.Failure(DominioError.SinConexion)

        override suspend fun crear(reporte: Reporte): Result<Unit> =
            Result.Failure(DominioError.SinConexion)

        override suspend fun cerrar(reporte: Reporte): Result<Unit> =
            Result.Failure(DominioError.SinConexion)

        override suspend fun descartar(reporte: Reporte): Result<Unit> =
            Result.Failure(DominioError.SinConexion)

        override suspend fun actualizarContadores(
            reporteId: String,
            estado: EstadoSubida,
            mediosSubidos: Int,
            completo: Boolean,
        ): Result<Unit> = Result.Failure(DominioError.SinConexion)
    }

    /** Remoto que deniega por reglas: permanente, reintentar no lo arregla. */
    private class RemotoReporteQueDeniega : ReporteRemoto {
        override suspend fun obtenerCiclo(reporteId: String): Result<Ciclo?> =
            Result.Failure(DominioError.PermisoDenegado)

        override suspend fun crear(reporte: Reporte): Result<Unit> =
            Result.Failure(DominioError.PermisoDenegado)

        override suspend fun cerrar(reporte: Reporte): Result<Unit> =
            Result.Failure(DominioError.PermisoDenegado)

        override suspend fun descartar(reporte: Reporte): Result<Unit> =
            Result.Failure(DominioError.PermisoDenegado)

        override suspend fun actualizarContadores(
            reporteId: String,
            estado: EstadoSubida,
            mediosSubidos: Int,
            completo: Boolean,
        ): Result<Unit> = Result.Failure(DominioError.PermisoDenegado)
    }

    /** Registrar de fallos fake, para capturar qué y con qué contexto se reporta. */
    private class RegistroFake : RegistroDeFallos {
        val reportados: MutableList<Pair<String, Throwable>> = mutableListOf()
        override fun registrar(contexto: String, causa: Throwable) {
            reportados += contexto to causa
        }
    }

    /** Remoto que responde bien: el reporte ya existe y las escrituras pasan. */
    private class RemotoReporteQueFunciona : ReporteRemoto {
        override suspend fun obtenerCiclo(reporteId: String): Result<Ciclo?> =
            Result.Success(Ciclo.ACTIVO)

        override suspend fun crear(reporte: Reporte): Result<Unit> = Result.Success(Unit)
        override suspend fun cerrar(reporte: Reporte): Result<Unit> = Result.Success(Unit)
        override suspend fun descartar(reporte: Reporte): Result<Unit> = Result.Success(Unit)
        override suspend fun actualizarContadores(
            reporteId: String,
            estado: EstadoSubida,
            mediosSubidos: Int,
            completo: Boolean,
        ): Result<Unit> = Result.Success(Unit)
    }

    private class RemotoMedioQueFunciona : MedioRemoto {
        override suspend fun subirArchivo(
            reporte: Reporte,
            medio: Medio,
            contenido: ByteArray,
            miniatura: ByteArray?,
        ): Result<RutasRemotas> = Result.Success(RutasStorage.de(medio))

        override suspend fun crearDocumentoSiNoExiste(
            reporte: Reporte,
            medio: Medio,
            rutas: RutasRemotas,
        ): Result<Unit> = Result.Success(Unit)
    }

    private lateinit var registroDeFallos: RegistroFake

    @Before
    fun preparar() {
        archivoDb = File(context.cacheDir, "worker-${UUID.randomUUID()}.db")
        database = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        directorioMedios = File(context.cacheDir, "worker-medios-${UUID.randomUUID()}").also { it.mkdirs() }
        reportes = RoomReporteRepository(database)
        medios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios))
        registroDeFallos = RegistroFake()
    }

    @After
    fun limpiar() {
        database.close()
        archivoDb.delete()
        directorioMedios.deleteRecursively()
    }

    private fun proceso(
        reporteRemoto: ReporteRemoto,
        medioRemoto: MedioRemoto = RemotoMedioQueFunciona(),
    ) = SubidaMedioProceso(
        reporteRepository = reportes,
        medioRepository = medios,
        reporteRemoto = reporteRemoto,
        medioRemoto = medioRemoto,
        almacenArchivos = FilesDirAlmacenArchivos(directorioMedios),
    )

    private fun worker(proceso: SubidaMedioProceso, reporteId: String, medioId: String, intento: Int) =
        TestListenableWorkerBuilder<WorkerSubidaMedio>(
            context = context,
            inputData = workDataOf(
                SubidaScheduler.REPORTE_ID to reporteId,
                SubidaScheduler.MEDIO_ID to medioId,
            ),
        )
            .setRunAttemptCount(intento)
            .setWorkerFactory(factory(proceso))
            .build()

    // La factory exige los dos procesos; el de sync no se ejercita en esta clase.
    private fun factory(proceso: SubidaMedioProceso) = CustodioWorkerFactory(
        procesoSubida = proceso,
        procesoSync = ReporteSyncProceso(reportes, medios, RemotoReporteQueFunciona()),
        medioRepository = medios,
        registroDeFallos = registroDeFallos,
    )

    private suspend fun sembrarReporteConMedio(): Pair<String, String> {
        val reporte = Reporte(
            id = "reporte-${UUID.randomUUID()}",
            placas = "ABC123",
            placasCapturadas = "abc-123",
            custodioUid = "custodio-1",
            custodioTelefono = "custodio@ai27.test",
            clienteId = "cliente-provisional",
            deviceId = "dispositivo-1",
            fechaLocal = "2026-09-21",
            createdAtDevice = instante,
            ciclo = Ciclo.ACTIVO,
            subida = EstadoSubida.PENDIENTE,
            completo = false,
            mediosEsperados = null,
            mediosSubidos = 0,
            resumenMedios = ResumenMedios(),
        )
        assertTrue(reportes.crear(reporte) is Result.Success)

        val medio = Medio(
            id = "medio-${UUID.randomUUID()}",
            reporteId = reporte.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            createdAtDevice = instante,
            contentType = "image/jpeg",
            estadoSubida = EstadoSubida.PENDIENTE,
            etiquetas = listOf("sin-novedad"),
        )
        val guardado = (medios.guardar(medio, ByteArray(64), ByteArray(16)) as Result.Success).value
        return reporte.id to guardado.id
    }

    @Test
    fun un_fallo_transitorio_pide_retry_sin_tope() = runBlocking {
        // C5: sin clasificar, se reintentaba 4 veces y se rendía. Un `SinConexion` es
        // transitorio: el worker deja que WorkManager reintente con backoff exponencial
        // sin tope (la constraint de red evita el spin) hasta que la evidencia suba.
        val (reporteId, medioId) = sembrarReporteConMedio()
        val proceso = proceso(RemotoReporteQueFalla())

        for (intento in 1..6) {
            val resultado = worker(proceso, reporteId, medioId, intento).doWork()
            assertEquals(
                "El intento $intento debe pedir retry: no hay tope para lo transitorio",
                ListenableWorker.Result.retry(),
                resultado,
            )
        }
    }

    @Test
    fun un_fallo_permanente_termina_de_inmediato_sin_consumir_reintentos() = runBlocking {
        // C5: un PermisoDenegado de las reglas no va a cambiar con más intentos. En el
        // primer intento (runAttemptCount 0) el worker falla de inmediato, persiste la
        // causa y el conteo para diagnóstico, y reporta el fallo a Crashlytics.
        val (reporteId, medioId) = sembrarReporteConMedio()
        val proceso = proceso(RemotoReporteQueDeniega())

        val resultado = worker(proceso, reporteId, medioId, intento = 0).doWork()

        assertEquals(ListenableWorker.Result.failure(), resultado)
        val fallido = medios.obtenerPorId(medioId)!!
        assertEquals("El medio queda FALLIDA para el reintento manual", EstadoSubida.FALLIDA, fallido.estadoSubida)
        assertEquals("La causa queda persistida para diagnóstico", "PermisoDenegado", fallido.ultimoError)
        assertEquals("Este era el primer intento", 1, fallido.intentosSubida)
        assertEquals(1, registroDeFallos.reportados.size)
        assertEquals("subida_medio_permanente", registroDeFallos.reportados.single().first)
    }

    @Test
    fun una_subida_exitosa_marca_el_medio_COMPLETA_y_limpia_el_diagnostico() = runBlocking {
        val (reporteId, medioId) = sembrarReporteConMedio()
        // Una falla previa deja el diagnóstico; la re-subida exitosa debe limpiarlo
        // (el DAO los resetea al marcar COMPLETA).
        assertTrue(medios.registrarFallo(medioId, DominioError.PermisoDenegado, 2) is Result.Success)
        val proceso = proceso(RemotoReporteQueFunciona())

        val resultado = worker(proceso, reporteId, medioId, intento = 1).doWork()

        assertEquals(ListenableWorker.Result.success(), resultado)
        val subido = medios.obtenerPorId(medioId)!!
        assertEquals(EstadoSubida.COMPLETA, subido.estadoSubida)
        assertTrue("Debe quedar el sha256 calculado", subido.sha256!!.isNotBlank())
        assertEquals("El diagnóstico de la falla previa se limpia", null, subido.ultimoError)
        assertEquals(0, subido.intentosSubida)
    }

    @Test
    fun sin_datos_de_entrada_el_worker_falla_sin_reintentar() = runBlocking {
        val proceso = proceso(RemotoReporteQueFunciona())
        val sinDatos = TestListenableWorkerBuilder<WorkerSubidaMedio>(context)
            .setWorkerFactory(factory(proceso))
            .build()

        assertEquals(
            "Sin reporteId/medioId no hay nada que reintentar",
            ListenableWorker.Result.failure(),
            sinDatos.doWork(),
        )
    }

    @Test
    fun un_medio_ya_COMPLETA_no_se_vuelve_a_subir() = runBlocking {
        val (reporteId, medioId) = sembrarReporteConMedio()
        medios.marcarSubidaCompleta(medioId, "medios/$reporteId/$medioId.jpg", "sha-previo")

        // Con el remoto sano, el pipeline solo propaga los contadores del reporte para
        // sanar la divergencia (hallazgo C1); no vuelve a tocar Storage ni a reescribir
        // el documento del medio, así que el sha original se conserva intacto.
        val resultado = worker(proceso(RemotoReporteQueFunciona()), reporteId, medioId, intento = 1).doWork()

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals("sha-previo", medios.obtenerPorId(medioId)!!.sha256)
        assertEquals("medios/$reporteId/$medioId.jpg", medios.obtenerPorId(medioId)!!.storagePath)
    }

    @Test
    fun no_se_sube_evidencia_a_un_reporte_descartado_localmente() = runBlocking {
        val (reporteId, medioId) = sembrarReporteConMedio()
        assertTrue(reportes.descartar(reporteId, instante, "abierto por error") is Result.Success)

        val resultado = worker(proceso(RemotoReporteQueFunciona()), reporteId, medioId, intento = 1).doWork()

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals(
            "El medio no debe subir a un viaje descartado",
            EstadoSubida.PENDIENTE,
            medios.obtenerPorId(medioId)!!.estadoSubida,
        )
    }
}
