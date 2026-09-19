package com.ai27.app.custodio.data.local.archivo

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilesDirAlmacenArchivos(
    private val directorio: File,
) : AlmacenArchivos {

    override suspend fun guardar(nombre: String, contenido: ByteArray): String = withContext(Dispatchers.IO) {
        directorio.mkdirs()
        val archivo = File(directorio, nombre)
        archivo.writeBytes(contenido)
        archivo.absolutePath
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
}