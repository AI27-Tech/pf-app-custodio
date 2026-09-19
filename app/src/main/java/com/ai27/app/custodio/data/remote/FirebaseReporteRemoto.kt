package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.data.mapper.aDocumentoFirestore
import com.ai27.app.custodio.data.mapper.sinNulos
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant

class FirebaseReporteRemoto(
    private val firestore: FirebaseFirestore,
) : ReporteRemoto {

    override suspend fun obtenerCiclo(reporteId: String): Result<Ciclo?> = ejecutarRemoto {
        val doc = firestore.collection(REPORTES).document(reporteId).get().esperar()
        if (doc.exists()) {
            Ciclo.valueOf(doc.getString("ciclo") ?: error("Documento de reporte sin ciclo"))
        } else {
            null
        }
    }

    override suspend fun crear(reporte: Reporte): Result<Unit> = ejecutarRemoto {
        firestore.collection(REPORTES).document(reporte.id).set(reporte.aDocumentoFirestore()).esperar()
        Unit
    }

    override suspend fun cerrar(reporte: Reporte): Result<Unit> = ejecutarRemoto {
        firestore.collection(REPORTES).document(reporte.id).update(mapaCierre(reporte).sinNulos()).esperar()
        Unit
    }

    override suspend fun descartar(reporte: Reporte): Result<Unit> = ejecutarRemoto {
        val update = mapOf(
            "ciclo" to Ciclo.DESCARTADO.name,
            "motivoDescarte" to reporte.motivoDescarte,
            "closedAt" to reporte.closedAt?.let { Timestamp(it.epochSecond, it.nano) },
        ).sinNulos()
        firestore.collection(REPORTES).document(reporte.id).update(update).esperar()
        Unit
    }

    override suspend fun actualizarContadores(
        reporteId: String,
        estado: EstadoSubida,
        mediosSubidos: Int,
        completo: Boolean,
    ): Result<Unit> = ejecutarRemoto {
        val update = mapOf(
            "subida" to estado.name,
            "mediosSubidos" to mediosSubidos,
            "completo" to completo,
        )
        // Solo estas llaves: es lo que las reglas permiten avanzar en un reporte CERRADO.
        firestore.collection(REPORTES).document(reporteId).update(update).esperar()
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