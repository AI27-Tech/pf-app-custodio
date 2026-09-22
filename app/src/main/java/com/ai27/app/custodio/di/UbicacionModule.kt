package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.ubicacion.FusedProveedorUbicacion
import com.ai27.app.custodio.data.ubicacion.ProveedorUbicacion
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UbicacionModule {

    @Provides
    @Singleton
    fun proveeClienteUbicacion(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun proveeProveedorUbicacion(
        @ApplicationContext context: Context,
        cliente: FusedLocationProviderClient,
    ): ProveedorUbicacion = FusedProveedorUbicacion(context, cliente)
}