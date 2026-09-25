# AUDIT — Echo

Auditoría técnica y de experiencia de uso levantada el **2026-09-21** sobre el estado
del repositorio en el commit `fix: hoja de etiquetado expandida y no descartable…`
(2026-09-21 15:44). Se revisaron las ~10.000 líneas de `app/src/main`, las 26 clases de
prueba, las reglas de Firebase y los scripts de build.

**Fuera de alcance por decisión:** el icono de launcher y la provisión real de la consola
de Firebase son *known issues* y no se reportan aquí.

## Estado de atención (actualizado 2026-09-21)

Los **cinco hallazgos críticos** —los 3 `BLOQUEANTE` y los 2 `CRÍTICO`— quedaron **atendidos**
el 2026-09-21, según el plan registrado en `docs/PLAN.md` §12. Cada uno lleva su marca abajo.

Los otros **90 hallazgos siguen abiertos**. No se tocaron, y eso fue una decisión de alcance
explícita, no un descuido: §12.5 de `docs/PLAN.md` los lista por ID para que nadie los dé por
resueltos al ver este documento.

| Hallazgo | Estado | Evidencia |
| --- | --- | --- |
| B1 · el pipeline no podía subir nada | ✅ atendido | Revertir el arreglo hace fallar 7 de 8 pruebas contra el Emulator Suite |
| B2 · crash al grabar en Android 8-11 | ✅ atendido | Corrección presente y `lintDebug` limpio; comprobación en equipo Android ≤11 **PENDIENTE-HW** (el teléfono es API 36) |
| B3 · gate de release ciego | ✅ atendido | Con el defecto de B2 reintroducido, `assembleRelease` ahora **falla** |
| S1 · foto publicada en la galería compartida | ✅ atendido | Cero referencias en `app/src`; **verificado en el teléfono**: 0 filas en MediaStore de `com.ai27.app.custodio` (15683 imágenes + 1033 audios) |
| S2 · evidencia respaldada a Google | ✅ atendido | `allowBackup="false"` en el manifest fusionado; **`bmgr backupnow` en el teléfono → "Backup is not allowed"** |

El **Grupo 2** (S3–S14) quedó atendido el **2026-09-23** según `docs/PLAN.md` §16; las marcas
viven en cada hallazgo: S3–S10 y S12–S14 `✅ ATENDIDO`, **S11 → Producto** (retención) y
**S13 → Grupo 6** (contexto de Crashlytics con T7). El Grupo 1 quedó atendido el 2026-09-22
(§15, marcas en cada hallazgo). El despliegue de las reglas endurecidas a la consola real se hizo
el **2026-09-23** (Firestore + Storage vía `firebase deploy`, carril A3).

**Aviso que sigue vigente y que conviene no perder de vista:** arreglar B1 hizo que las subidas
funcionen por primera vez, y en ese momento **C1, C2 y C4 empezaron a producir datos incorrectos
en Firestore** — `sha256` nulo y no rellenable, contadores que pueden quedar desfasados, y
reintentos que cancelan subidas en vuelo. Está desarrollado en §12.6 de `docs/PLAN.md`. Son los
primeros candidatos para la siguiente tanda.

**Dos hallazgos nuevos que salieron de ejecutar el plan**, ninguno atendido:

- **`lintVital` solo considera severidad `Fatal`.** `abortOnError = true` **no basta** para que
  un error de lint detenga `assembleRelease`; hay que promover el issue a `Fatal`. Se descubrió
  porque la primera versión del arreglo de B3 no cerraba el gate: el release seguía pasando con
  el defecto de B2 dentro. Afecta a cómo se configure cualquier gate futuro.
- **`FirebaseNetworkException` no se puede instanciar en pruebas JVM de host**, porque su
  constructor llama a `android.text.TextUtils.isEmpty`. Deja una rama de
  `excepcionRemotoAError` sin cubrir por pruebas unitarias; la cubre `IOException`, que entra por
  el mismo `when`.

## Cómo leer este documento

- **Severidad:** `BLOQUEANTE` (impide entregar a campo), `ALTO` (pierde evidencia o
  produce datos falsos), `MEDIO` (degrada el uso o la operación), `BAJO` (deuda).
- **Estado de verificación:** `VERIFICADO` = se leyó el código o se ejecutó el comando y
  se observó el resultado. `ANALIZADO` = derivado por lectura estática, sin ejecución en
  dispositivo ni contra Firebase real. Ningún hallazgo se reproduce en un equipo físico:
  no hubo dispositivo disponible durante la auditoría.
- Las referencias son `ruta:línea` contra el commit indicado.
- Este documento **no** modifica el alcance de `docs/PLAN.md`. Donde una decisión del plan
  choca con un hallazgo, se señala explícitamente.

### Delta del manifest posterior al levantamiento

`app/src/main/AndroidManifest.xml` se editó **después** de levantar los hallazgos y el cambio
se conserva a decisión del autor del proyecto. Respecto del commit base:

- **Se declara `ACCESS_COARSE_LOCATION`.** Resuelve la mitad de S8; la otra mitad sigue abierta,
  porque `permisosSolicitados()` aún no lo pide en runtime.
- **`screenOrientation` pasa de `portrait` a `fullSensor`.** Esto **eleva** A9 de riesgo latente
  en tablets a comportamiento activo en todos los equipos, y con ello A3.9 (cuatro pantallas sin
  `verticalScroll`) y la recreación de CameraX/`MediaRecorder` al rotar. Ver la nota en A9.
- **Se retira el `android:label` redundante** de `MainActivity`, lo que cierra ese punto de T15.

Los hallazgos de abajo describen el código de producción tal como está hoy, con estas tres
diferencias ya incorporadas en S8, A9 y T15.

## Índice

1. [Bloqueantes](#1-bloqueantes)
2. [Pérdida de evidencia](#2-pérdida-de-evidencia)
3. [Consistencia de datos](#3-consistencia-de-datos)
4. [Seguridad y privacidad](#4-seguridad-y-privacidad)
5. [Rendimiento y el estándar de 5 segundos](#5-rendimiento-y-el-estándar-de-5-segundos)
6. [Fricción de uso en campo](#6-fricción-de-uso-en-campo)
7. [Accesibilidad, contraste y adaptación](#7-accesibilidad-contraste-y-adaptación)
8. [Build, release y proceso](#8-build-release-y-proceso)
9. [Cobertura de pruebas](#9-cobertura-de-pruebas)
10. [Lo que está bien y no conviene tocar](#10-lo-que-está-bien-y-no-conviene-tocar)
11. [Orden de atención sugerido](#11-orden-de-atención-sugerido)

---

## 1. Bloqueantes

### B1 · El pipeline de subida no puede funcionar contra Firebase real — BLOQUEANTE · VERIFICADO · ✅ ATENDIDO 2026-09-21

> **Atendido (T6/T7 de `docs/PLAN.md` §12).** Se borró el helper propio y se sustituyó por
> `await()` de `kotlinx-coroutines-play-services` en los 10 puntos de espera de `data/remote`,
> lo que además trae la propagación de cancelación que faltaba. La red de regresión son
> `EsperaDeTareasTest` (4 pruebas JVM) y `RemotoEmuladorTest` (8 pruebas contra el Emulator
> Suite). Comprobado: al reintroducir el helper en un solo punto de llamada, 7 de las 8 fallan
> con `IllegalStateException: Tarea Auth sin resultado ni error`.

`data/remote/AuthDataSource.kt:52-64`

El helper `Task<T>.esperar()` solo resuelve la corrutina cuando
`resultado.isSuccessful && resultado.result != null`; cualquier otro caso cae al `else` y
lanza `IllegalStateException("Tarea Auth sin resultado ni error")`.

`DocumentReference.set()` y `.update()` devuelven `Task<Void>`, y una `Task<Void>`
**exitosa** tiene `getResult() == null`. Por lo tanto, en el camino feliz lanzan excepción:

- `FirebaseReporteRemoto.crear` (`:29`), `cerrar` (`:34`), `descartar` (`:44`),
  `actualizarContadores` (`:60`)
- `FirebaseMedioRemoto.crearDocumentoSiNoExiste`

Consecuencia encadenada, siguiendo `SubidaMedioProceso.ejecutar`: `asegurarReporte()`
corre **antes** de tocar Storage, llama a `crear()`, recibe `Failure`, marca el medio
`FALLIDA` y retorna. **No sale ni un byte a Storage ni un documento a Firestore.** Todo
medio pasa a `FALLIDA` en el primer intento, el badge de pendientes nunca baja, el cierre
de sesión queda bloqueado para siempre y el custodio no puede terminar un viaje.

Los `get()` sí funcionan (`Task<DocumentSnapshot>` con resultado no nulo), igual que
`putBytes` (`Task<TaskSnapshot>`), de ahí que el error no sea evidente por inspección.

Por qué no lo detectó nada: las 71 pruebas unitarias corren contra fakes y `data/remote`
solo tiene una prueba (mapeo de códigos de Auth); las pruebas de reglas corren en Node,
no a través de la app; `assembleRelease` compila en verde.

**Dirección:** separar el caso `Void` (resolver con `cont.resume(resultado.result)`
aceptando null, o usar `await()` de `kotlinx-coroutines-play-services`). De paso, el
helper tampoco cancela la `Task` al cancelarse la corrutina (falta `invokeOnCancellation`),
así que un worker cancelado deja el `UploadTask` corriendo. Cerrar con una prueba de la
capa remota contra el Emulator Suite, que hoy no existe.

### B2 · Crash garantizado al grabar nota de voz en Android 8–11 — BLOQUEANTE · VERIFICADO · ✅ ATENDIDO 2026-09-21

> **Atendido (T2).** La construcción del recorder salió a `nuevoRecorder()`, con gate
> `SDK_INT >= Build.VERSION_CODES.S` y caída al constructor sin contexto en equipos anteriores.
> El `catch (_: Exception)` de `iniciar()` pasó a `catch (_: Throwable)`, porque lo que lanzaba
> era un `NoSuchMethodError`. `lintDebug` ya no reporta `NewApi`.
> **Pendiente manual:** grabar en un equipo con Android ≤11. El teléfono de pruebas es API 36,
> así que la rama del constructor antiguo no se ejercitó.

`data/local/media/GrabadoraMediaRecorder.kt:22`

`MediaRecorder(context)` es el constructor **añadido en API 31**. El `minSdk` del proyecto
es 26 y no hay ningún gate de `Build.VERSION.SDK_INT` alrededor (los dos únicos gates del
proyecto están en `CapturaEvidenciaScreen.kt:114` y `FotosPublicasMediaStore.kt:24`).

Agravante: el `catch (_: Exception)` que lo envuelve no ayuda, porque el runtime lanza
`NoSuchMethodError`, que es un `Error` y no un `Exception`. **La app se cierra.** Cualquier
equipo del parque con Android 8, 9, 10 u 11 pierde la nota de voz y muere en el intento.

`lintDebug` lo reporta como `NewApi`. Ver B3 para entender por qué no frenó el release.

**Dirección:** `if (SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()`,
y cambiar ese `catch` a `Throwable`.

### B3 · El gate de lint del release es ciego a 141 errores — BLOQUEANTE · VERIFICADO · ✅ ATENDIDO 2026-09-21

> **Atendido (T10).** Se borró `res/values-es-rMX/` (17 claves duplicadas que generaban 138 de
> los 141 errores) y se arregló el único error real que quedaba, el `MissingPermission` de
> `ProveedorUbicacion`, con una comprobación real de permiso. lint quedó en **cero errores**, así
> que no se generó ningún baseline.
> **Corrección al diagnóstico original:** cerrar el gate exige más que `abortOnError = true`.
> `lintVital` solo considera severidad `Fatal`, y `NewApi` es `Error`, así que con solo
> `abortOnError` el release **seguía pasando** con el defecto de B2 dentro (comprobado). Lo que
> lo cierra es `fatal += setOf("NewApi", "MissingPermission", …)`. Con eso, reintroducir B2 hace
> fallar `assembleRelease`.

| Comando | Resultado observado |
|---|---|
| `./gradlew lintDebug` | **FALLA** — `Lint found 141 errors, 34 warnings, 1 hint` |
| `./gradlew lintVitalRelease` | **BUILD SUCCESSFUL in 19s** |
| `./gradlew assembleRelease` | **BUILD SUCCESSFUL** — APK de 22.15 MiB, firmado |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** — 71 pruebas, 0 fallos |

`lintVital` —lo que corre `assembleRelease`— solo aborta con issues de severidad `Fatal`,
y los 141 son `Error`. No hay bloque `lint { }` en `app/build.gradle.kts`. B2 se puede
empaquetar y entregar a campo sin que nada lo detecte.

Además, **138 de los 141 son ruido** de `MissingTranslation`: `res/values/strings.xml`
tiene 151 strings y `res/values-es-rMX/strings.xml` tiene 17, todos duplicados idénticos
en español. La carpeta es 100 % redundante (el default ya es español) y su único efecto es
hacer fallar `lintDebug` y esconder los 3 errores reales.

Trampa adicional de esa carpeta: si algún día se corrige el copy de una de esas 17 claves
en `values/`, el cambio **no se verá** en un teléfono es-MX. El síntoma será "cambié el
texto y no cambió".

**Dirección:** borrar `res/values-es-rMX/` completa y añadir
`lint { checkReleaseBuilds = true; abortOnError = true; baseline = file("lint-baseline.xml") }`.

---

## 2. Pérdida de evidencia

La premisa del producto es que la evidencia llega. Estos hallazgos la contradicen.

### E1 · El doble toque del disparador descarta fotos en silencio — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-21

`ui/viaje/CapturaEvidenciaScreen.kt:136-140` + `ui/captura/CapturaViewModel.kt:155-162`

`disparoHabilitado` depende de `estado.capturando`, pero `capturando` solo se pone en
`true` **dentro** del callback de éxito de CameraX, cientos de milisegundos después del
toque. No hay debounce local ni bandera síncrona. El custodio toca 3 veces —comportamiento
normal cuando el visor no responde—, se lanzan 3 `takePicture`, y la 2ª y 3ª caen en el
`return` silencioso del ViewModel: **dos fotos se descartan sin ningún aviso**.

Si dos sí pasan el guard, la segunda sobreescribe `medioPendienteDeEtiquetar` y la primera
queda persistida sin etiqueta (ver E3).

Efecto colateral de rendimiento: dos pipelines de ~20 MB de bitmaps concurrentes en el
pool `Default` (ver R4), con riesgo real de `OutOfMemoryError` en equipos de 2 GB.

### E2 · Todo fallo de cámara y de procesamiento es mudo — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-21

`ui/captura/VistaCamara.kt:79` es `catch (_: Exception) {}` y `:82-83` es
`override fun onError(exception: ImageCaptureException) {}`, ambos **vacíos**.

Un OOM, un error del HAL de la cámara o un fallo de decodificación producen exactamente
cero: sin flash, sin diálogo, sin log, sin Crashlytics. El custodio toca y no pasa nada, y
no hay forma de diagnosticarlo después desde el campo. Es el modo de fallo más caro del
producto: el custodio cree que la evidencia existe.

### E3 · Los medios sin etiquetar suben igual, y bloquean la sesión para siempre — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-21

`ui/captura/CapturaViewModel.kt:298-304` + `data/worker/WorkManagerSubidaScheduler.kt:43-46`

`descartarEtiquetado()` (la X de la hoja) deja el medio persistido en Room con
`etiqueta = null` y nunca lo encola. Pero `reconciliar()` encola **todos** los
`estadoSubida != COMPLETA`, **sin filtrar por etiqueta**, y `firestore.rules` no exige el
campo. Al siguiente arranque ese medio sube con `etiqueta = null`: se rompe la invariante
que la hoja modal defiende con tanta agresividad y el analista recibe evidencia sin
clasificar.

Lo mismo ocurre con cualquier medio capturado cuando el ViewModel muere con la hoja
abierta (cierre de sesión, muerte de proceso): `medioPendienteDeEtiquetar` vive solo en
memoria, así que **la hoja no se vuelve a abrir nunca** y no existe ninguna pantalla para
etiquetar después (`ArchivosScreen` no marca "sin etiquetar" ni ofrece etiquetar).

Consecuencia operativa en cascada: ese medio cuenta como pendiente **para siempre**
(`MedioDao` define pendiente como todo lo `!= COMPLETA`), y como el cierre de sesión se
deshabilita con `pendientesSinSubir == 0`, **el custodio no puede volver a salir** sin
adivinar que la salida es ir a Archivos y borrar esa celda.

**Dirección:** derivar "pendiente de etiquetar" de Room (`WHERE etiqueta IS NULL`) para que
la hoja reabra al relanzar; bloquear el encolado de medios sin etiqueta en `reconciliar()`
y en `SubidaMedioProceso`; mostrarlos en la rejilla con acción "etiquetar".

**Cómo quedó atendido (§13 del plan, 2026-09-21).** La cola excluye los medios sin etiqueta
(`MedioDao.obtenerPendientes` exige `etiqueta IS NOT NULL`) y `SubidaMedioProceso` repite el
guard como defensa en profundidad. Para que dejen de ser un callejón sin salida, la rejilla
de Archivos los marca con `SIN ETIQUETA`, los vuelve tocables, hospeda la hoja de etiquetado
y avisa arriba cuántos faltan; el mismo aviso aparece en el hub del viaje, y en Pendientes
esos medios dicen "Falta etiqueta" en lugar de mentir con "En cola".

Dos desvíos respecto a la dirección propuesta, a propósito:

- La lista no se deriva con una consulta aparte sino filtrando los `medios` que el estado ya
  tiene (están acotados a 50). Se llegó a añadir `observarSinEtiquetaDelReporte` al DAO y se
  **retiró** al ver que quedaba sin llamadas: una query de DAO sin uso es peor que ninguna.
- **El badge sigue contándolos.** Un medio sin etiquetar es trabajo sin terminar, y permitir
  cerrar sesión con evidencia sin clasificar sería cambiar un problema por otro. Lo que
  cambia es que ahora se pueden resolver con un toque, así que la sesión se desbloquea
  etiquetando en lugar de borrando. La queja de U6 (el badge mezcla tres cosas distintas)
  sigue abierta.

### E4 · Disco lleno tira la app en pleno disparo — ALTO · ANALIZADO · ✅ ATENDIDO 2026-09-21

`data/local/archivo/FilesDirAlmacenArchivos.kt:11-16` → `data/repository/RoomMedioRepository.kt:23-26`
→ `domain/usecase/CapturarMedio.kt` → `ui/captura/CapturaViewModel.kt:162-177`

`archivo.writeBytes(contenido)` no está envuelto en `try/catch` en ningún punto de la
cadena, y `capturarFoto` lo invoca dentro de un `viewModelScope.launch` desnudo. El
`IOException` escapa de la corrutina y **tira la app en el momento del disparo**. El
`Result`/`DominioError` del dominio nunca se usa para errores de E/S (verificado por grep:
cero `try/catch` en `domain/usecase`, `data/repository` y `data/pipeline`).

El escenario es realista porque **nada purga nunca**: las miniaturas no se borran tras
subir (`RoomMedioRepository.kt:94-95`), y cada foto se escribe además una segunda vez en
la galería pública (ver S1).

### E5 · La nota de voz graba silencio, o audio corrupto, sin que nadie lo note — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-22

`data/local/media/GrabadoraMediaRecorder.kt` (completo) + `ui/viaje/AudioScreen.kt`

No hay `setOnErrorListener`, ni `setOnInfoListener`, ni observador de ciclo de vida, ni
`onCleared`, ni servicio en primer plano, ni `foregroundServiceType` en el manifest
(verificado por grep: cero `setForeground`/`ForegroundInfo`/`getForegroundInfo` en
`app/src/main`).

- **App en segundo plano o pantalla apagada:** desde Android 9 el micrófono se corta para
  apps sin FGS. La grabación **continúa, el cronómetro sigue subiendo y el archivo
  resultante es silencio**. Se guarda y se sube como evidencia válida.
- **Llamada entrante:** sin listener de error, `detenerInterno()` se come la excepción de
  `stop()` y `detener()` lee el `.m4a` truncado. Como `bytes.isNotEmpty()` es cierto, **se
  persiste y sube un audio corrupto**.
- **Muerte de proceso grabando:** el audio se pierde entero y el `nota-*.m4a` queda
  huérfano en `cacheDir`; nada lo limpia.
- **Salir de AudioScreen grabando:** la grabación sigue sin indicador. Y si el grafo
  `viaje` se descarta (`popUpTo(Rutas.VIAJE){inclusive=true}` al cerrar sesión), el
  ViewModel muere **sin `onCleared`** → el `MediaRecorder` nunca se libera y, al ser
  `Grabadora` un `@Singleton`, el micrófono queda tomado hasta que muere el proceso.
- **Desincronía:** `grabandoAudio` vive en el ViewModel y `enGrabacion` en el singleton; la
  UI nunca lee `grabadora.grabando`. Tras una recreación del ViewModel, AudioScreen dice
  "toca para iniciar" mientras el micrófono sigue grabando.

Añadido de rendimiento: `iniciar()` se llama fuera de cualquier `launch`
(`CapturaViewModel.kt:198`) y `detener()` corre en `Dispatchers.Main.immediate`
(`:228`) haciendo `MediaRecorder.stop()` (bloqueante, documentado) más un `readBytes()`
de hasta ~2,4 MB. Riesgo de ANR en el toque de "detener".

**Cómo quedó atendido (§13 del plan, 2026-09-22).** `Grabadora` gana `pausar`/`reanudar`/
`pausada`, un `Flow<EventoGrabacion>` y `barrerNotasHuerfanas()`; todas las operaciones son
`suspend` sobre `Dispatchers.IO`, lo que cierra el añadido de rendimiento. En la
implementación se cablearon `setOnErrorListener`, `setOnInfoListener` y `setMaxDuration`, y
la duración pasa a contarse **por segmentos** para que el cronómetro no sume el tiempo en
segundo plano. `AudioScreen` observa el ciclo de vida y pausa en `ON_STOP`; el ViewModel
recoge los eventos —un fallo descarta la nota, avisa y va a Crashlytics; el tope de 5 minutos
la guarda—, y `onCleared` suelta el micrófono.

Decisiones que acotan el arreglo, y lo que siguen dejando abierto:

- **Se pausa, no se graba en segundo plano** (R2 de §13.2). Grabar con la app atrás exige un
  servicio en primer plano, con notificación permanente y las restricciones de FGS de
  targetSdk 34+. La consecuencia es que el custodio que se distraiga tiene que volver y
  tocar para continuar; a cambio, la app nunca vuelve a producir silencio.
- **Una nota interrumpida por muerte del proceso sigue siendo irrecuperable** (R3). `pause()`
  no escribe el índice del MPEG-4, así que ese `.m4a` no se puede leer por más que se
  conserve. Lo que cambia es que ya no desaparece en silencio: el archivo suelto en caché
  funciona como marca, el barrido de arranque lo borra y la pantalla de audio **lo avisa**.
- **La desincronía de estado se resolvió por un lado solo:** la UI sigue leyendo el estado del
  ViewModel, no `grabadora.grabando`. Ya no divergen en el caso descrito porque la pausa y el
  fallo actualizan ambos, pero el estado sigue duplicado. Queda como deuda, no como defecto
  activo.

### E6 · En equipo compartido, la evidencia del custodio anterior se quema — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-21

`data/worker/WorkManagerSubidaScheduler.kt:43-46` + `ui/ajustes/AjustesViewModel.kt:69-82`

`reconciliar()` usa `obtenerPendientesSubida()` y `obtenerPendientes()`, **ninguna filtrada
por `custodioUid`** ni por fecha. Si A deja pendientes y B inicia sesión en el mismo
teléfono, la app reencola la evidencia de A con el token de B: las reglas la deniegan
correctamente, y **la evidencia de A queda `FALLIDA` para siempre**, invisible para B (su
badge filtra por uid) e imposible de borrar (`borrarPendiente` solo borra `PENDIENTE`).

`cerrarSesion()` es `auth.signOut()` y nada más: sin `cancelAllWork`, sin limpieza de Room,
sin borrado de `filesDir`, sin tocar la galería pública. Los workers en cola siguen
corriendo sin usuario autenticado, son rechazados y marcan `FALLIDA`.

Sin límite de fecha, además, **cada arranque reencola todos los reportes históricos**: a
los meses son cientos de trabajos únicos por lanzamiento. `descartar` no toca `subida`, y
`computarEstado` devuelve `PENDIENTE` para lista vacía, así que esos reportes nunca
alcanzan `subida = COMPLETA` y nunca salen de la cola.

**Cómo quedó atendido (§13 del plan, 2026-09-21).** Las consultas se acotaron por
`custodioUid` con un `JOIN` contra `reportes` y por una ventana de 30 días, y
`cerrarSesion` cancela el trabajo de subida por etiqueta antes del `signOut`. **No se
borran datos locales**, a diferencia de lo que sugería este hallazgo: al implementarlo se
vio que el purgado era innecesario y peligroso. Innecesario porque el aislamiento ya lo da
el filtro por uid y porque `marcarSubidaCompleta` ya borra el archivo grande, así que en
disco solo quedan miniaturas. Peligroso porque Historial lee **solo** de Room y no hay
código que reconstruya nada desde Firestore: purgar filas borraría 30 días de historial de
forma irrecuperable en la app, y si el custodio saliente tenía un viaje `ACTIVO` se lo
llevaría con él. El purgado pasa a Producto como decisión de **retención de datos**, no
como arreglo de bug; ver R3 bis en `docs/PLAN.md` §13.2.

### E7 · El guard de cierre de sesión con pendientes es código muerto — MEDIO · VERIFICADO · ✅ ATENDIDO 2026-09-21

`ui/ajustes/AjustesViewModel.kt:60-63`

`solicitarCerrarSesion()` evalúa `interno.value.pendientesSinSubir`, pero el conteo solo se
inyecta en el `uiState` **derivado** por `combine`; `interno` nunca lo recibe, así que
**siempre vale 0** y la condición jamás frena. Lo único que bloquea de verdad es
`habilitado = estado.pendientesSinSubir == 0` en `AjustesScreen.kt:118-122`. La invariante
que el código dice defender no existe, y cualquier ruta futura (un "forzar salida") pasa
de largo.

Incoherencia asociada: `Dialogos.kt:113-152` tiene una rama "Salir de todos modos" para
`pendientes > 0` que es inalcanzable, porque el botón que abre el diálogo está
deshabilitado precisamente en ese caso.

### E8 · Medios pegados en `EN_PROGRESO` sin rescate — MEDIO · ANALIZADO

`ReintentarSubida.kt:19` devuelve `MedioEnSubida` para cualquier medio `EN_PROGRESO`, así
que no se puede reintentar a mano. El único camino es "recargar" en Pendientes, que además
cancela las subidas sanas en curso (ver C4). Si el proceso muere con medios `EN_PROGRESO` y
el usuario no vuelve a entrar al grafo `viaje`, nada los rescata: `reconciliar()` no se
llama al arrancar el proceso ni al recuperar red (existe `ConectividadMonitor` pero **no
está conectado al scheduler**).

**Dirección:** al arrancar, degradar `EN_PROGRESO → PENDIENTE` para medios sin worker vivo
(`getWorkInfosForUniqueWork`) o con marca de tiempo vencida.

### E9 · Archivos huérfanos y crecimiento sin techo de `filesDir` — MEDIO · VERIFICADO

`data/repository/RoomMedioRepository.kt:23-40`, `:88-99`

- No hay **ni un solo `@Transaction`** en todo el proyecto (verificado por grep). `guardar`
  escribe archivo → escribe miniatura → inserta fila; el rollback solo existe en la rama
  de `insertar` fallido. Si muere el proceso entre medias, o si falla la miniatura, quedan
  bytes en `filesDir/medios` sin fila que los referencie. Desde el punto de vista del
  producto la foto **sí se perdió** (invisible en la rejilla, nunca se sube) aunque los
  bytes estén en disco.
- **Las miniaturas son inmortales:** `marcarSubidaCompleta` borra el original y conserva
  deliberadamente la miniatura, y nada la borra al cerrar o archivar el viaje. Con 50
  medios por viaje y meses de operación en un equipo compartido, `filesDir/medios` crece
  sin techo hasta llenar el almacenamiento — y entonces la captura empieza a fallar (E4).
  Es el problema silencioso más probable a 3-6 meses.
- No existe ninguna rutina de limpieza ni de reconciliación de archivos.

**Dirección:** un barrido al arrancar que (a) borre archivos sin fila, (b) borre miniaturas
de reportes en ciclo terminal con subida `COMPLETA`, y (c) marque y reporte las filas
`PENDIENTE` cuyo `localPath` no existe, con una salida que permita al custodio descartar y
seguir en lugar de quedarse bloqueado.

### E10 · Trabajo crítico en `viewModelScope`, cancelable a mitad — ALTO · ANALIZADO

Todas las escrituras salen de la UI: `iniciarViaje`, `capturarFoto`,
`detenerGrabacionDeAudio`, `guardarEtiqueta`, `cerrarViaje`, `descartarViaje`. El
`CapturaViewModel` cuelga de la entrada del grafo `viaje`, así que
`popUpTo(Rutas.VIAJE){inclusive=true}` (cierre de sesión) limpia el `ViewModelStore` y
cancela el `viewModelScope`.

El caso más caro: `guardarEtiqueta` (`CapturaViewModel.kt:269-296`) actualiza Room, luego
hace `trabajosGps.remove(medioId)?.join()` (hasta 8 s de espera) y **después**
`encolarSubidaMedio`. Si el ViewModel muere en esa ventana, el medio queda etiquetado y
`PENDIENTE` pero **nunca encolado** — y la UI ya había dicho "se subirá en breve". Igual
`cerrarViaje`/`descartarViaje`: el reporte se cierra en Room y `sincronizarReporte` no
llega a encolarse, así que el analista no ve el cierre.

**Dirección:** sacar de `viewModelScope` la sección crítica "persistir medio + encolar" y
"cerrar reporte + sincronizar", a un scope de aplicación o a WorkManager, y dejar que la UI
solo dispare intención.

### E11 · La sesión perdida en caliente destruye el viaje en curso — ALTO · ANALIZADO

`ui/navigation/AppNavHost.kt:294-307` + `data/remote/AuthDataSource.kt:22-35`

Si Firebase emite `currentUser == null` (contraseña cambiada, cuenta deshabilitada, token
revocado), `EstadoSesion.NO_SESION` dispara `popUpTo(Rutas.VIAJE){inclusive=true}`: al
custodio se lo saca a login **con un viaje abierto**, se destruye el `CapturaViewModel`, se
cancelan las escrituras en vuelo, se abandona el medio sin etiquetar y se pierde la
grabación en curso. Sin aviso y sin forma de recuperar el etiquetado.

**Dirección:** distinguir "sin sesión inicial" de "sesión perdida en caliente"; en el
segundo caso conservar el grafo de viaje y mostrar un diálogo bloqueante de
re-autenticación sobre la pantalla actual.

### E12 · Auto Backup puede resucitar filas sin archivos — ALTO · VERIFICADO

Ver S2 (es a la vez un hallazgo de privacidad y de pérdida de evidencia). Un restore deja
filas `PENDIENTE` cuyo `localPath` —guardado como ruta **absoluta**— apunta a archivos que
no existen, o a otro perfil de usuario (`/data/user/0/…` vs `/data/user/10/…`). El pipeline
no crashea, marca `FALLIDA`, y el custodio queda con evidencia fantasma que el reintento
manual nunca podrá resolver y con un badge que nunca baja. Un restore puede además
reintroducir un reporte `ACTIVO` y chocar con el `CREATE UNIQUE INDEX` de `onOpen`
(ver D2).


---

## 3. Consistencia de datos

Estos hallazgos hacen que el panel del analista muestre algo distinto de la realidad. Dado
que §1 del plan dice que el entregable es "un conjunto de datos consultable y filtrable",
son fallas de producto, no de implementación.

### C1 · El resultado de `actualizarContadores` se descarta, y el reporte sale de la reconciliación para siempre — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-22

> **Atendido (T1/T2 de `docs/PLAN.md` §15).** `SubidaMedioProceso.ejecutar` devuelve lo que
> propage `reporteRemoto.actualizarContadores(...)`: si el remoto falla con un error
> transitorio el worker reintenta (criterio en C5), ya no se reporta éxito falso. Y la
> reconciliación dejó de filtrar por el flag local: `obtenerPendientesSubida` emite medios y
> reportes por **divergencia con el remoto** (ver C3), y un reporte cuyo remoto ya está
> `COMPLETA` pero local diverge se auto-sana propagando contadores sin re-subir (hallazgo
> C2 del proceso; test `un_medio_ya_COMPLETA_no_se_vuelve_a_subir`). Evidencia: suite
> `ReconciliacionColaTest` (10 pruebas, verde en emulador el 2026-09-22).

`data/pipeline/SubidaMedioProceso.kt:119` llama `reporteRemoto.actualizarContadores(...)`
**sin mirar su `Result`**, y `ejecutar` devuelve `Result.Success` igual. Si esa escritura
falla (red, reglas, B1), el worker reporta éxito.

Y como `reporteRepository.actualizarSubida` ya escribió local, cuando el medio era el último
el reporte queda con `subida = COMPLETA` en Room, y `obtenerPendientesSubida`
(`ReporteDao.kt:62`) filtra por `subida != 'COMPLETA'` → **el reporte deja de ser candidato
a reconciliación permanentemente**. El panel ve `mediosSubidos = 2/5`, `completo = false`,
con toda la evidencia ya en Storage.

**Dirección:** propagar el `Failure` (devolver retry) o encolar `sincronizarReporte` al
completar cada medio, y basar la reconciliación en "divergencia con el remoto" en lugar del
flag local.

### C2 · El `sha256` nunca llega al servidor, y no se puede rellenar después — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-22

> **Atendido (T1 de `docs/PLAN.md` §15).** El sha se calcula antes de construir el documento
> y viaja en él: es la red de regresión `DocumentoMappersTest` (3 pruebas JVM) y
> `RemotoEmuladorTest.el_sha256_viaja_en_el_documento_del_medio` contra el Emulator Suite
> (verde el 2026-09-22). Se conserva la regla create-only: el sha queda persistido en el
> primer `set()`.

`data/pipeline/SubidaMedioProceso.kt:47` calcula `sha`, pero `:56` llama
`crearDocumentoSiNoExiste(reporte, medio, rutas)` pasando el `medio` leído de Room **antes**
de la subida, cuyo `sha256` es `null`; `DocumentoMappers.kt:59` escribe ese null. El hash
solo se guarda local, en `marcarSubidaCompleta`.

Y `firestore.rules:94` prohíbe `update` en `medios`, así que **es imposible rellenarlo**.
Para evidencia de cadena de custodia, el documento remoto queda sin integridad verificable.

**Dirección:** calcular el sha antes de construir el documento y pasarlo al mapper.

### C3 · Carrera de lost-update en los contadores — ALTO · ANALIZADO · ✅ ATENDIDO 2026-09-22

> **Atendido (T2 de `docs/PLAN.md` §15).** `FirebaseReporteRemoto.actualizarContadores`
> escribe en una **transacción de Firestore** un merge monótono: `subida` solo avanza
> (COMPLETA es terminal), `mediosSubidos` usa `max(actual, solicitado)` y `completo` nunca
> vuelve a `false`. El write es remote-first: el medio sube primero (doc remoto) y los
> contadores se calculan sobre lo que el remoto confirma, no sobre una fotografía de Room.
> `obtenerPendientesSubida` pasó a depender de la divergencia con el remoto (subida de C1).
> Evidencia: `RemotoEmuladorTest.actualizarContadores_es_monotono_ante_escrituras_intercaladas`
> contra el Suite (verde el 2026-09-22).

Cada medio tiene su propio unique work, así que WorkManager los corre en paralelo.
`actualizarContadores` no usa `FieldValue.increment` ni transacción: recomputa el valor
absoluto desde Room y lo escribe. Dos workers intercalados: A lee 3, B lee 4 y escribe 4, A
escribe 3 → **`mediosSubidos` retrocede** y `completo` se queda en `false` de forma
permanente (nada lo vuelve a calcular; ver C1).

Nota: el escenario de *doble incremento* que se podría temer **no ocurre**, precisamente por
recalcular el valor absoluto. El problema es el inverso.

**Dirección:** transacción de Firestore, escritura monótona (solo avanzar), o serializar los
contadores en un único worker de sync encolado tras cada medio.

### C4 · `ExistingWorkPolicy.REPLACE` cancela subidas en vuelo — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-22

> **Atendido (T4 de `docs/PLAN.md` §15).** `WorkManagerSubidaScheduler` usa
> `ExistingWorkPolicy.KEEP` tanto en `encolarSubidaMedio` como en `sincronizarReporte`: el
> re-encolado de reconciliación (Pull-to-refresh de Pendientes, arranque) ya no mata la
> ejecución en curso. KEEP decide atómicamente en el enqueue, así que el guard previo de
> `getWorkInfosForUniqueWork` (un check-then-act con su propia carrera) se eliminó.
> Caracterización volteada en `ReconciliacionColaTest.C4_encolar_de_nuevo_NO_reemplaza_el_trabajo_en_vuelo`:
> sin `CANCELLED` y queda exactamente 1 trabajo, el original (verde en emulador el
> 2026-09-22).

`data/worker/WorkManagerSubidaScheduler.kt:31` y `:40`. Encolar el mismo `medioId` mata la
ejecución en curso. Y `reconciliar()` re-encola **todos** los pendientes, incluidos los
`EN_PROGRESO`; se invoca desde `CapturaViewModel.init` y desde `PendientesViewModel.recargar()`
(el pull-to-refresh de Pendientes).

En campo: el custodio ve "subiendo", abre Pendientes y tira a refrescar → **cancela las
subidas reales y las reinicia desde el byte cero**. Con `putBytes` no reanudable
(ver R5) y 26–50 MB por viaje sobre cobertura pobre, esto puede convertirse en un livelock
que quema datos sin terminar nunca.

**Dirección:** `ExistingWorkPolicy.KEEP` para el re-encolado de reconciliación y reintento;
reservar `REPLACE`, si acaso, para cambios reales de input.

### C5 · Tope de 4 intentos en ~70 s, sin clasificar permanente vs transitorio — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-22

> **Atendido (T5 de `docs/PLAN.md` §15).** Se quitó `MAX_INTENTOS` y se clasifica por kind:
> `ErroresRemoto.excepcionRemotoAError` mapea `IOException`/`FirebaseNetworkException` →
> `SinConexion` y `"UNAVAILABLE"`/`"DEADLINE_EXCEEDED"` → `ServidorIndisponible` (transitorios:
> `Result.retry()` sin tope, la constraint de red evita el spin); `"PERMISSION_DENIED"` →
> `PermisoDenegado` y archivo ausente → `ArchivoLocalAusente` (permanentes: `Result.failure()`
> inmediato, `registrarFallo` persiste `ultimoError`/`intentosSubida` en `medios` —migración
> Room 2→3— y `RegistroDeFallos` manda la causa a Crashlytics). La celda FALLIDA ya no es
> ciega: se reintenta a mano desde Pendientes. La clasificación JVM prueba por código de
> error en string (`FirebaseFirestoreException`/`Code` no se instancian fuera del device),
> rama de device en `WorkerSubidaMedioTest` (6, verde el 2026-09-22).

`MAX_INTENTOS = 4` con `BackoffPolicy.EXPONENTIAL, 10s` ≈ 10+20+40 s. Un bache de cobertura
de dos minutos agota los intentos en ~70 s y el medio queda `FALLIDA` hasta que alguien lo
reintente a mano. (Los 10 reintentos de WorkManager nunca se alcanzan: el tope propio
dispara antes.)

`ErroresRemoto.kt:8-11` solo distingue `IOException`/`FirebaseNetworkException` →
`SinConexion`; todo lo demás (`PERMISSION_DENIED`, `UNAVAILABLE`, `DEADLINE_EXCEEDED`,
archivo ausente) cae en `Inesperado` y consume los mismos 4 intentos. La causa **no se
registra en ningún lado**: no hay `Crashlytics.recordException` ni razón persistida en la
fila. La celda solo dice `FALLIDA`, así que el soporte queda a ciegas y no puede distinguir
"sin red" de "las reglas rechazan" de "archivo corrupto".

**Dirección:** para `SinConexion`, reintentar sin tope con backoff largo (la constraint de
red ya evita el spin); para errores permanentes, `Result.failure()` inmediato. Persistir
`ultimoError`/`intentos` en `medios` y mandar la excepción a Crashlytics.

### C6 · El cierre no es transaccional: un reporte puede marcarse completo sin estarlo — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-22

> **Atendido (T3 de `docs/PLAN.md` §15).** El cierre es ahora un **solo `UPDATE` atómico del
> DAO** que deriva los conteos del estado presente de `medios` en el mismo statement
> (`SELECT COUNT(*)` + `SUM(subido)` en subconsultas, `completo = subidos >= esperados`):
> un solo statement de SQLite es igual o más atómico que `@Transaction`, y elimina la ventana
> entre "leer lista" y "marcar CERRADO". Contrato determinista nuevo en
> `ContratoRepositoriosTest`: `cerrar cuenta los medios presentes al momento del UPDATE, no
> una lectura previa` (siembra 2 medios, cierra, y el `resumenMedios`/`completo` reflejan la
> fotografía del UPDATE, no una previa). `ContratoFakesTest` y `CerrarReporteTest` corren el
> mismo contrato con `mediosConsultables`.

`domain/usecase/CerrarReporte.kt:32-49` lee la lista de medios y **después** llama
`dao.cerrar`; `CapturarMedio.kt:37-72` valida `ciclo == ACTIVO` y **después** inserta. Son
dos secuencias sin transacción compartida, y no hay `@Transaction` en el proyecto.

Una foto capturada en la ventana intermedia se inserta con el reporte ya cerrado y queda
fuera del conteo. Efectos:

- `mediosEsperados = N` con N+1 medios reales → `SubidaReporte.completo`
  (`subidos >= esperados`) se vuelve `true` con evidencia todavía en vuelo: **el analista ve
  el viaje completo antes de tiempo**.
- `resumenMedios` subcuenta, y solo se escribe remoto en el cierre; las reglas ya no
  permiten corregirlo en `CERRADO` → **queda mal reportado de forma permanente**.

**Dirección:** cierre en una transacción de Room que derive los conteos con `SELECT COUNT(*)`
y marque `ciclo='CERRADO'` en el mismo paso, y que la inserción de captura valide el ciclo
dentro de esa misma transacción.

### C7 · `crear()` con `serverTimestamp` puede chocar con las reglas — MEDIO · ANALIZADO

`FirebaseReporteRemoto.kt:28-31` hace un `set()` completo cuyo mapa incluye
`createdAtServer = FieldValue.serverTimestamp()`. Si el documento ya existe, ese `set` es un
*update* que siempre ensucia `createdAtServer`, y `transicionValida` (`firestore.rules:71-81`)
solo permite ACTIVO→ACTIVO si `affectedKeys().hasOnly(['subida','mediosSubidos','completo'])`
→ `PERMISSION_DENIED`.

Ocurre cuando dos flujos creen que el doc no existe: `WorkerSyncReporte` y el
`asegurarReporte` de `WorkerSubidaMedio` no tienen dependencia entre sí. Si el perdedor es
el sync del cierre, el viaje nunca se marca `CERRADO` remoto con su comentario y
`resumenMedios`.

**Dirección:** tratar el "ya existe" como éxito y no reenviar `serverTimestamp` en
reintentos.

### C8 · La confirmación de existencia puede venir de la caché de Firestore — MEDIO · ANALIZADO

`FirebaseMedioRemoto.kt:40` usa `doc.get()` con `Source.DEFAULT`, que offline responde desde
caché **incluyendo mutaciones locales pendientes**. Un reintento puede ver `exists() == true`
por una escritura que el servidor nunca confirmó, seguir hasta `marcarSubidaCompleta` y
**borrar el archivo local**. Si esa mutación se rechaza luego, queda el binario en Storage
sin documento, sin archivo local y con Room diciendo `COMPLETA` → evidencia irrecuperable e
invisible. Mismo patrón en `FirebaseReporteRemoto.kt:20` (`obtenerCiclo` puede devolver un
ciclo stale).

Aparte de esto, **el orden del borrado local es correcto**: nunca se borra antes de que
Storage y el documento reporten éxito.

**Dirección:** `Source.SERVER` para los `get()` de decisión (si falla, tratar como
`SinConexion` y reintentar).

### C9 · Detalles menores del mismo bloque — BAJO · ANALIZADO

- `SubidaReporte.kt:7-13` prioriza `EN_PROGRESO` sobre `FALLIDA`, así que un reporte con una
  falla real se muestra "en progreso" mientras otro medio suba.
- `MedioDao.kt:32` (`etiquetar`) no tiene guarda de estado, así que reetiquetar después de
  subir divierte local vs. remoto sin remedio (`medios` es create-only). El GPS sí está
  protegido (`:39`, solo `PENDIENTE`).
- `ReporteSyncProceso.conContadores()` escribe el estado local **antes** de
  `reporteRemoto.cerrar()`: si el remoto falla, Room queda diciendo `COMPLETA` y el servidor
  nunca recibió el cierre.
- Un cierre de viaje sin sincronizar es **invisible** para el custodio: la pantalla de
  pendientes solo lista medios, y el bloqueo de cierre de sesión solo cuenta medios, no
  reportes sin sincronizar.
- `sincronizarReporte(...)` y `copiarAPublica(...)` se invocan **dentro** del lambda de
  `interno.update` (`CapturaViewModel.kt:175, 347, 368`). `MutableStateFlow.update` puede
  re-ejecutar el lambda bajo contención → encolado duplicado. Hoy es inocuo por `REPLACE`,
  pero es un efecto secundario en un lambda que debe ser puro.

### C10 · La migración 1→2/2→3 crashea en una base v1 real por el índice parcial manual — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-22

> **Atendido.** Cada migración ahora empieza con `DROP INDEX IF EXISTS
> ux_reportes_activo_por_custodio`; la validación de Room ve `indices = {}` y pasa, y el
> `onOpen` (que corre después del migration+validation) lo recrea. Regresión cubierta en
> `MigracionIndiceActivoTest` (2 pruebas): la nueva prueba **falla** sin las dos líneas de
> `DROP` (comprobado revertiéndolas en el momento) y **pasa** con ellas. Descubierto en el
> teléfono de prueba (CPH2769): la base real v1 migró a v3 en vivo (`user_version 1→3` por
> WAL) y la app abrió sin FATAL.

`AppCustodioDatabase.kt:65-100` (MIGRACION_1_2/2_3 + `INDICE_UNICO_ACTIVO`). El índice único
parcial `ux_reportes_activo_por_custodio` se crea a mano (`onCreate`/`onOpen`, no declarable
en Room 3 porque `androidx.room3.Index` no tiene `where`). La validación post-migración
compara `indices` de `reportes` contra lo que las entidades declaran —**vacío**—, así que
**cualquier base v1 real** (con el índice persistido por un `onOpen` anterior) reventaba al
abrir en §14 con `IllegalStateException: Migration didn't properly handle: reportes`. Los
tests de migración de host no lo vieron: sus fixtures v1 nunca incluían el índice. En el
teléfono la DB estaba en `user_version = 1` (nunca migró) con el índice en disco.

La prueba config del driver (sqlite-bundled) arroja `getText == ""` para NULL, no `null`;
los tests de migración normalizan con `takeIf { it.isNotEmpty() }`.

---

## 4. Seguridad y privacidad

El modelo de autorización (dueño por `custodioUid`, nada se borra, analista por correo) está
bien pensado y probado. Los agujeros están en la ausencia de validación de forma y tamaño,
en la copia pública de la evidencia y en la falta de limpieza al rotar de custodio.

### S1 · Cada foto se publica en la galería pública del equipo compartido — CRÍTICO · VERIFICADO · ✅ ATENDIDO 2026-09-21

> **Atendido (T3).** Se eliminó por completo, en los 9 puntos de contacto: borrados
> `FotosPublicas.kt` y `FotosPublicasMediaStore.kt`, fuera el provider de Hilt, fuera el
> parámetro/llamada/método de `CapturaViewModel`, fuera `WRITE_EXTERNAL_STORAGE` del manifest,
> simplificado `permisosSolicitados()`, y fuera el fake y sus usos. Verificado por búsqueda: cero
> referencias en `app/src`. De paso desaparecieron la tercera escritura a disco por foto (parte de
> R5), el único `Log.` de `main/` (S13) y el efecto secundario dentro del lambda de
> `interno.update` (parte de C9).
> **Verificado en dispositivo (2026-09-22, teléfono CPH2769).** Con el teléfono conectado se
> comprobó sobre MediaStore real: la app guarda la evidencia en `files/medios` (privado) y la
> galería pública tiene **0 filas** que referencien `com.ai27.app.custodio` entre 15683
> imágenes y 1033 audios indexados. Las únicas entradas públicas son de otras apps
> (`Pictures/Reddit`, `Pictures/Instagram`, `Music/...`).
> **Aviso operativo:** en equipos ya instalados, las copias de versiones anteriores **siguen** en
> `Pictures/AppCustodio` y hay que borrarlas a mano al actualizar.

`data/local/media/FotosPublicasMediaStore.kt:21-70`, cableado **sin condición** en
`di/CapturaModule.kt:69`, invocado en `ui/captura/CapturaViewModel.kt:178 → copiarAPublica():398-407`.

Cada foto se escribe **siempre** una segunda vez en `Pictures/AppCustodio` vía
`MediaStore.Images.Media.EXTERNAL_CONTENT_URI` (API 29+) o
`getExternalStoragePublicDirectory()` + `MediaScannerConnection` (API 26-28). No hay flag,
ajuste ni condición de build.

- Contradice la premisa "`filesDir` privado" de `docs/PLAN.md` §4. Esa copia es legible por
  la galería, por WhatsApp, por cualquier app con `READ_MEDIA_IMAGES`, por el backup de
  Google Fotos si está activo, y por el siguiente custodio que tome el equipo.
- **La copia nunca se borra:** ni al eliminar un medio pre-subida, ni al descartar un
  reporte, ni al cerrar sesión. El borrado "pre-subida" de la UI es **cosmético**: la foto
  sigue en la galería.
- Si el equipo se pierde o va a mantenimiento, la evidencia sale sin ninguna barrera (la app
  no tiene PIN).
- En API 26-28 es lo único que justifica `WRITE_EXTERNAL_STORAGE`.

**Dirección:** eliminar la copia, o volverla opt-in explícita y escribirla en
`getExternalFilesDir` fuera de `Images`; si se conserva, borrarla en el mismo flujo que borra
el medio local.

### S2 · `allowBackup="true"` con reglas de backup vacías — CRÍTICO · VERIFICADO · ✅ ATENDIDO 2026-09-21

> **Atendido (T4).** `android:allowBackup="false"` (confirmado en el manifest fusionado), más
> exclusiones explícitas de `app_custodio.db` con sus `-wal`/`-shm` y del directorio `medios` en
> **los dos** XML, y en `data_extraction_rules.xml` tanto en `cloud-backup` como en
> `device-transfer`. Las exclusiones son defensa en profundidad: con `allowBackup="false"` no se
> ejecutan, pero si alguien reactiva el respaldo la evidencia sigue fuera.
> **Consecuencia aceptada:** la preferencia de tema nocturno ya no sobrevive a una reinstalación.
> **Verificado en dispositivo (2026-09-22).** En el teléfono conectado, `adb shell bmgr
> enable true; adb shell bmgr backupnow com.ai27.app.custodio` responde *"Package
> com.ai27.app.custodio with result: Backup is not allowed"* y el transport termina con
> *"Backup finished with result: Success"*: el device transport confirma que el paquete está
> excluido del Auto Backup (allowBackup `false`).

`AndroidManifest.xml:19-22` declara `allowBackup="true"` con `dataExtractionRules` y
`fullBackupContent`, pero **ambos XML son la plantilla de Android Studio con todo
comentado**: `backup_rules.xml` tiene un `<full-backup-content>` vacío y
`data_extraction_rules.xml` un `<cloud-backup>` vacío con el `TODO`.

Un `<full-backup-content>` sin reglas **no excluye nada**: significa "respalda todo lo
respaldable por defecto", es decir la base `app_custodio.db`, las SharedPreferences **y**
`filesDir/medios`. La evidencia y los metadatos GPS de clientes salen hacia la cuenta de
Google **del equipo compartido**, fuera del control de la empresa, sin que nadie lo decida.
También habilita `adb backup` en equipos con depuración activa.

Contradice `docs/PLAN.md` §4, que dice explícitamente que `filesDir` está "excluido de Auto
Backup". Ver E12 para el lado de pérdida de evidencia.

**Dirección:** `android:allowBackup="false"` es la postura correcta para una app de
evidencia con pipeline propio. Si se quiere conservar backup, hay que excluir
`domain="database" path="app_custodio.db"` y `domain="file" path="medios"` en **los dos** XML
(`backup_rules.xml` gobierna API ≤30, `data_extraction_rules.xml` API ≥31, y hay que cubrir
`cloud-backup` **y** `device-transfer`) — respaldar solo la base es peor que no respaldar
nada. Y pasar `localPath`/`thumbnailPath` a ruta **relativa** resuelta contra `filesDir`.

### S3 · Storage sin tope de tamaño ni validación de `contentType` — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-4 de `docs/PLAN.md` §16).** `storage.rules` ganó `esArchivoPermitido()`:
> `FOTO` ≤ 12 MB y `image/jpeg`, `AUDIO` ≤ 25 MB y `audio/mp4`, con el `{tipo}` de la ruta
> acoplado al prefijo del nombre (`medios/{reporteId}/{medioId}[.jpg|-min.jpg|.m4a]`), de modo
> que la lista blanca y la ruta no se pueden desalinear. Casos en `reglas.test.js` y round-trip
> de `RemotoEmuladorTest`.

`firebase/storage.rules:34-38`. `allow create, update` solo comprueba autenticación y dueño
del reporte: no hay `request.resource.size < N`, ni
`request.resource.contentType.matches(...)`, ni validación del nombre de archivo.

Un custodio (o un APK modificado con sus credenciales, o un bug) puede subir un archivo de
2 GB o de cualquier tipo bajo `medios/{reporteId}/`. Con 37 cuentas es facturación abierta
en Storage y egress, y un vector de almacenamiento arbitrario en infraestructura de la
empresa. El panel del analista recibiría URLs de descarga de tipo arbitrario.

**Dirección:** tope duro por tipo (p. ej. ≤12 MB foto, ≤25 MB audio) y lista blanca de
`contentType`. Es una regla, no requiere cambios en la app.

### S4 · `update` en Storage permite sobrescribir evidencia ya cerrada — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-5 de `docs/PLAN.md` §16).** `update`/`delete` en Storage están **negados**;
> solo se permite `create` con `resource == null` (idempotencia contra ausencia del objeto) y
> el `update` del documento `medios` —create-only— nunca reescribe la ruta. El reintento de la
> app es un `subirSiNoExiste` (`FirebaseMedioRemoto`), probado en arnés y en dispositivo.

`firebase/storage.rules:37`, **probado y aceptado** en `firebase/test/reglas.test.js:190`
("dueño sobrescribe en reintento (update)").

`update` existe para permitir reintentos idempotentes, pero la regla no distingue "reintento
de un archivo que aún no terminó" de "reemplazo de evidencia que el analista ya vio", ni
consulta el `ciclo` del reporte: un reporte `CERRADO` o `DESCARTADO` sigue aceptando
sobrescrituras indefinidamente.

La cadena de custodia no es inmutable. Un custodio con motivos puede reemplazar la foto
incriminatoria días después, y el documento `medios/{id}` —create-only, con `sha256`— seguirá
apuntando al mismo `storagePath` con un hash que ya no corresponde. Es **peor que un
borrado, porque es silencioso**. (Y hoy el `sha256` remoto es null de todos modos; ver C2.)

**Dirección:** negar `update` (`allow create: if … && resource == null`) y hacer el reintento
idempotente contra la ausencia del objeto. Si `update` debe quedar, condicionarlo a
`firestore.get(...).data.ciclo == 'ACTIVO'`.

### S5 · Firestore: cero validación de forma, tipo y tamaño — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-3 de `docs/PLAN.md` §16).** `hasOnly()` con las llaves exactas del contrato,
> tipos por campo (`is string`/`is int`/`is timestamp`), enums (`ciclo`, `tipo`, `origen`,
> `subida`), rango y monotonía de contadores (S6), y `storagePath`/`thumbnailPath` anclados a
> `medios/{reporteId}/` con `matches`. Los campos nullable del contrato se aceptan **null de
> verdad** (`gps`, `ubicacionTexto`, `comentario`, `sizeBytes`, `durationMs`, `sha256`,
> `thumbnailPath`, `mediosEsperados`, `closedAt`, `motivoDescarte`) porque la app los escribe
> así en el create (ver hallazgo de `thumbnailPath` en §16.4). Cerrado con 89 casos en el arnés.

`firebase/firestore.rules:63-67` (create de reporte) y `:88-90` (create de medio).

`create` de reporte solo exige `custodioUid == request.auth.uid` y `ciclo == 'ACTIVO'`.
`create` de medio solo exige dueño + que el reporte le pertenezca. No se valida ninguna
llave más: ni tipos, ni longitud de strings, ni `hasOnly()`, ni que `storagePath` empiece
con `medios/{reporteId}/`, ni rangos de `mediosEsperados`, ni que `lat`/`lng` sean números.

Un cliente comprometido puede escribir documentos con campos arbitrarios y strings de MB en
la colección que consume el panel (DoS del panel, inyección de contenido en su UI,
`storagePath` apuntando a otro reporte para que el panel cargue evidencia cruzada).

**Dirección:** `hasOnly()` con la lista de llaves esperadas, `is string`/`is int`/`is timestamp`
en los campos que el panel usa, y anclar
`storagePath.matches('^medios/' + request.resource.data.reporteId + '/.*')`.

### S6 · `mediosSubidos` y `completo` son contadores libres, sin cota — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-2 de `docs/PLAN.md` §16; coordina con C3).** `contadoresValidos()` exige
> monotonía (`nuevo.mediosSubidos >= previo.mediosSubidos`), cota
> (`<= previo/actual mediosEsperados`), `subida in [PENDIENTE, EN_PROGRESO, COMPLETA, FALLIDA]`
> y `completo == (mediosSubidos == mediosEsperados)`. El cliente sube con merge monótono
> (C3), así que reintentos no reportan falsos COMPLETA. Tests de arnés actualizados al
> contrato real (ver §16.4).

`firebase/firestore.rules:70-78`. `transicionValida()` restringe **qué llaves** cambian pero
no **a qué valores**. Nada impide `mediosSubidos: 99999`, `completo: true` con cero archivos
subidos, ni que el contador retroceda. Tampoco se valida `subida` contra un enum.

El panel puede mostrar un reporte como "completo" cuando falta evidencia, que es
exactamente el indicador operativo en el que se va a confiar para cerrar un turno. Es una
falsificación de estado, no solo un dato sucio.

**Dirección:** exigir monotonía y cota:
`nuevo.mediosSubidos >= previo.mediosSubidos && nuevo.mediosSubidos <= previo.mediosEsperados`,
`nuevo.subida in [...]`, y `completo == (nuevo.mediosSubidos == previo.mediosEsperados)`.

### S7 · `ACTIVO → CERRADO` no acota qué más cambia en la misma escritura — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-1 de `docs/PLAN.md` §16).** `soloCambia()` con lista blanca por transición:
> cierre (`ciclo`, `closedAt`, `comentario`, `hayNovedad`, `mediosEsperados`, `resumenMedios`,
> `subida`, `mediosSubidos`, `completo`), descarte (`ciclo`, `closedAt`, `motivoDescarte`) y
> contadores (los tres de hoy); `custodioUid`/`placas`/`fechaLocal`/`createdAtDevice` no
> aparecen en `affectedKeys()`. Pruebas de transición inválida en el arnés.

`firebase/firestore.rules:73-75`. Las dos primeras ramas de `transicionValida()` solo miran
el par `(previo.ciclo, nuevo.ciclo)`. En el mismo `update` que pasa a `CERRADO` se puede
reescribir **cualquier** campo: `placas`, `custodioCorreo`, `clienteId`, `fechaLocal`,
`createdAtDevice`, `mediosEsperados`. `soloCambia(...)` solo se aplica a las ramas de
contadores.

La prueba "CERRADO no edita placas" (`reglas.test.js:130`) cubre `CERRADO→CERRADO`, no
`ACTIVO→CERRADO`, **así que este hueco pasa la suite en verde**. Toda la trazabilidad del
reporte depende de campos que son mutables una vez.

**Dirección:** acotar cada transición con su propio `soloCambia(...)` (cierre:
`['ciclo','closedAt','comentario','hayNovedad','mediosEsperados','resumenMedios']`; descarte:
`['ciclo','closedAt','motivoDescarte']`) y verificar que `custodioUid` y `placas` nunca
aparecen en `affectedKeys()`.

### S8 · `ACCESS_COARSE_LOCATION` se declara pero no se solicita — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-6 de `docs/PLAN.md` §16).** `permisosSolicitados()` y `permisosActuales()`
> (`CapturaEvidenciaScreen`) incluyen `ACCESS_COARSE_LOCATION` y la petición automática se
> dispara si falta cámara, micrófono **o** ubicación. `ProveedorUbicacion.prioridadDeUbicacion()`
> usa `PRIORITY_HIGH_ACCURACY` con FINE y `PRIORITY_BALANCED_POWER_ACCURACY` con solo COARSE
> (que también encaja con R13). Re-chequeo en `ON_RESUME`.

`AndroidManifest.xml:10` **sí** declara `ACCESS_COARSE_LOCATION` (añadido después del
levantamiento; ver el delta del manifest arriba). Pero
`ui/viaje/CapturaEvidenciaScreen.kt:108-116` `permisosSolicitados()` sigue devolviendo solo
`CAMERA`, `RECORD_AUDIO` y `ACCESS_FINE_LOCATION`, así que **el hallazgo sigue abierto**: la
declaración sin la solicitud no cambia el comportamiento en runtime.

La documentación oficial de Android indica pedir ambos permisos en **la misma** solicitud,
porque en algunas versiones de Android 12 el sistema **ignora** una petición que solo incluya
`FINE`. Y si el custodio elige "Aproximada" en el diálogo, `FINE` queda denegado: ahora `COARSE`
es concedible, pero la app nunca lo pide ni lo consulta
(`permisosActuales()` solo comprueba cámara y micrófono), así que el resultado práctico sigue
siendo **sin ubicación, sin explicación**. El GPS por archivo es el diferenciador contra
WhatsApp (`docs/PLAN.md` §1).

Además, el único re-lanzamiento del diálogo (`:171-173`) está detrás del estado "cámara
denegada": si la cámara está concedida y la ubicación no, **nunca se vuelve a pedir**.

Relacionado: `ProveedorUbicacion.kt:27` llama `getCurrentLocation(...)` sin
`checkSelfPermission` previo (lint `MissingPermission`) → `SecurityException` si el permiso
no está; y `tarea.result` dentro del `addOnCompleteListener` lanza
`RuntimeExecutionException` si la Task falló, sin `try`.

**Dirección:** añadir `ACCESS_COARSE_LOCATION` a `permisosSolicitados()`, comprobarlo en
`permisosActuales()`, y degradar a `PRIORITY_BALANCED` cuando solo haya COARSE concedido (lo
que encaja con el fix de R13).

### S9 · Sin rationale y sin salida del "no volver a preguntar" — MEDIO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-7 de `docs/PLAN.md` §16).** La pantalla de captura con cámara denegada muestra
> rationale + "Permitir cámara" + `BotonSecundario` "Abrir ajustes del sistema"
> (`ui/components/AjustesDelSistema.kt`, `ACTION_APPLICATION_DETAILS_SETTINGS`), y las pantallas
> de captura y audio **re-chequean permisos en `ON_RESUME`**, así que conceder desde Ajustes
> actualiza la UI al volver. AudioScreen pide `RECORD_AUDIO` por su cuenta (S10).

No existe `shouldShowRequestPermissionRationale` en todo el proyecto (verificado por grep).
Tras la segunda negación, `lanzadorPermisos.launch(...)` retorna denegado al instante: el
botón "Permitir cámara" (`CapturaEvidenciaScreen.kt:172`) queda **muerto** y la pantalla es
inutilizable sin ruta alguna a los Ajustes del sistema. Con 37 custodios y equipos
compartidos, un solo mal toque en el aprovisionamiento deja el equipo sin cámara para todo
el viaje.

Se piden además 3-4 permisos de golpe sin explicar ninguno, lo que maximiza la negación. Y
el estado de permisos solo se recalcula en el callback del launcher: si el custodio los
concede desde los Ajustes de Android y vuelve, la pantalla sigue diciendo "sin cámara"
(falta re-chequeo en `ON_RESUME`).

### S10 · Sin permiso de micrófono no hay forma de concederlo desde Audio — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-7 de `docs/PLAN.md` §16).** `AudioScreen` solicita `RECORD_AUDIO` por su
> cuenta: `rememberLauncherForActivityResult(RequestPermission)`, franja de aviso
> `audio_sin_microfono` + botones "Permitir micrófono" y "Abrir ajustes del sistema" en el
> estado idle, y re-chequeo en `ON_RESUME`.

`RECORD_AUDIO` solo se solicita en `CapturaEvidenciaScreen`. Si el custodio entra directo
Reporte → Nota de voz (camino válido en `AppNavHost`), o si negó el micrófono antes,
`grabadora.iniciar()` falla, se muestra `error_grabacion_no_inicio` y **no hay ningún botón
para pedir el permiso**. Es exactamente el caso de un equipo recién provisionado.

### S11 · Rotación de custodio: no se limpia nada — ALTO · VERIFICADO · → PRODUCTO (retención)

> **Reclasificado (G2-9 de `docs/PLAN.md` §16; D6).** El grueso quedó atendido por E6 (§13):
> la cola se acota por `custodioUid` y fecha, y **no se purga** por decisión — los archivos son
> inmutables. El residuo (archivos locales y miniaturas acumulándose entre custodios) es una
> decisión de **retención de datos**, no una tarea de implementación, y pasa a Producto junto
> con las de `docs/PLAN.md` §3.2. No se salida de la auditoría: se mueve a las decisiones de
> producto pendientes.

Ver E6. Qué queda del custodio anterior accesible al siguiente: todas las filas de Room
(la UI las filtra por uid, pero siguen en disco, en claro, sin cifrado — Room sin SQLCipher),
los JPEG/M4A en `filesDir/medios`, y las copias en `Pictures/AppCustodio`, que **sí** son
visibles. El dispositivo acumula datos de clientes de las 37 personas indefinidamente.

### S12 · `ModoEmulador` depende de un meta-data, no de `BuildConfig.DEBUG` — MEDIO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-8 de `docs/PLAN.md` §16).** `ModoEmulador.activo` pasó a
> `BuildConfig.DEBUG && meta-data modo_emulador` (`buildConfig = true` en `app/build.gradle.kts`,
> habilita con pausa de Sync respetada). La condición emulador ahora es inspeccionable y no
> puede viajar al release: sin `DEBUG` el `meta-data` no activa nada.

`di/ModoEmulador.kt:12-21`, `app/src/debug/AndroidManifest.xml:4-8`, consumido en
`di/AuthModule.kt:22-24`. Es correcto **hoy**: el meta-data solo existe en el manifest de
debug, y se verificó que el manifest principal no referencia
`network_security_config.xml` (el cleartext **no** viaja al release). Pero la garantía es
frágil y no inspeccionable en el binario: un `<meta-data>` añadido por error a `src/main` o
por un manifest de librería apunta el release a `127.0.0.1`, y con R8 desactivado las
constantes de host/puerto y la llamada a `useEmulator` viajan en el APK de producción.

**Dirección:** envolver en `BuildConfig.DEBUG && activo(context)`.

### S13 · Crashlytics sin contexto y con PII residual — MEDIO · VERIFICADO · → GRUPO 6

> **Reclasificado (G2-9 de `docs/PLAN.md` §16; D6).** La recolección incondicional en equipos
> de empresa queda **escrita como defendible** (D6) y no se toca: en campo la app no debe
> colgar del consentimiento. El contexto (`setUserId`/`setCustomKey`/`recordException` y el
> `RecordException` de `RegistroDeFallos`) se cierra con el contexto de Crashlytics del Grupo 6
> (T7), donde además vive el `mappingFileUploadEnabled`. La PII residual (trazas con rutas de
> `filesDir/medios`) se acota con lo que T7 decida exponer.

No hay `setUserId`, `setCustomKey` ni `recordException` en ningún punto del código
(verificado por grep), así que no se envía PII deliberadamente. Dos vías residuales:
`DominioError.Inesperado(e)` envuelve excepciones arbitrarias cuyas trazas pueden incluir
rutas de `filesDir/medios/{medioId}` y mensajes de Firebase con el uid; y el único
`Log.` del proyecto (`CapturaViewModel.kt:406`) registra `error.message` de la copia pública
(sin correo, uid ni coordenadas; en API 26+ LogCat no es legible por otras apps).

La recolección está en `true` de forma incondicional, sin consentimiento. Para dispositivos
de la empresa es defendible; conviene que quede escrito.

### S14 · Casos de abuso que `reglas.test.js` no cubre — MEDIO · VERIFICADO · ✅ ATENDIDO 2026-09-23

> **Atendido (G2-0 de `docs/PLAN.md` §16).** El arnés se endureció (sin estado compartido entre
> casos) y subió de 48 a **89 casos**: cubre los vectores S3–S7 (tope de tamaño/`contentType`,
> transiciones inválidas, `soloCambia`, contadores, forma/tipo, rutas ancladas, `hasOnly`) y
> cada regla nueva entró con su prueba (D2). Además el arnés destapó el bug de `thumbnailPath`
> null en AUDIO (§16.4), con regresión también en dispositivo.

El archivo tiene buena cobertura de autorización (48 casos) pero no prueba ninguno de los
vectores de S3–S7. Faltan concretamente:

- `ACTIVO → CERRADO` cambiando `placas`/`custodioUid`/`createdAtDevice` en la misma
  escritura (S7).
- `mediosSubidos` inflado, `completo: true` sin archivos, contador que retrocede, `subida`
  fuera del enum (S6).
- Reporte con campos extra, strings enormes, tipos incorrectos (`mediosEsperados: "diez"`),
  `storagePath` apuntando a otro reporte (S5).
- Storage: archivo grande y `contentType` no permitido (S3); sobrescritura de un archivo de
  un reporte ya `CERRADO`/`DESCARTADO` (S4).
- Transiciones inválidas: `CERRADO → ACTIVO`, `DESCARTADO → CERRADO`, `ACTIVO → 'INVENTADO'`.
- `medios` cuyo `reporteId` apunta a un reporte inexistente (`reportePerteneceA` hace
  `get().data.custodioUid` sin comprobar existencia: revienta en lugar de negar limpiamente;
  es el quirk ya documentado en `AGENTS.md`).
- Un analista **retirado** de la lista (regresión de revocación), y un token sin `email`.
- Variación de mayúsculas en el correo del analista (`Ana@AI27.mx`).
- `usuarios/{uid}`: `create` está permitido con el propio uid **sin validar contenido** — un
  custodio puede escribir `{rol: 'analista'}` en su propio documento. Hoy no se usa para
  autorizar (la lista es por correo), pero si el panel llega a leer `rol` de ahí, es una
  escalada de privilegios.

**Fragilidad del arnés (MEDIO):** `reglas.test.js` no es un test runner; es un script con
contador `fallos` y **estado compartido en orden de ejecución** (`reportes/r-a` se muta a
`CERRADO` a mitad del archivo y los casos posteriores dependen de eso). Insertar un caso en
medio rompe los siguientes. Migrar a Mocha/Jest con `beforeEach` + seed elimina eso.


---

## 5. Rendimiento y el estándar de 5 segundos

El estándar declarado en `docs/PLAN.md` §7 es: **abrir la app y tener una foto etiquetada y
encolada en menos de cinco segundos.** Hoy no se cumple de forma fiable, y nadie lo está
midiendo desde el código.

### R1 · La app no abre en la cámara — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-A)

`ui/navigation/AppNavHost.kt:143` arranca en `LOGIN`; cuando la sesión resuelve, un
`LaunchedEffect` navega a `INICIO`, que es la pestaña **Reporte** (pantalla intermedia de
resumen), no el visor. El plan dice explícitamente: "Con reporte activo, la app abre en la
cámara lista para disparar. No en un menú, no en una lista."

Secuencia real con reporte activo: **4 toques y dos transiciones de pantalla**, con CameraX
vinculando **en frío después del primer toque** (el disparador está deshabilitado hasta que
`controllerCamara != null`).

| # | Acción | Espera |
|---|---|---|
| 0 | Abrir app | spinner mientras `EstadoSesion.CARGANDO` + resolución de Auth/Room |
| 1 | "Capturar evidencia" | `navigate` + montaje de `PreviewView` + `bindToLifecycle` (300-800 ms en gama baja) |
| 2 | Disparador | obturador (~200-400 ms) + decodificar/rotar/recomprimir/miniatura (~300-600 ms) |
| 3 | Chip de etiqueta | — |
| 4 | "Guardar" | `etiquetarMedio` + `join()` del job de GPS (**hasta 8 s**) antes de encolar |

Suma realista en un equipo barato con viaje ya abierto: **~3-5 s**, es decir justo en el
límite, y **por encima** con arranque en frío, diálogo de permisos, o si el GPS no fijó y hay
que teclear la ubicación. La cámara **no se pre-calienta** en ningún momento.

### R2 · "Sin reporte activo" se muestra aunque haya un viaje abierto — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-A)

`ui/viaje/ReporteScreen.kt:64-82` + `ui/captura/CapturaViewModel.kt:98-105`

`uiState` arranca en `CapturaUiState()` con `reporte = null`, y `ReporteScreen` **no
distingue "todavía no sé" de "no hay viaje"**: al abrir la app con un viaje activo se ve
"Sin reporte activo / Iniciar nuevo reporte" durante el primer frame a cientos de ms. Un
custodio apurado toca ese botón, teclea la placa, y recibe el diálogo
`error_ya_existe_activo` ("Ya hay un viaje en curso en este teléfono").

Falta un estado de carga explícito en la raíz del flujo.

### R3 · El grafo de navegación se reconstruye en cada emisión de la cola — ALTO · VERIFICADO

`ui/navigation/AppNavHost.kt:112-114`

`AppNavHost` instancia `PendientesViewModel` en la raíz y observa la **lista completa** de
medios pendientes (`List<Medio>`, no un conteo) solo para pintar `pendientes.size` en
`BarraNavegacion`. El `builder` del `NavHost` captura esa lista. `NavHost` guarda el grafo con
`remember(route, startDestination, builder)`; como `List<Medio>` es inestable y con strong
skipping se compara por identidad, **cada emisión de Room produce un `builder` nuevo → el
`remember` se invalida → el `NavGraph` se vuelve a crear**.

Durante una sesión de 50 evidencias son del orden de 100-150 emisiones
(`PENDIENTE → EN_PROGRESO → COMPLETA` por medio, más el badge del reporte), cada una
recomponiendo `Scaffold` + `NavHost` completos y reconstruyendo 10 destinos, justo cuando la
CPU ya está comprimiendo y subiendo. Añade el riesgo funcional de reasignar el grafo durante
una transición — el propio `entradaDelGrafoViaje` (`:96-105`) documenta que la pila es frágil
en ese momento.

Es la peor lectura de estado del proyecto. `entradaViaje` (`:120`) además recalcula
`getBackStackEntry` en cada recomposición de la raíz, sin `remember`.

**Dirección:** exponer un `StateFlow<Int>` con un `COUNT` y leerlo dentro del `bottomBar`, no
en el cuerpo de `AppNavHost`.

### R4 · Cada foto se decodifica, rota y recomprime: ~20 MB de pico y EXIF perdido — ALTO · VERIFICADO

`ui/captura/VistaCamara.kt:88-94` + `data/local/media/Miniaturas.kt:11-32`

`takePicture` en memoria entrega un `ImageProxy` **que ya es JPEG**. El código lo decodifica
(`toBitmap()` = 8,3 MB para 1920×1080 ARGB_8888), crea una **segunda** copia rotada con
matriz (otros 8,3 MB, el original no se recicla), la recomprime a JPEG q88, crea un **tercer**
bitmap escalado y comprime la miniatura, con dos `ByteArrayOutputStream` que se duplican en
`toByteArray()`. Pico transitorio **18-20 MB por disparo**, 300-600 ms de CPU, y pérdida de
calidad por doble compresión.

Con el doble disparo de E1, son dos pipelines concurrentes: **40 MB transitorios** sobre un
heap de ~96-128 MB que además tiene los búferes de CameraX y la caché de Coil.

**El JPEG guardado no tiene EXIF.** Al recomprimir desde `Bitmap` se descarta todo:
marca de tiempo, modelo de equipo, orientación, exposición. Para una app de cadena de
custodia, el archivo entregado al analista no tiene metadatos verificables propios; todo
depende de la fila de Firestore.

**Dirección:** leer los bytes JPEG del buffer sin decodificar, escribir la orientación como
tag EXIF en vez de rotar píxeles, generar la miniatura con `BitmapFactory` + `inSampleSize`,
y limitar el paralelismo a 1.

### R5 · La misma foto se materializa 3 veces en memoria y se escribe 3 veces a disco — ALTO · VERIFICADO

Por cada foto: JPEG de cámara → bitmap → JPEG#2 → escritura en `filesDir/medios/{id}` →
escritura de la miniatura → **tercera escritura completa en MediaStore** (`copiarAPublica`,
ver S1) → y más tarde el worker lo **vuelve a leer entero** (`leerContenido`) para el SHA-256
y para la subida, con `Sha256.digestHex` sobre el `ByteArray` completo, no en streaming.

`FirebaseMedioRemoto.kt:24` usa `putBytes` con el archivo completo en memoria: **cada
reintento re-sube desde el byte cero** (no hay sesión reanudable), y con varios workers en
paralelo hay N archivos completos en RAM. El límite de 10 minutos de ejecución de un worker
puede matar una subida lenta en 2G y dejar el medio en `EN_PROGRESO`.

Relevante para `targetSdk 37`: Android 17 aplica **límites de memoria por app según la RAM
del dispositivo, a todas las apps sin importar targetSdk**. Estos picos pueden terminar en
kill del proceso con `REASON_OTHER` + `MemoryLimiter:AnonSwap` en `ApplicationExitInfo` — y
sin custom keys de Crashlytics (S13) sería invisible.

**Dirección:** `putFile(Uri)` con sesión reanudable persistida, SHA en streaming, y límite de
concurrencia de subida.

### R6 · Ningún Flow se recoge con conciencia de ciclo de vida — ALTO · VERIFICADO

La dependencia `lifecycle-runtime-compose` **no está** en `app/build.gradle.kts`, así que
`collectAsStateWithLifecycle` no existe en el proyecto (grep: cero apariciones). Todo usa
`collectAsState()`.

Con la app en segundo plano (llamada, WhatsApp, pantalla apagada) la composición sigue viva y
los colectores suscritos, así que el `SharingStarted.WhileSubscribed(5_000)` que ya está
escrito en los tres ViewModels **nunca se relaja: es decorativo**. Durante una subida de 50
medios con la pantalla apagada, cada transición de estado dispara 3 consultas Room +
`combine` + materialización de hasta 50 `Medio` y agenda recomposiciones. Consumo de CPU y
batería en el peor momento posible: un custodio en ruta con pocas horas de batería.

El cronómetro de audio (1 Hz, `CapturaViewModel.kt:202-212`) sigue tictaqueando y emitiendo
en segundo plano por el mismo motivo.

### R7 · El flash del obturador recompone la pantalla completa a 60 fps — MEDIO · VERIFICADO

`ui/viaje/CapturaEvidenciaScreen.kt:120, 225`: `flash` es un `Animatable(0f)` y su valor se
lee con `flash.value` **en el cuerpo del composable**, no dentro de un `drawBehind`/
`graphicsLayer`. Cada frame de la animación de 280 ms invalida **todo
`CapturaEvidenciaScreen`**: `BarraSuperior`, el `Surface` del obturador, `AvisoSubidaFlotante`,
los `stringResource`, el cálculo de `disparoHabilitado`. Son ~17 recomposiciones de pantalla
completa en el instante exacto en que la CPU comprime dos JPEG y Room escribe.

`BarraSuperior` lee además `estado.medios.size` (`:172`), así que cada inserción en Room
recompone la pantalla de cámara completa.

### R8 · Modelos de dominio inestables: la rejilla recompone entera en cada emisión — MEDIO · VERIFICADO

Los `UiState` son `data class` con `List<T>` cruda, no hay `kotlinx.collections.immutable` en
el proyecto, y los modelos de dominio **no están anotados** (`@Immutable` solo aparece en
`ui/theme/EstadoColores.kt`). `Medio` contiene `java.time.Instant` y `Gps?`, tipos que el
compilador no puede inferir como estables, así que **`Medio` es inestable** y con strong
skipping se compara por identidad — y Room materializa instancias nuevas en cada emisión
aunque los datos no cambien.

Resultado: cada cambio de estado de subida recompone las 9-12 celdas visibles completas
(Surface + AsyncImage + hasta 3 overlays + `stringResource` cada una), no solo la que cambió.
Igual en `PendientesScreen` y en `HistorialScreen` con `Reporte`.

Lo que **sí** está bien: `key = { it.id }` está presente en las tres listas. Falta
`contentType` en `ArchivosScreen`, que mezcla celdas de FOTO (con `AsyncImage`) y de AUDIO
(con `Icon`) en la misma rejilla.

**Dirección:** anotar `Medio`, `Reporte`, `Gps` y `Etiqueta` con `@Immutable` (convierte la
comparación a `equals`) y añadir `contentType`. Es la diferencia entre ~10 celdas
recompuestas y ~1 por evento.

### R9 · Room sin índices, sin `distinctUntilChanged` y sin transacciones — MEDIO · VERIFICADO

Verificado por búsqueda exhaustiva: en `app/src/main/java` **no existe ni un solo**
`@Transaction`, `@Index`, `@ForeignKey`, `addMigrations`, `fallbackToDestructiveMigration`,
`exportSchema` explícito ni `setQueryCoroutineContext`.

Quedan en **full table scan**:

- `medios.reporteId`: `obtenerDelReporte`/`observarDelReporte` y el `JOIN` de
  `contarPendientes`/`observarPendientesDe`. La rejilla de 50 medios y el badge se recalculan
  escaneando toda la tabla `medios` del dispositivo (equipo compartido = acumulado de varios
  custodios y varios viajes).
- `medios.estadoSubida`: `obtenerPendientes` y los filtros `!= 'COMPLETA'`.
- `reportes.custodioUid + ciclo + fechaLocal` para `observarHistorial`, más un sort en memoria.
- Excepción afortunada: `obtenerActivo`/`observarActivo` sí pueden usar el índice único
  parcial `(ciclo, custodioUid)`, porque la query incluye literalmente `ciclo = 'ACTIVO'`.

**Ningún Flow de Room pasa por `distinctUntilChanged`.** El `InvalidationTracker` de Room
invalida **por tabla, no por fila**: cada `actualizarEstadoSubida`, `actualizarGps` y
`marcarSubidaCompleta` re-dispara `observarDelReporte`, `contarPendientes` y
`observarPendientesDe`, que reconsultan y remapean las 50 entidades a dominio. Peor: las dos
últimas hacen JOIN con `reportes`, así que también se invalidan con cada `actualizarSubida`
del reporte. Durante una subida de 50 archivos con reintentos es una ráfaga de
recomposiciones justo cuando la cámara y el worker compiten por CPU.

No hay `BLOB` en las entidades (los binarios viven en `filesDir`, correcto), pero los
`SELECT *` de `medios` traen ~20 columnas cuando la rejilla solo necesita 5.

**Dirección:** `@Index("reporteId")` y `@Index("estadoSubida")` en `medios`;
`@Index(value = ["custodioUid","ciclo","fechaLocal"])` en `reportes`; `.distinctUntilChanged()`
en los Flows del repositorio y `conflate()` donde solo interesa el último valor. Son cambios
aditivos y entran en la primera migración.

### R10 · Arranque en frío: trabajo síncrono antes del primer frame útil — MEDIO · VERIFICADO

1. `AppCustodioApplication.onCreate`: componente de Hilt + `FirebaseCrashlytics.getInstance()`,
   más la auto-inicialización de `FirebaseApp`/Auth/Firestore por los `ContentProvider`.
2. `MainActivity.kt:25-27`: `getSharedPreferences(...).getBoolean(...)` **dentro de
   `setContent`** — lectura de disco síncrona en la primera composición, por un booleano de
   tema.
3. `AppNavHost` instancia dos ViewModels en la raíz; el arranque es en `LOGIN`, así que hay
   una composición de login desechada más una navegación antes de ver nada útil.
4. `WorkManagerSubidaScheduler` llama `WorkManager.getInstance(context)` **en su constructor**,
   que se ejecuta al inyectar el ViewModel, es decir en el hilo principal durante la primera
   composición: abre su propia base de datos y arranca `ForceStopRunnable`. (`reconciliar()`
   sí hace `withContext(Dispatchers.IO)`, correcto.)
5. **No hay Baseline Profile propio** y R8 está desactivado: 45 MB de dex en 5 archivos que
   ART tiene que verificar y cargar, con todo el código de Compose interpretado/JITeado en el
   primer arranque. Ver T5.

### R11 · Coil sin configuración y con dos formas de pasar el modelo — MEDIO · VERIFICADO

Lo bueno, y conviene saberlo: **se cargan las miniaturas de 320 px, no los originales**
(`medio.thumbnailPath`), así que el escenario "50 bitmaps de 1920×1080 = OOM" **no ocurre**.
Tampoco cada `AsyncImage` crea su propio loader (Coil 3 usa el singleton).

Lo que falta:

- No hay `SingletonImageLoader.Factory` ni ninguna configuración (grep: cero `ImageLoader`/
  `ImageRequest`). Se acepta el default: caché de memoria del **25 % del heap** sin techo
  explícito. 50 miniaturas de 320×320 ARGB ≈ 20 MB; en un equipo de 2 GB con heap de ~96 MB
  la caché tope ronda 24 MB, así que la rejilla completa **llena la caché de imágenes de la
  app entera** y empieza a desalojar durante el scroll.
- Sin `placeholder`/`error`, así que cada celda es un cuadro vacío hasta que decodifica:
  pop-in visible al abrir Archivos con 50 medios.
- Inconsistencia: `ArchivosScreen.kt:141` pasa `File(it)` y `DetalleReporteScreen.kt:195` pasa
  el `String` crudo del path. Coil 3 mapea `String → Uri`; un path absoluto sin esquema
  depende del fetcher. No verificado en ejecución, pero si el detalle no pinta miniaturas es
  por aquí.

### R12 · Tipografía: cuatro `Typeface` resueltos de forma bloqueante — BAJO · VERIFICADO

`ui/theme/Type.kt:30-52`. Los 13 `TextStyle` **no** multiplican la carga (comparten una sola
`FontFamily`), pero esa familia declara **cuatro `Font` apuntando al mismo
`R.font.inter_variable`** con `FontVariation.Settings` distintos (400/500/600/700), así que
Android construye cuatro `Typeface` desde el mismo recurso, de forma **bloqueante** en el hilo
que compone/mide. Está en el camino crítico de los 5 s y se elimina precargando con
`FontFamily.Resolver.preload` fuera del hilo principal.

`TabularNumberStyle` se define bien como `val` de nivel superior, pero se consume siempre con
`.copy(...)` dentro del composable (`Renglones.kt:161`, `Botones.kt:193`, `AudioScreen.kt:75`):
un `TextStyle` nuevo por recomposición, en el cronómetro y el renglón de pendientes, o sea en
lo que más cambia. El `.copy(fontSize = …)` además **pierde el `lineHeight`** del estilo.

### R13 · GPS: sin respaldo, sin caché compartida y con 5 peticiones concurrentes — MEDIO · VERIFICADO

`data/ubicacion/ProveedorUbicacion.kt:17-34` + `ui/captura/CapturaViewModel.kt:378-396`

- Solo `getCurrentLocation(PRIORITY_HIGH_ACCURACY)`. **No hay fallback a `lastLocation`**, que
  en un vehículo en movimiento daría un fix de hace segundos, perfectamente válido con su
  `ageMs`. Bajo techo o en patio cubierto, la petición se vence en 8 s y el medio queda **sin
  GPS**, forzando al custodio a teclear la ubicación (más toques, y texto libre que el
  analista no puede explotar).
- 5 fotos seguidas lanzan **5 `getCurrentLocation` concurrentes**, cada una a máxima
  precisión, sin compartir ni cachear el fix. Desgaste de batería puro en un turno de 12 h.
- Doble `withTimeoutOrNull` de 8 s redundante.
- **Bien resuelto:** el GPS no bloquea la UI ni el guardado (la hoja abre al instante). El
  costo real es que la *subida* se retrasa hasta 8 s por medio (ver E10).

**Dirección:** un único fix compartido con TTL (válido 15-30 s para todos los medios de esa
ventana), `lastLocation` como respaldo inmediato registrando `ageMs`, y `PRIORITY_BALANCED`
como segundo intento.

### R14 · Sin control de red medida, batería ni Doze — MEDIO · VERIFICADO

`WorkManagerSubidaScheduler.kt:48-50` usa solo `NetworkType.CONNECTED`. Implica: sube por
datos celulares sin distinción, sin `setRequiresBatteryNotLow`, sin opción "solo Wi-Fi" en
Ajustes, y sin trabajo expedited para el viaje activo. `NetworkType.CONNECTED` tampoco conoce
el Ahorro de datos ni las redes medidas: con Data Saver activo el worker corre, la red se
bloquea y **quema los 4 intentos** (C5). Con 26-50 MB por viaje × 37 custodios es ~1-2 GB/día
de plan celular sin ninguna palanca de control.

`ConectividadAndroidMonitor.kt:41-50` tampoco valida `NET_CAPABILITY_VALIDATED`: un wifi de
caseta con portal cautivo **reporta conectado**, oculta la franja de sin conexión y deja que
WorkManager dispare subidas que fallan. (El registro/desregistro del `NetworkCallback` sí es
correcto, con `awaitClose`; no hay fuga. Falta `distinctUntilChanged`.)

### R15 · El límite de 50 medios se cuenta de forma costosa — BAJO · VERIFICADO

`domain/usecase/CapturarMedio.kt:55-57` materializa las 50 filas completas para contarlas
(`obtenerDelReporte(reporteId).size`) en cada captura, en vez de un `COUNT(*)`.

### R16 · La ventana de 30 días del historial se congela — BAJO · VERIFICADO

`fechaDesdeUltimos30Dias(ahora())` se evalúa **una sola vez**, al construir el Flow en
`ObtenerHistorial.kt:13-14`, y el `stateIn` del ViewModel lo mantiene vivo. En un equipo
compartido que no se cierra en días, la ventana se congela en la fecha de arranque.

Lo que **sí** está bien en historial: el filtro temporal está en SQL
(`ReporteDao.kt:26-33`, comparación lexicográfica de `yyyy-MM-dd`, válida por el formato ISO),
y el buscador por placa **no** reconsulta la base — filtra en Kotlin sobre la lista ya
cargada. Cero I/O por tecla.


---

## 6. Fricción de uso en campo

El competidor es WhatsApp y tiene cero ceremonia (`docs/PLAN.md` §1). Estos son los puntos
donde la app pierde contra ese estándar.

### U1 · La hoja de etiquetado bloquea la ráfaga y no tiene salida — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-B)

`ui/captura/HojaEtiqueta.kt:60-83`. La hoja es una trampa deliberada: `confirmValueChange`
rechaza `Hidden` sin chip, `onDismissRequest` ignora el back y el toque exterior, y la X se
deshabilita. Mientras esté abierta **no hay visor**: no se puede disparar en ráfaga y
etiquetar después. Escoltando un tráiler que pasa, el custodio pierde la segunda y tercera
foto.

**Trampa sin salida:** si el catálogo llegara vacío no hay chips → la hoja no se puede cerrar
de ninguna forma (ni back, ni X, ni gesto) y la única salida es **matar la app**. Y la salida
que sí existe (la X, `descartarEtiquetado`) deja el medio huérfano y bloquea la sesión (ver
E3).

**Dirección:** disparo libre con etiqueta diferida (cola de "sin etiquetar" visible en
Archivos + bloqueo del cierre de viaje hasta etiquetar), o etiqueta heredada automática con
opción de corregir.

### U2 · No hay etiqueta recordada, y "Guardar" es un toque redundante — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-B)

`HojaEtiqueta.kt:55` usa `remember(medio.id) { mutableStateOf<String?>(null) }`: no hay
etiqueta recordada del último uso. En un viaje de 50 evidencias, casi siempre de la misma
categoría, son **~100 toques evitables**. Debería preseleccionarse la última usada del viaje,
y tocar el chip podría confirmar directamente en lugar de exigir un "Guardar" aparte.

**Catálogo provisional:** 5 chips (`CatalogoEtiquetasRepository.kt:9-15`): Daño, Devolución,
Novedad, Contraentrega, **Otro**. "Otro" es un cajón de sastre que el analista no puede
explotar, y faltan las etiquetas obvias del ciclo (inicio de viaje, llegada, sello/candado,
unidad, documentos). Esto es consistente con `docs/PLAN.md` §3.1: el catálogo real sigue
pendiente y **bloquea la salida a producción**, no la construcción.

### U3 · No hay ningún canal de confirmación no visual — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-C)

Verificado por búsqueda en todo `app/src/main`: **cero `performHapticFeedback`, cero
`Vibrator`, cero `MediaActionSound`, cero `Snackbar`**. En cabina, a pleno sol, una píldora
verde con relleno al 12 % de alfa es prácticamente invisible y obliga a mirar la pantalla en
movimiento. El disparo pide vibración corta + sonido de obturador; el encolado, un tic.

Agravante: el velo de obturador se dispara **dentro del callback de éxito**, es decir después
de rotar el bitmap y comprimir el JPEG. Entre el toque y cualquier confirmación pasan cientos
de ms sin nada en pantalla, con el disparador ya deshabilitado. Es exactamente "la acción
parece no haber ocurrido".

### U4 · `PendientesScreen` oculta el estado de subida en el caso normal — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-D)

`ui/pendientes/PendientesScreen.kt:112-118`: `detalleDe` devuelve `medio.etiqueta ?: <estado>`.
Es decir, **en el caso normal (medio etiquetado) el renglón muestra el nombre de la etiqueta y
oculta el estado**. La pantalla que existe para resolver la ansiedad "¿sí se envió?" muestra
"Daño · 14:32" y nada más; "En cola / Enviando… / Subida fallida" solo aparecen en medios sin
etiqueta. El botón de reintento tampoco se pinta salvo `FALLIDA`, así que **un renglón
etiquetado y fallido se ve idéntico a uno etiquetado y en cola**.

### U5 · "Ya llegó al servidor" se comunica por ausencia — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-D)

Cuando un medio pasa a `COMPLETA` desaparece de `PendientesScreen` (el DAO filtra
`estadoSubida != 'COMPLETA'`) y en la rejilla de `ArchivosScreen` **no se dibuja nada**:
`CeldaArchivo` solo pinta icono de borrar si `PENDIENTE`, badge rojo si `FALLIDA`, y la
etiqueta. **Una foto enviada y una foto sin etiquetar se ven exactamente iguales.** La
biblioteca ya tiene `ChipEstadoSubida` con icono + color y la rejilla no lo usa.

Tampoco hay progreso real en ninguna parte: solo estados discretos, ni bytes, ni porcentaje,
ni "3 de 7 enviados en este viaje". Con red intermitente, "En cola" puede durar horas y se ve
igual que "va a salir ya". `docs/PLAN.md` §11.7 ya identificaba la falta de progreso; sigue
pendiente.

### U6 · El badge mezcla tres cosas distintas — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-D)

`MedioDao.kt:22-30` define "pendiente" como todo lo que no es `COMPLETA`, lo que junta **sin
etiquetar** (nunca encolado), **en cola** y **fallido**. Son tres situaciones con acciones
distintas y el custodio no puede distinguirlas. Combinado con E3, un medio huérfano mantiene
el badge en ≥1 para siempre y bloquea el cierre de sesión.

### U7 · Los errores son callejones sin salida — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-F)

`ui/components/Dialogos.kt:158-172`: `DialogoError` tiene un solo botón "Entendido". Todos los
errores del flujo lo usan, incluido `error_sin_conexion` y los fallos de captura y etiquetado:
**cero acciones de recuperación**, el custodio tiene que reconstruir mentalmente qué
reintentar.

El copy en sí está bien: sin jerga técnica, sin stack traces ni códigos filtrados. La
excepción de vocabulario es `reporte_limite_alcanzado` ("límite de 50 **medios**"), que
convive con `error_limite_medios` ("50 **archivos**") para el mismo límite.

### U8 · Carga, error y vacío son indistinguibles en Historial — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-E)

`ui/historial/HistorialScreen.kt:88`. El orden del `when` está invertido:
`estado.reportesTotales == 0` se evalúa **antes** de `estado.cargando` y de `estado.error`, y
en ambos casos `reportesTotales` es 0 → el custodio ve siempre **"Sin viajes en los últimos 30
días."** `CajaCargaReportes` y `CajaErrorHistorial` son **código muerto**, y un fallo de
lectura se presenta como "no tienes viajes".

Además `HistorialScreen.kt:68` declara `onReintentar: () -> Unit = {}` y `HistorialRoute` **no
lo cablea** al ViewModel: el botón de reintento no haría nada aunque fuese alcanzable.

Esto vuelve inefectivo el trabajo de la fase C5 del plan, que existió precisamente para
añadir esos estados.

### U9 · La franja de sin conexión solo cubre una pantalla — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-F)

`ui/viaje/ReporteScreen.kt:171-175`: `FranjaAviso(reporte_sin_conexion)` solo se pinta en
`ReporteAbierto`. **No aparece en Captura, Audio, Archivos, Pendientes ni Cierre**, o sea
durante todo el trabajo real. Y `RootViewModel.kt:29-35` inicializa `conectado = true`, así
que al abrir la app sin red hay una ventana en la que se afirma que hay conexión — justo lo
contrario de lo que documenta el comentario de `ConectividadAndroidMonitor`. Ver también R14
(portal cautivo).

### U10 · Doble toque duplica pantallas — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-E)

Ninguna navegación usa `launchSingleTop` (`AppNavHost.kt:160-164, 265`). Doble toque en
"Capturar evidencia" **apila dos veces la ruta CAPTURA** → dos `CapturaEvidenciaScreen`, dos
binds de CameraX peleando por el dispositivo, y un back que devuelve a otra cámara idéntica
(el custodio cree que el back no funciona). Igual para Audio, Archivos, Cierre, Pendientes y
Detalle.

Solo REPORTE lleva `popUpTo` (`:334-348`), así que alternar Historial↔Ajustes apila entradas
indefinidamente: el back físico recorre el historial de pestañas en lugar de salir.

El ViewModel, en cambio, **está bien blindado** contra dobles envíos de dominio:
`iniciarViaje` (`creandoViaje`), `cerrarViaje`, `descartarViaje`, `capturarFoto`,
`borrarPendiente` y `reintentar` todos validan estado. El hueco es de navegación y de
encolado. Detalle asociado: el botón de reintento no cambia de aspecto tras el toque, así que
un segundo toque produce un diálogo de error ("ya está en subida") por no haber dado señal de
respuesta.

### U11 · El estado del formulario de cierre se pierde con un back o una rotación — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-E; `verticalScroll` del resto de destinos queda en Grupo 5/A3.9)

`rememberSaveable` se usa **exactamente una vez** en toda la app (`Avisos.kt:140`). Con
`remember` —que se pierde en cualquier recreación de Activity, no solo en muerte de proceso—:

- `CierreScreen.kt:58-62`: `novedad`, `comentario`, `motivoDescarte`, y la visibilidad de los
  dos diálogos.
- `ReporteScreen.kt:96`: `mostrarHoja` (la hoja de placa se cierra en medio del tecleo).
- `ArchivosScreen.kt:68`: `medioABorrar` (el diálogo de confirmación desaparece).
- `HojaEtiqueta.kt:60,72`: `seleccionada`, `avisoVisible` — el chip elegido se resetea y la
  hoja vuelve a quedar "no descartable" desde cero.

Salir del cierre a medias es gratis y silencioso: no hay confirmación de descarte de cambios.
El **motivo de descarte** tecleado es el más sensible, porque es obligatorio.

### U12 · El motivo de descarte es texto libre en un diálogo — ALTO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-F)

`ui/components/Dialogos.kt:66-110`: `OutlinedTextField` libre de `minLines = 2` **dentro de un
`AlertDialog`**, con placeholder "Obligatorio" y **sin un solo motivo predefinido**. Es el peor
caso del plan (§7: "Escribir es el punto débil"): teclado sobre un diálogo, escritura libre,
en movimiento, y sin ella no se puede descartar. Tampoco recibe foco al abrir, así que hay un
toque extra.

**Dirección:** 4-5 motivos en chips (placa equivocada, viaje cancelado por el cliente,
duplicado, prueba/error, otro) y el campo de texto solo para "otro".

El copy del descarte **sí** está bien: `descarte_texto` explica que lo ya subido no se borra.

### U13 · `FranjaAviso` se usa para texto informativo permanente — MEDIO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-F)

`ui/viaje/AudioScreen.kt:71` usa `FranjaAviso` (superficie ámbar de advertencia, con icono de
alerta) para un texto puramente informativo y permanente: "Máximo 5:00 por nota. Habla
claro…". Devalúa el único affordance de advertencia del sistema; cuando aparezca un aviso real
(sin conexión), el custodio ya lo habrá aprendido a ignorar.

### U14 · Vocabulario inconsistente en tres ejes — MEDIO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-F)

- **reporte / viaje** para lo mismo: pestaña "Reporte", vacío "Sin **reporte** activo / Iniciar
  nuevo **reporte**", hoja "Nuevo **viaje** / Comenzar **viaje**", "Cerrar **viaje**",
  historial "**Viaje** del…".
- **evidencias / archivos / medios** para lo capturado. "Medios" es jerga interna filtrada al
  usuario.
- Género y registro: badge "FALLIDA" vs chip de biblioteca "Fallido" vs lista "Subida fallida".

El tono general del español es bueno (tuteo, frases cortas, sin anglicismos). El problema no
es el tono, es la falta de un glosario único.

### U15 · Repetición y jerarquía en `ReporteScreen` — MEDIO · VERIFICADO · ✅ ATENDIDO 2026-09-24 (G3-F)

`ui/viaje/ReporteScreen.kt:190-212`: el mismo número aparece **tres veces** en una pantalla
(contador "x de 50" en el encabezado, celda "EVIDENCIAS", fila "N evidencias capturadas"), y
ninguna de las tres es la información que el custodio necesita de un vistazo: **cuántas faltan
por subir**.

`ArchivosScreen` describe su vacío como "Captura fotos o notas de voz **desde la pestaña
Reporte**", pero se llega ahí desde Reporte: la instrucción devuelve al usuario al lugar de
donde vino en vez de ofrecer el atajo a la cámara.

### U16 · Dos sistemas de chips y un componente de biblioteca muerto — BAJO · VERIFICADO

- `ChipEstadoSubida` vive en la biblioteca (`Chips.kt:88`) y `ChipEstado`/`ChipCiclo` están
  reimplementados dentro de `HistorialScreen.kt:141-196`. Misma receta, dos fuentes de verdad.
- `ui/components/MiniaturaMedio.kt` (~180 líneas) **solo se invoca desde sus propios
  `@Preview`**: la rejilla real usa `CeldaArchivo` en `ArchivosScreen`. Son dos verdades sobre
  cómo se ve una miniatura. (De paso, su `Brush.verticalGradient` se crea en cada
  recomposición de cada celda.)
- `Botones.kt:264-289`: los previews del botón primario dicen "BAJAR BÁGARO", texto sin
  sentido. Solo afecta a la documentación viva, pero es lo primero que un revisor ve de la
  biblioteca.
- `CajasEstado.kt:75`: `private val CajasEstadoOscura: Boolean = true` no se usa.
- `AudioScreen.kt:59-60`: `modifier` se aplica dos veces (al `Box` y al `Column` interno), así
  que el `statusBarsPadding` que pasa el host se aplica doble.

### U17 · La hoja de etiquetado se vuelve a mostrar al elegir la primera Situación — ALTO · SOLUCIONADO 2026-09-22

- Reportado en campo: tras capturar una foto o nota de voz, la hoja de etiquetado "aparecía dos
  veces" — se volvía a animar desde abajo en cuanto se elegía la primera etiqueta de Situación.
  Afectaba a cualquier recomposición mientras la hoja estaba abierta (chip, GPS que llega, tecla
  del campo de ubicación), no solo a esa elección.
- Causa raíz (reproducida leyendo las fuentes de Material3 1.4.0): `rememberModalBottomSheetState`
  enruta `confirmValueChange` por `rememberSaveable` (`SheetDefaults.rememberSheetState`); al
  construirse una lambda nueva en cada recomposición, su referencia cambia y el `SheetState` se
  regenera con valor inicial `Hidden`. El `LaunchedEffect(sheetState) { sheetState.show() }` de
  `ModalBottomSheet.kt:213` se relanza y la hoja vuelve a entrar. `HojaInferior` no se veía
  afectada porque su `confirmValueChange` por defecto (`{ true }`) es un singleton estable.
- Fix en `HojaEtiqueta.kt`: se recuerda una lambda estable (`remember`) que lee el flag vivo con
  `rememberUpdatedState(tieneSituacion)`; el `SheetState` ya no se regenera por recomposición.
- Regresión: `HojaEtiquetaTest` (androidTest) congela el reloj de Compose, elige la primera
  Situación y mide el borde de la hoja durante los 8 fotogramas siguientes: sin el fix se
  desplazaba **706 px** (RED); con él, 0 px (GREEN) en dispositivo real CPH2769. Verificación
  completa: `testDebugUnitTest` 174/0, `lintDebug` y `assembleDebug` en 0 errores.

---

## 7. Accesibilidad, contraste y adaptación

Dato transversal: en todo `ui/` **no existe ni una sola llamada** a `liveRegion`,
`isTraversalGroup`, `traversalIndex`, `stateDescription`, `Role.*`, `toggleable`, `selectable`,
`autofill`, `imePadding`, `KeyboardCapitalization`, `FocusRequester` ni
`minimumInteractiveComponentSize` (verificado por grep). La única `semantics { }` del proyecto
está en `Botones.kt:238`. La fase C6 del plan migró los strings y los `contentDescription`,
pero la capa semántica de Compose sigue prácticamente sin usar.

### A1 · Áreas táctiles por debajo del mínimo — ALTO · VERIFICADO

El plan (§7) exige **56 dp**; Material exige 48 dp. Incumplimientos concretos:

| Severidad | Ubicación | Tamaño real |
|---|---|---|
| **Crítica** | `ui/viaje/ReporteScreen.kt:273-289` `BotonCerrarReporte` | `Surface(onClick)` con solo `padding(h=10, v=6)` + `labelMedium` ≈ **28 dp**. Acción cuasi-destructiva, pegada al contador, a una mano en movimiento |
| **Crítica** | `ui/captura/HojaEtiqueta.kt:137-141` | `FilterChip` de M3 = **32 dp**. Es el control **más repetido** de la app y está en la ruta crítica de los 5 s. Y no usa el `ChipCategoria` de la biblioteca |
| Alta | `ui/components/Chips.kt:64` `ChipCategoria` | `height(40.dp)` forzado |
| Alta | `ui/components/Renglones.kt:268-290` `Interruptor` | 56×**32 dp**; además el renglón entero también es clickeable → doble blanco solapado |
| Alta | `ui/components/CampoPlaca.kt:155-175` `SugerenciaPlaca` | ≈ **42 dp**, y es el camino rápido para abrir viaje |
| Media | `ui/components/Dialogos.kt:46, 88, 141, 168` | `TextButton` de M3 = **40 dp**, en todas las confirmaciones destructivas |
| Media | `ArchivosScreen.kt:159, 186`, `Renglones.kt:166` | `IconButton` = 48 dp (cumple M3, no los 56 del plan), con icono de 18 dp |
| Baja | `Botones.kt:68, 110`, `LoginScreen.kt:114` | `height(48.dp)`: cumplen Android, incumplen el estándar propio |
| **OK** | `BarraNavegacion.kt:100` (56), `Botones.kt:236` (96), obturador (72) | Los tres únicos que cumplen 56+ |

### A2 · Fallos de contraste WCAG AA — ALTO · VERIFICADO

Ratios calculados sobre los hex reales de `ui/theme/Color.kt`, resolviendo la composición alfa
sobre el fondo real de cada componente:

| Par | Ratio | Requisito | Dónde |
|---|---|---|---|
| `outline` (`#374151`) sobre `background` (`#0E1117`) | **1.83:1** | 4.5:1 | `ReporteScreen.kt:235`, `HistorialScreen.kt:97`, `:108` |
| `outline` sobre `surfaceVariant` (`#242B3A`) | **1.37:1** | 4.5:1 | `DetalleReporteScreen.kt:94, 116, 148, 226` — prácticamente invisible |
| `outline` claro (`#B3BCCF`) sobre canvas claro (`#EEF1F7`) | **1.69:1** | 4.5:1 | Los mismos sitios en tema claro |
| `outline` como tint de icono | 1.37–1.83:1 | 3:1 | `ArchivosScreen.kt:152`, `DetalleReporteScreen.kt:209` |
| Placeholder `onSurfaceVariant@60%` (`#626D7D`) sobre `surfaceContainerLow` | **3.29:1** | 4.5:1 | `CampoPlaca.kt:87` ("INGRESE PLACA") |
| **Claro:** `onTertiaryContainer` (`#B7791F`) sobre `tertiaryContainer` | **3.19:1** | 4.5:1 | `Avisos.kt:73-82` `FranjaAviso` — la franja más importante, ilegible a pleno sol |
| **Claro:** chip SUBIENDO, `Warning` (`#B7791F`) sobre relleno al 12 % | **3.19:1** | 4.5:1 | `Chips.kt:103` + `EstadoColores.kt:53` |
| **Claro:** chip ENVIADO, `Success` (`#0F9D63`) sobre relleno al 12 % | **3.03:1** | 4.5:1 | `Chips.kt:97` + `EstadoColores.kt:52` |

Dos patrones de fondo, y son los que hay que corregir, no los ocho sitios uno por uno:

1. **`colorScheme.outline` se está usando como color de texto en 8 lugares, y no es un color
   de texto** — es un trazo de borde. Es el fallo más repetido y el más grave. `onSurfaceVariant`
   ya pasa AA en todas las superficies (7.37:1 sobre canvas, 4.66:1 sobre `surfaceHighest`).
2. **El tema claro —el modo de uso a pleno sol— falla donde el oscuro pasa.** La receta de
   Nocturne "color al 12 % de relleno + color pleno de contenido" funciona sobre lienzo oscuro
   y se derrumba sobre blanco. En oscuro estos mismos chips dan 6.94:1 y 7.35:1.

### A3 · Escalado de texto: qué se rompe primero a 130 % y 200 % — ALTO · VERIFICADO

No hay ningún caso de `dp` usado para tamaño de texto (`Type.kt` está todo en `sp`, correcto).
El problema es el inverso: **contenedores en dp fijos con texto dentro**. En orden de rotura:

1. `Chips.kt:64` `ChipCategoria`: `height(40.dp)` con `labelMedium` 13 sp + padding 8+8. A 200 %
   (26 sp) el glifo se recorta. Es el primero en romperse.
2. `CampoPlaca.kt:76`: `height(52.dp)` con `bodyLarge` 17 sp. A 200 % la placa se corta
   verticalmente — y es el campo que abre el viaje.
3. `ReporteScreen.kt:226-251` `BloqueUnidad`: `Row` con placas `titleLarge` 20 sp `maxLines=1`
   **sin `overflow`** (se corta a hachazo), más el contador, más el botón de cierre, ninguno con
   `weight` salvo la columna de placas. A 130 % el contador ya se come las placas.
4. `Botones.kt:68/110`, `LoginScreen.kt:114`: `height(48.dp)` con `labelLarge` 15 sp. A 200 %
   el texto no cabe en **todos** los botones primarios y secundarios, incluido "Entrar".
5. `BarraNavegacion.kt:100-104`: `height(56.dp)` con `Box` de 32 dp + etiqueta `maxLines=1`. A
   130 % la etiqueta ya no cabe; a 200 % desaparece. No hay alternativa de solo-icono.
6. `Botones.kt:205-210` `BadgePendientes` en `size(18.dp)`: con dos cifras (≥10 pendientes es
   normal con 50 medios) ya va justo a 100 %.
7. `Renglones.kt:189-217` `RenglonResumen`: `etiqueta` con `maxLines=1` **sin `overflow`** y
   `valor` sin `weight`. Se usa en `AjustesScreen.kt:93` para el **correo del custodio**: un
   componente diseñado para cifras tabulares, sin wrap.
8. `Renglones.kt:63-79` `RenglonReporte`: `detalle` con `maxLines=1 + Ellipsis`, y el detalle es
   "Viaje del %s · %d de %d enviados · «motivo»". **El motivo de descarte queda truncado
   siempre, incluso a 100 %.** Información esencial perdida por elipsis.
9. **Cuatro pantallas sin `verticalScroll`**: `LoginScreen.kt:53`, `ReporteScreen.kt:151`
   (`ReporteAbierto`), `AudioScreen.kt:61`, `AjustesScreen.kt:80`. A 200 % el contenido no cabe
   y **no hay forma de alcanzarlo**: en Ajustes es el botón de cerrar sesión. Solo `CierreScreen`
   y `PendientesScreen` scrollean. **Con `screenOrientation="fullSensor"` esto ya no espera al
   200 %: se rompe en cuanto el custodio gira el teléfono.** Ver A9.
10. Saltos de línea a mano en `strings.xml:147` y `:203`: a 200 % el `\n` fuerza cortes en el
    lugar equivocado y descuadra el `EstadoVacio` centrado.

### A4 · Insets: Login se dibuja bajo las barras y el teclado tapa "Entrar" — ALTO · VERIFICADO

`enableEdgeToEdge()` sí se llama (`MainActivity.kt:24`) y el `Scaffold` declara
`contentWindowInsets = WindowInsets(0.dp)` (`AppNavHost.kt:139`), o sea que **cada destino es
responsable de sus propios insets**. El resultado es inconsistente:

| Ruta | Insets | Problema |
|---|---|---|
| `LOGIN` (`:148`) | **ninguno** | **Crítica:** contenido bajo la barra de estado y bajo la barra gestual; sin scroll y sin `imePadding`, **el botón "Entrar" queda tapado por el teclado**. Primera pantalla de la app |
| `CIERRE` (`:243`) | solo `statusBarsPadding` | "Descartar viaje" queda bajo la barra gestual (alcanzable con scroll, pero pisado) |
| `PENDIENTES` (`:259`), `DETALLE` (`:280`) | solo `statusBarsPadding` | El último renglón queda bajo la barra gestual, sin `contentPadding` compensatorio |
| `AUDIO` (`:210`) | solo `statusBarsPadding` | "Cancelar" puede caer en la zona gestual |
| `CAPTURA`, `ARCHIVOS`, `INICIO`, `HISTORIAL`, `AJUSTES` | ambos, o cubiertos por la `bottomBar` | OK |

**`imePadding` no existe en ninguna parte del proyecto.** `windowSoftInputMode="adjustResize"`
no basta en edge-to-edge con Compose. Afectados, todos con campo de texto y botón de acción
debajo: Login, la hoja de nueva placa, `HojaEtiqueta` (campo de ubicación sin GPS + "Guardar",
y la hoja **no se puede cerrar** sin etiquetar), `DialogoDescarte` (motivo obligatorio +
"Descartar") y el comentario de cierre.

### A5 · `contentDescription` inútil o ausente en lo que importa — ALTO · VERIFICADO

- **Crítica:** `Renglones.kt:166-173`, el `IconButton` de reintento tiene
  `contentDescription = null`. Es la única forma de reintentar una subida fallida desde
  `PendientesScreen`; con TalkBack se anuncia como "botón" sin nombre. El string
  `archivos_reintentar_subida` ya existe y no se usa ahí.
- `ArchivosScreen.kt:143` usa `R.string.comun_miniatura` = literalmente **"Miniatura"** para
  *todas* las fotos, y `:151` "Nota" para todos los audios (igual en
  `DetalleReporteScreen.kt:197`). **El lector de pantalla no puede distinguir una foto de
  otra.** La etiqueta, el badge "FALLIDA" y el estado son nodos hermanos sin merge ni orden:
  TalkBack recorre tres focos sueltos por celda, o **150 paradas sin relación** en 50 medios.
  Falta `mergeDescendants` con un `contentDescription` compuesto tipo "Foto ABEJO, 10:43,
  subida fallida".
- `Botones.kt:201-213` `BadgePendientes` es un `Text` con el número desnudo, dentro de un ítem
  cuyo icono es `null`: TalkBack lee "3 Reporte". El número no dice de qué es.
- `BarraNavegacion.kt:96-104`: `Column.clickable` sin `Role.Tab` ni `selected`, así que no se
  anuncia "pestaña 1 de 3" ni "seleccionado" — y el estado activo se transmite **solo** por
  color, tinte y una testiga de 4 dp.
- `Renglones.kt:274-292`: `Box.clickable` en lugar de `Modifier.toggleable(role = Role.Switch)`:
  no se anuncia "activado/desactivado" ni en el tema nocturno ni en la novedad del cierre.
- Superficies clickeables sin etiqueta compuesta ni `onClickLabel`: `MiniaturaMedio.kt:107`,
  `ReporteScreen.kt:295-340`, `Renglones.kt:47-92`, `CampoPlaca.kt:155`.
- **Correctos, para contraste:** `BarraSuperior.kt:54`, `CampoPlaca.kt:110`,
  `HojaEtiqueta.kt:122`, `CapturaEvidenciaScreen.kt:215`, `Botones.kt:238`. Y bien puestos en
  `null` los decorativos de `Chips.kt:145`, `Avisos.kt:75/115`, `HistorialScreen.kt:81`.

### A6 · Nada se anuncia a TalkBack cuando cambia — ALTO · VERIFICADO

- **El cronómetro de grabación es mudo** (`AudioScreen.kt:71-79`): se recompone cada segundo
  sin `liveRegion`. No hay forma de saber cuánto llevas grabando ni que el límite de 5:00 se
  acerca, ni se anuncia el corte automático.
- **El contador de pendientes no se anuncia nunca.** Es el indicador de salud del producto
  (evidencia sin subir) y es invisible para TalkBack.
- `AvisoSubidaFlotante` (`Avisos.kt:129-153`) aparece y desaparece en 3 s sin live region: "Tu
  archivo se subirá en breve" —la única confirmación de que la captura quedó encolada— no
  existe para TalkBack. Igual las dos `FranjaAviso`.
- Las hojas inferiores no usan `isTraversalGroup` ni `traversalIndex`. En la de etiquetado, el
  orden de composición pone primero el aviso verde transitorio, y **cuando ese aviso desaparece
  a los 3 segundos el árbol semántico cambia bajo el foco del usuario**. Con el cierre bloqueado
  hasta elegir chip y controles de 32 dp sin etiqueta de estado, un usuario de TalkBack queda
  atrapado en la hoja.
- El estado "procesando" (`capturando`, `creandoViaje`, `cerrandoViaje`) solo cambia el texto
  del botón, sin `stateDescription` ni live region.
- Los `AlertDialog` de M3 **sí** aportan rol y foco correctos; ahí no hay hallazgo estructural,
  pero ninguno pone foco inicial en su campo obligatorio.

### A7 · Información solo por color — MEDIO · VERIFICADO

Esto está **mejor de lo esperado** y conviene decirlo: la fase C6 funcionó. Los chips de
estado llevan icono + texto (`Chips.kt:93-122` mapea `ic_cheque`/`ic_reintento`/`ic_reloj`/
`ic_alerta`), los chips de ciclo también, la franja de sin conexión lleva `ic_alerta` + texto,
y "FALLIDA" en la rejilla es texto. Quedan tres fugas:

- `BarraNavegacion.kt:106-153`: el destino activo se marca solo con color, tinte y barra de
  4 dp, sin `selected` semántico.
- `CampoPlaca.kt:69-75`: el estado "con valor" se expresa solo cambiando el borde. El campo
  **nunca marca error** —ni color, ni texto, ni `semantics { error() }`— porque el error de
  placa inválida sale como `DialogoError` aparte.
- `LoginScreen.kt:95-103`: el error de login es un `Text` en `colorScheme.error` sin icono, sin
  `semantics { error() }` en los campos, no asociado al campo que lo causó y sin anunciarse.

### A8 · Teclado, capitalización y foco — MEDIO · VERIFICADO

- `CampoPlaca.kt:99`: `KeyboardOptions(imeAction = ImeAction.Next)` **sin `keyboardType`, sin
  `capitalization = KeyboardCapitalization.Characters` y sin `autoCorrect = false`**. El valor
  se pasa a mayúsculas por código, pero el teclado muestra minúsculas y sugiere autocorrección:
  el custodio teclea "abc123" y ve "ABC123", con el predictivo peleando. Y peor: `siguiente`
  llega **null** desde `ReporteScreen.kt:127-130`, así que `ImeAction.Next` **no lleva a ningún
  foco** — el usuario pulsa "siguiente", no pasa nada, y el teclado tapa "Comenzar viaje".
  Debería ser `Done`/`Go` disparando `iniciarViaje`.
- `Dialogos.kt:76-86` (motivo de descarte) y `CierreScreen.kt:118-123` (comentario): sin
  `keyboardOptions`, sin capitalización de frases, sin `imeAction`, sin `FocusRequester`.
- `HojaEtiqueta.kt:146-152` (ubicación sin GPS): `singleLine` sin `imeAction = Done` ni foco
  automático. Es el fallback cuando el GPS falla, o sea un caso frecuente.
- **Autofill en login:** no hay `semantics { contentType = ContentType.Password }`. En un equipo
  compartido por 37 personas **esto es defensa, no debilidad** — no se quiere que el llavero del
  equipo memorice credenciales de un custodio. **No cambiar hacia autofill.** El hallazgo real
  es el inverso: no hay toggle de "mostrar contraseña", lo que con guantes provoca reintentos, y
  `ERROR_TOO_MANY_REQUESTS` se mapea a `Inesperado`, así que el custodio bloqueado ve un error
  genérico y no entiende que debe esperar.

### A9 · Orientación libre (`fullSensor`) con cuatro pantallas que no scrollean — ALTO · VERIFICADO

`AndroidManifest.xml:44` declara `android:screenOrientation="fullSensor"` (cambiado desde
`portrait` después del levantamiento; ver el delta del manifest arriba) y `MainActivity` **no
declara `configChanges`**.

Con `portrait` este hallazgo era un riesgo latente, porque desde Android 16 —y confirmado en la
documentación de Android 17— las restricciones de orientación, redimensionado y aspect ratio
**se ignoran en pantallas con sw ≥ 600 dp** para apps que apuntan a API 37, y **el opt-out ya no
está disponible**. Con `fullSensor` el riesgo **ya no es latente: es el comportamiento en todos
los equipos, incluidos los 37 teléfonos**. Lo que hay que resolver, en consecuencia:

1. **Las cuatro pantallas sin `verticalScroll`** de A3.9 (`LoginScreen.kt:53`,
   `ReporteScreen.kt:151`, `AudioScreen.kt:61`, `AjustesScreen.kt:80`) quedan con contenido
   **inalcanzable** en horizontal, no solo a 200 % de escala de texto. En Ajustes eso es el botón
   de cerrar sesión; en Login, el botón "Entrar". Esto pasa de "se rompe a 200 %" a "se rompe al
   girar el teléfono", y es la consecuencia más inmediata del cambio.
2. **Sin `configChanges`, cada rotación recrea la Activity.** El `CapturaViewModel` sobrevive
   (cuelga de la entrada del grafo `viaje`) y el cronómetro de audio también (se calcula con
   `elapsedRealtime`), pero **la sesión de CameraX se re-vincula** —visible como un
   parpadeo del visor, justo en la pantalla del estándar de 5 s— y se pierde todo el estado
   `remember` de U11 (comentario de cierre, motivo de descarte, chip seleccionado, hoja de placa
   abierta). Girar el teléfono con el formulario de cierre a medias lo borra sin confirmación.
3. La rejilla sí sobrevive (`GridCells.Fixed(3)` queda con celdas anchísimas y desaprovechadas;
   no se rompe). No hay `res/layout-land/`, ni variantes por `sw`, ni `WindowSizeClass`.
4. La cámara está a salvo en cuanto a la evidencia: `VistaCamara.kt` no fija `targetRotation`,
   pero `tomarFoto` rota el bitmap según `imageInfo.rotationDegrees`, así que la foto queda
   derecha aunque el teléfono no lo esté. Nota: con `portrait` fijo ese cálculo producía fotos
   acostadas al fotografiar en horizontal; **con `fullSensor` ese defecto desaparece**, porque la
   rotación de destino sigue al dispositivo. Es la ganancia del cambio.

**Dirección:** añadir `verticalScroll` a las cuatro pantallas es ahora obligatorio, no opcional,
y conviene pasar el estado de U11 a `rememberSaveable` en la misma tanda, porque ambos defectos
se disparan con el mismo gesto.

### A10 · Strings: `values-es-rMX/` desincronizado — ALTO · VERIFICADO

Ver B3 para el impacto en lint. El impacto de producto es distinto y vale aparte: las 17 claves
de `values-es-rMX` se resuelven contra esa carpeta en **todos** los teléfonos de campo, y las
~134 restantes caen a `values/`. Funciona hoy porque son idénticas, pero **cualquier corrección
de copy en `values/` sobre una de esas 17 claves no se verá en producción**.

Lo demás está bien: `values/` está en español (un dispositivo en inglés verá español, que es lo
correcto), no hay `values-en/`, los cuatro `<plurals>` están bien formados para español y se
consumen con `pluralStringResource`, `supportsRtl="true"` con `start`/`end` consistentes, y
`uppercase()` sin locale es correcto (Kotlin usa la regla invariante y evita el problema turco
de la `i`).

Detalles menores: `DetalleReporteScreen.kt:159` concatena `"${x} de ${y}"` con la preposición
**codificada en Kotlin** (es el único literal de UI fuera de `strings.xml` en toda la app);
`HistorialScreen.kt:135-138` compone strings por suma con un recurso cuyo contenido es un
separador (`" · %1$s"`); y `AudioScreen.kt:143`/`CierreScreen.kt:205` usan `"%d:%02d".format()`
sin `Locale`.


---

## 8. Build, release y proceso

Todo lo de esta sección se **verificó ejecutando** los comandos, salvo donde se indique.

| Comando | Resultado |
|---|---|
| `./gradlew lintDebug` | **FALLA** — 141 errores, 34 warnings, 1 hint |
| `./gradlew lintVitalRelease` | BUILD SUCCESSFUL in 19s |
| `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL — 71 pruebas, 0 fallos, 17 clases, 0.34 s |
| `./gradlew assembleRelease` | BUILD SUCCESSFUL in 46s — 23 226 370 B = **22.15 MiB**, firmado |
| 2ª corrida de `assembleRelease` | `Reusing configuration cache` / 1 s — la configuration cache **sí** funciona |
| `apksigner verify` | v1 `false`, **v2 `true`**, v3 `false`; CN=App Custodio, RSA 2048 |
| `unzip -l` del APK | 5 dex = **45.2 MB sin comprimir**; `lib/` 4.86 MB (4 ABIs); `baseline.prof` 14 138 B |
| `:app:dependencies` (releaseRuntimeClasspath) | 287 módulos únicos |

### T1 · No hay CI — ALTO · VERIFICADO

`.github/` contiene únicamente `pull_request_template.md`; **no existe `.github/workflows/`**.
Nada ejecuta automáticamente `assembleDebug`, `testDebugUnitTest`, `lintDebug` ni las reglas de
Firebase (`cd firebase && npm test`). Es exactamente la razón por la que B2 (un crash
garantizado en Android 8-11) lleva commits sin detectarse, y por la que B1 pudo entrar sin que
nada lo notara.

**Dirección:** workflow mínimo en `ubuntu-latest` con `gradle/actions/setup-gradle` corriendo
`./gradlew assembleDebug testDebugUnitTest lintDebug` y subiendo
`app/build/reports/lint-results-debug.html` como artifact. Nota de portabilidad: la suite de
Room depende de `app/src/test/jniLibs/libsqliteJni.dylib` (macOS arm64); en un runner Linux
hará falta la variante `.so`.

### T2 · `versionCode = 1` fijo: no hay estrategia de versionado para sideload — ALTO · VERIFICADO

`app/build.gradle.kts:43-44` → `versionCode = 1`, `versionName = "1.0"`. Confirmado en el APK
con `aapt2 dump badging`.

Con distribución por App Distribution y sideload a 37 equipos, esto duele en tres puntos: el
custodio no puede decir qué build trae; **Crashlytics agrupa todos los crashes bajo `1.0 (1)`**,
así que un reporte de campo no se puede atribuir a un build; y reinstalar encima funciona sin
ninguna señal de que la actualización entró.

**Dirección:** `versionCode` derivado (contador en `gradle.properties`,
`git rev-list --count HEAD`, o `yyMMddHH`) y `versionName` con el hash corto.

### T3 · El esquema de Room no está exportado ni versionado — ALTO · VERIFICADO

`AppCustodioDatabase.kt:11-14` declara `version = 1` sin `exportSchema` explícito. El default es
`true`, pero **no hay dónde exportar**: no se aplica el plugin Gradle `androidx.room` ni se pasa
`room.schemaLocation` a KSP, y `app/schemas/` no existe. El esquema **no está bajo control de
código**, así que no se puede escribir una migración validada ni usar `MigrationTestHelper`, y
nadie detecta en CI que un cambio de entidad rompió la compatibilidad.

**Lo bueno:** `crearBaseDatos` **no** usa `fallbackToDestructiveMigration`, así que hoy no hay
borrado silencioso de evidencia. Los dos caminos de crash con sideload:

- **Upgrade sin migración:** subir a `version = 2` sin `Migration` lanza `IllegalStateException`
  al abrir. La app no arranca y la evidencia sin subir queda inaccesible. El riesgo real no es
  el crash: es la presión de campo que empuja a "arreglarlo" agregando
  `fallbackToDestructiveMigration` — ese parche **sí** borra evidencia de 37 custodios.
- **Downgrade, que con sideload es el caso más probable:** instalar un APK viejo sobre una base
  migrada lanza `IllegalStateException` por downgrade, porque no hay
  `fallbackToDestructiveMigrationOnDowngrade`. Bucle de crash al abrir.

**Dirección:** aplicar el plugin de Room con `schemaDirectory`, commitear `app/schemas/1.json`
**antes** de entregar (es la línea base; sin ella la v1 en campo ya no es reconstruible), fijar
la política de solo migraciones aditivas, y añadir una prueba de migración por cada bump.

### T4 · El índice único parcial se recrea en cada `onOpen` — ALTO · VERIFICADO

`AppCustodioDatabase.kt:25-37` crea `ux_reportes_activo_por_custodio` desde el callback, en
`onCreate` **y** en `onOpen`.

La carrera **sí** está bien resuelta y no hay que tocarla: `ReporteDao.insertar` es
`@Insert(onConflict = IGNORE)` → `INSERT OR IGNORE`, que absorbe la violación del índice único
devolviendo rowId `-1`, y `RoomReporteRepository.kt:38-51` lo traduce a
`DominioError.YaExisteReporteActivo(existente.id)`. No sale `SQLiteConstraintException` y no
revienta la app. El chequeo previo de `CrearReporte` es solo el camino rápido; el índice es la
red real.

Los problemas son de mantenimiento:

- El índice **no forma parte del esquema que Room valida** (no está en ninguna `@Index`), así que
  el `identity_hash` no lo cubre. Si una migración futura recrea `reportes` (patrón estándar
  create-copy-drop-rename), el índice se va sin que nada falle y **el invariante desaparece**
  hasta el siguiente `onOpen`.
- Ejecutar el `CREATE UNIQUE INDEX` en **cada apertura** significa que si por cualquier ruta
  llegan a existir dos reportes `ACTIVO` del mismo custodio (base restaurada, migración a
  medias), el `CREATE` falla y **la app queda en crash permanente al abrir**, sin forma de
  recuperar la evidencia.

**Dirección:** crear el índice solo en `onCreate`/migración, o envolver el `onOpen` para
detectar la violación, repararla (dejar el más reciente como ACTIVO) y reintentar.

### T5 · R8 desactivado: 22.15 MiB y 45 MB de dex — ALTO · VERIFICADO

`app/build.gradle.kts:57-62` → `optimization { enable = false }`. Medido en el APK construido:
dex 42.61 MB en 5 archivos, `lib/` 4.86 MB, `res`+`arsc` 1.11 MB.

- **Tamaño:** 287 módulos empaquetados enteros. Para un parque con datos móviles limitados, cada
  actualización son 22 MiB. En apps Compose+Firebase la reducción típica con R8 va del 50 al
  70 %; no se midió (implicaba editar el build script) y conviene medirlo antes de decidir.
- **Arranque en frío:** sin eliminación de código muerto, inlining ni merge de clases, ART debe
  verificar y cargar 45 MB de dex en 5 contenedores. `minSdk 26` da multidex nativo, así que no
  hay penalización legacy, pero el volumen pesa en cada arranque frío y en el `dexopt` de
  instalación.
- **Mapping de Crashlytics: hoy es un no-problema**, porque sin ofuscación las trazas llegan
  legibles. Es una trampa diferida: el día que se active R8 hará falta subir mapping, y
  `AGENTS.md` ya documenta que el bloque `firebaseCrashlytics { mappingFileUploadEnabled }` **no
  genera accessor con AGP 9**. Conviene resolver eso *antes* de activar R8, no después.
- `app/src/main/keepRules/rules.keep` son 418 bytes de comentarios. Al activar R8 harán falta
  reglas para lo que se resuelve por reflexión: POJOs serializados a Firestore, `Worker`s por
  nombre de clase (aunque `CustodioWorkerFactory` mitiga) y entidades/DAO de Room. Los POJOs de
  Firestore son el punto clásico de ruptura silenciosa: no crashea, simplemente sube documentos
  con campos vacíos.

### T6 · Solo hay baseline profile de las librerías, no de la app — MEDIO · VERIFICADO

El APK trae `assets/dexopt/baseline.prof` de 14 138 B y
`META-INF/androidx.profileinstaller_profileinstaller.version`, pero ese perfil sale del merge de
los perfiles que traen las dependencias, no de recorridos reales: no existe módulo
`:baselineprofile` ni el plugin `androidx.baselineprofile` en el catálogo.

Para 45 MB de dex sin R8 en equipos de gama baja, el arranque en frío y el primer scroll de la
rejilla de 50 medios se interpretan (JIT) en vez de ejecutarse AOT. Un baseline profile propio
de los flujos login → reporte → captura → rejilla es **la mejora de arranque más barata
disponible sin tocar R8**, y `profileinstaller` ya está en el APK.

### T7 · Crashlytics sin contexto útil — MEDIO · VERIFICADO

El plugin está aplicado, el SDK es `firebase-crashlytics:20.1.0`, el manifest declara
`firebase_crashlytics_collection_enabled = true` (confirmado en el manifest fusionado de
release), y los crashes tempranos **sí** se capturan: Crashlytics se instala por
`FirebaseInitProvider` + `CrashlyticsRegistrar` antes de `onCreate`, así que la llamada de
`AppCustodioApplication.kt:23` es redundante pero inofensiva. **No hay hallazgo de
inicialización tardía.**

Lo que falta: `grep` de `setUserId|setCustomKey|recordException` sobre `app/src/main/java`
devuelve **cero** resultados. Con 37 custodios en equipos compartidos, un crash no dice quién,
ni en qué equipo, ni en qué reporte o placa. Y hay **6 `catch (_: Exception)` silenciosos** en
`GrabadoraMediaRecorder`, `CapturaViewModel` y `VistaCamara` — justo las rutas donde se pierde
evidencia. Solo hay 1 `Log.` en todo `main/`.

**Dirección:** `setUserId(uid)` al abrir sesión, custom keys con `placa`/`reporteId`/`deviceId`,
y `recordException` como no-fatal en los catch silenciosos de captura y grabación.

### T8 · `debug` y `release` comparten `applicationId` — MEDIO · VERIFICADO

`app/build.gradle.kts:41` y no hay `applicationIdSuffix` para debug. Debug se firma con la
keystore de depuración y release con `app/keystore/release.jks`, así que instalar el release
encima de un debug falla con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` y los dos no pueden coexistir
en el mismo equipo. **Dirección:** `debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }`.

### T9 · Firma solo v2: sin posibilidad de rotar la clave — MEDIO · VERIFICADO

`apksigner` confirma v1 `false`, v2 `true`, **v3 `false`**. Con `minSdk 26` no falta nada para
instalar, pero sin v3 **no hay rotación de clave posible**. Dado que `release.jks` es lo único
que permite actualizar los 37 equipos ya desplegados (advertencia que `AGENTS.md` ya registra),
vale habilitar `enableV3Signing = true` **antes** del despliegue, no después.

### T10 · Cuatro ABIs, dos de ellas inútiles — MEDIO · VERIFICADO

```
lib/arm64-v8a/libsqliteJni.so    1.22 MB
lib/armeabi-v7a/libsqliteJni.so  1.20 MB
lib/x86_64/libsqliteJni.so       1.13 MB   ← solo emuladores
lib/x86/libsqliteJni.so          1.09 MB   ← solo emuladores
```

`sqlite-bundled` (Room 3) aporta ~4.6 MB de los 4.86 MB de nativas. Sin AAB (la distribución es
sideload) no hay split automático. `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }`
recorta ~2.2 MB sin tocar nada más. `android:extractNativeLibs="false"` ya está correcto.

### T11 · Dos runtimes de Room completos en el APK — MEDIO · VERIFICADO

`androidx.room3:room3-runtime:3.0.2` (directo) y **`androidx.room:room-runtime:2.7.0`**
arrastrado por `androidx.work:work-runtime-ktx:2.11.0`. Se confirma en el manifest fusionado,
que declara **las dos** variantes del servicio (`androidx.room3.MultiInstanceInvalidationService`
y `androidx.room.MultiInstanceInvalidationService`).

No es corregible hoy (WorkManager 2.11 depende de Room 2.x), pero explica parte de los 45 MB de
dex y es otro argumento para R8, que eliminaría las clases no alcanzadas de la copia 2.7.0.

Relacionado: `firebase-auth` arrastra todo el stack de Sign-In que esta app no usa
(`GenericIdpActivity`, `RecaptchaActivity`, `SignInHubActivity`, `play-services-fido`,
`androidx.credentials`, `firebase-sessions`). No se puede podar con exclusiones sin romper Auth;
es R8 quien lo resuelve.

### T12 · `ANDROID_ID` como identidad de equipo — MEDIO · VERIFICADO

`di/IdentidadModule.kt:25` usa `Settings.Secure.ANDROID_ID` (lint `HardwareIds`). Funciona, pero
se reinicia en factory reset y desde Android 8 es por app+usuario+**firma**: si se reinstala con
otra firma (p. ej. el debug de T8), el `deviceId` cambia y **se rompe la trazabilidad de qué
equipo subió qué**. **Dirección:** UUID propio persistido en las prefs ya existentes, con
`ANDROID_ID` como semilla inicial.

### T13 · Cambios de plataforma de `targetSdk 37` que sí aplican — MEDIO · VERIFICADO

Consultada la documentación oficial de Android 17. Contenido reformulado por cumplimiento de
licencias.

- **Edge-to-edge (forzado desde targetSdk 35): manejado.** `MainActivity.kt:25` llama
  `enableEdgeToEdge()` y el `Scaffold` único vive en `AppNavHost`. Vale una pasada visual en la
  pantalla de cámara a pantalla completa, donde el HUD puede quedar bajo la barra de gestos. Ver
  A4 para lo que no está resuelto.
- **Orientación ignorada en sw ≥ 600 dp: no manejado.** Ver A9.
- **Límites de memoria por app según RAM, aplicables a TODAS las apps sin importar targetSdk.**
  Ver R5.
- **Endurecimiento de audio en segundo plano, más estricto en targetSdk 37.** Refuerza E5: sin
  FGS, cualquier interacción de audio fuera del primer plano queda restringida.
- **Restricciones de FGS tipado (targetSdk 34) y `POST_NOTIFICATIONS` (33):** no aplican hoy
  porque la app no usa FGS ni notificaciones. Es la trampa de E5: el día que alguien añada
  `setForeground()` a un worker en Android 14+, será `MissingForegroundServiceTypeException` por
  falta de `FOREGROUND_SERVICE_DATA_SYNC` — y `lintVitalRelease` no lo va a atrapar (B3).
- **Permisos de media: bien resuelto.** `WRITE_EXTERNAL_STORAGE` está acotado con
  `maxSdkVersion="28"` y `FotosPublicasMediaStore.kt:24` bifurca a MediaStore en API ≥29. No se
  declaran `READ_MEDIA_*` y no hacen falta.
- **CT por defecto y ECH en TLS, `static final` inmutables, permiso de red local, CP2, RFCOMM,
  widget de memoria:** no aplican.

### T14 · El custodio no se entera de nada con la app cerrada — MEDIO · VERIFICADO

No hay `POST_NOTIFICATIONS` ni FGS. Hoy es consistente (los workers son de fondo sin
notificación), pero significa que **el custodio no recibe ninguna señal del sistema** de que
quedan archivos sin subir cuando la app está cerrada; el badge solo se ve dentro de la app. Con
operación offline prolongada y equipos que rotan, es evidencia que se queda sin subir sin que
nadie lo sepa. Se agrava con E6.

### T15 · Ruido menor de lint y compilación — BAJO · VERIFICADO

- **12 avisos `GradleDependency`**: catálogo desalineado con lo que realmente se resuelve (p. ej.
  `coreKtx = "1.10.1"` declarado, 1.16.0 resuelto por conflicto transitivo). **Todas las
  versiones están fijadas, sin rangos dinámicos** — eso está bien; lo que hay es un catálogo que
  no dice la verdad.
- `org.gradle.jvmargs=-Xmx2048m` es ajustado para AGP 9 + KSP + Hilt + Room; 4096m reduce el
  riesgo de GC thrash. `org.gradle.caching` no está activado (la configuration cache sí, y
  funciona).
- `android:label` redundante en `MainActivity` (`RedundantLabel`): **ya retirado** en el delta del
  manifest; punto cerrado.
- 3 recursos sin usar (`UnusedResources`): `R.color.nocturne_canvas_claro`,
  `drawable/ic_camara.xml`, y un string.
- 7 × `ModifierParameter` (`modifier` no es el primer parámetro opcional), 2 × `UseKtx`,
  `UseOfNonLambdaOffsetOverload` (`Renglones.kt:294`), `AutoboxingStateCreation`
  (`Avisos.kt:140`), `PluralsCandidate` (`strings.xml:185`).
- Warnings de Kotlin: `hiltViewModel` deprecado en 8 sitios (movido a
  `androidx.hilt.lifecycle.viewmodel.compose`), `ExperimentalCoroutinesApi` sin opt-in en
  `HistorialViewModel.kt:43` y `PendientesViewModel.kt:35`, aviso KT-73255 en `LoginUiState.kt:9`.

---

## 9. Cobertura de pruebas

26 archivos, ~2.800 líneas, **71 pruebas en verde**. El dominio y la lógica del pipeline están
bien cubiertos; hay tres agujeros que tocan directamente "no perder evidencia".

### Tabla de cobertura

| Área | Clases | Con prueba | Cobertura |
|---|---|---|---|
| `domain/usecase` | 8 | 8 | **Alta.** Feliz + errores de dominio |
| `domain/model` | 3 | indirecta | Media-baja. `SubidaReporte.computarEstado/completo` sin prueba directa; `fechaDesdeUltimos30Dias` sin frontera de medianoche |
| `data/repository` | 3 | 2 | Media |
| `data/local/db` (DAOs) | 2 | parcial | Media-baja. Sin cubrir: `contarPendientes`, `observarPendientesDe`, `obtenerPendientes`, `marcarSubidaCompleta`, `ReporteDao.cerrar`, `actualizarSubida`, `obtenerPendientesSubida` |
| `data/pipeline` | 2 | 2 | Alta en lógica, **nula en integración** (todo contra fakes) |
| `data/remote` | 6 | 1 (`codigoSesionAError`) | **Casi nula** — de ahí B1 |
| `data/worker` | 4 | **0** | **Nula** |
| `data/local/media` | 4 | 0 | **Nula** — de ahí B2 |
| `data/conectividad` | 2 | 0 (solo el fake) | Nula |
| `ui/*ViewModel` | 7 | 1 parcial (`CapturaViewModel`, 3 pruebas) | **Muy baja** |
| `ui/` Compose (~30 composables) | — | 0 | **Nula** |
| Reglas Firebase | 2 archivos | 48 casos | Media-alta en autorización, **nula en validación de payload** (ver S14) |

Dependencias que faltan para cerrar los huecos: **no hay `work-testing`, `room-testing`,
`robolectric`, `turbine` ni `mockk`** — solo `junit` + `kotlinx-coroutines-test`. Los workers no
son testeables hoy ni con esfuerzo cero.

### P1 · Las pruebas de Room sí corren contra SQLite real — VERIFICADO (nota positiva)

Confirmado por ejecución: `AppCustodioDatabase.crearBaseDatos` usa
`Room.databaseBuilder(...).setDriver(BundledSQLiteDriver())`, los tests crean un
`File.createTempFile(".db")` real, y el log muestra la carga del motor nativo
(`NativeLibraryLoader`). **El índice único parcial está probado de verdad**:
`InvarianteUnSoloActivoTest` verifica el `insertar` ignorado (`-1L`) y una carrera de dos
`CrearReporte` concurrentes que deja exactamente uno. Es la prueba más valiosa de la suite.

Lo que **no** está probado, pese al nombre: **las migraciones** (ver T3). No son testeables hoy.

### P2 · Cuatro fakes divergen de producción, y dos divergencias están afirmadas en verde — ALTO · VERIFICADO

Este es el hallazgo más peligroso de la suite, porque produce **confianza falsa**:

1. `FakeReporteRepository.cerrar()` fija `mediosSubidos = mediosEsperados` y `completo = true`;
   `RoomReporteRepository.cerrar()` **no toca ninguno de los dos**. `CerrarReporteTest` afirma
   `assertEquals(3, cerrado.mediosSubidos)` con 3 medios `PENDIENTE`: **una prueba verde que
   documenta un comportamiento inexistente**.
2. `FakeMedioRepository.marcarSubidaCompleta` pone `localPath = null`; `MedioDao` **no lo anula**.
   `SubidaMedioProcesoTest` afirma `assertNull(subido.localPath)`. En producción la fila conserva
   una ruta a un archivo ya borrado, lo que impacta la rejilla y Coil.
3. `FakeMedioRepository.obtenerPendientes()` **excluye `FALLIDA`**; el DAO (`!= 'COMPLETA'`) la
   **incluye**. Cualquier prueba futura de `reconciliar()` sobre el fake afirmaría lo contrario
   de producción.
4. `FakeReporteRepository.obtenerPendientesSubida()` filtra `ciclo != DESCARTADO`; el real filtra
   `subida != 'COMPLETA'`, o sea incluye descartados y reincluye para siempre los reportes sin
   medios (ver E6).

Además: **los fakes no emiten.** `FakeMedioRepository.observarDelReporte` y
`FakeReporteRepository.observarActivo/observarPorId/observarHistorial` construyen un
`MutableStateFlow` nuevo con un snapshot en cada llamada, así que **nunca emiten un segundo
valor**. Por eso `CapturaViewModelTest` afirma sobre `repoMedios.medios` y no sobre
`uiState.medios`: el camino reactivo real (rejilla, badge, aparición del viaje recién creado) no
está probado y **no puede probarse con estos fakes**. Y `FakeEtiquetaRepository` no es un fake:
delega en el `CatalogoEtiquetasRepository` de producción, así que no se puede simular catálogo
vacío (el escenario de U1).

### P3 · Aserciones débiles — MEDIO · VERIFICADO

`ObtenerEtiquetasTest` solo afirma `isNotEmpty`/`activo`/`orden`, no los ids que los chips
consumen. `AuthErrorMappingTest` cierra con `assertTrue(resultado is Inesperado)`.
`InvarianteUnSoloActivoTest` no verifica el `id` dentro de `YaExisteReporteActivo`.
`ReporteSyncProcesoTest.propaga fallo de escritura al cerrar` **no mira el estado local**, así
que la inconsistencia de C9 queda oculta por una aserción débil.

### P4 · Escenarios críticos sin prueba — ALTO · VERIFICADO

| Escenario | Estado |
|---|---|
| Doble incremento de `mediosSubidos` | No probado, y **por diseño no puede ocurrir** (valor absoluto). Lo que falta es la carrera de C3: **dos workers concurrentes** sobre el mismo reporte |
| Borrado local antes de confirmar | Parcial: se cubre "archivo ausente". Falta el borrado entre `subirArchivo` y `marcarSubidaCompleta`, y el TOCTOU de `borrarPendiente` compitiendo con un worker en vuelo |
| Worker interrumpido y reintentado | **No.** Sin `work-testing`, `runAttemptCount`, backoff y `REPLACE` no se ejercitan nunca |
| Token expirado / sesión revocada | **No.** Cero pruebas de `ErroresRemoto` |
| Disco lleno | **No.** Hoy crashea (E4) |
| Permisos denegados | **No.** El fake de ubicación nunca lanza `SecurityException` ni agota el timeout de 8 s, así que las dos ramas `catch` y el `withTimeoutOrNull` están sin cubrir |
| Muerte de proceso | **No.** Ninguna prueba arranca un segundo `CapturaViewModel` sobre la misma base |
| Cierre con captura concurrente | **No** (C6) |
| Descarte con medios ya subidos | **No.** Se cubre "no subir a un descartado", no el descarte que llega *después* de que 3 de 5 ya están en Storage |
| Autocorte de audio a 5 min | **No.** Requiere tiempo virtual; hoy no se prueba |

### P5 · Determinismo — MEDIO · VERIFICADO

- `CapturaViewModelTest` construye `CrearReporte`/`CapturarMedio` **sin** inyectar `ahora`, así
  que usan `Instant.now()` y `fechaLocal` real. Hoy no rompe nada, pero una prueba de historial
  en esa forma sería intermitente en la frontera de medianoche de `America/Mexico_City`.
- `MainDispatcherRule` existe y está bien usada, pero **solo** en `CapturaViewModelTest`.
  Cualquier prueba nueva de los otros 6 ViewModels fallará con "Module with the Main dispatcher
  had failed to initialize" si se olvida la regla.
- La mayoría de pruebas de dominio usan `runBlocking` en vez de `runTest`; funciona porque no hay
  `delay`, pero cierra la puerta a probar el temporizador de audio.
- `InvarianteUnSoloActivoTest` usa `runBlocking` + `async` sobre SQLite real: el ganador no es
  determinista, pero la aserción (`exitos == 1`) es robusta a eso. Bien planteada.

### P6 · Las cinco pruebas de mayor retorno

1. **Ciclo de vida del worker de subida** (`work-testing`): `TestListenableWorkerBuilder` con
   `runAttemptCount` 1..4 ⇒ `retry, retry, retry, failure` y medio en `FALLIDA`; luego
   `WorkManagerTestInitHelper` para afirmar que `reconciliar()` reencola exactamente un trabajo
   por medio pendiente **incluyendo `FALLIDA` y `EN_PROGRESO`**, y que un `encolarSubidaMedio`
   con `REPLACE` no deja un medio `EN_PROGRESO` huérfano. Cubre el hueco de `data/worker` y C4.
2. **Suite de contrato compartida de `MedioRepository`**: un mismo cuerpo de `@Test`
   parametrizado corriendo contra `RoomMedioRepository` **y** contra `FakeMedioRepository`
   (`obtenerPendientes` con `FALLIDA`, `marcarSubidaCompleta` y `localPath`, `borrarPendiente`
   sobre `EN_PROGRESO`, `actualizarGps` tardío). Mata de un golpe las cuatro divergencias de P2 y
   evita que vuelvan.
3. **Capa remota contra el Emulator Suite:** una sola prueba que haga un `set()` real y verifique
   que resuelve. Es lo que habría atrapado B1, y hoy no existe nada que ejercite `data/remote`.
4. **Cierre + pipeline contra Room real** (no fakes): cerrar un viaje con 1 medio `PENDIENTE` y
   afirmar que `mediosSubidos`/`completo` **no** se inflan; ejecutar `SubidaMedioProceso` dos
   veces y afirmar convergencia exacta; ejecutar `ReporteSyncProceso` con el remoto fallando al
   cerrar y afirmar que Room **no** queda `COMPLETA`. Cubre P2.1, C1 y C9.
5. **Inyección de fallos en la captura:** un `AlmacenArchivos` que lanza `IOException` y un
   `ProveedorUbicacion` que lanza `SecurityException` o nunca responde. Afirmar que
   `capturarFoto` no propaga la excepción, que `uiState.error` se pone, y que `guardarEtiqueta`
   **sí** encola aunque el GPS no llegue. Hoy el primer caso crashea (E4).

Si hay que elegir 3-4 pruebas de Compose: (a) el flujo de los 5 s (capturar ⇒ la hoja aparece ⇒
no se puede confirmar sin etiqueta ⇒ la miniatura aparece en la rejilla ⇒ el badge sube a 1);
(b) `CierreScreen` con `hayNovedad`/comentario llegando al ViewModel; (c) `AjustesScreen` con el
cierre de sesión bloqueado; (d) una prueba transversal de accesibilidad que recorra los controles
primarios afirmando `assertHeightIsAtLeast(48.dp)` y presencia de `contentDescription` — barata y
ataja regresiones de A1 y A5.

### P7 · Ampliar `reglas.test.js`

Ver S14. Los tests de reglas son el único mecanismo que evita que las reglas se regresen, y hoy
no cubren ninguno de los vectores de S3–S7.


---

## 10. Lo que está bien y no conviene tocar

Vale registrarlo explícitamente, para que una ronda de arreglos no lo deshaga:

**Dominio y persistencia**

- La invariante de un solo reporte activo está **bien resuelta en la base**, y su traducción a
  `DominioError.YaExisteReporteActivo` es correcta. Es la mejor pieza del repositorio.
- El orden archivo → fila en `guardar` es el seguro (nunca hay fila sin archivo).
- La restricción `WHERE estadoSubida = 'PENDIENTE'` en `actualizarGps`/`borrarPendiente` evita
  mutar medios ya en vuelo.
- El filtro temporal del historial está en SQL y la búsqueda por placa en memoria: cero I/O por
  tecla. Correcto en ambos lados.
- No hay `BLOB` en las entidades: los binarios viven en `filesDir`, como debe ser.
- No se usa `fallbackToDestructiveMigration`. Mantener esa decisión.

**Pipeline**

- El diseño de idempotencia es sólido **en su forma**: rutas de Storage determinísticas por
  `medioId`, documento de medios create-only con guarda de existencia, y contadores recomputados
  como valor absoluto en vez de `increment`. Eso último descarta el doble conteo por reintento.
- `asegurarReporte` corre **antes** de tocar Storage, así que un medio no puede subir antes que su
  reporte y las reglas de Storage encuentran el documento que necesitan.
- El **orden del borrado local es correcto**: no se borra antes de que Storage y el documento
  reporten éxito.
- El guard de `SubidaMedioProceso` que no sube evidencia a un reporte descartado localmente.

**Seguridad**

- `allow delete: if false` en `reportes`, `medios` y Storage; `update, delete: if false` en
  `medios`. La inmutabilidad de Firestore está bien puesta.
- No hay ningún `allow read` abierto: todas las lecturas exigen autenticación, y las de
  `reportes`/`medios` además dueño o analista.
- `config/{documento}` con `read, write: if false` y el comentario que explica por qué el `get()`
  interno sigue funcionando: correcto y bien documentado.
- El orden del `||` para no pagar la lectura de `config/analistas` en el camino del custodio es una
  buena decisión de coste, con su prueba de regresión.
- Los mensajes de login **no permiten enumeración de usuarios**: `codigoSesionAError` colapsa
  `USER_NOT_FOUND`, `WRONG_PASSWORD`, `INVALID_CREDENTIAL`, `USER_DISABLED` e `INVALID_EMAIL` en un
  único `CredencialesInvalidas`. No hay credenciales en logs.
- `network_security_config.xml` confinado a `src/debug`, con `cleartextTrafficPermitted="false"` en
  el `base-config` y cleartext solo para localhost. Verificado: **no se mezcla al release**.
- El inventario de permisos es el justo: no se pide `ACCESS_BACKGROUND_LOCATION`, y
  `WRITE_EXTERNAL_STORAGE` está acotado con `maxSdkVersion="28"`.
- **La ausencia de autofill en login es correcta** para un equipo compartido. No cambiarla.

**Presentación**

- Los chips de estado y de ciclo llevan **icono + texto**, no solo color. La fase C6 funcionó en
  ese punto.
- `key = { it.id }` está presente en las tres listas: no hay reciclaje mal hecho ni pérdida de
  estado al reordenar.
- Coil carga las **miniaturas de 320 px**, no los originales. El escenario de OOM por 50 bitmaps
  grandes no ocurre.
- La decodificación de bitmaps está correctamente fuera del hilo principal
  (`Dispatchers.Default.asExecutor()`), y el comentario del código lo justifica.
- `Type.kt` usa `sp` en todo el texto, sin un solo `dp`. `TabularNumberStyle` está bien definido
  como `val` de nivel superior.
- El **cronómetro de audio sobrevive** a cambios de configuración, porque se calcula con
  `SystemClock.elapsedRealtime()` en lugar de contar ticks.
- `ConectividadAndroidMonitor` registra y **desregistra** correctamente el `NetworkCallback` con
  `awaitClose` + `WhileSubscribed`. No hay fuga.
- El gate de sesión no produce parpadeo de contenido sin sesión: el overlay tapa el `CARGANDO`.
- `entradaDelGrafoViaje` ya captura la `IllegalArgumentException` de `getBackStackEntry`, así que
  no hay crash por pila frágil — solo un frame en blanco.
- El `ViewModel` está **bien blindado contra dobles envíos de dominio** en `iniciarViaje`,
  `cerrarViaje`, `descartarViaje`, `borrarPendiente` y `reintentar`.
- El tono del español es bueno: tuteo, frases cortas, sin anglicismos, errores sin jerga técnica.
- Los cuatro `<plurals>` están bien formados y bien consumidos.
- El copy del descarte explica correctamente que lo ya subido no se borra.

**Build**

- La **configuration cache funciona** (verificado: `Reusing configuration cache`, 1 s), incluida
  la lectura de `keystore.properties` en fase de configuración.
- Todas las versiones del catálogo están **fijadas, sin rangos dinámicos**.
- `extractNativeLibs="false"` correcto.
- Crashlytics **sí** captura crashes tempranos (vía `FirebaseInitProvider`), no hay problema de
  inicialización tardía.

---

## 11. Orden de atención sugerido

Los grupos están ordenados por dependencia y por riesgo, no por esfuerzo. Dentro de cada grupo el
orden es indiferente.

### Grupo 0 — Sin esto no se puede entregar

| # | Hallazgo | Nota |
|---|---|---|
| B1 | `esperar()` con `Task<Void>` | Desbloquea el producto entero. Cerrar con una prueba de `data/remote` contra el Emulator Suite (P6.3), porque hoy nada ejercita ese camino |
| B2 | `MediaRecorder` sin gate de API | 3 líneas más cambiar el `catch` a `Throwable` |
| B3 | Borrar `values-es-rMX/` + bloque `lint { }` | Los 141 errores bajan a 3 y el gate de release deja de ser ciego. Hacerlo **antes** de los demás grupos, para que lint sirva de red |

### Grupo 1 — Integridad de la evidencia

E3 (medios sin etiquetar), C1 (resultado de contadores descartado), C2 (`sha256` null), C4
(`REPLACE` → `KEEP`), E6 (filtro por `custodioUid` y límite de fecha en la cola), C5 (clasificar
permanente vs transitorio y registrar la causa), C6 (cierre transaccional), E4 (`IOException` de
disco), E2 (dejar de tragar los errores de `tomarFoto`).

### Grupo 2 — Privacidad y reglas ✅ atendido (2026-09-23, tanda §16 de `docs/PLAN.md`)

S2 (`allowBackup="false"`, una línea), S1 (quitar o volver privada la copia a la galería), S3 y S4
(tope de tamaño/`contentType` y `update` acotado a `ACTIVO`), S6 y S7 (cotas de contadores y
`soloCambia` en cada transición), S8 (declarar y pedir `COARSE`), S11/E6 (limpieza al rotar de
custodio). Cada cambio de reglas entra **con su caso en `reglas.test.js`** (S14): los tests son lo
único que evita que las reglas se regresen.

Cerrado en la tanda de `docs/PLAN.md` §16 (2026-09-23): S3–S10 y S12–S14 quedaron atendidos y
cada uno lleva su marca arriba; **S11 pasó a Producto** (decisión de retención; el grueso lo
cumplió E6) y **S13 pasó al Grupo 6** (contexto de Crashlytics con T7). Arné con 89 casos y
suite de dispositivo verde (33). El despliegue de las reglas endurecidas a la consola real se hizo
el 2026-09-23 (Firestore + Storage, `firebase deploy --only firestore:rules,storage`).

### Grupo 3 — Que la app sea usable en campo ✅ atendido (2026-09-24, tanda §17.1 de `docs/PLAN.md`)

R1 y R2 (abrir en el visor y estado de carga en Reporte: elimina el falso "Sin reporte activo" y
acerca los 5 s), U1 y U2 (desbloquear la ráfaga, etiqueta heredada, chips a 48 dp, quitar el toque
"Guardar"), U3 (vibración y sonido en el disparo), U4/U5/U6 (mostrar estado **y** etiqueta en
Pendientes, estado en la rejilla, separar "sin etiquetar" de "en cola" en el badge), U8 (orden del
`when` de Historial y cablear `onReintentar`), U10 (`launchSingleTop`), U11 (`rememberSaveable`),
U12 (motivos de descarte predefinidos), U9 (franja de sin conexión en todas las pantallas del
flujo), E5 (ciclo de vida del `MediaRecorder`), S9/S10 (rationale y ruta a Ajustes del sistema).

Cerrado en seis clústeres G3-A…G3-F (2026-09-24): cada hallazgo lleva su marca arriba. R1, R2,
U1–U11 quedaron atendidos en G3-A…G3-E con señales no visuales (U3), seña de reintento y estado
persistente; U7/U9/U12–U15 en G3-F, con `testDebugUnitTest` verde (6 casos nuevos de
`DescarteMotivosTest`/`AvisoSinConexionTest`) y `assembleDebug` verde. Quedan fuera: U16 (chips y
`MiniaturaMedio`, Grupo 6) y el recorrido cronometrado en dispositivo real y las pruebas de Compose
del cierre de tanda (§17.1) — **PENDIENTE-HW**, sin ejecutables aquí.

### Grupo 4 — Rendimiento

R3 (el conteo de pendientes fuera del cuerpo de `AppNavHost` — es el cambio más rentable del
grupo), R6 (añadir `lifecycle-runtime-compose` y migrar a `collectAsStateWithLifecycle`), R9
(índices + `distinctUntilChanged`), R8 (`@Immutable` + `contentType`), R4 y R5 (no recomprimir,
conservar EXIF, subir por stream con `putFile` reanudable), R7 (flash en `graphicsLayer`), R13
(fix de GPS compartido con TTL + `lastLocation`), R14 (constraints de red y `NET_CAPABILITY_VALIDATED`),
T6 (baseline profile propio).

### Grupo 5 — Accesibilidad

A3.9 + U11 (`verticalScroll` en Login, ReporteAbierto, Audio y Ajustes, y `rememberSaveable` en
el formulario de cierre): **ascendido a lo primero del grupo** por el cambio a `fullSensor`, que
convierte ambos en fallos al girar el teléfono en cualquier equipo, no solo a 200 % de escala.
Después: A2 (dejar de usar `colorScheme.outline` como color de texto: es un `sed` y arregla el
peor contraste de la app; luego recalcular la receta de chips y `FranjaAviso` **para tema
claro**), A4 (`imePadding` + `navigationBarsPadding` en los cinco destinos que faltan, empezando
por Login), A1 (los cuatro blancos táctiles críticos), A3 resto (`overflow` donde hay
`maxLines=1`), A5 (`contentDescription` del reintento y celda de rejilla con `mergeDescendants`),
A6 (`liveRegion` en cronómetro, badge y aviso de encolado; `Role.Tab` y `toggleable`).

### Grupo 6 — Proceso y deuda

T1 (CI), T3/T4 (esquema de Room exportado, política de migración, índice solo en `onCreate`), T2
(versionado), T9 (firma v3), T8 (`applicationIdSuffix`), T7 (contexto de Crashlytics), E9 (barrido
de huérfanos y purga de miniaturas), E8 (rescate de `EN_PROGRESO`), E10 (sacar el trabajo crítico
de `viewModelScope`), E11 (sesión perdida en caliente), E7 (guard muerto de cierre de sesión), P2
(suite de contrato que alinee los fakes), T10/T5 (ABIs y R8, esta última como tarea propia y
midiendo).

### Decisiones de producto que este documento no toma

1. **Resolución de captura** — sigue abierta en `docs/PLAN.md` §3.4 y esta auditoría no la
   resuelve. Nota relevante: si se adopta 2560×1920, R4 y R5 **empeoran proporcionalmente** (el
   pico de bitmaps y las tres escrituras crecen con el área), así que conviene arreglar el pipeline
   de imagen **antes** de subir la resolución, no después.
2. **Catálogo de etiquetas** — `docs/PLAN.md` §3.1. Bloquea producción. U2 añade un argumento:
   con 5 chips genéricos y "Otro" como cajón de sastre, el dato que llega al analista no sirve para
   filtrar, que es el propósito declarado del producto.
3. **Copia a la galería pública (S1)** — hay que decidir si es un requisito de operación o un
   resto de desarrollo. Si es requisito, necesita un directorio propio y una política de borrado;
   si no, se elimina junto con `WRITE_EXTERNAL_STORAGE`.
4. **Parque de dispositivos (A9)** — resuelta por el delta del manifest: con
   `screenOrientation="fullSensor"` se **acepta** la orientación libre en todos los equipos. La
   consecuencia que deja abierta es de implementación, no de producto: `verticalScroll` en las
   cuatro pantallas de A3.9 y `rememberSaveable` en el estado de U11 pasan a ser obligatorios.
5. **Solo Wi-Fi para subir (R14)** — con ~1-2 GB/día de plan celular, decidir si el ajuste existe.

---

## Limitaciones de esta auditoría

- **No se ejecutó la app en ningún dispositivo ni emulador Android.** Todos los hallazgos de
  comportamiento en runtime son análisis estático. En particular: B1 está derivado del contrato de
  la API de `Task` (`getResult()` devuelve null para `Void`) y del código, no de una ejecución
  contra Firebase; conviene confirmarlo con la prueba de P6.3 antes de darlo por cerrado.
- **No se ejecutó `cd firebase && npm test`.** Los huecos de reglas se derivaron leyendo
  `firestore.rules`, `storage.rules` y `reglas.test.js`.
- **No se midió nada con instrumentación.** Las cifras de latencia y de picos de memoria son
  estimaciones razonadas sobre el código (tamaños de bitmap, número de escrituras, timeouts
  declarados), no mediciones. No hay Macrobenchmark en el repo.
- **No se verificó el escalado de texto a 130 %/200 %, ni TalkBack, ni el contraste a pleno sol en
  dispositivo.** Los puntos A3, A6 y A9 señalan dónde se va a romper primero; la comprobación
  manual sigue pendiente, como ya registra `docs/PLAN.md` §11.8 y §11.9.
- **No se revisó el panel del analista** (otro repositorio), así que el riesgo de que lea
  `usuarios/{uid}.rol` (S14) queda como condicional.
- **No se comparó ninguna pantalla contra los `screen.png`** de `mock-app-design-language/`. Esta
  auditoría evalúa comportamiento, accesibilidad y rendimiento, no fidelidad visual al mockup.
