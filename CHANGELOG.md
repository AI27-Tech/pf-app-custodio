# CHANGELOG

Registro de cambios del proyecto App Custodio. Las entradas más recientes van primero.

## 2026:09:19 15:05

**Resumen:** B5: pipeline de subida a Firebase con WorkManager, repositorios de Firestore/Storage, contadores y reintentos con backoff, reintento manual desde la celda FALLIDA, y reglas de seguridad de Firestore y Storage probadas contra el Emulator Suite de Firebase.

**Cambios:**
- Pipeline idempotente de subida: `ReporteSyncProceso` y `SubidaMedioProceso` (orquestadores testables en `data/pipeline/`); el primer archivo etiquetado crea/asegura el reporte en Firestore y encola la subida; cada medio sube con progreso y actualiza los contadores; si el reporte quedó DESCARTADO en el servidor el medio queda PENDIENTE sin subir; cualquier fallo marca el medio como `FALLIDA` (reintentable) y el reporte como `EN_PROGRESO`.
- WorkManager: `WorkerSubidaMedio` y `WorkerSyncReporte` (máx. 4 intentos con backoff exponencial, red CONNECTED), `SubidaScheduler` con unique work por medio/reporte y `reconciliar()` al arrancar, y `CustodioWorkerFactory` con `@Inject` (sin dependencia extra de hilt-work; `AppCustodioApplication` implementa `Configuration.Provider`).
- Remotos: `FirebaseReporteRemoto`/`FirebaseMedioRemoto` idempotentes (upsert de contadores por la regla ACTIVO→CERRADO/CERRADO→CERRADO), `RutasStorage` (`medios/{reporteId}/{fileName}`), `Sha256` para IDs deterministas y mapeo de documentos en `DocumentoMappers`.
- UI: `CapturaViewModel` encola sync al `iniciarViaje` y subida al `guardarEtiqueta`; botón de reintento con badge "FALLIDA" en la celda del medio (encadenado `onReintentar` desde `CapturaScreen`).
- Pruebas JVM (64 verdes): `SubidaMedioProcesoTest` y `ReporteSyncProcesoTest` sobre fakes (remotos falsos, `FakeAlmacenArchivos`) que verifican orden, contadores, reintentos y el caso DESCARTADO en servidor.
- Reglas de seguridad (Emulator Suite): `firebase/firestore.rules` y `firebase/storage.rules` + `firebase/test/reglas.test.js` (31 verificaciones) corridas con `cd firebase && npm test` → `firebase emulators:exec --only firestore,storage --project demo-custodio`. `reportes` create solo ACTIVO y del dueño; updates solo ACTIVO→CERRADO/DESCARTADO/ACTIVO o CERRADO→CERRADO (solo contadores, valores absolutos); delete negado. `medios` create-only del dueño (permite upload tardío a CERRADO); update/delete negados. Storage `medios/{reporteId}/{fileName}`: read/create/update del dueño, delete negado. Nota: en los tests de reglas, `context.firestore()`/`storage()` solo se obtiene **una vez por contexto** (repetirlo lanza "Firestore has already been started" porque vuelve a aplicar `useEmulator`).
- Pila del suite: `@firebase/rules-unit-testing` ^3.0.4 + `firebase` ^10.14.1 + `firebase-tools` ^13.29.0 en `firebase/package.json`; requiere Node y `npm install` la primera vez.
- Pendiente manual (no automatizado): verificación end-to-end en dispositivo real (captura en modo avión → recupera la red → sube solo a Firestore y Storage).

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/AppCustodioApplication.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/archivo/AlmacenArchivos.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/archivo/FilesDirAlmacenArchivos.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/db/MedioDao.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/db/ReporteDao.kt`
- `app/src/main/java/com/ai27/app/custodio/data/remote/AuthDataSource.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomReporteRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/MedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/ReporteRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/usecase/ReintentarSubida.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/data/mapper/DocumentoMappers.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/pipeline/` (nuevo: ReporteSyncProceso, SubidaMedioProceso)
- `app/src/main/java/com/ai27/app/custodio/data/remote/ErroresRemoto.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/remote/FirebaseMedioRemoto.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/remote/FirebaseReporteRemoto.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/remote/MedioRemoto.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/remote/ReporteRemoto.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/remote/RutasStorage.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/util/Sha256.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/worker/` (nuevo: WorkerSubidaMedio, WorkerSyncReporte, SubidaScheduler, CustodioWorkerFactory)
- `app/src/main/java/com/ai27/app/custodio/di/PipelineModule.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/domain/model/SubidaReporte.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/FakeReporteRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/data/pipeline/` (nuevo: FakesPipeline, SubidaMedioProcesoTest, ReporteSyncProcesoTest)
- `firebase/firestore.rules`, `firebase/storage.rules` (nuevos)
- `firebase/firebase.json`, `firebase/package.json`, `firebase/test/reglas.test.js` (nuevos)
- `.gitignore`
- `CHANGELOG.md`

## 2026:09:19 00:51

**Resumen:** B4: autenticación con correo y contraseña (Firebase Auth), identidad real del custodio en lugar de la provisional, sesión persistente que dirige la navegación (login ↔ captura) y logout visible bloqueado mientras haya archivos sin subir, con opción forzada "Salir de todos modos".

**Cambios:**
- Sesión: `SesionRepositorio` (`observarCustodio`/`iniciarSesion`/`cerrarSesion`); `FirebaseAuthSesionRepositorio` con `callbackFlow` para `authStateChanges` y `suspendCancellableCoroutine` para `Task` (sin dependencias nuevas); mapeo puro de códigos de Auth → `CredencialesInvalidas`/`SinConexion`/`Inesperado`.
- Navegación: `RootViewModel` con `EstadoSesion` CARGANDO/NO_SESION/CON_SESION y gate en `AppNavHost` (`LOGIN` → `INICIO`, popUpTo, overlay de carga); pantalla de login con validación y errores legibles.
- Identidad: se retira el custodio provisional `custodio-provisional`; `Custodio(uid, correo)` proviene de la sesión; `IdentidadModule` conserva solo el `deviceId` (`ANDROID_ID`).
- Captura: barra de sesión con correo y botón `Cerrar sesión`; logout con 0 pendientes es inmediato; con pendientes (`estadoSubida != COMPLETA`, JOIN medios↔reportes por custodio) pide confirmación y avisa de la cola; verificación en emulador detectó que las acciones leían el custodio de un estado interno sin poblar → ahora leen del `uiState` combinado.
- Manifest: se agrega permiso `INTERNET` (requerido por Firebase Auth).
- Pruebas: `AuthErrorMappingTest` (mapeo de errores de sesión) y `FakeMedioRepository` con `observarPendientes`; 39 verdes. Verificado en emulador con cuenta real: login, sesión sobrevive a reinicio, bloqueo de logout con pendiente (cancelar y forzado).

**Archivos modificados:**
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/SesionRepositorio.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/domain/repository/MedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/error/DominioError.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/error/DominioErrorMensaje.kt`
- `app/src/main/java/com/ai27/app/custodio/data/remote/AuthDataSource.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/local/db/MedioDao.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/di/AuthModule.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/IdentidadModule.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/login/LoginScreen.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/login/LoginViewModel.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/login/LoginUiState.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/RootViewModel.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaUiState.kt`
- `app/src/test/java/com/ai27/app/custodio/data/remote/AuthErrorMappingTest.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`
- `CHANGELOG.md`

## 2026:09:19 00:18

**Resumen:** B3: pantalla de captura completa (foto y nota de voz), etiquetado obligatorio con chips, miniaturas locales, rejilla de hasta 50 medios con borrado solo antes de subir y cierre del viaje; todo persiste en Room y sobrevive a reiniciar la app.

**Cambios:**
- Captura: `CapturaViewModel`/@HiltViewModel + pantalla Compose: cámara lista vía CameraX (`LifecycleCameraController`, captura en memoria 1920×1080, miniaturas 320 px en `Miniaturas`), disparo → hoja modal de etiqueta (chips del catálogo obligatorios, campo de ubicación como fallback cuando no hay GPS) y grabadora MediaRecorder AAC `.m4a` con temporizador visible y tope de 5 min.
- Rejilla 4 columnas con miniatura (Coil) o ícono de nota, badge de etiqueta y borrado con confirmación solo para medíos `PENDIENTE`; encabezado con placa, contador "X de 50" y botón "Cerrar viaje" (usa `CerrarReporte`, exige ≥1 archivo).
- "Nuevo viaje": formulario de placa cuando no hay reporte activo; navegación arranca en `CapturaRoute`.
- Identidad provisional hasta B4: custodio fijo `custodio-provisional` + `deviceId` de `ANDROID_ID`; cliente provisional.
- GPS por archivo: `ProveedorUbicacion` (FusedLocationProvider 1-shot, ~8 s límite, precisión + antigüedad) con fallback de texto; `ubicacionTexto` se guarda con `EtiquetarMedio`.
- Dominio/datos: `guardar(medio, contenido, miniatura)` persiste `thumbnailPath` y lo borra junto con el archivo en `borrarPendiente`; `etiquetar` acepta `ubicacionTexto` (COALESCE en el DAO).
- Permisos runtime CAMERA/RECORD_AUDIO/ACCESS_FINE_LOCATION en el arranque (+ manifest).
- Dependencias: `play-services-location` `21.4.0` y `material-icons-core` (BOM).
- Pruebas nuevas: miniatura en `CapturarMedioTest`, `RoomMedioRepositoryTest` (escribe y borra archivo+miniatura; `ubicacionTexto` preservado). Verificado en emulador: capturar foto y nota, etiquetar, reiniciar la app y recuperar el viaje activo, borrar pendiente y cerrar viaje.

**Archivos modificados:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/MedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/usecase/CapturarMedio.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/usecase/EtiquetarMedio.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/error/DominioErrorMensaje.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/local/db/MedioDao.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/media/Miniaturas.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/local/media/Grabadora.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/ubicacion/ProveedorUbicacion.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/IdentidadModule.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/UbicacionModule.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/CapturaModule.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/DatabaseModule.kt` (context calificado)
- `app/src/main/java/com/ai27/app/custodio/ui/captura/**` (nuevo: CapturaScreen/Route, CapturaViewModel, CapturaUiState, HojaEtiqueta, VistaCamara)
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/res/drawable/ic_mic.xml`, `ic_foto.xml` (nuevos)
- `app/src/test/java/com/ai27/app/custodio/CapturarMedioTest.kt`
- `app/src/test/java/com/ai27/app/custodio/RoomMedioRepositoryTest.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`
- `CHANGELOG.md`

## 2026:09:18 18:10

**Resumen:** B2: dominio puro (modelos, errores sellados y 8 casos de uso) y persistencia local Room + archivos, con invariante de un solo reporte ACTIVO por custodio garantizado por un índice único parcial.

**Cambios:**
- Dominio puro Kotlin (sin Android/Firebase): `Result<T>`, `DominioError` sellado, modelos `Etiqueta/Gps/Custodio/Reporte/Medio` + enums `Ciclo/EstadoSubida`, límites (`50` medios/reporte, audio ≤ `5 min`), util (zona `America/Mexico_City`, normalización de placas, ventana de 30 días) y repositorios.
- Casos de uso: Crear, CapturarMedio, EtiquetarMedio, Cerrar, Descartar, ReintentarSubida, ObtenerHistorial, ObtenerEtiquetas (validan invariantes y devuelven `Result`).
- Datos: `@Database` con `ReporteEntity`/`MedioEntity` y DAOs suspend/Flow; **índice único parcial** `ux_reportes_activo_por_custodio` en callback `onCreate`/`onOpen` (Room 3 no soporta índices parciales en `@Index`).
- Almacén de archivos `FilesDirAlmacenArchivos` (fotos/audios en `filesDir/medios`), mappers dominio↔entidad, repositorios `RoomReporteRepository`/`RoomMedioRepository`, catálogo de etiquetas provisional y `DatabaseModule` (Hilt, `BundledSQLiteDriver`).
- `Room.databaseBuilder` ahora acepta ruta explícita para reutilizar la misma configuración en pruebas JVM de host.
- Dependencias: `androidx.sqlite:sqlite-bundled` `2.7.0` (runtime + tests JVM de host).
- Pruebas (32, verdes): fakes y cobertura de los 8 casos de uso + `InvarianteUnSoloActivoTest` que ejecuta la carrera de dos reportes activos sobre una base Room real y verifica que queda exactamente uno.

**Archivos modificados:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/ai27/app/custodio/domain/**` (nuevos)
- `app/src/main/java/com/ai27/app/custodio/data/**` (nuevos)
- `app/src/main/java/com/ai27/app/custodio/di/DatabaseModule.kt` (nuevo)
- `app/src/test/**` (nuevos, incluye `jniLibs/libsqliteJni.dylib` para host macOS arm64)
- `CHANGELOG.md`

## 2026:09:18 17:39

**Resumen:** Se corrige el crash al arrancar la app causado por la librería de Crashlytics sin su plugin de Gradle.

**Cambios:**
- Se eliminó `firebase-crashlytics` del runtime y del catálogo de versiones: sin el plugin de Gradle de Crashlytics no se genera el `crashlyticsBuildId` y la app reventaba al iniciar (`IllegalStateException: The Crashlytics build ID is missing`).
- Se mantienen el Firebase BoM y auth/firestore/storage; Crashlytics (librería + plugin) vuelve en B5 conforme al plan.

**Archivos modificados:**
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

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
