# AGENTS.md

Android app: Kotlin + Jetpack Compose (Material3), single module `:app`, package `com.ai27.app.custodio`. B1 completado: `AppCustodioApplication` con Hilt registrada en el manifest, `MainActivity` con `@AndroidEntryPoint` y `AppNavHost` que arranca en la ruta `inicio` (navegación vacía en los dos temas), y el catálogo de dependencias (Hilt, Room 3, WorkManager, Navigation Compose, CameraX, Coil 3, Firebase BoM) en `gradle/libs.versions.toml`. Los paquetes de negocio (`data/`, `domain/`, `di/`) se crean conforme aterriza su contenido en las fases B2+; la estructura de capas está en `PLAN.md` §4. `ExampleUnitTest`/`ExampleInstrumentedTest` siguen siendo scaffolding.

## Required reading

Before planning, reviewing, or modifying this project, read docs/GENERIC_RULES.md in full and apply it alongside this file. The link is an explicit reading requirement; do not assume your tool automatically imports linked Markdown files.

GENERIC_RULES.md contains reusable working rules. This file adds the project's Android conventions and spec-driven workflow. For repository conventions, project-specific rules refine the generic defaults. Neither file overrides the user's explicit instructions or the agent's higher-priority instructions. If documents conflict in a way that affects behavior or scope, clarify the conflict before implementing the affected part.

## Commands (run from repo root via the wrapper)

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JUnit4, no device needed): `./gradlew testDebugUnitTest`
  - Single test: `./gradlew testDebugUnitTest --tests "com.ai27.app.custodio.ExampleUnitTest"`
- Instrumented / Compose UI tests (need emulator or connected device): `./gradlew connectedDebugAndroidTest`
- Install on device: `./gradlew installDebug`

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