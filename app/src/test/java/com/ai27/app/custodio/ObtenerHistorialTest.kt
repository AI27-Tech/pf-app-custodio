package com.ai27.app.custodio

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CerrarReporte
import com.ai27.app.custodio.domain.usecase.CrearReporte
import com.ai27.app.custodio.domain.usecase.DescartarReporte
import com.ai27.app.custodio.domain.usecase.ObtenerHistorial
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObtenerHistorialTest {

    private val custodio = Custodio(uid = "c-1", correo = "custodio@test.mx")
    private val fijo = Instant.parse("2026-09-18T15:00:00Z")

    @Test
    fun `devuelve solo reportes cerrados del custodio ordenados descendente`() = runBlocking {
        var reloj = fijo
        val repoReportes = FakeReporteRepository()
        val repoMedios = FakeMedioRepository()
        val crear = CrearReporte(repoReportes, ahora = { reloj })
        val capturar = CapturarMedio(repoReportes, repoMedios, ahora = { reloj })
        val cerrar = CerrarReporte(repoReportes, repoMedios, ahora = { reloj })

        val aaa = (crear(custodio, "d1", "c1", "AAA-1111") as Result.Success).value
        capturar("c-1", aaa.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
        cerrar(aaa.id)
        reloj = reloj.plusSeconds(3600)

        val bbb = (crear(custodio, "d1", "c1", "BBB-2222") as Result.Success).value
        capturar("c-1", bbb.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
        cerrar(bbb.id)
        reloj = reloj.plusSeconds(3600)

        val ccc = (crear(custodio, "d1", "c1", "CCC-3333") as Result.Success).value
        capturar("c-1", ccc.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
        cerrar(ccc.id)
        reloj = reloj.plusSeconds(3600)

        CrearReporte(repoReportes, ahora = { reloj })(custodio, "d1", "c1", "DDD-4444")

        val historial = ObtenerHistorial(repoReportes, ahora = { reloj })(custodio.uid).first()

        assertEquals(listOf("CCC3333", "BBB2222", "AAA1111"), historial.map { it.placas })
        assertTrue(historial.all { it.ciclo == Ciclo.CERRADO })

        crear(Custodio("otro", "otro@test.mx"), "d2", "c2", "EEE-5555")
        val historialOtro = ObtenerHistorial(repoReportes, ahora = { reloj })("otro").first()
        assertTrue(historialOtro.isEmpty())
    }

    @Test
    fun `un reporte descartado aparece en el historial con su motivo`() = runBlocking {
        var reloj = fijo
        val repoReportes = FakeReporteRepository()
        val repoMedios = FakeMedioRepository()
        val crear = CrearReporte(repoReportes, ahora = { reloj })
        val capturar = CapturarMedio(repoReportes, repoMedios, ahora = { reloj })
        val cerrar = CerrarReporte(repoReportes, repoMedios, ahora = { reloj })
        val descartar = DescartarReporte(repoReportes, ahora = { reloj })

        val cerrado = (crear(custodio, "d1", "c1", "AAA-1111") as Result.Success).value
        capturar("c-1", cerrado.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
        cerrar(cerrado.id)

        val descartado = (crear(custodio, "d1", "c1", "BBB-2222") as Result.Success).value
        capturar("c-1", descartado.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))
        descartar(descartado.id, "no era este viaje")

        val activo = (crear(custodio, "d1", "c1", "CCC-3333") as Result.Success).value
        capturar("c-1", activo.id, TipoMedio.FOTO, OrigenMedio.CAMARA, ByteArray(1))

        val historial = ObtenerHistorial(repoReportes, ahora = { reloj })(custodio.uid).first()

        assertEquals(
            setOf(Ciclo.CERRADO, Ciclo.DESCARTADO),
            historial.map { it.ciclo }.toSet(),
        )
        assertTrue(historial.none { it.placas == "CCC3333" })
        val descartadoEnHistorial = historial.first { it.ciclo == Ciclo.DESCARTADO }
        assertEquals("no era este viaje", descartadoEnHistorial.motivoDescarte)
    }
}