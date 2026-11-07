package com.ai27.app.custodio.data.foto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai27.app.custodio.data.local.media.Miniaturas
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El codec real sobre `Bitmap`, que no corre en JVM: tamaños finales, formato, EXIF y que la
 * marca se dibuje sobre el mismo bitmap revelado.
 */
@RunWith(AndroidJUnit4::class)
class BitmapCodecFotoTest {

    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val codec = BitmapCodecFoto(instrumentacion.targetContext.cacheDir)

    private fun fixture(): ByteArray =
        instrumentacion.context.assets.open("fixtures/foto_prueba_1600x1200.jpg").use { it.readBytes() }

    private fun jpegDeColor(ancho: Int, alto: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            .also { bitmap.recycle() }
    }

    private fun decodificar(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("No se pudo decodificar la salida")

    @Test
    fun revelar_con_rotacion_90_da_un_webp_de_960x1280() {
        val webp = codec.revelar(fixture(), 90, marca = null, mapa = null)

        assertTrue(Miniaturas.esWebp(webp))
        val bitmap = decodificar(webp)
        assertEquals(960, bitmap.width)
        assertEquals(1280, bitmap.height)
    }

    @Test
    fun dimensiones_finales_del_fixture_rotado_son_960x1280() {
        assertEquals(Dimensiones(960, 1280), codec.dimensionesFinales(fixture(), 90))
        assertEquals(Dimensiones(1280, 960), codec.dimensionesFinales(fixture(), 180))
    }

    @Test
    fun revelar_un_jpeg_grande_acota_el_lado_largo_a_1280() {
        val grande = jpegDeColor(2592, 1944, Color.GRAY)

        val bitmap = decodificar(codec.revelar(grande, 0, marca = null, mapa = null))

        assertEquals(1280, bitmap.width)
        assertEquals(960, bitmap.height)
        assertEquals(Dimensiones(960, 1280), codec.dimensionesFinales(grande, 270))
    }

    @Test
    fun miniatura_es_un_webp_de_lado_largo_320() {
        val miniatura = codec.miniatura(fixture(), 90)

        assertTrue(Miniaturas.esWebp(miniatura))
        val bitmap = decodificar(miniatura)
        assertEquals(240, bitmap.width)
        assertEquals(320, bitmap.height)
    }

    @Test
    fun con_orientacion_exif_90_escribe_rotate_90() {
        val conExif = codec.conOrientacionExif(fixture(), 90)

        assertTrue(Miniaturas.esJpeg(conExif))
        val orientacion = ExifInterface(ByteArrayInputStream(conExif))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, orientacion)
    }

    @Test
    fun dimensiones_finales_de_bytes_basura_es_null() {
        assertNull(codec.dimensionesFinales(ByteArray(256) { it.toByte() }, 0))
    }

    @Test
    fun revelar_con_marca_dibuja_la_fecha_arriba_a_la_izquierda() {
        val rojo = jpegDeColor(1600, 1200, Color.RED)
        val marca = MarcaDeAgua(fechaHora = "2026/01/05 09:03:07", coordenadas = null, llevaMapa = false)

        val sinMarca = decodificar(codec.revelar(rojo, 0, marca = null, mapa = null))
        val conMarca = decodificar(codec.revelar(rojo, 0, marca, mapa = null))

        assertTrue("Sin marca la esquina debe quedar roja", !hayPixelNoRojizo(sinMarca, 0, 0, 800, 200))
        assertTrue("Con marca debe haber tinta arriba-izquierda", hayPixelNoRojizo(conMarca, 0, 0, 800, 200))
        assertTrue("El centro queda intacto", esRojizo(conMarca.getPixel(800, 600)))
    }

    private fun esRojizo(color: Int): Boolean =
        Color.red(color) > 180 && Color.green(color) < 70 && Color.blue(color) < 70

    private fun hayPixelNoRojizo(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        for (x in x0 until x1) {
            for (y in y0 until y1) {
                if (!esRojizo(bitmap.getPixel(x, y))) return true
            }
        }
        return false
    }
}
