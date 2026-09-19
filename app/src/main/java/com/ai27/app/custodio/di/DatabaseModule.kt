package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.repository.CatalogoEtiquetasRepository
import com.ai27.app.custodio.data.repository.RoomMedioRepository
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun proveeBaseDatos(context: Context): AppCustodioDatabase =
        AppCustodioDatabase.crearBaseDatos(context)

    @Provides
    @Singleton
    fun proveeReporteRepository(database: AppCustodioDatabase): ReporteRepository =
        RoomReporteRepository(database)

    @Provides
    @Singleton
    fun proveeMedioRepository(
        database: AppCustodioDatabase,
        almacenArchivos: AlmacenArchivos,
    ): MedioRepository = RoomMedioRepository(database, almacenArchivos)

    @Provides
    @Singleton
    fun proveeEtiquetaRepository(): EtiquetaRepository = CatalogoEtiquetasRepository()

    @Provides
    @Singleton
    fun proveeAlmacenArchivos(context: Context): AlmacenArchivos =
        FilesDirAlmacenArchivos(File(context.filesDir, "medios"))
}