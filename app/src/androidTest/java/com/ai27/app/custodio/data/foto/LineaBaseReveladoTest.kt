package com.ai27.app.custodio.data.foto

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai27.app.custodio.data.local.media.Miniaturas
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
 * Medición del tratamiento de una foto con el pipeline de `RevelarFoto` (Tasks 5 y 7 de
 * `docs/PLAN_REVELAR_FOTO.md`). La línea base del pipeline viejo (decode + rotación + WebP al
 * disparar, y estampado sobre el WebP) quedó registrada en la Task 1 del plan; esta prueba ya
 * no la reproduce porque esas clases se borraron.
 *
 * Mide con el codec de producción ([BitmapCodecFoto]) y en el mismo orden que la app:
 * - **disparo**: copia de los bytes del HAL (lo que hace `tomarFoto` con el búfer del
 *   `ImageProxy`) y `miniatura(crudo, 90)`;
 * - **revelado**: `revelar(crudo, 90, marca, mapa)`, con un mapa del tamaño real
 *   (`DisposicionMarcaDeAgua.ladoMapaPx` de `dimensionesFinales`) pintado de un color, porque el
 *   snapshot de Google Maps necesita red y aquí solo se mide CPU y memoria. Como en
 *   `RevelarFoto`, `dimensionesFinales` y el mapa quedan fuera del tiempo de revelado.
 *
 * El pico de memoria nativa se muestrea con un hilo aparte cada milisegundo: el codec recicla
 * sus bitmaps antes de devolver, así que medir al terminar cada fase daría casi cero.
 *
 * No es una prueba de comportamiento: solo verifica que las salidas existan y publica los
 * números en logcat (`LineaBaseRevelado`) y en `getExternalFilesDir()/linea-base-revelado.json`,
 * que se lee con `adb shell cat` (o `--directories-to-pull` en Test Lab). La app de prueba es
 * debug: el código Kotlin corre algo más lento que en release, pero el grueso del costo (decode,
 * Skia, libwebp) es nativo y no cambia.
 */
@RunWith(AndroidJUnit4::class)
class LineaBaseReveladoTest {

    private val instrumentacion = InstrumentationRegistry.getInstrumentation()
    private val contextoApp: Context = instrumentacion.targetContext
    private val codec = BitmapCodecFoto(contextoApp.cacheDir)

    @Test
    fun mide_el_pipeline_revelar_foto() {
        val jpegHal = instrumentacion.context.assets.open(FIXTURE).use { it.readBytes() }
        val marca = MarcaDeAgua(
            fechaHora = "2026/11/06 17:30:00",
            coordenadas = "19.432608, -99.133209",
            llevaMapa = true,
        )

        repeat(CALENTAMIENTO) { iteracion(jpegHal, marca) }
        val medidas = List(ITERACIONES) { iteracion(jpegHal, marca) }

        val reporte = JSONObject().apply {
            put("pipeline", "revelar_foto")
            put("dispositivo", dispositivo())
            put("fixture", JSONObject().put("archivo", FIXTURE).put("kb", jpegHal.size / 1024))
            put("iteraciones", ITERACIONES)
            put("disparo_ms", resumen(medidas.map { it.msDisparo }))
            put("revelado_ms", resumen(medidas.map { it.msRevelado }))
            put("total_ms", resumen(medidas.map { it.msDisparo + it.msRevelado }))
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
        val muestreador = MuestreadorNativo(nativoBase).apply { start() }

        // Fase disparo (en producción: tomarFoto copia el búfer y miniaturaRapida en Default).
        val t0 = SystemClock.elapsedRealtimeNanos()
        val crudo = jpegHal.copyOf()
        val miniatura = codec.miniatura(crudo, ROTACION)
        val t1 = SystemClock.elapsedRealtimeNanos()

        // Fase revelado (en producción: RevelarFoto.revelar en Default, tras el GPS).
        val dims = codec.dimensionesFinales(crudo, ROTACION) ?: error("No se pudo leer el fixture")
        val lado = DisposicionMarcaDeAgua.ladoMapaPx(dims.ancho, dims.alto)
        val mapa = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(170, 200, 160))
        }
        val t2 = SystemClock.elapsedRealtimeNanos()
        val final = codec.revelar(crudo, ROTACION, marca, BitmapImagenMapa(mapa))
        val t3 = SystemClock.elapsedRealtimeNanos()

        val pico = muestreador.detener()
        mapa.recycle()

        check(Miniaturas.esWebp(final)) { "La foto revelada no es WebP" }
        return Medida(
            msDisparo = (t1 - t0) / 1_000_000.0,
            msRevelado = (t3 - t2) / 1_000_000.0,
            mbNativoPico = pico / (1024.0 * 1024.0),
            // Se conserva el campo del JSON viejo: el original limpio ahora es la cruda que se guarda.
            kbLimpio = crudo.size / 1024,
            kbMiniatura = miniatura.size / 1024,
            kbFinal = final.size / 1024,
            ladoMapa = lado,
        )
    }

    /** Muestrea el heap nativo por encima de [base] hasta [detener], y devuelve el máximo. */
    private class MuestreadorNativo(private val base: Long) : Thread("muestreador-nativo") {
        @Volatile private var activo = true
        @Volatile private var pico = 0L

        override fun run() {
            while (activo) {
                pico = maxOf(pico, Debug.getNativeHeapAllocatedSize() - base)
                sleep(1)
            }
        }

        fun detener(): Long {
            activo = false
            join()
            return maxOf(pico, Debug.getNativeHeapAllocatedSize() - base)
        }
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
        val msRevelado: Double,
        val mbNativoPico: Double,
        val kbLimpio: Int,
        val kbMiniatura: Int,
        val kbFinal: Int,
        val ladoMapa: Int,
    ) {
        fun aJson(): JSONObject = JSONObject()
            .put("disparo_ms", msDisparo)
            .put("revelado_ms", msRevelado)
            .put("nativo_pico_mb", mbNativoPico)
            .put("lado_mapa_px", ladoMapa)
    }

    private companion object {
        const val ETIQUETA = "LineaBaseRevelado"
        const val FIXTURE = "fixtures/foto_prueba_1600x1200.jpg"
        const val SALIDA = "linea-base-revelado.json"
        const val CALENTAMIENTO = 2
        const val ITERACIONES = 10
        const val ROTACION = 90
    }
}
