package com.ai27.app.custodio.ui.captura

import com.ai27.app.custodio.FakeEtiquetaRepository
import com.ai27.app.custodio.FakeGrabadora
import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeProveedorSnapshotMapa
import com.ai27.app.custodio.FakeProveedorUbicacion
import com.ai27.app.custodio.FakeRegistroDeFallos
import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.FakeSesionRepositorio
import com.ai27.app.custodio.FakeSubidaScheduler
import com.ai27.app.custodio.MainDispatcherRule
import com.ai27.app.custodio.R
import com.ai27.app.custodio.data.foto.CodecFoto
import com.ai27.app.custodio.data.foto.Dimensiones
import com.ai27.app.custodio.data.foto.RevelarFoto
import com.ai27.app.custodio.data.marcaDeAgua.ImagenMapa
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CerrarReporte
import com.ai27.app.custodio.domain.usecase.CrearReporte
import com.ai27.app.custodio.domain.usecase.DescartarReporte
import com.ai27.app.custodio.domain.usecase.EtiquetarMedio
import com.ai27.app.custodio.domain.usecase.ObtenerEtiquetas
import com.ai27.app.custodio.domain.usecase.ReintentarSubida
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val custodio = Custodio(uid = "c-1", correo = "custodio@ai27.com")
    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var sesion: FakeSesionRepositorio
    private lateinit var ubicacion: FakeProveedorUbicacion
    private lateinit var grabadora: FakeGrabadora
    private lateinit var scheduler: FakeSubidaScheduler
    private lateinit var registroDeFallos: FakeRegistroDeFallos
    private lateinit var codec: CodecFotoFalso
    private lateinit var proveedorMapa: FakeProveedorSnapshotMapa

    @Before
    fun setUp() {
        repoReportes = FakeReporteRepository()
        repoMedios = FakeMedioRepository()
        sesion = FakeSesionRepositorio(custodio)
        ubicacion = FakeProveedorUbicacion()
        grabadora = FakeGrabadora()
        scheduler = FakeSubidaScheduler()
        registroDeFallos = FakeRegistroDeFallos()
        codec = CodecFotoFalso()
        proveedorMapa = FakeProveedorSnapshotMapa()
    }

    private fun crearViewModel(): CapturaViewModel {
        val etiquetas = FakeEtiquetaRepository()
        // El despachador de la prueba en vez de Dispatchers.Default: así runCurrent() deja
        // el revelado terminado y el orden revelado → encolado es determinista.
        val revelarFoto = RevelarFoto(
            medioRepository = repoMedios,
            codec = codec,
            proveedorSnapshotMapa = proveedorMapa,
            registroDeFallos = registroDeFallos,
            zona = java.time.ZoneOffset.UTC,
            despachador = mainDispatcherRule.dispatcher,
        )
        return CapturaViewModel(
            reporteRepository = repoReportes,
            medioRepository = repoMedios,
            sesionRepositorio = sesion,
            crearReporte = CrearReporte(repoReportes),
            capturarMedio = CapturarMedio(repoReportes, repoMedios),
            etiquetarMedio = EtiquetarMedio(repoMedios, etiquetas),
            cerrarReporte = CerrarReporte(repoReportes, repoMedios),
            descartarReporte = DescartarReporte(repoReportes),
            obtenerEtiquetas = ObtenerEtiquetas(etiquetas),
            reintentarSubida = ReintentarSubida(repoMedios),
            proveedorUbicacion = ubicacion,
            revelarFoto = revelarFoto,
            grabadora = grabadora,
            programadorSubidas = scheduler,
            registroDeFallos = registroDeFallos,
            deviceId = "dev-test",
        )
    }

    private suspend fun seedReporteActivo(): Reporte =
        (CrearReporte(repoReportes).invoke(custodio, "dev-test", "cli", "ABC-1234") as Result.Success).value

    @Test
    fun `la captura de foto no espera el GPS para persistir y encolar`() =
        runTest(mainDispatcherRule.dispatcher) {
            // RETIRO_ETIQUETADO: ya no hay hoja de etiquetado. La foto se persiste de
            // inmediato (sin esperar el GPS) y queda lista para encolarse.
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), 0)
            runCurrent() // el GPS sigue sin resolverse (puerta abierta)

            val estado = vm.uiState.value
            assertEquals(reporte.id, estado.reporte?.id)
            // La foto ya se persistió, sin esperar la ubicación.
            assertEquals(1, repoMedios.medios.size)
            assertFalse(estado.capturando)
            assertNull(repoMedios.medios.single().gps)
            // Nada se encola todavía porque el encolado espera al GPS en segundo plano.
            assertTrue(scheduler.encolados.isEmpty())
        }

    @Test
    fun `capturar encola la subida sin etiquetar, solo despues de adjuntar el GPS`() =
        runTest(mainDispatcherRule.dispatcher) {
            // RETIRO_ETIQUETADO: el encolado ya no pasa por guardarEtiqueta. Apenas la foto
            // se persiste, encolarCuandoListo espera a que el GPS se adjunte y encola. El
            // medio sube SIN clasificar.
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), 0)
            runCurrent() // persistido; el encolado espera al GPS
            val medioId = repoMedios.medios.single().id

            assertTrue(
                "no debe encolar la subida antes de que el GPS se adjunte",
                scheduler.encolados.isEmpty(),
            )
            assertNull(repoMedios.medios.single().gps)

            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            // Ahora el GPS está en la fila y, solo entonces, se encoló la subida.
            assertEquals(10.0, repoMedios.medios.single().gps!!.lat, 0.0001)
            assertEquals(listOf(reporte.id to medioId), scheduler.encolados)
            // Y se subió sin ninguna etiqueta.
            assertTrue(repoMedios.medios.single().etiquetas.isEmpty())
        }

    @Test
    fun `capturar una foto persiste la cruda con su rotacion y la miniatura del codec`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Task 4 de PLAN_REVELAR_FOTO: el obturador ya no decodifica; se guarda el JPEG
            // del HAL tal cual, sin revelar, con los grados que CameraX no aplica.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            val crudo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

            vm.capturarFoto(crudo, 90)
            runCurrent() // el GPS sigue pendiente: todavía no se revela

            val medio = repoMedios.medios.single()
            assertEquals("image/jpeg", medio.contentType)
            assertFalse(medio.revelada)
            assertEquals(90, medio.rotacionGrados)
            assertArrayEquals(crudo, repoMedios.archivosGuardados[medio.localPath])
            assertArrayEquals(CodecFotoFalso.MINIATURA, repoMedios.archivosGuardados[medio.thumbnailPath])
            assertEquals("la miniatura se pide con la rotación de la foto", listOf(90), codec.rotacionesMiniatura)
            assertTrue(scheduler.encolados.isEmpty())
        }

    @Test
    fun `si la miniatura falla la foto se guarda igual sin miniatura y se registra el fallo`() =
        runTest(mainDispatcherRule.dispatcher) {
            // La evidencia no se pierde por la vista previa.
            seedReporteActivo()
            codec.fallarMiniatura = true
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), 0)
            runCurrent()

            val medio = repoMedios.medios.single()
            assertNull(medio.thumbnailPath)
            assertEquals("image/jpeg", medio.contentType)
            assertEquals(listOf("captura_miniatura"), registroDeFallos.registrados.map { it.first })
            assertNull("no es un error para el custodio", vm.uiState.value.error)
            assertEquals(1, vm.uiState.value.avisoEncolado)
        }

    @Test
    fun `con GPS la foto se revela con fecha y coords antes de encolar la subida`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Tras resolver el GPS, RevelarFoto revela la cruda (marca + WebP) y solo después
            // se encola la subida: nunca se sube una cruda desde la cámara.
            seedReporteActivo()
            val vm = crearViewModel()
            val reveladaAlEncolar = mutableListOf<Boolean>()
            scheduler.alEncolar = { _, id -> reveladaAlEncolar.add(repoMedios.medios.single { it.id == id }.revelada) }
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), 90)
            runCurrent()
            ubicacion.completar(Gps(lat = 19.432608, lng = -99.133209, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            val llamada = codec.revelados.single()
            assertEquals("19.432608, -99.133209", llamada.marca?.coordenadas)
            assertTrue("la fecha nunca falta", llamada.marca!!.fechaHora.isNotBlank())
            assertEquals(90, llamada.rotacion)
            // Snapshot null (fake sin red) => sin mapa al codec (requisito 3).
            assertNull(llamada.mapa)
            assertEquals("se pidió el snapshot al haber GPS", 1, proveedorMapa.veces)
            val medio = repoMedios.medios.single()
            assertTrue(medio.revelada)
            assertEquals("image/webp", medio.contentType)
            assertArrayEquals(CodecFotoFalso.REVELADA, repoMedios.archivosGuardados[medio.localPath])
            assertEquals("se encoló una vez, ya revelada", listOf(true), reveladaAlEncolar)
        }

    @Test
    fun `sin fix de GPS la foto se revela solo con fecha y sin pedir mapa antes de encolar`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Requisito 1: sin GPS la foto se graba igual, con el timestamp; sin coords ni mapa.
            seedReporteActivo()
            val vm = crearViewModel()
            val reveladaAlEncolar = mutableListOf<Boolean>()
            scheduler.alEncolar = { _, id -> reveladaAlEncolar.add(repoMedios.medios.single { it.id == id }.revelada) }
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), 0)
            runCurrent()
            ubicacion.completarSinFix()
            runCurrent()

            val llamada = codec.revelados.single()
            assertTrue("la fecha va aunque no haya GPS", llamada.marca!!.fechaHora.isNotBlank())
            assertNull("sin GPS no hay coords", llamada.marca.coordenadas)
            assertNull("sin GPS no se pasa mapa", llamada.mapa)
            assertEquals("sin GPS no se pide snapshot", 0, proveedorMapa.veces)
            assertEquals("image/webp", repoMedios.medios.single().contentType)
            assertEquals(listOf(true), reveladaAlEncolar)
        }

    @Test
    fun `si el GPS vence su timeout la foto se revela y luego se encola`() =
        runTest(mainDispatcherRule.dispatcher) {
            // El proveedor nunca responde: a los 8 s se revela sin coordenadas y se encola.
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            val reveladaAlEncolar = mutableListOf<Boolean>()
            scheduler.alEncolar = { _, id -> reveladaAlEncolar.add(repoMedios.medios.single { it.id == id }.revelada) }
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), 0)
            runCurrent()
            advanceTimeBy(7_999)
            runCurrent()
            assertTrue("antes del timeout no se revela", codec.revelados.isEmpty())
            assertTrue(scheduler.encolados.isEmpty())

            advanceTimeBy(2)
            runCurrent()

            val medio = repoMedios.medios.single()
            assertNull(codec.revelados.single().marca!!.coordenadas)
            assertTrue(medio.revelada)
            assertEquals("image/webp", medio.contentType)
            assertEquals(listOf(reporte.id to medio.id), scheduler.encolados)
            assertEquals(listOf(true), reveladaAlEncolar)
        }

    @Test
    fun `la nota de voz no se revela ni cambia`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Alcance: solo fotos. El audio no pasa por el codec ni pide mapa.
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.iniciarGrabacionDeAudio()
            runCurrent()
            vm.detenerGrabacionDeAudio()
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            val medio = repoMedios.medios.single()
            assertEquals(TipoMedio.AUDIO, medio.tipo)
            assertEquals("audio/mp4", medio.contentType)
            assertTrue(medio.revelada)
            assertEquals(0, medio.rotacionGrados)
            assertEquals("el audio no toca el codec", 0, codec.llamadas)
            assertEquals("ni se le pide mapa", 0, proveedorMapa.veces)
            assertEquals(listOf(reporte.id to medio.id), scheduler.encolados)
        }

    @Test
    fun `la nota de voz tampoco bloquea la captura esperando el GPS`() =
        runTest(mainDispatcherRule.dispatcher) {
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.iniciarGrabacionDeAudio()
            runCurrent()
            vm.detenerGrabacionDeAudio()
            runCurrent() // el GPS sigue sin resolverse

            val estado = vm.uiState.value
            assertFalse(estado.capturando)
            assertEquals(1, repoMedios.medios.size)
            assertEquals(TipoMedio.AUDIO, repoMedios.medios.single().tipo)
            assertNull(repoMedios.medios.single().gps)
        }

    @Test
    fun `un fallo de camara se avisa al custodio y se registra como no fatal`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Hallazgo E2: antes esto se perdía en un catch vacío. El custodio tocaba, no
            // pasaba nada, y no quedaba rastro para diagnosticarlo desde el campo.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            val causa = OutOfMemoryError("no cupo el bitmap")
            vm.fallaDeCaptura(causa)
            runCurrent()

            assertEquals(
                "el custodio debe ver un mensaje, no silencio",
                R.string.error_captura_fallo,
                vm.uiState.value.error,
            )
            assertFalse("el estado de captura debe soltarse", vm.uiState.value.capturando)
            assertEquals(1, registroDeFallos.registrados.size)
            assertEquals("captura_foto", registroDeFallos.registrados.single().first)
            assertEquals(causa, registroDeFallos.registrados.single().second)
        }

    // RETIRO_ETIQUETADO: se eliminan los tests de descartarEtiquetado/retomarEtiquetado.
    // Esas funciones del ViewModel quedaron comentadas (hoy no hay hoja de etiquetado ni
    // evidencia "sin clasificar" que rescatar). El flujo de reactivación vive en el plan y
    // en los bloques comentados de CapturaViewModel.

    // region arranque y estado de carga (R2 / G3-A)

    @Test
    fun `el estado inicial es cargando sin reporte`() =
        runTest(mainDispatcherRule.dispatcher) {
            // R2: el hub no puede asumir "sin reporte activo" mientras la raíz no haya
            // resuelto el primer valor real. `uiState` arranca en su initialValue.
            val vm = crearViewModel()
            assertTrue("arranca cargando", vm.uiState.value.cargando)
            assertNull("sin que se sepa aún si hay viaje", vm.uiState.value.reporte)
        }

    @Test
    fun `sin viaje activo la primera emision resuelve la carga con reporte nulo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            val estado = vm.uiState.value
            assertFalse("la carga se resuelve con el primer valor real", estado.cargando)
            assertNull("y ahí sí se puede decir que no hay viaje", estado.reporte)
        }

    @Test
    fun `con viaje activo la primera emision resuelve la carga con el reporte`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            val estado = vm.uiState.value
            assertFalse(estado.cargando)
            assertEquals(reporte.id, estado.reporte?.id)
        }

    // endregion

    // region nota de voz (hallazgo E5)

    @Test
    fun `salir de la app pausa la nota y conserva lo grabado`() =
        runTest(mainDispatcherRule.dispatcher) {
            // R2 de §13: seguir grabando en segundo plano exigiría un servicio en primer
            // plano con notificación permanente; dejarla corriendo sin él acaba en audio en
            // silencio cuando el sistema le quita el micrófono.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.iniciarGrabacionDeAudio()
            runCurrent()
            assertTrue(vm.uiState.value.grabandoAudio)

            vm.pausarGrabacionDeAudio()
            runCurrent()

            val estado = vm.uiState.value
            assertTrue("la nota sigue abierta, no se cancela", estado.grabandoAudio)
            assertTrue("y queda marcada como pausada", estado.audioPausado)
            assertEquals(1, grabadora.pausas)
            assertEquals("nada se descarta al pausar", 0, grabadora.descartes)
        }

    @Test
    fun `reanudar continua la misma nota sin perder los segundos ya grabados`() =
        runTest(mainDispatcherRule.dispatcher) {
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.iniciarGrabacionDeAudio()
            runCurrent()
            vm.pausarGrabacionDeAudio()
            runCurrent()

            vm.reanudarGrabacionDeAudio()
            runCurrent()

            assertFalse(vm.uiState.value.audioPausado)
            assertTrue(vm.uiState.value.grabandoAudio)
            assertEquals(1, grabadora.reanudaciones)
            assertEquals("es la misma grabación, no una nueva", 0, grabadora.descartes)

            // Se cierra la nota para que el cronómetro no quede corriendo: su `delay` en
            // bucle no termina nunca en tiempo virtual y `runTest` esperaría para siempre.
            vm.descartarAudio()
            runCurrent()
        }

    @Test
    fun `pausar dos veces no cuenta doble`() =
        runTest(mainDispatcherRule.dispatcher) {
            // ON_STOP puede llegar más de una vez y el observador se re-registra al cambiar
            // el estado de grabación: pausar debe ser idempotente.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.iniciarGrabacionDeAudio()
            runCurrent()

            vm.pausarGrabacionDeAudio()
            runCurrent()
            vm.pausarGrabacionDeAudio()
            runCurrent()

            assertEquals(1, grabadora.pausas)
        }

    @Test
    fun `una nota pausada se puede detener y se guarda`() =
        runTest(mainDispatcherRule.dispatcher) {
            // `stop()` cierra el índice del MPEG-4 incluso viniendo de pausa, así que una
            // nota pausada sí es recuperable. Es la diferencia con la muerte del proceso.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.iniciarGrabacionDeAudio()
            runCurrent()
            vm.pausarGrabacionDeAudio()
            runCurrent()

            vm.detenerGrabacionDeAudio()
            runCurrent()

            val estado = vm.uiState.value
            assertFalse(estado.grabandoAudio)
            assertFalse(estado.audioPausado)
            assertEquals("la nota se persistió", 1, repoMedios.medios.size)
            // RETIRO_ETIQUETADO: ya no hay hoja; la nota persistida es un AUDIO que se encola.
            assertEquals(TipoMedio.AUDIO, repoMedios.medios.single().tipo)
        }

    @Test
    fun `un fallo del grabador descarta la nota lo avisa y lo registra`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Hallazgo E5: sin `setOnErrorListener`, un MEDIA_RECORDER_ERROR_SERVER_DIED
            // dejaba la nota en silencio y la app entregaba ese silencio como evidencia.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.iniciarGrabacionDeAudio()
            runCurrent()

            grabadora.emitirFallo(codigo = 100)
            runCurrent()

            val estado = vm.uiState.value
            assertFalse("la grabación se corta", estado.grabandoAudio)
            assertEquals(
                "el custodio debe verlo, no quedarse esperando",
                R.string.error_grabacion_fallo,
                estado.error,
            )
            assertEquals("la nota corrupta se descarta", 1, grabadora.descartes)
            assertEquals("y queda rastro para diagnosticarlo", 1, registroDeFallos.registrados.size)
            assertEquals("grabacion_audio", registroDeFallos.registrados.single().first)
            assertTrue("no se persiste nada", repoMedios.medios.isEmpty())
        }

    @Test
    fun `alcanzar el limite de cinco minutos guarda la nota en lugar de perderla`() =
        runTest(mainDispatcherRule.dispatcher) {
            // El tope lo impone ahora `setMaxDuration` del grabador, no el cronómetro de la
            // UI: si la corrutina del timer moría, el archivo crecía sin límite (E5).
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.iniciarGrabacionDeAudio()
            runCurrent()

            grabadora.emitirLimiteAlcanzado()
            runCurrent()

            assertFalse(vm.uiState.value.grabandoAudio)
            assertEquals("la nota del tope sí se guarda", 1, repoMedios.medios.size)
            // RETIRO_ETIQUETADO: sin hoja; se verifica el medio persistido, no el estado.
            assertEquals(TipoMedio.AUDIO, repoMedios.medios.single().tipo)
        }

    @Test
    fun `una nota huerfana en cache se avisa al arrancar`() =
        runTest(mainDispatcherRule.dispatcher) {
            // R3 de §13: `pause()` no escribe el índice del MPEG-4, así que una nota
            // interrumpida por muerte del proceso es irrecuperable. Lo único honesto es
            // borrarla y decirlo, en lugar de perderla en silencio.
            grabadora.huerfanasEnCache = 1
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            assertTrue(vm.uiState.value.avisoNotaPerdida)

            vm.consumirAvisoNotaPerdida()
            runCurrent()
            assertFalse("el aviso no debe repetirse en cada visita", vm.uiState.value.avisoNotaPerdida)
        }

    @Test
    fun `sin notas huerfanas no se avisa nada`() =
        runTest(mainDispatcherRule.dispatcher) {
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            assertFalse(vm.uiState.value.avisoNotaPerdida)
        }

    @Test
    fun `si el grabador no puede reanudar se avisa y la nota sigue pausada`() =
        runTest(mainDispatcherRule.dispatcher) {
            // El audio ya grabado no se toca: el mensaje invita a detener para guardarlo.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.iniciarGrabacionDeAudio()
            runCurrent()
            vm.pausarGrabacionDeAudio()
            runCurrent()
            grabadora.puedeReanudar = false

            vm.reanudarGrabacionDeAudio()
            runCurrent()

            val estado = vm.uiState.value
            assertTrue("sigue pausada, no se pierde", estado.audioPausado)
            assertTrue(estado.grabandoAudio)
            assertEquals(R.string.error_grabacion_no_reanudo, estado.error)
        }

    @Test
    fun `una nota que el grabador devuelve vacia se avisa en lugar de desaparecer`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Mismo espíritu que el resto de E5: `detener()` devuelve null cuando el archivo
            // está vacío o la duración es cero. Antes eso era un `return` mudo y la nota se
            // esfumaba; el custodio se iba creyendo que la había dejado grabada.
            seedReporteActivo()
            grabadora.grabacionParaDetener = null
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.iniciarGrabacionDeAudio()
            runCurrent()

            vm.detenerGrabacionDeAudio()
            runCurrent()

            assertEquals(R.string.error_grabacion_vacia, vm.uiState.value.error)
            assertTrue("no se persiste una nota vacía", repoMedios.medios.isEmpty())
            assertNull(vm.uiState.value.medioPendienteDeEtiquetar)
        }

    // endregion

    // region confirmación no visual (G3-C / U3)

    @Test
    fun `capturar dispara el tic de aviso al persistir, sin esperar el GPS`() =
        runTest(mainDispatcherRule.dispatcher) {
            // U3 depende de plataforma (háptica + sonido) y no se asevera en JVM; el único
            // punto testeable es que `avisoEncolado` crece, que es justo lo que la UI observa
            // para tocar el canal no visual ("se enviará en breve"). UX de captura: el tic se
            // dispara al PERSISTIR la foto, ANTES de que el GPS resuelva (hasta 8 s) — no al
            // encolar. Esperar al GPS para avisar hacía sentir la captura lenta.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), 0)
            runCurrent()

            // Aún SIN completar el GPS, el aviso ya se disparó: ese es el arreglo de UX.
            assertEquals(
                "el aviso debe dispararse al persistir la foto, sin esperar el GPS",
                1,
                vm.uiState.value.avisoEncolado,
            )

            // Completar el GPS después no vuelve a tocar el aviso (un tic por captura).
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()
            assertEquals(1, vm.uiState.value.avisoEncolado)
        }

    // endregion

    /** Codec sin Bitmap (imposible en JVM): devuelve bytes marcadores y registra lo que recibe. */
    private class CodecFotoFalso : CodecFoto {
        data class LlamadaRevelar(val rotacion: Int, val marca: MarcaDeAgua?, val mapa: ImagenMapa?)

        val revelados = mutableListOf<LlamadaRevelar>()
        val rotacionesMiniatura = mutableListOf<Int>()
        var fallarMiniatura = false
        var llamadas = 0

        override fun dimensionesFinales(crudo: ByteArray, rotacionGrados: Int): Dimensiones? {
            llamadas++
            return Dimensiones(960, 1280)
        }

        override fun revelar(crudo: ByteArray, rotacionGrados: Int, marca: MarcaDeAgua?, mapa: ImagenMapa?): ByteArray {
            llamadas++
            revelados.add(LlamadaRevelar(rotacionGrados, marca, mapa))
            return REVELADA
        }

        override fun miniatura(crudo: ByteArray, rotacionGrados: Int): ByteArray {
            llamadas++
            rotacionesMiniatura.add(rotacionGrados)
            if (fallarMiniatura) throw OutOfMemoryError("miniatura")
            return MINIATURA
        }

        override fun conOrientacionExif(crudo: ByteArray, rotacionGrados: Int): ByteArray {
            llamadas++
            return crudo
        }

        companion object {
            val REVELADA = ByteArray(2048) { 7 }
            val MINIATURA = byteArrayOf(9, 9)
        }
    }
}
