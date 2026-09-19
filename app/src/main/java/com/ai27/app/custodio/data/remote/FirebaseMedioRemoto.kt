package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.data.mapper.aDocumentoFirestore
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata

class FirebaseMedioRemoto(
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
) : MedioRemoto {

    override suspend fun subirArchivo(
        reporte: Reporte,
        medio: Medio,
        contenido: ByteArray,
        miniatura: ByteArray?,
    ): Result<RutasRemotas> = ejecutarRemoto {
        val rutas = RutasStorage.de(medio)
        val metadatos = StorageMetadata.Builder().setContentType(medio.contentType).build()
        storage.reference.child(rutas.media).putBytes(contenido, metadatos).esperar()
        // Las rutas repetidas sobrescriben: el reintento es idempotente (PLAN §5.4).
        rutas.miniatura?.let { ruta ->
            if (miniatura != null) {
                storage.reference.child(ruta).putBytes(miniatura, metadatos).esperar()
            }
        }
        rutas
    }

    override suspend fun crearDocumentoSiNoExiste(
        reporte: Reporte,
        medio: Medio,
        rutas: RutasRemotas,
    ): Result<Unit> = ejecutarRemoto {
        val doc = firestore.collection(MEDIOS).document(medio.id)
        if (!doc.get().esperar().exists()) {
            // create-only en reglas: si la red se cae a mitad reescribimos el mismo id.
            val documento = medio.copy(storagePath = rutas.media, thumbnailPath = rutas.miniatura)
            doc.set(documento.aDocumentoFirestore(reporte, rutas)).esperar()
        }
        Unit
    }

    private companion object {
        const val MEDIOS = "medios"
    }
}