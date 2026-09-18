# CHANGELOG

Registro de cambios del proyecto App Custodio. Las entradas más recientes van primero.

## 2026:09:18 17:35

**Resumen:** Se incorporan al repositorio las reglas genéricas de `docs/GENERIC_RULES.md`, las pautas móviles de `docs/MOBILE_GUIDELINES.md` y la configuración del IDE (`.idea/`).

**Cambios:**
- Se agregan `docs/GENERIC_RULES.md` (reglas de trabajo reutilizables para agentes de codificación) y `docs/MOBILE_GUIDELINES.md` (pautas específicas de Android).
- Se versiona la configuración de Android Studio (`.idea/`) generada en la apertura del proyecto.

**Archivos modificados:**
- `docs/GENERIC_RULES.md`
- `docs/MOBILE_GUIDELINES.md`
- `.idea/.name`
- `.idea/AndroidProjectSystem.xml`
- `.idea/codeStyles/`
- `.idea/compiler.xml`
- `.idea/deviceManager.xml`
- `.idea/gradle.xml`
- `.idea/markdown.xml`
- `.idea/misc.xml`
- `.idea/runConfigurations.xml`
- `.idea/vcs.xml`

## 2026:09:18 17:32

**Resumen:** Se agregó la regla de trabajo "pausa para Sync" en `AGENTS.md`.

**Cambios:**
- Se documentó que cualquier cambio en scripts de build que requiera Sync (`libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`) debe detener al agente: se informa al humano y se espera a que realice el Sync en Android Studio antes de continuar.

**Archivos modificados:**
- `AGENTS.md`

## 2026:09:18 17:29

**Resumen:** Fase B1 del plan: dependencias y andamiaje. La app arranca con un `AppNavHost` vacío en los dos temas, con Hilt, Room 3, WorkManager, Navigation Compose, CameraX, Coil 3 y Firebase BoM en el catálogo de versiones.

**Cambios:**
- Se amplió `gradle/libs.versions.toml` con el catálogo completo de la fase B1: KSP, Hilt y `hilt-navigation-compose`, Room 3 (`androidx.room3`, solo KSP), WorkManager, Navigation Compose, CameraX (core/camera2/lifecycle/view), Coil 3, Firebase BoM con auth/firestore/storage/crashlytics, y los plugins de google-services y Crashlytics.
- Se activó el plugin de google-services (`4.5.0`) en `:app`, cerrando el pendiente de B0: el `google-services.json` ya se procesa y `assembleDebug` compila con el plugin activo.
- Se alineó el toolchain con el Kotlin embebido de AGP 9.4: compose compiler y KSP en `2.3.10` (el `2.2.10-2.0.2` inicial chocaba con el guardrail `kotlin.sourceSets` de AGP 9).
- Se aplicaron los plugins de KSP y Hilt en `:app`; el compilador de Hilt y Room corren por KSP.
- Se creó `AppCustodioApplication` con `@HiltAndroidApp` y se registró en el manifest.
- Se creó `ui/navigation/AppNavHost.kt` con el `NavHost` que arranca en la ruta `inicio` (contenido vacío) y previews de los dos temas; `MainActivity` pasó a `@AndroidEntryPoint` y renderiza el `AppNavHost`.
- Se agregó `values-es-rMX/strings.xml` (textos en español de México); `AppCustodioTheme` ya soporta los dos temas (claro/oscuro).
- El plugin de Crashlytics queda **versionado pero sin aplicar**: tiene fricción conocida con AGP 9 y la consola Firebase no existe todavía (carril A); se aplica en B5.

**Archivos modificados:**
- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ai27/app/custodio/AppCustodioApplication.kt`
- `app/src/main/java/com/ai27/app/custodio/MainActivity.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/res/values-es-rMX/strings.xml`
- `AGENTS.md`

## 2026:09:18 17:20

**Resumen:** Primer commit del repositorio: andamiaje de la app Android, configuración de Firebase, reescritura del plan de diseño y guardarraíles del repo.

**Cambios:**
- Se renombró la rama inicial a `pf-appcus-d`, siguiendo la convención `pf-<abrev>-{d,m,q}` de los repositorios de AI27.
- Se reescribió `PLAN.md` en español con el diseño completo de la app: contrato de datos en Firebase, ciclo de vida del reporte, pipeline de subida progresiva, diseño de la experiencia en campo, volumen y costo, fases de ejecución y tabla de trazabilidad.
- Se movió `google-services.json` de la raíz del repositorio a `app/`, donde el plugin de google-services lo lee.
- Se amplió `.gitignore` con lo necesario para un proyecto Android: `.kotlin/`, credenciales de firma (`*.jks`, `*.keystore`, `keystore.properties`), llaves de cuenta de servicio, artefactos de empaquetado, logs y exportaciones del emulador de Firebase, artefactos de Crashlytics, reportes de lint y cobertura, y volcados de perfilado. `google-services.json` se deja deliberadamente fuera de las exclusiones.
- Se agregó la carpeta `.kiro` con la skill `grill-me` y la regla de steering de CHANGELOG.
- Se creó este archivo `CHANGELOG.md` para cumplir la regla de steering `.kiro/steering/changelog.md`.
- Se versionó el andamiaje generado por Android Studio: plantilla de Jetpack Compose con Material 3, AGP 9.4.1, Kotlin 2.2.10, minSdk 26, target/compileSdk 37 y wrapper de Gradle 9.6.0.

**Archivos modificados:**
- `CHANGELOG.md`
- `PLAN.md`
- `AGENTS.md`
- `.gitignore`
- `.github/pull_request_template.md`
- `.kiro/skills/grill-me/SKILL.md`
- `.kiro/skills/grill-me/agents/openai.yaml`
- `.kiro/steering/changelog.md`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/gradle-daemon-jvm.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`
- `app/.gitignore`
- `app/build.gradle.kts`
- `app/google-services.json`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ai27/app/custodio/MainActivity.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Color.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Theme.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Type.kt`
- `app/src/main/keepRules/rules.keep`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- `app/src/main/res/mipmap-hdpi/ic_launcher.webp`
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-mdpi/ic_launcher.webp`
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.webp`
- `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp`
- `app/src/androidTest/java/com/ai27/app/custodio/ExampleInstrumentedTest.kt`
- `app/src/test/java/com/ai27/app/custodio/ExampleUnitTest.kt`
- `.idea/.gitignore`
