package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte

// Subida de un medio a Storage y creación de su documento en Firestore.
interface MedioRemoto {
    suspend fun subirArchivo(
        reporte: Reporte,
        medio: Medio,
        contenido: ByteArray,
        miniatura: ByteArray?,
    ): Result<RutasRemotas>

    suspend fun crearDocumentoSiNoExiste(
        reporte: Reporte,
        medio: Medio,
        rutas: RutasRemotas,
    ): Result<Unit>
}