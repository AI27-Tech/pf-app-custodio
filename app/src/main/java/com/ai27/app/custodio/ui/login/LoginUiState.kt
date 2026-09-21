package com.ai27.app.custodio.ui.login

import androidx.annotation.StringRes

data class LoginUiState(
    val correoEscrito: String = "",
    val contrasenaEscrita: String = "",
    val iniciando: Boolean = false,
    @StringRes val error: Int? = null,
)