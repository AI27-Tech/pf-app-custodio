package com.ai27.app.custodio.ui.pendientes

import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeSesionRepositorio
import com.ai27.app.custodio.FakeSubidaScheduler
import com.ai27.app.custodio.MainDispatcherRule
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.ReintentarSubida
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * R3: el badge deriva de un COUNT (`totalPendientes`), no de la lista. Esta es la única
 * función de conteo que la barra ve; PendientesScreen sigue leyendo la lista en su destino.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendientesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val custodio = Custodio(uid = "c-1", correo = "custodio@ai27.com")

    private fun medio(id: String, estado: EstadoSubida): Medio = Medio(
        id = id,
        reporteId = "r-1",
        tipo = TipoMedio.FOTO,
        origen = OrigenMedio.CAMARA,
        createdAtDevice = Instant.parse("2026-09-21T15:00:00Z"),
        contentType = "image/jpeg",
        estadoSubida = estado,
    )

    private fun crearViewModel(repoMedios: FakeMedioRepository, sesion: FakeSesionRepositorio): PendientesViewModel =
        PendientesViewModel(
            sesionRepositorio = sesion,
            medioRepository = repoMedios,
            reintentarSubida = ReintentarSubida(repoMedios),
            programadorSubidas = FakeSubidaScheduler(),
        )

    @Test
    fun `sin sesion el total del badge es cero`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = crearViewModel(FakeMedioRepository(), FakeSesionRepositorio(null))

        backgroundScope.launch { vm.totalPendientes.collect {} }
        runCurrent()

        assertEquals(0, vm.totalPendientes.value)
    }

    @Test
    fun `el total del badge cuenta los medios no COMPLETA`() = runTest(mainDispatcherRule.dispatcher) {
        val repoMedios = FakeMedioRepository(
            medios = mutableListOf(
                medio("m1", EstadoSubida.PENDIENTE),
                medio("m2", EstadoSubida.EN_PROGRESO),
                medio("m3", EstadoSubida.COMPLETA),
            ),
        )
        val vm = crearViewModel(repoMedios, FakeSesionRepositorio(custodio))

        backgroundScope.launch { vm.totalPendientes.collect {} }
        runCurrent()

        assertEquals(2, vm.totalPendientes.value)
    }

    @Test
    fun `el total del badge se actualiza en vivo cuando un medio termina de subir`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repoMedios = FakeMedioRepository(
                medios = mutableListOf(
                    medio("m1", EstadoSubida.PENDIENTE),
                    medio("m2", EstadoSubida.PENDIENTE),
                ),
            )
            val vm = crearViewModel(repoMedios, FakeSesionRepositorio(custodio))

            backgroundScope.launch { vm.totalPendientes.collect {} }
            runCurrent()
            assertEquals(2, vm.totalPendientes.value)

            repoMedios.marcarSubidaCompleta("m1", "medios/r-1/m1.jpg", "sha-1")
            runCurrent()

            assertEquals(1, vm.totalPendientes.value)
        }
}