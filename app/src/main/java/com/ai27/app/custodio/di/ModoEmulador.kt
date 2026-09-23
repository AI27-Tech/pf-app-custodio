package com.ai27.app.custodio.di

import android.content.Context
import android.content.pm.PackageManager
import com.ai27.app.custodio.BuildConfig

internal object ModoEmulador {

    private const val META_ACTIVO = "com.ai27.app.custodio.modo_emulador"
    const val HOST = "127.0.0.1"
    const val PUERTO_FIRESTORE = 8080
    const val PUERTO_STORAGE = 9199
    const val PUERTO_AUTH = 9099

    // S12: el emulador solo se puede usar en debug. Si algún día un APK de release
    // apareciera con el meta-data puesto (por accidente, por un fork, etc.), que NO
    // apunte a un emulador local, sino a la configuración de Producción.
    fun activo(context: Context): Boolean =
        BuildConfig.DEBUG &&
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
                ?.getBoolean(META_ACTIVO, false)
                ?: false
}