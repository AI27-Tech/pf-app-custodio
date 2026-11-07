package com.ai27.app.custodio.data.foto

import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.marcaDeAgua.DisposicionMarcaDeAgua
import com.ai27.app.custodio.domain.marcaDeAgua.FormatoMarcaDeAgua
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Desenlace de [RevelarFoto.revelar]. `OK`, `SIN_MARCA` y `FALLO_DECODE` son los de la telemetría (D11). */
enum class ResultadoRevelado { OK, SIN_MARCA, FALLO_DECODE, YA_REVELADA, NO_APLICA }

/**
 * Lo que pasó al revelar. Los campos numéricos alimentan el evento `foto_revelada` (D11) que
 * conecta la Task 5; aquí solo se calculan.
 */
data class Revelado(
    val resultado: ResultadoRevelado,
    val msRevelado: Long = 0,
    val conGps: Boolean = false,
    val conMapa: Boolean = false,
    val kbFinal: Int = 0,
)

/**
 * Único módulo que trata la imagen de una foto de evidencia (`docs/PLAN_REVELAR_FOTO.md`).
 *
 * Glosario (D7):
 * - **foto cruda**: los bytes JPEG del HAL tal como llegan, sin rotar ni marcar.
 * - **revelar**: decode → rotación → marca → WebP, una sola vez.
 * - **foto revelada**: el WebP final que se sube.
 *
 * El hilo va fijado adentro ([despachador], `Dispatchers.Default` por defecto) porque antes
 * la marca corría en el hilo principal según quién la llamara, y en el SC9863A eso congela
 * la cámara. Ningún llamador puede volver a ponerla ahí.
 *
 * Regla "nunca se pierde la foto" (D12): si falla la marca, se revela sin marca; si falla el
 * decode, se guarda la cruda como `image/jpeg` con la orientación en EXIF (o tal cual si ni
 * eso se puede) y se registra el fallo. Subir sin marca es mejor que no subir.
 */
class RevelarFoto(
    private val medioRepository: MedioRepository,
    private val codec: CodecFoto,
    private val proveedorSnapshotMapa: ProveedorSnapshotMapa,
    private val registroDeFallos: RegistroDeFallos,
    private val zona: ZoneId = ZoneId.systemDefault(),
    private val despachador: CoroutineDispatcher = Dispatchers.Default,
    private val reloj: () -> Long = System::nanoTime,
) {
    /** Al disparar: miniatura limpia de la foto cruda. Corre en [despachador]. */
    suspend fun miniaturaRapida(crudo: ByteArray, rotacionGrados: Int): ByteArray =
        withContext(despachador) { codec.miniatura(crudo, rotacionGrados) }

    /**
     * En segundo plano. Idempotente: una foto ya revelada no se toca. [conMapa] = `false` lo
     * usa el worker cuando el proceso murió antes de revelar (D6): fecha y coordenadas, sin mapa.
     */
    suspend fun revelar(medioId: String, conMapa: Boolean = true): Revelado = withContext(despachador) {
        val medio = medioRepository.obtenerPorId(medioId)
        if (medio == null || medio.tipo != TipoMedio.FOTO) return@withContext Revelado(ResultadoRevelado.NO_APLICA)
        if (medio.revelada) return@withContext Revelado(ResultadoRevelado.YA_REVELADA)
        val crudo = medioRepository.leerOriginal(medioId)
            ?: return@withContext Revelado(ResultadoRevelado.NO_APLICA)
        val rotacion = medio.rotacionGrados
        val inicio = reloj()

        val dims = intentar("revelar_foto_dimensiones") { codec.dimensionesFinales(crudo, rotacion) }
        if (dims == null) {
            registroDeFallos.registrar("revelar_foto_decode", IllegalArgumentException("Foto cruda ilegible"))
            return@withContext conservarCruda(medio, crudo, inicio)
        }

        val gps = medio.gps
        val marca = FormatoMarcaDeAgua.de(medio.createdAtDevice, gps, zona)
        val mapa = if (conMapa && gps != null) {
            proveedorSnapshotMapa.snapshot(gps, DisposicionMarcaDeAgua.ladoMapaPx(dims.ancho, dims.alto))
        } else {
            null
        }

        val conMarca = intentar("revelar_foto_marca") { codec.revelar(crudo, rotacion, marca, mapa) }
        val (final, resultado) = if (conMarca != null) {
            conMarca to ResultadoRevelado.OK
        } else {
            val sinMarca = intentar("revelar_foto_sin_marca") { codec.revelar(crudo, rotacion, marca = null, mapa = null) }
                ?: return@withContext conservarCruda(medio, crudo, inicio)
            sinMarca to ResultadoRevelado.SIN_MARCA
        }
        guardar(medio, final, "image/webp", resultado, inicio, conMapa = resultado == ResultadoRevelado.OK && mapa != null)
    }

    /** Camino FALLO_DECODE: la cruda con la orientación en EXIF, o tal cual si ni eso se puede. */
    private suspend fun conservarCruda(medio: Medio, crudo: ByteArray, inicio: Long): Revelado {
        val bytes = intentar("revelar_foto_exif") { codec.conOrientacionExif(crudo, medio.rotacionGrados) } ?: crudo
        return guardar(medio, bytes, "image/jpeg", ResultadoRevelado.FALLO_DECODE, inicio, conMapa = false)
    }

    private suspend fun guardar(
        medio: Medio,
        bytes: ByteArray,
        contentType: String,
        resultado: ResultadoRevelado,
        inicio: Long,
        conMapa: Boolean,
    ): Revelado {
        val guardado = medioRepository.guardarRevelado(medio.id, bytes, contentType)
        if (guardado is Result.Failure) {
            // Lo normal es que el medio saliera de PENDIENTE mientras se revelaba; la cruda
            // en disco no se tocó.
            registroDeFallos.registrar("revelar_foto_guardar", IllegalStateException("guardarRevelado: ${guardado.error}"))
            return Revelado(ResultadoRevelado.NO_APLICA)
        }
        return Revelado(
            resultado = resultado,
            msRevelado = (reloj() - inicio) / 1_000_000,
            conGps = medio.gps != null,
            conMapa = conMapa,
            kbFinal = bytes.size / 1024,
        )
    }

    // Atrapa Throwable porque un OutOfMemoryError al decodificar no debe perder la foto; la
    // cancelación sí se propaga para no tragarse el fin de la corrutina.
    private inline fun <T> intentar(contexto: String, bloque: () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        registroDeFallos.registrar(contexto, e)
        null
    }
}
