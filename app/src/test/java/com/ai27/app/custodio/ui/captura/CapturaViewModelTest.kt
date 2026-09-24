package com.ai27.app.custodio.ui.captura

import com.ai27.app.custodio.FakeEtiquetaRepository
import com.ai27.app.custodio.FakeGrabadora
import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeProveedorUbicacion
import com.ai27.app.custodio.FakeRegistroDeFallos
import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.FakeSesionRepositorio
import com.ai27.app.custodio.FakeSubidaScheduler
import com.ai27.app.custodio.MainDispatcherRule
import com.ai27.app.custodio.R
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    private val custodio = Custodio(uid = "c-1", telefono = "+525544880512")
    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var sesion: FakeSesionRepositorio
    private lateinit var ubicacion: FakeProveedorUbicacion
    private lateinit var grabadora: FakeGrabadora
    private lateinit var scheduler: FakeSubidaScheduler
    private lateinit var registroDeFallos: FakeRegistroDeFallos

    @Before
    fun setUp() {
        repoReportes = FakeReporteRepository()
        repoMedios = FakeMedioRepository()
        sesion = FakeSesionRepositorio(custodio)
        ubicacion = FakeProveedorUbicacion()
        grabadora = FakeGrabadora()
        scheduler = FakeSubidaScheduler()
        registroDeFallos = FakeRegistroDeFallos()
    }

    private fun crearViewModel(): CapturaViewModel {
        val etiquetas = FakeEtiquetaRepository()
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
            grabadora = grabadora,
            programadorSubidas = scheduler,
            registroDeFallos = registroDeFallos,
            deviceId = "dev-test",
        )
    }

    private suspend fun seedReporteActivo(): Reporte =
        (CrearReporte(repoReportes).invoke(custodio, "dev-test", "cli", "ABC-1234") as Result.Success).value

    @Test
    fun `la captura de foto no espera el GPS para abrir la hoja de etiquetado`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent() // el GPS sigue sin resolverse (puerta abierta)

            val estado = vm.uiState.value
            assertEquals(reporte.id, estado.reporte?.id)
            // La foto ya se persistió y la hoja está lista, sin esperar la ubicación.
            assertEquals(1, repoMedios.medios.size)
            assertFalse(estado.capturando)
            assertEquals(estado.medioPendienteDeEtiquetar?.id, repoMedios.medios.single().id)
            assertNull(repoMedios.medios.single().gps)
            // Nada se encola todavía porque aún no se etiqueta.
            assertTrue(scheduler.encolados.isEmpty())
        }

    @Test
    fun `la subida se encola solo despues de adjuntar el GPS`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            val medioId = repoMedios.medios.single().id

            vm.guardarEtiqueta(medioId, listOf("sin-novedad"))
            runCurrent() // etiqueta aplicada y hoja cerrada, pero el encolado espera al GPS

            assertNull(vm.uiState.value.medioPendienteDeEtiquetar)
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
            assertEquals(TipoMedio.AUDIO, estado.medioPendienteDeEtiquetar?.tipo)
            assertEquals(1, repoMedios.medios.size)
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

    @Test
    fun `descartar el etiquetado deja el medio visible como sin etiquetar`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Hallazgo E3: cerrar la hoja con la X dejaba el medio sin situación y
            // sin ninguna forma de llegar a él. Ahora la rejilla lo puede señalar porque
            // el estado lo expone, y la cola no lo sube hasta que se clasifique.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            vm.descartarEtiquetado()
            runCurrent()

            val estado = vm.uiState.value
            assertNull("la hoja se cerró", estado.medioPendienteDeEtiquetar)
            assertEquals(
                "el medio sigue ahí y la UI lo puede señalar",
                1,
                estado.mediosSinEtiquetar.size,
            )
            assertTrue(
                "no se encola nada: sin etiqueta no se sube",
                scheduler.encolados.isEmpty(),
            )
        }

    @Test
    fun `retomar el etiquetado reabre la hoja de un medio ya guardado`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Es la salida de E3 que no existía: `medioPendienteDeEtiquetar` vive en
            // memoria, así que tras muerte de proceso la hoja no reabría nunca y la
            // evidencia quedaba atrapada.
            val reporte = seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            val medioId = repoMedios.medios.single().id
            vm.descartarEtiquetado()
            runCurrent()

            vm.retomarEtiquetado(medioId)
            runCurrent()

            assertEquals(medioId, vm.uiState.value.medioPendienteDeEtiquetar?.id)

            vm.guardarEtiqueta(medioId, listOf("sin-novedad"))
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            val estado = vm.uiState.value
            assertNull(estado.medioPendienteDeEtiquetar)
            assertTrue("ya no hay nada sin etiquetar", estado.mediosSinEtiquetar.isEmpty())
            assertEquals(
                "etiquetado al fin, la subida se encola",
                listOf(reporte.id to medioId),
                scheduler.encolados,
            )
        }

    @Test
    fun `retomar el etiquetado ignora un medio que ya tiene etiqueta`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Reetiquetar no está en el alcance y la subida pudo haber salido ya: reabrir
            // la hoja sobre un medio etiquetado solo produciría una escritura ambigua.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            val medioId = repoMedios.medios.single().id
            vm.guardarEtiqueta(medioId, listOf("sin-novedad"))
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            vm.retomarEtiquetado(medioId)
            runCurrent()

            assertNull(
                "un medio ya etiquetado no vuelve a la hoja",
                vm.uiState.value.medioPendienteDeEtiquetar,
            )
        }

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
            assertEquals(TipoMedio.AUDIO, estado.medioPendienteDeEtiquetar?.tipo)
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
            assertEquals(TipoMedio.AUDIO, vm.uiState.value.medioPendienteDeEtiquetar?.tipo)
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

    // region herencia de Situación (G3-B / D-B1, U2)

    @Test
    fun `guardar etiqueta recuerda la situacion para el siguiente medio`() =
        runTest(mainDispatcherRule.dispatcher) {
            // U2: el custodio ya sabe qué pasó en la foto anterior; la hoja siguiente debe
            // arrancar con esa última Situación confirmada, no vacía.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            vm.guardarEtiqueta(repoMedios.medios.single().id, listOf("sin-novedad", "accidente"))
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            assertEquals(
                "solo la Situación se hereda; la Evidencia es opcional y no se presume",
                listOf("sin-novedad"),
                vm.uiState.value.ultimaSituacion,
            )
        }

    @Test
    fun `la ultima situacion sobrevive al capturar otro medio`() =
        runTest(mainDispatcherRule.dispatcher) {
            // El estado no se resetea al volcar una nueva captura: la hoja del segundo
            // medio se compone desde `ultimaSituacion` y encuentra la heredada.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            vm.guardarEtiqueta(repoMedios.medios.single().id, listOf("sin-novedad"))
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()

            assertEquals(listOf("sin-novedad"), vm.uiState.value.ultimaSituacion)
        }

    @Test
    fun `la herencia se renueva con la situacion de la ultima confirmacion`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Si la última vez eligió otra Situación, la herencia le sigue el ritmo: lo que
            // se recuerda es la última confirmada, no la primera.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            vm.guardarEtiqueta(repoMedios.medios.single().id, listOf("sin-novedad"))
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            vm.guardarEtiqueta(repoMedios.medios.last().id, listOf("con-novedad", "otro"))
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            assertEquals(
                "se recuerda la última Situación, con su evidencia opcional al lado",
                listOf("con-novedad"),
                vm.uiState.value.ultimaSituacion,
            )
        }

    // endregion

    // region confirmación no visual (G3-C / U3)

    @Test
    fun `guardar etiqueta incrementa el contador que dispara el tic de encolado`() =
        runTest(mainDispatcherRule.dispatcher) {
            // U3 depende de plataforma (háptica + sonido) y no se asevera en JVM; el único
            // punto testeable es que `avisoEncolado` crece cuando la subida se encola, que es
            // justo lo que la UI observa para tocar el canal no visual.
            seedReporteActivo()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.capturarFoto(ByteArray(32), ByteArray(8))
            runCurrent()
            vm.guardarEtiqueta(repoMedios.medios.single().id, listOf("sin-novedad"))
            runCurrent()
            ubicacion.completar(Gps(lat = 10.0, lng = 20.0, accuracyM = 5.0, ageMs = 100L))
            runCurrent()

            assertEquals(1, vm.uiState.value.avisoEncolado)
        }

    // endregion
}
