package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReporteSyncProcesoTest {

    private val fijo = Instant.parse("2026-09-18T15:00:00Z")

    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var remoto: FakeReporteRemoto
    private lateinit var proceso: ReporteSyncProceso

    @Before
    fun setUp() {
        repoReportes = FakeReporteRepository()
        repoMedios = FakeMedioRepository()
        remoto = FakeReporteRemoto()
        proceso = ReporteSyncProceso(repoReportes, repoMedios, remoto)
    }

    private fun crearReporteLocal(ciclo: Ciclo): Reporte {
        val reporte = Reporte(
            id = "r-1",
            placas = "ABC-123",
            placasCapturadas = "ABC-123",
            custodioUid = "c-1",
            custodioCorreo = "a@b.cl",
            clienteId = "cli-tmp",
            deviceId = "dev-1",
            fechaLocal = "2026-09-18",
            createdAtDevice = fijo,
            ciclo = ciclo,
            subida = EstadoSubida.PENDIENTE,
            completo = false,
            mediosEsperados = 1,
            mediosSubidos = 0,
            resumenMedios = ResumenMedios(),
            closedAt = if (ciclo == Ciclo.CERRADO) fijo else null,
        )
        repoReportes.reportes.add(reporte)
        return reporte
    }

    private fun crearMedioCompleto() = runBlocking {
        repoMedios.guardar(
            com.ai27.app.custodio.domain.model.Medio(
                id = "m-1",
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = fijo,
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.COMPLETA,
                storagePath = "medios/r-1/m-1.jpg",
            ),
            ByteArray(4),
        )
    }

    @Test
    fun `hace nada cuando reporte y servidor estan activos`() = runBlocking {
        crearReporteLocal(Ciclo.ACTIVO)
        remoto.ciclos["r-1"] = Ciclo.ACTIVO

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(0, remoto.creaciones)
        assertEquals(0, remoto.cierres)
        assertEquals(0, remoto.actualizacionesContadores)
    }

    @Test
    fun `crea el reporte si no existe en el servidor`() = runBlocking {
        crearReporteLocal(Ciclo.ACTIVO)

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(1, remoto.creaciones)
        assertEquals(Ciclo.ACTIVO, remoto.ciclos["r-1"])
    }

    @Test
    fun `crea y luego cierra cuando el reporte se cerro offline`() = runBlocking {
        crearReporteLocal(Ciclo.CERRADO)
        crearMedioCompleto()

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(1, remoto.creaciones)
        assertEquals(1, remoto.cierres)
        assertEquals(Ciclo.CERRADO, remoto.ciclos["r-1"])
        // El cierre lleva los contadores calculados.
        val local = repoReportes.obtenerPorId("r-1")!!
        assertEquals(EstadoSubida.COMPLETA, local.subida)
        assertEquals(1, local.mediosSubidos)
        assertTrue(local.completo)
    }

    @Test
    fun `cierra un reporte ya creado en el servidor`() = runBlocking {
        crearReporteLocal(Ciclo.CERRADO)
        remoto.ciclos["r-1"] = Ciclo.ACTIVO

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(0, remoto.creaciones)
        assertEquals(1, remoto.cierres)
    }

    @Test
    fun `descarta un reporte creado en el servidor`() = runBlocking {
        crearReporteLocal(Ciclo.DESCARTADO)
        remoto.ciclos["r-1"] = Ciclo.ACTIVO

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(0, remoto.creaciones)
        assertEquals(1, remoto.descartes)
    }

    @Test
    fun `crea y descarta cuando el descarte fue offline`() = runBlocking {
        crearReporteLocal(Ciclo.DESCARTADO)

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(1, remoto.creaciones)
        assertEquals(1, remoto.descartes)
        assertEquals(Ciclo.DESCARTADO, remoto.ciclos["r-1"])
    }

    @Test
    fun `en reporte cerrado remoto solo avanza contadores`() = runBlocking {
        crearReporteLocal(Ciclo.CERRADO)
        crearMedioCompleto()
        remoto.ciclos["r-1"] = Ciclo.CERRADO

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(0, remoto.creaciones)
        assertEquals(0, remoto.cierres)
        assertEquals(1, remoto.actualizacionesContadores)
        assertEquals(EstadoSubida.COMPLETA, repoReportes.obtenerPorId("r-1")!!.subida)
    }

    @Test
    fun `no toca un reporte activo local con servidor cerrado`() = runBlocking {
        crearReporteLocal(Ciclo.ACTIVO)
        remoto.ciclos["r-1"] = Ciclo.CERRADO

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(0, remoto.creaciones)
        assertEquals(0, remoto.cierres)
        assertEquals(0, remoto.actualizacionesContadores)
    }

    @Test
    fun `cierra en el servidor cuando los medios ya estan completos (regresion)`() = runBlocking {
        // Regresión del bug de cierre atascado: con el doc remoto ACTIVO y los medios ya
        // COMPLETA, el orden viejo mandaba actualizarContadores(completo=true) sobre el doc
        // aún ACTIVO -> la regla lo rechazaba (PERMISSION_DENIED) y se retornaba antes de
        // cerrar (cierres==0, ciclo seguía ACTIVO). Con el fix el cierre va directo.
        crearReporteLocal(Ciclo.CERRADO)
        crearMedioCompleto()
        remoto.ciclos["r-1"] = Ciclo.ACTIVO

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Success)
        assertEquals(1, remoto.cierres)
        assertEquals(Ciclo.CERRADO, remoto.ciclos["r-1"])
    }

    @Test
    fun `propaga fallo al consultar el servidor`() = runBlocking {
        crearReporteLocal(Ciclo.ACTIVO)
        remoto.debeFallarObtencion = true

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.SinConexion, (resultado as Result.Failure).error)
    }

    @Test
    fun `propaga fallo de escritura al cerrar`() = runBlocking {
        crearReporteLocal(Ciclo.CERRADO)
        remoto.ciclos["r-1"] = Ciclo.ACTIVO
        remoto.debeFallarEscritura = true

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Failure)
    }

    @Test
    fun `no adelanta Room cuando la escritura remota de contadores falla`() = runBlocking {
        // Hallazgo C1: el orden era local-primero y una escritura remota fallida dejaba a
        // Room adelantado (subida COMPLETA) sin el respaldo en Firestore. Ahora el remoto
        // gana primero y el fallo propaga retry sin tocar el estado local.
        crearReporteLocal(Ciclo.CERRADO)
        crearMedioCompleto()
        remoto.ciclos["r-1"] = Ciclo.CERRADO
        remoto.debeFallarEscritura = true

        val resultado = proceso.ejecutar("r-1")

        assertTrue(resultado is Result.Failure)
        val local = repoReportes.obtenerPorId("r-1")!!
        assertEquals(EstadoSubida.PENDIENTE, local.subida)
        assertEquals(0, local.mediosSubidos)
        assertFalse(local.completo)
    }
}