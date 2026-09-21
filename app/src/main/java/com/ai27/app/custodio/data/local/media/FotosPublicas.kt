package com.ai27.app.custodio.data.local.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guarda una copia de cada foto tomada en la carpeta pública `Pictures/AppCustodio`,
 * visible en el administrador de archivos del dispositivo (copia independiente de la
 * que vive en `filesDir/medios` para la subida).
 *
 * En Android 10+ se inserta vía MediaStore con `RELATIVE_PATH` (sin permisos). En
 * Android 8-9 se escribe sobre `getExternalStoragePublicDirectory` y se avisa al
 * escáner; eso requiere `WRITE_EXTERNAL_STORAGE`.
 */
class FotosPublicas(private val context: Context) {

    suspend fun guardar(nombre: String, jpeg: ByteArray) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            guardarConMediaStore(nombre, jpeg)
        } else {
            guardarEnAlmacenamientoLegado(nombre, jpeg)
        }
    }

    private fun guardarConMediaStore(nombre: String, jpeg: ByteArray) {
        val valores = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$nombre.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, CARPETA_PUBLICA_RELATIVA)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
            ?: throw IOException("No se pudo crear la entrada en la galería")
        try {
            val salida = resolver.openOutputStream(uri)
                ?: throw IOException("No se pudo abrir el flujo de escritura de la galería")
            salida.use { it.write(jpeg) }
            valores.clear()
            valores.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun guardarEnAlmacenamientoLegado(nombre: String, jpeg: ByteArray) {
        val carpeta = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            CARPETA_PUBLICA,
        )
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw IOException("No se pudo crear Pictures/$CARPETA_PUBLICA")
        }
        val archivo = File(carpeta, "$nombre.jpg")
        archivo.writeBytes(jpeg)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(archivo.absolutePath),
            arrayOf("image/jpeg"),
            null,
        )
    }

    private companion object {
        const val CARPETA_PUBLICA = "AppCustodio"
        const val CARPETA_PUBLICA_RELATIVA = "Pictures/AppCustodio"
    }
}