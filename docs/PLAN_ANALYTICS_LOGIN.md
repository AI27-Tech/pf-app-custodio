# Plan — Analítica del login: evento `login` con método y desenlace

**Estado:** Plan escrito el 2026-09-25. **Implementado el 2026-09-28** (las seis tareas). Changelog y
desviaciones registradas en la entrada `2026:09:28 12:32` de `CHANGELOG.md`.
**Origen:** petición del humano: registrar el evento de inicio de sesión con el patrón documentado por Firebase
(`Bundle` + `FirebaseAnalytics.Param.METHOD` + `FirebaseAnalytics.Event.LOGIN`).
**Alcance:** una capa de datos (`data/diagnostico/`), su binding de Hilt, y seis puntos de `ui/login/LoginViewModel.kt`.
No cambia la UI, ni el dominio, ni las interfaces de repositorio, ni las reglas de Firestore/Storage.

---

## Por qué

Hoy la app **no reporta un solo evento propio**. Verificado: en todo `app/src/` hay cero referencias a
`FirebaseAnalytics`, `logEvent`, `setUserId` o `setUserProperty` (así estaba el 2026-09-25, la fecha en que se
escribió este plan; el evento `login` se instrumentó después — ver el estado de arriba). La dependencia sí está declarada
(`gradle/libs.versions.toml:71` y `app/build.gradle.kts:152`, resuelta a `firebase-analytics:23.2.0` por el BoM
34.18.0) y sus componentes ya entran al manifiesto fusionado, o sea que el SDK recolecta sesiones y
`first_open` automáticos — pero **nadie sabe si un custodio logra entrar**.

La pregunta de negocio que hoy no se puede contestar es: *de cada 100 custodios que abren la app, ¿cuántos
terminan adentro, y en qué paso se quedan?* El login por SMS tiene al menos seis desenlaces distinguibles
(OTP válido, teléfono no autorizado en la allowlist, código inválido, código expirado, demasiados intentos,
sin conexión, el broker de GMS mudo). Cada uno es una fuga distinta y cada una se arregla distinto. Sin
eventos, el paso donde se pierde el custodio es invisible.

El precedente de la casa para instrumentación ya existe: `RegistroDeFallos` (`data/diagnostico/`) se creó
para el hallazgo **E2** de `docs/AUDIT.md` con exactamente la misma razón —*los `catch` vacíos se comían los
fallos y no quedaba rastro*— y resolvió el problema con una interfaz en vez de una llamada directa a
Crashlytics. Este plan reutiliza esa forma.

---

## Decisiones ya tomadas (por el humano, 2026-09-25)

| Decisión | Elección | Razón |
|---|---|---|
| Contenido de `method` | **Método + desenlace**: `method` = `"PHONE"` fijo, desenlace en un parámetro aparte | `method` es el parámetro reservado que Google le da al método de autenticación del evento `login`; cargarle el desenlace lo vacía de sentido |
| Cobertura | **Éxito y todos los fallos** (los seis puntos) | El valor de medir el login está en la tasa de fallo por paso, no solo en el éxito |
| Arquitectura | **Interfaz + Hilt**, igual que `RegistroDeFallos` | `FirebaseAnalytics.getInstance()` no debe aparecer en un ViewModel, y sin interfaz el fake no cabe y los tests de JVM no pueden afirmar qué se registró |

---

## Estado actual (verificado en el código)

**La dependencia está, el cableado no.** Exacto, archivo por archivo:

- `gradle/libs.versions.toml:71` — `firebase-analytics` declarado (sin `version.ref`: lo versiona el BoM).
- `app/build.gradle.kts:152` — `implementation(libs.firebase.analytics)`.
- `app/src/**` — **cero** coincidencias de `Analytics`, `logEvent`, `setUserId`, `setUserProperty`.
- `app/src/main/AndroidManifest.xml` — sin `firebase_analytics_collection_enabled`; la recolección está en el
  valor por defecto del SDK (auto-init activado), que es lo que ya hace Crashlytics con su `true` explícito.
- **Sin bloqueo de arranque:** `app/google-services.json` no trae `google_app_id`, pero el plugin de
  `google-services` lo **sintetiza** desde `mobilesdk_app_id`. Verificado en el recurso empaquetado de
  release (`app/build/intermediates/packaged_res/release/packageReleaseResources/values/values.xml`):
  `google_app_id = 1:755381066416:android:ca071cb050565888fdb975`. Analytics inicializa sin tocar la consola.
  (El bloque `analytics_client_info` ausente es campo legado de GA4 para BigQuery; no afecta al SDK.)

**Dónde vive hoy cada desenlace del login.** En `ui/login/LoginViewModel.kt`, y —importante— **los dos caminos
de éxito confluyen en un solo punto**: la verificación tecleada (`verificarCodigo`, L168) y la auto-lectura del
SMS (`EventoVerificacion.VerificacionAutomatica`, L155) llaman las dos a `validarAllowlist(...)`. Por eso el
éxito se instrumenta en un sitio y no en dos.

| # | Ubicación | Rama | Desenlace |
|---|---|---|---|
| 1 | `enviarCodigo` L84-87 | el número no compone E.164 (≠ 10 dígitos) | número inválido |
| 2 | `manejarEventoVerificacion` L157-159 | `EventoVerificacion.Fallo` | fallo al pedir el SMS (incluye `SinRespuestaSms`, el watchdog de 30 s de `AuthDataSource.kt:143-147`) |
| 3 | `verificarCodigo` L169-171 | `Result.Failure` de `sesionRepositorio.verificarCodigo` | OTP inválido / expirado / demasiados intentos / sin conexión |
| 4 | `validarAllowlist` L185-187 | `Result.Success(true)` | **éxito** |
| 5 | `validarAllowlist` L189-196 | `Result.Success(false)` | teléfono verificado pero fuera de la allowlist → se cierra la sesión |
| 6 | `validarAllowlist` L199-201 | `Result.Failure` de la consulta a la allowlist | la consulta a Firestore falló (p. ej. sin red) |

**No hay doble conteo.** `AuthDataSource.flujoVerificacion` emite **un** evento terminal y cierra el flujo
(`AuthDataSource.kt:102-147`: `onVerificationCompleted` / `onVerificationFailed` / `onCodeSent` y el watchdog
del broker, los cuatro con `close()`). Así, una llamada a `enviarCodigo` produce a lo sumo un evento del punto 2,
y un intento de código, uno del punto 3. Cada reintento real del usuario genera su propio evento, que es lo
deseado.

**Radio de impacto del cambio de firma.** `LoginViewModel` solo se construye en dos lugares: por Hilt desde
`ui/login/LoginScreen.kt:48` (`hiltViewModel()`) y a mano en `app/src/test/.../ui/login/LoginViewModelTest.kt:43`.
Ningún test instrumentado lo instancia (verificado con `rg LoginViewModel app/src/androidTest`). Agregar un
parámetro de constructor no rompe `LoginScreen` y obliga a tocar **un** archivo de test.

---

## El cambio, en una frase

`ui/login/LoginViewModel` recibe un `RegistroDeEventos` y, en cada uno de los seis desenlaces de su flujo, registra
el evento `login` con `method` fijo y un parámetro de desenlace; el `logEvent` de Firebase Analytics queda
encapsulado en `data/diagnostico/`, igual que el `recordException` de Crashlytics lo está en su implementación.

---

## El contrato de eventos

Nombre **`RegistroDeEventos`**, en `data/diagnostico/` (el mismo bucket que `RegistroDeFallos`: los dos son
observabilidad). Interfaz con **un** método, porque el login es lo único que se instrumenta hoy:

```kotlin
interface RegistroDeEventos {
    fun registroLogin(metodo: MetodoAuth, resultado: ResultadoLogin)
}
```

**Por qué enums y no cadenas libres.** `RegistroDeFallos.registrar(contexto: String, ...)` acepta texto libre
a propósito: agrupan fallos y el valor no importa. El caso es al revés —el valor **es** la dimensión por la
que se va a filtrar en la consola—, así que un `String` libre permitiría `login_result="ok"` y
`login_result="OK"` como dos dimensiones distintas sin que nada avise. Los enums hacen que eso sea imposible
de escribir. Cada entrada lleva su `valor` explícito, que es lo que viaja al `Bundle`.

| `ResultadoLogin` | `valor` enviado | Origen en el flujo |
|---|---|---|
| `OK` | `ok` | punto 4 |
| `NUMERO_INVALIDO` | `numero_invalido` | punto 1 |
| `SIN_RESPUESTA_SMS` | `sin_respuesta_sms` | punto 2 (`DominioError.SinRespuestaSms`) |
| `CODIGO_INVALIDO` | `codigo_invalido` | punto 3 (`DominioError.CodigoInvalido`) |
| `CODIGO_EXPIRADO` | `codigo_expirado` | punto 3 (`DominioError.CodigoExpirado`) |
| `DEMASIADOS_INTENTOS` | `demasiados_intentos` | punto 3 (`DominioError.DemasiadosIntentos`) |
| `TELEFONO_NO_AUTORIZADO` | `telefono_no_autorizado` | punto 5 |
| `SIN_CONEXION` | `sin_conexion` | puntos 2/3/6 (`SinConexion`, `ServidorIndisponible`) |
| `INESPERADO` | `inesperado` | puntos 2/3/6 (`Inesperado`) |

`MetodoAuth` nace con **un solo caso**, `PHONE` → `"PHONE"`. No es especulación: hoy el único método es SMS
(`CODIGO_PAIS = "+52"`, `LoginViewModel.kt:28`) y el enum existe para que cuando llegue otro método
(no improbable: `docs/AUDIT.md` ya contempla correo para el panel de analistas) el cambio sea de un `when`,
no de una firma de interfaz más cada punto de llamada.

**Todos los valores son ASCII y de menos de 100 caracteres**, respetando el límite de longitud de valor de
parámetro de evento de GA4 (verificado: 100 caracteres para el valor, 40 para el nombre del parámetro y del
evento; 25 parámetros por evento; 500 nombres de evento distintos por usuario en streams de app —aquí van
**uno**).

**Mapeo de `DominioError`.** Los puntos 2, 3 y 6 ya reciben un `DominioError`, así que hace falta una
traducción, como la que ya existe hacia strings en `ui/error/DominioErrorRecurso.kt`. Va como extensión
`fun DominioError.aResultadoLogin(): ResultadoLogin` en el mismo archivo del contrato, por estas razones:

- `data` → `domain` es la dirección permitida; `ui` → `data.diagnostico` es un precedente ya establecido
  (`ui/captura/CapturaViewModel.kt` inyecta `RegistroDeFallos`). Ubicar el mapeo en `data/diagnostico/`
  respeta las dos, mientras que ponerlo en `ui/error/` obligaría a la capa de presentación a conocer el
  vocabulario de la analítica.
- Se escribe **exhaustivo**, sin `else`, igual que `aRecurso()`. Los **diecinueve** `DominioError` que el
  login no puede producir (los diecisiete de reporte y medio, más `PermisoDenegado` y `ArchivoLocalAusente`)
  caen todos en `INESPERADO`, que es exactamente lo que significan en contexto: si alguno llegara, sería una
  fuga de capas. `PermisoDenegado` no es hipotético —un `PERMISSION_DENIED` de Phone Auth lo produce y en
  pantalla sale como "Algo salió mal"—, pero no es un paso medible del embudo. La forma exhaustiva es la que
  hace que el compilador avise cuando se agregue una variante nueva a `DominioError`; con `else` el error se
  descubre en silencio. Dos precisiones sobre la tabla de arriba, resueltas al implementar:
  - El plan decía "los once": son diecinueve. El comportamiento pedido queda igual, solo la cuenta.
  - `DominioError.TelefonoNoAutorizado` no aparece en la tabla porque hoy **nadie lo emite** (el punto 5 es un
    `Result.Success(false)`, no un error), pero el `when` exhaustivo tiene que decidir su destino: mapea a
    `TELEFONO_NO_AUTORIZADO`, que es su desenlace exacto. Si algún día una ruta lo produce, el evento dice la
    verdad sin cambiar el código.

**Sin PII.** Ningún parámetro lleva el número, el `uid` ni nada del custodio: el `method` es constante y el
desenlace es un valor de un conjunto cerrado de nueve. Es la misma política que ya gobierna `RegistroDeFallos`
(S13: *"no custodio data in tags"*) y es la razón por la que `TELEFONO_NO_AUTORIZADO` se puede medir sin que
la dimensión arrastre datos personales. La allowlist vive en `telefonos_autorizados/{e164}` y el teléfono es
PII por definición.

---

## Tareas

### Tarea 1 — El contrato (`data/diagnostico/RegistroDeEventos.kt`, nuevo)

- Interfaz `RegistroDeEventos` con el único método `registroLogin(metodo, resultado)`, con el KDoc que explique
  las dos razones de `RegistroDeFallos` (fuera del ViewModel, y falseable en pruebas de JVM) más el tercer
  motivo propio de aquí: el conjunto de desenlaces es cerrado, por eso son enums.
- `enum class MetodoAuth(val valor: String)` y `enum class ResultadoLogin(val valor: String)` con la tabla de arriba.
- `fun DominioError.aResultadoLogin(): ResultadoLogin`, exhaustiva y sin `else`.
- En el KDoc, dejar escrito que ningún parámetro lleva datos del custodio y por qué (es la restricción que
  hace este evento publicable).

### Tarea 2 — La implementación (`data/diagnostico/RegistroDeEventosAnalytics.kt`, nuevo)

- `class RegistroDeEventosAnalytics(private val analytics: FirebaseAnalytics) : RegistroDeEventos`.
- El cuerpo es el fragmento pedido, con los dos valores juntos (encapsulados en los enums, nunca pasados por
  parámetro):

  ```kotlin
  override fun registroLogin(metodo: MetodoAuth, resultado: ResultadoLogin) {
      val bundle = Bundle()
      bundle.putString(FirebaseAnalytics.Param.METHOD, metodo.valor)
      bundle.putString(CLAVE_RESULTADO, resultado.valor)
      analytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle)
  }
  ```

- `CLAVE_RESULTADO = "login_result"`, en un `private companion object` como las demás constantes privadas de la
  casa (`RegistroDeFallosCrashlytics.kt:21-23`). `FirebaseAnalytics.Param` no trae constante para el desenlace
  (porque no es un parámetro reservado de `login`), así que la cadena es nuestra y vive en un solo sitio.
- Constructor con `FirebaseAnalytics` inyectado (no `getInstance()` adentro), para que el módulo de Hilt sea el
  único que habla con el singleton —igual que `RegistroDeFallosCrashlytics` recibe su `FirebaseCrashlytics`.
- KDoc con el "por qué": el `method` conserva el significado que le da Google y el desenlace va aparte, para
  que la consola pueda desglosar la tasa de fallo por paso.

### Tarea 3 — El binding (`di/DiagnosticoModule.kt`)

- Un `@Provides @Singleton fun proveeRegistroDeEventos(): RegistroDeEventos` junto al `proveeRegistroDeFallos`
  que ya existe en el módulo, con `FirebaseAnalytics.getInstance()`.
- El módulo **no** se toca en ningún otro lugar. No se necesita el enganche de `ModoEmulador`: Analytics no
  tiene emulador (ver "Verificación"). **Corrección al implementar:** sí hace falta `@ApplicationContext` —
  `FirebaseAnalytics.getInstance()` no tiene sobrecarga sin argumentos y el binding no compila sin el
  `Context`; quedó como los demás módulos que hablan con un singleton de Firebase (`AuthModule`,
  `PipelineModule`): `getInstance(context)`.

### Tarea 4 — Los seis puntos (`ui/login/LoginViewModel.kt`)

- Añadir `private val registroDeEventos: RegistroDeEventos` al constructor (tercer parámetro, después de
  `allowlistRepositorio`).
- Un helper privado y pequeño, para que las seis llamadas sean una línea y el `method` **nunca** se escriba
  en un punto de llamada (si se escribiera, bastaría un typo para partir la dimensión en dos):

  ```kotlin
  private fun registrarLogin(resultado: ResultadoLogin) =
      registroDeEventos.registroLogin(MetodoAuth.PHONE, resultado)
  ```

- Las seis inserciones, según la tabla de arriba. Las de los puntos 1, 4 y 5 llevan el enum explícito; las de
  los puntos 2, 3 y 6 llevan `error.aResultadoLogin()`.
- El punto 5 va **antes** del `sesionRepositorio.cerrarSesion()`: se registra que la allowlist rechazó, que es
  el hecho; que además se cierre la sesión es consecuencia interna. El punto 4 va junto a `limpiarSesionPendiente()`.
- **No tocar el resto del flujo.** En particular no se corrigen los `verificando`/`enviandoSms` que quedan en
  `true` en el camino de éxito (`LoginViewModel.kt:166` y `:98`): hoy son inocuos porque `AppNavHost` saca
  `Rutas.LOGIN` de la pila de inmediato. Es un defecto real y está anotado aquí a propósito, pero arreglarlo es
  otro cambio.

### Tarea 5 — Pruebas

- `app/src/test/.../CapturaFakes.kt`: `FakeRegistroDeEventos` con `val registrados = mutableListOf<Pair<MetodoAuth, ResultadoLogin>>()`,
  calcado de `FakeRegistroDeFallos` (L201-206), que solo acumula.
- `ui/login/LoginViewModelTest.kt`: actualizar `crearViewModel()` (L43-46) con el tercer argumento, y añadir
  el fake a `setUp()`. Los **10 tests existentes no cambian de aserciones** — esa es la señal de que la
  instrumentación no alteró el comportamiento del login.
- Casos nuevos en `LoginViewModelTest`:
  1. allowlist autoriza → exactamente un `registroLogin(PHONE, OK)`.
  2. allowlist rechaza → exactamente un `(PHONE, TELEFONO_NO_AUTORIZADO)`, y **cero** eventos `OK` (es el fallo
     que más caro sale y el más fácil de introducir por un descuido).
  3. número inválido → `(PHONE, NUMERO_INVALIDO)` y no se pide el SMS.
  4. `EventoVerificacion.Fallo(DominioError.SinRespuestaSms)` → `(PHONE, SIN_RESPUESTA_SMS)`.
  5. `verificarCodigo` falla con `DominioError.CodigoInvalido` → `(PHONE, CODIGO_INVALIDO)`.
  6. la consulta a la allowlist falla con `SinConexion` → `(PHONE, SIN_CONEXION)`.
- Test nuevo del mapeo puro, en `app/src/test/.../data/diagnostico/ResultadoLoginTest.kt`: la matriz
  `DominioError` → `ResultadoLogin` completa, incluida la equivalencia `ServidorIndisponible → SIN_CONEXION` y
  la traducción de los errores de reporte a `INESPERADO`. Es una función pura, se testea sin Firebase —el mismo
  criterio que `AuthErrorMappingTest.kt` (su KDoc explica que evita instanciar excepciones de Firebase porque
  `android.jar` va sin mocks).

### Tarea 6 — Documentación

- `CHANGELOG.md`: entrada con fecha y hora, resumen, cambios y lista de archivos (regla de steering de
  `.kiro/steering/changelog.md`).
- `AGENTS.md`: una línea en el bloque de estado, junto a PHONE_AUTH, describiendo que el login se instrumenta
  con `RegistroDeEventos` y que la analítica **no** lleva PII.

---

## Fuera de alcance (deliberado)

- **No se llama `setUserId` / `setAnalyticsUserId`.** Atar el evento al `uid` del custodio haría la analítica
  mucho más potente (permitiría "inicios de sesión activos por custodio"), pero mete PII en la dimension que
  se agrega y abre una pregunta de retención y de avisos que este plan no puede responder solo. Queda como
  decisión de producto, registrada aquí para que no se pierda. Lo que **sí** hace falta y ya se tiene gratis:
  `docs/PLAN.md` T7 ya contempla `setUserId` para Crashlytics; ahí la decisión ya se tomó aparte.
- **No se registra `logout`.** Existe un punto de cierre de sesión limpio (`AjustesViewModel.confirmarCerrarSesion()`,
  L74-94) y Google provee `Event.SIGN_OUT`; es una línea más cuando haga falta. Este plan se ciñe al login que
  es lo que pidió.
- **No se toca `google-services.json`** ni se despliega nada en la consola. `google_app_id` ya se sintetiza y
  la recolección está en el valor por defecto.
- **No se registran los demás pasos de la app** (crear viaje, tomar foto, subir). El contrato admite
  `registroLogin`; ampliarlo a otros eventos es agregar métodos a la misma interfaz, no rehacer nada de esto.
- **No se arreglan las banderas `verificando`/`enviandoSms`** que quedan en `true` tras el éxito (Tarea 4).

---

## Riesgo y límites de la plataforma

- **Riesgo de datos: bajo.** El evento se emite con dos valores de conjuntos cerrados, sin nada identificable.
  Este es el motivo por el que el plan es publicable tal cual; un `metodo` o un desenlace que llevaran el
  número lo arruinaría.
- **Riesgo de comportamiento: bajo.** `logEvent` es no bloqueante, en hilo y sincrónico desde el punto de vista
  del llamador, y no lanza. Aun así, la interfaz no atrapa excepciones: si el SDK fallara, el fallo se vería en
  el login. Se acepta conscientemente — un `try/catch` vacío alrededor de la instrumentación es peor, porque
  escondería justo el fallo que se está intentando medir. Si alguna vez se decide lo contrario, se registra
  también con `RegistroDeFallos`, no con un `catch` mudo.
- **Límite de plataforma, no del código:** los datos de un APK **debug** también se procesan, pero el
  proyecto no tiene separación entre debug y producción en Analytics, así que **los eventos de desarrollo se mezclan con
  los de campo** en la misma propiedad. Como `ModoEmulador` solo desvía Firestore, Storage y Auth
  (`ModoEmulador.kt:11-13`), la analítica de un APK debug sale a producción de verdad. Mientras no se separe
  por propiedad, hay que leer la analítica sabiendo que una parte del volumen es de desarrollo.
- **Confirmación pendiente en consola:** el nombre del parámetro `login_result` debe confirmarse en el
  **DebugView** de la consola, que es donde se ve si el parámetro llegó tal cual o si el backend lo renombró.
  Los límites de longitud están verificados y ningún valor los roza, pero la comprobación de que el nombre
  sobrevive el viaje es de una sola corrida en la consola (ver Tarea 6 de "Verificación funcional").

---

## Verificación

**Automática (en este repo)** — ejecutada el 2026-09-28, las tres verdes:

- `./gradlew testDebugUnitTest` — quedó en **249/249** (239 previos + 6 casos nuevos de `LoginViewModelTest` + 4
  de `ResultadoLoginTest`), 0 fallos. Los 10 tests de `LoginViewModelTest` que ya existían siguen verdes **sin
  tocar sus aserciones**: solo cambiaron `setUp()` y `crearViewModel()` con el tercer argumento del constructor.
- `./gradlew assembleDebug` — compila.
- `./gradlew compileDebugAndroidTestKotlin` — verde, comprobando que el cambio de firma no alcanzó a los tests
  instrumentados (ninguno instancia `LoginViewModel`).

**Lo que estas pruebas NO prueban**, y hay que decirlo: que el evento llegue a la consola. Los tests de JVM
afirman que el ViewModel llama al fake con el par correcto; `FirebaseAnalytics` nunca se toca, y **Analytics
no tiene emulador**, así que no hay forma de probarlo sin salir a la red.

**Funcional (manual, requiere la consola en `custodio-906e7268`):**

1. Con el APK instalado y la app en primer plano, entrar con un teléfono autorizado y **DebugView → Events**
   activado en la consola.
2. Se espera un evento `login` con `method=PHONE` y `login_result=ok` (además de los automáticos de
   `first_open`/`session_start`, que es la prueba de que el SDK ya estaba recolectando).
3. Repetir con un teléfono **fuera** de la allowlist: debe llegar `login_result=telefono_no_autorizado` y
   **cero** eventos con `ok` de esa sesión.
4. Repetir con un código incorrecto: `login_result=codigo_invalido`.
5. Solo si alguno de los cuatro no muestra el parámetro con el nombre esperado, abrir la definición del evento
   en la consola: ahí se ve si el backend lo reservó o renombró.

> Nota operativa: el login por SMS real usa el **APK release** contra producción (el debug apunta al emulador
> vía `modo_emulador` para Auth/Firestore/Storage). Pero como la analítica no se desvía, el paso 1 puede
> hacerse con cualquiera de los dos APKs —lo que no se puede es autenticar de verdad contra el emulador hasta
> que se re-exporte el seed (ver `docs/PLAN_ALLOWLIST_POR_DOCUMENTO.md`, "Resultado de la implementación").

---

## Prerequisito de ejecución

Este repo marca `docs/GENERIC_RULES.md` como lectura obligatoria antes de modificar código, y `AGENTS.md` fija
las convenciones (AGP 9, workflow de pausa para Sync). Para **implementar** este plan hay que aplicarlas; para
el diseño de este documento ya se leyeron. Ninguna tarea toca `libs.versions.toml`, `build.gradle.kts` ni
`settings.gradle.kts`, así que **no hay pausa para Sync** en este plan.
