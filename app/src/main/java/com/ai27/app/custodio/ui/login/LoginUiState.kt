package com.ai27.app.custodio.ui.login

import androidx.annotation.StringRes

/** Fases del login por SMS (PHONE_AUTH_PLAN Task 5): primero el número, luego el OTP. */
enum class FaseLogin { INGRESANDO_NUMERO, ESPERANDO_CODIGO }

data class LoginUiState(
    val fase: FaseLogin = FaseLogin.INGRESANDO_NUMERO,
    val telefonoEscrito: String = "",
    val codigoEscrito: String = "",
    val enviandoSms: Boolean = false,
    val verificando: Boolean = false,
    val segundosParaReenviar: Int = 0,
    @StringRes val error: Int? = null,
)