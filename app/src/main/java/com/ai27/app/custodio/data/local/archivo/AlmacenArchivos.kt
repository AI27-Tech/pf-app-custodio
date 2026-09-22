package com.ai27.app.custodio.data.local.archivo

import com.ai27.app.custodio.domain.Result

interface AlmacenArchivos {
    /**
     * Escribe [contenido] con el nombre [nombre] y devuelve la ruta resultante.
     *
     * Devuelve `Result` y no `String` por el hallazgo **E4** de `docs/AUDIT.md`: antes
     * hacía un `writeBytes` desnudo, y con el disco lleno la `IOException` escapaba de la
     * corrutina de la ViewModel y **tiraba la app en el momento del disparo**. El escenario
     * es realista porque nada purga `filesDir` hasta que los archivos suben.
     */
    suspend fun guardar(nombre: String, contenido: ByteArray): Result<String>
    suspend fun leer(ruta: String): ByteArray?
    suspend fun borrar(ruta: String)
}
