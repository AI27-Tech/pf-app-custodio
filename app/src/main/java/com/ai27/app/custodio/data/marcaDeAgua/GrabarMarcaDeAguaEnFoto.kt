package com.ai27.app.custodio.data.marcaDeAgua

import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Gps
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.MedioRepository

/**
 * Graba la marca de agua en el archivo original de una foto ya persistida: lee el JPEG
 * limpio, lo compone con [ComponerMarcaDeAgua] (fecha + coords + mapa según haya GPS) y
 * reescribe el original con el resultado. Es la pieza que une la composición con la
 * persistencia; se invoca desde el flujo de GPS en segundo plano, una vez por foto.
 *
 * **Solo fotos.** El audio no se estampa (requisito de alcance). Si el medio no es FOTO, es
 * no-op silencioso.
 *
 * **No bloquea la evidencia.** Si algo falla (el medio ya no existe, no se pudo leer el
 * original, o la escritura falló), se registra como fallo no fatal y se deja el archivo
 * limpio tal cual: subir la foto sin marca es mejor que no subirla.
 */
class GrabarMarcaDeAguaEnFoto(
    private val medioRepository: MedioRepository,
    private val componerMarcaDeAgua: ComponerMarcaDeAgua,
    private val registroDeFallos: RegistroDeFallos,
) {
    suspend operator fun invoke(medioId: String) {
        val medio = medioRepository.obtenerPorId(medioId) ?: return
        if (medio.tipo != TipoMedio.FOTO) return

        val jpegLimpio = medioRepository.leerOriginal(medioId) ?: return

        val estampado = try {
            componerMarcaDeAgua(jpegLimpio, medio.createdAtDevice, medio.gps)
        } catch (causa: Throwable) {
            registroDeFallos.registrar(CONTEXTO, causa)
            return
        }

        when (val r = medioRepository.reescribirOriginal(medioId, estampado)) {
            is Result.Success -> Unit
            is Result.Failure -> {
                // El medio pudo salir de PENDIENTE o borrarse mientras se componía; no es
                // fatal. Se deja el original limpio en disco.
                registroDeFallos.registrar(CONTEXTO, IllegalStateException("No se reescribió: ${r.error}"))
            }
        }
    }

    private companion object {
        const val CONTEXTO = "marca_de_agua"
    }
}
