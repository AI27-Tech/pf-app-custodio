package com.ai27.app.custodio

import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.repository.RoomMedioRepository
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CerrarReporte
import com.ai27.app.custodio.domain.usecase.CrearReporte
import com.ai27.app.custodio.domain.usecase.DescartarReporte
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomReporteRepositoryTest {

    private lateinit var database: AppCustodioDatabase
    private lateinit var archivoDb: File
    private lateinit var directorioMedios: File
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")
    private val custodio = Custodio("c-1", "+525544880512")

    @Before
    fun setUp() {
        archivoDb = File.createTempFile("b6-reportes-", ".db").also { it.delete() }
        database = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        directorioMedios = Files.createTempDirectory("b6-reportes-dir").toFile()
    }

    @After
    fun tearDown() {
        database.close()
        archivoDb.delete()
        directorioMedios.deleteRecursively()
    }

    @Test
    fun `el historial incluye cerrados y descartados y excluye el activo`() = runBlocking {
        var reloj = fijo
        val repoReportes = RoomReporteRepository(database)
        val repoMedios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios), FakeEtiquetaRepository())
        val crear = CrearReporte(repoReportes, ahora = { reloj })
        val capturar = CapturarMedio(repoReportes, repoMedios, ahora = { reloj })
        val cerrar = CerrarReporte(repoReportes, repoMedios, ahora = { reloj })
        val descartar = DescartarReporte(repoReportes, ahora = { reloj })

        val cerrado = (crear(custodio, "d1", "c1", "AAA-1111") as Result.Success).value
        capturar("c-1", cerrado.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
        cerrar(cerrado.id)
        reloj = reloj.plusSeconds(3600)

        val descartado = (crear(custodio, "d1", "c1", "BBB-2222") as Result.Success).value
        capturar("c-1", descartado.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
        descartar(descartado.id, "placa mal leida")
        reloj = reloj.plusSeconds(3600)

        crear(custodio, "d1", "c1", "CCC-3333")

        val historial = repoReportes.observarHistorial(custodio.uid, "2026-09-18").first()

        assertEquals(
            listOf("BBB2222", "AAA1111"),
            historial.map { it.placas },
        )
        assertEquals(
            mapOf("BBB2222" to Ciclo.DESCARTADO, "AAA1111" to Ciclo.CERRADO),
            historial.associate { it.placas to it.ciclo },
        )
        assertFalse(historial.any { it.placas == "CCC3333" })
        assertEquals("placa mal leida", historial.first { it.id == descartado.id }.motivoDescarte)
    }

    @Test
    fun `observarPorId emite el reporte en los distintos ciclos`() = runBlocking {
        val repoReportes = RoomReporteRepository(database)
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val descartar = DescartarReporte(repoReportes, ahora = { fijo })

        val reporte = (crear(custodio, "d1", "c1", "AAA-1111") as Result.Success).value

        assertEquals(reporte.id, repoReportes.observarPorId(reporte.id).first()?.id)
        assertEquals(Ciclo.ACTIVO, repoReportes.observarPorId(reporte.id).first()?.ciclo)

        descartar(reporte.id, "motivo")

        val observado = repoReportes.observarPorId(reporte.id).first()
        assertEquals(Ciclo.DESCARTADO, observado?.ciclo)
        assertTrue(observado!!.motivoDescarte == "motivo")
    }
}