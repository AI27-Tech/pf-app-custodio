package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import kotlinx.coroutines.flow.Flow

/**
 * Sesión del custodio. Tras el cutover a correo la identidad es el CORREO verificado por
 * Firebase Auth (correo + contraseña), no el número de teléfono.
 *
 * El login es de una sola fase: no hay Activity, ni reCAPTCHA, ni código que teclear, ni
 * cuenta regresiva. Eso también se refleja en la forma de la interfaz: tres funciones en
 * vez del enjambre de Phone Auth.
 */
interface SesionRepositorio {

    fun observarCustodio(): Flow<Custodio?>

    /**
     * Autentica contra Firebase Auth con correo y contraseña.
     *
     * No lleva `Activity` a propósito: `signInWithEmailAndPassword` no lo necesita
     * (el reCAPTCHA y la auto-lectura del SMS eran exclusivos de Phone Auth), así que la
     * firma no ata el dominio a Android.
     */
    suspend fun iniciarSesion(correo: String, contrasena: String): Result<Custodio>

    suspend fun cerrarSesion(): Result<Unit>
}
