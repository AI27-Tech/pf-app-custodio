package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.local.media.Grabadora
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.ai27.app.custodio.domain.usecase.CapturarMedio
import com.ai27.app.custodio.domain.usecase.CerrarReporte
import com.ai27.app.custodio.domain.usecase.CrearReporte
import com.ai27.app.custodio.domain.usecase.EtiquetarMedio
import com.ai27.app.custodio.domain.usecase.ObtenerEtiquetas
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CapturaModule {

    @Provides
    fun proveeCrearReporte(reporteRepository: ReporteRepository): CrearReporte =
        CrearReporte(reporteRepository)

    @Provides
    fun proveeCapturarMedio(
        reporteRepository: ReporteRepository,
        medioRepository: MedioRepository,
    ): CapturarMedio = CapturarMedio(reporteRepository, medioRepository)

    @Provides
    fun proveeEtiquetarMedio(
        medioRepository: MedioRepository,
        etiquetaRepository: EtiquetaRepository,
    ): EtiquetarMedio = EtiquetarMedio(medioRepository, etiquetaRepository)

    @Provides
    fun proveeObtenerEtiquetas(etiquetaRepository: EtiquetaRepository): ObtenerEtiquetas =
        ObtenerEtiquetas(etiquetaRepository)

    @Provides
    fun proveeCerrarReporte(
        reporteRepository: ReporteRepository,
        medioRepository: MedioRepository,
    ): CerrarReporte = CerrarReporte(reporteRepository, medioRepository)

    @Provides
    @Singleton
    fun proveeGrabadora(@ApplicationContext context: Context): Grabadora = Grabadora(context)
}