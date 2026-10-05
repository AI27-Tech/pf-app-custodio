package com.ai27.app.custodio.ui.ajustes

import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeSesionRepositorio
import com.ai27.app.custodio.FakeSubidaScheduler
import com.ai27.app.custodio.MainDispatcherRule
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Cierre de sesión en un equipo compartido (hallazgos E6 y E7 de `docs/AUDIT.md`).
 *
 * Estas pruebas no existían: `AjustesViewModel` no tenía ninguna, y por eso el guard de
 * pendientes llevaba tiempo siendo código muerto sin que nada lo notara.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val custodio = Custodio(uid = "c-1", correo = "custodio@ai27.com")
    private lateinit var sesion: FakeSesionRepositorio
    private lateinit var repoMedios: FakeMedioRepository
    private lateinit var scheduler: FakeSubidaScheduler

    @Before
    fun setUp() {
        sesion = FakeSesionRepositorio(custodio)
        repoMedios = FakeMedioRepository()
        scheduler = FakeSubidaScheduler()
    }

    private fun crearViewModel() = AjustesViewModel(
        sesionRepositorio = sesion,
        medioRepository = repoMedios,
        programadorSubidas = scheduler,
    )

    private fun sembrarMedioPendiente() {
        repoMedios.medios.add(
            Medio(
                id = "m-1",
                reporteId = "r-1",
                tipo = TipoMedio.FOTO,
                origen = OrigenMedio.CAMARA,
                createdAtDevice = Instant.parse("2026-09-21T15:00:00Z"),
                contentType = "image/jpeg",
                estadoSubida = EstadoSubida.PENDIENTE,
                etiquetas = listOf("sin-novedad"),
            ),
        )
        repoMedios.actualizarPendientes()
    }

    @Test
    fun `con archivos sin subir no se abre la confirmacion de cierre`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Hallazgo E7: el guard leía `interno`, que nunca recibe el conteo, así que
            // valía 0 siempre. Lo único que bloqueaba era el botón deshabilitado en la UI;
            // cualquier otro camino a este método cerraba sesión con evidencia sin subir.
            sembrarMedioPendiente()
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            assertEquals("el estado debe ver el pendiente", 1, vm.uiState.value.pendientesSinSubir)

            vm.solicitarCerrarSesion()
            runCurrent()

            assertFalse(
                "con pendientes el guard debe bloquear, no solo el botón",
                vm.uiState.value.confirmacionCerrarSesion,
            )
        }

    @Test
    fun `sin pendientes se abre la confirmacion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.solicitarCerrarSesion()
            runCurrent()

            assertTrue(vm.uiState.value.confirmacionCerrarSesion)
        }

    @Test
    fun `cerrar sesion cancela el trabajo de subida antes del signOut`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Hallazgo E6: `cerrarSesion` era `auth.signOut()` y nada más. Un worker
            // arrancado con el token del custodio saliente seguía corriendo mientras
            // entraba el siguiente, las reglas denegaban sus escrituras y la fila quedaba
            // en FALLIDA — invisible para el nuevo, porque su badge filtra por uid.
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            vm.solicitarCerrarSesion()
            vm.confirmarCerrarSesion()
            runCurrent()

            assertEquals("debe cancelarse el trabajo en vuelo", 1, scheduler.cancelaciones)
            assertEquals("y cerrarse la sesión", 1, sesion.cierres.size)
            assertFalse(vm.uiState.value.cerrandoSesion)
        }

    @Test
    fun `confirmar con pendientes que llegaron despues no cierra la sesion`() =
        runTest(mainDispatcherRule.dispatcher) {
            // El diálogo abre un hueco entre solicitar y confirmar: en ese rato pueden
            // entrar medios nuevos desde otra pestaña, así que se revalida al confirmar.
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()
            vm.solicitarCerrarSesion()
            runCurrent()
            assertTrue(vm.uiState.value.confirmacionCerrarSesion)

            sembrarMedioPendiente()
            runCurrent()
            vm.confirmarCerrarSesion()
            runCurrent()

            assertEquals("no debe cancelarse nada", 0, scheduler.cancelaciones)
            assertTrue("la sesión sigue abierta", sesion.cierres.isEmpty())
        }
}
