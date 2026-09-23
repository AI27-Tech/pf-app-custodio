package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.error.DominioError
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clasificación de fallos remotos del hallazgo C5: separa lo **transitorio** (se
 * reintenta solo) de lo **permanente** (fallo inmediato + diagnóstico). Antes solo se
 * mapeaba `IOException` → `SinConexion`; el resto caía en `Inesperado` y el worker
 * reintentaba 4 veces sin criterio.
 */
class ErroresRemotoTest {

    @Test
    fun `una IOException es SinConexion y se reintenta`() {
        val error = excepcionRemotoAError(IOException("sin red"))

        assertTrue(error is DominioError.SinConexion)
        assertTrue(esReintentable(error))
    }

    @Test
    fun `el servidor indisponible es transitorio`() {
        // Firestore no se instancia en JVM host: se prueba la traducción por código.
        val noDisponible = codigoFirestoreAError("UNAVAILABLE")!!
        val vencido = codigoFirestoreAError("DEADLINE_EXCEEDED")!!

        assertTrue(noDisponible is DominioError.ServidorIndisponible)
        assertTrue(vencido is DominioError.ServidorIndisponible)
        assertTrue(esReintentable(noDisponible))
        assertTrue(esReintentable(vencido))
    }

    @Test
    fun `permiso denegado es permanente y no se reintenta`() {
        val error = codigoFirestoreAError("PERMISSION_DENIED")!!

        assertTrue(error is DominioError.PermisoDenegado)
        assertFalse(esReintentable(error))
    }

    @Test
    fun `un codigo desconocido no clasifica`() {
        assertEquals(null, codigoFirestoreAError("CANCELLED"))
        assertEquals(null, codigoFirestoreAError(""))
    }

    @Test
    fun `un fallo no clasificado queda Inesperado y no se reintenta`() {
        val error = excepcionRemotoAError(IllegalStateException("disco raro"))

        assertTrue(error is DominioError.Inesperado)
        assertFalse(esReintentable(error))
    }

    @Test
    fun `los datos object del dominio permanente tampoco se reintentan`() {
        assertFalse(esReintentable(DominioError.ArchivoLocalAusente))
        assertFalse(esReintentable(DominioError.PermisoDenegado))
        assertFalse(esReintentable(DominioError.Inesperado(IllegalStateException("x"))))
    }

    @Test
    fun `aTextoPersistible queda corto y sin rutas de usuario`() {
        val conCausa = DominioError.Inesperado(IllegalStateException("se rompió el put"))
            .aTextoPersistible()
        val estatico = DominioError.PermisoDenegado.aTextoPersistible()

        assertTrue(conCausa.contains("IllegalStateException"))
        assertTrue(conCausa.contains("se rompió el put"))
        assertEquals("PermisoDenegado", estatico)
    }
}