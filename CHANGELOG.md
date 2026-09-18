# CHANGELOG

Registro de cambios del proyecto App Custodio. Las entradas más recientes van primero.

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
