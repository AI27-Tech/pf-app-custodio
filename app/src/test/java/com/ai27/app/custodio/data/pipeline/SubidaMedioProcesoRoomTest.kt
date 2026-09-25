package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.FakeEtiquetaRepository
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
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * C0 del PLAN §15: el pipeline de subida corre contra las implementaciones **reales**
 * (Room + filesystem temporal), no contra los fakes. Es la red de regresión que le faltaba
 * a `data/pipeline` y aísla las regresiones de contadores (C1/C3) fuera del emulador.
 */
class SubidaMedioProcesoRoomTest {

    private lateinit var database: EchoDatabase
    private lateinit var archivoDb: File
    private lateinit var directorioMedios: File
    private lateinit var repoReportes: RoomReporteRepository
    private lateinit var repoMedios: RoomMedioRepository
    private lateinit var reporteRemoto: FakeReporteRemoto
    private lateinit var medioRemoto: FakeMedioRemoto
    private lateinit var proceso: SubidaMedioProceso

    private val fijo = Instant.parse("2026-09-18T15:00:00Z")

    @Before
    fun preparar() {
        archivoDb = File.createTempFile("c0-pipeline-", ".db").also { it.delete() }
        database = EchoDatabase.crearBaseDatos(archivoDb.absolutePath)
        directorioMedios = Files.createTempDirectory("c0-pipeline-medios").toFile()
        repoReportes = RoomReporteRepository(database)
        repoMedios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios), FakeEtiquetaRepository())
        reporteRemoto = FakeReporteRemoto()
        medioRemoto = FakeMedioRemoto()
        proceso = SubidaMedioProceso(
            repoReportes,
            repoMedios,
            FakeEtiquetaRepository(),
            reporteRemoto,
            medioRemoto,
            FilesDirAlmacenArchivos(directorioMedios),
        )
    }

    @After
    fun limpiar() {
        database.close()
        archivoDb.delete()
        directorioMedios.deleteRecursively()
    }

    private fun reporte(reporteId: String, mediosEsperados: Int): Reporte = Reporte(
        id = reporteId,
        placas = "ABC-1",
        placasCapturadas = "ABC-1",
        custodioUid = "c-1",
        custodioTelefono = "a@b.cl",
        clienteId = "cli-tmp",
        deviceId = "dev-1",
        fechaLocal = "2026-09-18",
        createdAtDevice = fijo,
        ciclo = Ciclo.ACTIVO,
        subida = EstadoSubida.PENDIENTE,
        completo = false,
        mediosEsperados = mediosEsperados,
        mediosSubidos = 0,
        resumenMedios = ResumenMedios(),
    )

    private suspend fun crearReporteEnRoom(reporteId: String, mediosEsperados: Int) {
        assertTrue("se debe poder crear el reporte", repoReportes.crear(reporte(reporteId, mediosEsperados)) is Result.Success)
    }

    private suspend fun guardarMedio(reporteId: String, idSufijo: String = "1"): Medio {
        val guardado = repoMedios.guardar(
            Medio(
                id = "m-$reporteId-$idSufijo",
                reporteId = reporteId,
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = fijo,
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.PENDIENTE,
                etiquetas = listOf("sin-novedad"),
            ),
            contenido = ByteArray(64),
            miniatura = ByteArray(16),
        )
        return (guardado as Result.Success).value
    }

    @Test
    fun `el pipeline avanza el reporte contra Room real y borra el archivo`() = runBlocking {
        val reporteId = "r-c0-unico"
        crearReporteEnRoom(reporteId, mediosEsperados = 1)
        val medio = guardarMedio(reporteId)
        reporteRemoto.ciclos[reporteId] = Ciclo.ACTIVO

        val resultado = proceso.ejecutar(reporteId, medio.id)

        assertTrue(resultado is Result.Success)
        val reporte = repoReportes.obtenerPorId(reporteId)!!
        assertEquals(EstadoSubida.COMPLETA, reporte.subida)
        assertEquals(1, reporte.mediosSubidos)
        assertTrue(reporte.completo)
        val subido = repoMedios.obtenerPorId(medio.id)!!
        assertEquals(EstadoSubida.COMPLETA, subido.estadoSubida)
        assertTrue(subido.sha256!!.isNotBlank())
        // El archivo grande se borró del filesystem real; la miniatura sobrevive.
        assertFalse("el archivo grande se borra", File(requireNotNull(subido.localPath)).exists())
        assertTrue("la miniatura se conserva para la rejilla", File(requireNotNull(subido.thumbnailPath)).exists())
        // C2: el documento remoto se construyó con el sha (el pipeline pasa la copia con hash).
        assertEquals(subido.sha256, medioRemoto.mediosDeDocumento[0].sha256)
        assertEquals(1, reporteRemoto.actualizacionesContadores)
    }

    @Test
    fun `con dos medios el contador final no se infla ni se pierde`() = runBlocking {
        val reporteId = "r-c0-par"
        crearReporteEnRoom(reporteId, mediosEsperados = 2)
        val medioA = guardarMedio(reporteId, "a")
        val medioB = guardarMedio(reporteId, "b")
        reporteRemoto.ciclos[reporteId] = Ciclo.ACTIVO

        assertTrue(proceso.ejecutar(reporteId, medioA.id) is Result.Success)
        assertTrue(proceso.ejecutar(reporteId, medioB.id) is Result.Success)

        val reporte = repoReportes.obtenerPorId(reporteId)!!
        assertEquals("cada medio suma exactamente uno", 2, reporte.mediosSubidos)
        assertEquals(EstadoSubida.COMPLETA, reporte.subida)
        assertTrue(reporte.completo)
        assertEquals(2, reporteRemoto.actualizacionesContadores)

        // Con la subida terminada y sin divergencia, la reconciliación ya no lo devuelve.
        assertFalse(repoReportes.obtenerPendientesSubida("c-1", "2026-01-01").any { it.id == reporteId })
    }
}