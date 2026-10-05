package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.mapa.MapsSnapshotMapa
import com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa
import com.ai27.app.custodio.data.marcaDeAgua.BitmapEstampadorFoto
import com.ai27.app.custodio.data.marcaDeAgua.ComponerMarcaDeAgua
import com.ai27.app.custodio.data.marcaDeAgua.EstampadorFoto
import com.ai27.app.custodio.data.marcaDeAgua.GrabarMarcaDeAguaEnFoto
import com.ai27.app.custodio.domain.repository.MedioRepository
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

    @Provides
    @Singleton
    fun proveeEstampadorFoto(): EstampadorFoto = BitmapEstampadorFoto()

    @Provides
    fun proveeComponerMarcaDeAgua(
        estampador: EstampadorFoto,
        proveedorSnapshotMapa: ProveedorSnapshotMapa,
    ): ComponerMarcaDeAgua = ComponerMarcaDeAgua(estampador, proveedorSnapshotMapa)

    @Provides
    fun proveeGrabarMarcaDeAguaEnFoto(
        medioRepository: MedioRepository,
        componerMarcaDeAgua: ComponerMarcaDeAgua,
        registroDeFallos: RegistroDeFallos,
    ): GrabarMarcaDeAguaEnFoto =
        GrabarMarcaDeAguaEnFoto(medioRepository, componerMarcaDeAgua, registroDeFallos)
}
