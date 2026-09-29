package com.ai27.app.custodio.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.EchoDatabase
import com.ai27.app.custodio.data.repository.RoomMedioRepository
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.Result
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comportamiento de [WorkManagerSubidaScheduler] y de `reconciliar()`, la rutina que
 * repone la cola al volver a abrir la app. Era código sin ninguna prueba.
 *
 * Una de estas pruebas es de **caracterización**: fija comportamiento que la auditoría
 * marcó como defectuoso pero que queda **fuera del alcance** acordado (§12.5 de
 * `docs/PLAN.md`). Se escribe afirmando lo que la app hace **hoy**, con el ID del
 * hallazgo en el nombre y en un comentario. No lleva `@Ignore` a propósito: una prueba
 * ignorada no protege nada, y así el día que se arregle el defecto la prueba falla y
 * obliga a actualizarla de forma consciente.
 *
 * Las caracterizaciones de E3 y E6 se convirtieron en aserciones normales al cerrarse esos
 * hallazgos en §13: hoy afirman el comportamiento correcto, no el defectuoso.
 */
@RunWith(AndroidJUnit4::class)
class ReconciliacionColaTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: EchoDatabase
    private lateinit var archivoDb: File
    private lateinit var directorioMedios: File
    private lateinit var reportes: ReporteRepository
    private lateinit var medios: MedioRepository
    private lateinit var scheduler: WorkManagerSubidaScheduler
    private lateinit var workManager: WorkManager
    private val instante: Instant = Instant.parse("2026-09-21T15:00:00Z")

    @Before
    fun preparar() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)

        archivoDb = File(context.cacheDir, "recon-${UUID.randomUUID()}.db")
        database = EchoDatabase.crearBaseDatos(archivoDb.absolutePath)
        directorioMedios = File(context.cacheDir, "recon-medios-${UUID.randomUUID()}").also { it.mkdirs() }
        reportes = RoomReporteRepository(database)
        medios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios), EtiquetasFijas())
        scheduler = WorkManagerSubidaScheduler(context, reportes, medios)
    }

    @After
    fun limpiar() {
        workManager.cancelAllWork()
        database.close()
        archivoDb.delete()
        directorioMedios.deleteRecursively()
    }

    private suspend fun crearReporte(custodioUid: String = "custodio-1"): Reporte {
        val reporte = Reporte(
            id = "reporte-${UUID.randomUUID()}",
            placas = "ABC123",
            placasCapturadas = "abc-123",
            custodioUid = custodioUid,
            custodioCorreo = "custodio@ai27.test",
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
        return reporte
    }

    private suspend fun crearMedio(reporteId: String, etiquetas: List<String> = listOf("sin-novedad")): Medio {
        val medio = Medio(
            id = "medio-${UUID.randomUUID()}",
            reporteId = reporteId,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            createdAtDevice = instante,
            contentType = "image/jpeg",
            estadoSubida = EstadoSubida.PENDIENTE,
            etiquetas = etiquetas,
        )
        return (medios.guardar(medio, ByteArray(64), ByteArray(16)) as Result.Success).value
    }

    private fun trabajosDeMedio(medioId: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(SubidaScheduler.NOMBRE_SUBIDA + medioId).get()

    private fun trabajosDeSync(reporteId: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(SubidaScheduler.NOMBRE_SYNC + reporteId).get()

    @Test
    fun encolarSubidaMedio_crea_un_trabajo_unico_por_medio() = runBlocking {
        val reporte = crearReporte()
        val medio = crearMedio(reporte.id)

        scheduler.encolarSubidaMedio(reporte.id, medio.id)

        val trabajos = trabajosDeMedio(medio.id)
        assertEquals("Debe haber exactamente un trabajo para ese medio", 1, trabajos.size)
    }

    @Test
    fun reconciliar_repone_la_cola_de_los_medios_pendientes() = runBlocking {
        val reporte = crearReporte()
        val unMedio = crearMedio(reporte.id)
        val otroMedio = crearMedio(reporte.id)

        scheduler.reconciliar(reporte.custodioUid)

        assertEquals(1, trabajosDeMedio(unMedio.id).size)
        assertEquals(1, trabajosDeMedio(otroMedio.id).size)
        assertEquals("También se encola el sync del reporte", 1, trabajosDeSync(reporte.id).size)
    }

    @Test
    fun reconciliar_reintenta_los_medios_FALLIDA() = runBlocking {
        val reporte = crearReporte()
        val medio = crearMedio(reporte.id)
        medios.actualizarEstadoSubida(medio.id, EstadoSubida.FALLIDA)

        scheduler.reconciliar(reporte.custodioUid)

        assertEquals(
            "Un medio FALLIDA debe volver a la cola al reabrir la app; si no, la evidencia se queda ahí",
            1,
            trabajosDeMedio(medio.id).size,
        )
    }

    @Test
    fun reconciliar_rescata_los_medios_pegados_en_EN_PROGRESO() = runBlocking {
        // Si el proceso muere a mitad de una subida, el medio queda EN_PROGRESO. No se
        // puede reintentar a mano (ReintentarSubida lo rechaza), así que la única salida
        // es que la reconciliación lo vuelva a encolar.
        val reporte = crearReporte()
        val medio = crearMedio(reporte.id)
        medios.actualizarEstadoSubida(medio.id, EstadoSubida.EN_PROGRESO)

        scheduler.reconciliar(reporte.custodioUid)

        assertEquals(1, trabajosDeMedio(medio.id).size)
    }

    @Test
    fun reconciliar_no_encola_los_medios_ya_COMPLETA() = runBlocking {
        val reporte = crearReporte()
        val medio = crearMedio(reporte.id)
        medios.marcarSubidaCompleta(medio.id, "medios/${reporte.id}/${medio.id}.jpg", "sha-1")

        scheduler.reconciliar(reporte.custodioUid)

        assertTrue("Un medio ya subido no se vuelve a encolar", trabajosDeMedio(medio.id).isEmpty())
    }

    @Test
    fun reconciliar_no_encola_los_medios_SIN_situacion() = runBlocking {
        // Hallazgo E3, cerrado en §13 y endurecido en §14 (decisión A: al menos una
        // etiqueta de Situación).
        //
        // La hoja de clasificación es una trampa deliberada en la UI: no se puede cerrar
        // sin elegir una Situación. Pero si el custodio la descarta con la X, o si el
        // proceso muere con la hoja abierta, el medio queda persistido sin situación.
        // Antes, reconciliar() encolaba TODO lo que no estuviera COMPLETA, así que al
        // siguiente arranque ese medio subía sin clasificar y el analista recibía evidencia
        // que no podía filtrar.
        //
        // Ahora la cola filtra por Situación. El medio NO se pierde: sigue en Room, cuenta
        // en el badge de pendientes y la rejilla de Archivos lo marca como pendiente de
        // clasificar para que el custodio lo resuelva con un toque.
        val reporte = crearReporte()
        val sinSituacion = crearMedio(reporte.id, etiquetas = emptyList())
        val soloEvidencia = crearMedio(reporte.id, etiquetas = listOf("estado-transporte"))
        val conSituacion = crearMedio(reporte.id)

        scheduler.reconciliar(reporte.custodioUid)

        assertTrue(
            "Un medio sin Situación no debe encolarse: subiría evidencia sin clasificar",
            trabajosDeMedio(sinSituacion.id).isEmpty(),
        )
        assertTrue(
            "Un medio solo con Evidencia tampoco: la Situación es obligatoria",
            trabajosDeMedio(soloEvidencia.id).isEmpty(),
        )
        assertEquals(
            "El resto del reporte sí se encola: un medio sin clasificar no bloquea a los demás",
            1,
            trabajosDeMedio(conSituacion.id).size,
        )
    }

    @Test
    fun cancelarTodo_cancela_los_trabajos_de_subida_encolados() = runBlocking {
        // Hallazgo E6: al cerrar sesión hay que parar el trabajo en vuelo antes del
        // signOut, porque un worker con el token del custodio saliente ve sus escrituras
        // denegadas por las reglas y deja la fila en FALLIDA.
        val reporte = crearReporte()
        val medio = crearMedio(reporte.id)
        scheduler.encolarSubidaMedio(reporte.id, medio.id)
        scheduler.sincronizarReporte(reporte.id)

        scheduler.cancelarTodo()

        assertTrue(
            "el trabajo del medio debe quedar cancelado",
            trabajosDeMedio(medio.id).all { it.state == WorkInfo.State.CANCELLED },
        )
        assertTrue(
            "y también el del reporte",
            trabajosDeSync(reporte.id).all { it.state == WorkInfo.State.CANCELLED },
        )
    }

    @Test
    fun reconciliar_rescata_lo_que_cancelo_el_cierre_de_sesion() = runBlocking {
        // La contrapartida de cancelar: nada se pierde. Un medio que la cancelación dejó
        // sin subir vuelve a la cola cuando ese custodio reabre la app.
        val reporte = crearReporte()
        val medio = crearMedio(reporte.id)
        scheduler.encolarSubidaMedio(reporte.id, medio.id)
        scheduler.cancelarTodo()

        scheduler.reconciliar(reporte.custodioUid)

        assertTrue(
            "tras reconciliar debe haber trabajo vivo otra vez",
            trabajosDeMedio(medio.id).any { it.state != WorkInfo.State.CANCELLED },
        )
    }

    @Test
    fun C4_encolar_de_nuevo_NO_reemplaza_el_trabajo_en_vuelo() = runBlocking {
        // Hallazgo C4, cerrado en §15.
        //
        // El scheduler usa ExistingWorkPolicy.KEEP en subida y sync, así que volver a
        // encolar el mismo medio no cancela la subida en vuelo. `reconciliar()`, el
        // pull-to-refresh de Pendientes y el reintento manual dejan de reiniciar desde el
        // byte cero una subida que ya iba por 26-50 MB (putBytes no es reanudable). KEEP
        // sustituye además al guard explícito "no re-encolar EN_PROGRESO con worker vivo":
        // comprobar primero con getWorkInfosForUniqueWork sería una carrera check-then-act;
        // KEEP decide atómicamente dentro de WorkManager.
        val reporte = crearReporte()
        val medio = crearMedio(reporte.id)

        scheduler.encolarSubidaMedio(reporte.id, medio.id)
        val primeroId = trabajosDeMedio(medio.id).single().id

        scheduler.encolarSubidaMedio(reporte.id, medio.id)

        val trabajos = trabajosDeMedio(medio.id)
        assertFalse(
            "re-encolar no debe cancelar el trabajo anterior",
            trabajos.any { it.state == WorkInfo.State.CANCELLED },
        )
        assertEquals(
            "sigue habiendo exactamente un trabajo y es el original",
            1,
            trabajos.size,
        )
        assertEquals(primeroId, trabajos.single().id)
    }

    @Test
    fun reconciliar_solo_encola_evidencia_del_custodio_pedido() = runBlocking {
        // Hallazgo E6, cerrado en §13.
        //
        // reconciliar() usaba obtenerPendientes() y obtenerPendientesSubida() sin filtrar
        // por custodioUid. En un equipo que rotan 37 personas, si el custodio A dejaba
        // pendientes y B iniciaba sesión, la app reencolaba la evidencia de A con el token
        // de B: las reglas la denegaban y quedaba FALLIDA para siempre, invisible para B
        // porque su badge filtra por uid.
        //
        // Ahora la reconciliación recibe el uid de la sesión y las consultas lo acotan con
        // un JOIN contra reportes.
        val reporteDeA = crearReporte(custodioUid = "custodio-A")
        val medioDeA = crearMedio(reporteDeA.id)
        val reporteDeB = crearReporte(custodioUid = "custodio-B")
        val medioDeB = crearMedio(reporteDeB.id)

        scheduler.reconciliar("custodio-B")

        assertEquals(
            "La evidencia del custodio de la sesión sí se encola",
            1,
            trabajosDeMedio(medioDeB.id).size,
        )
        assertEquals("También su reporte", 1, trabajosDeSync(reporteDeB.id).size)
        assertTrue(
            "La evidencia de otro custodio no se encola: subiría con el token equivocado",
            trabajosDeMedio(medioDeA.id).isEmpty(),
        )
        assertTrue(
            "Tampoco el reporte del otro custodio",
            trabajosDeSync(reporteDeA.id).isEmpty(),
        )
    }
}
