package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.mapa.MapsSnapshotMapa
import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapaModule {

    @Provides
    @Singleton
    fun proveeProveedorSnapshotMapa(
        @ApplicationContext context: Context,
        registroDeFallos: RegistroDeFallos,
    ): ProveedorSnapshotMapa = MapsSnapshotMapa(context, registroDeFallos)
}
