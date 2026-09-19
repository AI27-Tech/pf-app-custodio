package com.ai27.app.custodio.ui.login

data class LoginUiState(
    val correoEscrito: String = "",
    val contrasenaEscrita: String = "",
    val iniciando: Boolean = false,
    val error: String? = null,
)