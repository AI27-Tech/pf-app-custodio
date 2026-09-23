package com.ai27.app.custodio.contrato

import com.ai27.app.custodio.FakeMedioRepository
import com.ai27.app.custodio.FakeReporteRepository
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import org.junit.Before

/**
 * El mismo contrato contra los fakes que usan las pruebas de dominio, pipeline y
 * ViewModels. Si un fake se separa del comportamiento de Room, estas pruebas fallan.
 *
 * Es la red que faltaba: antes de esto, cuatro divergencias vivían sin detectarse y dos
 * estaban afirmadas como correctas en pruebas verdes (hallazgo P2 de `docs/AUDIT.md`).
 */
class ContratoFakesTest : ContratoRepositoriosTest() {

    override lateinit var medios: MedioRepository
    override lateinit var reportes: ReporteRepository

    @Before
    fun preparar() {
        // C6: el fake de reportes consulta los medios al cerrar (mismo derivación que el
        // UPDATE atómico del DAO), así que necesita la referencia al fake de medios.
        medios = FakeMedioRepository()
        reportes = FakeReporteRepository(mediosConsultables = medios)
    }

    /**
     * El fake de medios no tiene JOIN con reportes, así que la relación medio → custodio
     * se la damos explícitamente. Room no necesita este gancho.
     */
    override fun registrarDuenoDelReporte(reporteId: String, custodioUid: String) {
        (medios as FakeMedioRepository).custodioPorReporte[reporteId] = custodioUid
    }
}
