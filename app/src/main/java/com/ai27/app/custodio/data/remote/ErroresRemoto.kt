package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.google.firebase.FirebaseNetworkException
import java.io.IOException

internal fun excepcionRemotoAError(causa: Throwable): DominioError = when (causa) {
    is IOException, is FirebaseNetworkException -> DominioError.SinConexion
    else -> DominioError.Inesperado(causa)
}

internal suspend fun <T> ejecutarRemoto(bloque: suspend () -> T): Result<T> = try {
    Result.Success(bloque())
} catch (e: Exception) {
    Result.Failure(excepcionRemotoAError(e))
}