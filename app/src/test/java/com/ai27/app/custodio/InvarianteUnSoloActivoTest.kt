package com.ai27.app.custodio

import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.local.db.ReporteDao
import com.ai27.app.custodio.data.mapper.aEntidad
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.usecase.CrearReporte
import java.io.File
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Aceptación B2: un solo reporte ACTIVO por custodio, garantizado por el índice
 * único parcial creado en el callback de la base de datos.
 */
class InvarianteUnSoloActivoTest {

    private lateinit var database: AppCustodioDatabase
    private lateinit var archivo: File
    private lateinit var dao: ReporteDao
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")
    private val custodio = Custodio(uid = "c-1", telefono = "custodio@test.mx")

    @Before
    fun setUp() {
        archivo = File.createTempFile("b2-invariante-", ".db").also { it.delete() }
        database = AppCustodioDatabase.crearBaseDatos(archivo.absolutePath)
        dao = database.reporteDao()
    }

    @After
    fun tearDown() {
        database.close()
        archivo.delete()
    }

    private fun reporteActivo(custodioUid: String, id: String, placas: String): Reporte = Reporte(
        id = id,
        placas = placas,
        placasCapturadas = placas,
        custodioUid = custodioUid,
        custodioTelefono = "correo@test.mx",
        clienteId = "cliente-1",
        deviceId = "device-1",
        fechaLocal = "2026-09-18",
        createdAtDevice = fijo,
        ciclo = Ciclo.ACTIVO,
        subida = EstadoSubida.PENDIENTE,
        completo = false,
        mediosEsperados = null,
        mediosSubidos = 0,
        resumenMedios = ResumenMedios(),
    )

    @Test
    fun `el dao descarta el segundo activo del mismo custodio`() = runBlocking {
        val primero = dao.insertar(reporteActivo("c-1", "r-1", "AAA1111").aEntidad())
        val segundo = dao.insertar(reporteActivo("c-1", "r-2", "BBB2222").aEntidad())

        assertEquals(1L, primero)
        assertEquals(-1L, segundo)

        val enBase = dao.obtenerActivo("c-1")!!
        assertEquals("r-1", enBase.id)

        val deOtro = dao.insertar(reporteActivo("c-99", "r-3", "CCC3333").aEntidad())
        assertNotEquals(-1L, deOtro)
    }

    @Test
    fun `la carrera de dos reportes activos deja exactamente uno`() = runBlocking {
        val repo = RoomReporteRepository(database)
        val crear = CrearReporte(repo, ahora = { fijo })

        val (a, b) = coroutineScope {
            val primera = async {
                crear(custodio, "d1", "c1", "AAA-1111")
            }
            val segunda = async {
                crear(custodio, "d1", "c1", "BBB-2222")
            }
            primera.await() to segunda.await()
        }

        val exitos = listOf(a, b).count { it is Result.Success }
        assertEquals(1, exitos)
        val fracaso = (listOf(a, b).filterIsInstance<Result.Failure>().single().error)
        assertTrue(fracaso is DominioError.YaExisteReporteActivo)

        val activos = checkNotNull(dao.obtenerActivo("c-1"))
        assertEquals("c-1", activos.custodioUid)
        assertEquals("ACTIVO", activos.ciclo)
    }
}