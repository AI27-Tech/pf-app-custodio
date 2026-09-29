package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.error.DominioError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan de correo, Task 6: el mapeo de errores de Firebase Auth es una función PURA
 * ([mapearErrorAutenticacion]) sobre tipo de excepción + código del backend. Los tipos
 * concretos de Firebase no se instancian en JVM de host (el android.jar de pruebas lanza
 * "not mocked" en sus constructores), así que la traducción se prueba por su matriz
 * (tipo, código) y el pegamento de [excepcionSesionAError] queda como código fino sin probar.
 */
class AuthErrorMappingTest {

    private fun mapear(tipo: TipoErrorAuth, codigo: String?) = mapearErrorAutenticacion(tipo, codigo)

    @Test
    fun `correo mal formado mapea a CredencialesInvalidas`() {
        assertEquals(
            DominioError.CredencialesInvalidas,
            mapear(TipoErrorAuth.AUTENTICACION, "ERROR_INVALID_EMAIL"),
        )
    }

    @Test
    fun `contrasena incorrecta mapea a CredencialesInvalidas`() {
        assertEquals(
            DominioError.CredencialesInvalidas,
            mapear(TipoErrorAuth.AUTENTICACION, "ERROR_WRONG_PASSWORD"),
        )
    }

    @Test
    fun `credenciales invalidas y usuario inexistente mapean a lo mismo`() {
        // El par del login se fusiona a propósito: distinguir "no existe" de "contraseña
        // incorrecta" permitiría enumerar las cuentas registradas.
        assertEquals(
            DominioError.CredencialesInvalidas,
            mapear(TipoErrorAuth.AUTENTICACION, "ERROR_INVALID_LOGIN_CREDENTIALS"),
        )
        assertEquals(
            DominioError.CredencialesInvalidas,
            mapear(TipoErrorAuth.AUTENTICACION, "ERROR_USER_NOT_FOUND"),
        )
    }

    @Test
    fun `cuenta deshabilitada mapea a CuentaDeshabilitada`() {
        assertEquals(
            DominioError.CuentaDeshabilitada,
            mapear(TipoErrorAuth.AUTENTICACION, "ERROR_USER_DISABLED"),
        )
    }

    @Test
    fun `demasiados intentos mapea a DemasiadosIntentos`() {
        assertEquals(
            "Por la clase de excepción (FirebaseTooManyRequestsException)",
            DominioError.DemasiadosIntentos,
            mapear(TipoErrorAuth.DEMASIADOS, null),
        )
        assertEquals(
            "Por el código del backend",
            DominioError.DemasiadosIntentos,
            mapear(TipoErrorAuth.AUTENTICACION, "ERROR_TOO_MANY_REQUESTS"),
        )
    }

    @Test
    fun `fallo de red mapea a SinConexion`() {
        assertEquals(
            "Por la clase de excepción (FirebaseNetworkException)",
            DominioError.SinConexion,
            mapear(TipoErrorAuth.RED, null),
        )
        assertEquals(
            "Por el código del backend",
            DominioError.SinConexion,
            mapear(TipoErrorAuth.AUTENTICACION, "ERROR_NETWORK_REQUEST_FAILED"),
        )
    }

    @Test
    fun `codigos desconocidos mapean a Inesperado`() {
        val resultado = mapear(TipoErrorAuth.AUTENTICACION, "ERROR_CREDENTIAL_ALREADY_IN_USE")
        assertTrue("Debe ser Inesperado", resultado is DominioError.Inesperado)
    }
}
