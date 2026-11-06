package com.ai27.app.custodio.data.foto

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai27.app.custodio.data.local.media.Miniaturas
import com.ai27.app.custodio.data.marcaDeAgua.BitmapEstampadorFoto
import com.ai27.app.custodio.data.marcaDeAgua.BitmapImagenMapa
import com.ai27.app.custodio.domain.marcaDeAgua.DisposicionMarcaDeAgua
import com.ai27.app.custodio.domain.marcaDeAgua.MarcaDeAgua
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Línea base del tratamiento de una foto (Task 1 de `docs/PLAN_REVELAR_FOTO.md`).
 *
 * Mide el pipeline ACTUAL con las mismas clases de producción y en el mismo orden:
 * - **disparo**: decode del JPEG del HAL, rotación de 90° (lo que hace `VistaCamara.aBitmap`,
 *   que es privada y aquí se replica tal cual) y `Miniaturas.fotoOriginalYMiniatura`;
 * - **marca**: `BitmapEstampadorFoto.estampar` sobre el WebP ya guardado, con un mapa del
 *   tamaño real (`DisposicionMarcaDeAgua.ladoMapaPx`) pintado de un color, porque el snapshot de
 *   Google Maps necesita red y aquí solo se mide CPU y memoria.
 *
 * No es una prueba de comportamiento: solo verifica que las salidas existan y publica los
 * números en logcat (`LineaBaseRevelado`) y en `getExternalFilesDir()/linea-base-revelado.json`,
 * que Firebase Test Lab recupera con `--directories-to-pull`. Se corre igual antes y después del
 * refactor para comparar. La app de prueba es debug: el código Kotlin corre algo más lento que en
 * release, pero el grueso del costo (decode, Skia, libwebp) es nativo y no cambia.
 */
@RunWith(AndroidJUnit4::class)
class LineaBaseReveladoTest {

    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val contextoApp: Context = instrumentacion.targetContext

    @Test
    fun mide_el_pipeline_actual_de_una_foto() {
        val jpegHal = instrumentacion.context.assets.open(FIXTURE).use { it.readBytes() }
        val marca = MarcaDeAgua(
            fechaHora = "2026/11/06 17:30:00",
            coordenadas = "19.432608, -99.133209",
            llevaMapa = true,
        )

        repeat(CALENTAMIENTO) { iteracion(jpegHal, marca) }
        val medidas = List(ITERACIONES) { iteracion(jpegHal, marca) }

        val reporte = JSONObject().apply {
            put("pipeline", "actual")
            put("dispositivo", dispositivo())
            put("fixture", JSONObject().put("archivo", FIXTURE).put("kb", jpegHal.size / 1024))
            put("iteraciones", ITERACIONES)
            put("disparo_ms", resumen(medidas.map { it.msDisparo }))
            put("marca_ms", resumen(medidas.map { it.msMarca }))
            put("total_ms", resumen(medidas.map { it.msDisparo + it.msMarca }))
            put("nativo_pico_mb", resumen(medidas.map { it.mbNativoPico }))
            put("kb_original_limpio", medidas.last().kbLimpio)
            put("kb_miniatura", medidas.last().kbMiniatura)
            put("kb_final", medidas.last().kbFinal)
            put("crudo", JSONArray(medidas.map { it.aJson() }))
        }

        val texto = reporte.toString(2)
        Log.i(ETIQUETA, texto)
        contextoApp.getExternalFilesDir(null)?.let { File(it, SALIDA).writeText(texto) }

        assertTrue("El WebP final debe existir", medidas.all { it.kbFinal > 0 })
    }

    private fun iteracion(jpegHal: ByteArray, marca: MarcaDeAgua): Medida {
        Runtime.getRuntime().gc()
        val nativoBase = Debug.getNativeHeapAllocatedSize()
        var pico = 0L
        fun muestrear() {
            pico = maxOf(pico, Debug.getNativeHeapAllocatedSize() - nativoBase)
        }

        // Fase disparo (en producción: callback de CameraX en Dispatchers.Default).
        val t0 = SystemClock.elapsedRealtimeNanos()
        val decodificado = BitmapFactory.decodeByteArray(jpegHal, 0, jpegHal.size)
            ?: error("No se pudo decodificar el fixture")
        val rotado = Bitmap.createBitmap(
            decodificado, 0, 0, decodificado.width, decodificado.height,
            Matrix().apply { postRotate(90f) }, true,
        )
        muestrear() // en producción los dos bitmaps siguen vivos aquí
        val (limpio, miniatura) = Miniaturas.fotoOriginalYMiniatura(rotado)
        muestrear()
        val t1 = SystemClock.elapsedRealtimeNanos()

        // Fase marca (en producción: hoy en el hilo principal).
        val estampador = BitmapEstampadorFoto()
        val lado = estampador.ladoMapaPx(limpio)
        val mapa = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(170, 200, 160))
        }
        val t2 = SystemClock.elapsedRealtimeNanos()
        val final = estampador.estampar(limpio, marca, BitmapImagenMapa(mapa))
        muestrear()
        val t3 = SystemClock.elapsedRealtimeNanos()

        // Higiene entre iteraciones: producción no recicla, pero la medición debe ser independiente.
        decodificado.recycle()
        rotado.recycle()
        mapa.recycle()

        check(Miniaturas.esWebp(final)) { "La salida de la marca no es WebP" }
        return Medida(
            msDisparo = (t1 - t0) / 1_000_000.0,
            msMarca = (t3 - t2) / 1_000_000.0,
            mbNativoPico = pico / (1024.0 * 1024.0),
            kbLimpio = limpio.size / 1024,
            kbMiniatura = miniatura.size / 1024,
            kbFinal = final.size / 1024,
            ladoMapa = DisposicionMarcaDeAgua.ladoMapaPx(rotado.width, rotado.height),
        )
    }

    private fun dispositivo(): JSONObject {
        val am = contextoApp.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return JSONObject().apply {
            put("fabricante", Build.MANUFACTURER)
            put("modelo", Build.MODEL)
            put("hardware", Build.HARDWARE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) put("soc", Build.SOC_MODEL)
            put("api", Build.VERSION.SDK_INT)
            put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            put("ram_baja", am.isLowRamDevice)
            put("heap_mb", am.memoryClass)
            put("nucleos", Runtime.getRuntime().availableProcessors())
        }
    }

    private fun resumen(valores: List<Double>): JSONObject {
        val ordenados = valores.sorted()
        fun percentil(p: Double) = ordenados[((ordenados.size - 1) * p).toInt()]
        return JSONObject()
            .put("min", ordenados.first())
            .put("mediana", percentil(0.5))
            .put("p90", percentil(0.9))
            .put("max", ordenados.last())
    }

    private data class Medida(
        val msDisparo: Double,
        val msMarca: Double,
        val mbNativoPico: Double,
        val kbLimpio: Int,
        val kbMiniatura: Int,
        val kbFinal: Int,
        val ladoMapa: Int,
    ) {
        fun aJson(): JSONObject = JSONObject()
            .put("disparo_ms", msDisparo)
            .put("marca_ms", msMarca)
            .put("nativo_pico_mb", mbNativoPico)
            .put("lado_mapa_px", ladoMapa)
    }

    private companion object {
        const val ETIQUETA = "LineaBaseRevelado"
        const val FIXTURE = "fixtures/foto_prueba_1600x1200.jpg"
        const val SALIDA = "linea-base-revelado.json"
        const val CALENTAMIENTO = 2
        const val ITERACIONES = 10
    }
}
