package com.ai27.app.custodio.di

import android.content.Context
import androidx.work.WorkManager
import com.ai27.app.custodio.data.pipeline.ReporteSyncProceso
import com.ai27.app.custodio.data.pipeline.SubidaMedioProceso
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.remote.FirestoreAllowlistRepositorio
import com.ai27.app.custodio.data.remote.FirebaseMedioRemoto
import com.ai27.app.custodio.data.remote.FirebaseReporteRemoto
import com.ai27.app.custodio.data.remote.MedioRemoto
import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.data.worker.CustodioWorkerFactory
import com.ai27.app.custodio.data.worker.SubidaScheduler
import com.ai27.app.custodio.data.worker.WorkManagerSubidaScheduler
import com.ai27.app.custodio.domain.repository.AllowlistRepositorio
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {

    @Provides
    @Singleton
    fun proveeFirestore(@ApplicationContext context: Context): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        if (ModoEmulador.activo(context)) {
            firestore.useEmulator(ModoEmulador.HOST, ModoEmulador.PUERTO_FIRESTORE)
        }
        return firestore
    }

    @Provides
    @Singleton
    fun proveeStorage(@ApplicationContext context: Context): FirebaseStorage {
        val storage = FirebaseStorage.getInstance()
        if (ModoEmulador.activo(context)) {
            storage.useEmulator(ModoEmulador.HOST, ModoEmulador.PUERTO_STORAGE)
        }
        return storage
    }

    @Provides
    @Singleton
    fun proveeReporteRemoto(firestore: FirebaseFirestore): ReporteRemoto =
        FirebaseReporteRemoto(firestore)

    @Provides
    @Singleton
    fun proveeMedioRemoto(
        storage: FirebaseStorage,
        firestore: FirebaseFirestore,
    ): MedioRemoto = FirebaseMedioRemoto(storage, firestore)

    @Provides
    @Singleton
    fun proveeAllowlistRepositorio(firestore: FirebaseFirestore): AllowlistRepositorio =
        FirestoreAllowlistRepositorio(firestore)

    @Provides
    fun proveeReporteSyncProceso(
        reporteRepository: ReporteRepository,
        medioRepository: MedioRepository,
        reporteRemoto: ReporteRemoto,
    ): ReporteSyncProceso = ReporteSyncProceso(reporteRepository, medioRepository, reporteRemoto)

    @Provides
    fun proveeSubidaMedioProceso(
        reporteRepository: ReporteRepository,
        medioRepository: MedioRepository,
        etiquetaRepository: com.ai27.app.custodio.domain.repository.EtiquetaRepository,
        reporteRemoto: ReporteRemoto,
        medioRemoto: MedioRemoto,
        almacenArchivos: com.ai27.app.custodio.data.local.archivo.AlmacenArchivos,
    ): SubidaMedioProceso =
        SubidaMedioProceso(
            reporteRepository,
            medioRepository,
            etiquetaRepository,
            reporteRemoto,
            medioRemoto,
            almacenArchivos,
        )

    @Provides
    fun proveeWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun proveeSubidaScheduler(
        @ApplicationContext context: Context,
        reporteRepository: ReporteRepository,
        medioRepository: MedioRepository,
    ): SubidaScheduler = WorkManagerSubidaScheduler(context, reporteRepository, medioRepository)

    @Provides
    @Singleton
    fun proveeWorkerFactory(
        procesoSubida: SubidaMedioProceso,
        procesoSync: ReporteSyncProceso,
        medioRepository: MedioRepository,
        registroDeFallos: RegistroDeFallos,
    ): CustodioWorkerFactory = CustodioWorkerFactory(
        procesoSubida,
        procesoSync,
        medioRepository,
        registroDeFallos,
    )
}