package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.remote.FirebaseAuthSesionRepositorio
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun proveeFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth {
        val auth = FirebaseAuth.getInstance()
        if (ModoEmulador.activo(context)) {
            auth.useEmulator(ModoEmulador.HOST, ModoEmulador.PUERTO_AUTH)
        }
        return auth
    }

    @Provides
    @Singleton
    fun proveeSesionRepositorio(auth: FirebaseAuth): SesionRepositorio =
        FirebaseAuthSesionRepositorio(auth)
}