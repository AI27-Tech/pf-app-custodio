package com.ai27.app.custodio.data.local.archivo

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilesDirAlmacenArchivos(
    private val directorio: File,
) : AlmacenArchivos {

    override suspend fun guardar(nombre: String, contenido: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            val archivo = File(directorio, nombre)
            try {
                directorio.mkdirs()
                // Se comprueba antes de escribir para poder dar un mensaje accionable en
                // lugar de un "algo salió mal": el custodio necesita saber que el problema
                // es el espacio, no la app.
                if (!cabeEnDisco(contenido.size)) {
                    return@withContext Result.Failure(DominioError.SinEspacioEnDispositivo)
                }
                archivo.writeBytes(contenido)
                Result.Success(archivo.absolutePath)
            } catch (causa: IOException) {
                // Un archivo a medias es peor que ninguno: la fila de Room apuntaría a un
                // JPEG truncado que se subiría como evidencia.
                archivo.delete()
                if (!cabeEnDisco(contenido.size)) {
                    Result.Failure(DominioError.SinEspacioEnDispositivo)
                } else {
                    Result.Failure(DominioError.Inesperado(causa))
                }
            }
        }

    override suspend fun leer(ruta: String): ByteArray? = withContext(Dispatchers.IO) {
        val archivo = File(ruta)
        if (archivo.exists()) archivo.readBytes() else null
    }

    override suspend fun borrar(ruta: String) = withContext(Dispatchers.IO) {
        val archivo = File(ruta)
        if (archivo.exists()) {
            archivo.delete()
        }
    }

    // Se pide un margen además del tamaño del archivo: el sistema de archivos necesita
    // espacio para metadatos, y quedarse al filo vuelve a fallar en la captura siguiente.
    private fun cabeEnDisco(bytes: Int): Boolean =
        directorio.usableSpace > bytes + MARGEN_BYTES

    private companion object {
        const val MARGEN_BYTES = 2L * 1024 * 1024
    }
}
