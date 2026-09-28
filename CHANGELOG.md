# CHANGELOG

Registro de cambios del proyecto Echo. Las entradas más recientes van primero.

## 2026:09:28 12:32

**Resumen:** Se implementó `docs/PLAN_ANALYTICS_LOGIN.md`: el login por SMS ahora emite el evento `login` de Firebase Analytics con el método y el **desenlace**, en los seis puntos en que puede terminar. Antes la app no reportaba un solo evento propio y la pregunta de negocio —*de cada 100 custodios que abren la app, ¿cuántos terminan adentro y en qué paso se quedan?*— no tenía respuesta.

**Cambios:**
- **Contrato `RegistroDeEventos`** en `data/diagnostico/` (el mismo bucket que `RegistroDeFallos`): interfaz de un método `registroLogin(metodo, resultado)`, más los enums `MetodoAuth` (hoy un solo caso, `PHONE`) y `ResultadoLogin` (nueve desenlaces). Son enums y no cadenas libres porque **el valor ES la dimensión por la que se va a filtrar** en la consola: un `String` libre permitiría emitir `login_result="ok"` y `login_result="OK"` como dos dimensiones distintas sin que nada avise.
- **Implementación `RegistroDeEventosAnalytics`** con el patrón documentado por Firebase (`Bundle` + `FirebaseAnalytics.Param.METHOD` + `FirebaseAnalytics.Event.LOGIN`). El `method` conserva el parámetro reservado que Google le da al método de autenticación y el desenlace va aparte, en `login_result`: cargarle el desenlace al `method` vaciaría su sentido y la consola no podría desglosar la tasa de fallo por paso. `FirebaseAnalytics.Param` no trae constante para el desenlace, así que la cadena vive en un `private companion object`.
- **Los seis puntos de `LoginViewModel`** quedan instrumentados, cada uno con un comentario que dice cuál es: número inválido, fallo al pedir el SMS (incluido el watchdog de 30 s del broker de GMS), fallo de `verificarCodigo`, **éxito**, teléfono fuera de la allowlist, y fallo de la consulta a la allowlist. Los dos caminos de éxito (OTP tecleado y auto-lectura del SMS) confluyen en `validarAllowlist(...)`, así que el `OK` se instrumenta **una vez, no dos**. El punto de allowlist rechazada se registra **antes** del `cerrarSesion()`: que la allowlist rechazó es el hecho, el cierre es consecuencia interna. Un helper privado de una línea concentra el `method` para que ningún punto de llamada pueda escribirlo con un typo.
- **Mapeo `DominioError.aResultadoLogin()`**, exhaustivo y sin `else` como el `aRecurso()` que ya existe: el compilador avisa cuando se agrega una variante a `DominioError`. Vive en `data/diagnostico/` y no en `ui/` para que la capa de presentación no conozca el vocabulario de la analítica (la dirección `ui` → `data.diagnostico` ya tiene precedente en `CapturaViewModel`).
- **Ningún parámetro lleva PII**: ni número, ni `uid`, ni nada del custodio. El método es constante y el desenlace es un valor de un conjunto cerrado — es la política de S13 y es la razón por la que este evento es publicable tal cual. Sigue **fuera de alcance deliberado**: `setUserId` (ataría el evento al custodio y abre una decisión de retención) y el evento `logout`.
- **10 casos nuevos de prueba:** los seis desenlaces contra el fake en `LoginViewModelTest` (incluido el que afirma que el fallo más caro, teléfono no autorizado, viene acompañado de **cero** eventos `ok`), y la matriz completa `DominioError` → `ResultadoLogin` en `ResultadoLoginTest`, que además cuenta las variantes para que una nueva no se cuelgue en `INESPERADO` y comprueba los límites de GA4 (100 caracteres, ASCII). `FakeRegistroDeEventos` solo acumula, calcado de `FakeRegistroDeFallos`.

**Dos desviaciones del plan, por hechos verificados en el código:**
- **`@ApplicationContext` sí hacía falta.** El plan decía que no; `FirebaseAnalytics.getInstance()` no tiene sobrecarga sin argumentos y el binding no compilaba. Se resolvió como los demás módulos que hablan con un singleton de Firebase (`AuthModule`, `PipelineModule`): `getInstance(context)` con el `Context` de aplicación.
- **La tabla del plan decía "los once `DominioError` que el login no puede producir"**; son **diecinueve** (los diecisiete de reporte y medio, más `PermisoDenegado` y `ArchivoLocalAusente`). `PermisoDenegado` no es hipotético: un `PERMISSION_DENIED` de Phone Auth lo produce, y en pantalla sale como "Algo salió mal". El comportamiento que el plan pedía —todo lo que el login no puede medir cae en `INESPERADO`— queda igual. Y `DominioError.TelefonoNoAutorizado`, que la tabla no mencionaba, mapea a `TELEFONO_NO_AUTORIZADO`: hoy nada lo emite (el punto 5 es un `Result.Success(false)`, no un error), pero si algún día lo hiciera, el desenlace exacto es ese y no `INESPERADO`.

**Verificación:** `testDebugUnitTest` **249/249** (239 antes + 6 en `LoginViewModelTest` + 4 en `ResultadoLoginTest`), 0 fallos. Los **10 tests que ya existían de `LoginViewModelTest` siguen verdes sin tocar una sola aserción** — solo se actualizaron `setUp()` y `crearViewModel()` con el tercer argumento: esa es la señal de que la instrumentación no alteró el comportamiento del login. `assembleDebug` y `compileDebugAndroidTestKotlin` verdes. Ninguna tarea tocó `libs.versions.toml` ni los scripts de build, así que no hubo pausa para Sync.

> Lo que estas pruebas **no** cubren, y hay que decirlo: que el evento llegue a la consola. Los tests de JVM afirman que el ViewModel llama al fake con el par correcto; `FirebaseAnalytics` nunca se toca y **Analytics no tiene emulador**. La confirmación de que `login_result` conserva su nombre es de una sola corrida en el **DebugView** de la consola (`custodio-906e7268`), manual. Y sigue vigente la advertencia del plan: la propiedad no separa debug de producción en Analytics, así que **los eventos de un APK debug salen a producción de verdad** (`ModoEmulador` solo desvía Firestore, Storage y Auth).

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/data/diagnostico/RegistroDeEventos.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/diagnostico/RegistroDeEventosAnalytics.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/DiagnosticoModule.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/login/LoginViewModel.kt`
- `app/src/test/java/com/ai27/app/custodio/CapturaFakes.kt`
- `app/src/test/java/com/ai27/app/custodio/ui/login/LoginViewModelTest.kt`
- `app/src/test/java/com/ai27/app/custodio/data/diagnostico/ResultadoLoginTest.kt` (nuevo)
- `AGENTS.md`
- `CHANGELOG.md`

## 2026:09:28 12:02

**Resumen:** Versión nueva del logo maestro (`brand/echo-logo.png`) regenerada en las 20 capas de `res/`. El diseño es otro —fondo plano en vez de degradado, y la marca es un ojo blanco con filete gris más tres ondas azules, **1.26 veces más ancha que alta**— y el generador hubo que cambiar en tres puntos para producirla bien. No se tocó Kotlin.

- **`load()` normaliza a RGBA.** El maestro nuevo viene como RGB de 3 canales y `extrae_marca` leía cuatro por píxel: el resultado era una imagen corrida (la fila desalineada un canal) en vez de un error obvio. Ahora `load()` devuelve siempre RGBA y soporta 0/2/3/4/6.
- **El fondo se mide en vez de Rampas de brillo/saturación.** El fondo nuevo es `#112439`, saturación 40: la rampa de saturación del script (`> 30`) lo declaraba tinta y la marca extraída salía quadrada. Ahora el script toma el color del anillo exterior (que es fondo puro) y usa su **variación por canal como zona muerta**, de modo que el mismo código sirve para un fondo plano y para uno con degradado sin comerse el borde de la marca. Con el maestro actual la variación es de cero y la tinta sale con el antialiasing del maestro intacto.
- **La escala sale de la zona segura y no de un porcentaje del lado.** `LADO_MARCA = 0.66` se suponía válido porque la marca anterior era casi cuadrada (0.33 del lado son 35.6dp de semiancho contra 36dp de radio). La nueva es ancha: 0.66 del lado dejaba las puntas de las ondas pegadas al recorte del lanzador. La perilla ahora es `RADIO_SEGURO_DP = 36` —el radio que debe caber la tinta más lejana desde el centro— y da 70.5×55.75dp de marca: **0.01% fuera del círculo de 72dp, 0.00% fuera del squircle de 72dp**, contra 41% del badge entero. El radio se mide sobre la tinta real y no sobre la diagonal del bbox, cuyas esquinas están vacías en una marca ancha y encogerían el icono sin necesidad. `RADIO_SEGURO_DP = 33` es la zona segura estricta de Google (66dp), documentada como la alternativa más conservadora.
- **`ic_launcher_background.xml` pasó de degradado a relleno liso `#FF112439`**, el color medido sobre el maestro. El script lo imprime en cada corrida para que el vector no se quede viejo.
- **La variante clara de la marca repinta dos condiciones, no una.** El maestro nuevo mete un filete gris (`#808080`) alrededor del glifo blanco, también de saturación 0: la regla anterior (poco saturado → tinta `#1A2130`) se lo comía y engordaba la silueta. Ahora se repinta lo que es poco saturado **y** claro (`SAT_GLIFO = 60`, `BRILLO_TINTA = 190`), así el glifo pasa a tinta y el filete conserva su tono. Las ondas (`#3A70C8`, sat 142) y el marino de sus sombras (`#183463`, sat 75) se quedan igual.

**Verificación:** la corrida imprime las medidas del maestro y la simulación de recorte; composición verificada por histograma (la marca sale 8.11% azul `#3A70C8` + 3.70% blanco en el foreground, como en el maestro) y el centro de la marca en el lienzo queda en 215.5/215.0 de 432. La variante clara **no deja ni un píxel casi blanco** (0 verificados), que es lo que evita un halo invisible sobre el lienzo claro. `assembleDebug` verde; no se corrieron tests de unidad porque no cambió código Kotlin. `AGENTS.md` actualizado con las mediciones del maestro nuevo.

## 2026:09:28 10:15

**Resumen:** Incidente de login en campo, cerrado. Tras el fin de semana los custodios no podían entrar (la app mostraba "Algo salió mal. Intenta de nuevo."). El diagnóstico halló **dos causas independientes**; ambas quedaron corregidas del lado de infra/consola, sin tocar código de la app. Se actualizó `AGENTS.md` de este repo y de `pf-infra-firebase`, y el `DESPLIEGUE-PROD-CHECKLIST.md`.

**Causas y correcciones:**
- **Certificado de firma rotado sin registrar (impacto: todos los usuarios distribuidos).** El keystore de release se regeneró con `CN=Echo` el 2026-09-25; su SHA-256 no estaba registrado en la app Android de Firebase, así que Phone Auth fallaba la verificación de app antes de enviar el SMS. Se registraron el SHA-1 `FA:D9:FE:76:1E:A5:17:DA:76:F6:07:E5:EF:17:62:89:C2:1F:1C:43` y el SHA-256 `61:D3:1A:98:BF:62:F6:7F:EF:33:EA:B4:71:2B:73:CC:AC:79:33:DF:A6:D5:56:7E:30:90:E8:0D:DE:A0:04:72` (los del `app/keystore/release.jks` vigente) y se rebajó `app/google-services.json`. Verificado contra la Firebase Management API: los dos SHA registrados coinciden con el keystore.
- **`beforeCreate` pasó de INERTE a VIVO (impacto: custodios sin cuenta previa).** GCIP se activó el fin de semana y la función `bloquearTelefonoNoAutorizado` quedó registrada con trigger `user.beforeCreate`. Ahora autoriza por existencia del documento `telefonos_autorizados/{e164}` (id == número) al crear la cuenta.
- **Documento de allowlist con id malformado (impacto: un custodio).** El documento de Maximiliano Ruíz vivía bajo `+5255222679536` (14 dígitos) con el campo `telefono` correcto `+525522679536`. La regla y `beforeCreate` autorizan por id del documento, no por el campo. Se creó su cuenta de Auth, se escribió `telefonos_autorizados/+525522679536` (id correcto) y `usuarios/{uid}`, se borró el documento de 14 dígitos y se agregó su número a `pf-infra-firebase/provision/telefonos.csv`. Barrido completo: 37 documentos, 0 malformados.

**Regla que sale del incidente:** rotar el keystore de release obliga a registrar el SHA nuevo en la consola de Firebase en la misma maniobra; si no, todo login por SMS de la build nueva se cae en la verificación de app, antes del OTP.

**Verificación:** solo lectura contra `custodio-906e7268` para el diagnóstico; las correcciones se hicieron con el Admin SDK (allowlist/usuarios/Auth) y en la consola (SHA). No se tocó código ni se corrió build de la app.

## 2026:09:25 19:05

**Resumen:** Plan para instrumentar el login con Firebase Analytics. **No se implementó: el cambio es solo de documentación.** Se traduce en `docs/PLAN_ANALYTICS_LOGIN.md`. La app hoy no reporta un solo evento propio: la dependencia `firebase-analytics` está declarada y sus componentes entran al manifiesto, pero en todo `app/src/` hay cero llamadas a `logEvent`. El plan registra el evento `login` con `method` fijo y un parámetro de desenlace, en los seis puntos en que el login por SMS puede terminar.

**Cambios:**
- Se documentó que el login tiene seis desenlaces distinguibles y que los dos caminos de éxito (OTP tecleado y auto-lectura del SMS) confluyen en un solo punto, `validarAllowlist(...)`, así que el evento de éxito se instrumenta una vez y no dos.
- Se verificó que no hay bloqueo de arranque: `app/google-services.json` no trae `google_app_id`, pero el plugin de `google-services` lo sintetiza desde `mobilesdk_app_id` (confirmado en el recurso empaquetado de release). Analytics inicializa sin tocar la consola.
- Se verificó que no hay doble conteo: `AuthDataSource.flujoVerificacion` emite un único evento terminal y cierra el flujo, de modo que una llamada a `enviarCodigo` produce a lo sumo un evento.
- Decisiones del humano, ya resueltas en el plan: `method` = `"PHONE"` fijo con el desenlace en un parámetro aparte (para no vaciar de sentido el parámetro reservado que Google le da al método de autenticación); cobertura de éxito **y** todos los fallos; y arquitectura de interfaz + Hilt, igual que `RegistroDeFallos`, para que `FirebaseAnalytics.getInstance()` no aparezca en un ViewModel y el evento se pueda falsear en pruebas de JVM.
- La dimensionalidad se cerrará con enums (`MetodoAuth`, `ResultadoLogin`) y no con cadenas libres: el valor **es** la dimensión por la que se va a filtrar, y un `String` libre permitiría partirla en dos sin que nada avise. Ningún parámetro llevará PII — ni número ni `uid` —, lo que además de la política S13 es lo que hace el evento publicable.
- Se dejó registrado como **fuera de alcance deliberado**: `setUserId` (ataría el evento al custodio y abre una decisión de retención que este plan no puede resolver solo), el evento de `logout`, y el arreglo de las banderas `verificando`/`enviandoSms` que quedan en `true` tras el éxito y hoy son inocuas solo porque `AppNavHost` saca `Rutas.LOGIN` de la pila de inmediato.
- Advertencia operativa: la propiedad no tiene separación entre debug y producción en Analytics, y `ModoEmulador` solo desvía Firestore, Storage y Auth, así que **los eventos de un APK debug salen a producción de verdad**.

**Verificación:** no aplica — el cambio es documental y no toca código. El plan deja escrito qué línea de límite no puede cubrir ninguna prueba automática: **Analytics no tiene emulador**, así que los tests de JVM solo pueden afirmar que el ViewModel llama al fake con el par correcto, nunca que el evento llegue a la consola. La confirmación del nombre del parámetro en el backend queda como paso manual en el DebugView.

**Archivos modificados:**
- `docs/PLAN_ANALYTICS_LOGIN.md` (nuevo)
- `CHANGELOG.md`

## 2026:09:25 16:20

**Resumen:** Se actualizó el README para reflejar que el carril A (consola de Firebase) quedó cerrado el 2026-09-25. El README decía que el carril A "sigue abierto" y que sin él no se podía repartir la app, lo cual ya no era cierto (el propio `docs/PLAN.md` §9 tenía A1–A4 hechas, y en esta fecha se activó el candado `beforeCreate` tras el upgrade a GCIP).

**Cambios:**
- Sección "Estado": el carril A pasa de "abierto" a "cerrado" con resumen del estado de producción.
- Nueva sección "Estado de producción (2026-09-25)": lista lo desplegado/verificado en `custodio-906e7268` (GCIP `IDENTITY_PLATFORM`, reglas e índices, allowlist `telefonos_autorizados`, `beforeCreate` activo) y los pendientes menores que no bloquean el reparto (`config/analistas`, catálogo R4, seed del emulador, verificación en dispositivo).
- "Prerrequisitos del carril A" → "Referencia del carril A — ya ejecutado" (se conserva como referencia de reprovisión, ya no como checklist pendiente).
- Bullet del candado `beforeCreate`: de "se despliega solo con Blaze / en Spark la app cierra sesión" a "desplegada y activa desde 2026-09-25 (GCIP)", con nota del diseño A y el runbook.
- Crashlytics y "Verificación manual del ciclo completo": se quita el condicional "cuando exista la consola" / "con el carril A disponible".

**Archivos modificados:**
- `README.md`

## 2026:09:25 16:05

**Resumen:** La marca entra en la pantalla de login, sobre el título "Echo". Va **sin** el fondo del badge (la marca suelta) a 84dp. Como el glifo del logo es blanco y la app tiene tema claro, se generaron **dos variantes** desde el mismo maestro y `MarcaEcho` elige con `LocalTemaOscuro`: sobre el lienzo claro el glifo se remapea al token de texto del tema (`#1A2130`), porque de otro modo sería invisible.

**Cambios:**
- `brand/generar_icono.py` ahora emite también la marca in-app: `drawable-*dpi/echo_logo.png` (tema oscuro) y `echo_logo_claro.png` (tema claro), en las cinco densidades a partir de `MARCA_DP = 84`. El lienzo es cuadrado con la marca inscrita, así que `ContentScale.Fit` —el valor por defecto de `Image`— la dibuja 1:1 sin deformarla.
- La variante clara **no repinta el maestro**: mapea al token de tinta solo los píxeles poco saturados (el glifo) y deja intactas las ondas azules. El umbral es seguro por un margen amplio: el azul de las ondas tiene saturación ~192 y el glifo blanco ~0, así que el borde difuminado de cada uno cae limpiamente a un lado o al otro. Se conservó el alfa del maestro, de modo que el antialiasing del glifo no hubo que difuminarlo a mano.
- **Verificado byte a byte** que extender el script **no alteró** las capas del icono del launcher de la entrada anterior (`shasum` antes/después idéntico): el generador tiene una sola fuente de verdad y las dos salidas pueden regenerarse por separado sin efectos colaterales.
- `ui/components/Marca.kt` (nuevo): `MarcaEcho(tamano = 84.dp)`, siguiendo la convención de la biblioteca —KDoc con el porqué y `@Preview` Oscuro/Claro. El `contentDescription` es `null` a propósito: el nombre "Echo" se escribe justo debajo, y describir la imagen haría que el lector de pantalla lo dijera dos veces.
- **`Vitrina` ahora provee `LocalTemaOscuro`** además de `EchoTheme`, igual que `MainActivity` en ejecución. Sin este cambio la vista previa "Claro" de `MarcaEcho` habría dibujado la variante oscura, porque el `CompositionLocal` tiene `true` por defecto y `Vitrina` solo cambiaba el esquema de color. Es un arreglo de la biblioteca, no un detalle de esta pantalla: cualquier componente futuro que lea esa preferencia suffería lo mismo.
- `ui/login/LoginScreen.kt`: `MarcaEcho()` + `Spacer(24.dp)` encima del título, **fuera** del `when` de fase, así que aparece igual en la fase de número y en la de código.

**Verificación ejecutada:** `./gradlew testDebugUnitTest` **239/239** (sin tocar) y `assembleDebug` + `compileDebugAndroidTestKotlin` verdes. `aapt2 dump resources`: `echo_logo` y `echo_logo_claro` presentes con las cinco densidades. Comprobado por lectura de píxeles que la variante clara quedó con el glifo en `#1A2130` y las ondas sin cambio. **No verificado en dispositivo**: no había ninguno conectado, así que ni el aspecto final en pantalla ni el comportamiento con el teclado abierto se comprobaron en un teléfono real.

**Nota de composición:** con teclado abierto la columna de login se desborda y `verticalScroll` la alinea arriba, de modo que la marca no se recorta (el `Arrangement.Center` no centra cuando no hay holgura porque el scroll mide el contenido sin alto acotado). Aun así, los 108dp de cabecera que suma la marca empujan el campo de teléfono hacia abajo en pantallas cortas: conviene mirarlo en un equipo real antes de la distribución.

**Archivos modificados:**
- `brand/generar_icono.py` (emite también la marca in-app, dos variantes)
- `app/src/main/res/drawable-*dpi/echo_logo.png`, `echo_logo_claro.png` (nuevos)
- `app/src/main/java/com/ai27/app/custodio/ui/components/Marca.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/components/Vitrina.kt` (provee `LocalTemaOscuro`)
- `app/src/main/java/com/ai27/app/custodio/ui/login/LoginScreen.kt`
- `AGENTS.md`

## 2026:09:25 15:40

**Resumen:** Entra el logo de Echo como icono del launcher. El icono de la app era todavía el robot verde de la plantilla de Android Studio. El logo se instaló como **icono adaptativo de dos capas** (fondo + marca), no como la imagen tal cual, porque el maestro viene "compuesto" y no sobrevive al recorte que hace el lanzador. Las capas son un **artefacto generado**: la fuente de verdad es `brand/echo-logo.png` y `brand/generar_icono.py` las produce de forma reproducible.

**Cambios:**
- **El maestro salió de `res/`.** Estaba en `app/src/main/res/echo-logo.png`, donde un archivo suelto en la raíz de `res/` es un error de build. Se movió a `brand/echo-logo.png`, fuera de `res/`, para que además no viaje dentro del APK (49 KB que no usa nadie).
- **Separación en capas.** Midiendo el logo: el fondo es un degradado diagonal oscuro (`#171F2E` arriba-izquierda → `#0C1321` abajo-derecha) y la marca es un glifo blanco más unas ondas azules (`#1848D8`) en x 31%..91%, con centro óptico al 61%. Como el contenido llega al 98% del lienzo, usarlo entero como `foreground` lo expondría al recorte del lanzador, que solo conserva el 72dp central de 108dp: las ondas azules se cortarían. Por eso:
  - `drawable/ic_launcher_background.xml` = el degradado medido sobre el maestro, como vector con `<gradient>` (sustituye al robot verde). Vector y no PNG porque pesa cuatro líneas y es nítido en cualquier densidad.
  - `drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png` = la marca extraída del fondo, re-centrada y escalada al 66% del lienzo. La extracción separa por dos rampas (brillo y saturación) aprovechando que el fondo no pasa de `0x2E` en ningún canal y la marca es blanca o azul saturada; el margen entre ambos es enorme, así que no hay ambigüedad. El reescalado es promedio de caja con alpha premultiplicado y halves sucesivos, porque mdpi es una reducción de 4.3x y el muestreo directo habría aliaseado.
  - `drawable-*dpi/ic_launcher_monochrome.png` = la misma silueta en blanco, para los iconos temáticos de Android 13+. El `monochrome` de la plantilla apuntaba al foreground de color, que al tintarse deja un borrón.
- **`brand/generar_icono.py`**: generador sin dependencias (trae su propio lector y escritor de PNG, porque en esta máquina no hay Pillow ni ImageMagick) con el porqué de cada decisión en el docstring. Verificado **idempotente**: ejecutarlo de nuevo reproduce las capas byte a byte.
- **Plantilla muerta borrada:** el `ic_launcher_foreground.xml` del robot y los diez `ic_launcher.webp`/`ic_launcher_round.webp` de las carpetas de densidad. Con `minSdk = 26` esos webp son inalcanzables (`mipmap-anydpi` gana a cualquier carpeta de densidad), así que nunca llegaban a mostrarse; dejaban el robot verde a un `git checkout` de distancia.
- `ic_launcher.xml` e `ic_launcher_round.xml`: `monochrome` apuntando a la capa nueva, con una nota de por qué no hay mapa de bits de respaldo.
- `AGENTS.md` §Config notes documenta el pipeline, la perilla `LADO_MARCA` y el motivo de no sustituir el badge por el foreground.

**Verificación ejecutada:** `./gradlew testDebugUnitTest` **239/239** (sin tocar) y `assembleDebug` verdes. `aapt2 dump resources` sobre el APK: `ic_launcher_background` como XML y `ic_launcher_foreground`/`ic_launcher_monochrome` con las cinco densidades (mdpi→xxxhdpi). Se **simuló** lo que ve el lanzador componiendo las dos capas y aplicando las máscaras: con máscara circular de 72/108 (la que más recorta) y con squircle, la marca entra completa, con márgenes de 1 px a cada lado (marca x 73..357 dentro del círculo visible x 72..360). La comprobación visual en un dispositivo o en el Image Asset Studio de Android Studio sigue pendiente: en esta máquina no hay forma de ver la imagen.

**Nota de diseño (no se cambió nada):** el azul de las ondas queda a ~2.6:1 de contraste contra el fondo, según la fórmula de WCAG. Es una propiedad del logo, no un defecto de la implementación, y se conservó tal cual; si en tamaños pequeños se ve apagado, el ajuste es de diseño (aclarar el azul de la marca en el maestro) y luego `python3 brand/generar_icono.py`.

**Archivos modificados:**
- `brand/echo-logo.png` (nuevo; antes `app/src/main/res/echo-logo.png`)
- `brand/generar_icono.py` (nuevo)
- `app/src/main/res/drawable/ic_launcher_background.xml` (reescrito: degradado en vez del robot)
- `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_{foreground,monochrome}.png` (nuevos)
- `app/src/main/res/drawable/ic_launcher_foreground.xml` (borrado: robot verde)
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` (capa monochrome nueva + notas)
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp`, `ic_launcher_round.webp` (borrados)
- `AGENTS.md`

**Pendiente natural (no pedido):** la pantalla de login (`ui/login/LoginScreen.kt`) no tiene hoy ningún elemento de marca; se puede añadir el logo ahí si se quiere, con un `drawable/echo_logo` generado desde el mismo maestro.

## 2026:09:25 15:55

**Resumen:** El keystore de release se regenera con `CN=Echo` y se corrige la razón que en la entrada de las 14:19 Justificaba dejar el resto de los nombres viejos. Ningún cambio de código.

**Por qué ahora:** la entrada anterior dejó el keystore con `CN=App Custodio` bajo la condición explícita de regenerarlo *"solo si la app aún no se ha distribuido a equipos reales"*. Se confirmó que **no hay ningún equipo en campo: todas las instalaciones serán frescas**. La condición se cumplió, así que la ventana para cambiarlo sin costo estaba abierta y se usó. Un keystore distinto solo obliga a reinstalar cuando ya hay instalaciones que actualizar, y reinstalar borra la cola local de evidencia y los reportes sin subir.

**Cambios:**
- **Keystore regenerado:** `CN=App Custodio, OU=AI27, O=AI27, L=CDMX, ST=CDMX, C=MX` (alias `custodio`) → `CN=Echo, OU=AI27, O=AI27, L=CDMX, ST=CDMX, C=MX` (alias `echo`). Se replicó todo lo demás del cert anterior para que el cambio sea solo de nombre: PKCS12, RSA 2048, SHA256withRSA, v3, 25 años, y **las mismas contraseñas** (así `keystore.properties` no cambió más que en `keyAlias`).
- **Respaldo del cert anterior:** `~/Documents/ai-27/keystore-respaldo-2026-09-25/` (fuera de todo repo; `~/Documents/ai-27` no está versionado), con `release-CN-AppCustodio.jks` y una copia del `keystore.properties` viejo. Se verificó que el respaldo abre con `keytool` y es byte a byte el mismo archivo. **Solo sirve como referencia histórica: no permite volver a firmar en el futuro**, porque la app ya está firmada con el nuevo.
- **Docs corregidas** (la entrada de las 14:19 razonaba sobre instalaciones que no existen):
  - `AGENTS.md` §Config notes: el package se justifies ahora por el amarre de `google-services.json` (`mobilesdk_app_id` + `package_name`), no por preservar actualizaciones; `app_custodio.db` queda marcado como legacy sin efecto funcional; y se registra el cert nuevo, su respaldo y la regla de que **el primer APK que llegue a un equipo real congela el keystore para siempre**.
  - `AGENTS.md` §Toolchain: misma regla en la viñeta de firma.
  - KDoc de `EchoDatabase.NOMBRE`: dice que la razón original perdió efecto.
  - `CHANGELOG.md`: las tres menciones de las 14:19 quedan marcadas como superadas, sin reescribir la entrada (describe el estado en el momento en que se escribió).
- **Lo que NO se tocó, y ahora por una razón distinta:** `app_custodio.db` y el `applicationId`. El primero ya se puede renombrar sin costo, pero no lo aporta: es un nombre de archivo local que nadie ve, y cambiarlo obligaría a tocar `backup_rules.xml` y `data_extraction_rules.xml`. El segundo sigue atado al cliente Android de Firebase en `app/google-services.json`, así que moverlo exigiría dar de alta otro cliente en la consola.

**Verificación ejecutada:** `keytool -list -v` sobre el keystore nuevo: `CN=Echo, OU=AI27, O=AI27, L=CDMX, ST=CDMX, C=MX`, alias `echo`, SHA256withRSA, RSA 2048, v3, válido hasta 2051. `./gradlew assembleRelease` → BUILD SUCCESSFUL. `apksigner verify --print-certs` sobre `app/build/outputs/apk/release/app-release.apk`: **un solo firmante**, `certificate DN: CN=Echo, OU=AI27, O=AI27, L=CDMX, ST=CDMX, C=MX`, SHA-256 `61d31a98…72`. `aapt2 dump badging`: `package: name='com.ai27.app.custodio'` y `application-label:'Echo'`, ambos intactos.

**Pendiente:** ninguno por el renombrado. Queda decidir si el respaldo del cert viejo se borra (hoy está fuera de todo repo) o se conserva como referencia.

## 2026:09:25 14:19

**Resumen:** La app se renombra de "App Custodio" a **Echo**. El renombrado es **deliberadamente parcial**: cambian la etiqueta del launcher y los identificadores internos, pero se dejan intactos cuatro nombres que, de tocarse, romperían instalaciones ya desplegadas. Ningún cambio de comportamiento; los 239 tests unitarios pasan sin tocar una sola aserción y la etiqueta del APK se verificó con `aapt2`.

**Cambios:**
- **Identidad visible:** `app_name` = "Echo" (esta es también la etiqueta que ve App Distribution, así que el nombre nuevo aparece ahí en la siguiente distribución). Verificado en el APK: `application-label:'Echo'`.
- **Identificadores internos:** `AppCustodioApplication` → `EchoApplication` (archivo y clase, registrado en el manifest), `AppCustodioTheme` → `EchoTheme` (composable, 4 archivos), `AppCustodioDatabase` → `EchoDatabase` (archivo y clase, 15 archivos: los `_Impl` que genera Room siguen el nombre de la clase solos), `Theme.AppCustodio` → `Theme.Echo` (`values/themes.xml` + 2 referencias del manifest), y `rootProject.name` = "Echo".
- **Lo que NO cambió, a propósito** (documentado en `AGENTS.md` §Config notes y en el KDoc de `EchoDatabase.NOMBRE`):
  - El package y el `applicationId` siguen siendo `com.ai27.app.custodio`: es lo que permite actualizar las instalaciones ya desplegadas. Verificado en el APK. **_(Razón corregida a las 15:55: no hay instalaciones en campo; la razón real es que `google-services.json` amarra el cliente Android de Firebase a ese `package_name`.)_**
  - El archivo de Room sigue siendo `app_custodio.db`. Room no puede renombrar un archivo ya creado, así que cambiarlo haría que cada instalación existente abriera una base vacía y quedaran huérfanos los reportes sin subir y los archivos de `filesDir/medios` no enviados. Las exclusiones de `backup_rules.xml` y `data_extraction_rules.xml` lo nombran por eso y se dejaron como están.
  - El keystore de release sigue con `CN=App Custodio, OU=AI27`: otro keystore no puede actualizar lo ya firmado con este, y desinstalar para reinstalar borra la cola local de evidencia. Se regenera solo si la app aún no llegó a equipos reales. **_(Superado a las 15:55, mismo día: la condición se cumplió y el keystore se regeneró como `CN=Echo`.)_**
  - `reporte_protocolo` sigue siendo "Protocolo AI27 Custodio": nombra el protocolo del cliente, no la app.
- **Documentación:** títulos de `README.md`, `docs/PLAN.md`, `docs/AUDIT.md`, `.gitignore` y `.idea/.name`; `AGENTS.md` registra la decisión y los límites del renombrado. Las menciones históricas en entradas previas de este CHANGELOG y en `docs/AUDIT.md` se dejaron como están: describen el nombre que la app tenía cuando se escribieron.
- **Fricción encontrada:** el primer build falló con `Could not find class file for 'com.ai27.app.custodio.AppCustodioApplication'` en `hiltJavaCompileDebug`. No era código fuente: eran 25 archivos generados por Hilt/KSP en `app/build/` con el nombre viejo (el sentinel de raíz agregada y los `_ComponentTreeDeps`). Se resolvió con `./gradlew clean`; conviene recordarlo para el próximo renombrado de una clase con `@HiltAndroidApp`.

**Verificación ejecutada:** `./gradlew clean testDebugUnitTest` **239/239**, `assembleDebug` verdes. `aapt2 dump badging` sobre el APK: `application-label:'Echo'` y `package: name='com.ai27.app.custodio'`. `rg "AppCustodio|App Custodio" app/src` → cero coincidencias. Pendiente de verificación en dispositivo: la actualización sobre una instalación real (que la base y la cola local sobreviven) y el aspecto del launcher con el logo nuevo cuando se entregue. **_(A las 15:55 se confirmó que no hay equipos en campo: toda instalación será fresca, así que la actualización sobre una instalación real queda descartada como escenario y el aspecto del launcher ya se verificó.)_**

**Archivos modificados:**
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ai27/app/custodio/EchoApplication.kt` (antes `AppCustodioApplication.kt`)
- `app/src/main/java/com/ai27/app/custodio/data/local/db/EchoDatabase.kt` (antes `AppCustodioDatabase.kt`)
- `app/src/main/java/com/ai27/app/custodio/data/local/db/` — 13 pruebas y 4 archivos de `data/`, `di/` que referenciaban la clase
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Theme.kt` + `MainActivity.kt`, `ui/components/Vitrina.kt`
- `app/src/androidTest/java/com/ai27/app/custodio/ui/captura/HojaEtiquetaTest.kt`
- `settings.gradle.kts`
- `README.md`, `AGENTS.md`, `docs/PLAN.md`, `docs/AUDIT.md`, `.gitignore`, `.idea/.name`

## 2026:09:25 12:17

**Resumen:** Implementado el plan `docs/PLAN_ALLOWLIST_POR_DOCUMENTO.md` (B1 de infra, P1-6): la allowlist de custodios pasa de un documento enumerable (`config/telefonos` con un arreglo `telefonos`) a **un documento por número** en `telefonos_autorizados/{e164}`. La app ahora consulta la existencia de su propio documento, lo que la regla de Firestore permite (`request.auth.token.phone_number == e164`) y que ya no expone la PII de los demás custodios.

**Cambios:**
- `FirestoreAllowlistRepositorio.contiene()` hace `get()` a `telefonos_autorizados/{telefono}` y devuelve `documento.exists()`; se conserva el envoltorio `ejecutarRemoto` y el cierre seguro por omisión (documento inexistente → nadie autorizado). Constantes `CONFIG`/`TELEFONOS` sustituidas por `TELEFONOS_AUTORIZADOS`.
- KDoc de la clase reescrito con el motivo del cambio de modelo (PII enumerable) y la nota de por qué el propio custodio sí puede leer su documento; KDoc de `AllowlistRepositorio.contiene` y de `DominioError.TelefonoNoAutorizado` alineados al modelo nuevo.
- Sin cambios en la interfaz `AllowlistRepositorio`, el DI (`PipelineModule`), `LoginViewModel` ni los tests: el `grep` de `config/telefonos` en `app/src/` confirma que la única referencia viva era el repositorio, ya eliminada.
- `README.md`: alta/baja de custodios describen el documento por número, se advierte que omitir un número en el CSV **no** lo da de baja (hay que borrar su documento) y que el seed del emulador es del modelo viejo.
- `docs/PLAN.md`: §5.1 (árbol de Firestore), §5.3 (reglas), §5.4 (identidad) y §9 A2/A3 alineados al modelo `telefonos_autorizados/{e164}`; `AGENTS.md` registra el cambio y el pendiente del seed.
- El plan `docs/PLAN_ALLOWLIST_POR_DOCUMENTO.md` pasa a estado "Implementado".
- Verificación ejecutada: `./gradlew testDebugUnitTest` **239/239** (los 11 de `LoginViewModelTest` incluidos, sin tocar) y `./gradlew assembleDebug` verdes. La verificación funcional contra el emulador sigue pendiente del lado de infra: el seed (`../pf-infra-firebase/seed`) es una exportación del modelo viejo y hay que re-exportarlo con los documentos de `telefonos_autorizados`.

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/data/remote/FirestoreAllowlistRepositorio.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/AllowlistRepositorio.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/error/DominioError.kt`
- `README.md`
- `AGENTS.md`
- `docs/PLAN.md`
- `docs/PLAN_ALLOWLIST_POR_DOCUMENTO.md`
- `CHANGELOG.md`

## 2026:09:25 11:30

**Resumen:** Se agregó el documento de planeación del paso B1 (allowlist por documento): la app debe leer su propio documento `telefonos_autorizados/{e164}` en vez del arreglo enumerable `config/telefonos`, alineándose con el modelo nuevo del perímetro en `pf-infra-firebase` (hallazgo P1-6). Solo documentación; sin cambios de código todavía.

**Cambios:**
- Se creó el plan con el estado actual verificado (la lectura está aislada en `FirestoreAllowlistRepositorio.contiene`), las tareas, el alcance excluido, el riesgo de orden de despliegue y el prerequisito de lectura de `GENERIC_RULES.md`.

**Archivos modificados:**
- `docs/PLAN_ALLOWLIST_POR_DOCUMENTO.md`

## 2026-09-24 — Hoja de etiquetado: "Guardar" anclado al pie con scroll del contenido medio

**Resumen:** el botón "Guardar" de la hoja de etiquetado quedaba recortado bajo el borde
mientras la nota "se subirá en breve" ocupaba espacio en la parte superior (unos 2 s), y al
colapsar volvía a "aparecer". El contenido medio de la hoja se hace desplazable
(`verticalScroll`) y el botón queda fijado fuera del scroll, siempre visible; se conserva el
`Button` estándar de Material3 con `enabled = tieneSituacion`.

**Cambios:**
- `ui/captura/HojaEtiqueta.kt`: el bloque superior (banner de subida, título, categorías y campo
  de ubicación) pasa a una columna con `weight(1f, fill = false)` + `verticalScroll`; el botón
  "Guardar" sale del scroll y se ancla al pie del sheet.

**Verificación:** `assembleDebug` + `installDebug` en CPH2769 contra el Emulator Suite.

## 2026:09:24 — Grupo 3 (usabilidad en campo): tanda completa cerrada (G3-A…G3-F, §17.1)

**Resumen:** la tanda §17.1 de `docs/PLAN.md` se cerró en seis clústeres que acercan el flujo
al estándar de 5 s y quitan la fricción contra WhatsApp: la app abre directo en la cámara con un
reporte activo y Reporte ya distingue "cargando" de "sin reporte" (R1, R2); la hoja de etiquetado
hereda la última Situación y ya no puede quedar atrapada sin catálogo (U1/U2); el disparo vibra,
suena obturador y el encolado hace tic (U3); Pendientes muestra estado **y** etiqueta, la rejilla
marca los enviados y el badge separa sus tres papeles, con el conteo por `COUNT` (U4/U5/U6/R3);
Historial corrige el `when` y cablea el reintento, todas las idas usan `launchSingleTop` y el
formulario de cierre sobrevive a back y rotación (U8/U10/U11); y el cierre de la tanda trajo
errores con acción de recuperación donde el reintento es genuino (U7), motivo de descarte en chips
con "Otro" como único cajón libre (U12), franja de sin conexión tri-estado en todo el flujo (U9),
texto informativo sin `FranjaAviso` en Audio (U13) y el glosario canónico **Reporte/Evidencia**
con sweep viaje→reporte y subir→enviar (U14/U15).

**Cambios:**
- `ui/navigation/RootViewModel.kt`: `hayReporteActivo` (sonda Room del arranque), `conectado` pasa a
  tri-estado `Boolean?` (no afirmar conexión al abrir).
- `ui/navigation/AppNavHost.kt`: arranque al visor con reporte activo; estado de carga de sesión;
  `launchSingleTop` en las idas del flujo, Pendientes y Detalle; `conectado` cableado a
  Captura/Audio/Archivos/Pendientes/Cierre; atajo "Capturar evidencia" desde el vacío de Archivos.
- `ui/captura/`: `CapturaViewModel` con `ultimaSituacion` heredada, `CapturaUiState` con `error`
  consumible y `pendientesSinSubir`; `VistaCamara` con `tomarFoto` y `onFallaDeCaptura`.
- `ui/components/`: háptica + `MediaActionSound` (en `AppCustodioApplication`/util), `TicDeEncolado`
  y `AvisoSubidaFlotante` (U3), `DialogoError` con `textoAccion`/`onAccion` (U7), `DialogoDescarte`
  con chips + `SeleccionDeMotivo`/`descarteConfirmable` puros (U12), `ContenedorConAviso` +
  `franjaSinConexionVisible` (U9), `ChipEstadoSubida` en la rejilla (U5), `ChipCategoria` reutilizada
  en el descarte.
- `ui/pendientes/`, `ui/historial/`, `ui/ajustes/`: `PendientesViewModel` con `COUNT`, recarga y
  reintento con señal; Historial con `when` ordenado y `onReintentar` cableado; Ajustes con retry del
  cierre de sesión.
- `ui/viaje/`: `ReporteScreen` con estado de carga y encabezado "Faltan por enviar" (sin fila
  duplicada), `CierreScreen` con `rememberSaveable` y franja offline, `ArchivosScreen` con chip de
  enviado y vacío accionable, `AudioScreen` con texto informativo.
- `res/values/strings.xml`: sweeping de glosario U14/P3; keyes muertas eliminadas
  (`reporte_evidencias`, `reporte_pendientes`, `reporte_cerrar_viaje`); motivos de descarte;
  plurals `reporte_faltan_subir`; "Enviando"/"En cola" para los estados.
- Tests: nuevos unit de lógica pura `DescarteMotivosTest` (4) y `AvisoSinConexionTest` (2);
  `RemotoEmuladorTest` cablea idempotente los emuladores (dispositivo, PENDIENTE-HW).

**Archivos modificados:** 36 archivos entre `app/src/main/java`, `app/src/test/`,
`app/src/androidTest/` y `res/values/strings.xml`, en 7 commits (`14ffc86`…`75c58a6`).

## 2026:09:24 11:20 — Permisos de captura: todos a la vez tras el primer inicio de sesión

**Resumen:** los permisos de cámara, micrófono y ubicación (fina + aproximada) ya no se piden
al abrir el visor de captura: se solicitan **todos juntos en un solo diálogo** justo después del
primer inicio de sesión (bandera persistente en `preferencias`, se pregunta una sola vez por
instalación). La pantalla de cámara conserva su re-petición manual y el acceso a los Ajustes del
sistema para lo que quede denegado.

**Cambios:**
- `ui/navigation/PermisosTrasLogin.kt` (nuevo): `SolicitarPermisosTrasInicioDeSesion` lanza
  `RequestMultiplePermissions` con los 4 permisos cuando la sesión pasa a `CON_SESION` y aún no
  se habían pedido.
- `ui/navigation/AppNavHost.kt`: se invoca el solicitador anclado a `estadoRoot.estado`.
- `ui/viaje/CapturaEvidenciaScreen.kt`: se elimina el lanzado automático de permisos al componer
  la pantalla; la re-petición queda solo bajo el botón "Permitir cámara".

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/PermisosTrasLogin.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`

## 2026:09:24 10:40 — Cliente fijo del piloto y plan detallado del Grupo 3

**Resumen:** el cliente provisional pasa a ser **Colgate** (constante `CLIENTE_PROVISIONAL` en
`CapturaViewModel`; sigue siendo un valor único hasta que exista el catálogo de clientes, R4) y
se incorpora `docs/PLAN_GRUPO_3.md`, el plan en modo TDD de la tanda §17.1 de `docs/PLAN.md`.
Se elimina `docs/PHONE_AUTH_PLAN.md`, absorbido por `docs/PLAN.md` y `AGENTS.md`.

**Cambios:**
- `ui/captura/CapturaViewModel.kt`: `CLIENTE_PROVISIONAL` cambia de `"cliente-provisional"` a `"Colgate"`.
- `docs/PLAN_GRUPO_3.md` (nuevo): decisiones P1–P3 y plan de trabajo del Grupo 3.
- `docs/PHONE_AUTH_PLAN.md` (eliminado): su contenido quedó integrado en `docs/PLAN.md` y `AGENTS.md`.

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `docs/PLAN_GRUPO_3.md` (nuevo)
- `docs/PHONE_AUTH_PLAN.md` (eliminado)

## 2026:09:24 — Hoja de etiquetado: botón Guardar estable y copia local del catálogo (A4)

**Resumen:** dos mejoras en la `HojaEtiqueta` pedidas en el Grupo 3. (1) El botón de acción
siempre dice **"Guardar"** y está presente desde que abre la hoja, gris y deshabilitado hasta
elegir una Situación (antes cambiaba de etiqueta según el estado y el custodio "esperaba a que
apareciera"). (2) El catálogo de etiquetas se **calienta al abrir la app** y se **copia en local**
(`data/local/CatalogoEtiquetasCodificador` + `CatalogoEtiquetasOffline` por SharedPreferences);
el `EtiquetaRepository` pasa a ser `CacheEtiquetasRepositorio` (memoria → copia local → remoto,
un solo refresco de fondo por proceso y llamadas concurrentes coalescidas con `Mutex`). Como el
`get()` de Firestore con red espera al servidor, esto elimina el round-trip de los pocos segundos
en que la hoja abría sin chips en la primera captura.

**Cambios:**
- `HojaEtiqueta.kt`: botón único "Guardar" deshabilitado hasta tener Situación; se quitó la cadena
  `hoja_selecciona_etiqueta`.
- `data/local/CatalogoEtiquetasCodificador.kt` (nuevo): codificación compacta y sin dependencias
  de `List<Etiqueta>` con escapes.
- `data/local/CatalogoEtiquetasOffline.kt` (nuevo): interfaz `CatalogoEtiquetasAlmacen` +
  implementación SharedPreferences; un guardado corrupto se limpia y cae al remoto.
- `data/repository/CacheEtiquetasRepositorio.kt` (nuevo): decorador con caché en memoria y copia
  local; los fallos de prefs nunca rompen el etiquetado ni la subida.
- `di/DatabaseModule.kt`: el `EtiquetaRepository` envuelve el repo Firestore con la caché.
- `AppCustodioApplication.kt`: calentamiento del catálogo en `onCreate` en un scope de fondo.
- Pruebas unitarias: `CatalogoEtiquetasCodificadorTest` y `CacheEtiquetasRepositorioTest`.

**Verificación:** `./gradlew assembleDebug testDebugUnitTest` verde. Sin cambios de scripts de
build (no requirió Sync).

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/ui/captura/HojaEtiqueta.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/CatalogoEtiquetasCodificador.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/local/CatalogoEtiquetasOffline.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/repository/CacheEtiquetasRepositorio.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/DatabaseModule.kt`
- `app/src/main/java/com/ai27/app/custodio/AppCustodioApplication.kt`
- `app/src/test/java/com/ai27/app/custodio/data/local/CatalogoEtiquetasCodificadorTest.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/data/repository/CacheEtiquetasRepositorioTest.kt` (nuevo)
- `app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

## 2026:09:23 — Grupo 3 (usabilidad en campo): plan de tanda en modo TDD

**Resumen:** se creó el documento de plan `docs/PLAN_GRUPO_3.md` que detalla en modo TDD la
siguiente tanda de `docs/PLAN.md` §17.1 (Grupo 3 — que la app sea usable en campo). Recoge las
tres decisiones de producto acordadas con el usuario (U1/ráfaga diferida a versión futura;
motivos de descarte en chips; glosario canónico "Reporte"/"Evidencias"), confirma los hallazgos
R1/R2/R3 y U2–U15 contra el código actual, y los organiza en seis clústeres (G3-A…G3-F) con
dependencias, pruebas y riesgos. No se tocó código de la app todavía.

**Cambios:**
- Se agregó `docs/PLAN_GRUPO_3.md` con el detalle de la tanda del Grupo 3.
- Se registró esta entrada en el CHANGELOG.

**Archivos modificados:**
- `docs/PLAN_GRUPO_3.md`
- `CHANGELOG.md`

## 2026:09:23 17:11 — Infraestructura Firebase movida a `pf-infra-firebase`

**Resumen:** la carpeta `firebase/` (reglas de Firestore/Storage, arnés `reglas.test.js`, funciones,
seed y provisioning) dejó de vivir en este repo y pasó a su propio hogar, el repo nuevo
`pf-infra-firebase`. Motivo: el panel web analista ahora es consumidor real de las reglas (lectura
del analista en `config/analistas`), y con dos clientes el perímetro compartido no puede vivir dentro
de ninguno. La app consume las reglas/seed desde `../pf-infra-firebase`.

**Cambios:**
- Movidos a `pf-infra-firebase/`: `firebase.json`, `firestore.rules`, `storage.rules`,
  `package.json`/`package-lock.json`, `functions/`, `provision/` (provisionar.js + telefonos.csv),
  `seed/` y `test/reglas.test.js`.
- `AGENTS.md`: el comando de reglas pasa de `cd firebase && npm test` a
  `cd ../pf-infra-firebase && npm test`; seed, funciones y provisioning se referencian al repo nuevo.
- `README.md`: comandos de `npm test`, `emulators:start`, deploy de reglas y provisioning apuntan a
  `../pf-infra-firebase`.
- `.gitignore`: se quitaron las entradas `firebase/node_modules/` y `firebase/functions/node_modules/`
  (el folder ya no existe).

**Archivos modificados:**
- `AGENTS.md`
- `README.md`
- `.gitignore`
- eliminados todos los archivos bajo `firebase/` (rastreados por el repo).

## 2026:09:23 — A4: catálogo remoto de etiquetas (PLAN §3.1) — entregado

**Resumen:** el catálogo de etiquetas dejó de ser código y pasó a ser **dato**: la app lee
`catalogos/etiquetas` en Firestore (`data/remote/FirestoreCatalogoEtiquetasRepositorio`), lo cachea
con la persistencia de Firestore y usa el set curado empaquetado (`CATALOGO_ETIQUETAS`) solo de
**respaldo offline** (doc ausente, malformado o vacío — la trampa sin salida del catálogo vacío de
AUDIT §11 se rechaza en `valoresDelCatalogo`). Cambiar los valores reales no requiere recompilar.
La **Situación de subida pasó a ser dinámica**: qué ids cuentan lo decide el catálogo activo en
`RoomMedioRepository.etiquetar` (columna `tieneSituacion`) y en el guard de `SubidaMedioProceso`;
el set fijo queda solo en la migración de Room (`Migration(1,2)`) y en `MedioMappers` (históricos:
no deben seguir al catálogo dinámico). Alcance B aprobado por el usuario; contrato `valores` con
`categoria` en `docs/PLAN.md` §5.1.

**Cambios:**

- `app/.../data/remote/CatalogoEtiquetasDocumento.kt` (nuevo): `valoresDelCatalogo`, parser puro con
  fallback `null` (lista vacía o entrada rota → respaldo empaquetado); admite `orden` `Int`/`Long`.
- `app/.../data/remote/FirestoreCatalogoEtiquetasRepositorio.kt` (nuevo): lee `catalogos/etiquetas`,
  respaldo `CATALOGO_ETIQUETAS` por defecto.
- `app/.../data/repository/RoomMedioRepository.kt`: `etiquetar` calcula `tieneSituacion` con el
  catálogo activo (nuevo parámetro `EtiquetaRepository`).
- `app/.../data/pipeline/SubidaMedioProceso.kt`: guard de Situación con el catálogo activo (nuevo
  parámetro `EtiquetaRepository`).
- `app/.../di/DatabaseModule.kt`: `proveeEtiquetaRepository(FirebaseFirestore)` → firestore;
  `proveeMedioRepository` inyecta el repo de etiquetas.
- `app/.../di/PipelineModule.kt`: `proveeSubidaMedioProceso` cablea `EtiquetaRepository`.
- Tests: `CatalogoEtiquetasDocumentoTest` (nuevo, 6 casos), Situación dinámica en
  `RoomMedioRepositoryTest` y `SubidaMedioProcesoTest`; `FakeEtiquetaRepository` ahora con catálogo
  configurable; constructores actualizados en `ContratoRoomTest`, `RoomReporteRepositoryTest`,
  `SubidaMedioProcesoRoomTest`, y los androidTest `WorkerSubidaMedioTest`/`ReconciliacionColaTest`
  (que no ven el Fake de unit tests; `EtiquetasFijas` nuevo en el source set de instrumentación).
- Seed idempotente del catálogo en `firebase/provision/provisionar.js` (11 entradas, las mismas del
  respaldo embebido).
- `firebase/test/reglas.test.js`: fixture `catalogos/c1 {etiquetas:[...]}` → `catalogos/etiquetas
  {valores:[...categoria...]}` (reglas no validan contenido, solo lectura autenticada).
- Docs: `docs/PLAN.md` §3.1/§5.1/§9-A4, `README.md` sección A4.

**Pendiente único:** sustituir los valores provisionales por los reales derivados del tráfico del
grupo de WhatsApp (R4). Verificación: `testDebugUnitTest` y `assembleDebug` verdes;
`compileDebugAndroidTestKotlin` verdes.

## 2026:09:23 17:10 — A2: `beforeCreate` desplegado pero INERTE (decisión GCIP)

**Resumen:** se desplegó la Cloud Function `beforeCreate` de `firebase/functions`
(`bloquearTelefonoNoAutorizado`, trigger `providers/cloud.auth/eventTypes/user.beforeCreate`,
us-central1, nodejs20) contra `custodio-906e7268`. Al intentar registrarla como blocking function
(`PATCH .../admin/v2/projects/{project}/config?updateMask=blockingFunctions.triggers.beforeCreate`)
la Identity Platform API respondió **`OPERATION_NOT_ALLOWED: Blocking Functions may only be
configured for GCIP projects`**: el proyecto está en `subtype: FIREBASE_AUTH` (Auth básico). Habilitar
blocking functions exige migrar a **Identity Platform (GCIP)**, cambio **irreversible**, diferido como
**decisión de producto** (el candado real sigue siendo la allowlist post-OTP en la app + las reglas
Firestore/Storage). La función queda desplegada pero **no registrada** así que Auth nunca la invoca;
registrarla tras el upgrade es un solo PATCH con su `functionUri`.

**Cambios:**

- `firebase/firebase.json`: bloque `functions.source: functions` (para `firebase deploy --only
  functions`).
- `firebase/functions/index.js`: cabecera ampliada con el requisito GCIP y el PATCH de registro.
- `.gitignore`: `firebase/functions/node_modules/` agregado.
- `docs/PLAN.md` §9: carril A con estado real (A1 ✅, A2 provisión ✅ + beforeCreate ⚠️ inerte,
  A3 ✅, A4 pendiente); corregida la línea stale "consola no configurada".
- `AGENTS.md`: nota del `beforeCreate` inerte y del requisito GCIP.

## 2026:09:23 16:00 — A2: provisioning de Producción (cuentas + allowlist)

**Resumen:** se corrió `firebase/provision/provisionar.js` contra `custodio-906e7268` con la service
account (roles Firebase Auth Admin + Cloud Datastore User): creó las **35 cuentas de Authentication**
(de los 35 E.164 de `telefonos.csv`, que ahora incluye `+525563322805`) y reescribió
`config/telefonos` con la allowlist completa (verificado por REST: 35, incluye `+525563322805`).

**Cambios:**

- `firebase/package.json` / `package-lock.json`: nueva dependencia `firebase-admin` **anclada a
  `^12.7.0`**. La v14 (última de esa fecha) **rompe la API clásica** (`admin.firestore`/`admin.auth`
  no son funciones); con v12 el script funciona tal cual.
- `firebase/provision/telefonos.csv`: `+525563322805` agregado a la lista.
- `AGENTS.md`: marcado el provisioning de A2 (sigue pendiente `config/analistas`).

## 2026:09:23 15:41 — A3: reglas endurecidas publicadas en Producción

**Resumen:** las reglas de §5.3/§16 (`G2: Privacidad y reglas S3–S14`) se desplegaron a la consola
real `custodio-906e7268` el 2026-09-23 desde `firebase/firestore.rules` y `firebase/storage.rules`.
Firestore: `firebase deploy --only firestore:rules --project custodio-906e7268`. Storage: antes hubo
que inicializar el bucket en la consola ("Get Started" en Storage; el bucket del SDK es
`custodio-906e7268.firebasestorage.app`), y `--only storage:rules` falló por un bug del CLI
(`Could not find rules for the following storage targets: rules`; el target con `.firebaserc`
desencadenó `TypeError: _a.match is not a function`), por lo que se publicó con **`--only
storage`**, que compila y libera `storage.rules` correctamente.

**Cambios:**

- `firestore.rules` publicadas en `cloud.firestore` de `custodio-906e7268`.
- `storage.rules` publicadas en `firebase.storage` de `custodio-906e7268` (create-only + acotadas).
- Documentos `config/telefonos` y `config/analistas` verificados: **no existen aún en Producción**
  (404 en ambos). Sin ellos el login (allowlist tras el OTP) y la lectura del analista quedan
  denegados: es el pendiente A2 de provisión (`firebase/provision/provisionar.js`) y de la "Alta de
  analistas" (README).

## 2026:09:23 14:13 — G2: tanda de Privacidad y reglas (S3–S14) cerrada

**Resumen:** cierre del Grupo 2 de la auditoría (tanda §16 de `docs/PLAN.md`): reglas de
Firestore/Storage endurecidas y validadas contra el Emulator Suite y el teléfono, permisos de
ubicación/micrófono completos, y `ModoEmulador` atado a `BuildConfig.DEBUG`. Se desplegó en el
emulador y en dispositivo; el despliegue a la consola real queda como tarea manual del carril A
(no se hizo). El audio no subía (los `.m4a` llegaban a Storage pero el documento de `medios` se
rechazaba porque `thumbnailPath` nulo no estaba tolerado); quedó corregido con regresión en el
arné y en el dispositivo.

**Cambios:**

- S3/S4 (Storage): lista blanca de `contentType` y tope de tamaño (`FOTO` ≤ 12 MB `image/jpeg`,
  `AUDIO` ≤ 25 MB `audio/mp4`) acoplados al `{tipo}` de la ruta; `update`/`delete` negados,
  reintento idempotente contra ausencia del objeto (`resource == null`).
- S5/S6/S7 (Firestore): `hasOnly()` con las llaves exactas, tipos por campo, enums, rango de
  `mediosEsperados`, `storagePath`/`thumbnailPath` anclados a `medios/{reporteId}/`,
  `contadoresValidos` (monotonía + cota + `subida` enum + `completo` derivado) y `soloCambia`
  por transición; campos nullable aceptados como null de verdad (incluido `thumbnailPath`).
- S8 (ubicación): `AccessCoarseLocation` pedido y consultado; `ProveedorUbicacion` degrada a
  `PRIORITY_BALANCED_POWER_ACCURACY` con solo COARSE.
- S9/S10 (permisos): rationale + botón "Abrir ajustes del sistema"
  (`ui/components/AjustesDelSistema.kt`), re-chequeo de permisos en `ON_RESUME` (cámara y
  audio), petición de `RECORD_AUDIO` desde `AudioScreen`. Strings nuevos
  `captura_abrir_ajustes`, `audio_sin_microfono`, `audio_permitir_microfono`.
- S12: `ModoEmulador.activo = BuildConfig.DEBUG && meta-data modo_emulador` (`buildConfig =
  true`).
- Arné de reglas endurecido: 48 → **89 casos** (nuevo caso de AUDIO con `thumbnailPath` null);
  suite de dispositivo 32 → **33** (`audio_graba_subida_y_documento_sin_miniatura`).
- S11 → Producto (retención) y S13 → Grupo 6 (contexto de Crashlytics con T7): reclasificados
  en `AUDIT.md`, no arreglados aquí.

**Archivos modificados:**

- `firebase/firestore.rules`
- `firebase/storage.rules`
- `firebase/test/reglas.test.js`
- `app/src/androidTest/java/com/ai27/app/custodio/data/remote/RemotoEmuladorTest.kt`
- `app/src/main/java/com/ai27/app/custodio/data/ubicacion/ProveedorUbicacion.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/AudioScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/components/AjustesDelSistema.kt`
- `app/src/main/java/com/ai27/app/custodio/di/ModoEmulador.kt`
- `app/build.gradle.kts`
- `app/src/main/res/values/strings.xml`
- `docs/PLAN.md`
- `docs/AUDIT.md`
- `CHANGELOG.md`

## 2026:09:23 12:15

**Resumen:** Se reconció `docs/PLAN.md` con la migración PHONE_AUTH (identidad por teléfono verificado por SMS), que estaba desactualizada en las secciones de autenticación y contrato de datos frente al código y al README. Solo documentación; no se tocó código.

**Cambios:**

- §2 (Decisiones bloqueadas): la fila "Auth" pasa de "correo+contraseña" a teléfono verificado por SMS, login de dos fases número→OTP con allowlist `config/telefonos`.
- §2 "Lo que cambió": nuevo bullet que registra la migración de identidad de correo a teléfono (PHONE_AUTH, 2026-09-23), aclarando que el único correo que sobrevive es el del analista.
- §5.1 (contrato Firestore): `usuarios/{uid}` con `telefono`; `reportes.custodioCorreo` → `custodioTelefono`; documentado el doc `config/telefonos` (allowlist).
- §5.3 (reglas): añadida la regla de lectura de `config/telefonos` y aclarado que el acceso del dashboard es por correo del analista en `config/analistas.correos`, eje aparte de la identidad del custodio.
- §5.4: renombrada a "Identidad, autenticación e idempotencia" con la descripción del flujo OTP + allowlist + `beforeCreate`.
- §9 (carril A/B): A1 phone sign-in + Blaze; A2 aprovisionamiento Admin SDK + allowlist + `beforeCreate`; A3 con `config/telefonos`/`config/analistas`; B4 reescrita al login de dos fases por SMS.
- §10 (trazabilidad): fila nueva de identidad por teléfono que supersede la fila de Auth de §2.
- §11.6 (checklist C4): "correo" → "teléfono del custodio" en la descripción de la pantalla de Ajustes.

**Archivos modificados:**

- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:23 10:40 — PHONE_AUTH_HOTFIX: crash en "Reenviar código"

**Resumen:** Prueba física; al tocar "Reenviar código" la app reventaba con
`IllegalArgumentException: The given phoneNumber is empty` en `main`. El builder de
`PhoneAuthOptions` exige `setPhoneNumber` SIEMPRE (también en el reenvío), y `reenviarCodigo`
solo agregaba el `ForceResendingToken`.

**Cambios:**

- `FirebaseAuthSesionRepositorio`: recuerda `ultimoTelefono` del último `enviarCodigo`;
  `reenviarCodigo` lo reintroduce en el builder junto al token de reenvío.
- `flujoVerificacion`: `build()` + `verifyPhoneNumber` protegidos en `try/catch` — cualquier
  opción inválida se reporta como `EventoVerificacion.Fallo` en vez de crashear el hilo main.
- Nota de campo: el emulador NO entrega SMS reales; el código se lee en la Emulator UI
  (`http://127.0.0.1:4000/auth` → "Phone verification codes") o vale cualquier código de 6
  dígitos. El SMS real queda para el carril Blaze (provisionar + beforeCreate).

## 2026:09:23 10:30 — PHONE_AUTH_HOTFIX: gate de sesión exige teléfono

**Resumen:** Prueba física (OPPO CPH2769, Android 16) contra el Emulator Suite. La app fue
directo a `inicio` con una sesión restaurada SIN teléfono: `installDebug` sobre la versión
anterior conservó la cuenta de correo+contraseña heredada, que Firebase restaura al arrancar,
y el gate la trataba como sesión válida (Ajustes mostró "Sesión de custodio" en blanco).

**Cambios:**

- `RootViewModel`: `Custodio` sin `telefono` (o `null`) → `NO_SESION`. Tras PHONE_AUTH la
  identidad es el teléfono; una sesión de correo heredada ya no entra al `inicio` y el login
  por OTP la reemplaza al autenticar.
- Seed del emulador: export real de Firestore (`seed/firestore_export` + metadata) con el doc
  `config/telefonos` = allowlist completa (34 del CSV + `+525544880512` demo; incluye
  `+525548162705`). El emulador ya NO arranca con la allowlist vacía.
- `telefonos.csv`: alta de `+525548162705` (34 teléfonos).

## 2026:09:23 10:00 — PHONE_AUTH_TASK_8

**Resumen:** Aprovisionamiento de custodios con Admin SDK y el candado de producción `beforeCreate`.

**Cambios:**

- `firebase/provision/telefonos.csv`: los 33 teléfonos E.164 (+52 + 10 dígitos).
- `firebase/provision/provisionar.js`: crea la cuenta de Authentication de cada teléfono (idempotente; único camino de alta de phone providers sin Identity Platform) y escribe `config/telefonos.telefonos` (la allowlist).
- `firebase/functions/` (`package.json` + `index.js`): `beforeUserCreated` que autoriza solo teléfonos de la allowlist. **Solo se despliega con el plan Blaze** (documentado en el header y en README).
- `README.md` "Alta y baja de custodios (teléfono)" + prerrequisito A1 (phone sign-in / Blaze).

## 2026:09:23 09:55 — PHONE_AUTH_TASK_7

**Resumen:** Emulador listo para el flujo telefónico + cableado final + documentación viva.

**Cambios:**

- `firebase/seed/auth_export/accounts.json`: el usuario de seed pasa de correo+contraseña a **teléfono `+525544880512`** (provider `phone`), el mismo que usa `DatosDemo` y la app en el emulador.
- `strings.xml`: reescrita la sección Login (teléfono, código, reenviar) y CerrarSesión muestra `cierre_sesion_telefono`.
- `AGENTS.md`: actualizado con el estado PHONE_AUTH (identidad por teléfono, allowlist, beforeCreate, provisioning).

## 2026:09:23 09:50 — PHONE_AUTH_TASK_6

**Resumen:** Ajustes y diálogo de cierre de sesión muestran el teléfono del custodio, no el correo.

**Cambios:**

- `AjustesScreen`: `estado.custodio?.telefono` en el resumen de sesión y en `DialogoCerrarSesion`.
- `Dialogos.kt`: parámetro `correo` → `telefono`; string `cierre_sesion_correo` → `cierre_sesion_telefono`; previews con `DatosDemo.custodio.telefono`.

## 2026:09:23 09:45 — PHONE_AUTH_TASK_5

**Resumen:** Login de dos fases por SMS: número → OTP, con cuenta regresiva de reenvío y verificación automática al completar 6 dígitos.

**Cambios:**

- `LoginUiState`: `FaseLogin` (INGRESANDO_NUMERO/ESPERANDO_CODIGO), `telefonoEscrito`, `codigoEscrito`, `enviandoSms`, `verificando`, `segundosParaReenviar`.
- `LoginViewModel`: `enviarCodigo(activity?, telefono)`/`reenviarCodigo` vía `EventoVerificacion` (el `verificationId`/`resendToken` solo viven en el ViewModel), `verificarCodigoTecleado`, normalización de teléfono (10 dígitos → `+52…`, 12 con prefijo), countdown 60 s y la validación de allowlist tras autenticar.
- `LoginScreen`: dos fases con campo de teléfono / código; `onVerificar` cableado al botón.

## 2026:09:23 09:40 — PHONE_AUTH_TASK_4

**Resumen:** Allowlist de teléfonos del lado de la app (opción B del plan): se consulta DESPUÉS del OTP.

**Cambios:**

- `AllowlistRepositorio.contiene(telefono)` + `FirestoreAllowlistRepositorio` leyendo `config/telefonos.telefonos` (sin documento → nadie autorizado, cierre seguro).
- `PipelineModule.proveeAllowlistRepositorio`.
- `firestore.rules`: `config/telefonos` con `allow read: if autenticado()` (excepción de `config/*` que es ilegible/ilegible+noescribible).
- `reglas.test.js`: custodio lee `config/telefonos`, sin-auth no, nadie la modifica; regresión de `custodioTelefono` obligatorio en el contrato del reporte.

## 2026:09:23 09:35 — PHONE_AUTH_TASK_3

**Resumen:** Firebase Phone Auth en `SesionRepositorio`: OTP por SMS con evento único de verificación.

**Cambios:**

- `EventoVerificacion` (sealed): `CodigoEnviado(verificationId, resendToken opaco Long)` / `VerificacionAutomatica(custodio)` / `Fallo(error)`.
- `SesionRepositorio` reemplaza `iniciarSesion(correo, contraseña)` por `enviarCodigo(activity?, telefono)`/`reenviarCodigo(...)`/`verificarCodigo(verificationId, codigo)`; `Activity` es opcional solo para SMS Retriever/reCAPTCHA (nullable para pruebas JVM).
- `FirebaseAuthSesionRepositorio`: `PhoneAuthOptions` + `PhoneAuthProvider`, registro interno de `ForceResendingToken` → `Long`, `signInWithCredential`, `observarCustodio` lee `usuario.phoneNumber`.
- `DominioError` nuevo: `NumeroInvalido`, `CodigoInvalido`, `CodigoExpirado`, `DemasiadosIntentos`, `TelefonoNoAutorizado` (se retira `CredencialesInvalidas`); mapeo en `excepcionSesionAError` (por errorCode con respaldo de tipo) y strings nuevas.
- `FakeSesionRepositorio` con `alEnviarSms`; `AuthErrorMappingTest` reescrito; nuevo `LoginViewModelTest` (5 casos de Task 4/5).

## 2026:09:23 09:30 — PHONE_AUTH_TASK_2

**Resumen:** El contrato Firestore usa `custodioTelefono` (nada de `custodioCorreo`) y las pruebas de reglas lo cubren.

**Cambios:**

- `firebase/test/reglas.test.js`: fixtures `custodioCorreo` → `custodioTelefono`; seed de `config/telefonos` para Task 4.

## 2026:09:23 09:00 — PHONE_AUTH_TASK_1

**Resumen:** La identidad del custodio pasa de `correo` a `telefono` de punta a punta en el dominio y la persistencia local: `Custodio.correo`→`Custodio.telefono`, `Reporte.custodioCorreo`→`custodioTelefono`, columna Room `custodioCorreo`→`custodioTelefono` con la migración **3→4** no destructiva (`RENAME COLUMN`), y todos los mappers/use cases/previews/tests ajustados.

**Cambios:**

- **Modelo:** `Custodio(uid, correo)` → `Custodio(uid, telefono)`; `Reporte.custodioCorreo` → `Reporte.custodioTelefono`.
- **Room:** `ReporteEntity` columna `custodioCorreo` → `custodioTelefono` (`NOT NULL`); `AppCustodioDatabase.version` 3→**4** con `MIGRACION_3_4` que hace `ALTER TABLE reportes RENAME COLUMN custodioCorreo TO custodioTelefono` tras dropear el índice único parcial (patrón de `MIGRACION_2_3`; `onOpen` lo recrea). Se registró en `addMigrations`.
- **Mappers/use cases/previews:** `ReporteMappers` (ambas direcciones), `DocumentoMappers` (comentario de coordinación cross-repo con el panel), `CrearReporte` (`custodioTelefono = custodio.telefono`), `DatosDemo` con teléfono demo `+525544880512`.
- **Regresión nueva:** `MigracionCorreoATelefonoTest` siembra una base v3 con `custodioCorreo` y un valor conocido; abre con la configuración de producción y verifica que el dato quedó en `custodioTelefono` y que la columna vieja desapareció.
- **Tests ajustados:** todos los `Custodio(cretificacion)` con `correo`→`telefono` y los `custodioCorreo = ...` de fixtures (unitarias y androidTest), salvo `MigracionIndiceActivoTest` que siembra el DDL v1 REAL del dispositivo (esa migración se propaga 1→2→3→4 renombrando al final).

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/domain/model/Custodio.kt`, `domain/model/Reporte.kt`, `domain/usecase/CrearReporte.kt`, `data/local/db/ReporteEntity.kt`, `data/local/db/AppCustodioDatabase.kt`, `data/mapper/ReporteMappers.kt`, `data/mapper/DocumentoMappers.kt`, `ui/previews/DatosDemo.kt`
- `app/src/test/java/com/ai27/app/custodio/data/local/db/MigracionCorreoATelefonoTest.kt` (nueva regresión)
- 15 archivos de prueba ajustados al nuevo campo, `CHANGELOG.md`

## 2026:09:23 00:00

**Resumen:** Se redactó el plan de migración del login de correo+contraseña a verificación real por teléfono (Firebase Phone Auth / OTP), con el número como identidad, allowlist app-side (opción B, verificada tras autenticar) y renombre de `custodioCorreo` a `custodioTelefono` de punta a punta. Solo documentación; aún no se toca código.

**Cambios:**

- Se creó `docs/PHONE_AUTH_PLAN.md` con el plan completo: requisitos confirmados, antecedentes verificados en el código, decisiones de diseño/trade-offs y un desglose de 8 tareas incrementales (dominio + migración Room 3→4, contrato Firestore, flujo Phone Auth en el repositorio, allowlist `config/telefonos` opción B, UI de login de dos fases, Ajustes/diálogo, seed/emulador/wiring, aprovisionamiento Admin SDK + `beforeCreate` para Blaze).
- Se documentaron los puntos de pausa (inyección de `Activity`, verificación de dependencias/Sync, CSV de números E.164) y las tareas del humano en consola Firebase (proveedor Teléfono, SHA-256, despliegue en Blaze).

**Archivos modificados:**

- `docs/PHONE_AUTH_PLAN.md` (creado)

## 2026:09:22 17:00

**Resumen:** Hallazgo de campo **U17** — la hoja de etiquetado "aparecía dos veces": se volvía a animar desde abajo en cuanto se elegía la primera etiqueta de Situación (y en cualquier recomposición con la hoja abierta). Arreglado con su regresión instrumentada (RED 706 px → GREEN 0 px en el teléfono CPH2769) y el catalogo de espreso subido a 3.7.0, necesario para correr cualquier test de UI en dispositivos Android 16+.

**Cambios:**

- **U17 (bug real de UI):** `rememberModalBottomSheetState` enruta `confirmValueChange` por `rememberSaveable`; la lambda re-creada en cada recomposición cambiaba de referencia y regeneraba el `SheetState` con valor inicial `Hidden`, relanzando el `LaunchedEffect(sheetState) { sheetState.show() }` de `ModalBottomSheet.kt:213`. El fix recuerda una lambda estable (`remember` + `rememberUpdatedState`) que lee `tieneSituacion` vivo; ya no hay re-show.
- **Regresión:** `HojaEtiquetaTest` (androidTest, `createComposeRule`) congela el reloj de Compose, elige la primera Situación y muestrea el borde de la hoja durante 8 fotogramas. Sin el fix el borde se desplazaba **706,28 px** (RED, `AssertionError`); con él, **0 px** (GREEN). Corrido en el teléfono real (CPH2769).
- **Infra de pruebas de UI:** `espresso-core` 3.5.1 → **3.7.0**. Espresso ≤ 3.6.1 refleja en la API oculta `InputManager.getInstance()`, eliminada en Android 16 (API 36); sin esto ningún `connectedDebugAndroidTest` arrancaba en AVD API 37 ni en el teléfono API 36 (`NoSuchMethodException`). Documentado en el catálogo.
- **Verificación:** `testDebugUnitTest` 174/0, `lintDebug` y `assembleDebug` en 0 errores; `connectedDebugAndroidTest` del caso amarrado en CPH2769.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/captura/HojaEtiqueta.kt` (fix del `confirmValueChange` estable)
- `app/src/androidTest/java/com/ai27/app/custodio/ui/captura/HojaEtiquetaTest.kt` (nueva regresión)
- `gradle/libs.versions.toml` (espressoCore 3.7.0 con motivo documentado)
- `docs/AUDIT.md` (U17), `CHANGELOG.md`

## 2026:09:22 16:10

**Resumen:** Cierre de los pendientes de dispositivo de §15 en el teléfono real (CPH2769): S1 (galería pública) y S2 (`bmgr backupnow`) verificados en Hardware, y hallazgo nuevo **C10** — la migración 1→2/2→3 de Room crasheaba al abrir cualquier base v1 real por el índice único parcial manual — arreglado con su regresión. El teléfono era una instalación v1 previa a §14; B2 (Android ≤11) sigue `PENDIENTE-HW`.

**Cambios:**

- **C10 (bug real de migración):** `MIGRACION_1_2` y `MIGRACION_2_3` empiezan con `DROP INDEX IF EXISTS ux_reportes_activo_por_custodio`; la validación post-migración de Room compara `indices` contra las entidades (vacío) y el índice manual —creado en `onOpen` por versiones anteriores, persistido en disco— la hacía fallar con `IllegalStateException: Migration didn't properly handle: reportes`. Tras la validación, `onOpen` (que corre después del migration+validation) recrea el índice, preservando el invariante de un solo reporte ACTIVO por custodio.
- **Regresión:** `MigracionIndiceActivoTest` (2 pruebas) siembra una base v1 real del dispositivo —`reportes` con el índice parcial + `medios` con `etiqueta`— y abre la base con la configuración de producción. Verificado RED sin el fix (misma excepción de dispositivo) y GREEN con él.
- **Verificación de dispositivo (CPH2769):** la instalación v1 migró a v3 en vivo (vía WAL, `user_version 1→3`) y la app abrió sin FATAL conservando el viaje ACTIVO `123ABC` y los reportes CERRADO. S1: la evidencia vive en `files/medios` y MediaStore público tiene **0 filas** de `com.ai27.app.custodio` (15683 imágenes + 1033 audios; el resto son de otras apps). S2: `bmgr backupnow com.ai27.app.custodio` → *"Backup is not allowed"* + *"Backup finished with result: Success"* (el transport confirma `allowBackup="false"`).

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/local/db/AppCustodioDatabase.kt` (drop del índice en las dos migraciones + docs del porqué)
- `app/src/test/java/com/ai27/app/custodio/data/local/db/MigracionIndiceActivoTest.kt` (nueva regresión)
- `docs/PLAN.md` (§15.1 y §15.5bis: S1/S2 cerrados, C10 documentado), `docs/AUDIT.md` (C10 + notas de dispositivo en S1/S2 y tabla de estado)

## 2026:09:22 15:40

**Resumen:** §15 ejecutado (Grupo 1 — integridad del dato, hallazgos C1–C6 de la auditoría): verificación de §12/§13 confirmada contra el código, checklist manual con lo que queda `PENDIENTE-DISPOSITIVO`/`PENDIENTE-HW`, y los seis hallazgos de consistencia atendidos con su red de regresión unitaria y de emulador. `assembleDebug` y `testDebugUnitTest` (171 pruebas, 26 suites) verdes; `connectedDebugAndroidTest` (31 pruebas en emulador Pixel 9a) verde.

**Cambios:**

- **C2/C6 (sha + cierre):** el `sha256` se calcula antes de construir el documento y se persiste en el remoto (create-only se conserva); el cierre es ahora un solo `UPDATE` atómico del DAO cuyos conteos (`COUNT(*)`, `SUM(subido)`, `completo = subidos >= esperados`) se derivan del estado de `medios` en el mismo statement, sin ventana leer/escribir. `ContratoFakesTest`/`CerrarReporteTest` cableados con `mediosConsultables` y contrato determinista nuevo: `cerrar cuenta los medios presentes al momento del UPDATE, no una lectura previa`.
- **C1+C3 (contadores):** `SubidaMedioProceso` propaga el `Failure` de `actualizarContadores` (retry si transitorio); `actualizarContadores` de Firestore es un merge monótono en transacción (`subida` solo avanza, `mediosSubidos` con `max`, `completo` no vuelve a `false`); la reconciliación se basa en divergencia real y un reporte `COMPLETA` remoto-distinto se auto-sana sin re-subir.
- **C4 (re-encolado):** `ExistingWorkPolicy.KEEP` en `encolarSubidaMedio` y `sincronizarReporte`; se eliminó el guard de `getWorkInfosForUniqueWork` (KEEP decide atómicamente); caracterización volteada en `ReconciliacionColaTest.C4_encolar_de_nuevo_NO_reemplaza_el_trabajo_en_vuelo`.
- **C5 (errores):** `DominioError` gana `ServidorIndisponible`/`PermisoDenegado`/`ArchivoLocalAusente`; `ErroresRemoto` clasifica por kind (`SinConexion`/`ServidorIndisponible` → reintento sin tope; permanente → `Result.failure()` inmediato); se persisten `ultimoError`+`intentosSubida` en `medios` (primera migración 2→3 para diagnóstico, suma `ALTER TABLE` de dos columnas) y la causa va a Crashlytics vía `RegistroDeFallos`. La clasificación JVM se prueba por código de error en string (`FirebaseFirestoreException` no se instancia fuera del device).
- **Regresión:** `DocumentoMappersTest` (sha), `ErroresRemotoTest`, `MigracionDiagnosticoSubidaTest` (2→3), `WorkerSubidaMedioTest` reescrito (transitorio sin tope, permanente inmediato + persistencia + Crashlytics), `RoomMedioRepositoryTest` (registrarFallo); `RemotoEmuladorTest` gana la marcha del sha y la prueba monotónica de contadores intercalados.
- **Emulador:** el Suite se levantó con `--project custodio-906e7268` (sin ello la regla de Storage falla por cross-project `get()`); requisito documentado en la cabecera de `RemotoEmuladorTest`.

**Archivos modificados:**

- `docs/PLAN.md` (§15.5bis resultado y estado de tareas), `docs/AUDIT.md` (C1–C6 → `✅ ATENDIDO 2026-09-22`), `CHANGELOG.md`
- `app/src/main/java/com/ai27/app/custodio/` dominio: `domain/error/DominioError.kt`, `domain/model/Medio.kt`, `domain/repository/MedioRepository.kt`, `domain/error/DominioErrorRecurso.kt`; datos: `data/local/db/ReporteDao.kt`, `AppCustodioDatabase.kt`, `MedioDao.kt`, `MedioEntity.kt`, `data/mapper/MedioMappers.kt`, `data/repository/RoomMedioRepository.kt`, `data/remote/FirebaseReporteRemoto.kt`, `data/remote/ErroresRemoto.kt`; pipeline/worker: `data/pipeline/SubidaMedioProceso.kt`, `data/worker/WorkerSubidaMedio.kt`, `WorkManagerSubidaScheduler.kt`, `CustodioWorkerFactory.kt`, `di/PipelineModule.kt`
- Tests: `app/src/test/java/com/ai27/app/custodio/` (`FakeMedioRepository.kt`, `FakeReporteRepository.kt`, `contrato/ContratoRepositoriosTest.kt`, `data/remote/ErroresRemotoTest.kt`, `data/local/db/MigracionDiagnosticoSubidaTest.kt`, `RoomMedioRepositoryTest.kt`); `app/src/androidTest/java/com/ai27/app/custodio/` (`data/worker/WorkerSubidaMedioTest.kt`, `data/worker/ReconciliacionColaTest.kt`, `data/remote/RemotoEmuladorTest.kt`)

## 2026:09:22 14:20

**Resumen:** Se agregaron las secciones §16 y §17 a `docs/PLAN.md` con el plan de las tandas restantes de la auditoría: §16 detalla el Grupo 2 (Privacidad y reglas, S3–S14) en modo TDD, y §17 deja secuenciados los Grupos 3–6 (uso en campo, rendimiento, accesibilidad, proceso) a nivel de tanda. Cambio solo de documentación.

**Cambios:**

- Nueva sección **§16** (Grupo 2): hallazgos S3–S14 confirmados contra el código (reglas de Firestore/Storage, permisos, `ModoEmulador`, Crashlytics), decisiones D1–D6, tabla de tareas G2-0…G2-9 con dependencias y pausas de Sync.
- Nueva sección **§17** (Grupos 3–6): metas, clústeres de tarea, criterios de cierre, mapa de dependencias entre tandas, solapes a explotar, y las decisiones de Producto que ninguna tanda toma.

**Archivos modificados:**

- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:22 13:56

**Resumen:** Se agregó la sección §15 a `docs/PLAN.md` con el plan de la tercera tanda sobre la auditoría: verificación exhaustiva de §12/§13, cierre de los pendientes de comprobación manual, y la tanda de **integridad del dato (Grupo 1: C1–C6)** en modo TDD, más un roadmap de una línea para los Grupos 2–6. Cambio solo de documentación; aún no se toca código.

**Cambios:**

- Nueva sección **§15** en `docs/PLAN.md`: estado verificado de §12/§13 contra el código (B1, B2, B3, S1, S2, E3/E6, E5 confirmados leyendo los archivos), los seis hallazgos del Grupo 1 confirmados presentes (C1 contador con `Result` descartado, C2 `sha256` nulo, C3 lost-update, C4 `REPLACE` cancela subidas, C5 error sin clasificar, C6 cierre no transaccional), alcance acordado, decisiones de diseño (D1–D5), tabla de tareas (T1/T2 + C0–C6), roadmap de los Grupos 2–6 y lista de fuera de alcance.
- El plan registra que hay dispositivo con Android > 11 y Emulator Suite, y que la rama del constructor viejo de B2 (Android ≤11) queda `PENDIENTE-HW`.

**Archivos modificados:**

- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:22 12:59

**Resumen:** §14 completa: las etiquetas provisionales de una sola opción (`Medio.etiqueta`) se reemplazan por un catálogo curado de dos categorías — **Situación** (obligatoria, 5 etiquetas) y **Evidencia** (opcional, 6 etiquetas) — y los medios aceptan varias, con la regla de dominio "≥1 etiqueta de Situación" validada en todos los puntos de entrada.

**Cambios:**

- **Dominio y catálogo**: `CategoriaEtiqueta` (SITUACION/EVIDENCIA), `Etiqueta.categoria`, `Medio.etiqueta` → `etiquetas: List<String>`, `DominioError.FaltaSituacion`, y el catálogo nuevo en `CatalogoEtiquetasRepository` (los ids viejos —`dano`, `devolucion`, `novedad`, `contraentrega`, `otro`— dejan de existir).
- **Room v2**: `MedioEntity.etiquetas` (con `EtiquetasConverter`) + columna denormalizada `tieneSituacion`; `etiquetar` devuelve la lista y actualiza el flag; `obtenerPendientes` (cola de subida y badge) filtra por `tieneSituacion = 1`. Primera migración de la app: `Migration(1,2)` convierte `etiqueta TEXT` en `etiquetas TEXT` (JSON de un elemento si venía valor o `[]`) y calcula `tieneSituacion` según el catálogo; los registros viejos pasan sin Situación y quedan pendientes de reclasificar.
- **Caso de uso**: `EtiquetarMedio` valida que todos los ids existan y estén activos (`EtiquetaInvalida`) y que el conjunto incluya al menos una Situación (`FaltaSituacion`).
- **Remoto**: `DocumentoMappers` escribe `"etiquetas": [..]`; `SubidaMedioProceso` no sube evidencia de un medio sin Situación.
- **UI**: `HojaEtiqueta` con dos grupos de chips multi-selección (marca "Situación (obligatoria)"), que reemplaza los chips de una sola etiqueta; `ArchivosScreen`, `CapturaEvidenciaScreen`, `AudioScreen` y `DetalleReporteScreen` muestran la lista (unidas con " · ", "Sin situación" si está vacía); `PendientesScreen` distingue "Falta situación" del detalle normal.
- **Pruebas**: contrato, `FakeMedioRepository`, worker, reconciliación, emulador y el caso de uso reescrito para la lista y el filtro de Situación; de paso se corrigió una divergencia que dejaba entrar en la cola a un medio solo con Evidencia (el fake vs. la query de Room, 🔁 alineados con `ETIQUETAS_SITUACION_IDS`). Nueva prueba de migración (`MigracionEtiquetasTest`) que aplica `MIGRACION_1_2` sobre una base v1 sembrada y verifica columnas y datos.

**Resultado:** 149 pruebas unitarias / 0 fallos (142 previas + 7 de esta tanda); `assembleDebug` y `lintDebug` en 0 errores (35 warnings todos preexistentes: bump de dependencias, orden de `Modifier` en componentes viejos, orientación fija, recursos sin uso).

**Archivos modificados:** dominio (`Etiqueta.kt`, `Medio.kt`, `DominioError.kt`), `data/local/db/` (`MedioEntity.kt`, `MedioDao.kt`, `AppCustodioDatabase.kt` en versión 2, `EtiquetasConverter.kt`), `data/repository/{CatalogoEtiquetasRepository.kt, RoomMedioRepository.kt}`, `data/mapper/{MedioMappers.kt, DocumentoMappers.kt}`, `data/usecase/{EtiquetarMedio.kt, SubidaMedioProceso.kt}`, `ui/captura/{HojaEtiqueta.kt, CapturaViewModel.kt, CapturaUiState.kt}`, `ui/viaje/{ArchivosScreen.kt, CapturaEvidenciaScreen.kt, AudioScreen.kt}`, `ui/historial/DetalleReporte*.kt`, `ui/pendientes/PendientesScreen.kt`, `ui/error/DominioErrorRecurso.kt`, `ui/previews/DatosDemo.kt`, `res/values/strings.xml`, pruebas unitarias e instrumentadas citadas, y `docs/PLAN.md` (§14 cerrada).

## 2026:09:22 11:37

**Resumen:** Se ejecutó la verificación instrumentada de §13 que había quedado pendiente: el teléfono se reconectó y `connectedDebugAndroidTest` corrió completo contra el Emulator Suite. Sin cambios de código.

**Resultado:** 30 pruebas / 0 fallos en CPH2769 (API 36), incluidas las 7 nuevas de esta tanda — las 5 de `GrabadoraMediaRecorderTest` (barrido de notas huérfanas, estado inicial, pausar/reanudar sin grabación) y las 2 de cancelación en `ReconciliacionColaTest` (`cancelarTodo_cancela_los_trabajos_de_subida_encolados` y `reconciliar_rescata_lo_que_cancelo_el_cierre_de_sesion`). Las 23 previas de §12 siguen en verde.

Con esto, §13 queda verificada por completo: `assembleDebug`, `testDebugUnitTest` (142/0), `lintDebug` (0 errores) y ahora `connectedDebugAndroidTest` (30/0).

**Archivos modificados:** ninguno.

## 2026:09:22 09:43

**Resumen:** Cierre de la tanda de §13. Los seis hallazgos de pérdida de evidencia (E1–E6) más el E7 quedan marcados `✅ ATENDIDO` en `docs/AUDIT.md`, y §13 de `docs/PLAN.md` queda cerrada con su sección de resultado. Es la tarea T7.

**Por qué las marcas llevan nota.** Tres hallazgos se cerraron con un alcance distinto al que proponía el propio hallazgo, y dar por resuelto sin decirlo dejaría el documento mintiendo. Cada marca explica **cómo** quedó atendido y qué sigue abierto:

- **E3**: la lista de medios sin etiquetar se deriva del estado en lugar de consultarse aparte, y el badge **sigue contándolos** a propósito (un medio sin clasificar es trabajo sin terminar; lo que cambia es que ahora se resuelve con un toque en lugar de borrando). La queja de U6 sobre el badge sigue abierta.
- **E5**: la nota **se pausa**, no se graba en segundo plano — eso exigiría un servicio en primer plano. Y una nota interrumpida por muerte del proceso **sigue siendo irrecuperable**: `pause()` no escribe el índice del MPEG-4. Lo que cambia es que se avisa. El estado sigue duplicado entre ViewModel y singleton: queda como deuda, no como defecto activo.
- **E6**: **no se borran datos locales**, a diferencia de lo que sugería el hallazgo. El purgado pasa a Producto como decisión de retención, con el razonamiento completo anotado.

**Resultado de la tanda:** 114 → 142 pruebas unitarias, 23 → 30 instrumentadas, 0 errores de lint antes y después.

**Dos cosas que aparecieron al ejecutar y no estaban en el plan,** ambas registradas en §13.8:

1. Una divergencia de fakes que la suite de contrato de §12 no podía ver, porque comparaba lecturas puntuales y no emisiones de `Flow`. Ya hay prueba de contrato que la cubre.
2. Una query de DAO añadida en T3 que quedó sin uso al simplificarse T4; se retiró.

**Verificación pendiente, y queda dicho en el plan:** las siete pruebas instrumentadas nuevas (dos de cancelación de trabajo, cinco del barrido de notas) **no se han ejecutado en un equipo** — el teléfono se desconectó a media T5 y no volvió. Falta `./gradlew connectedDebugAndroidTest` con el dispositivo conectado y los túneles `adb reverse` puestos. Lo demás está verde: `assembleDebug`, `testDebugUnitTest` 142/0, `lintDebug` 0 errores, `compileDebugAndroidTestKotlin`.

**Archivos modificados:**

- `docs/AUDIT.md`
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:22 01:11

**Resumen:** Se corrigió el hallazgo E5: la nota de voz podía grabar silencio o audio corrupto sin que nadie lo notara, y el micrófono quedaba tomado al salir. Ahora la nota **se pausa sola** al pasar la app a segundo plano y se reanuda con el mismo botón, el grabador reporta sus fallos, el tope de 5 minutos lo impone él mismo, y una nota que se perdió por muerte del proceso **se avisa** en lugar de desaparecer callada. Es la tarea T6 del plan de §13, con las decisiones R2 y R3.

**Por qué la nota salía en silencio:** `Grabadora` no tenía `pausar`/`reanudar`, `GrabadoraMediaRecorder` no registraba `setOnErrorListener` ni `setOnInfoListener`, `AudioScreen` no observaba el ciclo de vida y `CapturaViewModel` no tenía `onCleared`. Al salir de la app el `MediaRecorder` seguía nominalmente grabando; cuando el sistema le quitaba el micrófono, el resultado era un `.m4a` en silencio o truncado que la app entregaba como evidencia válida. Y como `Grabadora` es `@Singleton`, el grabador quedaba tomado y la grabación siguiente fallaba sin explicación.

**Cambios en el contrato:**

- `data/local/media/Grabadora.kt`: `pausar()`, `reanudar()`, `pausada`, `barrerNotasHuerfanas()` y un `Flow<EventoGrabacion>` con `LimiteAlcanzado` y `Fallo(codigo)`. Las operaciones pasan a `suspend`: `prepare`, `start`, `stop` y la lectura del `.m4a` tocan disco, y en el hilo principal eso son ANR en un equipo de campo.
- `data/local/media/GrabadoraMediaRecorder.kt`: `pause()`/`resume()` reales, duración **por segmentos** (`acumuladoMs` + tramo en curso) para que el cronómetro no cuente el tiempo en segundo plano, `setMaxDuration(AUDIO_MAX_MS)` para que el tope lo imponga el grabador y no la corrutina del timer, los dos listeners cableados al `Flow`, y todo bajo `Dispatchers.IO`. El `SharedFlow` es `replay = 1` porque el grabador avisa desde su propio hilo y el evento puede llegar antes de que la UI se suscriba.

**Cambios en el ViewModel:**

- `pausarGrabacionDeAudio()` / `reanudarGrabacionDeAudio()`, ambas idempotentes: `ON_STOP` puede llegar más de una vez.
- El cronómetro se extrajo a `arrancarCronometro()` y ya **no** comprueba el tope: eso lo hace el grabador.
- Se recogen los eventos: `LimiteAlcanzado` guarda la nota (llegar al tope no es un fallo), `Fallo` la descarta, avisa al custodio y la manda a Crashlytics con el contexto `grabacion_audio`.
- `onCleared()` suelta el micrófono con un scope propio, porque `viewModelScope` ya está cancelado ahí.
- Al arrancar se barre la caché: si hay un `.m4a` suelto, se enciende `avisoNotaPerdida`.
- `detenerGrabacionDeAudio` ya no se calla cuando el grabador no devuelve audio usable (archivo vacío o duración cero). Era un `return` mudo: la nota se esfumaba y el custodio se iba creyendo que la había dejado grabada — el mismo defecto que E5 vino a cerrar, escondido dos líneas más abajo.

**Cambios en la UI:**

- `ui/viaje/AudioScreen.kt`: `DisposableEffect` con `LifecycleEventObserver` que pausa en `ON_STOP`. En pausa el cronómetro se atenúa, aparece `EN PAUSA` con la indicación de tocar para continuar, el botón redondo **reanuda**, y el guardado pasa a un botón propio para no tener un solo control con tres significados.
- `ui/captura/CapturaUiState.kt`: `audioPausado` y `avisoNotaPerdida`.
- `res/values/strings.xml`: `error_grabacion_no_reanudo`, `error_grabacion_fallo`, `audio_en_pausa`, `audio_reanudar_indicacion`, `audio_detener_y_guardar`, `audio_nota_perdida`.

**Sobre la nota que se pierde (R3).** Una nota **pausada** sí se puede guardar: `stop()` escribe el índice del MPEG-4 aunque venga de pausa. Una nota interrumpida por **muerte del proceso** no, porque `pause()` no lo escribe. Ese archivo suelto en caché sirve de marca: el barrido lo borra, cuenta cuántos había y la pantalla de audio lo dice. No hace falta persistir nada aparte.

**Pruebas nuevas:**

- `ui/captura/CapturaViewModelTest.kt`, diez pruebas: pausar conserva lo grabado y no descarta; reanudar continúa la misma nota; pausar dos veces no cuenta doble; una nota pausada se detiene y se guarda; un fallo del grabador la descarta, lo avisa y lo registra; el tope de 5 minutos **guarda** la nota; una huérfana en caché se avisa al arrancar y el aviso se consume; sin huérfanas no se avisa; si el grabador no puede reanudar se avisa y la nota sigue pausada sin perderse; y una nota que el grabador devuelve vacía se avisa en lugar de desaparecer.
- `res/values/strings.xml` suma `error_grabacion_vacia`.
- `androidTest/.../GrabadoraMediaRecorderTest.kt`, nuevo: el barrido borra y cuenta los `.m4a` de nota, no toca otros archivos de caché, devuelve 0 sin nada, el estado inicial es limpio, y pausar/reanudar sin grabación fallan limpio. No graba: eso exige micrófono y permiso en tiempo de ejecución.

**Detalle de las pruebas que conviene recordar:** con `runTest`, dejar una prueba terminada mientras el cronómetro sigue vivo cuelga la suite — su `delay` en bucle nunca acaba en tiempo virtual. Las pruebas que dejan la nota grabando la cierran al final a propósito.

**Limpieza:** se retiró `super.onCleared()` (lint `EmptySuperCall`: el de `ViewModel` está vacío) y el string `audio_reanudar`, que quedó sin uso porque el botón redondo ya cumple esa función.

**Verificación:** `testDebugUnitTest` 142 pruebas / 0 fallos; `lintDebug` 0 errores (36 avisos, todos preexistentes); `assembleDebug` y `compileDebugAndroidTestKotlin` verdes. **Pendiente:** las instrumentadas de T5 y T6 no se han ejecutado — el teléfono lleva desconectado desde media T5.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/local/media/Grabadora.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/media/GrabadoraMediaRecorder.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaUiState.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/AudioScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/ai27/app/custodio/CapturaFakes.kt`
- `app/src/test/java/com/ai27/app/custodio/ui/captura/CapturaViewModelTest.kt`
- `app/src/androidTest/java/com/ai27/app/custodio/data/local/media/GrabadoraMediaRecorderTest.kt`
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 18:37

**Resumen:** Se corrigió el hallazgo E6c y el E7: cerrar sesión era `auth.signOut()` a secas —dejaba workers corriendo con el token del custodio saliente— y el guard que impedía salir con archivos sin subir era código muerto. Es la tarea T5 del plan de §13, con la decisión **A** del usuario: cancelar el trabajo, **no** borrar datos locales.

**Por qué el guard estaba muerto:** `AjustesViewModel.solicitarCerrarSesion` leía `interno.value.pendientesSinSubir`, pero el conteo vive en el estado *combinado*; `interno` nunca lo recibe, así que valía 0 siempre. Lo único que bloqueaba de verdad era el botón deshabilitado en la UI. Importa porque ese guard es la invariante que impide dejar evidencia huérfana al rotar de custodio, y una sola defensa en la capa de presentación no basta.

**Por qué cancelar el trabajo:** en un equipo que rotan 37 personas, un worker que sigue subiendo con el token de A mientras B inicia sesión ve sus escrituras denegadas por las reglas y deja la fila en `FALLIDA` — invisible para B, porque su badge filtra por uid. No hay pérdida: lo que quede en `EN_PROGRESO` lo rescata `reconciliar()` cuando A vuelva a entrar, que es justo lo que ya afirmaba una prueba de §12.

**Cambios:**

- `data/worker/SubidaScheduler.kt`: nuevo `cancelarTodo()` y constante `TAG_SUBIDA`.
- `data/worker/WorkManagerSubidaScheduler.kt`: los dos tipos de trabajo se etiquetan con `TAG_SUBIDA` y `cancelarTodo()` usa `cancelAllWorkByTag`. Se prefiere la etiqueta a `cancelAllWork()` para no arrastrar trabajo ajeno que se añada al proyecto más adelante.
- `ui/ajustes/AjustesViewModel.kt`: el guard lee `uiState.value`; se revalida también **al confirmar**, porque el diálogo abre un hueco en el que pueden entrar medios nuevos desde otra pestaña; y `confirmarCerrarSesion` cancela el trabajo **antes** del `signOut`.
- `app/src/test/.../CapturaFakes.kt`: `FakeSubidaScheduler.cancelarTodo()` con contador, y `FakeSesionRepositorio.cerrarSesion()` deja de lanzar `error(...)` — ahora registra el cierre, apaga el flujo del custodio y admite fallo inyectado.

**Lo que NO se hizo, y por qué (decisión A del usuario).** El plan contemplaba purgar filas y archivos del uid saliente. Investigándolo resultó innecesario y riesgoso:

- El aislamiento entre custodios ya lo da el filtro por uid de T3; la app no vuelve a tocar evidencia ajena.
- `marcarSubidaCompleta` ya borra el archivo grande al subir, así que en disco solo quedan miniaturas de 320 px.
- Historial lee **solo** de Room y no hay código que reconstruya nada desde Firestore: borrar las filas haría desaparecer del dispositivo los 30 días de historial del custodio de forma irrecuperable *en la app*, también para él mismo al volver a entrar.
- Si el custodio saliente tenía un viaje `ACTIVO` con todo ya subido, el guard permite salir y el borrado se llevaría el viaje abierto — o sea, pérdida de evidencia, exactamente lo que esta tanda vino a eliminar.

Queda anotado para Producto como decisión de **retención de datos**, no como arreglo de bug: "borrar datos locales al rotar de custodio" necesita primero una forma de recuperar el historial desde el servidor.

**Pruebas nuevas** en `ui/ajustes/AjustesViewModelTest.kt`, que antes no existía (y por eso el guard llevaba tiempo muerto sin que nada lo notara):

- con archivos sin subir no se abre la confirmación de cierre — se comprobó que **falla** al revertir el guard a `interno`;
- sin pendientes sí se abre;
- cerrar sesión cancela el trabajo de subida y luego cierra la sesión;
- confirmar con pendientes que llegaron después del diálogo no cierra la sesión ni cancela nada.

En `androidTest/.../ReconciliacionColaTest.kt`, dos pruebas más contra WorkManager: `cancelarTodo` deja `CANCELLED` el trabajo del medio y del reporte, y `reconciliar` vuelve a poner trabajo vivo después de una cancelación.

**Verificación:** `testDebugUnitTest` 132 pruebas / 0 fallos; `lintDebug` 0 errores; `compileDebugAndroidTestKotlin` verde. **Pendiente:** las dos instrumentadas nuevas no se han ejecutado — el teléfono se desconectó a mitad de la tarea.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/worker/SubidaScheduler.kt`
- `app/src/main/java/com/ai27/app/custodio/data/worker/WorkManagerSubidaScheduler.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/ajustes/AjustesViewModel.kt`
- `app/src/test/java/com/ai27/app/custodio/CapturaFakes.kt`
- `app/src/test/java/com/ai27/app/custodio/ui/ajustes/AjustesViewModelTest.kt`
- `app/src/androidTest/java/com/ai27/app/custodio/data/worker/ReconciliacionColaTest.kt`
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 18:30

**Resumen:** Se corrigió el hallazgo E3b: la evidencia que quedaba **sin etiquetar** era un callejón sin salida. Ahora la rejilla de Archivos la señala, se etiqueta con un toque, y ninguna pantalla afirma que está "en cola" cuando no lo está. Es la tarea T4 del plan de §13.

**Por qué era un callejón sin salida:** `medioPendienteDeEtiquetar` vive solo en memoria. Si el custodio cierra la hoja con la X —o si el proceso muere con la hoja abierta— el medio queda persistido con `etiqueta = null` y la hoja no reabre nunca. Desde T3 la cola además lo excluye, así que sin esta tarea el medio se quedaba invisible, sin subir, y manteniendo el badge en ≥1 y el cierre de sesión bloqueado para siempre.

**Cambios:**

- `ui/captura/CapturaUiState.kt`: nueva propiedad derivada `mediosSinEtiquetar`. Se calcula filtrando `medios` en lugar de consultar Room aparte: la lista ya está en el estado y está acotada a 50.
- `ui/captura/CapturaViewModel.kt`: nuevo `retomarEtiquetado(medioId)`, que reabre la hoja de un medio ya guardado sin etiqueta. Ignora los que ya tienen etiqueta: reetiquetar no está en el alcance y la subida pudo haber salido ya.
- `ui/viaje/ArchivosScreen.kt`: las celdas sin etiqueta llevan borde terciario y la insignia `SIN ETIQUETA`, y son **tocables** para abrir la hoja; la pantalla ahora hospeda `HojaEtiqueta` (antes solo vivía en Captura y Audio, las dos pantallas transitorias). Arriba, una `FranjaAviso` dice cuántas faltan. Solo se hace clickeable la celda que lo necesita: una celda ya etiquetada no debe anunciarse a TalkBack como botón deshabilitado, así que se usa `Modifier.clickable` condicional en lugar del `Surface(onClick = …, enabled = …)`.
- `ui/viaje/ReporteScreen.kt`: el aviso también aparece en el hub del viaje. Enterarse solo al entrar a Archivos no sirve: nadie entra a buscar un problema que no sabe que tiene.
- `ui/pendientes/PendientesScreen.kt`: un medio sin etiqueta ya no dice "En cola" —era mentira desde T3— sino "Falta etiqueta". Se retiró el string `pendientes_en_cola`, que quedó sin uso.
- `ui/navigation/AppNavHost.kt`: se cablean los tres callbacks nuevos de `ArchivosScreen`.
- `res/values/strings.xml`: `archivos_sin_etiqueta`, `archivos_etiquetar_ahora`, `pendientes_falta_etiqueta` y el plural `archivos_aviso_sin_etiquetar`.

**Se retiró código de T3 que quedó sin uso:** `observarSinEtiquetaDelReporte` en `MedioDao`, `MedioRepository`, `RoomMedioRepository` y el fake. Se había añadido pensando en esta tarea, pero derivar la lista de `medios` resultó más simple y evita una segunda consulta. Una query de DAO sin llamadas es peor que ninguna.

**Divergencia de fakes encontrada al implementar esto:** `FakeMedioRepository.observarDelReporte` devolvía un `MutableStateFlow` recién creado con un snapshot, así que **nunca reemitía**, mientras el `Flow` de Room está vivo. Cualquier prueba que se suscribiera antes de la primera captura veía la rejilla vacía para siempre — y dos de las pruebas nuevas fallaron por eso, no por el código de producción. Ahora el fake publica un espejo observable y reemite en toda mutación, incluidas `etiquetar` y `actualizarGps`, que antes tampoco notificaban.

**Pruebas:**

- `contrato/ContratoRepositoriosTest.kt`: `observarDelReporte reemite cuando entra un medio nuevo`, ejecutada contra Room y contra el fake. Es el hueco que dejó la suite de §12: el contrato comparaba lecturas puntuales, no emisiones, así que esta clase de divergencia pasaba inadvertida. Se comprobó que la prueba falla al revertir el fake.
- `ui/captura/CapturaViewModelTest.kt`: tres pruebas nuevas — descartar el etiquetado deja el medio visible como sin etiquetar y no encola nada; retomar el etiquetado reabre la hoja, y al guardar la etiqueta la subida sí se encola; retomar sobre un medio ya etiquetado no hace nada.
- Previews nuevas `Sin etiquetar · Claro/Oscuro` en `ArchivosScreen`.

**Verificación:** `testDebugUnitTest` 128 pruebas / 0 fallos; `connectedDebugAndroidTest` 23 / 0 fallos en CPH2769 (API 36); `lintDebug` 0 errores.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaUiState.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/ArchivosScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/ReporteScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/pendientes/PendientesScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/db/MedioDao.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/MedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/contrato/ContratoRepositoriosTest.kt`
- `app/src/test/java/com/ai27/app/custodio/ui/captura/CapturaViewModelTest.kt`
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 18:18

**Resumen:** Se corrigieron los hallazgos E3a y E6a/E6b: la cola de subida ya no encola evidencia **sin etiquetar** (subía al servidor sin clasificar, inservible para el analista) ni evidencia **de otro custodio** (se reintentaba con el token equivocado y quedaba FALLIDA para siempre), y la reconciliación acota su barrido a los últimos 30 días. Es la tarea T3 del plan de §13.

**Por qué importaba:** `reconciliar()` encolaba todo lo que no estuviera `COMPLETA`, sin mirar etiqueta, custodio ni fecha. En un equipo de 37 personas que rotan el mismo teléfono, eso significa que al iniciar sesión el custodio B la app intentaba subir los pendientes de A con las credenciales de B: las reglas lo deniegan, el medio queda `FALLIDA` y B nunca lo ve porque su badge filtra por uid. La evidencia de A se volvía irrecuperable sin que nadie se enterara. Y sin ventana de fecha, cada arranque reencolaba todo el histórico que jamás llegó a completarse.

**Cambios:**

- `data/local/db/MedioDao.kt`: `obtenerPendientes` recibe `custodioUid` y hace `INNER JOIN reportes` para acotar la cola al dueño; además exige `etiqueta IS NOT NULL`.
- `data/local/db/ReporteDao.kt`: `obtenerPendientesSubida` recibe `custodioUid` y `fechaDesde`, y filtra por ambos.
- `domain/repository/MedioRepository.kt` y `ReporteRepository.kt`: firmas nuevas.
- `data/repository/RoomMedioRepository.kt` y `RoomReporteRepository.kt`: implementaciones al día.
- `data/worker/SubidaScheduler.kt` y `WorkManagerSubidaScheduler.kt`: `reconciliar` recibe el uid de la sesión y acota la ventana reutilizando `fechaDesdeUltimos30Dias` de `domain/util/Fechas.kt`, el mismo helper que ya usaba el historial — así la cola y la pantalla de Historial no pueden discrepar sobre qué es "reciente".
- `ui/captura/CapturaViewModel.kt`: la reconciliación del arranque espera al custodio (`custodioFlow.filterNotNull().first().uid`) en un `launch` propio, para no bloquear la observación del reporte activo.
- `ui/pendientes/PendientesViewModel.kt`: `recargar()` resuelve el uid de la sesión antes de reconciliar; si no hay sesión, no hace nada.
- `data/pipeline/SubidaMedioProceso.kt`: guard de defensa en profundidad — un medio sin etiqueta devuelve `Success` sin subir nada, para que ningún camino futuro se salte el filtro de la cola.

**Nota sobre los medios sin etiquetar:** no se pierden ni se esconden. Siguen en Room, siguen contando en el badge de pendientes (es trabajo sin terminar) y T4 los volverá resolubles con un toque desde la rejilla de Archivos. Lo que cambia es que ya no se suben a medio clasificar.

**Pruebas:**

- `contrato/ContratoRepositoriosTest.kt`: cuatro pruebas nuevas, ejecutadas contra Room **y** contra los fakes — la cola excluye medios sin etiqueta, la cola solo trae medios del custodio pedido, `obtenerPendientesSubida` solo trae reportes del custodio pedido, y excluye los anteriores a la fecha pedida. `nuevoReporte`/`reporteGuardado` aceptan `fechaLocal` para poder probar la ventana.
- `contrato/ContratoFakesTest.kt`: implementa el gancho `registrarDuenoDelReporte` (el fake de medios no tiene JOIN, así que se le declara la relación medio → custodio; Room no lo necesita).
- `data/pipeline/SubidaMedioProcesoTest.kt`: prueba nueva del guard — un medio sin etiqueta no se sube, no se marca `FALLIDA` y su archivo local no se toca. El medio del fixture gana `etiqueta = "dano"`.
- `androidTest/.../data/worker/ReconciliacionColaTest.kt`: las dos caracterizaciones de E3 y E6 pasan a afirmar el comportamiento **correcto** (`reconciliar_no_encola_los_medios_SIN_etiquetar` y `reconciliar_solo_encola_evidencia_del_custodio_pedido`). La de C4 queda intacta: sigue fuera de alcance.

**Verificación:** `testDebugUnitTest` 123 pruebas / 0 fallos; `connectedDebugAndroidTest` 23 / 0 fallos en CPH2769 (API 36) contra el Emulator Suite vía `adb reverse`; `lintDebug` 0 errores.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/local/db/MedioDao.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/db/ReporteDao.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/MedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/ReporteRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomReporteRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/worker/SubidaScheduler.kt`
- `app/src/main/java/com/ai27/app/custodio/data/worker/WorkManagerSubidaScheduler.kt`
- `app/src/main/java/com/ai27/app/custodio/data/pipeline/SubidaMedioProceso.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/pendientes/PendientesViewModel.kt`
- `app/src/test/java/com/ai27/app/custodio/contrato/ContratoRepositoriosTest.kt`
- `app/src/test/java/com/ai27/app/custodio/contrato/ContratoFakesTest.kt`
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/FakeReporteRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/CapturaFakes.kt`
- `app/src/test/java/com/ai27/app/custodio/data/pipeline/SubidaMedioProcesoTest.kt`
- `app/src/androidTest/java/com/ai27/app/custodio/data/worker/ReconciliacionColaTest.kt`
- `CHANGELOG.md`

## 2026:09:21 18:06

**Resumen:** Se corrigió el hallazgo E4: con el disco lleno, la app se cerraba **en el momento del disparo**. `FilesDirAlmacenArchivos.guardar` hacía un `writeBytes` desnudo y no había un solo `try/catch` en toda la cadena hasta el `viewModelScope.launch` de `capturarFoto`, así que la `IOException` escapaba de la corrutina y tumbaba el proceso. Ahora el error está tipado, llega a la pantalla con un mensaje accionable, y no deja basura. Es la tarea T2 del plan de §13.

**Por qué el escenario es realista y no teórico:** nada purga `filesDir/medios` hasta que los archivos suben. Con 50 medios por viaje, varios viajes al día y un equipo que rotan 37 custodios, llenar el almacenamiento es cuestión de tiempo — y el síntoma era que la app muriera justo al capturar evidencia.

**Cambios:**

- `domain/error/DominioError.kt`: nuevo caso `SinEspacioEnDispositivo`.
- `data/local/archivo/AlmacenArchivos.kt`: `guardar` pasa de devolver `String` a `Result<String>`. El KDoc explica por qué, citando E4.
- `data/local/archivo/FilesDirAlmacenArchivos.kt`: `guardar` comprueba el espacio **antes** de escribir y devuelve `SinEspacioEnDispositivo` si no cabe, para poder dar un mensaje concreto en lugar de un "algo salió mal". Si aun así falla con `IOException`, **borra el archivo a medias** antes de reportar: una fila de Room apuntando a un JPEG truncado se subiría como evidencia válida. Se exige un margen de 2 MB además del tamaño del archivo, porque el sistema de archivos necesita espacio para metadatos y quedarse al filo vuelve a fallar en la captura siguiente.
- `data/repository/RoomMedioRepository.kt`: `guardar` propaga el fallo sin insertar fila. Si la que falla es la **miniatura**, borra el original antes de salir: ya estaba escrito, y sin fila que lo referencie quedaría como huérfano invisible que nadie recolecta.
- `ui/error/DominioErrorRecurso.kt`: mapeo a `error_sin_espacio`, el string añadido en T1, que dice qué hacer ("espera a que suban los archivos pendientes o cierra el viaje") y no solo que falló.
- `app/src/test/.../data/pipeline/FakesPipeline.kt`: `FakeAlmacenArchivos` se adapta a la firma nueva y gana `fallaAlGuardar`, para poder simular el disco lleno.

**Pruebas nuevas** en `RoomMedioRepositoryTest`, contra Room real:

- `no deja fila ni archivo cuando el disco esta lleno`: el resultado es `Failure(SinEspacioEnDispositivo)`, no hay fila y no hay archivos.
- `si falla la miniatura no deja huerfano el original`: con un almacén que deja pasar la primera escritura y falla la segunda, se comprueba que el original **sí** se borra. Es el caso que el hallazgo E9 describe como fuente de huérfanos permanentes; aquí queda cubierto el camino de la miniatura.

**Verificación:**

- `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, **114 pruebas, 0 fallos** (112 previas + 2 nuevas).
- `./gradlew lintDebug` → **0 errores**.
- El cambio de firma lo encontró el compilador en un solo sitio más (`FakeAlmacenArchivos`), lo que confirma que el almacén tenía un único punto de escritura.
- Sin verificar en dispositivo: el comportamiento con el almacenamiento realmente lleno. Las pruebas simulan el fallo del almacén, no agotan el disco del teléfono.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/domain/error/DominioError.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/archivo/AlmacenArchivos.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/archivo/FilesDirAlmacenArchivos.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/error/DominioErrorRecurso.kt`
- `app/src/test/java/com/ai27/app/custodio/data/pipeline/FakesPipeline.kt`
- `app/src/test/java/com/ai27/app/custodio/RoomMedioRepositoryTest.kt`
- `CHANGELOG.md`

## 2026:09:21 18:02

**Resumen:** Se corrigieron los hallazgos E1 y E2 de la auditoría, que van juntos porque están acoplados. El disparador descartaba fotos en silencio al doble toque, y cualquier fallo de cámara o de procesamiento de imagen se perdía en un `catch` vacío: el custodio tocaba, no pasaba nada, y no quedaba rastro para diagnosticarlo. Es la tarea T1 del plan de §13.

**Por qué el doble toque perdía fotos:** `disparoHabilitado` dependía de `estado.capturando`, y ese flag solo se pone en `true` dentro del callback de éxito de CameraX, **después** de decodificar, rotar y comprimir el JPEG — cientos de milisegundos más tarde. En esa ventana el disparador seguía habilitado, así que el segundo y tercer toque lanzaban capturas que luego morían en un `return` mudo del ViewModel. El flash también se dibujaba en ese callback, así que el toque no producía **ninguna** señal inmediata: eso es precisamente lo que hace que el custodio vuelva a tocar.

**Cambios:**

- `data/diagnostico/RegistroDeFallos.kt` (nuevo): interfaz de una sola función para registrar fallos no fatales, con el `contexto` como etiqueta de agrupación. El KDoc advierte que el contexto no debe llevar datos del custodio ni del cliente.
- `data/diagnostico/RegistroDeFallosCrashlytics.kt` (nuevo): implementación sobre `FirebaseCrashlytics.recordException`, con el contexto como clave personalizada para que la consola agrupe por tipo de excepción y siga siendo filtrable. No añade PII, según la política de S13.
- `di/DiagnosticoModule.kt` (nuevo): el binding de Hilt.
- `ui/captura/VistaCamara.kt`: `tomarFoto` gana el parámetro `alFallar`. El `catch` vacío pasa a `catch (causa: Throwable)` —`Throwable` porque ahí es donde revienta un `OutOfMemoryError` al recomprimir el bitmap—, `onError` deja de estar vacío, y la llamada a `takePicture` se envuelve porque puede lanzar de forma sincrónica si la cámara no está lista. Los dos callbacks son excluyentes y **siempre se invoca exactamente uno**, que es de lo que depende el cerrojo.
- `ui/viaje/CapturaEvidenciaScreen.kt`: cerrojo `disparoEnCurso` que se pone en `true` **de forma sincrónica en el toque** y entra en `disparoHabilitado`; se libera en los dos callbacks. El flash se movió **antes** de disparar, para que el toque tenga confirmación inmediata. Nuevo parámetro `onFallaDeCaptura`.
- `ui/captura/CapturaViewModel.kt`: nuevo `fallaDeCaptura(causa)` que suelta `capturando`, pone el mensaje en el estado y manda la causa al registro como no fatal. Se inyecta `RegistroDeFallos`.
- `ui/navigation/AppNavHost.kt`: cablea `onFallaDeCaptura = viewModel::fallaDeCaptura`.
- `res/values/strings.xml`: `error_captura_fallo` nuevo. Se añadió también `error_sin_espacio`, que consumirá T2.
- Pruebas: `FakeRegistroDeFallos` en `CapturaFakes.kt`, cableado en `CapturaViewModelTest`, y una prueba nueva de que un fallo de cámara pone el mensaje en el estado, suelta `capturando` y queda registrado con el contexto `captura_foto`.

**Se conservó el guard de `capturando` en el ViewModel** como defensa en profundidad. Con el cerrojo ya no debería alcanzarse desde la UI, pero sigue protegiendo si alguien llama a `capturarFoto` por otra vía.

**Verificación:**

- `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, **112 pruebas, 0 fallos** (111 previas + 1 nueva).
- `./gradlew lintDebug` → **0 errores**, el gate cerrado en §12 sigue en verde.
- Sin verificar: el comportamiento del cerrojo con toques reales. Necesita una prueba de Compose con `createAndroidComposeRule` que afirme una sola invocación de `onTomarFoto` ante dos toques seguidos; queda anotada como pendiente de la tanda.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/diagnostico/RegistroDeFallos.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/diagnostico/RegistroDeFallosCrashlytics.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/di/DiagnosticoModule.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/captura/VistaCamara.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/ai27/app/custodio/CapturaFakes.kt`
- `app/src/test/java/com/ai27/app/custodio/ui/captura/CapturaViewModelTest.kt`
- `CHANGELOG.md`

## 2026:09:21 17:57

**Resumen:** Se registró en `docs/PLAN.md` la sección §13 con el plan aprobado para eliminar los seis hallazgos de pérdida de evidencia de `docs/AUDIT.md` (E1 a E6). Es la tarea T0 de esa tanda: documento únicamente, sin cambios de código.

**Cambios:**

- `docs/PLAN.md`: nueva sección `## 13. Eliminación de la pérdida de evidencia (E1–E6) — aprobado 2026-09-21`, con siete subsecciones — los seis hallazgos y su evidencia en el código (§13.1), el alcance acordado (§13.2), las decisiones D1-D3 (§13.3), las ocho tareas con dependencias (§13.4), las pruebas de caracterización que van a fallar a propósito (§13.5), el fuera de alcance por ID (§13.6) y los tres riesgos (§13.7).
- Se documenta **R2**, la decisión de producto sobre E5: la nota de voz se **pausa automáticamente** al salir de la app y el custodio la reanuda pulsando el botón al volver. Se descartaron tanto cortar la grabación como montar un servicio en primer plano; esta vía no necesita permisos nuevos ni notificación permanente y no pierde lo grabado.
- Se documenta **R3**: si el proceso muere con una nota a medias hay que avisar, no perderla en silencio.
- Se documenta **D1**: los fallos de cámara se reportan por una interfaz propia `RegistroDeFallos` sobre Crashlytics, en lugar de un `Log.w`, para que no queden indiagnosticables en campo y para poder falsearlos en pruebas.
- Se documenta **D2**: los medios sin etiquetar siguen contando en el badge de pendientes, porque son trabajo sin terminar; lo que cambia es que pasan a ser resolubles.
- Se documenta **D3**: arreglar el guard muerto de `AjustesViewModel.solicitarCerrarSesion` (hallazgo E7, fuera de alcance) entra como prerrequisito de una línea, porque es la invariante que hace segura la limpieza de datos de T5.
- §13.5 deja registrado que dos de las tres pruebas de caracterización de la tanda anterior van a fallar y hay que convertirlas, y que la de C4 se queda como está. Que fallen es la señal de que el mecanismo de D2 de §12 funcionó.
- §13.7 anticipa tres riesgos: el diff ancho de T3 por cambiar cuatro firmas de repositorio, que T5 es la única tarea que borra datos y necesita confirmación explícita, y que en T6 el caso de muerte de proceso **no se puede arreglar, solo avisar**, porque `pause()` no cierra el índice del `.m4a`.
- Se deja constancia de que la tanda **no necesita ninguna pausa de Sync**: no hay dependencias nuevas y los scripts de build no se tocan.

**Archivos modificados:**

- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 17:45

**Resumen:** Se cerró la tanda de atención de los cinco hallazgos críticos de `docs/AUDIT.md`. Esta entrada es la tarea T11: solo documentación, sin cambios de código. Se marcaron las once tareas de §12 como hechas con su resultado y su verificación, se marcaron B1, B2, B3, S1 y S2 como atendidos en `docs/AUDIT.md`, y se dejó registrado qué quedó verificado por ejecución, qué sigue pendiente de comprobación manual, y qué hallazgos nuevos salieron de ejecutar el plan.

**Cambios:**

- `docs/PLAN.md` §12.4: las once tareas pasan a `✅ hecha`.
- `docs/PLAN.md` §12.4.1 (nueva): tabla comparativa del estado de las pruebas antes y después, la lista de lo verificado por ejecución, las tres correcciones al plan que salieron de ejecutarlo, y los pendientes de comprobación manual.
- `docs/AUDIT.md`: nueva sección "Estado de atención" arriba, con la tabla de los cinco hallazgos cerrados y su evidencia, el recordatorio de que los otros 90 siguen abiertos, y los dos hallazgos nuevos.
- `docs/AUDIT.md`: los cinco encabezados de B1, B2, B3, S1 y S2 llevan `✅ ATENDIDO 2026-09-21` y una nota con qué se hizo, qué tarea lo hizo y qué queda pendiente de comprobar a mano.

**Estado final de las pruebas:**

| Suite | Antes | Después |
| --- | --- | --- |
| `testDebugUnitTest` | 71 pruebas | **111 pruebas, 0 fallos** |
| `connectedDebugAndroidTest` | 1 (andamiaje) | **23 pruebas, 0 fallos** |
| `lintDebug` | **141 errores** | **0 errores** |
| `lintVitalRelease` | pasaba en verde siendo ciego | pasa, y ahora sí detiene el release |

**Lo que se verificó ejecutando, no leyendo:**

- **B1 en los dos sentidos:** con el helper viejo reintroducido en un solo punto de llamada, 7 de las 8 pruebas contra el Emulator Suite fallan con `IllegalStateException: Tarea Auth sin resultado ni error`; restaurado, vuelven las 8 a verde.
- **B3 en los dos sentidos:** con el defecto de B2 reintroducido, `assembleRelease` falla con `Call requires API level 31 (current min is 26) [NewApi]`. Antes de esta tanda ese mismo APK se empaquetaba y firmaba sin una queja.
- **S1** por búsqueda: cero referencias en `app/src`. **S2** en el manifest fusionado.
- El APK de release sigue firmado con `CN=App Custodio, OU=AI27` y la configuration cache se sigue reutilizando.

**Tres correcciones al plan, registradas para que no se repitan:**

1. `abortOnError = true` **no** cerraba el gate de B3: `lintVital` solo mira severidad `Fatal`. Hay que promover los issues a `Fatal`.
2. La decisión D1 había aceptado que no quedaría un test unitario barato para B1. Resultó que sí lo hay: `Tasks.forResult<Void>(null)` y `await()` funcionan en la JVM de host.
3. `kotlinx-coroutines-play-services` ya estaba en el classpath por Firebase; T5 hizo explícito un transitivo, no añadió algo nuevo.

**Pendiente de comprobación manual, por falta de equipo o de acceso:**

- Grabar una nota de voz en un equipo con Android 8, 9, 10 u 11: el teléfono de pruebas es API 36, así que la rama del gate de B2 con el constructor antiguo **no se ejercitó**.
- `adb shell bmgr backupnow com.ai27.app.custodio`.
- Mirar la galería de un dispositivo tras capturar, para confirmar que `Pictures/AppCustodio` ya no recibe copias.
- En equipos ya instalados, las copias de versiones anteriores siguen en la galería y hay que borrarlas a mano.

**Sigue abierto y conviene decidirlo pronto:** arreglar B1 hizo que las subidas funcionen por primera vez, y con eso C1, C2 y C4 empezaron a producir datos incorrectos en Firestore — `sha256` nulo y no rellenable, contadores desfasados, y reintentos que cancelan subidas en vuelo. Los otros 90 hallazgos de la auditoría siguen sin atender, por decisión de alcance.

**Archivos modificados:**

- `docs/PLAN.md`
- `docs/AUDIT.md`
- `CHANGELOG.md`

## 2026:09:21 17:42

**Resumen:** Se corrigió el hallazgo bloqueante B3: el gate de lint del release era ciego. `lintDebug` fallaba con 141 errores pero `assembleRelease` pasaba en verde, así que se podía empaquetar y entregar a campo un APK con un crash garantizado. Se podó la carpeta de recursos redundante que generaba el 98 % del ruido, se arregló el único error real que quedaba, y se cerró el gate de verdad. Es la tarea T10 del plan de §12.

**Hallazgo importante: la receta del plan no bastaba.** El plan preveía cerrar el gate con `abortOnError = true`. Se comprobó por experimento que **eso no funciona**: `lintVitalRelease` —la tarea que corre `assembleRelease`— solo considera issues de severidad **Fatal**, y `NewApi` es severidad **Error**, así que ni se reporta. Reintroduciendo a propósito el defecto de B2 (quitar el gate de `SDK_INT` del `MediaRecorder`), `assembleRelease` seguía dando BUILD SUCCESSFUL con `abortOnError` activo. Lo que cierra el gate es **promover a Fatal** los issues que significan "esto truena o filtra en campo". Con eso, el mismo experimento hace fallar el release con el mensaje correcto.

**Cambios:**

- Se borró `app/src/main/res/values-es-rMX/` completa (17 claves, todas duplicados idénticos en español de `values/`, que ya es el default). Generaba **138 de los 141 errores** de lint por `MissingTranslation` y tapaba los reales. Además era una trampa: una corrección de copy en `values/` sobre una de esas 17 claves no se habría visto en ningún teléfono es-MX.
- `data/ubicacion/ProveedorUbicacion.kt`: `FusedProveedorUbicacion` recibe ahora el `Context` y comprueba el permiso con `ContextCompat.checkSelfPermission` (aceptando `ACCESS_FINE_LOCATION` **o** `ACCESS_COARSE_LOCATION`) antes de llamar a `getCurrentLocation`. Sin permiso devuelve `null`, que es lo que hace que la UI ofrezca el campo de ubicación por texto previsto en §7 del plan, en lugar de que un `SecurityException` tumbe la captura. Esto resuelve el error `MissingPermission`, que era el único error real que quedaba.
- `data/ubicacion/ProveedorUbicacion.kt`: se añadió `try/catch (SecurityException)` alrededor de la llamada, porque el permiso se puede revocar entre la comprobación y el uso, y se cambió `tarea.result` por `if (tarea.isSuccessful) tarea.result?.aGps() else null`. Lo segundo era un segundo defecto latente en las mismas tres líneas: `Task.getResult()` lanza `RuntimeExecutionException` si la tarea falló, y aquí se leía sin comprobar nada.
- `di/UbicacionModule.kt`: el provider pasa el `@ApplicationContext` al constructor.
- `app/build.gradle.kts`: bloque `lint { }` nuevo con `checkReleaseBuilds = true`, `abortOnError = true`, `warningsAsErrors = false` y, sobre todo, `fatal += setOf("NewApi", "InlinedApi", "MissingPermission", "CoarseFineLocation", "MissingTranslation", "Recycle", "WrongThread")`. El comentario del bloque documenta por qué `abortOnError` solo no alcanza, para que nadie lo simplifique después.

**Sin baseline, a propósito:** no se generó `lint-baseline.xml`. Tras las dos correcciones lint quedó en **cero errores**, así que un baseline vacío solo serviría para esconder los próximos. Los 35 issues restantes son warnings e informativos (13 `GradleDependency`, 7 `ModifierParameter`, 4 `NewerVersionAvailable`, 3 `UnusedResources`, 2 `UseKtx`, y uno cada uno de `UseOfNonLambdaOffsetOverload`, `PluralsCandidate`, `HardwareIds`, `DiscouragedApi`, `AutoboxingStateCreation`, `AndroidGradlePluginVersion`), ninguno bloqueante y todos ya inventariados en T15 de `docs/AUDIT.md`.

**Verificación:**

- `./gradlew lintDebug` → BUILD SUCCESSFUL, **cero errores** (antes: 141).
- `./gradlew lintVitalRelease` → BUILD SUCCESSFUL.
- `./gradlew testDebugUnitTest` → **111 pruebas, 0 fallos**.
- `./gradlew assembleRelease` → BUILD SUCCESSFUL, APK de 23 208 770 B, y `apksigner verify` confirma que sigue firmado con `CN=App Custodio, OU=AI27`.
- **Gate comprobado en los dos sentidos:** con el defecto de B2 reintroducido, `assembleRelease` **falla** con `Error: Call requires API level 31 (current min is 26) [NewApi]`; con el arreglo restaurado, vuelve a pasar. Antes de esta tarea ese mismo defecto se empaquetaba sin una sola queja.
- Compatibilidad con configuration-cache conservada: `Reusing configuration cache` en la segunda corrida de `assembleDebug`.

**Archivos modificados:**

- `app/src/main/res/values-es-rMX/strings.xml` (eliminado, y la carpeta con él)
- `app/src/main/java/com/ai27/app/custodio/data/ubicacion/ProveedorUbicacion.kt`
- `app/src/main/java/com/ai27/app/custodio/di/UbicacionModule.kt`
- `app/build.gradle.kts`
- `CHANGELOG.md`

## 2026:09:21 17:36

**Resumen:** Se cubrió con pruebas la capa `data/worker`, que no tenía **ninguna** y es la que decide si la evidencia se reintenta o se abandona. Son 14 pruebas instrumentadas: la escalera de reintentos del worker de subida y el comportamiento de `reconciliar()`, la rutina que repone la cola al reabrir la app. Es la tarea T9 del plan de §12, con la decisión D2.

**Cambios:**

- `app/src/androidTest/.../data/worker/WorkerSubidaMedioTest.kt` (nuevo, 6 pruebas): escalera de reintentos con `TestListenableWorkerBuilder`. Verifica que los intentos 1 a 3 devuelven `retry()`, que el intento 4 (`MAX_INTENTOS`) devuelve `failure()` y **deja el medio en `FALLIDA`** para el reintento manual, que una subida exitosa lo marca `COMPLETA` con su `sha256`, que sin datos de entrada falla sin reintentar, que un medio ya `COMPLETA` no se vuelve a subir, y que no se sube evidencia a un reporte descartado localmente.
- `app/src/androidTest/.../data/worker/ReconciliacionColaTest.kt` (nuevo, 8 pruebas): usa `WorkManagerTestInitHelper` con `SynchronousExecutor`. Verifica que `encolarSubidaMedio` crea un trabajo único por medio, que `reconciliar()` repone la cola de los pendientes y encola también el sync del reporte, que reintenta los `FALLIDA`, que **rescata los medios pegados en `EN_PROGRESO`** (importante: esos no se pueden reintentar a mano porque `ReintentarSubida` los rechaza, así que la reconciliación es su única salida) y que no vuelve a encolar los `COMPLETA`.

**Decisión de diseño:** las pruebas usan **Room real** sobre un archivo temporal y el almacén de archivos real; lo único falseado es la capa remota, que son interfaces. Se tomó ese camino porque los fakes existentes viven en `app/src/test` y no están en el classpath de `androidTest`, y porque duplicarlos habría dado menos fidelidad: así el `doWork()` se ejercita de verdad, incluidas las escrituras a la base.

**Tres pruebas de caracterización (decisión D2).** Documentan defectos reales que quedaron **fuera del alcance** acordado. Afirman lo que la app hace hoy, con el ID del hallazgo en el nombre y la explicación en un comentario. No llevan `@Ignore`, porque una prueba ignorada no protege nada: cuando el defecto se arregle, la prueba falla y obliga a actualizarla conscientemente.

- `caracterizacion_E3_reconciliar_encola_medios_SIN_etiquetar`: `reconciliar()` encola todo lo que no esté `COMPLETA` sin mirar la etiqueta, así que un medio descartado desde la hoja de etiquetado sube sin clasificar y el analista recibe evidencia que no puede filtrar.
- `caracterizacion_C4_encolar_de_nuevo_REEMPLAZA_el_trabajo_anterior`: con `ExistingWorkPolicy.REPLACE`, volver a encolar cancela el trabajo anterior. Como `reconciliar()` se llama desde el pull-to-refresh de Pendientes, el custodio puede cancelar sin saberlo subidas en vuelo y reiniciarlas desde cero.
- `caracterizacion_E6_la_cola_no_filtra_por_custodio`: ni `obtenerPendientes()` ni `obtenerPendientesSubida()` filtran por `custodioUid`, así que en un equipo compartido la app reencola la evidencia del custodio anterior con el token del nuevo.

**Verificación:**

- `./gradlew connectedDebugAndroidTest` sobre CPH2769 (API 36) → **23 pruebas, 0 fallos, 0 errores**: 8 de `RemotoEmuladorTest`, 8 de `ReconciliacionColaTest`, 6 de `WorkerSubidaMedioTest` y 1 del `ExampleInstrumentedTest` de andamiaje.
- `./gradlew testDebugUnitTest` sigue en **111 pruebas, 0 fallos**.
- Confirmado por ejecución que `MAX_INTENTOS = 4` se comporta como dice el código: tres `retry` y un `failure`. Los 10 reintentos por defecto de WorkManager nunca se alcanzan, porque el tope propio dispara antes.

**Archivos modificados:**

- `app/src/androidTest/java/com/ai27/app/custodio/data/worker/WorkerSubidaMedioTest.kt` (nuevo)
- `app/src/androidTest/java/com/ai27/app/custodio/data/worker/ReconciliacionColaTest.kt` (nuevo)
- `CHANGELOG.md`

## 2026:09:21 17:33

**Resumen:** Se atendió el hallazgo P2 de la auditoría: los fakes de prueba divergían de las implementaciones Room en cuatro puntos, y **dos de esas divergencias estaban afirmadas como correctas en pruebas verdes**, así que la suite documentaba comportamiento que no existe en producción. Se creó una suite de contrato que corre la misma batería de aserciones contra Room y contra los fakes, se alinearon los cuatro fakes y se corrigieron las dos pruebas equivocadas. Es la tarea T8 del plan de §12, con la decisión D3.

**Cambios:**

- `app/src/test/.../contrato/ContratoRepositoriosTest.kt` (nuevo): clase abstracta con **18 pruebas** del contrato observable de `MedioRepository` y `ReporteRepository`. Solo comportamiento visible a través de la interfaz, nada de detalles de implementación, porque eso es justamente lo que legítimamente difiere entre Room y un fake.
- `app/src/test/.../contrato/ContratoRoomTest.kt` (nuevo): ejecuta el contrato contra `RoomMedioRepository` y `RoomReporteRepository` sobre SQLite nativo y un directorio temporal. Es la definición de verdad.
- `app/src/test/.../contrato/ContratoFakesTest.kt` (nuevo): ejecuta el mismo contrato contra `FakeMedioRepository` y `FakeReporteRepository`.

**Las cuatro divergencias corregidas en los fakes:**

1. `FakeMedioRepository.obtenerPendientes()` excluía `FALLIDA` y exigía `reporteId` no vacío; ahora filtra solo por `!= COMPLETA`, igual que `MedioDao`. Importa porque `reconciliar()` usa esa lista para reponer la cola: con el filtro viejo, un medio fallido no se reintentaría nunca al reabrir la app.
2. `FakeMedioRepository.marcarSubidaCompleta` ponía `localPath = null`; ahora lo conserva, porque el `UPDATE` del DAO solo toca `estadoSubida`, `storagePath` y `sha256`. El archivo físico sí se borra, así que la fila real queda apuntando a una ruta inexistente — y eso es lo que ve la rejilla.
3. `FakeReporteRepository.cerrar` fijaba `completo = true` y `mediosSubidos = mediosEsperados`; ahora no toca ninguno de los dos, como `ReporteDao.cerrar`. Cerrar un viaje no significa que la evidencia ya subió.
4. `FakeReporteRepository.obtenerPendientesSubida` filtraba por `ciclo != DESCARTADO`; ahora filtra por `subida != COMPLETA`, igual que el DAO. Por eso un reporte descartado sigue apareciendo en la cola, que es el origen del backlog creciente descrito en E6.

**Las dos pruebas corregidas (decisión D3):** no es maquillar un fallo, es lo contrario — describían comportamiento que no existe.

- `CerrarReporteTest.cierra reporte agregando el resumen de medios`: afirmaba `assertEquals(3, cerrado.mediosSubidos)` con los tres medios en `PENDIENTE`. Ahora afirma `mediosSubidos == 0` y `completo == false`, con un comentario que explica que el contador lo avanza el pipeline y no el cierre.
- `SubidaMedioProcesoTest.sube un medio y lo marca completo borrando el archivo local`: afirmaba `assertNull(subido.localPath)`. Ahora afirma `assertNotNull`, con la explicación de que el archivo se borra pero la columna se conserva. La comprobación de que el archivo físico desapareció ya estaba y se mantiene.

**Cómo se validó el enfoque:** al correr el contrato antes de tocar los fakes, `ContratoRoomTest` pasó **18 de 18** y `ContratoFakesTest` falló **5** pruebas — las cuatro divergencias, con la número 4 apareciendo en dos casos distintos. Después de alinear los fakes, los dos únicos fallos de toda la suite fueron exactamente las dos aserciones equivocadas, lo que confirma que el contrato apunta a lo que debía y que no había daño colateral en ninguna otra prueba.

**Verificación:**

- `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, **111 pruebas, 0 fallos** (75 previas + 36 del contrato, que son 18 × 2 implementaciones).
- El contrato pasa idéntico contra Room y contra los fakes, así que una divergencia futura rompe la compilación de la confianza en lugar de esconderse.

**Nota de portabilidad para CI:** las pruebas de Room corren en la JVM con `BundledSQLiteDriver`, y eso depende de `app/src/test/jniLibs/libsqliteJni.dylib`, que es el binario nativo de **macOS arm64**. Un runner de Linux necesitará la variante `.so`. Queda anotado porque la suite de contrato multiplica la dependencia de ese binario: ahora 18 pruebas más lo necesitan.

**Archivos modificados:**

- `app/src/test/java/com/ai27/app/custodio/contrato/ContratoRepositoriosTest.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/contrato/ContratoRoomTest.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/contrato/ContratoFakesTest.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/FakeReporteRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/CerrarReporteTest.kt`
- `app/src/test/java/com/ai27/app/custodio/data/pipeline/SubidaMedioProcesoTest.kt`
- `CHANGELOG.md`

## 2026:09:21 17:28

**Resumen:** Se construyó la red de regresión del hallazgo B1, que es el hueco que permitió que el pipeline de subida estuviera roto sin que nada lo detectara: `data/remote` solo tenía una prueba (el mapeo de códigos de Auth). Son dos capas: pruebas JVM que fijan la semántica exacta que se rompió, y una prueba instrumentada contra el Emulator Suite que recorre el viaje redondo real. Es la tarea T7 del plan de §12, y **con ella B1 queda verificado de verdad, no solo compilando**.

**Resultado principal: la regresión de B1 quedó demostrada empíricamente.** Al revertir *un solo* punto de llamada (`FirebaseReporteRemoto.crear`) al helper viejo y correr la prueba instrumentada en el dispositivo, **fallan 7 de las 8 pruebas**, con el mensaje exacto del defecto: `crear() debe devolver Success; era el fallo de B1. Obtuve: Failure(error=Inesperado(causa=java.lang.IllegalStateException: Tarea Auth sin resultado ni error))`. La única que sobrevive es la que no llama a `crear`. Después se restauró el arreglo y las 8 volvieron a verde. Eso confirma dos cosas: que el defecto era real y total, y que la prueba nueva lo atrapa.

**Cambios:**

- `app/src/test/.../data/remote/EsperaDeTareasTest.kt` (nuevo): 4 pruebas JVM que ejercitan `ejecutarRemoto` de producción con `Task`s reales de Play Services. Fijan que una `Task<Void>` exitosa produce `Success` (la forma que devuelven `set()` y `update()`, y exactamente lo que fallaba), que una `Task` con resultado no nulo sigue devolviendo su valor, y que un fallo propaga la excepción y se mapea al error de dominio correcto. Corren **sin dispositivo ni emulador**, así que sirven en CI.
- `app/src/androidTest/.../data/remote/RemotoEmuladorTest.kt` (nuevo): 8 pruebas de integración contra los emuladores. Construyen los remotos a mano (sin Hilt), crean una cuenta al vuelo en el emulador de Auth —las reglas exigen `request.auth.uid == custodioUid`, así que hay que estar autenticado de verdad— y cubren: `crear` + `obtenerCiclo`, `actualizarContadores`, `cerrar` con transición a `CERRADO`, `descartar` con transición a `DESCARTADO`, `obtenerCiclo` de un reporte inexistente, el viaje redondo `subirArchivo` + `crearDocumentoSiNoExiste` con verificación del documento leído por llave, y la idempotencia del reintento.
- `README.md`: nueva subsección "Pruebas instrumentadas contra el Emulator Suite" con los tres pasos y, sobre todo, la explicación de por qué hacen falta los túneles `adb reverse`.

**Decisiones tomadas durante la tarea:**

- **El acceso al emulador se resuelve con `adb reverse`, no cambiando `ModoEmulador.HOST`.** La prueba corrió contra un teléfono físico (CPH2769, API 36), donde `10.0.2.2` no existe: esa dirección solo funciona en el emulador de Android. `adb reverse tcp:8080|9199|9099` hace que `127.0.0.1` del dispositivo salga por el cable al host, así que la constante actual sirve sin tocarla y hay una sola configuración para teléfono y emulador. Queda documentado porque sin los túneles las pruebas fallan con `SinConexion` y parece un problema de reglas.
- Se respetaron los quirks del Emulator Suite ya documentados en `AGENTS.md`: solo `get` por llave (nunca `list`/`runQuery`), y el documento del reporte se crea **antes** que el del medio, porque la regla de Storage hace un `get()` cruzado a `reportes/{id}` y en el emulador un `get()` a un documento inexistente revienta la regla completa.
- Cada prueba usa UUID nuevos, así que no hay estado compartido ni dependencia del orden de ejecución — al contrario de lo que hoy pasa en `firebase/test/reglas.test.js`, señalado en S14.

**Prueba de caracterización añadida (hallazgo C2, fuera de alcance):** `el_sha256_del_documento_queda_nulo_hallazgo_C2_aceptado` afirma que el documento remoto de `medios` se escribe con `sha256` nulo. Es el comportamiento actual y está **mal**, pero C2 quedó fuera del alcance acordado (§12.5). Se deja clavado a propósito, con el ID citado en el nombre y en un comentario: el día que se arregle C2, la prueba falla y obliga a actualizarla conscientemente. Es el mismo criterio de la decisión D2.

**Limitación encontrada y registrada:** la rama de `FirebaseNetworkException` de `excepcionRemotoAError` **no se puede cubrir en una prueba JVM**, porque el constructor de esa excepción llama a `android.text.TextUtils.isEmpty`, que no existe fuera de Android, y falla antes de llegar al mapeo. Se cubre con `IOException`, que entra por la misma rama del `when`. Queda anotado en el propio archivo de prueba. Tampoco se pudo replicar el helper viejo en una prueba JVM para demostrar el fallo: usaba `addOnCompleteListener` sin executor, que requiere un `Looper` de hilo principal, así que revienta con `ExceptionInInitializerError` en la JVM; por eso la demostración se hizo en el dispositivo, que además es evidencia más fuerte.

**Verificación:**

- `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, **75 pruebas, 0 fallos** (71 previas + 4 nuevas).
- `./gradlew connectedDebugAndroidTest` sobre CPH2769 (API 36) con los emuladores y los túneles activos → **8 pruebas, 0 fallos, 0 errores**.
- Regresión comprobada revirtiendo `crear()` al helper viejo: **7 de 8 fallos**, con el mensaje de B1. Restaurado el arreglo, 8 de 8 en verde. Se verificó por búsqueda que no quedó ningún resto del helper temporal en `FirebaseReporteRemoto`.

**Archivos modificados:**

- `app/src/test/java/com/ai27/app/custodio/data/remote/EsperaDeTareasTest.kt` (nuevo)
- `app/src/androidTest/java/com/ai27/app/custodio/data/remote/RemotoEmuladorTest.kt` (nuevo)
- `README.md`
- `CHANGELOG.md`

## 2026:09:21 17:20

**Resumen:** Se corrigió el hallazgo bloqueante B1, la causa de que el pipeline de subida no pudiera funcionar contra Firebase real. El helper propio `Task<T>.esperar()` solo resolvía la corrutina cuando `resultado.result != null`, y una `Task<Void>` **exitosa** devuelve `getResult() == null`, así que toda escritura a Firestore (`set` y `update`) caía al `else` y lanzaba `IllegalStateException`. Como `asegurarReporte()` corre antes de tocar Storage, el efecto era total: ni un byte salía a Storage ni un documento a Firestore, todo medio terminaba en `FALLIDA`, el badge de pendientes nunca bajaba y el cierre de sesión quedaba bloqueado para siempre. Es la tarea T6 del plan de §12, con la decisión D1 ya confirmada.

**Cambios:**

- Se borró `internal suspend fun <T> Task<T>.esperar()` de `data/remote/AuthDataSource.kt` y se sustituyó por `await()` de `kotlinx-coroutines-play-services`, la implementación oficial de este contrato. Se eliminaron los imports que quedaron sin uso: `com.google.android.gms.tasks.Task`, `kotlin.coroutines.resume`, `kotlin.coroutines.resumeWithException` y `kotlinx.coroutines.suspendCancellableCoroutine`.
- `data/remote/FirebaseReporteRemoto.kt`: los 5 usos pasan a `await()` — `obtenerCiclo` (`get`), `crear` (`set`), `cerrar` (`update`), `descartar` (`update`) y `actualizarContadores` (`update`). Los cuatro últimos son los que fallaban siempre.
- `data/remote/FirebaseMedioRemoto.kt`: los 4 usos pasan a `await()` — los dos `putBytes` de `subirArchivo` (archivo y miniatura) y el `get` más el `set` de `crearDocumentoSiNoExiste`. El `set` era el que fallaba siempre.
- Se añadió el import `kotlinx.coroutines.tasks.await` en los tres archivos.
- Se conservó intacto el orden de los `catch` de `iniciarSesion`: `FirebaseAuthException` sigue capturándose **antes** del `Exception` genérico, así que `codigoSesionAError` sigue colapsando `USER_NOT_FOUND`, `WRONG_PASSWORD`, `INVALID_CREDENTIAL`, `USER_DISABLED` e `INVALID_EMAIL` en un único `CredencialesInvalidas`. Eso es lo que evita la enumeración de usuarios y está registrado como correcto en §10 de `docs/AUDIT.md`; no se tocó.

**Beneficio adicional de usar la implementación oficial:** el helper propio no tenía `invokeOnCancellation`, así que al cancelarse la corrutina la `Task` seguía viva y un worker cancelado dejaba el `UploadTask` corriendo. `await()` sí propaga la cancelación. Era la segunda mitad del hallazgo B1 y queda resuelta sin trabajo extra.

**Verificación:**

- `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, **71 pruebas, 0 fallos, 0 errores**.
- Búsqueda en `app/src` de `esperar()` → sin resultados. El único `suspendCancellableCoroutine` que queda es el de `ProveedorUbicacion`, que es una implementación distinta y correcta (sí tiene `invokeOnCancellation`) y no entra en esta tarea.
- Los 10 puntos de espera de `data/remote` usan `await()`.
- **Sin verificar, y es lo importante de esta tarea:** que las escrituras a Firestore funcionen de verdad. La suite actual **no ejercita la capa remota** — `data/remote` solo tenía una prueba, la del mapeo de códigos de Auth — así que un build verde no demuestra nada sobre B1. Es exactamente el hueco que permitió que este defecto llegara a este punto sin detectarse. La red de regresión la construye la tarea T7 contra el Emulator Suite, y ahí hay que comprobar que el test **falla** si se revierte este cambio.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/remote/AuthDataSource.kt`
- `app/src/main/java/com/ai27/app/custodio/data/remote/FirebaseReporteRemoto.kt`
- `app/src/main/java/com/ai27/app/custodio/data/remote/FirebaseMedioRemoto.kt`
- `CHANGELOG.md`

## 2026:09:21 17:16

**Resumen:** Se declararon las dos dependencias que necesitan las tareas siguientes del plan de §12: `kotlinx-coroutines-play-services` para el `Task<T>.await()` que sustituye al helper propio roto (hallazgo B1, tarea T6) y `androidx.work:work-testing` para poder probar la escalera de reintentos de los workers (tarea T9). Es la tarea T5 del plan, agrupada en una sola edición de scripts de build para que haya una única pausa de Sync.

**Hallazgo relevante durante la tarea:** `kotlinx-coroutines-play-services:1.10.2` **ya estaba en el classpath**, arrastrado transitivamente por Firebase (se resuelve desde `1.7.3` y `1.9.0` vía el BOM de coroutines). Es decir, `await()` ya era alcanzable sin añadir nada. Se declara explícitamente de todos modos, porque `data/remote` va a usarlo de forma directa y depender de un transitivo significa que una actualización de Firebase podría quitarlo y romper la compilación sin aviso.

**Cambios:**

- `gradle/libs.versions.toml`: nueva versión `coroutines = "1.10.2"`, alineada con el `kotlinx-coroutines-core` que realmente se resuelve en el classpath (verificado con `:app:dependencies`). Se dejó `coroutinesTest` como estaba para no tocar lo que ya funciona; el comentario explica la relación entre ambas.
- `gradle/libs.versions.toml`: nueva librería `kotlinx-coroutines-play-services` apuntando a `coroutines`, con comentario que la liga al hallazgo B1 y a la tarea T6.
- `gradle/libs.versions.toml`: nueva librería `androidx-work-testing` apuntando a la versión `work` que ya existía (`2.11.0`), de modo que quede siempre alineada con `work-runtime-ktx` y no puedan desincronizarse.
- `app/build.gradle.kts`: `implementation(libs.kotlinx.coroutines.play.services)` en el bloque de Firebase, con comentario explicando por qué se declara algo que ya llegaba transitivamente.
- `app/build.gradle.kts`: `androidTestImplementation(libs.androidx.work.testing)`.
- No se tocó nada más del build. El bloque `lint { }` va aparte, en la tarea T10, precisamente para no encadenar dos Sync seguidos.

**Verificación:**

- `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, 71 pruebas en verde.
- `:app:dependencies --configuration releaseRuntimeClasspath` → `kotlinx-coroutines-play-services:1.10.2` ya aparece como dependencia declarada, no solo transitiva.
- `:app:dependencies --configuration debugAndroidTestRuntimeClasspath` → `androidx.work:work-testing:2.11.0` presente.
- Compatibilidad con configuration-cache conservada: una segunda corrida de `assembleDebug` reporta `Reusing configuration cache` y termina en 467 ms.

**Archivos modificados:**

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `CHANGELOG.md`

## 2026:09:21 17:14

**Resumen:** Se corrigió el hallazgo crítico S2 de la auditoría. El manifest declaraba `allowBackup="true"` apuntando a dos archivos de reglas que eran la **plantilla de Android Studio con todo comentado**, y un `<full-backup-content>` sin reglas no excluye nada: significa "respalda todo lo respaldable". Es decir, la base Room con los reportes y los medios, y las fotos con GPS y las notas de voz de `filesDir/medios`, salían hacia la cuenta de Google **del equipo compartido** y hacia la transferencia equipo a equipo. Contradecía §4 del plan, que dice explícitamente que `filesDir` está excluido de Auto Backup. Es la tarea T4 del plan de §12.

**Cambios:**

- `AndroidManifest.xml`: `android:allowBackup` pasa de `"true"` a `"false"`. Es la postura correcta para una app de evidencia con cadena de custodia y con su propio pipeline a Firebase: el respaldo de usuario no aporta nada y sí saca datos de cliente del control de la empresa.
- `res/xml/backup_rules.xml` (gobierna API ≤30): se reemplazó la plantilla comentada por exclusiones explícitas de `app_custodio.db`, sus archivos `-wal` y `-shm`, y del directorio `medios` del dominio `file`.
- `res/xml/data_extraction_rules.xml` (gobierna API ≥31): se reemplazó la plantilla comentada por las mismas cuatro exclusiones, declaradas en **los dos canales** — `cloud-backup` y `device-transfer` — porque son independientes y excluir solo uno deja el otro abierto.
- Ambos XML llevan comentarios que explican qué se excluye, por qué, y que son defensa en profundidad: con `allowBackup="false"` no se ejecutan, pero si alguien vuelve a activar el respaldo la evidencia sigue quedando fuera. Se referencian los hallazgos S2 y E12.
- Se incluyen los archivos WAL y SHM porque viven en el mismo dominio `database` y son archivos aparte: excluir solo el `.db` dejaría el diario dentro del respaldo.

**Rutas verificadas antes de escribir las reglas:**

- La base vive en `context.getDatabasePath("app_custodio.db")` (`AppCustodioDatabase.crearBaseDatos`), es decir el directorio estándar de bases → dominio `database`.
- Los binarios viven en `File(context.filesDir, "medios")` (`DatabaseModule.proveeAlmacenArchivos`) → dominio `file`, ruta `medios`.

**Consecuencia aceptada:**

- La preferencia de tema nocturno (SharedPreferences `preferencias`) ya no sobrevive a una reinstalación ni a un cambio de equipo. Es un booleano con default sano (la app es dark-first), así que el costo es que el custodio vuelva a activar el tema claro si lo tenía.
- Deja de ser posible restaurar evidencia sin subir desde un respaldo. Eso es deliberado: el hallazgo E12 explica que ese "rescate" en realidad producía filas `PENDIENTE` apuntando a archivos inexistentes, con rutas absolutas que podían señalar a otro perfil de usuario, y el custodio quedaba con evidencia fantasma imposible de subir o descartar y un badge de pendientes que nunca bajaba.

**Verificación:**

- `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, 71 pruebas en verde.
- Manifest fusionado de debug: `android:allowBackup="false"`, con `dataExtractionRules` y `fullBackupContent` apuntando a los dos XML nuevos. Ambos XML compilan sin error de aapt.
- Sin verificar: `adb shell bmgr backupnow com.ai27.app.custodio` no debe respaldar datos del paquete. No hubo dispositivo disponible; queda como comprobación manual pendiente.

**Archivos modificados:**

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `CHANGELOG.md`

## 2026:09:21 17:12

**Resumen:** Se corrigió el hallazgo crítico S1 de la auditoría eliminando por completo la copia pública de cada foto. La app escribía **siempre** una segunda copia JPEG en `Pictures/AppCustodio` vía MediaStore, sin flag ni condición de build y sin borrarla nunca: era legible por la galería, por WhatsApp, por cualquier app con `READ_MEDIA_IMAGES`, por el respaldo de Google Fotos y por el siguiente custodio que tomara el equipo compartido. Contradecía la premisa de `filesDir` privado de §4 del plan y volvía cosmético el borrado de evidencia pre-subida. Es la tarea T3 del plan de §12.

**Cambios:**

- Se borraron `data/local/media/FotosPublicas.kt` (interfaz) y `data/local/media/FotosPublicasMediaStore.kt` (implementación con MediaStore para API 29+ y `getExternalStoragePublicDirectory` + `MediaScannerConnection` para API 26-28).
- `di/CapturaModule.kt`: se quitó el provider `proveeFotosPublicas` y los dos imports asociados.
- `ui/captura/CapturaViewModel.kt`: se quitaron el import de `FotosPublicas`, el parámetro de constructor `fotosPublicas`, la llamada `copiarAPublica(...)` y el método completo. La rama `is Result.Success` del `when` se simplifica a un `it.copy(...)` directo.
- `ui/captura/CapturaViewModel.kt`: se quitaron el import de `android.util.Log` y la constante `TAG`, que solo existían para registrar el fallo de la copia pública.
- `AndroidManifest.xml`: se quitó `WRITE_EXTERNAL_STORAGE` (con su `maxSdkVersion="28"`), que solo existía para la copia pública en Android 8-9. Se conservaron intactos los cambios previos del usuario: `ACCESS_COARSE_LOCATION`, `screenOrientation="fullSensor"` y la ausencia de `android:label` en `MainActivity`.
- `ui/viaje/CapturaEvidenciaScreen.kt`: `permisosSolicitados()` deja de ser un `if` sobre `SDK_INT <= P` y pasa a ser un `arrayOf` plano de cámara, micrófono y ubicación fina. Se quitó el import de `android.os.Build`, que quedó sin uso.
- `app/src/test/.../CapturaFakes.kt`: se quitó `FakeFotosPublicas` y su import.
- `app/src/test/.../ui/captura/CapturaViewModelTest.kt`: se quitaron el import, el campo `fotos`, su inicialización en `setUp` y el argumento `fotosPublicas` del constructor.

**Efectos colaterales positivos, registrados por trazabilidad:**

- Desaparece la **tercera escritura completa a disco por foto** y la retención del `ByteArray` que exigía: es parte del hallazgo R5, que por lo demás sigue abierto (la subida sigue usando `putBytes` con el archivo entero en memoria).
- Desaparece el **único `Log.` de `app/src/main`**, que era la vía residual de PII señalada en S13.
- Se cierra el efecto secundario que vivía **dentro del lambda de `interno.update`**, señalado en C9: `MutableStateFlow.update` puede re-ejecutar su lambda bajo contención, así que `copiarAPublica` podía dispararse más de una vez. La otra mitad de C9 (`sincronizarReporte` en la misma situación) sigue abierta.

**Verificación:**

- `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, **71 pruebas, 0 fallos, 0 omitidas**.
- Búsqueda en todo `app/src` de `FotosPublicas|copiarAPublica|WRITE_EXTERNAL_STORAGE|MediaStore|MediaScannerConnection` → **sin resultados**.
- Permisos finales del manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- Sin verificar: que la galería de un dispositivo real ya no muestre nada en `Pictures/AppCustodio` tras capturar. No hubo dispositivo disponible. Nota operativa: en equipos que ya tienen la app instalada, **las copias creadas por versiones anteriores siguen en la galería** y hay que borrarlas a mano al actualizar.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/local/media/FotosPublicas.kt` (eliminado)
- `app/src/main/java/com/ai27/app/custodio/data/local/media/FotosPublicasMediaStore.kt` (eliminado)
- `app/src/main/java/com/ai27/app/custodio/di/CapturaModule.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/ai27/app/custodio/CapturaFakes.kt`
- `app/src/test/java/com/ai27/app/custodio/ui/captura/CapturaViewModelTest.kt`
- `CHANGELOG.md`

## 2026:09:21 17:10

**Resumen:** Se corrigió el hallazgo B2 de la auditoría: `GrabadoraMediaRecorder` construía `MediaRecorder(context)`, un constructor que existe desde API 31, con `minSdk = 26` y sin ningún gate de versión. En cualquier equipo con Android 8, 9, 10 u 11 el runtime lanzaba `NoSuchMethodError` y la app se cerraba al grabar una nota de voz. Es la tarea T2 del plan de §12.

**Cambios:**

- `GrabadoraMediaRecorder.kt`: la construcción del recorder sale a un método privado `nuevoRecorder()` que devuelve `MediaRecorder(context)` desde API 31 (`Build.VERSION_CODES.S`) y cae al constructor sin contexto, con `@Suppress("DEPRECATION")`, en los equipos anteriores. Se documenta en KDoc por qué existe el gate y se referencia el hallazgo B2.
- `GrabadoraMediaRecorder.kt`: el `catch (_: Exception)` de `iniciar()` pasa a `catch (_: Throwable)`. El motivo queda comentado: un constructor ausente lanza `NoSuchMethodError`, que es `Error` y no `Exception`, así que el `catch` anterior no lo atrapaba y la app moría en lugar de devolver `false`.
- `GrabadoraMediaRecorder.kt`: se añade el import de `android.os.Build`.
- No se tocó nada más del ciclo de vida del recorder: el hallazgo E5 (sin `setOnErrorListener`, sin observador de ciclo de vida, sin `onCleared`, sin servicio en primer plano) queda fuera del alcance acordado y sigue abierto.

**Verificación:**

- `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, 71 pruebas en verde.
- `./gradlew lintDebug` → el error `NewApi` **desaparece** del reporte (141 → 139 errores; los otros dos que bajaron son `NewApi` y `CoarseFineLocation`, este último por el `ACCESS_COARSE_LOCATION` que ya estaba declarado en el manifest). Los 139 restantes son 138 `MissingTranslation` y 1 `MissingPermission`.
- Sin verificar: que grabar una nota de voz en un equipo real con Android 9 ya no cierre la app. No hubo dispositivo disponible; un test unitario del gate exigiría Robolectric, que no está en el proyecto.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/local/media/GrabadoraMediaRecorder.kt`
- `CHANGELOG.md`

## 2026:09:21 17:08

**Resumen:** Se registró en `docs/PLAN.md` la sección §12 con el plan aprobado para atender los cinco hallazgos críticos de `docs/AUDIT.md` (B1, B2, B3, S1, S2). Es la tarea T1 del plan: documento únicamente, sin cambios de código.

**Cambios:**

- `docs/PLAN.md`: nueva sección `## 12. Atención de hallazgos críticos de la auditoría (2026-09-21)` con seis subsecciones — los cinco hallazgos y por qué son críticos (§12.1), el alcance acordado con el usuario (§12.2), las tres decisiones de diseño D1-D3 (§12.3), las once tareas con estado y dependencias (§12.4), el fuera de alcance citando los 90 hallazgos que siguen abiertos por ID (§12.5) y el aviso de riesgo para Producto (§12.6).
- Se documenta D1: B1 se arregla sustituyendo el `Task<T>.esperar()` propio por `await()` de `kotlinx-coroutines-play-services`, con la consecuencia aceptada de que la red de regresión pasa a ser el test de integración contra el emulador en lugar de un test unitario barato.
- Se documenta D2: los tests de `work-testing` que se topan con C4 y E3 se escriben como tests de caracterización que citan el ID de auditoría, sin `@Ignore`, para que fallen conscientemente el día que esos defectos se arreglen.
- Se documenta D3: la corrección de `CerrarReporteTest` y `SubidaMedioProcesoTest` es para que dejen de afirmar comportamiento inexistente en producción, no para ocultar un fallo.
- §12.6 deja por escrito que T6 hace funcionar las subidas por primera vez y que en ese momento C1, C2 y C4 empiezan a producir datos incorrectos en Firestore (`sha256` nulo no rellenable, contadores desfasados, reintentos que cancelan subidas en vuelo), para que Producto lo sepa antes de entregar a los 37 custodios.

**Archivos modificados:**

- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 16:25

**Resumen:** Se levantó una auditoría técnica y de experiencia de uso de toda la app y se documentó en `docs/AUDIT.md`. No se modificó código de producción: el documento solo registra hallazgos, con severidad, `ruta:línea` y estado de verificación. El hallazgo principal es que el pipeline de subida no puede funcionar contra Firebase real, porque el helper `Task<T>.esperar()` lanza excepción ante una `Task<Void>` exitosa (que devuelve `getResult() == null`), de modo que toda escritura a Firestore falla y ningún medio llega a subir.

**Cambios:**

- Se creó `docs/AUDIT.md` con 70 hallazgos agrupados en 11 secciones: bloqueantes, pérdida de evidencia, consistencia de datos, seguridad y privacidad, rendimiento contra el estándar de 5 segundos, fricción de uso en campo, accesibilidad y contraste, build y proceso, cobertura de pruebas, lo que está bien y no conviene tocar, y un orden de atención sugerido en 7 grupos.
- Se distinguen tres bloqueantes de entrega: el helper `esperar()` con `Task<Void>`; `MediaRecorder(context)` usado con `minSdk 26` cuando el constructor es de API 31 (crash garantizado en Android 8-11, con el `NoSuchMethodError` escapando del `catch (_: Exception)`); y `lintVitalRelease` pasando en verde pese a los 141 errores que reporta `lintDebug`, 138 de ellos ruido de `MissingTranslation` provocado por la carpeta redundante `res/values-es-rMX/`.
- Se documentan los ratios WCAG calculados sobre los hex reales de `ui/theme/Color.kt`, con los dos patrones de fondo: `colorScheme.outline` usado como color de texto en 8 sitios, y la receta de chips y `FranjaAviso` fallando en tema claro donde el oscuro pasa.
- Se registran también las limitaciones de la auditoría: no se ejecutó la app en dispositivo ni emulador Android, no se corrió `firebase && npm test`, no se midió nada con instrumentación, y no se comparó ninguna pantalla contra los `screen.png` de los mockups.
- `app/src/main/AndroidManifest.xml` quedó con los cambios que hizo el autor del proyecto durante la auditoría (se declara `ACCESS_COARSE_LOCATION`, `screenOrientation` pasa de `portrait` a `fullSensor` y se retira el `android:label` redundante de `MainActivity`). El documento incorpora ese delta: S8 queda medio resuelto (falta pedir el permiso en runtime), A9 sube de riesgo latente en tablets a comportamiento activo en todos los equipos —lo que vuelve obligatorios el `verticalScroll` de cuatro pantallas y el `rememberSaveable` del formulario de cierre— y el punto de `RedundantLabel` en T15 queda cerrado.

**Archivos modificados:**

- `docs/AUDIT.md`
- `CHANGELOG.md`

## 2026:09:21 15:50

**Resumen:** La app queda bloqueada en orientación Portrait todo el tiempo: la cámara, la grabadora y las hojas de etiquetado se diseñaron para lectura vertical, así que se fija `screenOrientation="portrait"` en la única Activity.

**Cambios:**

- `AndroidManifest.xml`: `MainActivity` gana `android:screenOrientation="portrait"`.

**Archivos modificados:**

- `app/src/main/AndroidManifest.xml`
- `CHANGELOG.md`

## 2026:09:21 15:44

**Resumen:** El etiquetado ya no se puede descartar sin elegir al menos una etiqueta: la hoja bloquea el cierre por los tres caminos (botón X, toque fuera de la hoja, gesto de baja y botón atrás) mientras no haya un chip seleccionado, de modo que la foto y la nota de voz recién capturadas siempre quedan etiquetadas y pasan a la cola de subida.

**Cambios:**

- `HojaEtiqueta.kt`: el `confirmValueChange` del estado de la hoja devuelve `false` para el cambio a `Hidden` mientras `seleccionada == null` (frena el gesto de baja); `onDismissRequest` se ignora sin chip elegido (bloquea toque fuera de la hoja y back); y el `IconButton` de cerrar se deshabilita (`enabled = seleccionada != null`) igual que el botón de guardar. El descarte sigue disponible una vez elegida la etiqueta.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/captura/HojaEtiqueta.kt`
- `CHANGELOG.md`

## 2026:09:21 15:37

**Resumen:** Se corrige la hoja de etiquetado (foto y nota de voz) que abría parcialmente expandida y dejaba el botón de guardar fuera de pantalla, obligando a hacer scroll.

**Cambios:**

- `HojaEtiqueta.kt`: la `ModalBottomSheet` pasa a `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, igual que la hoja canónica `HojaInferior`, con lo que se abre totalmente expandida y el botón de guardar queda siempre visible sin necesidad de desplazarse.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/captura/HojaEtiqueta.kt`
- `CHANGELOG.md`

## 2026:09:21 15:31

**Resumen:** Se corrige el desborde de la pantalla de evidencias capturadas (rejilla de Archivos) sobre los botones OS de navegación: el contenido no recibía el inset inferior porque la ruta no tiene barra inferior (`BarraNavegacion` solo se compone en las tres pestañas) y `contentWindowInsets = WindowInsets(0.dp)` deja el inset en cero.

**Cambios:**

- `AppNavHost.kt`: la ruta `archivos` ahora aplica `Modifier.navigationBarsPadding()` además del `statusBarsPadding()`, con lo que la última fila de la rejilla queda por encima de los botones OS / gesto de navegación del sistema.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `CHANGELOG.md`

## 2026:09:21 16:30

**Resumen:** Se habilitó la prueba unitaria de `CapturaViewModel`: se extrajeron interfaces para los colaboradores atados a Android y se agregó `kotlinx-coroutines-test`, con tres pruebas que fijan el comportamiento del desacople de GPS.

**Cambios:**

- Se extrajeron interfaces para poder falsear los colaboradores en pruebas de JVM: `SubidaScheduler` (interfaz + impl `WorkManagerSubidaScheduler`, conservando las constantes del companion que usan los workers), `Grabadora` (interfaz + impl `GrabadoraMediaRecorder`) y `FotosPublicas` (interfaz + impl `FotosPublicasMediaStore`). Los proveedores de Hilt (`CapturaModule`, `PipelineModule`) ahora construyen las implementaciones y devuelven las interfaces; `CapturaViewModel`/`PendientesViewModel` no cambiaron (mismos nombres y paquetes).
- `CapturaViewModelTest` (nuevo): la captura de foto abre la hoja de etiquetado sin esperar el GPS; la subida se encola **solo** después de que el GPS se adjunta al medio (verifica el `join` previo a `encolarSubidaMedio`); y la nota de voz tampoco bloquea la captura por el GPS.
- Andamiaje de pruebas: `MainDispatcherRule` (fija `Dispatchers.Main` con un `StandardTestDispatcher`) y `CapturaFakes` (`FakeSesionRepositorio`, `FakeProveedorUbicacion` con compuerta `CompletableDeferred`, `FakeFotosPublicas`, `FakeGrabadora`, `FakeSubidaScheduler` que registra los encolados).
- `gradle/libs.versions.toml` y `app/build.gradle.kts`: se agregó `kotlinx-coroutines-test` (1.10.2, alineado con el coroutines-core resuelto) como `testImplementation`.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/worker/SubidaScheduler.kt`
- `app/src/main/java/com/ai27/app/custodio/data/worker/WorkManagerSubidaScheduler.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/media/Grabadora.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/media/GrabadoraMediaRecorder.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/media/FotosPublicas.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/media/FotosPublicasMediaStore.kt`
- `app/src/main/java/com/ai27/app/custodio/di/CapturaModule.kt`
- `app/src/main/java/com/ai27/app/custodio/di/PipelineModule.kt`
- `app/src/test/java/com/ai27/app/custodio/ui/captura/CapturaViewModelTest.kt`
- `app/src/test/java/com/ai27/app/custodio/MainDispatcherRule.kt`
- `app/src/test/java/com/ai27/app/custodio/CapturaFakes.kt`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

## 2026:09:21 15:40

**Resumen:** Se agregaron pruebas de `RoomMedioRepository.actualizarGps` que cubren el corazón del cambio de captura: adjuntar la ubicación tardía a un medio pendiente y no mutarla una vez que la subida arrancó.

**Cambios:**

- `RoomMedioRepositoryTest`: nueva prueba que verifica que `actualizarGps` pega lat/lng/accuracy/age a un medio `PENDIENTE` (y persiste en la fila), y otra que verifica que, con el medio en `EN_PROGRESO`, `actualizarGps` no sobrescribe la ubicación (respeta la restricción a `PENDIENTE` de la query).

**Archivos modificados:**

- `app/src/test/java/com/ai27/app/custodio/RoomMedioRepositoryTest.kt`

## 2026:09:21 15:10

**Resumen:** La captura ya no se bloquea esperando el arreglo de GPS: foto y nota de voz se guardan al instante y la hoja de etiquetado aparece de inmediato; la ubicación se adjunta en segundo plano y se espera solo antes de encolar la subida, de modo que el documento remoto sigue llevando GPS.

**Cambios:**

- `CapturaViewModel`: `capturarFoto` y `detenerGrabacionDeAudio` persisten el medio con `gps = null` y lanzan `adjuntarGpsEnSegundoPlano(medioId)`, que resuelve el GPS (con su timeout de 8s) y lo pega a Room sin bloquear la UI. `guardarEtiqueta` espera (`trabajosGps.remove(medioId)?.join()`) a que ese trabajo termine antes de `encolarSubidaMedio`, para que la subida remota conserve la ubicación. `descartarEtiquetado` suelta la referencia del trabajo de GPS.
- `MedioDao.actualizarGps`: nuevo `UPDATE` de las columnas `gps*` restringido a medios `PENDIENTE` (una vez `EN_PROGRESO`/`COMPLETA` el documento remoto ya existe y no debe mutar).
- `MedioRepository`/`RoomMedioRepository`: nuevo método `actualizarGps(medioId, gps)`.
- `FakeMedioRepository` (test): implementación de `actualizarGps` que refleja la restricción a `PENDIENTE`.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/db/MedioDao.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/MedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`

## 2026:09:21 14:32

**Resumen:** El "flash" del obturador ahora se dispara cuando la captura tiene éxito (dentro del callback de `tomarFoto`) en lugar de al tocar el botón, para confirmar la foto real.

**Cambios:**

- Se movió el lanzamiento del velo negro desde el `onClick` del disparador al callback de éxito de `tomarFoto`, de modo que el flash aparece cuando el JPEG ya está listo.
- Se actualizó el comentario de la animación del flash para reflejar el nuevo punto de disparo.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`

## 2026:09:21 14:20

**Resumen:** La captura de foto se siente más rápida y da retroalimentación visible: el procesamiento de la imagen sale del hilo principal y al disparar aparece un "flash" negro que se desvanece.

**Cambios:**

- Se movió la rotación y compresión JPEG (original + miniatura) fuera del hilo principal: `tomarFoto` ahora recibe un ejecutor respaldado por `Dispatchers.Default` en lugar de `context.mainExecutor`, evitando que el visor se congele en el momento del disparo.
- Se agregó un velo negro a pantalla completa (`Animatable` con `tween` de 280ms) que se pinta al tomar la foto, para que sea evidente que la captura ocurrió aunque el guardado y la ubicación tarden un instante más.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`

## 2026:09:21 13:54

**Resumen:** El aviso flotante de subida aterriza 20dp más abajo: el despegue desde la barra superior pasa de 80dp a 100dp en captura y audio.

**Cambios:**

- `CapturaEvidenciaScreen.kt`: `padding(top)` del aviso de 80dp a 100dp.
- `AudioScreen.kt`: `padding(top)` del aviso de 80dp a 100dp.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/AudioScreen.kt`
- `CHANGELOG.md`

## 2026:09:21 13:52

**Resumen:** El aviso flotante de subida que aparece al guardar la etiqueta ahora entra por la parte superior de la pantalla (en vez de flotar sobre el fondo): se ancla arriba, debajo de la barra superior, y desliza desde arriba hacia abajo.

**Cambios:**

- `Avisos.kt`: la entrada de `AvisoSubidaFlotante` pasa a `slideInVertically { -it / 2 }` (desliza desde arriba).
- `CapturaEvidenciaScreen.kt`: el aviso se ancla en `Alignment.TopCenter` con `padding(top = 80.dp)` debajo de la barra superior de la cámara.
- `AudioScreen.kt`: el aviso se ancla en `Alignment.TopCenter` con `padding(top = 80.dp)`; se elimina el import `navigationBarsPadding` que quedó sin uso.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/components/Avisos.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/AudioScreen.kt`
- `CHANGELOG.md`

## 2026:09:21 13:47

**Resumen:** Se agrega el segundo aviso verde de subida: además del que aparece al capturar (dentro de la hoja de etiquetado), al guardar la etiqueta se muestra un aviso flotante transitorio "Tu archivo se subirá en breve" sobre la pantalla de captura o de audio — justo cuando `guardarEtiqueta` encola la subida del medio y la hoja se cierra.

**Cambios:**

- `CapturaUiState.kt`: nuevo campo evento `avisoEncolado: Int` (contador que crece en cada etiqueta guardada, para no perder avisos consecutivos).
- `CapturaViewModel.kt`: `guardarEtiqueta` en éxito incrementa `avisoEncolado` junto con la cola de subida (`encolarSubidaMedio`).
- `Avisos.kt`: nuevo composable `AvisoSubidaFlotante(evento, texto, modifier)` — muestra la píldora `AvisoSubida` cuando `evento` cambia, la oculta solo a los `AVISO_SUBIDA_DURACION_MS` (3 s) y recuerda el último evento visto con `rememberSaveable` para no re-aparecer al volver a la pantalla; constante `AVISO_SUBIDA_DURACION_MS` compartida con la hoja; previews Claro/Oscuro.
- `CapturaEvidenciaScreen.kt`: `AvisoSubidaFlotante` anclado bajo la barra superior de la cámara, arriba del disparador.
- `AudioScreen.kt`: raíz envuelta en `Box` y `AvisoSubidaFlotante` anclado al fondo sobre la barra de navegación.
- `HojaEtiqueta.kt`: usa la constante compartida `AVISO_SUBIDA_DURACION_MS` (se elimina la privada).

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaUiState.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/components/Avisos.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/AudioScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/HojaEtiqueta.kt`
- `CHANGELOG.md`

## 2026:09:21 13:39

**Resumen:** Al tomar una foto o terminar una nota de voz, la hoja de etiquetado abre con un aviso verde transitorio ("Tu archivo se subirá en breve") que confirma que el archivo recién capturado quedó en la cola de subida. La hoja aparece exactamente en ese momento para ambos flujos (foto y audio), así que el aviso se muestra ahí —en la misma ventana de la hoja, garantizando que no quede tapado por ella— y se desvanece solo a los 3 segundos.

**Cambios:**

- `Avisos.kt`: nuevo composable `AvisoSubida`, píldora verde que reutiliza la receta "enviado" de `AppTheme.coloresEstado` (relleno al 12%, borde al 25%, tinta a plena intensidad) con el icono de cheque; previews Claro/Oscuro.
- `HojaEtiqueta.kt`: al abrir la hoja se muestra `AvisoSubida` con `AnimatedVisibility` (fade + expand/shrink) y se oculta solo tras `AVISO_SUBIDA_DURACION_MS = 3s`; cubre por igual la foto capturada y la nota de voz terminada.
- `strings.xml`: nueva cadena `aviso_subida_en_breve = "Tu archivo se subirá en breve"`.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/components/Avisos.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/HojaEtiqueta.kt`
- `app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

## 2026:09:21 13:34

**Resumen:** La barra superior del Historial pierde la flecha de regreso (es una pestaña de la barra inferior, no una ruta apilada), y el título de `BarraSuperior` gana una altura mínima de 48dp para que las barras sin botón de regreso (Historial y Ajustes) conserven el mismo alto y el mismo padding vertical que las que sí lo tienen, p. ej. Nota de Voz o Captura de evidencia.

**Cambios:**

- `BarraSuperior.kt`: el `Text` del título pasa a `Modifier.weight(1f).heightIn(min = 48.dp)`, igualando el hueco vertical que deja el `IconButton` de regreso: sin él la barra ya no encoge a la altura del texto y el alto/padding vertical queda idéntico al de las barras con botón.
- `HistorialScreen.kt`: se quita `onAtras` de `HistorialRoute`/`HistorialScreen` y la `BarraSuperior` queda sin flecha de regreso (`BarraSuperior(titulo = ...)`).
- `AppNavHost.kt`: la ruta `HISTORIAL` deja de pasar `onAtras` a `HistorialRoute`.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/components/BarraSuperior.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/historial/HistorialScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `CHANGELOG.md`

## 2026:09:21 13:15

**Resumen:** Se corrige el resumen del cierre de viaje, que mostraba siempre 0 fotografías y 0 notas de voz sin importar cuánta evidencia se hubiera capturado. La pantalla de cierre leía los conteos de `reporte.resumenMedios`, un campo que solo se calcula y persiste en el momento de cerrar (`CerrarReporte`), así que durante el viaje activo quedaba en 0/0. Ahora el recuento se calcula en vivo desde la lista real de medios del viaje (`estado.medios`), con la misma lógica que usa `CerrarReporte` al cerrar.

**Cambios:**

- `CierreScreen.kt`: el resumen de fotografías, notas de voz y duración total ya no sale de `estado.reporte?.resumenMedios` (0/0 durante el viaje activo) sino que se calcula con `ResumenMedios` sobre `estado.medios` — `fotos = count { tipo == FOTO }`, `audios = count { tipo == AUDIO }` y `durationMsTotal = sumOf { durationMs }` — coincidiendo exactamente con `CerrarReporte`, de modo que lo que se ve en pantalla es lo mismo que queda grabado en el reporte al cerrar.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CierreScreen.kt`
- `CHANGELOG.md`

## 2026:09:21 13:06

**Resumen:** Las fotos tomadas por la app ahora también se guardan en la memoria del dispositivo, en la carpeta pública `Pictures/AppCustodio/` visible desde el administrador de archivos, además del almacenamiento interno (`filesDir/medios`) que ya alimenta la subida a Firebase.

**Cambios:**

- `FotosPublicas.kt` (nuevo): guarda una copia JPEG pública por foto capturada — vía MediaStore con `RELATIVE_PATH = Pictures/AppCustodio` e `IS_PENDING`/`update` en Android 10+ (sin permisos) y vía `getExternalStoragePublicDirectory` más `MediaScannerConnection.scanFile` en Android 8-9 (requiere `WRITE_EXTERNAL_STORAGE`).
- `CapturaViewModel.kt`: se inyecta `FotosPublicas` y, tras guardar el medio con `capturarMedio`, se lanza `copiarAPublica(medioId, jpeg)`; la copia es accesoria — si falla se registra en LogCat (`Tag "CapturaViewModel"`) sin bloquear la captura ni marcar error al usuario.
- `CapturaModule.kt`: `provéeFotosPublicas` (singleton con `@ApplicationContext`).
- `AndroidManifest.xml`: permiso `WRITE_EXTERNAL_STORAGE` con `maxSdkVersion="28"` (Android 8-9), sin efecto en Android 10+.
- `CapturaEvidenciaScreen.kt`: la solicitud de permisos (arranque y botón "Permitir cámara") incluye `WRITE_EXTERNAL_STORAGE` solo en Android 8-9 vía `permisosSolicitados()`.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/data/local/media/FotosPublicas.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/di/CapturaModule.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `CHANGELOG.md`

## 2026:09:21 13:05

**Resumen:** Se reestructuran las reglas de seguridad de Firestore y Storage para el modelo de dos roles: los custodios (la app) escriben y leen solo lo suyo, y los analistas (panel web, separado) leen TODO, incluida la evidencia en Storage, y no escriben. El analista se identifica ahora por CORREO en `config/analistas.correos` (antes por UIDs), y las escrituras de custodio dejaron de consultar ese documento: subir evidencia no debe depender de la lista de analistas ni romperse en un emulador recién iniciado (quirk que revienta un `get()` interno de reglas sobre documento inexistente). El panel gana lectura de Storage para visualizar los archivos.

**Cambios:**

- `firestore.rules`: `esAnalista()` lee `config/analistas.correos` y compara con `request.auth.token.email`; se elimina `!esAnalista()` de las escrituras de `reportes`/`medios` (basta dueño + ciclo/transiciones válidas; ver nota de diseño); se documenta en `match /config` la provisión de analistas por consola/REST.
- `storage.rules`: `read` autoriza al analista (por correo) además del dueño del reporte; `create`/`update` siguen siendo solo del dueño; `delete` negado.
- `firebase/test/reglas.test.js`: `config/analistas` se siembra con `correos`; contexto de Storage del analista; nuevos chequeos "el analista lee el archivo del reporte de alicia" y "el analista no sube a storage"; la aserción del analista escribiendo se reescribe como "no inyecta un reporte de otra persona". Todas las verificaciones pasan con `cd firebase && npm test`.
- `README.md`: nueva sección "Alta de analistas (panel web, solo lectura)" con los pasos de consola y del emulador.
- `AGENTS.md`: se amplía la nota del quirk del Emulator Suite con el `get()` interno de reglas sobre documento inexistente y la lista de analistas por correo.

**Archivos modificados:**

- `firebase/firestore.rules`
- `firebase/storage.rules`
- `firebase/test/reglas.test.js`
- `README.md`
- `AGENTS.md`
- `CHANGELOG.md`

## 2026:09:21 12:30

**Resumen:** Se corrige la flecha de regreso de `BarraSuperior`: apuntaba a la derecha (`ic_chevron`) cuando la acción es volver, así que ahora usa un chevron izquierdo nuevo (`ic_chevron_atras`). Los chevrones hacia la derecha que indican navegación hacia adentro (filas de Historial, pendientes y la celda de evidencias de Reporte) se conservan intactos.

**Cambios:**

- `res/drawable/ic_chevron_atras.xml` (nuevo): chevron de trazo que apunta a la izquierda (path `M15,18l-6,-6l6,-6`), mismo peso/tamaño que `ic_chevron`.
- `BarraSuperior.kt`: el botón de regreso usa `ic_chevron_atras` en lugar de `ic_chevron`.

**Archivos modificados:**

- `app/src/main/res/drawable/ic_chevron_atras.xml` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/components/BarraSuperior.kt`
- `CHANGELOG.md`

## 2026:09:21 12:24

**Resumen:** Se corrige el botón de foto de la pantalla de captura de evidencia, que quedaba debajo de los botones OS de navegación: el disparador está anclado al borde inferior del área (`Alignment.BottomCenter`) y el `Scaffold` usa `contentWindowInsets = WindowInsets(0.dp)`, así que la app dibuja tras la barra de navegación del sistema y el botón se salía del área visible.

**Cambios:**

- `CapturaEvidenciaScreen.kt`: el `Surface` del disparador gana `Modifier.navigationBarsPadding()` entre el `align(BottomCenter)` y el `padding(bottom = 32.dp)`, de modo que el botón queda por encima de los botones OS manteniendo el margen de diseño.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt`
- `CHANGELOG.md`

## 2026:09:21 12:15

**Resumen:** Se agrega la posibilidad de refrescar la pantalla de Pendientes para ver los cambios de la cola de subida al enviar una foto o nota de voz: gesto de tirar hacia abajo (pull-to-refresh) y re-tocar la pestaña Reporte de la barra inferior. Ambos disparan la reconciliación de la cola (`SubidaScheduler.reconciliar()`), que re-encola los trabajos de subida pendientes; el `Flow` reactivo de Room re-emite entonces los estados actualizados (PENDIENTE → EN_PROGRESO → COMPLETA/FALLIDA) y los medios recién enviados aparecen en la lista.

**Cambios:**

- `PendientesViewModel.kt`: nuevo `refrescando: StateFlow<Boolean>` y `recargar()` que marca el estado de refresco, ejecuta `programadorSubidas.reconciliar()` y lo libera con `finally`.
- `PendientesScreen.kt`: el contenido (lista o estado vacío) se envuelve en `PullToRefreshBox` de Material3 (estable en material3 1.4.0); el estado vacío es scrollable con `verticalScroll` para que el gesto funcione también sin elementos. Nuevos parámetros `refrescando` y `onRecargar`, con los 4 `@Preview` actualizados.
- `AppNavHost.kt`: se cablea `refrescando`/`onRecargar` a `PendientesScreen`; `navegarADestino` gana `alReTocar`, que en Reporte (pestaña ya seleccionada) ejecuta `PendientesViewModel.recargar()` en vez de no hacer nada.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/pendientes/PendientesViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/pendientes/PendientesScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `CHANGELOG.md`

## 2026:09:21 12:00

**Resumen:** Se corrige el badge de pendientes de la barra de navegación inferior (`BarraNavegacion`): el número del contador sobre el icono de Reporte no se mostraba correctamente porque el badge artesanal se empujaba fuera de su contenedor (quedaba recortado/encimado con el borde de la barra) y el dígito iba en una ventana de 14dp con fuente por defecto, que se recortaba a escalas de fuente normales del dispositivo.

**Cambios:**

- `BarraNavegacion.kt`: el badge de pendientes del icono de Reporte reutiliza el componente canónico `BadgePendientes` (`Botones.kt`, con `TabularNumberStyle` + `labelSmall`) en lugar del `Text` artesanal de 10sp, y se coloca **dentro** del contenedor del icono en `Alignment.TopEnd` con `padding(top = 3.dp, end = 4.dp)` — sin `offset` negativo hacia el borde de la barra, igual que hace `BotonPendientes` — para que el número se vea completo.
- Se eliminan los imports huérfanos (`offset`, `CircleShape`, `TextAlign`, `sp`).

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/components/BarraNavegacion.kt`
- `CHANGELOG.md`

## 2026:09:21 11:50

**Resumen:** Se corrige la barra superior de Historial (y Detalle) que se solapaba con la barra de notificaciones del teléfono al estar activo `enableEdgeToEdge()`: las rutas `historial` y `detalle` eran las únicas autenticadas sin `statusBarsPadding()` en `AppNavHost`.

**Cambios:**

- `AppNavHost.kt`: las rutas `historial` y `detalle` ahora aplican `Modifier.fillMaxSize().statusBarsPadding()`, igual que las demás pantallas autenticadas.
- `HistorialRoute`/`HistorialScreen` y `DetalleReporteRoute`/`DetalleReporteScreen`: nuevas `modifier: Modifier = Modifier` que se aplican al `Column` raíz, siguiendo el patrón de `AjustesScreen`/`PendientesScreen`.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/historial/HistorialScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/historial/DetalleReporteScreen.kt`
- `CHANGELOG.md`

## 2026:09:21 D4

**Resumen:** Se resuelve la decisión D4 del plan: las cinco pantallas fuera de familia de la ronda anterior (`*_reparado`) quedan confirmadas como pantallas/estados Nocturne adaptados en C4, y las dos variantes que no tenían preview ganan una en claro y oscuro. Se cierra la última decisión pendiente de §11.2.

**Cambios:**

- `docs/PLAN.md` §11.2: D4 pasa de "Pendiente al implementar" a "Resuelta (2026-09-21)" con el mapeo de cada mockup a su implementación.
- `ReporteScreen.kt`: previews "Abierto sin conexión · Claro/Oscuro" (`conectado = false`) para la variante `reporte_abierto_sin_conexi_n_reparado`.
- `AjustesScreen.kt`: previews "Cierre deshabilitado · Claro/Oscuro" (`pendientesSinSubir = 3`) para la variante `ajustes_cierre_de_sesi_n_deshabilitado_reparado`.
- Los otros tres mockups ya estaban cubiertos: `ajustes_*` (Ajustes normal), `cerrar_reporte_estructura` (CierreScreen) y `estado_vac_o_base` (`EstadoVacio` en la biblioteca y en `SinReporte`/pendientes vacío).

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/viaje/ReporteScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/ajustes/AjustesScreen.kt`
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 Saneo del plan

**Resumen:** Se cierra el bloque de reproducción del diseño en `docs/PLAN.md`: las fases C4 y C5 pasan a marcarse como hechas (ya estaban completadas en los commits de C3-C5) y el diagrama de orden de §11.11 queda con las siete fases C ✅.

**Cambios:**

- §11.6 Fase C4 marcada como ✅ hecha (2026-09-20) con nota de cierre: Ajustes (tema nocturno persistente y cierre de sesión bloqueado con pendientes), sesión fuera de Reporte, novedad/comentario sincronizados hasta Room/Firestore, Historial/Detalle sin `Scaffold` anidado.
- §11.7 Fase C5 marcada como ✅ hecha (2026-09-20); su nota de cierre ya existía junto a la lista de estados pendientes.
- §11.11 diagrama de orden actualizado con ✅ en las siete fases C.

**Archivos modificados:**

- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 C7

**Resumen:** Fase C7 (verificación): todas las pantallas ganan `@Preview` en claro y oscuro sobre el lienzo `Vitrina`, con datos de demostración aislados en `ui/previews/`. Cierra el bloque de reproducción del diseño (C1-C7): componentes, pantallas, strings y accesibilidad.

**Cambios:**

- Previews de pantallas (claro + oscuro cada una): `LoginScreen`, `ReporteScreen` (viaje abierto y sin viaje), `CapturaEvidenciaScreen`, `AudioScreen` (grabando), `ArchivosScreen` (rejilla con PENDIENTE/FALLIDA/COMPLETA), `CierreScreen` (con pendientes), `HistorialScreen` (cerrado + descartado), `DetalleReporteScreen`, `PendientesScreen` (cola y vacío) y `AjustesScreen`. Previews nuevas también en `Dialogos.kt` (los 4 diálogos) y `HojaEtiqueta`.
- Nuevo `ui/previews/DatosDemo.kt`: entidades de dominio ficticias (`Reporte` activo/cerrado/descartado, `Medio` de la rejilla y de la cola, `Etiqueta`, `Custodio`) que solo usan las previews; ningún ViewModel las produce.
- Para que la preview de captura sea fiel al mockup de "visor negro", `CapturaEvidenciaScreen` detecta el modo de inspección (`LocalInspectionMode`): pinta un visor negro simulado y no pide permisos; en ejecución real no cambia nada.
- `docs/PLAN.md` §11.9 marcada como hecha. Pendiente manual en dispositivo real: comparar cada preview contra su `screen.png` en Android Studio, escala de texto 130%/200% y revisión a pleno sol y de noche.

**Archivos modificados:**

- `app/src/main/java/com/ai27/app/custodio/ui/previews/DatosDemo.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/{login,viaje,historial,pendientes,ajustes,captura}/` (previews por pantalla)
- `app/src/main/java/com/ai27/app/custodio/ui/components/Dialogos.kt` (previews de los 4 diálogos)
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/CapturaEvidenciaScreen.kt` (visor negro en modo inspección)
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 C6

**Resumen:** Fase C6: todos los strings de la UI salen a `values/strings.xml` (incluidos los `contentDescription`), los errores de dominio pasan de `String?` a recursos `@StringRes` en los UiState, y se corrigen los tres puntos de accesibilidad detectados en la auditoría (mínimos táctiles, chips de ciclo sólo-color y estados de grabación sin descripción).

**Cambios:**

- `values/strings.xml` reescrito completo: strings por feature (login, errores de dominio y de validación, diálogos, viaje, captura, audio, archivos, cierre, hoja de etiquetado, historial, detalle, pendientes, ajustes), comunes (`comun_cancelar`, `comun_reintentar`, `comun_aviso`, `comun_entendido`, `comun_miniatura`, `foto`, `nota_de_voz`, `evidencia`, `archivos_con_conteo`, `conteo_de_50`, `cerrar_viaje`) y 4 plurals (`archivos_sin_subir`, `archivos_pendientes_de_subida`, `evidencias_capturadas`, `cantidad_archivos`). Se corrige el typo `reporte_penculadas` → `reporte_pendientes`.
- Nuevo `ui/error/DominioErrorRecurso.kt`: `DominioError.aRecurso(): Int` (`@StringRes`) y los UiState pasan `error: String?` → `error: Int?`; las pantallas resuelven con `stringResource(error)`. Se elimina `domain/error/DominioErrorMensaje.kt` (`aMensajeUsuario`) sin quedar referencias. `PlacaVacia` se mapea al mensaje estático `error_placa_vacia_sin_valor`.
- Migradas a `stringResource`/`pluralStringResource` todas las pantallas y sus ViewModels: `LoginScreen`, `ReporteScreen`, `CapturaEvidenciaScreen` (contentDescription «Tomar foto»), `AudioScreen`, `ArchivosScreen`, `CierreScreen`, `HojaEtiqueta`, `HistorialScreen`, `DetalleReporteScreen`, `PendientesScreen`, `AjustesScreen`, `Dialogos.kt`, `CajasEstado.kt` y los ViewModels de login, captura, historial, detalle, pendientes y ajustes.
- Accesibilidad: se quita la pinza `Modifier.size(28.dp)` de los `IconButton`/`FilledIconButton` de Archivos y la `size(40.dp)` de CampoPlaca, que encogían el área interactiva por debajo del mínimo táctil de 48dp. `ChipCiclo` deja de ser un estado sólo-color: gana icono (cheque/alerta/reloj) como `ChipEstadoSubida`. `BotonGrabacion` anuncia con TalkBack "Grabar nota de voz" en reposo y "Detener grabación" mientras graba (el string `boton_grabacion_detener` ya no queda huérfano).
- `docs/PLAN.md` §11.8 marcada como hecha; queda pendiente manual de C7 la escala de texto 130%/200% y el contraste en dispositivo real.

**Archivos modificados:**

- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/ai27/app/custodio/ui/error/DominioErrorRecurso.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/domain/error/DominioErrorMensaje.kt` (eliminado)
- `app/src/main/java/com/ai27/app/custodio/ui/{login,viaje,historial,pendientes,ajustes}/` y `ui/components/{Dialogos,CajasEstado,Botones,CampoPlaca}.kt`
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:21 23:40

**Resumen:** Se documentó la resolución de captura como decisión abierta y se corrigieron las cifras de volumen del plan, que estaban basadas en un presupuesto de imagen que el código nunca implementó.

**Cambios:**

- Se agregó la sección 3.4 al plan: la resolución de captura queda como decisión pendiente de Producto. El código captura 1920×1080 en 16:9 con calidad 88, es decir unos 2 megapíxeles, y el original no se reescala. Al levantar el propósito del panel del analista quedó claro que la evidencia que el cliente necesita no es la foto como objeto sino su contenido, un número de candado o un folio, así que la compresión dejó de ser un asunto de datos celulares y pasó a ser el asunto del producto. A esa resolución un candado fotografiado a dos metros no se lee, y el formato 16:9 desperdicia la mitad del encuadre en documentos verticales.
- No se cambió ninguna constante: la premisa de las decisiones D3 y R11 cambió, pero la decisión es de Producto. Se documentó que es un cambio de dos constantes y el riesgo de no tomarlo, que es entregar un sistema que captura evidencia ilegible y descubrirlo cuando alguien intente usarla con un cliente.
- Se corrigieron las cifras que el plan traía de una versión anterior: decía fotos de aproximadamente un megapíxel a calidad 75, que el código nunca implementó. Con los valores reales, el peso por reporte sube de 11 a 26 megabytes, el consumo diario de 400 a 960 megabytes y el mensual de 12 a 29 gigabytes.
- Se actualizó la proyección de almacenamiento anual de 145 a 350 gigabytes, con la nota de que se duplicaría si se adopta la resolución propuesta. Es la cifra que va a forzar la conversación de retención, que sigue pendiente.
- Se ajustaron las menciones a la resolución en la tabla de decisiones bloqueadas, en el resumen de cambios respecto de la versión anterior y en la tabla de trazabilidad.

**Archivos modificados:**

- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:20 10:37

**Resumen:** Fase C3: navegación con barra inferior y descomposición de `CapturaScreen.kt` (777 líneas) en cinco pantallas del flujo de viaje. Rutas de 4 a 10 + grafo anidado, `Scaffold` único en el host, rejilla a 3 columnas, pantalla de pendientes con reintento y diálogos a la biblioteca.

**Cambios:**
- Navegación: `object Rutas` salta a `login`, `viaje` (grafo anidado con `inicio`/`captura`/`audio`/`archivos`/`cierre`), `pendientes`, `historial`, `detalle/{reporteId}` y `ajustes`. Las pantallas del flujo comparten el `CapturaViewModel` de la entrada del grafo con `hiltViewModel(navController.getBackStackEntry(Rutas.VIAJE))`, así el viaje activo y el estado de captura sobreviven al cambio de pestaña.
- Host: `AppNavHost` es el único dueño del `Scaffold` (`contentWindowInsets = WindowInsets(0.dp)`), pinta `BarraNavegacion` sólo en las tres pestañas (badge de pendientes desde `PendientesViewModel`, de ámbito Activity) y aplica `statusBarsPadding()` vía el `modifier` de cada pantalla del flujo. Cambio de pestaña por `popBackStack(ruta, false)` o `navigate`; Reporte vuelve con `popBackStack(VIAJE, false)`. El gate de sesión quedó en un `LaunchedEffect`: `CON_SESION` salta a `inicio` y `NO_SESION` hace `popUpTo(VIAJE){inclusive=true}` a `login`.
- Descomposición: se elimina `ui/captura/CapturaScreen.kt` y nacen cinco pantallas en `ui/viaje/` — `ReporteScreen` (raíz: barra de sesión con correo y salida, nuevo viaje con `CampoPlaca`, unidad en curso con contra de 50, botones de captura/nota/archivos y `BotonPendientes`), `CapturaEvidenciaScreen` (cámara a pantalla completa con disparador circular de 72dp, contador en la barra y `HojaEtiqueta`; pide CAMERA/RECORD_AUDIO/ACCESS_FINE_LOCATION), `AudioScreen` (cronómetro tabular, máximo 5:00, cancelar en grabación, `HojaEtiqueta`), `ArchivosScreen` (rejilla `GridCells.Fixed(3)`, borrado sólo PENDIENTE con confirmación, chip FALLIDA con reintento y chip de etiqueta) y `CierreScreen` (resumen con `resumenMedios`, `RenglonSwitch` de novedad y comentario locales que C4 cablea, cerrar con confirmación y descartar con motivo obligatorio). Se conservan `CapturaViewModel`, `CapturaUiState`, `VistaCamara` (`tomarFoto`) y `HojaEtiqueta` en `ui/captura/`.
- Pendientes: nueva fuente de la cola global de la sesión — `MedioDao.obtenerPendientesDe(custodioUid): Flow<List<MedioEntity>>` (JOIN con reportes) y `MedioRepository.observarPendientesMedios(custodioUid): Flow<List<Medio>>` (el nombre evita colisionar por tipo de retorno en JVM) con implementación en `RoomMedioRepository` y `FakeMedioRepository`. `PendientesViewModel` (Activity-scoped) alimenta el badge de la barra y `PendientesScreen` (lista `RenglonPendiente` con reintento de fallidas, vacío "Todo sincronizado").
- Diálogos: los cinco `AlertDialog` inline salen a `ui/components/Dialogos.kt` (`DialogoConfirmacion`, `DialogoDescarte`, `DialogoCerrarSesion`, `DialogoError`); `DialogoCerrarSesion` pide confirmación con aviso de la cola como en B4.
- `AjustesScreen` existe como destino placeholder ("Próximamente"); el contenido real es C4.

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt` (reescrito)
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaScreen.kt` (eliminado)
- `app/src/main/java/com/ai27/app/custodio/ui/viaje/` (nuevo: ReporteScreen, CapturaEvidenciaScreen, AudioScreen, ArchivosScreen, CierreScreen)
- `app/src/main/java/com/ai27/app/custodio/ui/pendientes/` (nuevo: PendientesScreen, PendientesViewModel)
- `app/src/main/java/com/ai27/app/custodio/ui/ajustes/AjustesScreen.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/ui/components/Dialogos.kt` (nuevo)
- `app/src/main/java/com/ai27/app/custodio/data/local/db/MedioDao.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/MedioRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomMedioRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/FakeMedioRepository.kt`
- `docs/PLAN.md`
- `CHANGELOG.md`

**Pendiente manual:** recorrer en dispositivo el flujo completo (iniciar viaje → capturar con etiqueta → archivos en 3 columnas → cierre con novedad/comentario → ver pendientes → historial) y comparar las cinco pantallas nuevas contra los `*_reparado` y el resto de PNG de `mock-app-design-language/`.

## 2026:09:20 09:53

**Resumen:** Fase C2: biblioteca de componentes en `ui/components/`. Los 18 componentes base de los mockups portados a Compose con `@Preview` en claro y oscuro, iconografía de trazo propia en drawables, y deduplicación del chip de estado de subida.

**Cambios:**
- Se creó `ui/components/` con los 18 composables de la tabla de la fase C2: `BarraNavegacion` (tres destinos con testiga y badge de pendientes), `BarraSuperior` (flecha de regreso opcional y acciones), `BotonPrimario`/`BotonSecundario` (48dp, 4dp de radio), `BotonPendientes` (tarjeta con contador ámbar), `BotonGrabacion` (círculo de 96dp con micrófono/stop, única píldora grande del sistema), `ChipCategoria`, `ChipEstadoSubida`, `MiniaturaMedio`, `RenglonReporte`, `RenglonPendiente`, `RenglonResumen`, `RenglonSwitch`, `EncabezadoFecha`, `FranjaAviso`, `EstadoVacio`, `HojaInferior` y `CampoPlaca`.
- Cada componente se muestra en dos previews, Oscuro y Claro, montados sobre el wrapper `Vitrina` que usa el lienzo del tema en lugar del gris del studio.
- Iconografía: 12 drawables de trazo nuevos en `res/drawable/` copiados de los paths de los mockups (`ic_archivo`, `ic_reloj`, `ic_ajustes`, `ic_camara`, `ic_subir`, `ic_adjunto`, `ic_chevron`, `ic_carpeta`, `ic_placa`, `ic_alerta`, `ic_cheque`, `ic_reintento`). No entró `material-icons-extended`: con R8 apagado los íconos nuevos inflarían el APK y el trazo del sistema no vive en ese set.
- `ChipEstadoSubida` se extrajo de `HistorialScreen.kt` a la biblioteca con icono real por estado (marcas de verificación, reintento, reloj, alerta) y los estados que antes decían "Error" ahora dicen "Fallido"; `HistorialScreen` y `DetalleReporteScreen` importan la versión del paquete.
- `RenglonSwitch` dibuja un interruptor rectangular: el `Switch` de M3 es píldora y D3 lo prohíbe; el pulgar se desliza con `animateDpAsState`.
- Los strings que estos componentes necesitan salieron a `values/strings.xml` y `values-es-rMX/strings.xml` (destinos, estados de subida, pendientes, placa, grabación, barra superior); los de las pantallas quedan para C6.
- Geometría y colores consumen los tokens de C1 (`AppShapes`, `colorScheme`, `AppTheme.coloresEstado`); no hay radio suelto ni color literal dentro de los componentes, salvo los casos que el propio sistema define.

**Archivos modificados:**
- `docs/PLAN.md`
- `CHANGELOG.md`
- `app/src/main/java/com/ai27/app/custodio/ui/components/` (nuevo: `Vitrina.kt`, `BarraNavegacion.kt`, `BarraSuperior.kt`, `Botones.kt`, `Chips.kt`, `MiniaturaMedio.kt`, `Renglones.kt`, `Avisos.kt`, `HojaInferior.kt`, `CampoPlaca.kt`)
- `app/src/main/java/com/ai27/app/custodio/ui/historial/HistorialScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/historial/DetalleReporteScreen.kt`
- `app/src/main/res/drawable/ic_archivo.xml`, `ic_reloj.xml`, `ic_ajustes.xml`, `ic_camara.xml`, `ic_subir.xml`, `ic_adjunto.xml`, `ic_chevron.xml`, `ic_carpeta.xml`, `ic_placa.xml`, `ic_alerta.xml`, `ic_cheque.xml`, `ic_reintento.xml` (nuevos)
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-es-rMX/strings.xml`

**Pendiente:** comparación visual de los `@Preview` contra los PNG de `mock-app-design-language/` en el studio, y verificar que Inter se dibuje de verdad (deuda heredada de C1).

## 2026:09:20 09:40

**Resumen:** Fase C1: se unificó el lenguaje visual sobre los tokens de Nocturne con Inter, sustituyendo la plantilla de Android Studio, y se dejaron de versionar los mockups.

**Cambios:**
- Se resolvieron las decisiones D1, D2 y D3 de la sección 11 del plan: el lenguaje canónico es Nocturne (los tokens de `pf-library-frontend-components`, los mismos del panel interno y del portal del cliente), la tipografía es Inter para los dos temas con una sola escala de formas de 4dp base, y ante la contradicción entre los PNG y los documentos de diseño manda el documento, así que no se usan formas de píldora salvo el botón de grabación y los contadores pequeños.
- Se reescribió `Color.kt` con los dos juegos de tokens de Nocturne, oscuro y claro, tomados de `tokens.css`.
- Se eliminó `dynamicColor` de `Theme.kt`. Era el defecto visual más grave del repo: en Android 12 o superior la app se pintaba con el fondo de pantalla del usuario y el diseño no se veía nunca. Ahora hay dos esquemas completos de Material 3, se pasan las formas al tema y el modo oscuro es el predeterminado sin seguir al sistema, porque el producto es dark-first.
- Se creó `Shape.kt` con la escala de 2, 4, 8 y 12dp.
- Se reescribió `Type.kt` con la escala completa de trece estilos en Inter, empaquetando la fuente variable en `res/font/inter_variable.ttf` en lugar de usar fuentes descargables, porque la app trabaja sin señal. Se agregó un estilo con cifras tabulares para contadores y cronómetro. La escala se subió entre dos y cuatro puntos respecto de Nocturne, que está calibrado para escritorio, porque esta app se lee con el brazo extendido y a pleno sol.
- Se agregó `EstadoColores.kt` con los colores de los cuatro estados de subida, que Material 3 no cubre, expuestos por `CompositionLocal` con la receta de Nocturne de relleno al 12 por ciento, borde al 25 por ciento y contenido a color pleno.
- Se corrigió `themes.xml`, que heredaba de un tema de plataforma claro y producía un destello blanco al abrir una app oscura.
- Se conectó `ChipEstadoSubida` a los nuevos colores de estado y se le agregó borde; `ChipEstado` pasó a usar la escala de formas del tema en lugar de un radio suelto.
- Se limpiaron los colores morados y turquesa de la plantilla en `colors.xml`.
- Se agregó `mock-app-design-language/` al `.gitignore`: son unos 8MB de PNG y HTML generados en Stitch, material de referencia que no necesita versionarse.
- Se agregó la licencia OFL de Inter en `assets/licenses/inter-OFL.txt`.

**Archivos modificados:**
- `.gitignore`
- `CHANGELOG.md`
- `docs/PLAN.md`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Color.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Theme.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Type.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/Shape.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/theme/EstadoColores.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/historial/HistorialScreen.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/font/inter_variable.ttf`
- `app/src/main/assets/licenses/inter-OFL.txt`

## 2026:09:20 09:00

**Resumen:** Se agregó al plan la sección 11, con los siguientes pasos para reproducir en Jetpack Compose los mockups finales de `mock-app-design-language/`.

**Cambios:**
- Se documentó el estado actual de la capa de presentación contra lo que exigen los mockups: el tema sigue siendo la plantilla de Android Studio con `dynamicColor = true`, no existe `Shape.kt`, no hay barra de navegación inferior, no hay componentes compartidos, no hay ningún `@Preview` y los textos están embebidos en el código.
- Se registraron cuatro decisiones que bloquean la implementación: cuál de los dos lenguajes de diseño de los mockups es el canónico, que la tipografía y las formas no pueden depender del tema (claro usa Inter con radio 4px y oscuro usa Plus Jakarta Sans más Manrope con radio 8px), la contradicción entre los PNG y los documentos de diseño respecto de las formas de píldora, y las cinco pantallas `*_reparado` que quedaron fuera de la familia visual.
- Se señaló que el acento periwinkle de Nocturne, el sistema que usan los frontends web, no aparece en ninguna pantalla final, por lo que la app de campo divergiría visualmente del panel interno y del portal del cliente.
- Se planearon siete fases (C1 a C7): tokens y tema, biblioteca de componentes, navegación y descomposición de `CapturaScreen.kt`, pantallas una por una, estados de carga/error/sin conexión, strings y accesibilidad, y verificación.
- Se incluyeron la tabla de mapeo de los 20 componentes base a composables, el orden sugerido de las 12 pantallas, las exclusiones de alcance que el mockup no revoca y el grafo de dependencias entre fases.

**Archivos modificados:**
- `docs/PLAN.md`
- `CHANGELOG.md`

## 2026:09:19 17:10

**Resumen:** B7: release y documentación. APK release firmado con keystore propio, Crashlytics integrado (plugin + SDK), WorkManagerInitializer fuera del manifest para desbloquear `lintVitalRelease`, y README de provisión de equipos y cuentas + App Distribution.

**Cambios:**
- Firma de release: keystore propio `app/keystore/release.jks` (RSA-2048, 25 años, alias `custodio`) y `keystore.properties`, **ambos gitignored** (ya cubierto por `*.jks` y `keystore.properties`). `app/build.gradle.kts` lee las credenciales si el archivo existe; si falta, `release` queda sin firmar (`signingConfigs.release` + `buildTypes.release.signingConfig` condicionales). Nota: la firma se configuró con `import java.util.Properties` y sentencias explícitas porque `java.util.Properties` no resolvía por nombre completo en este script y `.use { }` fallaba la inferencia con Kotlin 2.3 embebido.
- Crashlytics: plugin `com.google.firebase.crashlytics` **aplicado en `:app`** (versión 3.0.8 del catálogo), librería runtime vía BoM (`firebase-crashlytics`), SDK inicializado en `AppCustodioApplication.onCreate` y recolección explícita en el manifest (`firebase_crashlytics_collection_enabled=true`). El bloque `firebaseCrashlytics { mappingFileUploadEnabled }` NO se usa: el accessor no genera con AGP 9 y con R8 apagado no hay mapping; el reporte real queda operativo cuando exista la consola (carril A).
- Manifest: además del meta-data de Crashlytics, se removió el `androidx.work.WorkManagerInitializer` (provider de startup con `tools:node="remove"`), porque `AppCustodioApplication` implementa `Configuration.Provider` y `lintVitalRelease` lo marcaba error fatal. Este lint venía latente desde B5 y afloró al hacer el primer build de release.
- `assembleRelease` verde (lintVitalRelease incluido); APK firmado V1+V2 verificado con `apksigner` (~22,5 MB, output en `app/build/outputs/apk/release/app-release.apk`).
- Docs: `README.md` nuevo (no existía) con build/verificación, provisión de equipos (sideload, App Distribution), alta/baja de cuentas (A2) con sus pasos 1–3 del carril A, resguardo del keystore y verificación manual del ciclo completo (aún pendiente en dispositivo real con el carril A disponible).
- `AGENTS.md`: B7 en el resumen de fases, comando `assembleRelease` en Commands, Crashlytics aplicado y nota de firma de release.

**Archivos modificados:**
- `app/build.gradle.kts` (plugin Crashlytics, signingConfig release, dependencia `firebase-crashlytics`)
- `gradle/libs.versions.toml` (librería `firebase-crashlytics`)
- `app/src/main/AndroidManifest.xml` (meta-data Crashlytics, remoción WorkManagerInitializer)
- `app/src/main/java/com/ai27/app/custodio/AppCustodioApplication.kt` (init Crashlytics)
- `README.md` (nuevo)
- `AGENTS.md`, `CHANGELOG.md`
- Generados y gitignored (no commitear): `app/keystore/release.jks`, `keystore.properties`

**Pendiente manual:** carril A en la consola (A1 servicios, A3 reglas, A2 cuentas, A4 catálogo de etiquetas) y, una vez disponible, desplegar el APK a un equipo de campo y validar el ciclo completo en Firestore (criterio B7).

## 2026:09:19 16:30

**Resumen:** B6: historial y estados. Pantalla de historial (30 días, solo lectura, buscador por placa) y detalle de viaje (solo lectura) con chips de ciclo y estado de subida; descarte de viaje con motivo obligatorio; badge permanente de pendientes.

**Cambios:**
- Historial: `ReporteDao.observarHistorial` ahora emite solo `Ciclo.CERRADO` y `Ciclo.DESCARTADO` de los últimos 30 días (`fechaLocal >= fechaDesde`), ordenados descendente; `observarPorId` (Flow) nuevo en `ReporteDao`/`ReporteRepository`/`RoomReporteRepository` para el detalle. Todo sale de Room local, sin pull remoto.
- Descarte: `CapturaViewModel` inyecta `DescartarReporte` y expone `descartarViaje(motivo)`; tras descartar (y también tras `cerrarViaje`) se llama `programadorSubidas.sincronizarReporte(id)` para empujar la transición DESCARTADO/CERRADO a Firestore (corrige un hueco de B5: cerrar/descartar no sincronizaba el ciclo).
- Guard del pipeline: `SubidaMedioProceso` no sube evidencia a un reporte **descartado localmente** (corta antes de `asegurarReporte`); el contador tampoco avanza.
- UI: nueva jerarquía `ui/historial/` — `HistorialUiState` (estado + `filtrarPorPlaca` puro), `HistorialViewModel` (combina historial + pendientes de la sesión + filtro), `HistorialScreen` (encabezado con botón atrás, buscador por placa, tarjetas con chips `Cerrado/Descartado` y `Enviado/Pendiente/Subiendo/Error`, vacíos "Sin viajes…" / "Ningún viaje coincide…"), `DetalleReporteViewModel` (lee `reporteId` del `SavedStateHandle`, combina reporte + medios + catálogo de etiquetas) y `DetalleReporteScreen` (solo lectura: placas, fecha, ciclo, estado, contador, comentario, motivo de descarte, miniaturas/fotos y notas con su etiqueta).
- Navegación: rutas `historial` y `detalle/{reporteId}` en `AppNavHost`; botón historial y badge "N sin subir" en `BarraSesion`; "Descartar viaje" en el encabezado con diálogo de motivo obligatorio (confirm deshabilitado mientras está vacío o descartando).
- Hilt: `CapturaModule` provee ahora `DescartarReporte` y `ObtenerHistorial`.
- Pruebas JVM (67 verdes): `ObtenerHistorialTest` (descartado aparece con su motivo y el activo no), `SubidaMedioProcesoTest` (reporte descartado local → no sube, no crea doc, no avanza contadores), `HistorialFiltroTest` (filtro sin mayúsculas, parcial, vacío) y `RoomReporteRepositoryTest` (historial con CERRADO/DESCARTADO excluyendo ACTIVO contra Room real + `observarPorId`).
- Pendiente manual (no automatizado): recorrer el ciclo completo en un dispositivo real (crear → capturar → cerrar y verlo en Historial → detalle → descartar con motivo y verlo reflejado).

**Archivos modificados:**
- `app/src/main/java/com/ai27/app/custodio/di/CapturaModule.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaScreen.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/captura/CapturaViewModel.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/navigation/AppNavHost.kt`
- `app/src/main/java/com/ai27/app/custodio/data/pipeline/SubidaMedioProceso.kt`
- `app/src/main/java/com/ai27/app/custodio/data/local/db/ReporteDao.kt`
- `app/src/main/java/com/ai27/app/custodio/domain/repository/ReporteRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/data/repository/RoomReporteRepository.kt`
- `app/src/main/java/com/ai27/app/custodio/ui/historial/` (nuevo: HistorialUiState, HistorialViewModel, HistorialScreen, DetalleReporteViewModel, DetalleReporteScreen)
- `app/src/test/java/com/ai27/app/custodio/FakeReporteRepository.kt`
- `app/src/test/java/com/ai27/app/custodio/ObtenerHistorialTest.kt`
- `app/src/test/java/com/ai27/app/custodio/data/pipeline/SubidaMedioProcesoTest.kt`
- `app/src/test/java/com/ai27/app/custodio/RoomReporteRepositoryTest.kt` (nuevo)
- `app/src/test/java/com/ai27/app/custodio/ui/historial/HistorialFiltroTest.kt` (nuevo)
- `AGENTS.md`, `CHANGELOG.md`

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
