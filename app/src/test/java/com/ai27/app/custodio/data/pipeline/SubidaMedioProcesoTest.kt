package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.FakeEtiquetaRepository
import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.data.remote.MedioRemoto
import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
// RETIRO_ETIQUETADO (Task 2): CategoriaEtiqueta y Etiqueta solo los usaba el test del
// candado por catálogo (A4), hoy retirado. Comentados —no borrados—.
// import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
// import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            etiquetaRepository = FakeEtiquetaRepository(),
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
            // Un reporte ACTIVO nace con mediosEsperados=null (CrearReporte); el valor solo
            // se fija al cerrar. En producción TODO reporte ACTIVO tiene esperados=null, así
            // que un ACTIVO con esperados=1 es un estado imposible que enmascaraba el bug.
            mediosEsperados = null,
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
                etiquetas = listOf("sin-novedad"),
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
        // localPath se CONSERVA en la fila: MedioDao.marcarSubidaCompleta solo actualiza
        // estadoSubida, storagePath y sha256. El archivo físico sí se borra (se comprueba
        // abajo), así que la fila queda apuntando a una ruta que ya no existe. Esta
        // aserción decía assertNull porque el fake limpiaba la columna (P2/D3).
        assertNotNull(subido.localPath)
        assertEquals("medios/r-1/m-1.jpg", subido.storagePath)
        assertTrue(subido.sha256!!.isNotBlank())
        // El archivo grande se borra; la miniatura local se conserva para la rejilla.
        assertNull(archivos.archivos["fisico/m-1"])
        assertTrue(archivos.archivos.containsKey("fisico/m-1-min"))
        assertEquals(1, medioRemoto.subidos.size)
        assertEquals(1, medioRemoto.docsCreados.size)
    }

    @Test
    fun `el sha256 viaja al remoto en la subida y en el documento`() = runBlocking {
        // Hallazgo C2 del AUDIT: el documento de medios quedaba con sha256 nulo porque se
        // construía con el medio leído de Room antes de subir. El proceso debe pasar una
        // copia con el hash ya calculado al remoto (medios es create-only en las reglas).
        proceso.ejecutar("r-1", medio.id)

        assertTrue(medioRemoto.mediosSubidos.size == 1)
        assertTrue("el sha debe llegar ya calculado", medioRemoto.mediosSubidos[0].sha256!!.isNotBlank())
        assertTrue(medioRemoto.mediosDeDocumento.size == 1)
        assertEquals(
            "el documento se crea con el mismo sha que Room guarda",
            repoMedios.obtenerPorId(medio.id)!!.sha256,
            medioRemoto.mediosDeDocumento[0].sha256,
        )
    }

    @Test
    fun `sube un medio sin etiquetas (candado retirado)`() = runBlocking {
        // RETIRO_ETIQUETADO (Task 2): el candado de Situación se retiró. Un medio con
        // `etiquetas` vacío ahora sube hasta COMPLETA en lugar de quedarse PENDIENTE.
        val sinSituacion = (repoMedios.guardar(
            Medio(
                id = "m-2",
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = fijo,
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.PENDIENTE,
                etiquetas = emptyList(),
            ),
            contenido = ByteArray(10),
            miniatura = ByteArray(4),
        ) as Result.Success).value

        val resultado = proceso.ejecutar("r-1", sinSituacion.id)

        assertTrue(resultado is Result.Success)
        assertEquals(EstadoSubida.COMPLETA, repoMedios.obtenerPorId(sinSituacion.id)!!.estadoSubida)
        assertEquals(1, medioRemoto.subidos.size)
        assertEquals(1, medioRemoto.docsCreados.size)
    }

    // RETIRO_ETIQUETADO (Task 2): se retiran los tests del candado (`no sube un medio sin
    // situacion`, `no sube un medio que solo lleva evidencia`, `sube un medio cuya situacion
    // existe solo en el catalogo remoto (A4)`). Hoy la subida no consulta el catálogo ni
    // exige Situación; se reactivan al descomentar el candado en SubidaMedioProceso.

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
        // El doc remoto sigue ACTIVO (esperados=null), así que completo=false: SubidaReporte
        // .completo exige mediosEsperados!=null. El contador de subidos sí avanza.
        assertFalse(actualizado.completo)
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
    fun `un medio ya completo recalcula los contadores para sanar la divergencia`() = runBlocking {
        // Hallazgo C1: la escritura remota de contadores se descartó y el reporte quedó
        // con la subida sin terminar aunque el medio ya estaba completo. En el reintento
        // no se vuelve a subir el medio: solo se propagan los contadores.
        repoMedios.actualizarEstadoSubida(medio.id, EstadoSubida.COMPLETA)

        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Success)
        assertEquals("el medio ya no se vuelve a subir", 0, medioRemoto.subidos.size)
        assertEquals(1, reporteRemoto.actualizacionesContadores)
        val actualizado = repoReportes.obtenerPorId("r-1")!!
        assertEquals(EstadoSubida.COMPLETA, actualizado.subida)
        // El doc sigue ACTIVO (esperados=null) -> completo=false. Lo que se sana es la
        // divergencia de contadores (actualizacionesContadores=1), no la bandera completo.
        assertFalse(actualizado.completo)
    }

    @Test
    fun `un medio ya completo propaga el fallo de la escritura remota de contadores`() = runBlocking {
        // C1: si el remoto vuelve a fallar, el resultado es Failure para que el worker
        // reintente, y Room NO se adelanta sobre Firestore (remoto-primero).
        repoMedios.actualizarEstadoSubida(medio.id, EstadoSubida.COMPLETA)
        reporteRemoto.debeFallarEscritura = true

        val resultado = proceso.ejecutar("r-1", medio.id)

        assertTrue(resultado is Result.Failure)
        assertEquals(DominioError.SinConexion, (resultado as Result.Failure).error)
        val local = repoReportes.obtenerPorId("r-1")!!
        assertEquals("Room no debe adelantar un estado que el remoto rechazó", EstadoSubida.PENDIENTE, local.subida)
        assertFalse(local.completo)
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