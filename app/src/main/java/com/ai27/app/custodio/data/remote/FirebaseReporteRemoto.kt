package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.data.mapper.aDocumentoFirestore
import com.ai27.app.custodio.data.mapper.sinNulos
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import java.time.Instant
import kotlinx.coroutines.tasks.await

class FirebaseReporteRemoto(
    private val firestore: FirebaseFirestore,
) : ReporteRemoto {

    /** Orden de avance del rango de `subida`: once COMPLETA, nunca se retrocede. */
    private fun rangoSubida(estado: EstadoSubida): Int = when (estado) {
        EstadoSubida.PENDIENTE -> 0
        EstadoSubida.EN_PROGRESO -> 1
        EstadoSubida.FALLIDA -> 2
        EstadoSubida.COMPLETA -> 3
    }

    private fun maxRango(anterior: EstadoSubida?, nuevo: EstadoSubida): EstadoSubida =
        if (anterior != null && rangoSubida(anterior) > rangoSubida(nuevo)) anterior else nuevo

    override suspend fun obtenerCiclo(reporteId: String): Result<Ciclo?> = ejecutarRemoto {
        val doc = firestore.collection(REPORTES).document(reporteId).get().await()
        if (doc.exists()) {
            Ciclo.valueOf(doc.getString("ciclo") ?: error("Documento de reporte sin ciclo"))
        } else {
            null
        }
    }

    override suspend fun crear(reporte: Reporte): Result<Unit> = ejecutarRemoto {
        firestore.collection(REPORTES).document(reporte.id).set(reporte.aDocumentoFirestore()).await()
        Unit
    }

    override suspend fun cerrar(reporte: Reporte): Result<Unit> = ejecutarRemoto {
        firestore.collection(REPORTES).document(reporte.id).update(mapaCierre(reporte).sinNulos()).await()
        Unit
    }

    override suspend fun descartar(reporte: Reporte): Result<Unit> = ejecutarRemoto {
        val update = mapOf(
            "ciclo" to Ciclo.DESCARTADO.name,
            "motivoDescarte" to reporte.motivoDescarte,
            "closedAt" to reporte.closedAt?.let { Timestamp(it.epochSecond, it.nano) },
        ).sinNulos()
        firestore.collection(REPORTES).document(reporte.id).update(update).await()
        Unit
    }

    override suspend fun actualizarContadores(
        reporteId: String,
        estado: EstadoSubida,
        mediosSubidos: Int,
        completo: Boolean,
    ): Result<Unit> = ejecutarRemoto {
        // Transacción de lectura+escritura con merge monótono (hallazgo C3): los medios
        // suben en paralelo (unique work por medio) y una escritura absoluta sin
        // transacción perdía la que llegara antes — `mediosSubidos` retrocedía y
        // `completo` quedaba en false para siempre (C1). Aquí nada de estas tres un puede
        // retroceder: subida solo avanza de rango, mediosSubidos toma el máximo y
        // completo es un OR con el valor anterior. Solo se escriben estas tres llaves:
        // es lo que las reglas permiten avanzar en un reporte CERRADO.
        val ref = firestore.collection(REPORTES).document(reporteId)
        val transaccion: Task<Unit> = firestore.runTransaction(Transaction.Function<Unit> { transaccion ->
            val ancla = transaccion.get(ref)
            if (!ancla.exists()) {
                throw FirebaseFirestoreException(
                    "Reporte sin documento para avanzar contadores: $reporteId",
                    FirebaseFirestoreException.Code.NOT_FOUND,
                )
            }
            val anterior: EstadoSubida? = ancla.getString("subida")
                ?.let { runCatching { EstadoSubida.valueOf(it) }.getOrNull() }
            val mediosPrevios = (ancla.getLong("mediosSubidos") ?: 0L).toInt()
            val completoPrevio = ancla.getBoolean("completo") ?: false
            transaccion.update(
                ref,
                mapOf(
                    "subida" to maxRango(anterior, estado).name,
                    "mediosSubidos" to maxOf(mediosSubidos, mediosPrevios),
                    "completo" to (completo || completoPrevio),
                ),
            )
            Unit
        })
        transaccion.await()
        Unit
    }

    private fun mapaCierre(reporte: Reporte): Map<String, Any?> {
        val closedAt = reporte.closedAt ?: Instant.now()
        return mapOf(
            "ciclo" to Ciclo.CERRADO.name,
            "closedAt" to Timestamp(closedAt.epochSecond, closedAt.nano),
            "comentario" to reporte.comentario,
            "mediosEsperados" to reporte.mediosEsperados,
            "mediosSubidos" to reporte.mediosSubidos,
            "completo" to reporte.completo,
            "subida" to reporte.subida.name,
            "resumenMedios" to mapaResumen(reporte.resumenMedios),
            "hayNovedad" to reporte.hayNovedad,
        )
    }

    private fun mapaResumen(resumen: ResumenMedios): Map<String, Any> = mapOf(
        "fotos" to resumen.fotos,
        "audios" to resumen.audios,
        "durationMsTotal" to resumen.durationMsTotal,
    )

    private companion object {
        const val REPORTES = "reportes"
    }
}