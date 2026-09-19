package com.ai27.app.custodio.di

import com.ai27.app.custodio.data.remote.FirebaseAuthSesionRepositorio
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun proveeFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun proveeSesionRepositorio(auth: FirebaseAuth): SesionRepositorio =
        FirebaseAuthSesionRepositorio(auth)
}