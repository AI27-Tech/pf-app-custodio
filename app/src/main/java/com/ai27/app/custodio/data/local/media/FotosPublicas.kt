package com.ai27.app.custodio.data.local.media

/**
 * Guarda una copia de cada foto tomada en la carpeta pública `Pictures/AppCustodio`,
 * visible en el administrador de archivos del dispositivo (copia independiente de la
 * que vive en `filesDir/medios` para la subida).
 *
 * Es una interfaz para poder sustituirla por un doble en pruebas de JVM; la
 * implementación real [FotosPublicasMediaStore] usa MediaStore y necesita un `Context`.
 */
interface FotosPublicas {
    suspend fun guardar(nombre: String, jpeg: ByteArray)
}
