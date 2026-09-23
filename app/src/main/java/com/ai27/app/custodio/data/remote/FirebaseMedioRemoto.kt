package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.data.mapper.aDocumentoFirestore
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

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
        // S4: `storage.rules` hace las rutas create-only (una vez escrito, el blob es
        // inmutable). El reintento idempotente (PLAN §5.4) ya no "sobrescribe": se
        // sube CONTRA LA AUSENCIA del objeto. Si el blob ya está —un reintento tras
        // caerse la red a mitad— no se escribe nada y solo se completa lo que faltara
        // (p. ej. la miniatura cuando el primer intento murió entre archivo y miniatura).
        subirSiNoExiste(rutas.media, contenido, metadatos)
        rutas.miniatura?.let { ruta ->
            if (miniatura != null) subirSiNoExiste(ruta, miniatura, metadatos)
        }
        rutas
    }

    override suspend fun crearDocumentoSiNoExiste(
        reporte: Reporte,
        medio: Medio,
        rutas: RutasRemotas,
    ): Result<Unit> = ejecutarRemoto {
        val doc = firestore.collection(MEDIOS).document(medio.id)
        if (!doc.get().await().exists()) {
            // create-only en reglas: si la red se cae a mitad reescribimos el mismo id.
            val documento = medio.copy(storagePath = rutas.media, thumbnailPath = rutas.miniatura)
            doc.set(documento.aDocumentoFirestore(reporte, rutas)).await()
        }
        Unit
    }

    // El contenido del reintento es el mismo del primer intento (mismo medio, mismo
    // sha), así que "ya existe" es exactamente "ya está subido".
    private suspend fun subirSiNoExiste(ruta: String, bytes: ByteArray, metadatos: StorageMetadata) {
        if (objetoExiste(ruta)) return
        storage.reference.child(ruta).putBytes(bytes, metadatos).await()
    }

    private suspend fun objetoExiste(ruta: String): Boolean =
        try {
            storage.reference.child(ruta).metadata.await()
            true
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) false else throw e
        }

    private companion object {
        const val MEDIOS = "medios"
    }
}