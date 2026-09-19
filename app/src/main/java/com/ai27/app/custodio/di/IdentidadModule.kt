package com.ai27.app.custodio.di

import android.content.Context
import android.provider.Settings
import com.ai27.app.custodio.domain.model.Custodio
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

object IdentidadNombres {
    const val CUSTODIO_PROVISIONAL = "custodioProvisional"
    const val DEVICE_ID = "deviceId"
}

@Module
@InstallIn(SingletonComponent::class)
object IdentidadModule {

    const val UID_CUSTODIO_PROVISIONAL = "custodio-provisional"
    const val CORREO_CUSTODIO_PROVISIONAL = "provisional@ai27.mx"

    @Provides
    @Singleton
    @Named(IdentidadNombres.CUSTODIO_PROVISIONAL)
    fun proveeCustodioProvisional(): Custodio = Custodio(
        uid = UID_CUSTODIO_PROVISIONAL,
        correo = CORREO_CUSTODIO_PROVISIONAL,
    )

    @Provides
    @Singleton
    @Named(IdentidadNombres.DEVICE_ID)
    fun proveeDeviceId(@ApplicationContext context: Context): String {
        val id = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        return id?.takeIf { it.isNotBlank() } ?: "device-desconocido"
    }
}