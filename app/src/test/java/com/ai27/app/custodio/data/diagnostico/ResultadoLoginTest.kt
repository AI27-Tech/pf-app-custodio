package com.ai27.app.custodio.data.diagnostico

import com.ai27.app.custodio.domain.error.DominioError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matriz completa de `DominioError` → `ResultadoLogin` (docs/PLAN_ANALYTICS_LOGIN.md,
 * Tarea 5).
 *
 * Es una función PURA sobre tipos del dominio: ni Firebase ni `android.jar` se tocan, así
 * que se puede probar entera en JVM de host — el mismo criterio que
 * `data/remote/AuthErrorMappingTest.kt`, que existe justamente porque los tipos de
 * Firebase no se instancian en pruebas de JVM.
 */
class ResultadoLoginTest {

    /** Los nueve errores que el login sí puede producir, con su desenlace. */
    private val erroresDelLogin = listOf(
        DominioError.NumeroInvalido to ResultadoLogin.NUMERO_INVALIDO,
        DominioError.SinRespuestaSms to ResultadoLogin.SIN_RESPUESTA_SMS,
        DominioError.CodigoInvalido to ResultadoLogin.CODIGO_INVALIDO,
        DominioError.CodigoExpirado to ResultadoLogin.CODIGO_EXPIRADO,
        DominioError.DemasiadosIntentos to ResultadoLogin.DEMASIADOS_INTENTOS,
        DominioError.TelefonoNoAutorizado to ResultadoLogin.TELEFONO_NO_AUTORIZADO,
        DominioError.SinConexion to ResultadoLogin.SIN_CONEXION,
        // El servidor "caído" es el mismo paso medible que "sin red": el custodio ve igual.
        DominioError.ServidorIndisponible to ResultadoLogin.SIN_CONEXION,
        DominioError.Inesperado(RuntimeException()) to ResultadoLogin.INESPERADO,
    )

    /**
     * Las diecinueve variantes restantes. Casi todas son de reporte y de medio y el login
     * no puede producirlas; las dos últimas sí podrían tocarlo (`PermisoDenegado` sale de
     * un PERMISSION_DENIED de Phone Auth, que en pantalla es "Algo salió mal") pero no son
     * un paso medible del embudo.
     */
    private val erroresFueraDelLogin = listOf(
        DominioError.PlacaVacia(null),
        DominioError.YaExisteReporteActivo("rep-1"),
        DominioError.NoHayReporteActivo,
        DominioError.ReporteNoEncontrado,
        DominioError.ReporteCerradoInmutable,
        DominioError.ReporteYaDescartado,
        DominioError.ReporteSinMedios,
        DominioError.MotivoDescarteVacio,
        DominioError.LimiteDeMediosAlcanzado,
        DominioError.SinContenido,
        DominioError.SinEspacioEnDispositivo,
        DominioError.AudioExcedeCincoMinutos,
        DominioError.EtiquetaInvalida("et-1"),
        DominioError.FaltaSituacion,
        DominioError.MedioNoEncontrado,
        DominioError.MedioEnSubida,
        DominioError.MedioYaSubido,
        DominioError.PermisoDenegado,
        DominioError.ArchivoLocalAusente,
    )

    @Test
    fun `cada error del login mapea a su desenlace`() {
        erroresDelLogin.forEach { (error, esperado) ->
            assertEquals("mapeo de $error", esperado, error.aResultadoLogin())
        }
    }

    @Test
    fun `todas las variantes fuera del login caen en inesperado`() {
        erroresFueraDelLogin.forEach { error ->
            assertEquals("mapeo de $error", ResultadoLogin.INESPERADO, error.aResultadoLogin())
        }
    }

    @Test
    fun `la matriz cubre todas las variantes de DominioError`() {
        // El compilador ya obliga a decidir el destino de cada variante nueva, porque
        // `aResultadoLogin()` se escribe sin `else`. Esto cierra el otro hueco: que la
        // nueva variante quede anotada en esta matriz y no se cuelgue en INESPERADO.
        assertEquals(
            "falta o sobra una variante en la matriz: cuéntala en DominioError y anótala aquí",
            TOTAL_VARIANTES_DOMINIO_ERROR,
            erroresDelLogin.size + erroresFueraDelLogin.size,
        )
    }

    @Test
    fun `ningun desenlace ni metodo excede los limites de GA4`() {
        // 100 caracteres para el valor de parámetro, 40 para su nombre.
        val valores = ResultadoLogin.entries.map { it.valor } + MetodoAuth.entries.map { it.valor }
        valores.forEach { valor ->
            assertTrue("'$valor' excede los 100 caracteres del valor", valor.length <= 100)
            assertTrue("'$valor' no es ASCII", valor.all { c -> c.code in 0..127 })
        }
    }

    /** Variantes declaradas en `domain/error/DominioError.kt` a la fecha de este test. */
    private companion object {
        const val TOTAL_VARIANTES_DOMINIO_ERROR = 28
    }
}
