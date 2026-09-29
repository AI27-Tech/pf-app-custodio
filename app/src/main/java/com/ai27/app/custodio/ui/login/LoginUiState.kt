package com.ai27.app.custodio.ui.login

import androidx.annotation.StringRes

/**
 * Login de una sola fase (plan de correo, Task 7): correo + contraseña, sin OTP.
 *
 * Ya no hay `FaseLogin` porque no hay dos pasos que recordar ni `segundosParaReenviar`
 * porque no hay SMS que reenviar.
 */
data class LoginUiState(
    val correoEscrito: String = "",
    val contrasenaEscrita: String = "",
    val autenticando: Boolean = false,
    @StringRes val error: Int? = null,
)
