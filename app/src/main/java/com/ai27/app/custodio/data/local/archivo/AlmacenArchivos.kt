package com.ai27.app.custodio.data.local.archivo

interface AlmacenArchivos {
    suspend fun guardar(nombre: String, contenido: ByteArray): String
    suspend fun leer(ruta: String): ByteArray?
    suspend fun borrar(ruta: String)
}