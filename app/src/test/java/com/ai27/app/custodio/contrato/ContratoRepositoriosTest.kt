package com.ai27.app.custodio.contrato

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato observable de [MedioRepository] y [ReporteRepository].
 *
 * Existe por el hallazgo **P2** de `docs/AUDIT.md`: los fakes de prueba divergían de las
 * implementaciones Room en cuatro puntos, y **dos de esas divergencias estaban afirmadas
 * en pruebas verdes**, así que la suite documentaba comportamiento que no existía en
 * producción. Esta clase se ejecuta contra **las dos** implementaciones, de modo que un
 * fake no puede volver a separarse del DAO sin que algo falle.
 *
 * Reglas para lo que se escribe aquí: solo comportamiento **observable** a través de la
 * interfaz. Nada de detalles de implementación (ni SQL, ni rutas de archivo concretas, ni
 * si el archivo físico existe), porque eso es justamente lo que legítimamente difiere
 * entre Room y el fake.
 */
abstract class ContratoRepositoriosTest {

    protected abstract val medios: MedioRepository
    protected abstract val reportes: ReporteRepository

    protected val instante: Instant = Instant.parse("2026-09-21T15:00:00Z")

    /** Fecha lo bastante antigua para que la ventana no excluya nada en las pruebas. */
    protected val desdeSiempre: String = "2020-01-01"

    /**
     * El fake de medios no conoce los reportes, así que necesita que le digan de quién es
     * cada uno para poder acotar la cola por custodio. Room lo resuelve con un JOIN y no
     * necesita nada; por eso el gancho está vacío por defecto.
     */
    protected open fun registrarDuenoDelReporte(reporteId: String, custodioUid: String) = Unit

    // region ayudas

    protected fun nuevoReporte(
        custodioUid: String = "custodio-1",
        ciclo: Ciclo = Ciclo.ACTIVO,
        fechaLocal: String = "2026-09-21",
    ): Reporte = Reporte(
        id = "reporte-${UUID.randomUUID()}",
        placas = "ABC123",
        placasCapturadas = "abc-123",
        custodioUid = custodioUid,
        custodioCorreo = "custodio@ai27.test",
        clienteId = "cliente-provisional",
        deviceId = "dispositivo-1",
        fechaLocal = fechaLocal,
        createdAtDevice = instante,
        ciclo = ciclo,
        subida = EstadoSubida.PENDIENTE,
        completo = false,
        mediosEsperados = null,
        mediosSubidos = 0,
        resumenMedios = ResumenMedios(),
    )

    protected fun nuevoMedio(
        reporteId: String,
        tipo: TipoMedio = TipoMedio.FOTO,
        etiquetas: List<String> = listOf("sin-novedad"),
        durationMs: Long? = null,
    ): Medio = Medio(
        id = "medio-${UUID.randomUUID()}",
        reporteId = reporteId,
        tipo = tipo,
        origen = if (tipo == TipoMedio.FOTO) OrigenMedio.CAMARA else OrigenMedio.GRABADORA,
        createdAtDevice = instante,
        contentType = if (tipo == TipoMedio.FOTO) "image/jpeg" else "audio/mp4",
        estadoSubida = EstadoSubida.PENDIENTE,
        etiquetas = etiquetas,
        durationMs = durationMs,
    )

    private suspend fun reporteGuardado(
        ciclo: Ciclo = Ciclo.ACTIVO,
        custodioUid: String = "custodio-1",
        fechaLocal: String = "2026-09-21",
    ): Reporte {
        val reporte = nuevoReporte(custodioUid = custodioUid, ciclo = ciclo, fechaLocal = fechaLocal)
        assertTrue(reportes.crear(reporte) is Result.Success)
        registrarDuenoDelReporte(reporte.id, custodioUid)
        return reporte
    }

    private suspend fun medioGuardado(
        reporteId: String,
        tipo: TipoMedio = TipoMedio.FOTO,
        etiquetas: List<String> = listOf("sin-novedad"),
        durationMs: Long? = null,
    ): Medio {
        val resultado = medios.guardar(nuevoMedio(reporteId, tipo, etiquetas, durationMs), ByteArray(64), ByteArray(16))
        return (resultado as Result.Success).value
    }

    // endregion

    // region MedioRepository

    @Test
    fun `obtenerPendientes incluye los medios FALLIDA`() = runBlocking {
        // Divergencia P2: el fake excluía FALLIDA, el DAO filtra solo por != COMPLETA.
        // Importa porque reconciliar() usa esta lista para reponer la cola: si excluyera
        // los fallidos, un medio en FALLIDA no se reintentaría nunca al reabrir la app.
        val reporte = reporteGuardado()
        val fallido = medioGuardado(reporte.id)
        medios.actualizarEstadoSubida(fallido.id, EstadoSubida.FALLIDA)

        val pendientes = medios.obtenerPendientes(reporte.custodioUid)

        assertTrue(
            "obtenerPendientes() debe incluir los FALLIDA para que la reconciliación los reintente",
            pendientes.any { it.id == fallido.id },
        )
    }

    @Test
    fun `obtenerPendientes incluye EN_PROGRESO y excluye COMPLETA`() = runBlocking {
        val reporte = reporteGuardado()
        val enProgreso = medioGuardado(reporte.id)
        val completa = medioGuardado(reporte.id)
        medios.actualizarEstadoSubida(enProgreso.id, EstadoSubida.EN_PROGRESO)
        medios.marcarSubidaCompleta(completa.id, "medios/${reporte.id}/${completa.id}.jpg", "sha-1")

        val pendientes = medios.obtenerPendientes(reporte.custodioUid)

        assertTrue(pendientes.any { it.id == enProgreso.id })
        assertFalse(pendientes.any { it.id == completa.id })
    }

    @Test
    fun `obtenerPendientes excluye los medios sin etiqueta de situacion`() = runBlocking {
        // Hallazgo E3: un medio sin Situación no puede subirse (siempre se exige al menos
        // una, decisión A del PLAN §14), así que encolarlo solo produce trabajo que falla en
        // bucle. Se queda fuera de la cola hasta que alguien lo clasifique.
        val reporte = reporteGuardado()
        val sinEtiqueta = medioGuardado(reporte.id, etiquetas = emptyList())
        val soloEvidencia = medioGuardado(reporte.id, etiquetas = listOf("accidente"))
        val conSituacion = medioGuardado(reporte.id, etiquetas = listOf("sin-novedad", "accidente"))

        val pendientes = medios.obtenerPendientes(reporte.custodioUid)

        assertFalse(
            "Un medio sin etiqueta de Situación no debe entrar a la cola de subida",
            pendientes.any { it.id == sinEtiqueta.id },
        )
        assertFalse(
            "Un medio solo con Evidencia no debe entrar a la cola de subida",
            pendientes.any { it.id == soloEvidencia.id },
        )
        assertTrue(pendientes.any { it.id == conSituacion.id })
    }

    @Test
    fun `obtenerPendientes solo devuelve medios del custodio consultado`() = runBlocking {
        // Hallazgo E6: la cola no filtraba por custodio, así que tras cambiar de sesión se
        // reintentaba subir evidencia del custodio anterior con las credenciales del nuevo.
        val mio = reporteGuardado(custodioUid = "custodio-1")
        val ajeno = reporteGuardado(custodioUid = "custodio-2")
        val medioMio = medioGuardado(mio.id)
        val medioAjeno = medioGuardado(ajeno.id)

        val pendientes = medios.obtenerPendientes("custodio-1")

        assertTrue(pendientes.any { it.id == medioMio.id })
        assertFalse(
            "La cola no debe incluir evidencia de otro custodio",
            pendientes.any { it.id == medioAjeno.id },
        )
    }

    @Test
    fun `observarDelReporte reemite cuando entra un medio nuevo`() = runBlocking {
        // Divergencia encontrada al implementar E3b: el fake devolvía un StateFlow recién
        // creado con un snapshot y nunca reemitía, mientras el Flow de Room está vivo. Una
        // pantalla suscrita antes de la primera captura se quedaba con la rejilla vacía
        // para siempre. Nada lo detectaba porque el contrato solo comparaba lecturas
        // puntuales, no emisiones.
        val reporte = reporteGuardado()
        val emisiones = mutableListOf<List<Medio>>()
        val coleccion = launch { medios.observarDelReporte(reporte.id).collect { emisiones += it } }

        esperarHasta("la primera emisión") { emisiones.isNotEmpty() }
        val nuevo = medioGuardado(reporte.id)
        esperarHasta("la reemisión con el medio nuevo") {
            emisiones.last().any { it.id == nuevo.id }
        }

        coleccion.cancel()
    }

    /** Espera activa con tope: los Flow de Room emiten desde su propio hilo. */
    private suspend fun esperarHasta(que: String, condicion: () -> Boolean) {
        val cumplio = withTimeoutOrNull(2_000L) {
            while (!condicion()) delay(10L)
            true
        }
        assertTrue("Se agotó el tiempo esperando $que", cumplio == true)
    }

    @Test
    fun `marcarSubidaCompleta fija estado storagePath y sha256 y conserva localPath`() = runBlocking {
        // Divergencia P2: el fake ponía localPath = null; el UPDATE del DAO no toca esa
        // columna. La fila real conserva una ruta a un archivo que ya se borró, y eso es
        // lo que ve la rejilla (por eso Coil puede quedarse sin imagen). Se fija el
        // comportamiento real para que nadie construya lógica sobre el del fake.
        val reporte = reporteGuardado()
        val medio = medioGuardado(reporte.id)
        assertNotNull("El medio guardado debe tener localPath", medio.localPath)

        val resultado = medios.marcarSubidaCompleta(medio.id, "medios/${reporte.id}/${medio.id}.jpg", "sha-256-abc")

        assertTrue(resultado is Result.Success)
        val subido = medios.obtenerPorId(medio.id)!!
        assertEquals(EstadoSubida.COMPLETA, subido.estadoSubida)
        assertEquals("medios/${reporte.id}/${medio.id}.jpg", subido.storagePath)
        assertEquals("sha-256-abc", subido.sha256)
        assertNotNull(
            "marcarSubidaCompleta() NO limpia localPath: la fila conserva la ruta del archivo borrado",
            subido.localPath,
        )
    }

    @Test
    fun `borrarPendiente elimina un medio PENDIENTE`() = runBlocking {
        val reporte = reporteGuardado()
        val medio = medioGuardado(reporte.id)

        val resultado = medios.borrarPendiente(medio.id)

        assertTrue(resultado is Result.Success)
        assertNull(medios.obtenerPorId(medio.id))
    }

    @Test
    fun `borrarPendiente rechaza un medio que ya esta EN_PROGRESO`() = runBlocking {
        val reporte = reporteGuardado()
        val medio = medioGuardado(reporte.id)
        medios.actualizarEstadoSubida(medio.id, EstadoSubida.EN_PROGRESO)

        val resultado = medios.borrarPendiente(medio.id)

        assertTrue("No se debe poder borrar evidencia que ya está subiendo", resultado is Result.Failure)
        assertEquals(DominioError.MedioEnSubida, (resultado as Result.Failure).error)
        assertNotNull("El medio debe seguir existiendo", medios.obtenerPorId(medio.id))
    }

    @Test
    fun `actualizarGps adjunta la ubicacion mientras el medio sigue PENDIENTE`() = runBlocking {
        val reporte = reporteGuardado()
        val medio = medioGuardado(reporte.id)
        assertNull(medio.gps)

        val resultado = medios.actualizarGps(medio.id, Gps(lat = 19.43, lng = -99.13, accuracyM = 4.5, ageMs = 250L))

        assertTrue(resultado is Result.Success)
        val conGps = medios.obtenerPorId(medio.id)!!.gps
        assertNotNull(conGps)
        assertEquals(19.43, conGps!!.lat, 0.0001)
    }

    @Test
    fun `actualizarGps no muta el medio cuando ya dejo de estar PENDIENTE`() = runBlocking {
        // El GPS llega hasta 8 s después de capturar. Si el medio ya empezó a subir, el
        // documento remoto pudo escribirse: una ubicación tardía no debe divergir de él.
        val reporte = reporteGuardado()
        val medio = medioGuardado(reporte.id)
        medios.actualizarEstadoSubida(medio.id, EstadoSubida.EN_PROGRESO)

        val resultado = medios.actualizarGps(medio.id, Gps(lat = 19.43, lng = -99.13, accuracyM = 4.5, ageMs = 250L))

        assertTrue(resultado is Result.Success)
        assertNull("La ubicación tardía no debe pisar un medio que ya subió", medios.obtenerPorId(medio.id)!!.gps)
    }

    @Test
    fun `etiquetar fija las etiquetas y conserva la ubicacion previa si no se pasa otra`() = runBlocking {
        val reporte = reporteGuardado()
        val medio = medioGuardado(reporte.id)

        assertTrue(medios.etiquetar(medio.id, listOf("sin-novedad"), "Entrada 3") is Result.Success)
        assertEquals(listOf("sin-novedad"), medios.obtenerPorId(medio.id)!!.etiquetas)
        assertEquals("Entrada 3", medios.obtenerPorId(medio.id)!!.ubicacionTexto)

        assertTrue(medios.etiquetar(medio.id, listOf("detenido", "accidente")) is Result.Success)
        val reetiquetado = medios.obtenerPorId(medio.id)!!
        assertEquals(listOf("detenido", "accidente"), reetiquetado.etiquetas)
        assertEquals("Entrada 3", reetiquetado.ubicacionTexto)
    }

    @Test
    fun `obtenerDelReporte devuelve solo los medios de ese reporte`() = runBlocking {        val unoA = reporteGuardado(ciclo = Ciclo.ACTIVO)
        val medioA = medioGuardado(unoA.id)
        // Segundo reporte de otro custodio, para no chocar con el invariante de un activo.
        val otro = nuevoReporte(custodioUid = "custodio-2")
        assertTrue(reportes.crear(otro) is Result.Success)
        val medioB = medioGuardado(otro.id)

        val delPrimero = medios.obtenerDelReporte(unoA.id)

        assertTrue(delPrimero.any { it.id == medioA.id })
        assertFalse(delPrimero.any { it.id == medioB.id })
    }

    // endregion

    // region ReporteRepository

    @Test
    fun `cerrar no infla mediosSubidos ni completo`() = runBlocking {
        // Divergencia P2: el fake fijaba completo = true y mediosSubidos = mediosEsperados.
        // El UPDATE real no toca ninguna de las dos columnas, y hace bien: al cerrar, la
        // evidencia todavía puede estar en la cola. Quien los avanza es el pipeline.
        // CerrarReporteTest afirmaba el comportamiento del fake, no el de producción.
        val reporte = reporteGuardado()
        medioGuardado(reporte.id)

        val resultado = reportes.cerrar(
            reporteId = reporte.id,
            closedAt = instante,
            comentario = "sin novedades",
            hayNovedad = false,
        )

        assertTrue(resultado is Result.Success)
        val cerrado = reportes.obtenerPorId(reporte.id)!!
        assertEquals(Ciclo.CERRADO, cerrado.ciclo)
        // C6: el conteo sale del UPDATE atómico (SELECT sobre medios), no de parámetros.
        assertEquals(1, cerrado.mediosEsperados)
        assertEquals("Cerrar no sube el contador: el pipeline es quien lo avanza", 0, cerrado.mediosSubidos)
        assertFalse("Cerrar no marca completo: la evidencia puede seguir en la cola", cerrado.completo)
    }

    @Test
    fun `cerrar guarda comentario y hayNovedad`() = runBlocking {
        val reporte = reporteGuardado()
        // El resumen lo deriva el propio cierre: se siembran 2 fotos + 1 audio para que la
        // derivación atómica tenga qué contar (hallazgo C6).
        medioGuardado(reporte.id)
        medioGuardado(reporte.id)
        medioGuardado(reporte.id, tipo = TipoMedio.AUDIO, durationMs = 5_000)

        assertTrue(
            reportes.cerrar(
                reporteId = reporte.id,
                closedAt = instante,
                comentario = "se detectó un sello roto",
                hayNovedad = true,
            ) is Result.Success,
        )

        val cerrado = reportes.obtenerPorId(reporte.id)!!
        assertEquals("se detectó un sello roto", cerrado.comentario)
        assertTrue(cerrado.hayNovedad)
        assertEquals(2, cerrado.resumenMedios.fotos)
        assertEquals(1, cerrado.resumenMedios.audios)
        assertEquals(5_000L, cerrado.resumenMedios.durationMsTotal)
        assertEquals(3, cerrado.mediosEsperados)
    }

    @Test
    fun `un reporte ya cerrado no se puede volver a cerrar`() = runBlocking {
        val reporte = reporteGuardado()
        assertTrue(reportes.cerrar(reporte.id, instante, null, false) is Result.Success)

        val segundo = reportes.cerrar(reporte.id, instante, null, false)

        assertTrue(segundo is Result.Failure)
        assertEquals(DominioError.ReporteCerradoInmutable, (segundo as Result.Failure).error)
    }

    @Test
    fun `cerrar cuenta los medios presentes al momento del UPDATE, no una lectura previa`() = runBlocking {
        // Hallazgo C6: el conteo salía de una lectura previa (obtenerDelReporte del caso de
        // uso) y un medio que entraba entre esa lectura y el UPDATE quedaba fuera de
        // mediosEsperados — `completo` podía ponerse true antes de tiempo. El cierre es
        // un solo UPDATE atómico con COUNT(*)/SUM sobre `medios`: cuenta lo que hay en el
        // instante exacto del cerrar, así que la "captura en la ventana" sí entra.
        val reporte = reporteGuardado()
        medioGuardado(reporte.id) // lo que una lectura previa habría visto

        // La captura que llega antes de que corra el cierre:
        medioGuardado(reporte.id)

        val resultado = reportes.cerrar(reporte.id, instante, null, false)

        assertTrue(resultado is Result.Success)
        val cerrado = reportes.obtenerPorId(reporte.id)!!
        assertEquals("el medio de la ventana queda contado", 2, cerrado.mediosEsperados)
        assertEquals(2, cerrado.resumenMedios.fotos)
        assertEquals("sin medios subidos no puede marcarse completo", 0, cerrado.mediosSubidos)
        assertFalse(cerrado.completo)
    }

    @Test
    fun `descartar exige ciclo ACTIVO y guarda el motivo`() = runBlocking {
        val reporte = reporteGuardado()

        val resultado = reportes.descartar(reporte.id, instante, "placa equivocada")

        assertTrue(resultado is Result.Success)
        val descartado = reportes.obtenerPorId(reporte.id)!!
        assertEquals(Ciclo.DESCARTADO, descartado.ciclo)
        assertEquals("placa equivocada", descartado.motivoDescarte)

        val segundo = reportes.descartar(reporte.id, instante, "otro motivo")
        assertTrue(segundo is Result.Failure)
    }

    @Test
    fun `obtenerActivo devuelve el reporte ACTIVO del custodio y nada tras cerrarlo`() = runBlocking {
        val reporte = reporteGuardado()

        assertEquals(reporte.id, reportes.obtenerActivo(reporte.custodioUid)?.id)

        assertTrue(reportes.cerrar(reporte.id, instante, null, false) is Result.Success)
        assertNull(reportes.obtenerActivo(reporte.custodioUid))
    }

    @Test
    fun `crear rechaza un segundo reporte activo del mismo custodio`() = runBlocking {
        val primero = reporteGuardado()

        val segundo = reportes.crear(nuevoReporte(custodioUid = primero.custodioUid))

        assertTrue(segundo is Result.Failure)
        val error = (segundo as Result.Failure).error
        assertTrue("Debe ser YaExisteReporteActivo, no un error genérico", error is DominioError.YaExisteReporteActivo)
        assertEquals(primero.id, (error as DominioError.YaExisteReporteActivo).reporteId)
    }

    @Test
    fun `obtenerPendientesSubida incluye un reporte DESCARTADO cuya subida no esta COMPLETA`() = runBlocking {
        // Divergencia P2: el fake filtraba por ciclo != DESCARTADO; el DAO filtra por
        // subida != COMPLETA, así que un descartado sigue en la lista. Es el
        // comportamiento real y es el que alimenta el backlog creciente descrito en E6.
        val reporte = reporteGuardado()
        assertTrue(reportes.descartar(reporte.id, instante, "abierto por error") is Result.Success)

        val pendientes = reportes.obtenerPendientesSubida(reporte.custodioUid, desdeSiempre)

        assertTrue(
            "El filtro real es subida != COMPLETA, no el ciclo: un descartado sigue apareciendo",
            pendientes.any { it.id == reporte.id },
        )
    }

    @Test
    fun `obtenerPendientesSubida excluye un reporte con subida COMPLETA`() = runBlocking {
        val reporte = reporteGuardado()
        assertTrue(
            reportes.actualizarSubida(reporte.id, EstadoSubida.COMPLETA, mediosSubidos = 1, completo = true)
                is Result.Success,
        )

        assertFalse(
            reportes.obtenerPendientesSubida(reporte.custodioUid, desdeSiempre).any { it.id == reporte.id },
        )
    }

    @Test
    fun `obtenerPendientesSubida REincluye un reporte COMPLETA en divergencia`() = runBlocking {
        // Hallazgo C1 del AUDIT: si el reporte quedó con `subida = COMPLETA` pero
        // `completo = false` (contadores locales adelantados por una escritura remota
        // descartada), salía de la reconciliación para siempre. Ahora la firma de
        // divergencia local lo vuelve a exponer para sanarlo.
        val reporte = reporteGuardado()
        assertTrue(
            reportes.cerrar(
                reporteId = reporte.id,
                closedAt = instante,
                comentario = null,
                hayNovedad = false,
            ) is Result.Success,
        )
        assertTrue(
            reportes.actualizarSubida(reporte.id, EstadoSubida.COMPLETA, mediosSubidos = 2, completo = false)
                is Result.Success,
        )

        val pendientes = reportes.obtenerPendientesSubida(reporte.custodioUid, desdeSiempre)

        assertTrue(
            "subida COMPLETA con completo = false es divergencia: debe seguir en la reconciliación",
            pendientes.any { it.id == reporte.id },
        )
    }

    @Test
    fun `obtenerPendientesSubida solo devuelve reportes del custodio consultado`() = runBlocking {
        // Hallazgo E6, mismo motivo que en la cola de medios.
        val mio = reporteGuardado(custodioUid = "custodio-1")
        val ajeno = reporteGuardado(custodioUid = "custodio-2")

        val pendientes = reportes.obtenerPendientesSubida("custodio-1", desdeSiempre)

        assertTrue(pendientes.any { it.id == mio.id })
        assertFalse(
            "La reconciliación no debe tocar reportes de otro custodio",
            pendientes.any { it.id == ajeno.id },
        )
    }

    @Test
    fun `obtenerPendientesSubida excluye los reportes anteriores a la fecha pedida`() = runBlocking {
        // Hallazgo E6: sin ventana, cada arranque reencolaba todo el histórico que nunca
        // llegó a COMPLETA. La reconciliación pide los últimos 30 días.
        val viejo = reporteGuardado(ciclo = Ciclo.CERRADO, fechaLocal = "2026-01-10")
        val reciente = reporteGuardado(fechaLocal = "2026-09-21")

        val pendientes = reportes.obtenerPendientesSubida("custodio-1", fechaDesde = "2026-08-22")

        assertTrue(pendientes.any { it.id == reciente.id })
        assertFalse(
            "Un reporte fuera de la ventana no debe reencolarse en cada arranque",
            pendientes.any { it.id == viejo.id },
        )
    }

    @Test
    fun `actualizarSubida avanza estado contador y completo`() = runBlocking {
        val reporte = reporteGuardado()

        val resultado = reportes.actualizarSubida(
            reporteId = reporte.id,
            estado = EstadoSubida.EN_PROGRESO,
            mediosSubidos = 2,
            completo = false,
        )

        assertTrue(resultado is Result.Success)
        val actualizado = reportes.obtenerPorId(reporte.id)!!
        assertEquals(EstadoSubida.EN_PROGRESO, actualizado.subida)
        assertEquals(2, actualizado.mediosSubidos)
        assertFalse(actualizado.completo)
    }

    // endregion
}
