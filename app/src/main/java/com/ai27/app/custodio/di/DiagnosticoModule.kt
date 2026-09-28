package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.diagnostico.RegistroDeEventos
import com.ai27.app.custodio.data.diagnostico.RegistroDeEventosAnalytics
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallosCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticoModule {

    @Provides
    @Singleton
    fun proveeRegistroDeFallos(): RegistroDeFallos =
        RegistroDeFallosCrashlytics(FirebaseCrashlytics.getInstance())

    // Analytics no tiene emulador ni modo de depuración propio: a diferencia de
    // Firestore/Storage/Auth, no lo desvía `ModoEmulador`, así que el singleton se
    // resuelve siempre contra la propiedad real.
    @Provides
    @Singleton
    fun proveeRegistroDeEventos(@ApplicationContext context: Context): RegistroDeEventos =
        RegistroDeEventosAnalytics(FirebaseAnalytics.getInstance(context))
}
