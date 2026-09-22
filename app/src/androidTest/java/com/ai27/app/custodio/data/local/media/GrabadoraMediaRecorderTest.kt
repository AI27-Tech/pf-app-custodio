package com.ai27.app.custodio.data.local.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Barrido de notas huérfanas de [GrabadoraMediaRecorder] contra el disco real.
 *
 * No graba: eso exige el micrófono y permiso en tiempo de ejecución. Lo que se fija aquí
 * es la parte comprobable sin hardware, que además es la que sostiene el aviso de R3
 * (§13 de `docs/PLAN.md`): un `.m4a` suelto en caché es la prueba de que el proceso murió
 * a media nota, porque `pause()` no escribe el índice del MPEG-4 y ese audio ya no se
 * puede leer.
 */
@RunWith(AndroidJUnit4::class)
class GrabadoraMediaRecorderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var grabadora: GrabadoraMediaRecorder
    private val creados = mutableListOf<File>()

    @Before
    fun preparar() {
        grabadora = GrabadoraMediaRecorder(context)
        limpiarNotas()
    }

    @After
    fun limpiar() {
        creados.forEach { it.delete() }
        limpiarNotas()
    }

    private fun limpiarNotas() {
        context.cacheDir.listFiles { archivo ->
            archivo.isFile && archivo.name.startsWith("nota-") && archivo.name.endsWith(".m4a")
        }?.forEach { it.delete() }
    }

    private fun notaSuelta(nombre: String): File =
        File(context.cacheDir, nombre).also {
            it.writeBytes(ByteArray(32))
            creados.add(it)
        }

    @Test
    fun barrerNotasHuerfanas_borra_las_notas_sueltas_y_las_cuenta() = runBlocking {
        val una = notaSuelta("nota-1000.m4a")
        val otra = notaSuelta("nota-2000.m4a")

        val barridas = grabadora.barrerNotasHuerfanas()

        assertEquals("debe contar las dos para poder avisar", 2, barridas)
        assertFalse("una nota que no se puede leer no debe quedarse ocupando caché", una.exists())
        assertFalse(otra.exists())
    }

    @Test
    fun barrerNotasHuerfanas_no_toca_otros_archivos_de_cache() = runBlocking {
        val ajeno = File(context.cacheDir, "otra-cosa.txt").also {
            it.writeBytes(ByteArray(8))
            creados.add(it)
        }
        val recon = File(context.cacheDir, "nota-de-otra-cosa.txt").also {
            it.writeBytes(ByteArray(8))
            creados.add(it)
        }

        val barridas = grabadora.barrerNotasHuerfanas()

        assertEquals(0, barridas)
        assertTrue("el barrido no es una limpieza general de caché", ajeno.exists())
        assertTrue("solo cuentan los .m4a con el prefijo de nota", recon.exists())
    }

    @Test
    fun barrerNotasHuerfanas_sin_nada_devuelve_cero() = runBlocking {
        assertEquals(0, grabadora.barrerNotasHuerfanas())
    }

    @Test
    fun el_estado_inicial_no_esta_grabando_ni_pausado() {
        assertFalse(grabadora.grabando)
        assertFalse(grabadora.pausada)
        assertEquals(0L, grabadora.duracionActualMs())
    }

    @Test
    fun pausar_y_reanudar_sin_grabacion_no_hacen_nada() = runBlocking {
        // Los guardas importan porque la pausa la dispara el ciclo de vida, no el custodio:
        // un ON_STOP con la pantalla abierta y sin nota no debe tocar nada.
        grabadora.pausar()
        assertFalse(grabadora.pausada)
        assertFalse("reanudar sin nada que reanudar debe fallar limpio", grabadora.reanudar())
    }
}
