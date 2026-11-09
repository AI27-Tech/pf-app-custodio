package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

// Clasificación de fallos remotos (hallazgo C5). Separa lo **transitorio** —volver a
// intentar tiene sentido— de lo **permanente** —reintentar solo gasta batería y red, y
// la causa conviene persistirla y mandarla a Crashlytics—.
//
// FirebaseNetworkException y FirebaseFirestoreException no se instancian en JVM host
// (su inicialización de clase toca Android), así que las pruebas de host ejercitan la
// parte pura por nombre de código y esas ramas concretas se cubren por antonomasia:
// la red de true siempre cae en IOException y el `Code` se traduce aquí mismo.
internal fun excepcionRemotoAError(causa: Throwable): DominioError {
    if (causa is IOException || causa is FirebaseNetworkException) {
        return DominioError.SinConexion
    }
    val firestore = causa as? FirebaseFirestoreException
        ?: return DominioError.Inesperado(causa)
    return codigoFirestoreAError(firestore.code.name) ?: DominioError.Inesperado(causa)
}

/** Traduce el nombre de un [FirebaseFirestoreException.Code] a un error de dominio. */
internal fun codigoFirestoreAError(codigo: String): DominioError? = when (codigo) {
    "UNAVAILABLE", "DEADLINE_EXCEEDED" -> DominioError.ServidorIndisponible

    // PERMISSION_DENIED es definitivo: las reglas rechazaron la escritura y un
    // reintento (mismo token, misma evidencia) volverá a ser denegado.
    "PERMISSION_DENIED" -> DominioError.PermisoDenegado
    else -> null
}

/**
 * Lo que el worker puede reintentar solo: fallo de red, indisponibilidad temporal, o una foto
 * que el revelado no pudo escribir todavía (Task 6 de PLAN_REVELAR_FOTO).
 */
internal fun esReintentable(error: DominioError): Boolean =
    error is DominioError.SinConexion ||
        error is DominioError.ServidorIndisponible ||
        error is DominioError.FotoSinRevelar

/** Texto corto persistible de un error (para `medios.ultimoError`). Sin datos personales. */
internal fun DominioError.aTextoPersistible(): String = when (this) {
    is DominioError.Inesperado ->
        "${causa::class.simpleName ?: "causa"}: ${causa.message ?: "sin mensaje"}"
    else -> this::class.simpleName ?: "error"
}

internal suspend fun <T> ejecutarRemoto(bloque: suspend () -> T): Result<T> = try {
    Result.Success(bloque())
} catch (e: Exception) {
    Result.Failure(excepcionRemotoAError(e))
}