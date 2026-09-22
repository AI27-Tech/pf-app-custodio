package com.ai27.app.custodio.di

import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallosCrashlytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticoModule {

    @Provides
    @Singleton
    fun proveeRegistroDeFallos(): RegistroDeFallos =
        RegistroDeFallosCrashlytics(FirebaseCrashlytics.getInstance())
}
