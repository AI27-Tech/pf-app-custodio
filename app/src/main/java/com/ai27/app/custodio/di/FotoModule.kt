package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.foto.BitmapCodecFoto
import com.ai27.app.custodio.data.foto.CodecFoto
import com.ai27.app.custodio.data.foto.RevelarFoto
import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import com.ai27.app.custodio.domain.repository.MedioRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FotoModule {

    @Provides
    @Singleton
    fun proveeCodecFoto(@ApplicationContext context: Context): CodecFoto = BitmapCodecFoto(context.cacheDir)

    // Singleton: el candado de RevelarFoto solo evita el doble revelado si cámara y worker comparten instancia.
    @Provides
    @Singleton
    fun proveeRevelarFoto(
        medioRepository: MedioRepository,
        codec: CodecFoto,
        proveedorSnapshotMapa: ProveedorSnapshotMapa,
        registroDeFallos: RegistroDeFallos,
    ): RevelarFoto = RevelarFoto(medioRepository, codec, proveedorSnapshotMapa, registroDeFallos)
}
