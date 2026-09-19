package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio

data class RutasRemotas(
    val media: String,
    val miniatura: String?,
)

// Rutas en Storage, agrupadas por reporte (PLAN §5.2). El medioId va en el nombre,
// así un reintento sobrescribe en lugar de duplicar (PLAN §5.4).
object RutasStorage {
    fun de(medio: Medio): RutasRemotas {
        val extension = when (medio.tipo) {
            TipoMedio.FOTO -> "jpg"
            TipoMedio.AUDIO -> "m4a"
        }
        val miniatura = if (medio.tipo == TipoMedio.FOTO) {
            "medios/${medio.reporteId}/${medio.id}-min.jpg"
        } else {
            null
        }
        return RutasRemotas(
            media = "medios/${medio.reporteId}/${medio.id}.$extension",
            miniatura = miniatura,
        )
    }
}