package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.data.local.media.Miniaturas
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.TipoMedio

data class RutasRemotas(
    val media: String,
    val miniatura: String?,
)

// Rutas en Storage, agrupadas por reporte (PLAN §5.2). El medioId va en el nombre,
// así un reintento sobrescribe en lugar de duplicar (PLAN §5.4).
//
// La extensión sale del contenido real y no del tipo de medio (Task 6 de PLAN_REVELAR_FOTO):
// la foto revelada es WebP, la que falló al decodificar queda JPEG y las pendientes del 1.0.3
// traen miniatura JPEG. Las rutas siguen siendo deterministas: el mismo archivo en disco da la
// misma ruta en cada reintento.
object RutasStorage {
    /**
     * @param miniatura los bytes de la miniatura que se van a subir; `null` si no hay. Una
     *   miniatura que no es WebP ni JPEG tampoco lleva ruta: el documento queda con
     *   `thumbnailPath = null`, que las reglas aceptan.
     * @throws IllegalArgumentException si [Medio.contentType] no es uno de los que la app produce.
     */
    fun de(medio: Medio, miniatura: ByteArray?): RutasRemotas {
        val rutaMiniatura = if (medio.tipo == TipoMedio.FOTO && miniatura != null) {
            contentTypeMiniatura(miniatura)?.let { "medios/${medio.reporteId}/${medio.id}-min.${extensionDe(it)}" }
        } else {
            null
        }
        return RutasRemotas(
            media = "medios/${medio.reporteId}/${medio.id}.${extensionDe(medio.contentType)}",
            miniatura = rutaMiniatura,
        )
    }

    /** Tipo de la miniatura según sus propios bytes; `null` si no es WebP ni JPEG. */
    fun contentTypeMiniatura(bytes: ByteArray): String? = when {
        Miniaturas.esWebp(bytes) -> "image/webp"
        Miniaturas.esJpeg(bytes) -> "image/jpeg"
        else -> null
    }

    private fun extensionDe(contentType: String): String = when (contentType) {
        "image/webp" -> "webp"
        "image/jpeg" -> "jpg"
        "audio/mp4" -> "m4a"
        else -> throw IllegalArgumentException("contentType sin extensión de Storage: $contentType")
    }
}
