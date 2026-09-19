# AGENTS.md

Android app: Kotlin + Jetpack Compose (Material3), single module `:app`, package `com.ai27.app.custodio`. B1 completado: `AppCustodioApplication` con Hilt registrada en el manifest, `MainActivity` con `@AndroidEntryPoint` y `AppNavHost` que arranca en la ruta `login`, y el catálogo de dependencias (Hilt, Room 3, WorkManager, Navigation Compose, CameraX, Coil 3, Firebase BoM, play-services-location) en `gradle/libs.versions.toml`. B2 completado: dominio puro + persistencia local Room (`AppCustodioDatabase`, índice único parcial de reporte activo) y archivos en `filesDir/medios`. B3 completado: pantalla de captura (CameraX foto + MediaRecorder nota de voz ≤5 min, GPS por archivo one-shot ~8 s con fallback de texto), etiquetado obligatorio con chips, miniaturas 320 px, rejilla de 50 medios con borrado solo pre-subida, "Nuevo viaje" por placa y "Cerrar viaje". B4 completado: autenticación con Firebase Auth (correo+contraseña), `RootViewModel`/gate en `AppNavHost` con rutas `login`/`inicio`, sesión persistente, barra de sesión con `Cerrar sesión` bloqueado (confirmación) mientras hay archivos sin subir; `Custodio(uid, correo)` sale de la sesión (ya no hay identidad provisional). `ExampleUnitTest`/`ExampleInstrumentedTest` siguen siendo scaffolding.

## Required reading

Before planning, reviewing, or modifying this project, read docs/GENERIC_RULES.md in full and apply it alongside this file. The link is an explicit reading requirement; do not assume your tool automatically imports linked Markdown files.

GENERIC_RULES.md contains reusable working rules. This file adds the project's Android conventions and spec-driven workflow. For repository conventions, project-specific rules refine the generic defaults. Neither file overrides the user's explicit instructions or the agent's higher-priority instructions. If documents conflict in a way that affects behavior or scope, clarify the conflict before implementing the affected part.

## Commands (run from repo root via the wrapper)

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JUnit4, no device needed): `./gradlew testDebugUnitTest`
  - Single test: `./gradlew testDebugUnitTest --tests "com.ai27.app.custodio.ExampleUnitTest"`
- Instrumented / Compose UI tests (need emulator or connected device): `./gradlew connectedDebugAndroidTest`
- Install on device: `./gradlew installDebug`

## Workflow: pausa para Sync

- Después de tocar scripts de build que requieren Sync (`gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`), párate: informa al humano y espera a que haga el Sync en Android Studio antes de continuar con más cambios o con verificación.

## Toolchain

- AGP 9.4.1, Gradle 9.6.0 (wrapper), Kotlin 2.3.10 (compose compiler plugin) + KSP 2.3.10, Compose BOM 2026.02.01.
- AGP 9 usa Kotlin embebido (built-in): no se aplica `org.jetbrains.kotlin.android`. El plugin de compose y KSP deben versionarse a la par del Kotlin embebido de AGP (2.3.10). Hilt 2.60.1 (requiere ≥2.59 para AGP 9), google-services 4.5.0 aplicado en `:app`.
- JVM toolchain 25 auto-resolved via foojay (`gradle/gradle-daemon-jvm.properties`); Java source/target 11.
- minSdk 26, target/compileSdk 37, applicationId `com.ai27.app.custodio`.

## AGP 9 gotchas (most online examples use the removed old DSL)

- `compileSdk` must use the new block form: `compileSdk { version = release(37) }`. A bare `compileSdk = 37` will not compile.
- Minify/R8 lives in `buildTypes.release.optimization { enable = ... }`. `minifyEnabled` / `isMinifyEnabled` no longer exist. Release currently has R8 disabled (`optimization { enable = false }`).
- Keep rules live in `app/src/main/keepRules/` (existing `rules.keep`), not `proguard-rules.pro`.
- El plugin de Crashlytics (`com.google.firebase.crashlytics`) está versionado en el catálogo pero **no aplicado**: tiene fricción conocida con AGP 9 y la consola Firebase aún no existe (carril A). Se aplica en B5, cuando el pipeline de subida entra en uso.

## Config notes

- `org.gradle.configuration-cache=true` in `gradle.properties` — keep build-script edits configuration-cache compatible.
- `local.properties` (machine-local `sdk.dir`) is gitignored; never commit it or hard-code SDK paths.
- App name is "App Custodio" (`rootProject.name`, manifest label, theme `Theme.AppCustodio`).