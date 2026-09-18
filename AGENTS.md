# AGENTS.md

Android app: Kotlin + Jetpack Compose (Material3), single module `:app`, package `com.ai27.app.custodio`. Still the stock Android Studio "Empty Compose Activity" template — `MainActivity.kt` renders a placeholder `Greeting`, and `ExampleUnitTest`/`ExampleInstrumentedTest` are scaffolding. No CI, no README, no commits yet (`main` is unborn).

## Commands (run from repo root via the wrapper)

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JUnit4, no device needed): `./gradlew testDebugUnitTest`
  - Single test: `./gradlew testDebugUnitTest --tests "com.ai27.app.custodio.ExampleUnitTest"`
- Instrumented / Compose UI tests (need emulator or connected device): `./gradlew connectedDebugAndroidTest`
- Install on device: `./gradlew installDebug`

## Toolchain

- AGP 9.4.1, Gradle 9.6.0 (wrapper), Kotlin 2.2.10 + compose compiler plugin, Compose BOM 2026.02.01.
- JVM toolchain 25 auto-resolved via foojay (`gradle/gradle-daemon-jvm.properties`); Java source/target 11.
- minSdk 26, target/compileSdk 37, applicationId `com.ai27.app.custodio`.

## AGP 9 gotchas (most online examples use the removed old DSL)

- `compileSdk` must use the new block form: `compileSdk { version = release(37) }`. A bare `compileSdk = 37` will not compile.
- Minify/R8 lives in `buildTypes.release.optimization { enable = ... }`. `minifyEnabled` / `isMinifyEnabled` no longer exist. Release currently has R8 disabled (`optimization { enable = false }`).
- Keep rules live in `app/src/main/keepRules/` (existing `rules.keep`), not `proguard-rules.pro`.

## Config notes

- `org.gradle.configuration-cache=true` in `gradle.properties` — keep build-script edits configuration-cache compatible.
- `local.properties` (machine-local `sdk.dir`) is gitignored; never commit it or hard-code SDK paths.
- App name is "App Custodio" (`rootProject.name`, manifest label, theme `Theme.AppCustodio`).