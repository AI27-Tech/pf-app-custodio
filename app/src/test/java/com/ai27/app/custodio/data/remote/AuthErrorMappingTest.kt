package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.error.DominioError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorMappingTest {

    @Test
    fun `credenciales invalidas mapea a CredencialesInvalidas`() {
        listOf(
            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_USER_NOT_FOUND",
            "ERROR_USER_DISABLED",
            "ERROR_INVALID_EMAIL",
            "ERROR_SIGN_IN_FAILED",
        ).forEach { codigo ->
            assertEquals(
                "Código $codigo debería ser CredencialesInvalidas",
                DominioError.CredencialesInvalidas,
                codigoSesionAError(codigo),
            )
        }
    }

    @Test
    fun `fallo de red mapea a SinConexion`() {
        assertEquals(
            DominioError.SinConexion,
            codigoSesionAError("ERROR_NETWORK_REQUEST_FAILED"),
        )
    }

    @Test
    fun `codigos desconocidos mapean a Inesperado`() {
        val resultado = codigoSesionAError("ERROR_CREDENTIAL_ALREADY_IN_USE")
        assertTrue("Debe ser Inesperado", resultado is DominioError.Inesperado)
    }
}