package com.ai27.app.custodio.data.local.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo del dibujo real sobre `Bitmap`/`Canvas` (no corre en JVM). Verifica que la
 * marca se graba en las esquinas correctas: se parte de un bitmap de un solo color y se
 * comprueba que esas esquinas cambiaron, mientras el centro queda intacto.
 */
@RunWith(AndroidJUnit4::class)
class DibujanteMarcaDeAguaTest {

    private val anchoPrueba = 1200
    private val altoPrueba = 900

    private fun lienzoRojo(): Bitmap =
        Bitmap.createBitmap(anchoPrueba, altoPrueba, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }

    private fun decodificar(jpeg: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: error("El JPEG estampado no se pudo decodificar")

    private fun esRojizo(color: Int): Boolean =
        Color.red(color) > 180 && Color.green(color) < 70 && Color.blue(color) < 70

    @Test
    fun `estampa fecha arriba-izquierda y coordenadas abajo-derecha`() {
        val marca = MarcaDeAgua(
            fechaHora = "2026/01/05 09:03:07",
            coordenadas = "19.432608, -99.133209",
            llevaMapa = false,
        )

        val jpeg = DibujanteMarcaDeAgua.estampar(lienzoRojo(), marca, mapa = null)
        val resultado = decodificar(jpeg)

        assertNotNull(resultado)
        // La fecha ocupa la zona superior izquierda: ahí debe haber tinta no roja.
        assertTrue(
            "Debe haber marca arriba-izquierda (fecha)",
            hayPixelNoRojizo(resultado, 0, 0, resultado.width / 2, resultado.height / 6),
        )
        // Las coordenadas ocupan la zona inferior derecha.
        assertTrue(
            "Debe haber marca abajo-derecha (coordenadas)",
            hayPixelNoRojizo(
                resultado,
                resultado.width / 2, resultado.height * 5 / 6,
                resultado.width, resultado.height,
            ),
        )
        // El centro de la foto no se toca.
        assertTrue(
            "El centro de la foto debe quedar intacto",
            esRojizo(resultado.getPixel(resultado.width / 2, resultado.height / 2)),
        )
    }

    @Test
    fun `sin coordenadas no marca la esquina inferior derecha`() {
        val marca = MarcaDeAgua(fechaHora = "2026/01/05 09:03:07", coordenadas = null, llevaMapa = false)

        val resultado = decodificar(DibujanteMarcaDeAgua.estampar(lienzoRojo(), marca, mapa = null))

        // La esquina inferior derecha queda roja: no se dibujaron coordenadas.
        assertFalse(
            "Sin coordenadas la esquina inferior derecha no debe tener marca",
            hayPixelNoRojizo(
                resultado,
                resultado.width * 3 / 4, resultado.height * 7 / 8,
                resultado.width, resultado.height,
            ),
        )
    }

    @Test
    fun `con mapa dibuja el tile abajo-izquierda`() {
        val marca = MarcaDeAgua(
            fechaHora = "2026/01/05 09:03:07",
            coordenadas = "19.432608, -99.133209",
            llevaMapa = true,
        )
        val mapa = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }

        val resultado = decodificar(DibujanteMarcaDeAgua.estampar(lienzoRojo(), marca, mapa = mapa))

        // Abajo-izquierda ahora hay azul del tile del mapa.
        var hayAzul = false
        for (x in 0 until resultado.width / 3) {
            for (y in resultado.height * 2 / 3 until resultado.height) {
                val c = resultado.getPixel(x, y)
                if (Color.blue(c) > 150 && Color.red(c) < 90) {
                    hayAzul = true
                }
            }
        }
        assertTrue("El tile del mapa debe aparecer abajo-izquierda", hayAzul)
    }

    private fun hayPixelNoRojizo(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        for (x in x0 until x1) {
            for (y in y0 until y1) {
                if (!esRojizo(bitmap.getPixel(x, y))) return true
            }
        }
        return false
    }
}
