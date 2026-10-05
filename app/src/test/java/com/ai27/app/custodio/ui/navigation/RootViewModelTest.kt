package com.ai27.app.custodio.ui.navigation

import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.FakeSesionRepositorio
import com.ai27.app.custodio.domain.conectividad.FakeConectividadMonitor
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.usecase.CrearReporte
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootViewModelTest {

    private val custodio = Custodio(uid = "c-1", correo = "custodio@ai27.com")

    private fun crearViewModel(reportes: FakeReporteRepository) = RootViewModel(
        sesionRepositorio = FakeSesionRepositorio(custodio),
        conectividadMonitor = FakeConectividadMonitor(),
        reporteRepository = reportes,
    )

    @Test
    fun `con viaje activo la sonda de arranque abre en camara`() =
        runTest {
            // G3-A/R1: la app debe arrancar en el visor cuando hay un reporte ACTIVO.
            val reportes = FakeReporteRepository()
            CrearReporte(reportes).invoke(custodio, "dev-test", "cli", "ABC-1234")
            val vm = crearViewModel(reportes)

            assertTrue("hayReporteActivo debe ver el viaje activo", vm.hayReporteActivo(custodio.uid))
        }

    @Test
    fun `sin viaje activo la sonda de arranque se queda en el hub`() =
        runTest {
            // Si no hay viaje activo, abrir la cámara no tiene sentido: el destino es INICIO.
            val reportes = FakeReporteRepository()
            val vm = crearViewModel(reportes)

            assertFalse("sin viaje la sonda debe ser false", vm.hayReporteActivo(custodio.uid))
        }

    @Test
    fun `la sonda ignora los reportes de otro custodio`() =
        runTest {
            val reportes = FakeReporteRepository()
            val vm = crearViewModel(reportes)
            val otroCustodio = Custodio(uid = "c-2", correo = "otro@ai27.com")
            CrearReporte(reportes).invoke(otroCustodio, "dev-test", "cli", "XYZ-0000")

            assertFalse(vm.hayReporteActivo(custodio.uid))
            assertTrue(vm.hayReporteActivo(otroCustodio.uid))
        }
}