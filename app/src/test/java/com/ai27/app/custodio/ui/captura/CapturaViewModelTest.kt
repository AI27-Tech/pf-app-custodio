package com.ai27.app.custodio.ui.captura

import com.ai27.app.custodio.FakeEtiquetaRepository
import com.ai27.app.custodio.FakeFotosPublicas
import com.ai27.app.custodio.FakeGrabadora
import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeProveedorUbicacion
import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.FakeSesionRepositorio
import com.ai27.app.custodio.FakeSubidaScheduler
import com.ai27.app.custodio.MainDispatcherRule
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

    private val custodio = Custodio(uid = "c-1", correo = "c@t.mx")
    private lateinit var repoReportes: FakeReporteRepository
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var sesion: FakeSesionRepositorio
    private lateinit var ubicacion: FakeProveedorUbicacion
    private lateinit var grabadora: FakeGrabadora
    private lateinit var fotos: FakeFotosPublicas
    private lateinit var scheduler: FakeSubidaScheduler

    @Before
    fun setUp() {
        repoReportes = FakeReporteRepository()
        repoMedios = FakeMedioRepository()
        sesion = FakeSesionRepositorio(custodio)
        ubicacion = FakeProveedorUbicacion()
        grabadora = FakeGrabadora()
        fotos = FakeFotosPublicas()
        scheduler = FakeSubidaScheduler()
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
            fotosPublicas = fotos,
            programadorSubidas = scheduler,
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

            vm.guardarEtiqueta(medioId, "dano")
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
}
