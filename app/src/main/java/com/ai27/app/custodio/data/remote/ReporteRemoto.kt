package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte

// Operaciones sobre el documento del reporte en Firestore.
// Espejan las transiciones de las reglas de seguridad (§5.3): crear solo en ACTIVO,
// cerrar/descartar desde ACTIVO, y en CERRADO solo avanzar subida/mediosSubidos/completo.
interface ReporteRemoto {
    suspend fun obtenerCiclo(reporteId: String): Result<Ciclo?>
    suspend fun crear(reporte: Reporte): Result<Unit>
    suspend fun cerrar(reporte: Reporte): Result<Unit>
    suspend fun descartar(reporte: Reporte): Result<Unit>
    suspend fun actualizarContadores(
        reporteId: String,
        estado: EstadoSubida,
        mediosSubidos: Int,
        completo: Boolean,
    ): Result<Unit>
}