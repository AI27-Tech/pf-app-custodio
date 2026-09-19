package com.ai27.app.custodio.domain

import com.ai27.app.custodio.domain.error.DominioError

sealed interface Result<out T> {
    data class Success<T>(val value: T) : Result<T>
    data class Failure(val error: DominioError) : Result<Nothing>

    fun <R> fold(onSuccess: (T) -> R, onFailure: (DominioError) -> R): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }
}