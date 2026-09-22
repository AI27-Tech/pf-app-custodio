package com.ai27.app.custodio

import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.pipeline.FakeAlmacenArchivos
import com.ai27.app.custodio.data.repository.RoomMedioRepository
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CrearReporte
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomMedioRepositoryTest {

    private lateinit var database: AppCustodioDatabase
    private lateinit var archivoDb: File
    private lateinit var directorioMedios: File
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")

    @Before
    fun setUp() {
        archivoDb = File.createTempFile("b3-medios-", ".db").also { it.delete() }
        database = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        directorioMedios = Files.createTempDirectory("b3-medios-dir").toFile()
    }

    @After
    fun tearDown() {
        database.close()
        archivoDb.delete()
        directorioMedios.deleteRecursively()
    }

    private fun medio(reporteId: String, tipo: TipoMedio = TipoMedio.FOTO): Medio = Medio(
        id = UUID.randomUUID().toString(),
        reporteId = reporteId,
        tipo = tipo,
        origen = if (tipo == TipoMedio.FOTO) OrigenMedio.CAMARA else OrigenMedio.GRABADORA,
        createdAtDevice = fijo,
        contentType = if (tipo == TipoMedio.FOTO) "image/jpeg" else "audio/mp4",
        estadoSubida = EstadoSubida.PENDIENTE,
    )

    @Test
    fun `guardar con miniatura escribe original y miniatura y borrar pendiente los elimina`() = runBlocking {
        val repoReportes = RoomReporteRepository(database)
        val repoMedios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios))
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val reporte = (crear(custodio = com.ai27.app.custodio.domain.model.Custodio("c-1", "c@t.mx"), deviceId = "d1", clienteId = "c1", placasCapturadas = "ABC-1234") as Result.Success).value

        val capturar = CapturarMedio(repoReportes, repoMedios, ahora = { fijo })
        val resultado = capturar(
            custodioUid = "c-1",
            reporteId = reporte.id,
            tipo = TipoMedio.FOTO,
            origen = OrigenMedio.CAMARA,
            contenido = ByteArray(2048),
            miniatura = ByteArray(512),
        )
        val guardado = (resultado as Result.Success).value

        assertTrue(File(guardado.localPath!!).exists())
        assertTrue(File(guardado.thumbnailPath!!).exists())
        assertEquals(2, directorioMedios.listFiles()!!.size)

        val borrado = repoMedios.borrarPendiente(guardado.id)
        assertTrue(borrado is Result.Success)
        assertFalse(File(guardado.localPath).exists())
        assertFalse(File(guardado.thumbnailPath).exists())
        assertEquals(0, directorioMedios.listFiles()!!.size)
    }

    @Test
    fun `etiquetar actualiza etiqueta y ubicacionTexto solo cuando se pasa`() = runBlocking {
        val repoReportes = RoomReporteRepository(database)
        val repoMedios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios))
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val reporte = (crear(custodio = com.ai27.app.custodio.domain.model.Custodio("c-1", "c@t.mx"), deviceId = "d1", clienteId = "c1", placasCapturadas = "ABC-1234") as Result.Success).value
        val guardado = (repoMedios.guardar(medio(reporte.id), ByteArray(10)) as Result.Success).value

        val conUbicacion = repoMedios.etiquetar(guardado.id, listOf("sin-novedad"), "Entrada 3")
        assertTrue(conUbicacion is Result.Success)
        assertEquals(listOf("sin-novedad"), (conUbicacion as Result.Success).value.etiquetas)
        assertEquals("Entrada 3", conUbicacion.value.ubicacionTexto)

        val sinUbicacion = repoMedios.etiquetar(guardado.id, listOf("detenido", "accidente"))
                assertTrue(sinUbicacion is Result.Success)
                assertEquals(listOf("detenido", "accidente"), (sinUbicacion as Result.Success).value.etiquetas)
                assertEquals("Entrada 3", (sinUbicacion as Result.Success).value.ubicacionTexto)
    }

    @Test
    fun `actualizarGps adjunta la ubicacion a un medio pendiente`() = runBlocking {
        val repoReportes = RoomReporteRepository(database)
        val repoMedios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios))
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val reporte = (
            crear(
                custodio = com.ai27.app.custodio.domain.model.Custodio("c-1", "c@t.mx"),
                deviceId = "d1",
                clienteId = "c1",
                placasCapturadas = "ABC-1234",
            ) as Result.Success
            ).value
        // Se captura sin GPS, como hace ahora la ViewModel (la ubicación llega después).
        val guardado = (repoMedios.guardar(medio(reporte.id), ByteArray(10)) as Result.Success).value
        assertEquals(null, guardado.gps)

        val gps = Gps(lat = 19.43, lng = -99.13, accuracyM = 4.5, ageMs = 250L)
        val resultado = repoMedios.actualizarGps(guardado.id, gps)

        assertTrue(resultado is Result.Success)
        val actualizado = (resultado as Result.Success).value
        val ubicacion = actualizado.gps!!
        assertEquals(19.43, ubicacion.lat, 0.0001)
        assertEquals(-99.13, ubicacion.lng, 0.0001)
        assertEquals(4.5, ubicacion.accuracyM, 0.0001)
        assertEquals(250L, ubicacion.ageMs)
        // Persistió en la fila, no solo en el objeto devuelto.
        assertEquals(19.43, repoMedios.obtenerPorId(guardado.id)!!.gps!!.lat, 0.0001)
    }

    @Test
    fun `actualizarGps no muta el medio una vez que dejo de estar pendiente`() = runBlocking {
        val repoReportes = RoomReporteRepository(database)
        val repoMedios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios))
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val reporte = (
            crear(
                custodio = com.ai27.app.custodio.domain.model.Custodio("c-1", "c@t.mx"),
                deviceId = "d1",
                clienteId = "c1",
                placasCapturadas = "ABC-1234",
            ) as Result.Success
            ).value
        val guardado = (repoMedios.guardar(medio(reporte.id), ByteArray(10)) as Result.Success).value

        // Simula que la subida ya arrancó: el documento remoto ya se creó, la ubicación
        // tardía NO debe pisarlo.
        repoMedios.actualizarEstadoSubida(guardado.id, EstadoSubida.EN_PROGRESO)

        val resultado = repoMedios.actualizarGps(
            guardado.id,
            Gps(lat = 19.43, lng = -99.13, accuracyM = 4.5, ageMs = 250L),
        )

        assertTrue(resultado is Result.Success)
        // Devuelve el medio tal cual (sin GPS) porque la query se restringe a PENDIENTE.
        assertEquals(null, (resultado as Result.Success).value.gps)
        assertEquals(null, repoMedios.obtenerPorId(guardado.id)!!.gps)
    }

    @Test
    fun `no deja fila ni archivo cuando el disco esta lleno`() = runBlocking {
        // Hallazgo E4: antes, la IOException del writeBytes escapaba de la corrutina de la
        // ViewModel y tiraba la app en el momento del disparo. Ahora debe quedar un
        // Failure tipado, sin fila en Room y sin archivos a medias.
        val almacen = FakeAlmacenArchivos()
        val repoReportes = RoomReporteRepository(database)
        val repoMedios = RoomMedioRepository(database, almacen)
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val reporte = (
            crear(
                custodio = com.ai27.app.custodio.domain.model.Custodio("c-1", "c@t.mx"),
                deviceId = "d1",
                clienteId = "c1",
                placasCapturadas = "ABC-1234",
            ) as Result.Success
            ).value

        almacen.fallaAlGuardar = DominioError.SinEspacioEnDispositivo
        val resultado = repoMedios.guardar(medio(reporte.id), ByteArray(2048), ByteArray(512))

        assertTrue("debe fallar en lugar de lanzar", resultado is Result.Failure)
        assertEquals(DominioError.SinEspacioEnDispositivo, (resultado as Result.Failure).error)
        assertTrue("no debe quedar ninguna fila", repoMedios.obtenerDelReporte(reporte.id).isEmpty())
        assertTrue("no debe quedar ningún archivo", almacen.archivos.isEmpty())
    }

    @Test
    fun `si falla la miniatura no deja huerfano el original`() = runBlocking {
        // El original ya está en disco cuando la miniatura falla: sin fila que lo
        // referencie sería un huérfano invisible que nadie recolecta nunca.
        val almacen = AlmacenQueFallaEnLaSegundaEscritura()
        val repoReportes = RoomReporteRepository(database)
        val repoMedios = RoomMedioRepository(database, almacen)
        val crear = CrearReporte(repoReportes, ahora = { fijo })
        val reporte = (
            crear(
                custodio = com.ai27.app.custodio.domain.model.Custodio("c-1", "c@t.mx"),
                deviceId = "d1",
                clienteId = "c1",
                placasCapturadas = "ABC-1234",
            ) as Result.Success
            ).value

        val resultado = repoMedios.guardar(medio(reporte.id), ByteArray(2048), ByteArray(512))

        assertTrue(resultado is Result.Failure)
        assertTrue("no debe quedar ninguna fila", repoMedios.obtenerDelReporte(reporte.id).isEmpty())
        assertTrue("el original debe haberse borrado", almacen.archivos.isEmpty())
    }

    /** Deja pasar la primera escritura (el original) y falla la segunda (la miniatura). */
    private class AlmacenQueFallaEnLaSegundaEscritura : AlmacenArchivos {
        val archivos = mutableMapOf<String, ByteArray>()
        private var escrituras = 0

        override suspend fun guardar(nombre: String, contenido: ByteArray): Result<String> {
            escrituras++
            if (escrituras >= 2) return Result.Failure(DominioError.SinEspacioEnDispositivo)
            archivos[nombre] = contenido
            return Result.Success(nombre)
        }

        override suspend fun leer(ruta: String): ByteArray? = archivos[ruta]

        override suspend fun borrar(ruta: String) {
            archivos.remove(ruta)
        }
    }
}