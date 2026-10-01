plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)

}

// Firma de release: el keystore y sus contraseñas viven en keystore.properties
// (gitignored). Si el archivo no existe, release queda sin firmar y solo se usa
// para verificaciones locales (assembleRelease exige firma, installRelease no).
import java.util.Properties

val archivoKeystore = rootProject.file("keystore.properties")
val keystoreProperties: Properties? =
    if (archivoKeystore.exists()) {
        val props = Properties()
        val entrada = archivoKeystore.inputStream()
        try {
            props.load(entrada)
        } finally {
            entrada.close()
        }
        props
    } else {
        null
    }

android {
    namespace = "com.ai27.app.custodio"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ai27.app.custodio"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = keystoreProperties?.let { signingConfigs.getByName("release") }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // S12 (G2-8): ModoEmulador.activo() = BuildConfig.DEBUG && meta-data
        // modo_emulador; el flag de debug se lee de BuildConfig.
        buildConfig = true
    }

    // El gate de lint del release era ciego: lintVitalRelease —lo que corre assembleRelease—
    // solo considera issues de severidad **Fatal**, así que los de severidad Error pasaban
    // y se podía empaquetar un APK con un crash garantizado (hallazgo B3 de docs/AUDIT.md).
    //
    // Verificado por experimento: con solo `abortOnError = true` el release SIGUE pasando
    // con un NewApi presente. Lo que cierra el gate de verdad es promover a Fatal los issues
    // que significan "esto truena o filtra en campo".
    //
    // No hay archivo de baseline a propósito: al podar values-es-rMX/ y arreglar el
    // MissingPermission de ProveedorUbicacion, lint quedó en CERO errores. Un baseline vacío
    // solo serviría para esconder los próximos.
    lint {
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = false
        // Cada uno de estos ya causó o pudo causar un fallo de campo en este proyecto:
        // NewApi fue B2 (crash al grabar en Android 8-11) y MissingPermission era el
        // SecurityException del GPS por archivo.
        fatal += setOf(
            "NewApi",
            "InlinedApi",
            "MissingPermission",
            "CoarseFineLocation",
            "MissingTranslation",
            "Recycle",
            "WrongThread",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Persistencia local
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.sqlite.bundled)

    // Workers de subida
    implementation(libs.androidx.work.runtime.ktx)

    // Navegación
    implementation(libs.androidx.navigation.compose)

    // Cámara
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Imágenes (miniaturas y rejilla)
    implementation(libs.coil.compose)

    // Ubicación (GPS por archivo)
    implementation(libs.play.services.location)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Task<T>.await() para las escrituras de Firestore/Storage (hallazgo B1).
    // Firebase ya lo arrastra transitivamente; se declara explícito porque el código
    // de data/remote lo usa de forma directa y no debe depender de eso.
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}