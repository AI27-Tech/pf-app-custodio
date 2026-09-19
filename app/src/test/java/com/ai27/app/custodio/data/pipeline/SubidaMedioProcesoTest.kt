package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.data.remote.MedioRemoto
import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubidaMedioProcesoTest {

    private val fijo = Instant.parse("2026-09-18T15:00:00Z")

    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var reporteRemoto: FakeReporteRemoto
    private lateinit var medioRemoto: FakeMedioRemoto
    private lateinit var archivos: FakeAlmacenArchivos
    private lateinit var proceso: SubidaMedioProceso
    private lateinit var reporte: Reporte
    private lateinit var medio: Medio

    @Before
    fun setUp() = runBlocking {
        val almacen = mutableMapOf<String, ByteArray>()
        repoMedios = FakeMedioRepository(archivosGuardados = almacen)
        repoReportes = FakeReporteRepository()
        reporteRemoto = FakeReporteRemoto(mutableMapOf("r-1" to Ciclo.ACTIVO))
        medioRemoto = FakeMedioRemoto()
        archivos = FakeAlmacenArchivos(almacen)
        proceso = SubidaMedioProceso(
            reporteRepository = repoReportes,
            medioRepository = repoMedios,
            reporteRemoto = reporteRemoto,
            medioRemoto = medioRemoto,
            almacenArchivos = archivos,
        )
        reporte = Reporte(
            id = "r-1",
            placas = "ABC-123",
            placasCapturadas = "ABC-123",
            custodioUid = "c-1",
            custodioCorreo = "a@b.cl",
            clienteId = "cli-tmp",
            deviceId = "dev-1",
            fechaLocal = "2026-09-18",
            createdAtDevice = fijo,
            ciclo = Ciclo.ACTIVO,
            subida = EstadoSubida.PENDIENTE,
            completo = false,
            mediosEsperados = 1,
            mediosSubidos = 0,
            resumenMedios = ResumenMedios(),
        )
        repoReportes.reportes.add(reporte)
        medio = (repoMedios.guardar(
            Medio(
                id = "m-1",
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = fijo,
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.PENDIENTE,
            ),
            contenido = ByteArray(10),
            miniatura = ByteArray(4),
        ) as Result.Success).value
    }

    @Test
    fun `sube un medio y lo marca completo borrando el archivo local`() = runBlocking {
        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Success)
        val subido = repoMedios.obtenerPorId(medio.id)!!
        assertEquals(EstadoSubida.COMPLETA, subido.estadoSubida)
        assertNull(subido.localPath)
        assertEquals("medios/r-1/m-1.jpg", subido.storagePath)
        assertTrue(subido.sha256!!.isNotBlank())
        // El archivo grande se borra; la miniatura local se conserva para la rejilla.
        assertNull(archivos.archivos["fisico/m-1"])
        assertTrue(archivos.archivos.containsKey("fisico/m-1-min"))
        assertEquals(1, medioRemoto.subidos.size)
        assertEquals(1, medioRemoto.docsCreados.size)
    }

    @Test
    fun `no recrea el reporte si ya esta activo en el servidor`() = runBlocking {
        proceso.ejecutar("r-1", medio.id)

        assertEquals(0, reporteRemoto.creaciones)
        assertEquals(EstadoSubida.COMPLETA, repoMedios.obtenerPorId(medio.id)!!.estadoSubida)
    }

    @Test
    fun `crea el reporte si no existe en el servidor`() = runBlocking {
        reporteRemoto.ciclos["r-1"] = null

        proceso.ejecutar("r-1", medio.id)

        assertEquals(1, reporteRemoto.creaciones)
        assertEquals(EstadoSubida.COMPLETA, repoMedios.obtenerPorId(medio.id)!!.estadoSubida)
    }

    @Test
    fun `no sube evidencia a un reporte descartado en el servidor`() = runBlocking {
        reporteRemoto.ciclos["r-1"] = Ciclo.DESCARTADO

        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Success)
        assertEquals(0, medioRemoto.subidos.size)
        assertEquals(EstadoSubida.PENDIENTE, repoMedios.obtenerPorId(medio.id)!!.estadoSubida)
        // El contador no avanza en descartados.
        assertEquals(0, reporteRemoto.actualizacionesContadores)
    }

    @Test
    fun `no sube evidencia a un reporte descartado localmente`() = runBlocking {
        repoReportes.reportes[0] = repoReportes.reportes[0].copy(ciclo = Ciclo.DESCARTADO)

        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Success)
        assertEquals(0, medioRemoto.subidos.size)
        assertEquals(0, medioRemoto.docsCreados.size)
        assertEquals(0, reporteRemoto.creaciones)
        assertEquals(0, reporteRemoto.actualizacionesContadores)
        assertEquals(EstadoSubida.PENDIENTE, repoMedios.obtenerPorId(medio.id)!!.estadoSubida)
    }

    @Test
    fun `marca fallida y no avanza contadores cuando falla la subida`() = runBlocking {
        medioRemoto.debeFallarSubida = true

        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.SinConexion, (resultado as Result.Failure).error)
        assertEquals(EstadoSubida.FALLIDA, repoMedios.obtenerPorId(medio.id)!!.estadoSubida)
        assertEquals(0, reporteRemoto.actualizacionesContadores)
    }

    @Test
    fun `marca fallida cuando el archivo local no existe`() = runBlocking {
        archivos.archivos.remove("fisico/m-1")

        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Failure)
        assertEquals(EstadoSubida.FALLIDA, repoMedios.obtenerPorId(medio.id)!!.estadoSubida)
    }

    @Test
    fun `avanza contadores al subir todos los medios esperados`() = runBlocking {
        reporteRemoto.ciclos["r-1"] = null

        proceso.ejecutar("r-1", medio.id)

        val actualizado = repoReportes.obtenerPorId("r-1")!!
        assertEquals(EstadoSubida.COMPLETA, actualizado.subida)
        assertEquals(1, actualizado.mediosSubidos)
        assertTrue(actualizado.completo)
        assertEquals(1, reporteRemoto.actualizacionesContadores)
    }

    @Test
    fun `un medio ya completo no se vuelve a subir`() = runBlocking {
        repoMedios.actualizarEstadoSubida(medio.id, EstadoSubida.COMPLETA)

        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Success)
        assertEquals(0, medioRemoto.subidos.size)
    }

    @Test
    fun `es idempotente ante reintentos repetidos`() = runBlocking {
        repeat(3) { assertTrue(proceso.ejecutar("r-1", medio.id) is Result.Success) }

        assertEquals(1, medioRemoto.subidos.size)
        assertEquals(1, medioRemoto.docsCreados.size)
        assertEquals(EstadoSubida.COMPLETA, repoMedios.obtenerPorId(medio.id)!!.estadoSubida)
        assertFalse(repoMedios.medios.any { it.estadoSubida == EstadoSubida.EN_PROGRESO })
    }
}