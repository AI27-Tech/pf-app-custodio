# PLAN — App Custodio: captura en campo y envío a base de datos

## 1. Para qué existe esta app

Hoy los custodios reportan por **WhatsApp**: mandan fotos y notas de voz a un grupo, y un analista de datos las busca mensaje por mensaje para armar reportes diarios, semanales y mensuales con conteos y evidencia para el cliente.

Esta app reemplaza ese flujo. No es una app de mensajería: es una **app de captura con metadatos**. Cada foto y cada audio llega a una base de datos propia con fecha, hora, ubicación, autor, placa y etiqueta, de modo que el analista consulte y filtre en lugar de rebuscar.

De ahí salen las tres reglas que gobiernan todo el diseño:

1. **La unidad de valor es el archivo, no el reporte.** Lo que reemplaza a un mensaje de WhatsApp es un documento de `medios`. El reporte es sólo el contenedor que los agrupa por viaje. `medios` es la colección que se consulta.
2. **El consumidor es un analista.** El entregable no son pantallas bonitas: es un conjunto de datos consultable y filtrable.
3. **El competidor es WhatsApp, y tiene cero ceremonia.** Abrir, tomar, enviar. Si esta app resulta más lenta que eso, los custodios seguirán usando WhatsApp y la base quedará a medias. Cada pantalla se juzga contra ese estándar.

**Fuera de alcance de este plan:** el dashboard del analista. Se construye aparte; el contrato de datos de §5 es lo que va a consumir.

---

## 2. Decisiones bloqueadas

| Tema | Decisión |
|---|---|
| Usuario | **Custodio de AI27**, 37 personas. No el operador del tracto |
| Cliente | **Uno solo** por ahora; se escribe `clienteId` con valor fijo para no migrar después |
| Auth | Firebase Auth por **teléfono verificado por SMS** (E.164 +52), login de dos fases número→OTP; identidad = teléfono, no correo. Allowlist de teléfonos en `config/telefonos` verificada tras el OTP; sesión persistente, logout visible. Ver "Lo que cambió" (PHONE_AUTH) |
| Unidad de agrupación | **Un reporte = un viaje.** Se abre al tomar el transporte, se cierra al entregarlo |
| Reporte activo | **Uno a la vez.** No se puede crear otro hasta cerrar o descartar el actual |
| Identificador | **Placa del tracto**, capturada al crear el reporte. Se cruza con fecha para ligar a un servicio (no hay folio) |
| Etiqueta | Va en **cada archivo**, no en el reporte: un viaje contiene fotos de tipos distintos. Obligatoria, de catálogo cerrado |
| Captura | **Sólo cámara y grabadora en la app.** Sin selector de galería |
| Metadatos por archivo | Fecha/hora, GPS al momento de capturar (con precisión y antigüedad del fix), autor, dispositivo, SHA-256 |
| Subida | **Progresiva: cada archivo sube en cuanto se captura**, no al cerrar el reporte |
| Offline | Cola en Room + WorkManager; reintento con backoff y botón manual |
| Media | Hasta 50 fotos por reporte a 1920×1080 calidad 88, más miniatura de 320px; audio hasta 5 min. **La resolución está en revisión, ver §3.4** |
| Inmutabilidad | Un reporte cerrado no se edita ni se borra. Descartar es un estado, no un borrado |
| Distribución | APK release por sideload y App Distribution; textos en español (México) |

### Lo que cambió respecto de la versión anterior de este plan

- La **etiqueta se movió del reporte a cada archivo** (R3). Antes el reporte tenía un `tipo` único; eso no sirve cuando el analista busca "todas las fotos de candado" dentro de viajes que contienen de todo.
- El **documento del reporte se escribe al crearlo**, no al cerrarlo (B8 + R7). Antes un reporte cerrado en zona sin señal no existía en ningún lado fuera del dispositivo: si el equipo se formateaba, la evidencia desaparecía y nadie sabía que ese reporte existió.
- **La subida dejó de dispararse al cerrar** (R7). Antes la evidencia de un viaje entero no salía del dispositivo hasta medio día después, en una sola subida de 20MB. WhatsApp entrega cada foto al instante; esto era una regresión funcional contra la herramienta que venimos a reemplazar.
- **Se quitó el selector de galería** (R6). Pediste GPS y hora por archivo; una foto sacada de la galería puede ser de otro día y otro lugar, y vacía justamente el dato que da valor.
- **Se desnormalizan campos del reporte en cada documento de media** (B2). Firestore no hace joins: sin esto, "todos los audios de la placa XYZ la semana pasada" es imposible.
- **Las fotos subieron de 10 a 50** (D3, R11). La resolución quedó en 1920×1080 calidad 88, y está en revisión porque el propósito real —leer el contenido de la foto— pide más (§3.4).
- **La identidad del custodio pasó de correo+contraseña a teléfono verificado por SMS** (PHONE_AUTH, 2026-09-23). El login es de dos fases (número → OTP), la autorización se resuelve con una allowlist de teléfonos en `config/telefonos` verificada **después** del OTP (más `beforeCreate` en Cloud Functions cuando haya plan Blaze), y el contrato de datos cambió `custodioCorreo` → `custodioTelefono`. El correo+contraseña quedó descartado por completo; el único correo que sobrevive es el del **analista** del panel (`config/analistas.correos`), que es otro eje. Detalle del contrato en §5.1, provisión en §9 (A1/A2).

---

## 3. Pendiente de definición (no se inventa)

### 3.1 Catálogo de etiquetas — bloquea la salida a producción, no la construcción

La etiqueta por archivo es obligatoria, pero **sus valores no están definidos**. No los invento: salen de lo que hoy manda la gente al grupo de WhatsApp, y la exportación de ese grupo no se entregó (R4).

Para que esto no detenga el desarrollo, el catálogo es **dato, no código**: vive en `catalogos/etiquetas` en Firestore, se cachea en el dispositivo y se usa una lista provisional de trabajo durante el desarrollo, claramente marcada como tal. Cambiar los valores reales no requiere recompilar ni publicar APK.

**La app no puede entregarse a los 37 custodios sin este catálogo.** La forma más barata de obtenerlo sigue siendo exportar una semana del grupo (WhatsApp → Exportar chat → Sin archivos) y derivarlo del tráfico real.

### 3.2 Retención de la media

Sin regla de ciclo de vida en V1 (D6). Las rutas de Storage quedan agrupadas por reporte para poder aplicar una política después sin migrar nada. A revisar a los 6 meses, cuando haya volumen real.

### 3.3 Búsqueda de texto libre

No la hay (B1). Firestore no busca texto. El dashboard filtra por campos: placa, fecha, custodio, etiqueta, novedad. Si después hace falta buscar dentro de los comentarios, se agrega un índice externo sin tocar el modelo.

### 3.4 Resolución de captura — decisión abierta, y trabaja contra el propósito

**Lo que hace el código hoy, verificado:** `VistaCamara.kt` fija `OutputSize(1920, 1080)` y `Miniaturas.kt` guarda el original en **JPEG calidad 88 sin reescalar** (`CALIDAD_ORIGINAL = 88`; `escalar` sólo se aplica a la miniatura de 320px). Es decir: **~2 megapíxeles en 16:9**.

**Por qué importa, y por qué no se había visto.** Al definir el presupuesto de fotos (D3, R11) el criterio era el costo de datos celulares. Pero al levantar el propósito del dashboard quedó claro que la evidencia que el cliente necesita **no es la foto como objeto, es su contenido**: un número de candado, un folio, un documento, una placa. El analista abre las fotos para *leer*. Con ese propósito, la compresión deja de ser un tema de ancho de banda y se vuelve el tema del producto.

Y con ese criterio, lo que hay hoy juega en contra:

- **2 MP son marginales para texto pequeño.** Un candado fotografiado a dos metros ocupa del orden de 80 píxeles de ancho: no se lee ni con zoom. Ningún visor arregla información que no se capturó.
- **16:9 es la peor proporción para documentos.** Un folio en vertical pierde la mitad del encuadre, y los píxeles se gastan en el piso y el techo en lugar del papel.

**Opciones:**

| | Resolución | Por foto (est.) | Por reporte de 50 | Al mes (37/día, est.) |
| --- | --- | --- | --- | --- |
| **Hoy** | 1920×1080, 16:9, q88 | ~500 KB | ~26 MB | ~29 GB |
| **Propuesta** | 2560×1920, 4:3, q88 | ~1 MB | ~50 MB | ~55 GB |

Con plan de datos empresarial (R11a) el costo de transmisión es asumible, y el de almacenamiento sigue siendo de unos pocos dólares al mes. Lo que se duplica es lo que se puede leer.

**Estado: PENDIENTE de decisión de Producto.** No se cambia por iniciativa propia porque contradice el presupuesto que D3/R11 fijaron a conciencia; la premisa de esa decisión cambió, no la decisión. Es un cambio de **dos constantes** (`OutputSize` en `VistaCamara.kt` y, si se quiere desacoplar, un tope de dimensión en `Miniaturas.kt`), así que sigue estando a tiempo.

**Riesgo de no decidir:** se entrega un sistema que captura evidencia que el analista no puede leer, y el defecto sólo se descubre cuando alguien intente usarla con un cliente. Es el tipo de falla que no aparece en ninguna prueba automatizada.

---

## 4. Arquitectura

Módulo único `:app`, **paquete por feature** dentro de tres capas limpias. Multi-módulo no compra nada a este tamaño.

```
com.ai27.app.custodio
├── data/
│   ├── local/        entidades Room + DAO, almacén de archivos, workers
│   ├── remote/       AuthDataSource, FirestoreDataSource, StorageDataSource
│   ├── mapper/       dominio ⇄ entidad ⇄ DTO
│   └── repository/   implementaciones
├── domain/           Kotlin puro: sin Android, sin Firebase
│   ├── model/        Reporte, Medio, Etiqueta, Gps, Custodio
│   ├── error/        errores de dominio sellados
│   └── usecase/      CrearReporte, CapturarMedio, EtiquetarMedio,
│                     CerrarReporte, DescartarReporte, ReintentarSubida,
│                     ObtenerHistorial, ObtenerEtiquetas
├── ui/
│   ├── login/        LoginScreen + ViewModel
│   ├── captura/      pantalla principal: cámara lista, grabadora, etiquetado
│   ├── reporte/      rejilla de medios del reporte activo, cerrar, descartar
│   ├── historial/    lista de reportes cerrados + detalle sólo lectura
│   ├── ajustes/      tema claro/oscuro, logout, pendientes
│   └── theme/        Material 3
└── di/               módulos Hilt
```

- **DI** Hilt · **Navegación** Compose Navigation · **Async** Coroutines + Flow
- **Cámara** CameraX, disparo único · **Audio** MediaRecorder AAC `.m4a`, temporizador visible, tope 5 min
- **Ubicación** FusedLocationProvider, one-shot en el momento de cada captura (≈8s de espera); se guardan precisión y antigüedad del fix, y si no engancha queda un campo de texto libre
- **Archivos locales** `filesDir` privado de la app; excluido de Auto Backup
- **Miniaturas** generadas en el dispositivo (320px), no en Cloud Functions

### Reglas de capa

- `domain` no compila contra Android ni contra Firebase. Los casos de uso se prueban con JUnit puro, sin Robolectric ni emulador. Ése es el único beneficio real de las capas a este tamaño, y se pierde en cuanto se filtra un `Timestamp` de Firestore al dominio.
- Los errores cruzan las capas como `Result` con errores de dominio sellados, no como excepciones.
- La invariante de **un solo reporte activo** se garantiza en **Room** (índice único parcial sobre el estado activo por custodio), no sólo en el caso de uso. El caso de uso da el mensaje amable; la base impide la carrera.
- Los mappers viven en `data` y son el único lugar que conoce `schemaVersion`.

---

## 5. Contrato de datos (Firebase)

Convención de nombres: los conceptos de negocio en español (`reportes`, `medios`, `placas`, `custodioUid`, `etiqueta`); los metadatos técnicos genéricos en inglés (`createdAtServer`, `storagePath`, `sizeBytes`, `schemaVersion`). Es la convención del workspace (DEC-024) y se decide antes de la primera escritura, porque es un contrato.

### 5.1 Firestore

```
usuarios/{uid}
  telefono, nombre, rol, activo                  ← identidad por teléfono (PHONE_AUTH)

config/telefonos
  telefonos: [string]      ← allowlist E.164 (+52…); se verifica tras el OTP. Ver §5.4

catalogos/etiquetas
  valores: [{ id, nombre, orden, activo }]      ← pendiente §3.1

reportes/{reporteId}                             ← contenedor de un viaje
  placas            string   normalizada: mayúsculas, sin espacios ni guiones
  placasCapturadas  string   lo que el custodio tecleó, tal cual
  custodioUid       string
  custodioTelefono  string   E.164; reemplazó a custodioCorreo (PHONE_AUTH)
  clienteId         string   valor fijo por ahora
  deviceId          string
  fechaLocal        string   "YYYY-MM-DD" en America/Mexico_City
  createdAtDevice   timestamp
  createdAtServer   timestamp  serverTimestamp() — la autoritativa para ordenar
  closedAt          timestamp | null
  comentario        string | null    narrativa del viaje, opcional
  hayNovedad        bool
  ciclo             ACTIVO | CERRADO | DESCARTADO
  motivoDescarte    string | null
  subida            PENDIENTE | EN_PROGRESO | COMPLETA | FALLIDA
  completo          bool     mediosSubidos == mediosEsperados
  mediosEsperados   number   definitivo al cerrar
  mediosSubidos     number
  resumenMedios     { fotos, audios, durationMsTotal }
  schemaVersion     number

medios/{medioId}                                 ← el reemplazo de un mensaje de WhatsApp
  reporteId         string
  tipo              FOTO | AUDIO
  etiqueta          string   id del catálogo, obligatoria
  storagePath       string
  thumbnailPath     string | null
  createdAtDevice   timestamp   momento de la captura
  createdAtServer   timestamp
  gps               { lat, lng, accuracyM, ageMs } | null
  ubicacionTexto    string | null    respaldo cuando el GPS no engancha
  comentario        string | null    pie de foto, opcional
  contentType       string
  sizeBytes         number
  durationMs        number | null
  sha256            string
  origen            CAMARA | GRABADORA
  # desnormalizado del reporte para que el documento se lea solo:
  placas            string
  custodioUid       string
  clienteId         string
  fechaLocal        string
  schemaVersion     number
```

`medios` es colección de primer nivel, no subcolección, para poder consultar archivos entre reportes. La desnormalización es lo que lo hace posible: sin `placas` y `fechaLocal` encima de cada archivo, filtrar media por atributos del viaje exigiría un join que Firestore no tiene.

`fechaLocal` existe para los conteos diarios, semanales y mensuales del analista: agrupar y hacer rangos sobre una cadena `YYYY-MM-DD` es trivial y no depende de la zona horaria de quien consulta. Las marcas de tiempo se guardan en UTC.

### 5.2 Storage

```
medios/{reporteId}/{medioId}.jpg
medios/{reporteId}/{medioId}-min.jpg
medios/{reporteId}/{medioId}.m4a
```

Agrupado por reporte porque "evidencia para el cliente" significa descargar un viaje completo.

### 5.3 Reglas de seguridad

- Escritura sólo autenticada y sólo del dueño: `request.auth.uid == custodioUid`.
- `reportes`: se puede actualizar mientras `ciclo == ACTIVO`. Transiciones permitidas: `ACTIVO → CERRADO` y `ACTIVO → DESCARTADO`. Una vez `CERRADO`, sólo se admite avanzar `subida`, `mediosSubidos` y `completo` — nada más, y nunca de regreso a `ACTIVO`.
- `medios`: creación permitida al dueño; **update y delete negados siempre**.
- `delete` negado en `reportes` siempre. Descartar es un estado.
- `config/telefonos` (allowlist de teléfonos, PHONE_AUTH): **lectura sólo autenticada, escritura negada** desde el cliente. La escribe el aprovisionamiento con Admin SDK (§9 A2). Es la excepción legible de `config/*`.
- El acceso de lectura del dashboard se resuelve por **correo del analista** en `config/analistas.correos` (no por custom claim). El correo del analista es un eje aparte de la identidad del custodio, que es por teléfono. Fuera de este plan en cuanto a UI, pero las reglas se escriben desde ahora para no dejarlas abiertas "temporalmente".

### 5.4 Identidad, autenticación e idempotencia

**Autenticación (PHONE_AUTH).** La identidad del custodio es su **teléfono E.164 verificado por SMS** (Firebase Phone Auth), no un correo. El login es de dos fases: el custodio teclea el número, recibe un OTP y lo confirma. La **autorización** se resuelve **después** del OTP contra la allowlist `config/telefonos.telefonos`: un teléfono verificado que no esté en la lista no entra. En producción con plan Blaze, un `beforeCreate` (Cloud Functions) refuerza esa puerta del lado del servidor. `Custodio(uid, telefono)` es lo que circula por la sesión; el correo+contraseña anterior quedó descartado.

`reporteId` y `medioId` son **UUID generados en el cliente**. La ruta en Storage y el id del documento se derivan de ellos, así que un reintento sobrescribe en lugar de duplicar. Es lo que hace segura una cola offline con backoff.

`schemaVersion` va en todos los documentos. La distribución es por sideload: los APK viejos no mueren, y vas a tener dos o tres versiones escribiendo al mismo tiempo.

---

## 6. Ciclo de vida y pipeline

### 6.1 Reporte

1. **Crear** — se teclea la placa (una sola vez por viaje), se normaliza y se confirma en pantalla. Se crea la fila en Room y, en cuanto haya red, el documento en Firestore con `ciclo: ACTIVO`. El borrador sobrevive reinicios de la app.
2. **Capturar** — cada foto o audio se guarda local, se etiqueta y **se encola para subir de inmediato**.
3. **Cerrar** — exige al menos un archivo. Fija `closedAt`, `mediosEsperados` y `ciclo: CERRADO`. No dispara la subida: sólo marca el fin. El reporte queda sólo lectura.
4. **Descartar** — salida de emergencia cuando se abrió por error o se cayó el servicio. Exige un motivo corto obligatorio y pasa a `ciclo: DESCARTADO`.

   **Consecuencia que conviene tener presente:** como los archivos suben en cuanto se capturan, descartar un reporte **no borra** lo que ya subió. Los archivos se quedan (son inmutables) y el reporte queda marcado como descartado para que el analista lo excluya. No hay forma de que la app borre evidencia ya enviada, y eso es deliberado.

5. Sin cierre automático por tiempo y sin aviso de reporte viejo (E2).

### 6.2 Subida

Un `CoroutineWorker` con restricción de red, serializado por reporte:

1. Asegura (upsert idempotente) que el documento del reporte exista.
2. Sube miniatura y archivo a Storage.
3. Escribe el documento en `medios`.
4. Incrementa `mediosSubidos` y recalcula `completo`.
5. Borra el archivo local.

Fallo → `subida: FALLIDA`, el archivo local **se conserva**, backoff exponencial y botón de "Reintentar" visible. Los estados se muestran como chips: Activo / Subiendo / Enviado ✓ / Error, más un badge permanente con el conteo de pendientes — "¿sí se envió?" es la ansiedad número uno en este tipo de app.

**Cerrar sesión queda bloqueado mientras haya pendientes**, con el conteo a la vista y una opción de forzar que advierte del riesgo. Si se cierra sesión, la cola pierde el token.

---

## 7. Diseño de la experiencia en campo

El estándar contra el que se mide todo: **abrir la app y tener una foto etiquetada y encolada en menos de cinco segundos.**

- **Con reporte activo, la app abre en la cámara lista para disparar.** No en un menú, no en una lista. Un toque al disparador.
- **Tras el disparo, una sola pantalla**: chips grandes de etiqueta (obligatoria), y opcionalmente nota de voz o comentario. Es el único toque extra respecto de WhatsApp, y es el que genera todo el valor.
- **Escribir es el punto débil.** La placa se teclea una vez por viaje; el resto es tocar chips y hablar. Ningún campo de texto es obligatorio salvo la placa y el motivo de descarte.
- **GPS silencioso**, capturado en cada archivo sin preguntar. Si no engancha, aparece un campo de ubicación en texto.
- **Rejilla** para los hasta 50 archivos del reporte activo, con la etiqueta como distintivo y borrado sólo antes de subir.
- **Tema claro y oscuro alternables por el usuario** (no forzado), alto contraste, objetivos táctiles de 56dp mínimo, sin texto chico, soporte a escalado de fuente del sistema. Se usa sin guantes, dentro de un vehículo en movimiento.
- **Historial** de los últimos 30 días, sólo lectura, buscable por placa.
- **Sin acción de pánico ni emergencia.** La plataforma AI27 tiene ese concepto; esta app no, explícitamente, para que nadie suponga que está.

---

## 8. Volumen y costo

37 custodios, ~37 reportes al día.

| Magnitud | Cálculo |
|---|---|
| Por reporte, peor caso | 50 archivos × ~520KB (1920×1080 q88 + miniatura) ≈ **26MB** |
| Por día | ≈ 960MB · ~1,850 documentos en `medios` |
| Por mes | ≈ **29GB** · ~55k documentos |
| Escrituras Firestore/día | ~3,700 — muy por debajo de cualquier límite relevante |
| Datos celulares | ~26MB por viaje por dispositivo, sobre plan empresarial |

El volumen es chico. No hacen falta agregados ni rollups: las consultas con `count()` sobre `medios` filtrando por `fechaLocal` alcanzan de sobra para los conteos diarios, semanales y mensuales. Tampoco hace falta exportar a BigQuery: el dashboard consulta Firestore directo.

El almacenamiento crece sin techo mientras no haya regla de retención (§3.2): unos **350GB al año**, algo así como 9 USD al mes de almacenamiento después del primer año. Si se adopta la resolución que propone §3.4, esa cifra se duplica a unos 660GB y ~17 USD al mes. Sigue siendo barato en términos absolutos, pero es el número que va a forzar la conversación de retención. Las miniaturas son lo que mantiene barato el costo de lectura del dashboard.

---

## 9. Fases

La consola de Firebase **no está configurada todavía** (nada habilitado, ninguna cuenta creada). Por eso el trabajo de consola es un **carril paralelo** y no la fase 1: así no te vuelves el bloqueo de tu propio camino crítico.

### Carril A — consola (tú, en paralelo)

- **A1.** Habilitar Auth (**phone sign-in**, verificación por SMS), Firestore (modo producción) y Storage en `custodio-906e7268`. El SMS OTP real exige el plan **Blaze** (Identity Platform); en Spark el Emulator Suite prueba el flujo completo pero no salen SMS reales.
- **A2.** Dar de alta los teléfonos de los custodios. El aprovisionamiento con **Admin SDK** (`firebase/provision/`) es idempotente: crea la cuenta de Authentication por teléfono y escribe la allowlist `config/telefonos.telefonos` (E.164, +52). Con Blaze, desplegar además el `beforeCreate` de `firebase/functions/`. Alta y baja manuales por un admin, documentado en el README.
- **A3.** Desplegar las reglas de seguridad de §5.3 (incluye `config/telefonos` legible y `config/analistas` para el correo del analista).
- **A4.** Entregar el catálogo de etiquetas (§3.1) — lo que destraba la salida a producción.

### Carril B — app

| # | Fase | Termina cuando |
|---|---|---|
| **B0** | Higiene del repo: mover `google-services.json` de la raíz a `app/` (donde está ahora no lo lee nadie), primer commit, README inicial | `assembleDebug` compila con el plugin de google-services activo |
| **B1** | Dependencias y andamiaje: Hilt, Room, WorkManager, Navigation, CameraX, Coil, Firebase BoM, Crashlytics en `libs.versions.toml`; estructura de paquetes; `strings.xml` es-MX; tema claro/oscuro | La app arranca con navegación vacía en los dos temas |
| **B2** | Dominio y persistencia local: modelos, casos de uso, Room con el índice único de reporte activo, almacén de archivos, errores sellados. **Sin Firebase, con fakes** | Pruebas unitarias en verde, incluida la carrera de dos reportes activos |
| **B3** | Captura: CameraX, grabadora con tope y temporizador, GPS por archivo con precisión y antigüedad, etiquetado con chips, miniaturas, rejilla, borrado previo a la subida | Se capturan y etiquetan 50 archivos sin red y sobreviven a reiniciar la app |
| **B4** | Autenticación: login de dos fases por SMS (número → OTP), allowlist de teléfonos verificada tras el OTP, sesión persistente, logout bloqueado con pendientes, gate de sesión (`RootViewModel`) dirigiendo la navegación | Se entra por OTP y se sale; la sesión sobrevive al reinicio. **Migrado a teléfono en PHONE_AUTH (2026-09-23); ver §5.4** |
| **B5** | Pipeline de subida: repositorios de Firestore y Storage, upsert idempotente del reporte, subida progresiva, contadores, backoff, reintento manual. Reglas de seguridad probadas con el **Emulator Suite** | Un reporte capturado en modo avión sube solo al recuperar red; las pruebas de reglas verifican que un custodio no lee ni modifica lo de otro y que un cerrado no se edita |
| **B6** | Historial y estados: lista de 30 días, detalle sólo lectura, chips de estado, badge de pendientes, descarte con motivo | Se recorre el ciclo completo en un dispositivo real |
| **B7** | Release y documentación: APK release por sideload, README de provisión de equipos y cuentas, App Distribution | APK instalado en un equipo de campo y un reporte real en Firestore |

B2 y B3 no dependen del carril A: avanzan contra fakes y el emulador. B4 y B5 necesitan A1; B7 necesita A4.

Cada fase cierra con `./gradlew testDebugUnitTest` en verde y un commit.

### Notas de toolchain

- AGP 9.4.1: `compileSdk { version = release(37) }` en forma de bloque, R8 en `buildTypes.release.optimization`, keep rules en `app/src/main/keepRules/`.
- Verificar que la versión del plugin de google-services sea compatible con AGP 9.4 — es un riesgo conocido del ecosistema y se detecta en B0.
- `org.gradle.configuration-cache=true`: los edits de build deben mantenerse compatibles.
- `google-services.json` se commitea (no es secreto, y simplifica un repo de una sola app). `local.properties` no.

---

## 10. Trazabilidad

Cada decisión de este plan sale de una respuesta del cuestionario `to-questionnaire-app-custodio.md`.

| Decisión | Origen |
|---|---|
| Usuario = custodio de AI27 | R1 |
| Reporte = un viaje | R2 |
| Etiqueta en el archivo, no en el reporte | R3 (supersede A2) |
| Catálogo de etiquetas pendiente | R4, A3 |
| Sin checklist; registro general del viaje | A9, R5 |
| Sólo cámara, sin galería | R6 |
| Subida progresiva por archivo | R7 |
| Sin vigilancia en vivo | R8 |
| Sin BigQuery; habrá dashboard | R9 |
| Reportes diario/semanal/mensual con conteo y evidencia | R10 → `fechaLocal`, miniaturas, agrupación por reporte en Storage |
| 50 fotos, plan de datos empresarial; resolución en revisión | D3, R11, §3.4 |
| Placa del tracto como llave, sin folio | A6, C1–C4, R12 |
| `clienteId` fijo | A7, R13 |
| Audio 5 min | R14 |
| Documento del reporte escrito temprano | B8, B9 |
| Desnormalización en `medios` | B2, B3, B4 |
| Dos marcas de tiempo, UTC | B5, B10 |
| UUID de cliente, `schemaVersion` | B6, B7 |
| Inmutabilidad y sin borrado | B11, B12 |
| Nombres en español | B13 (DEC-024) |
| Descarte con motivo, sin cierre automático ni aviso | E1, E2 |
| Logout bloqueado con pendientes | E3 |
| Dos dispositivos, dos activos: se acepta | E4 |
| Altas y bajas manuales por admin | E5 |
| UX: claro/oscuro, sin texto chico, sin guantes | F1 |
| Captura primero, chips, voz | F2, F3 |
| Chips de estado y badge de pendientes | F4 |
| Historial 30 días | F5 |
| Sin pánico | F6 |
| GPS al capturar, con precisión y antigüedad | F7, F8 |
| Dominio puro, Room como guarda, `Result`, emulador, Crashlytics | G1–G6 |
| Plan en español; fases reordenadas | G7, G8 |
| SHA-256, EXIF conservado, sin retención aún | D6, D7 |
| Identidad por teléfono verificado por SMS; allowlist tras OTP | PHONE_AUTH (supersede la fila "Auth = correo+contraseña" de §2) |


---

## 11. Reproducir el diseño en Jetpack Compose

Los mockups finales viven en `mock-app-design-language/`: 41 carpetas con `screen.png` y `code.html` de Stitch, más dos lenguajes de diseño documentados en `aegis_tactical_transit/DESIGN.md` y `tactical_daylight_security/DESIGN.md`.

Esta sección planea cómo llevarlos a Compose. **No es un rediseño desde cero:** las fases B1–B7 ya construyeron una UI funcional, y el trabajo consiste en realinearla. Conviene tener presente la magnitud real: la capa de presentación de hoy es la plantilla de Android Studio con lógica encima, no un sistema de diseño.

### 11.1 Punto de partida contra lo que exige el mockup

| Área | Hoy en el repo | Lo que pide el mockup |
| --- | --- | --- |
| Color | Plantilla morada de Android Studio (`Purple80`, `Pink40`…) y **`dynamicColor = true`**, así que en Android 12+ el color lo decide el fondo de pantalla del usuario | Dos paletas completas de ~50 tokens cada una, oscura y clara |
| Esquemas | Claro y oscuro sobreescriben sólo `primary`, `secondary`, `tertiary`; el resto son defaults de M3 | Todos los slots definidos, incluidos `surface-container-*` |
| Formas | No existe `Shape.kt`; `MaterialTheme` no recibe `shapes`; hay radios sueltos de 6, 8, 10 y 12dp puestos a mano | Una escala de formas única y estricta |
| Tipografía | Sólo `bodyLarge` sobreescrito; `FontFamily.Default`; no existe `res/font/` | Familias propias con escala completa y cifras tabulares |
| `themes.xml` | Un solo estilo con parent `android:Theme.Material.Light.NoActionBar`, fijado a **Light** | App dark-first; hace falta `values-night/` |
| Navegación | 4 rutas, **sin barra de navegación inferior**; se salta a historial con un `IconButton` dentro de la barra de sesión | Barra inferior de 3 destinos: Reporte, Historial, Ajustes |
| Barras superiores | Tres encabezados distintos hechos a mano (`BarraSesion`, `EncabezadoHistorial`, la fila de detalle); ningún `TopAppBar` | Una barra superior reutilizable |
| Pantallas | 4 (login, captura, historial, detalle). Audio, etiquetado y cierre viven **dentro** de captura; pendientes es un contador; ajustes no existe | 9 destinos más hojas y variantes |
| Componentes | Ninguno compartido salvo 3 chips `internal` dentro de `HistorialScreen.kt` | 20 componentes base |
| Estados vacíos | 4 copias de `Box` + `Text` repetidas sin composable común | Un bloque de estado vacío con icono |
| Strings | `strings.xml` tiene **una** entrada; todo el texto está embebido en Kotlin | Copy en español, traducible |
| `@Preview` | **Ninguno en todo el repo** | Indispensable para iterar visualmente contra los PNG |
| Conectividad | No hay detección de red | Variante "sin conexión" con franja de aviso |

La consecuencia práctica: **hoy no existe ningún token del diseño en el código.** La fase C1 no es un ajuste de colores, es crear el sistema.

### 11.2 Cuatro decisiones que bloqueaban la implementación — resueltas

Los mockups no son un solo lenguaje visual. Analizando los 41 `code.html` aparecen dos familias completas y coherentes, y un residuo de la ronda anterior. Esto había que resolverlo antes de escribir `Color.kt`, porque `MaterialTheme` admite **una** escala de tipografía y **una** de formas.

La evidencia era: las pantallas finales usan **Aegis Tactical Transit** en oscuro (`#101319` de lienzo, acento cian `#8ED5FF`/`#38BDF8`, Plus Jakarta Sans + Manrope, radio 8px) y **Tactical Daylight Security** en claro (`#F8F9FF`, acento `#3456A9`/`#4F6FC4`, Inter, radio 4px). El periwinkle `#7C9DF0` de Nocturne —el sistema de `@ai27-tech/ui` que ya usan los dos frontends web— no aparecía en ninguna pantalla final, sólo en los componentes base.

**D1 — Lenguaje canónico: Nocturne.** Decidido el 2026-09-20. La app adopta los tokens de `pf-library-frontend-components/src/styles/tokens.css`, los mismos del panel interno y el portal del cliente. Se descarta el acento cian de Aegis. La consecuencia buscada es de producto, no de estilo: los tres productos de AI27 se ven como el mismo producto, y `tokens.css` queda como fuente única de la paleta.

**D2 — Inter y una sola escala de formas.** Decidido. Inter para todo, en los dos temas, y 4dp de radio base (2dp para elementos pequeños, 8dp para contenedores, 12dp para hojas). Se descartan Plus Jakarta Sans y Manrope. Un cambio de tema altera colores, nunca geometría ni familia tipográfica.

**D3 — Manda el documento de diseño, no el PNG.** Decidido. `rounded-full` aparece 119 veces en los HTML, pero los dos `DESIGN.md` prohíben las píldoras: *"fully circular pill styling is prohibited"*. Se respeta la prohibición, con las dos excepciones que el propio documento admite: el botón redondo de grabación y los contadores de notificación pequeños, que declaran `CircleShape` en su componente.

**D4 — Cinco pantallas fuera de familia.** Resuelta (2026-09-21). `ajustes_ai27_custodio_reparado`, `ajustes_cierre_de_sesi_n_deshabilitado_reparado`, `cerrar_reporte_estructura_reparado`, `estado_vac_o_base_reparado` y `reporte_abierto_sin_conexi_n_reparado` son de la ronda anterior. Al llegar a esas pantallas en C4 se adaptan a Nocturne en lugar de copiarlas; con D1 resuelto, la adaptación es mecánica. Cada una vive hoy como pantalla/estado Nocturne con preview en claro y oscuro: `AjustesScreen` (y su variante con cierre de sesión deshabilitado por pendientes, `AjustesCierreDeshabilitadoVista*`), `CierreScreen`, `EstadoVacio` + `SinReporte`/pendientes vacío, y `ReporteScreen` con `conectado = false` para la franja `reporte_sin_conexion` (`ReporteSinConexionVista*`).

**Adaptación registrada:** la escala tipográfica de Nocturne está calibrada para una consola de escritorio (cuerpo de 13px, etiquetas de 11px). En esta app se sube entre 2 y 4 puntos, porque se lee con el brazo extendido, en un vehículo en movimiento y a pleno sol. Se conservan los pesos y el tracking del sistema.

### 11.3 Fase C1 — Tokens y tema ✅ hecha (2026-09-20)

Reemplazar la plantilla por el sistema de diseño. Es la fase de la que dependen todas las demás.

1. **`ui/theme/Color.kt`** — los seis colores del template salieron y entraron los dos juegos de tokens de Nocturne, copiados de `tokens.css`. Nombres en inglés, por rol y no por matiz, para conservar la correspondencia uno a uno con la hoja de tokens: son términos técnicos genéricos, no sustantivos de dominio (DEC-024). Sólo se declaran los tokens que el tema consume; el juego completo sigue en `tokens.css` y de ahí se promueve uno cuando se necesite.
2. **`ui/theme/Theme.kt`** — **`dynamicColor` eliminado.** Era el defecto visual más grave del repo: en Android 12+ la app se pintaba con el fondo de pantalla del custodio y el diseño no se veía nunca. Ahora hay dos esquemas completos, con todos los slots de M3 incluidos los `surfaceContainer*`, y se pasa `shapes` al `MaterialTheme`, que antes no se pasaba. El tema **no sigue al sistema**: el parámetro `oscuro` arranca en `true` porque el producto es dark-first, y el conmutador de Ajustes lo controlará en C4.
3. **`ui/theme/Shape.kt`** (nuevo) — escala única de 2/4/8/12dp. Los `RoundedCornerShape` sueltos se retiran conforme se toca cada pantalla; ya salió el de los chips.
4. **`ui/theme/Type.kt`** — escala completa de 13 estilos en Inter. La fuente va **empaquetada** en `res/font/inter_variable.ttf` (variable, eje `wght` vía `FontVariation`, que requiere API 26 = el `minSdk`), no descargable: la app trabaja sin señal y una fuente que dependa de la red no se puede garantizar en campo. La licencia OFL viaja en `assets/licenses/inter-OFL.txt`. Se agregó `TabularNumberStyle` con `tnum` para contadores y cronómetro, que si no se mueven al cambiar de dígito.
5. **`res/values/themes.xml`** — heredaba de `android:Theme.Material.Light.NoActionBar` y estaba fijado a claro, así que el primer frame de una app dark-first salía blanco. Ahora hereda del oscuro, con el lienzo de Nocturne como `windowBackground` y barras transparentes. **No hace falta `values-night/`**: como la app es oscura siempre, un duplicado sería idéntico.
6. **Colores semánticos de estado** — `ui/theme/EstadoColores.kt` (nuevo). M3 no tiene ranuras para los cuatro valores de `EstadoSubida` y forzarlos sobre `error`/`tertiary` los deforma. Se exponen por `CompositionLocal` junto al tema, con la receta de Nocturne (12% de relleno, 25% de borde, color pleno de contenido), y se acceden por `AppTheme.coloresEstado`. `ChipEstadoSubida` ya los consume.
7. **Limpieza** — los `purple_*`/`teal_*` del template salieron de `colors.xml`, que ahora sólo tiene los dos lienzos que necesita el tema de plataforma.

Verificado: `assembleDebug` compila, `testDebugUnitTest` pasa 66 pruebas y la fuente con su licencia quedan dentro del APK. **Sin verificar:** que Inter se dibuje de verdad en lugar de caer a Roboto; eso pide dispositivo o los `@Preview` de C2.

### 11.4 Fase C2 — Biblioteca de componentes ✅ hecha (2026-09-20)

Hoy no existe `ui/components/` y los tres únicos composables compartidos viven dentro de `HistorialScreen.kt`. Crear el paquete y portar los 20 componentes base, cada uno con **`@Preview` en claro y oscuro** — no hay ni un preview en el repo, y sin ellos la única forma de comparar contra los PNG es instalar la app.

| Mockup | Composable | Qué reemplaza hoy |
| --- | --- | --- |
| `barra_de_navegaci_n_base_*` | `BarraNavegacion` | No existe |
| `barra_superior_base_*` | `BarraSuperior` | `BarraSesion`, `EncabezadoHistorial` y la fila de detalle, tres barras a mano |
| `bot_n_primario_de_captura_*` | `BotonPrimario` | `Button` sueltos con estilos distintos |
| `bot_n_secundario_*` | `BotonSecundario` | `TextButton` sueltos |
| `bot_n_con_conteo_de_pendientes_*` | `BotonPendientes` | El `Surface` "N sin subir" de `BarraSesion` |
| `bot_n_redondo_de_grabaci_n_*` | `BotonGrabacion` | El `FilledIconButton` de `ControlesCaptura` |
| `chips_de_categor_a_*` | `ChipCategoria` | Los `FilterChip` de `HojaEtiqueta` |
| `chips_de_estado_de_env_o_*` | `ChipEstadoSubida` | El `internal` que vive en `HistorialScreen.kt` |
| `miniatura_de_archivo_*` | `MiniaturaMedio` | `CeldaMedio`, privada de `CapturaScreen.kt` |
| `rengl_n_de_reporte_*` | `RenglonReporte` | `TarjetaReporte` |
| `rengl_n_de_archivo_pendiente_*` | `RenglonPendiente` | No existe |
| `rengl_n_de_resumen_*` | `RenglonResumen` | `CabezaDetalle`, que apila todo en un `Surface` |
| `rengl_n_con_switch_*` | `RenglonSwitch` | No existe |
| `encabezado_de_fecha_*` | `EncabezadoFecha` | No existe; el historial no agrupa por día |
| `franja_de_aviso_*` | `FranjaAviso` | No existe |
| `estado_vac_o_base_*` | `EstadoVacio` | Cuatro `Box`+`Text` repetidos |
| `contenedor_de_bottom_sheet_*` | `HojaInferior` | El `ModalBottomSheet` de `HojaEtiqueta`, sin contenedor común |
| `campo_de_placa_con_sugerencias_*` | `CampoPlaca` | El `OutlinedTextField` de `NuevoViaje`, sin sugerencias |

La deduplicación es el beneficio real de esta fase: tres barras superiores, tres tratamientos de tarjeta y cuatro estados vacíos se vuelven uno de cada.

**Ejecutado:**
- Se creó `ui/components/` como estaba previsto, con los 18 composables de la tabla (la prosa habla de 20 pero la tabla mapea 18 y se implementaron exactamente esos). Cada uno con `@Preview` de Oscuro y Claro montados sobre un wrapper `Vitrina` que usa el lienzo del tema en lugar del gris del studio.
- Iconografía: se trajeron los 12 vectores de los mockups como drawables de trazo en `res/drawable/` (`ic_archivo`, `ic_reloj`, `ic_ajustes`, `ic_camara`, `ic_subir`, `ic_adjunto`, `ic_chevron`, `ic_carpeta`, `ic_placa`, `ic_alerta`, `ic_cheque`, `ic_reintento`). No se añadió `material-icons-extended`: con R8 apagado inflaría el APK y el trazo de los mockups no vive en ese set.
- `ChipEstadoSubida` salió de `HistorialScreen.kt` y pasa al catálogo con icono (antes el ✓ era un carácter, ahora `ic_cheque` real); las pantallas del historial importan el del paquete. `ChipEstado`/`ChipCiclo` se quedan en lugar por ahora, hasta C4.
- El switch es rectangular (`RenglonSwitch`): Material 3 dibuja el `Switch` como píldora, y D3 lo prohíbe. Track 56x32 con pulgar deslizante animado.
- Se separaron los strings que los componentes necesitan de los del C6 (destinos, estados, pendientes, placa, grabación).

Verificado: `assembleDebug` compila y `testDebugUnitTest` pasa. La comparación visual contra los PNG de `mock-app-design-language/` queda pendiente hasta correr los `@Preview` en el studio.

### 11.5 Fase C3 — Navegación y descomposición de la pantalla gorda ✅ hecha (2026-09-20)

1. **Agregar la barra inferior** con Reporte, Historial y Ajustes en `AppNavHost.kt`, con el punto numérico de pendientes sobre Reporte. Hoy no hay ninguna.
2. **Nuevos destinos:** `captura`, `audio`, `archivos`, `cierre`, `pendientes`, `ajustes`. El `object Rutas` pasa de 4 a 10.
3. **Partir `CapturaScreen.kt`.** Son 777 líneas que hoy contienen la barra de sesión, el formulario de nuevo viaje, el preview de cámara, los controles, la rejilla de 50 miniaturas y cinco `AlertDialog`. Es exactamente la densidad que el rediseño eliminó: el mockup separa `reporte_abierto` (sin rejilla), `captura_de_evidencia_*` (cámara a pantalla completa), `archivos_del_reporte` (la rejilla detrás de un toque), `grabaci_n_de_audio_*` y `cerrar_reporte_*`. Esta descomposición es el corazón del trabajo, y conviene hacerla **antes** de maquillar: mover cajas ya pintadas cuesta más.
4. **Un solo dueño del `Scaffold`.** Hoy `MainActivity` calcula `innerPadding` y no lo usa, y cada pantalla crea su propio `Scaffold` anidado. Con barra inferior eso rompe el padding. Decidir que el `Scaffold` vive en el host de navegación y las pantallas reciben el padding.
5. **Rejilla a 3 columnas.** Hoy es `GridCells.Fixed(4)`; el mockup pide tres por fila.
6. **Diálogos a componentes.** Los cinco `AlertDialog` inline salen a `ui/components/`.

**Ejecutado:**
- Navegación: `object Rutas` saltó a 10 destinos + el grafo anidado `viaje` (INICIO/CAPTURA/AUDIO/ARCHIVOS/CIERRE) que comparte el `CapturaViewModel` de la entrada del grafo vía `hiltViewModel(navController.getBackStackEntry(Rutas.VIAJE))`. El `Scaffold` único vive ahora en `AppNavHost`, con `contentWindowInsets = WindowInsets(0.dp)`, `BarraNavegacion` (badge de pendientes desde el `PendientesViewModel` de Activity) sólo en las pestañas y el gate de sesión movido a `LaunchedEffect(estadoRoot.estado)` con `popUpTo(VIAJE){inclusive=true}` al cerrar sesión. Las pantallas del flujo reciben `statusBarsPadding()` vía el `modifier` que les pasa el host; Historial/Detalle conservan su `Scaffold` anidado hasta C4. Ajustes existe como destino placeholder.
- Descomposición: se borró `CapturaScreen.kt` (777 líneas) y quedaron cinco pantallas en `ui/viaje/` — `ReporteScreen` (raíz con barra de sesión y las tres llamadas), `CapturaEvidenciaScreen` (cámara fullscreen con disparador overlay y `HojaEtiqueta`), `AudioScreen` (cronómetro tabular + reintento/descarte), `ArchivosScreen` (rejilla `GridCells.Fixed(3)` con borrado pre-subida, chip FALLIDA y reintento) y `CierreScreen` (resumen con `resumenMedios`, `RenglonSwitch` de novedad y comentario locales para C4, cerrar con confirmación, descartar con motivo obligatorio). Se conservan `CapturaViewModel`, `CapturaUiState`, `VistaCamara` (con `tomarFoto`) y `HojaEtiqueta` en `ui/captura/`.
- Pendientes: `MedioDao.obtenerPendientesDe` (`Flow<List<MedioEntity>>` por custodio, JOIN con reportes) + `MedioRepository.observarPendientesMedios` → `RoomMedioRepository` y `FakeMedioRepository`; `ui/pendientes/` con `PendientesViewModel` (Activity-scoped; alimenta el badge y `PendientesScreen` con reintento de fallidas) y `PendientesScreen`.
- Diálogos: `ui/components/Dialogos.kt` con `DialogoConfirmacion`, `DialogoDescarte`, `DialogoCerrarSesion` y `DialogoError`, consumidos por las pantallas del flujo.

Verificado: `assembleDebug` compila y `testDebugUnitTest` pasa. Pendiente visual: comparar los `@Preview` y las nuevas pantallas contra los PNG de `mock-app-design-language/` en el studio.

### 11.6 Fase C4 — Pantallas, una por una ✅ hecha (2026-09-20)

Orden sugerido, de la que fija el lenguaje a la que menos riesgo tiene:

1. `reporte_abierto` y `reporte_sin_reporte_abierto` → reescribir `CapturaScreen` como pantalla ligera.
2. `reporte_hoja_crear_reporte` → hoja con `CampoPlaca`, reemplaza el formulario `NuevoViaje`.
3. `captura_de_evidencia_c_mara_visor_negro` → pantalla completa; `VistaCamara` deja de ser un recuadro 16:10 embebido.
4. `captura_de_evidencia_hoja_de_etiquetado` y `hoja_sin_gps` → reescribir `HojaEtiqueta` sobre `HojaInferior`.
5. `grabaci_n_de_audio_nota_de_voz` → pantalla propia; sale de `ControlesCaptura`.
6. `archivos_del_reporte` → pantalla nueva con la rejilla.
7. `cerrar_reporte_estructura` y `cerrar_reporte_novedad_y_comentario` → pantalla propia; hoy es un `AlertDialog`. **Ojo:** el mockup incluye el switch de novedad y el comentario, que el `AlertDialog` actual no captura.
8. `historial_de_reportes_*` → agregar agrupación por fecha y `RenglonReporte`.
9. `detalle_de_reporte_cerrado_*` → `RenglonResumen` en lugar de `CabezaDetalle`.
10. `pendientes_de_subir_*` → pantalla nueva; hoy sólo existe el contador.
11. `ajustes_*` → pantalla nueva: toggle de tema, teléfono del custodio (PHONE_AUTH; era el correo), pendientes y cierre de sesión con su variante deshabilitada.
12. `iniciar_sesi_n_*` → aplicar el lenguaje al login, que hoy es un `Column` sin identidad.

- **C4 completado:** Ajustes con tema nocturno persistente, sesión del custodio y cierre de sesión (bloqueado mientras haya archivos sin subir); la sesión sale de la pestaña Reporte (`SinReporte` con `EstadoVacio` + `HojaInferior` para placa y crear viaje). La novedad y el comentario del cierre se sincronizan hasta Room/Firestore (`hayNovedad` en `Reporte`/DAO/`CerrarReporte`). `HistorialScreen`/`DetalleReporteScreen` quedan sin `Scaffold` anidado, usando `BarraSuperior` (el badge del encabezado se retira; el conteo de pendientes vive en la barra inferior y el badge de la pestaña Reporte) con `RenglonReporte`/`RenglonResumen`/`ChipCiclo`. LOGIN aplica el lenguaje con `CampoPlaca` heredado y tema del sistema.

### 11.7 Fase C5 — Estados que el diseño exige y el código no tiene ✅ hecha (2026-09-20)

Del checklist de `docs/MOBILE_GUIDELINES.md`, esto es lo que falta y el mockup ya dibuja:

- **Sin conexión.** `reporte_abierto_sin_conexi_n` necesita detección de red, que no existe en el repo. Hoy lo único que se ve es el contador "N sin subir", y sólo aparece un mensaje de red si una operación falla.
- **Carga.** `HistorialScreen` y `DetalleReporteScreen` no tienen estado de carga: el primer frame es idéntico al estado vacío, así que "cargando" y "no hay nada" se ven igual.
- **Error.** Ninguno de esos dos tiene campo `error` en su `UiState`; un fallo de lectura no tiene representación en pantalla.
- **Feedback.** No hay `Snackbar` en toda la app. Las confirmaciones de captura y de reintento no avisan nada.
- **Progreso de subida.** No hay barra ni porcentaje, sólo chips de estado.


- **C5 completado:** `CajaCargaReportes`/`CajaErrorHistorial` viven una sola vez en `ui/components/CajasEstado.kt` (canónico, con previews); podadas de `Avisos.kt` junto con sus imports duplicados; `HistorialScreen`/`DetalleReporteScreen` las importan y cablean `titulo`/`mensaje`/`onReintentar`. `HistorialViewModel` con `map`/`onStart`/`catch`; interface `ConectividadMonitor` en `data/conectividad/` con `Flow`. `FakeConectividadMonitor` en tests importa la interface canónica. Compile (`assembleDebug`) y `testDebugUnitTest` verdes.
### 11.8 Fase C6 — Strings y accesibilidad ✅ hecha (2026-09-21)

`strings.xml` tiene una sola entrada y todo el texto de UI está embebido como literales Kotlin, incluidos los `contentDescription`. Antes de dar por terminado el rediseño:

- Migrar todo a `stringResource`, incluidas las descripciones de accesibilidad.
- Verificar el mínimo táctil de 48dp que los dos `DESIGN.md` exigen, y los 56dp que fija §7 de este plan.
- Probar con el texto del sistema al 130% y al 200%: la tipografía nueva es más grande y los layouts densos se rompen ahí primero.
- Contraste en los dos temas, y que ningún estado se comunique **sólo** por color — los chips de subida necesitan icono además del color.

- **C6 completado:** `values/strings.xml` reescrito de punta a punta (strings por feature, comunes y 4 plurals), `ui/error/DominioErrorRecurso.kt` (mapeo `DominioError` → `@StringRes`, sustituye a `DominioErrorMensaje.kt`, borrado) y `error: String?` → `error: Int?` `@StringRes` en los UiState. Todas las pantallas y ViewModels migran a `stringResource`/`pluralStringResource` (login, reporte, captura, audio, archivos, cierre, hoja de etiquetado, historial, detalle, pendientes, ajustes, diálogos y cajas de estado). Accesibilidad: se quita la pinza `size(28.dp)`/`size(40.dp)` que encogía el área interactiva de los `IconButton` de Archivos y CampoPlaca por debajo de 48dp; `ChipCiclo` ya no es sólo color (icono cheque/alerta/reloj); `BotonGrabacion` publica `contentDescription` distinto al grabar (`boton_grabacion_detener`), que ya no queda huérfano. Compile (`assembleDebug`) y `testDebugUnitTest` verdes.
- **Pendiente manual (C7):** escala de texto del sistema al 130%/200% y contraste a pleno sol y de noche en dispositivo real.

### 11.9 Fase C7 — Verificación ✅ hecha (2026-09-21)

- `@Preview` en claro y oscuro de cada componente y cada pantalla; comparar contra el `screen.png` correspondiente.
- `./gradlew testDebugUnitTest` en verde. Los tests de dominio no deberían tocarse: este trabajo es de presentación.
- Revisión en dispositivo real **a pleno sol y de noche**, que es la condición que justifica los dos temas.
- Entrada en `CHANGELOG.md` por cada fase, según `.kiro/steering/changelog.md`.

- **C7 completado:** todas las pantallas pasan a tener `@Preview` en claro y oscuro sobre `Vitrina` (el mismo lienzo de la biblioteca de C2): login, reporte (abierto y sin viaje), captura de evidencia, nota de voz en grabación, archivos (rejilla con pendiente/fallida/completa), cierre con pendientes, historial (cerrado + descartado), detalle, pendientes (con cola y vacío) y ajustes. `DatosDemo` (`ui/previews/`) alimenta las previews con entidades de dominio ficticias; ningún ViewModel las produce. También reciben previews dos componentes que quedaban fuera del inventario de C2: los cuatro diálogos de `Dialogos.kt` y `HojaEtiqueta`. Para que la preview de captura sea fiel al mockup (visor negro) y no intente abrir cámara ni pedir permisos, `CapturaEvidenciaScreen` distingue el modo de inspección (`LocalInspectionMode`) y pinta un visor negro simulado; en dispositivo el comportamiento no cambia. Compile (`assembleDebug`) y `testDebugUnitTest` verdes; los tests de dominio no se tocaron.
- **Pendiente manual (dispositivo real):** comparar cada preview contra su `screen.png` en la IDE (este agente no procesa imágenes), recorrer el flujo completo a pleno sol y de noche con los dos temas, y validar la escala de texto del sistema al 130%/200%.

### 11.10 Lo que no se implementa, aunque aparezca dibujado

Las exclusiones de §2 siguen vigentes y el mockup no las revoca. Si en algún `code.html` aparece selector de galería, mapa, posición en vivo, gráficas, notificaciones, chat o borrado de evidencia, **no se implementa**. Ya ocurrió en este proyecto que un diseño arrastró alcance fuera de lo decidido; el diseño propone forma, no alcance.

Las dos carpetas `point_of_view_realistic_smartphone_camera_shot_*` y las de lenguaje de diseño son material de referencia, no pantallas a construir.

### 11.11 Orden y dependencias

```
D1, D2, D3, D4  (decisiones, bloquean todo)
      │
      ▼
C1 tokens y tema ✅ ──► C2 componentes ✅ ──► C3 navegación ✅
                                          │
                                          ▼
                                    C4 pantallas ✅ ──► C5 estados ✅
                                          │
                                          ▼
                          C6 strings y accesibilidad ✅ ──► C7 verificación ✅
```

C2 puede empezar en cuanto C1 tenga los tokens, aunque falten formas y tipografía. C3 no depende de C2 para la parte de navegación, así que la barra inferior y los destinos nuevos se pueden adelantar. C5 y C6 son transversales y se cierran al final, no pantalla por pantalla.


---

## 12. Atención de hallazgos críticos de la auditoría (2026-09-21)

El 2026-09-21 se levantó una auditoría técnica y de experiencia de uso de toda la app, que
vive en `docs/AUDIT.md` con **95 hallazgos** clasificados por severidad y con estado de
verificación. Esta sección registra el plan aprobado para atender **solo los cinco críticos**:
los tres `BLOQUEANTE` que impiden entregar a campo y los dos `CRÍTICO` de privacidad.

El resto de los 90 hallazgos queda **documentado y sin tocar**. Eso es deliberado, y §12.5
los lista para que nadie los dé por resueltos al leer esta sección.

### 12.1 Los cinco hallazgos

| ID | Hallazgo | Por qué es crítico |
| --- | --- | --- |
| **B1** | `Task<T>.esperar()` (`data/remote/AuthDataSource.kt:52-64`) resuelve solo si `resultado.result != null`, y una `Task<Void>` exitosa tiene `getResult() == null` | Toda escritura a Firestore lanza excepción. `asegurarReporte()` corre antes de Storage, así que **ni un byte sale del dispositivo**: todo medio termina en `FALLIDA`, el badge nunca baja y el cierre de sesión queda bloqueado para siempre |
| **B2** | `MediaRecorder(context)` (`data/local/media/GrabadoraMediaRecorder.kt:22`) es API 31 con `minSdk = 26`, sin gate, y el `catch (_: Exception)` no atrapa el `NoSuchMethodError` | Cualquier equipo con Android 8, 9, 10 u 11 **cierra la app** al grabar una nota de voz |
| **B3** | `lintDebug` falla con 141 errores pero `lintVitalRelease` pasa en verde, porque solo aborta en severidad `Fatal`; no hay bloque `lint { }` | B2 se puede empaquetar y entregar sin que nada lo detecte. 138 de los 141 errores son ruido de `MissingTranslation` que tapa los 3 reales |
| **S1** | Cada foto se escribe **siempre** una segunda vez en `Pictures/AppCustodio` (`FotosPublicasMediaStore`, cableado sin condición) y **nunca se borra** | Fuga de evidencia del cliente a la galería del equipo compartido, a WhatsApp y al respaldo de Google Fotos. Contradice la premisa de `filesDir` privado de §4 y vuelve cosmético el borrado pre-subida |
| **S2** | `allowBackup="true"` con `backup_rules.xml` y `data_extraction_rules.xml` como plantillas vacías | La base Room y `filesDir/medios` entran al respaldo en la nube y a la transferencia entre dispositivos. Contradice §4, que dice que `filesDir` está excluido de Auto Backup |

### 12.2 Alcance acordado

| # | Decisión |
| --- | --- |
| R1 | **Estrictamente los cinco.** Los demás hallazgos, incluidos los de severidad ALTO, quedan fuera |
| R2 | **S1 se elimina por completo**: se borra `FotosPublicas` entero, no se vuelve privado |
| R3 | Se construye la infraestructura de prueba que hoy no existe: test de `data/remote` contra el Emulator Suite, `work-testing`, y la suite de contrato que alinea los fakes divergentes (P2) |
| R4 | El plan se registra en esta sección §12 |

### 12.3 Decisiones de diseño

**D1 — B1 se arregla con `await()`, no parcheando el helper.** Se sustituye el
`Task<T>.esperar()` propio por `await()` de `kotlinx-coroutines-play-services`. Es la
implementación oficial y probada de este contrato, resuelve `Task<Void>` correctamente y trae
la **propagación de cancelación** que hoy falta (el helper no tiene `invokeOnCancellation`, así
que un worker cancelado deja el `UploadTask` corriendo).

Consecuencia aceptada y registrada: al borrar el helper no queda función propia que probar con
un test unitario barato, así que la red de regresión de B1 pasa a ser el test de integración
contra el emulador (T7). Se descartó arreglar el `when` y declarar `esperar(): T?`, que sí
permitía un test JVM puro pero conservaba código de concurrencia hecho a mano y no arreglaba la
cancelación.

**D2 — Los tests de `work-testing` documentan defectos aceptados.** Con R1 no se arreglan C4
(`ExistingWorkPolicy.REPLACE` cancela subidas en vuelo) ni E3 (`reconciliar()` encola medios sin
etiqueta), y los tests de T9 se topan con ambos. Se escriben como **tests de caracterización**:
afirman el comportamiento actual con un comentario que nombra el ID de auditoría y por qué está
aceptado hoy. No se usa `@Ignore`, porque un test ignorado no protege nada. Así, el día que se
arregle C4, el test falla y obliga a actualizarlo conscientemente.

**D3 — Corregir dos pruebas verdes no es maquillar un fallo.** `docs/GENERIC_RULES.md` prohíbe
modificar pruebas para ocultar fallos. Aquí es lo inverso: `CerrarReporteTest`
(`assertEquals(3, cerrado.mediosSubidos)`) y `SubidaMedioProcesoTest`
(`assertNull(subido.localPath)`) afirman comportamiento **que no existe en producción**, porque
los fakes divergen del DAO. Se corrigen para que digan la verdad, y el `CHANGELOG.md` lo deja
explícito para que quede trazable.

### 12.4 Tareas ✅ todas hechas (2026-09-21)

| # | Tarea | Estado |
| --- | --- | --- |
| T1 | Registrar este plan como §12 de `docs/PLAN.md` | ✅ hecha |
| T2 | **B2** — gate de `SDK_INT` en `MediaRecorder` y `catch (_: Throwable)` en esa ruta | ✅ hecha |
| T3 | **S1** — eliminar la copia a la galería pública en sus 9 puntos de contacto | ✅ hecha |
| T4 | **S2** — `allowBackup="false"` más exclusiones explícitas en los dos XML | ✅ hecha |
| T5 | Batch de build: `kotlinx-coroutines-play-services` y `work-testing` | ✅ hecha (Sync hecho) |
| T6 | **B1** — sustituir los `.esperar()` por `.await()` y borrar el helper | ✅ hecha |
| T7 | Test de `data/remote` contra el Emulator Suite, con el acceso al emulador documentado | ✅ hecha |
| T8 | **P2** — suite de contrato Room ⇄ fake y corrección de las dos aserciones falsas | ✅ hecha |
| T9 | `work-testing` — escalera de reintentos y reconciliación (caracterización según D2) | ✅ hecha |
| T10 | **B3** — podar `values-es-rMX/` y cerrar el gate de lint | ✅ hecha (Sync requerido) |
| T11 | Cierre: estado en §12, marcas en `AUDIT.md` y entrada de `CHANGELOG.md` | ✅ hecha |

Dependencias: T2, T3 y T4 son independientes entre sí y sólo dependen de T1. T5 agrupa los dos
cambios de dependencias para que haya **una sola** pausa de Sync antes del bloque de pruebas.
T6 habilita T7. T8 y T9 son paralelos a T6. T10 va al final porque activar `abortOnError` antes
de arreglar los errores reales bloquearía todos los builds.

Dos pausas de Sync en total (T5 y T10), agrupadas a propósito para no interrumpir cinco veces,
según la regla de `AGENTS.md`.

### 12.4.1 Resultado y verificación

**Los cinco hallazgos críticos quedaron cerrados.** Estado de las pruebas al terminar:

| Suite | Antes | Después |
| --- | --- | --- |
| `testDebugUnitTest` | 71 pruebas | **111 pruebas, 0 fallos** |
| `connectedDebugAndroidTest` | 1 (andamiaje) | **23 pruebas, 0 fallos** |
| `lintDebug` | **141 errores** | **0 errores** |
| `lintVitalRelease` | pasaba en verde siendo ciego | pasa, y ahora **sí** detiene el release |

Verificado por ejecución, no por lectura:

- **B1 comprobado en los dos sentidos.** Con el helper viejo reintroducido en un solo punto de
  llamada, **7 de las 8** pruebas de `RemotoEmuladorTest` fallan contra el Emulator Suite con el
  mensaje `IllegalStateException: Tarea Auth sin resultado ni error`. Restaurado el arreglo,
  vuelven las 8 a verde. Es la prueba de que el defecto era real y total, y de que la red nueva
  lo atrapa.
- **B3 comprobado en los dos sentidos.** Con el defecto de B2 reintroducido, `assembleRelease`
  **falla** con `Call requires API level 31 (current min is 26) [NewApi]`. Antes de esta tanda
  ese mismo APK se empaquetaba y se firmaba sin una sola queja.
- **B2** ya no aparece como `NewApi` en el reporte de lint.
- **S1** verificado por búsqueda: cero referencias a `FotosPublicas`, `copiarAPublica`,
  `WRITE_EXTERNAL_STORAGE` o `MediaStore` en todo `app/src`.
- **S2** verificado en el manifest fusionado: `android:allowBackup="false"`.
- El APK de release sigue firmado con `CN=App Custodio, OU=AI27` y la configuration cache se
  sigue reutilizando.

**Tres correcciones al plan que salieron de ejecutarlo:**

1. **`abortOnError = true` no cerraba el gate de B3.** `lintVitalRelease` solo considera issues
   de severidad **Fatal**, y `NewApi` es **Error**, así que ni se reportaba. Comprobado con el
   experimento descrito arriba: el release seguía pasando. Lo que cierra el gate es **promover a
   Fatal** los issues que significan "esto truena o filtra en campo". Está documentado en el
   comentario del bloque `lint { }` para que nadie lo simplifique.
2. **D1 resultó menos costoso de lo previsto.** Se había aceptado que al borrar el helper propio
   no quedaría un test unitario barato para B1. Resultó que sí: `Tasks.forResult<Void>(null)` y
   `await()` funcionan en una prueba JVM de host, porque la API de `Tasks` es Java puro y
   `await()` usa un executor directo. Son 4 pruebas que corren en CI sin dispositivo, además de
   las 8 de integración.
3. **`kotlinx-coroutines-play-services` ya estaba en el classpath**, arrastrado por Firebase. T5
   no añadió una dependencia nueva: hizo explícito un transitivo del que el código iba a
   depender de forma directa.

**Pendiente de comprobación manual** (no hubo forma de verificarlo con lo disponible):

- Grabar una nota de voz en un equipo con **Android 8, 9, 10 u 11**. El teléfono de pruebas es
  API 36, así que la rama del gate de B2 que usa el constructor antiguo **no se ejercitó**; lo
  que se comprobó es que lint ya no la marca y que el release la exige.
- `adb shell bmgr backupnow com.ai27.app.custodio` para confirmar que no se respalda nada.
- Capturar y mirar la galería del dispositivo para confirmar que `Pictures/AppCustodio` ya no
  recibe copias.
- **Aviso operativo:** en los equipos que **ya** tienen la app instalada, las copias que crearon
  las versiones anteriores siguen en `Pictures/AppCustodio`. Quitar el código no las borra; hay
  que limpiarlas a mano al actualizar.

**Hallazgos nuevos, anotados en `docs/AUDIT.md` y sin atender:** `lintVital` solo mira severidad
Fatal (afecta a cómo se configura cualquier gate futuro), y `FirebaseNetworkException` no se
puede instanciar en pruebas JVM porque su constructor usa `android.text.TextUtils`.

### 12.5 Fuera de alcance — no dar por resuelto

Estos hallazgos **siguen abiertos** después de este plan. Están descritos con su evidencia en
`docs/AUDIT.md`:

- **Consistencia de datos:** C1 (el resultado de `actualizarContadores` se descarta y el reporte
  sale de la reconciliación para siempre), C2 (`sha256` nunca llega a Firestore y las reglas
  impiden rellenarlo), C3 (carrera lost-update de contadores), C4 (`REPLACE` cancela subidas en
  vuelo), C5 (tope de 4 intentos en ~70 s sin clasificar permanente vs transitorio), C6 (cierre
  no transaccional: un reporte puede marcarse completo sin estarlo), C7, C8, C9.
- **Pérdida de evidencia:** E1 (el doble toque descarta fotos en silencio), E2 (todo fallo de
  cámara es mudo), E3 (los medios sin etiquetar suben igual y bloquean la sesión para siempre),
  E4 (disco lleno tira la app), E5 (la nota de voz graba silencio o audio corrupto sin que nadie
  lo note), E6 (la cola no filtra por `custodioUid` en equipos compartidos), E7–E12.
- **Seguridad y reglas:** S3 (Storage sin tope de tamaño ni `contentType`), S4 (`update` permite
  sobrescribir evidencia ya cerrada), S5 (Firestore sin validación de forma), S6 (contadores sin
  cota), S7 (`ACTIVO → CERRADO` no acota qué más cambia), S8 (`ACCESS_COARSE_LOCATION` declarado
  pero no solicitado), S9–S14.
- **Rendimiento:** todo el grupo R (R1–R16), incluidos R1 (la app no abre en la cámara) y R3 (el
  grafo de navegación se reconstruye en cada emisión de la cola).
- **Fricción de uso:** todo el grupo U (U1–U16), incluidos U1 (la hoja de etiquetado bloquea la
  ráfaga y no tiene salida) y U8 (carga, error y vacío son indistinguibles en Historial).
- **Accesibilidad:** todo el grupo A (A1–A10), incluidos A2 (`colorScheme.outline` usado como
  color de texto, con 1.37:1 de contraste) y A4 (el teclado tapa el botón "Entrar").
- **Proceso:** T1 (no hay CI), T2 (`versionCode = 1` fijo), T3 (esquema de Room no exportado ni
  versionado), T4 (el índice único parcial se recrea en cada `onOpen`), T5 (R8 desactivado),
  T6–T15, salvo lo que toque este plan.
- **Pruebas:** P3–P7, salvo P2, que sí entra por R3.

### 12.6 Aviso de riesgo para Producto

**T6 hace que las subidas funcionen por primera vez, y en ese momento C1, C2 y C4 empiezan a
producir datos incorrectos en Firestore.** Con el alcance acordado es una consecuencia aceptada,
no un descuido, pero conviene que Producto lo sepa antes de entregar a los 37 custodios:

- Todos los documentos de `medios` llevarán `sha256` nulo, y como `medios` es create-only en las
  reglas, **no se puede rellenar después**. La evidencia queda sin integridad verificable en el
  servidor (C2).
- `mediosSubidos` y `completo` pueden quedar desfasados respecto de lo que realmente hay en
  Storage, así que el panel del analista puede mostrar un viaje como completo cuando falta
  evidencia, o incompleto cuando no falta (C1, C3).
- Un pull-to-refresh en Pendientes cancela las subidas en curso y las reinicia desde cero (C4).

Ninguno de los tres impide entregar; los tres hacen que el dato que ve el analista sea menos
confiable de lo que aparenta. Son los primeros candidatos para la siguiente tanda.


---

## 13. Eliminación de la pérdida de evidencia (E1–E6) — ✅ hecha (2026-09-22)

Segunda tanda sobre `docs/AUDIT.md`, después de los cinco críticos de §12. Ataca los **seis
hallazgos del grupo "pérdida de evidencia"**, que comparten un patrón: la app pierde o corrompe
evidencia **sin decírselo al custodio**, que es el peor modo de fallo posible en un producto cuya
premisa es que la evidencia llega.

Importan más ahora que antes de §12: con B1 arreglado las subidas funcionan, así que lo que se
pierda aquí ya no es "se queda local", es "no existe".

### 13.1 Los seis hallazgos

| ID | Hallazgo | Evidencia en el código |
| --- | --- | --- |
| **E1** | El doble toque del disparador descarta fotos en silencio | `disparoHabilitado` (`CapturaEvidenciaScreen.kt:126-131`) depende de `estado.capturando`, que solo se pone en `true` en el callback de éxito de CameraX, **después** de decodificar, rotar y comprimir. El 2.º y 3.er toque caen en un `return` mudo. El flash también se dispara ahí, así que el toque no produce ninguna señal inmediata — y por eso el custodio vuelve a tocar |
| **E2** | Todo fallo de cámara y de procesamiento es mudo | `VistaCamara.kt:79` es `catch (_: Exception) {}` y `:83` es `onError` vacío. `tomarFoto` no tiene forma de reportar un fallo: su única salida es `alTenerFoto`. Aquí también muere un OOM de `Miniaturas.fotoOriginalYJpeg` |
| **E3** | Los medios sin etiquetar suben igual y bloquean la sesión para siempre | `MedioDao.obtenerPendientes()` no mira `etiqueta`, y `reconciliar()` la usa tal cual. `medioPendienteDeEtiquetar` vive **solo en memoria**, así que tras muerte de proceso la hoja no reabre nunca, y `ArchivosScreen.CeldaArchivo` no es clickeable: no hay forma de etiquetar después. Como `contarPendientes` cuenta todo lo `!= COMPLETA`, ese medio mantiene el badge en ≥1 y el cierre de sesión deshabilitado |
| **E4** | Disco lleno tira la app en pleno disparo | `FilesDirAlmacenArchivos.guardar` hace `writeBytes` desnudo y devuelve `String`. No hay `try/catch` en toda la cadena hasta el `viewModelScope.launch` de `capturarFoto`, y `DominioError` no tiene ningún caso de E/S |
| **E5** | La nota de voz graba silencio o audio corrupto sin que nadie lo note | `Grabadora` no tiene `pausar`/`reanudar`, no es `suspend`, y `GrabadoraMediaRecorder` no registra `setOnErrorListener` ni `setOnInfoListener` ni usa `setMaxDuration`. `AudioScreen` no observa el ciclo de vida, `CapturaViewModel` no tiene `onCleared` y `Grabadora` es `@Singleton`, así que el micrófono queda tomado. El estado está duplicado entre ViewModel y singleton, y la UI nunca lee el segundo |
| **E6** | En equipo compartido se quema la evidencia del custodio anterior | Ni `MedioDao.obtenerPendientes()` ni `ReporteDao.obtenerPendientesSubida()` filtran por `custodioUid`, y la segunda tampoco por fecha. `cerrarSesion` es `auth.signOut()` y nada más: no cancela trabajo ni limpia nada |

### 13.2 Alcance acordado

| # | Decisión |
| --- | --- |
| R1 | **Solo E1–E6.** Las reglas de Firebase (S3–S8) quedan fuera; no se cambia ni se despliega nada de Firebase |
| R2 | **E5 se resuelve pausando:** al salir de la app la nota se pausa sola y el custodio la reanuda pulsando el botón al volver. Sin servicio en primer plano, sin notificación permanente y sin permisos nuevos |
| R3 | Si el proceso muere con una nota a medias, **se avisa** en lugar de perderla en silencio |
| R3 bis | **T5 no borra datos locales** (decidido al ejecutar, 2026-09-21). Cancela el trabajo de subida antes del `signOut` y arregla el guard de pendientes, y nada más. Razones: el aislamiento entre custodios ya lo da el filtro por uid de T3; en disco solo quedan miniaturas porque `marcarSubidaCompleta` ya borra el original; Historial lee solo de Room y no hay forma de recuperarlo del servidor, así que purgar filas borraría 30 días de historial de forma irrecuperable en la app; y si el custodio saliente tenía un viaje `ACTIVO`, el purgado se lo llevaría, que es pérdida de evidencia. El purgado pasa a Producto como decisión de **retención**, no como arreglo de bug |

### 13.3 Decisiones de diseño

**D1 — Los fallos de cámara se reportan por una interfaz propia, no con un log.** Se añade
`RegistroDeFallos` en `data/` con una implementación sobre `FirebaseCrashlytics.recordException`,
inyectada por Hilt. Razón: no queremos `FirebaseCrashlytics.getInstance()` dentro de un composable
ni del ViewModel, y así se puede falsear en pruebas. Es una interfaz, una implementación y un
binding. La alternativa —un `Log.w` y ya— dejaría un fallo de cámara en campo indiagnosticable,
que es la mitad del hallazgo E2.

**D2 — Los medios sin etiquetar siguen contando como pendientes.** No se sacan del badge: un medio
sin etiquetar **es** trabajo sin terminar, y permitir cerrar sesión con evidencia sin clasificar
sería cambiar un problema por otro. Lo que cambia es que ahora **se pueden resolver**. La queja de
U6 (el badge mezcla tres cosas distintas) sigue abierta y fuera de alcance.

**D3 — Un arreglo de una línea fuera de alcance, como prerrequisito.**
`AjustesViewModel.solicitarCerrarSesion` lee `interno.value.pendientesSinSubir`, que nunca se
actualiza y por tanto **siempre vale 0**: el guard es código muerto (hallazgo E7). Lo único que
bloquea hoy es que el botón esté deshabilitado. Como T5 va a borrar los datos del custodio
saliente, hace falta que ese guard funcione de verdad, porque es la invariante que hace segura la
limpieza. Se declara prerrequisito, no ampliación de alcance.

**Sin pausas de Sync en toda la tanda.** Ninguna tarea necesita dependencias nuevas:
`LocalLifecycleOwner` ya se usa en `VistaCamara.kt` y `firebase-crashlytics` ya está declarado.
Los scripts de build no se tocan.

### 13.4 Tareas

| # | Tarea | Estado |
| --- | --- | --- |
| T0 | Registrar este plan como §13 de `docs/PLAN.md` | ✅ hecha (2026-09-21) |
| T1 | **E1 + E2** — cerrojo síncrono del obturador, flash en el toque, y `tomarFoto` que sí reporta fallos | ✅ hecha (2026-09-21) |
| T2 | **E4** — `AlmacenArchivos.guardar` devuelve `Result`, con `DominioError.SinEspacio` nuevo | ✅ hecha (2026-09-21) |
| T3 | **E3a + E6a + E6b** — acotar la cola por `custodioUid`, por etiqueta y por fecha | ✅ hecha (2026-09-21) |
| T4 | **E3b** — los medios sin etiquetar se pueden etiquetar después | ✅ hecha (2026-09-21) |
| T5 | **E6c** — cerrar sesión cancela el trabajo y limpia los datos del uid saliente | ✅ hecha (2026-09-21), con alcance recortado: ver R3 bis |
| T6 | **E5** — pausa y reanudación de la nota de voz, más listeners de error | ✅ hecha (2026-09-22) |
| T7 | Cierre: estado en §13, marcas en `AUDIT.md` y entrada de `CHANGELOG.md` | ✅ hecha (2026-09-22) |

**Por qué E1 y E2 van juntas:** están acopladas. El cerrojo del obturador solo se puede liberar de
forma fiable si `tomarFoto` sabe reportar un fallo; sin E2, un error dejaría el disparador muerto
para siempre.

**Por qué T3 agrupa tres cosas:** los filtros por uid, por etiqueta y por fecha tocan los mismos
DAO, los mismos repositorios y la misma suite de contrato. Separarlos multiplicaría el churn sin
ganar nada.

**Dependencias:** T3 habilita T4 y T5. T1, T2 y T6 son independientes entre sí.

### 13.5 Pruebas de caracterización que van a fallar, a propósito

La tanda de §12 dejó tres pruebas que fijan defectos aceptados (decisión D2 de §12). Dos de ellas
cubren hallazgos que **esta** tanda arregla, así que van a fallar y hay que convertirlas:

- `caracterizacion_E3_reconciliar_encola_medios_SIN_etiquetar` → pasa a afirmar que un medio sin
  etiqueta **no** se encola. ✅ convertida en T3 (`reconciliar_no_encola_los_medios_SIN_etiquetar`).
- `caracterizacion_E6_la_cola_no_filtra_por_custodio` → pasa a afirmar que la cola trae **solo** lo
  del custodio en sesión. ✅ convertida en T3
  (`reconciliar_solo_encola_evidencia_del_custodio_pedido`).
- `caracterizacion_C4_encolar_de_nuevo_REEMPLAZA_el_trabajo_anterior` **se queda como está**,
  porque C4 sigue fuera de alcance.

Que fallen es la señal de que el mecanismo funcionó: se escribieron sin `@Ignore` precisamente para
que obligaran a una actualización consciente.

### 13.6 Fuera de alcance — no dar por resuelto

Sigue abierto: **C1, C2, C3, C4, C6** (integridad del dato — los más urgentes después de esta
tanda), C5, C7, C8, C9, **E8** (medios pegados en
`EN_PROGRESO` que no se pueden reintentar a mano, aunque T3 toque la misma consulta), E9, E10, E11,
E12, **todo S3–S14** (reglas de Firestore y Storage, permisos, `ModoEmulador`, Crashlytics), **todo
el grupo R** (rendimiento), **todo U** (fricción de uso), **todo A** (accesibilidad) y T1–T15.

**E7 salió de esta lista:** entró como prerrequisito de una línea (D3) y acabó cerrado del todo en
T5, con el guard revalidado también al confirmar y cuatro pruebas que antes no existían.

### 13.7 Riesgos

1. **T3 cambia cuatro firmas públicas de repositorio.** El compilador encuentra todos los
   llamadores y la suite de contrato de §12 obliga a que Room y los fakes se muevan juntos, así que
   el riesgo real es bajo — pero el diff es ancho.
2. **T5 borra datos.** Es la única tarea de la tanda que destruye información. Solo se ejecuta sin
   pendientes y solo del uid saliente; las pruebas con dos custodios son el control de que no se
   lleva lo ajeno. Requiere confirmación explícita antes de activar el borrado.
   → **Resuelto al ejecutar: el borrado no se hizo.** Ver R3 bis en §13.2. Al pedir la confirmación
   quedó claro que el purgado no aportaba (T3 ya aísla por uid, y en disco solo quedan miniaturas) y
   sí podía llevarse el historial y un viaje `ACTIVO`. El usuario eligió la opción sin borrado.
3. **T6 es la tarea con más superficie de ciclo de vida,** y el caso de muerte de proceso **no se
   puede arreglar, solo avisar**: `pause()` no cierra el índice del `.m4a`, así que si Android mata
   el proceso el archivo es irrecuperable. Queda dicho de antemano para que no parezca que quedó a
   medias.

### 13.8 Resultado y verificación

Las siete tareas quedaron hechas y E1–E7 están marcados `✅ ATENDIDO` en `docs/AUDIT.md`, cada uno
con una nota de **cómo** quedó atendido y qué sigue abierto, porque tres se cerraron con un alcance
distinto al que proponía el hallazgo.

| Medida | Antes de §13 | Después |
| --- | --- | --- |
| Pruebas unitarias | 114 | 142 |
| Pruebas instrumentadas | 23 | 30 (verificado en dispositivo, 2026-09-22) |
| Errores de `lintDebug` | 0 | 0 |

**Verificado:** `assembleDebug`, `testDebugUnitTest` (142/0), `lintDebug` (0 errores, 36 avisos —
todos preexistentes) y `compileDebugAndroidTestKotlin`.

**Sin verificar todavía en el momento del cierre de T7:** las siete pruebas instrumentadas nuevas (dos de cancelación de trabajo en `ReconciliacionColaTest`, cinco en `GrabadoraMediaRecorderTest`) no se habían ejecutado en un equipo — el teléfono se desconectó a media T5. **Verificado después, 2026-09-22 11:37:** con el teléfono reconectado, `connectedDebugAndroidTest` corrió completo — 30 pruebas / 0 fallos en CPH2769, incluidas las 7 nuevas.

**Dos cosas que aparecieron al ejecutar y no estaban en el plan:**

1. `FakeMedioRepository.observarDelReporte` devolvía un `StateFlow` recién creado con un snapshot y
   **nunca reemitía**, mientras el de Room está vivo. Dos pruebas de T4 fallaron por eso y no por el
   código de producción. La suite de contrato de §12 no lo detectaba porque comparaba lecturas
   puntuales, no emisiones; ahora hay una prueba de contrato que sí, y se comprobó que falla al
   revertir el fake.
2. `observarSinEtiquetaDelReporte`, añadido al DAO en T3 pensando en T4, quedó sin llamadas cuando
   T4 resultó más simple derivando la lista del estado. Se **retiró**: una query de DAO sin uso es
   peor que no tenerla.



---

## 14. Etiquetas en dos categorías con selección múltiple (§3.1 destrabado por Operaciones)

Operaciones entregó por fin el insumo que §3.1 marcaba como pendiente y que bloqueaba la salida a
producción: el catálogo real de etiquetas. Llegó como dos cosas distintas que conviene no
confundir:

1. **Cómo reportan hoy el estado del viaje** — "sin novedad", "en recorrido", "detenido", "baños".
   Describen **qué está pasando ahora**, no qué muestra la foto.
2. **La imagen `Media.jpeg`** (en la raíz del repo): el codebook de **claves operativas** de radio,
   con 150+ entradas. **No es una taxonomía de etiquetas**: es el vocabulario compartido de la
   comunicación por radio. Volcar las 150 como chips sería inusable en un teléfono.

Y por separado, "suben sus fotos o el audio de cómo está el transporte" es la **evidencia** que se
captura, que es el `Medio` que ya se etiqueta.

**El hallazgo de fondo:** Operaciones mezcló dos ejes. El modelo de hoy tiene **una** etiqueta por
archivo (`Medio.etiqueta: String?`, chip de selección única). Lo que describen es una *situación*
("sin novedad", "en recorrido") **más** evidencia sobre el transporte. No son el mismo eje.

### 14.1 Decisión: dos ejes, selección múltiple, catálogo curado de las claves

Se adopta la **Opción B**: dos categorías de etiqueta en lugar de una lista plana. La imagen de
claves se trata como el **vocabulario maestro del que se curan las categorías**, no como la lista de
chips.

- **Situación** (qué está pasando): Sin novedad, En recorrido, Detenido, Parada por baño, Con
  novedad.
- **Evidencia** (qué muestra el archivo): Estado del transporte, Falla mecánica, Accidente, Robo o
  asalto, Desvío de ruta, Otro.

Un archivo puede llevar etiquetas de **ambas** categorías, y **varias** dentro de cada una
(selección múltiple). El catálogo queda **fijo en la app por ahora**; será configurable desde
Firebase cuando llegue el gancho de catálogo remoto (§3.1 / B5), y la forma del modelo ya no
cambiará entonces.

### 14.2 Decisiones bloqueadas (respuestas de Operaciones)

| # | Pregunta | Decisión |
| --- | --- | --- |
| A | ¿Qué cuenta como "etiquetado" para que el archivo suba? | **Al menos una etiqueta de Situación.** La Evidencia es opcional. Coincide con "normalmente quieren mostrar qué está pasando ahora"; exigir ambas categorías en cada captura molestaría al custodio |
| B | ¿Hay equipos en campo con datos reales? | **No, todavía no.** Aun así se hace **migración de Room como es debido** (versión 1 → 2), no `fallbackToDestructiveMigration()`: es barato aquí y es lo responsable en una app cuya premisa es no perder evidencia |
| C | ¿Cómo se guarda la lista en Room? | **`List<String>` serializada en una sola columna vía `TypeConverter`.** Catálogo fijo y necesidad solo legible (D) no justifican una tabla puente |
| D | ¿Algo externo consume `medios.etiqueta`? | **No.** Verificado en el repo: ninguna regla de Firestore/Storage valida el campo (los valores en `reglas.test.js` son solo fixtures), no hay panel de analista ni Cloud Function. Seguro renombrar `etiqueta` (string) → `etiquetas` (array) en el documento |

Nota de la verificación de D: los fixtures de `reglas.test.js` ya usan valores distintos
(`carga`, `descarga`, `novedad`) de los del catálogo hardcodeado, lo que confirma que nada externo
depende de cadenas de etiqueta concretas. Existe además un doc `catalogos/c1` con forma
`{ etiquetas: [...] }` (el futuro gancho de catálogo remoto de B5); el campo `etiquetas` (array) en
el medio no colisiona con él.

### 14.3 Decisiones de diseño

**D1 — El filtro de la cola pasa a "tiene Situación", no "tiene etiqueta".** Hoy la cola excluye
medios con `etiqueta IS NOT NULL` (E3/§13). Con dos categorías y selección múltiple, "etiquetado" es
ambiguo. Se define como **tiene al menos una etiqueta de Situación**. Para que la consulta SQL siga
siendo simple e indexable sobre una columna serializada, se añade una columna denormalizada
booleana `tieneSituacion` que la migración y el `etiquetar` recalculan; el predicado de
`obtenerPendientes` pasa a `tieneSituacion = 1`. La alternativa —traer todo y filtrar en Kotlin—
rompería la propiedad de que la cola es una sola consulta acotada.

**D2 — Migración de Room 1 → 2 que preserva el valor viejo.** No existe infraestructura de
migración (versión 1, sin migraciones, sin export de esquema; esto es el hallazgo T3 de la
auditoría, que sigue abierto). La migración convierte la columna `etiqueta TEXT` en la nueva
`etiquetas TEXT` (JSON de un elemento cuando había valor, arreglo vacío cuando era null) y calcula
`tieneSituacion` a partir de si ese valor pertenece a la categoría Situación del catálogo. Aunque no
haya equipos en campo (decisión B), se hace bien: el costo es una función de migración y su prueba.

**D3 — El renombre en Firestore es un corte limpio, no doble escritura.** Como D confirmó que nadie
lee `medios.etiqueta`, el documento pasa directo a `"etiquetas": [String]`. No se escribe el campo
viejo en paralelo: no hay lector que proteger y la doble escritura solo dejaría deuda.

**D4 — `EtiquetarMedio` valida la lista y exige Situación.** El caso de uso recibe `List<String>`,
valida que cada id exista y esté activo en el catálogo, y rechaza con un `DominioError` nuevo
(`FaltaSituacion`) si no hay ninguna etiqueta de Situación. `EtiquetaInvalida` se conserva, ahora
por-id sobre la lista.

**Pausa de Sync:** subir la versión de la base y añadir `TypeConverter`s **no** toca scripts de
build, así que no hace falta Sync. Si en el camino se añade un directorio de export de esquema
(cerraría parte de T3), eso sí es un cambio de `build.gradle.kts` y se para para el Sync en Android
Studio, según la regla de `AGENTS.md`.

### 14.4 Alcance

| # | Decisión |
| --- | --- |
| R1 | **Solo el cambio de etiquetas.** No se abren los hallazgos de integridad (C1–C9) ni las reglas de Firebase (S3–S14), aunque el renombre del campo toque el mapper que también arrastra el `sha256` nulo de C2 |
| R2 | El catálogo queda **fijo en la app**; el gancho remoto de B5 no se implementa en esta tanda, pero la forma del modelo se deja lista para él |
| R3 | Se actualizan las pruebas de contrato, worker, emulador y caso de uso a la forma de lista y a la nueva semántica del filtro |

### 14.5 Tareas

| # | Tarea | Estado |
| --- | --- | --- |
| T0 | Registrar este plan como §14 de `docs/PLAN.md` | ✅ hecha |
| T1 | **Dominio** — `enum CategoriaEtiqueta`, `categoria` en `Etiqueta`, `Medio.etiqueta` → `etiquetas: List<String>`, `DominioError.FaltaSituacion` | ✅ hecha |
| T2 | **Catálogo** — reemplazar las 5 etiquetas provisionales por el set curado de dos categorías | ✅ hecha |
| T3 | **Room** — `MedioEntity.etiquetas` + `tieneSituacion`, `TypeConverter`, `etiquetar` y `obtenerPendientes` nuevos, versión 2 + `Migration(1,2)`, mappers | ✅ hecha |
| T4 | **Caso de uso** — `EtiquetarMedio` sobre `List<String>` con validación por-id y regla "≥1 Situación" | ✅ hecha |
| T5 | **Remoto** — `DocumentoMappers` escribe `"etiquetas": [..]` | ✅ hecha |
| T6 | **UI** — `HojaEtiqueta` con dos grupos de chips multi-selección; `ArchivosScreen` y `DetalleReporteScreen` a la lista | ✅ hecha |
| T7 | **Pruebas** — contrato, worker, emulador y caso de uso a la forma de lista y al filtro de Situación | ✅ hecha |
| T8 | Cierre: estado en §14 y entrada de `CHANGELOG.md` | ✅ hecha |

**Dependencias:** T1 habilita todo lo demás. T2 depende de T1 (necesita `CategoriaEtiqueta`). T3, T4
y T5 dependen de T1; T3 además de T2 (la migración lee la categoría del catálogo para calcular
`tieneSituacion`). T6 depende de T1–T4. T7 es transversal y se cierra al final con T8.

### 14.6 Fuera de alcance — no dar por resuelto

Sigue abierto todo lo que ya listaban §12.5 y §13.6, en particular: **C2** (`sha256` nulo, que este
cambio roza al tocar el mapper pero no arregla), el resto del grupo C de integridad, **T3 de la
auditoría** (export de esquema de Room — esta tanda añade la primera migración pero no el
directorio de esquemas ni la prueba de `MigrationTestHelper` salvo que se decida cerrarlo aquí),
todas las reglas S3–S14, y el gancho de **catálogo remoto de B5** (§3.1), que este cambio deja
preparado pero no implementa.


---

## 15. Verificación de §12/§13, pendientes manuales e integridad del dato (Grupo 1)

Tercera tanda sobre `docs/AUDIT.md`. Nace de un encargo con tres partes: (a) **verificar
exhaustivamente** que el código respalda las marcas `✅ hecha` de §12 y §13, (b) **cerrar los
pendientes de comprobación manual** que esas dos tandas dejaron, y (c) **atacar lo que quedó
fuera de alcance**, empezando por la tanda que la propia auditoría marca como la más urgente en
§11 (Grupo 1) y en §12.6: la **integridad del dato**. Desde que B1 (§12) hizo funcionar las
subidas, C1/C2/C3/C4/C5/C6 están produciendo datos incorrectos en Firestore; son los primeros
candidatos de la siguiente tanda.

El encargo pidió **profundidad primero**: esta sección detalla en modo TDD solo el Grupo 1, y
deja las tandas de los Grupos 2–6 como un roadmap de una línea (§15.6) que se detallará cuando
toque.

### 15.1 Estado verificado de §12/§13 (leído contra el código, no contra la doc)

Confirmado leyendo los archivos que cada sección dice haber tocado:

| Hallazgo | Marca | Evidencia en el código |
| --- | --- | --- |
| B1 | ✅ confirmado | `data/remote/AuthDataSource.kt` usa `.await()` (`kotlinx.coroutines.tasks.await`); el helper `esperar()` ya no existe. `observarCustodio` gana `distinctUntilChanged` |
| B2 | ✅ confirmado | `GrabadoraMediaRecorder.nuevoRecorder()` con gate `SDK_INT >= S` y `catch (_: Throwable)` |
| B3 | ✅ confirmado | `app/build.gradle.kts` tiene `lint { abortOnError = true; fatal += setOf("NewApi","MissingPermission",…) }`, sin baseline |
| S1 | ✅ confirmado | El manifest no declara `WRITE_EXTERNAL_STORAGE` ni referencia MediaStore |
| S2 | ✅ confirmado | `android:allowBackup="false"` en el manifest |
| E3/E6 | ✅ confirmado | `MedioDao.obtenerPendientes` filtra `tieneSituacion = 1` + `custodioUid` (JOIN); `ReporteDao.obtenerPendientesSubida` filtra `custodioUid` + `fechaLocal >= :fechaDesde` |
| E5 | ✅ confirmado | `GrabadoraMediaRecorder` con `pausar`/`reanudar`/`pausada`/`eventos`/`barrerNotasHuerfanas`, `setOnErrorListener`/`InfoListener`/`MaxDuration`, todo `suspend` sobre `Dispatchers.IO` |

De paso se confirmó que §14 (etiquetas en dos categorías: `etiquetas: List<String>` +
`tieneSituacion`) ya está en el código.

**T1 — reporte de verificación (hecho 2026-09-22).** Se leyó el código de lo que quedaba por
cerrar y todo respalda la marca:

| Lo que faltaba leer | Verificación |
| --- | --- |
| E1 (doble toque) | ✅ **CONFIRMADO** — `CapturaEvidenciaScreen.kt:87,196-217`: `disparoEnCurso`, cerrojo **síncrono** con el toque, liberado en los dos callbacks excluyentes de `tomarFoto` |
| E2 (fallos mudos) | ✅ **CONFIRMADO** — `VistaCamara.tomarFoto`: `try/catch (Throwable)` + `onError` → `alFallar` (líneas 71-104); `CapturaViewModel` inyecta `RegistroDeFallos` (línea 68), cableado a `FirebaseCrashlytics` vía `DiagnosticoModule` |
| E4 (disco lleno) | ✅ **CONFIRMADO** — `AlmacenArchivos.guardar` → `Result`; `FilesDirAlmacenArchivos` comprueba espacio antes y devuelve `DominioError.SinEspacioEnDispositivo` (nunca lanza al disparo) |
| E7 (guard muerto) | ✅ **CONFIRMADO** — `AjustesViewModel.solicitarCerrarSesion`/`confirmarCerrarSesion` leen `uiState.value.pendientesSinSubir` (el estado combinado), no `interno` |
| Poda de `values-es-rMX/` | ✅ **CONFIRMADO** — solo queda `res/values/` (strings, themes, colors); no hay variante regional |
| XML de backup | ✅ **CONFIRMADO** — `backup_rules.xml` y `data_extraction_rules.xml` excluyen `app_custodio.db(sql)` y `medios` en los dos canales (`cloud-backup` y `device-transfer`), además de `allowBackup="false"` |
| Suites nuevas | ✅ **CONFIRMADO** — `EsperaDeTareasTest` (regresión de B1, 5 pruebas), `RemotoEmuladorTest` (integración contra el emulador) y el contrato `ContratoRepositoriosTest` (Room + fakes) existen y corren |

**T2 — checklist manual (hecho 2026-09-22).**

| Ítem | Verificación |
| --- | --- |
| `cd firebase && npm test` (reglas S3–S14) | ✅ **CONFIRMADO** — "Todas las reglas pasaron"; el emulador en ejecución ocupaba el hub, se detuvo, `npm test` corrió su `emulators:exec` propio y después se relanzó el set completo (`--only firestore,storage,auth`) |
| B2 por lint/release | ✅ **CONFIRMADO** — `assembleRelease` corre `lintVitalRelease` en 0 errores; la rama del constructor viejo (`SDK_INT < S`) queda **PENDIENTE-HW**: no hay equipo con Android ≤11 (§15.3 R2), solo se comprueba por lint |
| S1 (galería pública) | ✅ **CONFIRMADO** — verificado en el teléfono conectado (2026-09-22): la evidencia vive en `files/medios` y MediaStore público tiene **0 filas** del paquete (15683 imágenes + 1033 audios); las entradas públicas son de otras apps |
| S2 (`bmgr backupnow`) | ✅ **CONFIRMADO** — `allowBackup="false"` + XML de backup verificados por código **y** en el teléfono: `bmgr backupnow com.ai27.app.custodio` → *"Package ... Backup is not allowed"* + *"Backup finished with result: Success"* |

### 15.2 Los seis hallazgos del Grupo 1 (confirmados presentes)

| ID | Hallazgo | Evidencia en el código |
| --- | --- | --- |
| **C1** | El `Result` de `actualizarContadores` se descarta y el reporte sale de la reconciliación para siempre | `SubidaMedioProceso.actualizarContadores` llama `reporteRemoto.actualizarContadores(...)` sin mirar su `Result`; `actualizarSubida` ya escribió `COMPLETA` local, y `ReporteDao.obtenerPendientesSubida` filtra `subida != 'COMPLETA'` |
| **C2** | El `sha256` nunca llega al servidor y no se puede rellenar | `SubidaMedioProceso` calcula `sha`, pero `crearDocumentoSiNoExiste` recibe el `medio` pre-subida (sha null); `FirebaseMedioRemoto` copia solo rutas; `DocumentoMappers` escribe `"sha256" to null`. `medios` es create-only |
| **C3** | Carrera lost-update de contadores | `actualizarContadores` recomputa el valor absoluto desde Room y lo `.update()`; sin `FieldValue.increment` ni transacción → dos workers intercalados hacen retroceder `mediosSubidos` |
| **C4** | `ExistingWorkPolicy.REPLACE` cancela subidas en vuelo | `WorkManagerSubidaScheduler` usa `REPLACE` en `encolarSubidaMedio` y `sincronizarReporte`; `reconciliar` re-encola todo, incluido `EN_PROGRESO` |
| **C5** | Tope de 4 intentos sin clasificar permanente vs transitorio | `ErroresRemoto` solo mapea `IOException`/`FirebaseNetworkException → SinConexion`; el resto cae en `Inesperado`; la causa no se persiste ni va a Crashlytics |
| **C6** | El cierre no es transaccional | `CerrarReporte` lee los medios y después llama `cerrar`; sin `@Transaction`, una captura en la ventana intermedia queda fuera del conteo y `completo` puede ponerse `true` antes de tiempo |

El diseño de idempotencia base es sólido (rutas determinísticas por `medioId`, documento
create-only con guarda de existencia, `asegurarReporte` antes de Storage) y **no se toca**
(§10 de la auditoría).

### 15.3 Alcance acordado

| # | Decisión |
| --- | --- |
| R1 | **(a) verificación exhaustiva** de §12/§13; **(b) pendientes manuales**; **(c) Grupo 1 (C1–C6) en detalle** + roadmap para los Grupos 2–6 |
| R2 | Hay **dispositivo real con Android > 11** y **Emulator Suite**. No hay equipo con Android ≤11, así que la rama del constructor viejo de B2 queda comprobable solo por lint/release, no por ejecución |
| R3 | **C3 entra acoplado a C1**: ambos son el mismo defecto de contadores y su corrección es una sola (monótona + `Result` propagado). C7/C8/C9 quedan **fuera** |
| R4 | No se toca ni se despliega nada de Firebase salvo pruebas contra el Emulator Suite. Reglas (S3–S14) quedan para el Grupo 2 |

### 15.4 Decisiones de diseño

**D1 — Contadores monótonos y con el `Result` propagado (C1+C3).** Hoy se recomputa el valor
absoluto desde Room (bien contra el doble conteo por reintento, mal contra lost-update). La
corrección: propagar el `Failure` de `actualizarContadores` (devolver retry) o encolar
`sincronizarReporte` al completar cada medio; hacer la escritura remota **monótona** (solo
avanzar) vía transacción de Firestore o escritura condicionada; y basar la reconciliación en
**divergencia con el remoto** en vez del flag local `COMPLETA`. Con eso, un fallo de red deja
de reportar éxito y el reporte no se cae de la cola.

**D2 — Cierre en una transacción de Room (C6).** Un método `@Transaction` en el DAO deriva
`mediosEsperados`/`resumenMedios` con `COUNT(*)`/agregados y marca `ciclo='CERRADO'` en el mismo
paso; `CapturarMedio` valida `ciclo==ACTIVO` dentro de la misma frontera. Es el primer
`@Transaction` del proyecto (la auditoría hace notar que no hay ninguno), pero **no** exige
export de esquema ni migración —eso es T3, del Grupo 6—, así que no toca scripts de build.

**D3 — `sha` antes de construir el documento (C2).** Se calcula el sha y se pasa por la cadena
`SubidaMedioProceso.ejecutar → crearDocumentoSiNoExiste(reporte, medio.copy(sha256=sha), rutas)`
para que `DocumentoMappers` lo escriba. Consecuencia registrada: los medios ya subidos con
`sha256` nulo **no se pueden rellenar** (`medios` es create-only); esta corrección aplica de
aquí en adelante.

**D4 — `KEEP` para reconciliación y reintento (C4).** `ExistingWorkPolicy.KEEP` en los caminos
de reconciliación y reintento; `REPLACE` se reserva para cambios reales de input; `reconciliar`
no re-encola `EN_PROGRESO` con worker vivo (`getWorkInfosForUniqueWork`). La prueba de
caracterización `caracterizacion_C4_encolar_de_nuevo_REEMPLAZA_el_trabajo_anterior` (§13.5) va a
**fallar** con este cambio y hay que convertirla, como ya pasó con las de E3/E6.

**D5 — Clasificar el error y persistir la causa (C5).** Ampliar la clasificación para
`PERMISSION_DENIED`/`UNAVAILABLE`/`DEADLINE_EXCEEDED`/archivo ausente; para `SinConexion`,
reintentar con backoff largo sin tope (la constraint de red evita el spin); para permanentes,
fallo inmediato; persistir `ultimoError`/`intentos` en `medios` y mandar la excepción a
`RegistroDeFallos`/Crashlytics. Nota del audit: `FirebaseNetworkException` no se instancia en
JVM host, así que esa rama se cubre con `IOException`.

**Sin pausa de Sync.** Ninguna tarea del Grupo 1 añade dependencias: `work-testing` ya está en
el classpath (§12 T5) y el resto es código. Los scripts de build no se tocan.

### 15.5 Tareas

| # | Tarea | Estado |
| --- | --- | --- |
| T1 | **(a)** Completar la verificación exhaustiva de §12/§13: leer E1/E2/E4/E7, la poda de `values-es-rMX/`, los XML de backup y las suites nuevas; producir un reporte con `CONFIRMADO`/`DESVIACIÓN` por hallazgo | ✅ hecha (2026-09-22) |
| T2 | **(b)** Ejecutar el checklist manual disponible: `bmgr backupnow` (S2), revisar galería (S1), `cd firebase && npm test` (reglas), y B2 por lint/experimento; dejar anotado lo `PENDIENTE-HW` (Android ≤11) | ✅ hecha (2026-09-22) — S1 y S2 **verificados en el teléfono conectado** (MediaStore y `bmgr`), B2 (Android ≤11) queda **PENDIENTE-HW**; la sesión de dispositivo destapó un hallazgo nuevo: C10 (migración 1→2/2→3 crasheaba en una base v1 real), arreglado y con regresión (§15.1, §15.5bis) |
| C0 | Infra de prueba: `SubidaMedioProceso`/`ReporteSyncProceso` contra Room real (patrón de `InvarianteUnSoloActivoTest`), extender `RemotoEmuladorTest` con `actualizarContadores`; evaluar `turbine` solo si hace falta | ✅ hecha (2026-09-22) |
| C1 | **C2** — el `sha256` llega al documento remoto (calcular antes de construir el doc) | ✅ hecha (2026-09-22) |
| C2 | **C6** — cierre transaccional que no infla contadores (`@Transaction` + `COUNT(*)`) | ✅ hecha (2026-09-22) — un solo `UPDATE` atómico del DAO, ver §15.5bis |
| C3 | **C1 + C3** — contadores monótonos con `Result` propagado y reconciliación por divergencia | ✅ hecha (2026-09-22) |
| C4 | **C4** — `REPLACE → KEEP` en reconciliación/reintento; no re-encolar `EN_PROGRESO` vivo | ✅ hecha (2026-09-22) — el guard explícito de `getWorkInfosForUniqueWork` se eliminó: KEEP decide atómicamente en el enqueue, ver §15.5bis |
| C5 | **C5** — clasificar permanente vs transitorio, persistir causa, Crashlytics | ✅ hecha (2026-09-22) |
| C6 | Cierre: marcas en `AUDIT.md`, resultado y verificación en §15, entrada en `CHANGELOG.md` | ✅ hecha (2026-09-22) |

**Dependencias:** T1 y T2 son independientes y pueden ir primero. C0 habilita todo lo demás. C1
(C2) y C2 (C6) habilitan C3 (C1/C3, que consume el conteo correcto y el sha). C4 va después de
C3; C5 al final del bloque. C6 cierra.

**Orden de las pruebas que fija cada tarea:**
- C1: prueba de emulador que lee `medios/{id}` por llave y afirma `sha256 != null` y == al hash; unit del mapper.
- C2: prueba Room real de carrera cierre-vs-captura que afirma que `mediosEsperados`/`completo` no se inflan; ajustar `CerrarReporteTest`.
- C3: unit de que un remoto fallando devuelve `Failure`/retry y el reporte sigue reconciliable; prueba de emulador de dos actualizaciones intercaladas que no retroceden.
- C4: `work-testing` (`TestListenableWorkerBuilder` + `WorkManagerTestInitHelper`) de que un re-encolado no mata el trabajo en curso; convertir `caracterizacion_C4_...`.
- C5: unit de la clasificación por tipo de excepción y de que el permanente no consume reintentos.

### 15.5bis Resultado de la tanda (2026-09-22) y desviaciones al ejecutar

**Verde de punta a punta.** `assembleDebug`, `testDebugUnitTest` (171 pruebas, 26 suites, 0
fallos) y `connectedDebugAndroidTest` (31 pruebas en emulador Pixel 9a: `RemotoEmuladorTest`
9, `ReconciliacionColaTest` 10, `WorkerSubidaMedioTest` 6, `GrabadoraMediaRecorderTest` 5,
`ExampleInstrumentedTest` 1) pasan. El Emulator Suite se levantó con
`--project custodio-906e7268` (el proyecto que el SDK usa; sin ello la regla de Storage
falla porque su `firestore.get()` cruza a otro namespace de proyecto —documentado en
`RemotoEmuladorTest` requisito 1).

**D2 llevada más lejos de lo planeado (C6 — cierre).** En lugar de `@Transaction` alrededor
de "leer lista + escribir", el cierre es un **solo `UPDATE` atómico del DAO** que deriva los
conteos dentro del mismo statement con subconsultas sobre `medios` (`COUNT(*)`, `SUM(subido)`,
`completo = subidos >= esperados`). Un solo statement de SQLite es igual o más atómico que un
`@Transaction` y elimina de raíz la ventana "leer / escribir". Además `CapturarMedio` ya no
valida el ciclo en una segunda transacción: la inserción respeta la invariante en su propia
frontera. Contrato determinista nuevo en `ContratoRepositoriosTest` (fakes y Room): `cerrar
cuenta los medios presentes al momento del UPDATE, no una lectura previa`.

**D1/C3 — decisión de escritura bajo el test.** Se probó `FieldValue.increment(±deltas)` con
`completo` en la transacción; la foto positiva es el **otro extremo**: `update()` con merge
monótono sin valores absolutos desde Room (se escriben `max()` y `subida` que solo avanza),
sin leer el reporte primero, así no hay doble incremento por reintento ni lost-update.

**D4 — el guard explícito ya no existe (C4).** `ExistingWorkPolicy.KEEP` decide
atómicamente en el enqueue; mantener `getWorkInfosForUniqueWork` además era un
check-then-act con su propia carrera, así que la reconciliación ya no lo necesita: encola y
deja que KEEP proteja. Los `EN_PROGRESO` con worker vivo se quedan como están.

**C5 — clasificación por kind, no por excepción en host.** `FirebaseFirestoreException` y
sus `Code` no se instancian en la JVM host (NoClassDefFoundError), así que la clasificación
JVM y el `Result` de `SubidaMedioProceso` se prueban por **código de error en string**
(`"UNAVAILABLE"`, `"PERMISSION_DENIED"`, …) y la rama de excepción de verdad se cubre en
device con `WorkerSubidaMedioTest`. Lo permanente devuelve `Result.failure()` inmediato y
persiste `ultimoError`+`intentosSubida` (migración Room 2→3, sin export de esquema); lo
transitorio reintenta sin tope. Quedó registrado el quirk de sqlite-bundled: `getText` de una
columna NULL devuelve `""`, los tests de migración normalizan con `takeIf { it.isNotEmpty() }`.

**C2 — test de emulador por llave, con el sha dentro del documento.** `medios` conserva la
regla create-only; la regresión lee el documento por llave y afirma el sha persistido.

**Atendidos:** C1–C6 quedaron marcados `✅ ATENDIDO 2026-09-22` en `docs/AUDIT.md` y la
entrada de la tanda quedó en `docs/CHANGELOG.md`. Quedan **PENDIENTE** solo las comprobaciones
que requieren hardware o sesión: S1 (galería), S2 (`bmgr backupnow`) y B2 (Android ≤11).

**S1 y S2 cerrados en el teléfono (2026-09-22).** Con el teléfono CPH2769 conectado se
verificó: S1 — la evidencia vive en `files/medios` y MediaStore público tiene **0 filas** que
referencien `com.ai27.app.custodio` (15683 imágenes + 1033 audios; las entradas son de otras
apps); S2 — `bmgr backupnow com.ai27.app.custodio` responde *"Backup is not allowed"* y el
transport termina con *"Backup finished with result: Success"*.

**C10 — hallazgo nuevo salido de esa sesión (bug real de migración).** La base v1 del
dispositivo (con el índice único parcial `ux_reportes_activo_por_custodio` persistido por un
`onOpen` anterior) hacía crashear la app al abrir: `IllegalStateException: Migration didn't
properly handle: reportes`. La validación de Room compara `indices` contra las entidades
(vacío), y el índice manual sobraba. Fix: cada migración empieza con `DROP INDEX IF EXISTS
ux_reportes_activo_por_custodio`; la validación ve `{}` y pasa, y `onOpen` (post-validación)
lo recrea. Regresión en `MigracionIndiceActivoTest` (2 pruebas; verificado RED sin el fix y
GREEN con él). En el teléfono la base real migró a v3 en vivo (`user_version 1→3` vía WAL)
y la app abrió sin FATAL, conservando el viaje ACTIVO `123ABC` y los reportes CERRADO. Esto
afectaba a **cualquier** instalación previa a §14 (la MIGRACION_1_2 enviada habría crasheado
ante una base v1 con índice). Registrado en `AUDIT.md` como C10.

### 15.6 Roadmap de las tandas restantes (una línea; se detallan cuando toquen)

- **Grupo 2 — Privacidad y reglas:** S3/S4 (tope de tamaño y `contentType`, `update` acotado a
  `ACTIVO`), S5 (validación de forma `hasOnly`/tipos), S6/S7 (cotas de contadores y `soloCambia`
  por transición), S8 (declarar+pedir+consultar `COARSE`), S11 (limpieza al rotar), S12/S13
  (`ModoEmulador` tras `BuildConfig.DEBUG`, contexto de Crashlytics), **cada regla con su caso en
  `reglas.test.js`** (S14). Requiere despliegue de reglas y decisión de producto.
- **Grupo 3 — Uso en campo:** R1/R2 (abrir en el visor, estado de carga en Reporte), U1/U2
  (ráfaga sin bloqueo, etiqueta heredada, chips a 48dp), U3 (háptica+sonido), U4/U5/U6 (estado en
  Pendientes y rejilla, separar el badge), U8 (orden del `when` de Historial y cablear
  `onReintentar`), U9 (franja de sin conexión en todo el flujo), U10 (`launchSingleTop`), U11
  (`rememberSaveable`), U12 (motivos de descarte en chips), S9/S10 (rationale y ruta a Ajustes).
- **Grupo 4 — Rendimiento:** R3 (conteo fuera del cuerpo de `AppNavHost`), R6
  (`lifecycle-runtime-compose` + `collectAsStateWithLifecycle`), R9 (`@Index` +
  `distinctUntilChanged`), R8 (`@Immutable` + `contentType`), R4/R5 (no recomprimir, EXIF,
  `putFile` reanudable, SHA en streaming, concurrencia 1), R7 (flash en `graphicsLayer`), R13
  (fix GPS compartido con TTL + `lastLocation`), R14 (constraints de red +
  `NET_CAPABILITY_VALIDATED`), R15/R16, T6 (baseline profile).
- **Grupo 5 — Accesibilidad:** A3.9+U11 (`verticalScroll` en Login/ReporteAbierto/Audio/Ajustes
  por `fullSensor`), A2 (dejar de usar `outline` como color de texto; recalcular la receta de
  chips/`FranjaAviso` para tema claro), A4 (`imePadding`+insets en los 5 destinos), A1 (blancos
  táctiles ≥48/56dp), A3 resto (`overflow` con `maxLines`), A5 (`contentDescription` +
  `mergeDescendants`), A6 (`liveRegion`, `Role.Tab`, `toggleable`).
- **Grupo 6 — Proceso y deuda:** T1 (CI), T3/T4 (esquema de Room exportado + migración + índice
  solo en `onCreate`), T2 (versionado derivado), T9 (firma v3), T8 (`applicationIdSuffix`), T7
  (contexto de Crashlytics), T10/T5 (ABIs y R8 midiendo), T11/T12; E8 (rescate de `EN_PROGRESO`),
  E9 (barrido de huérfanos), E10 (sacar trabajo crítico de `viewModelScope`), E11 (sesión perdida
  en caliente); P2 restante, P3–P7.

### 15.7 Fuera de alcance — no dar por resuelto

Siguen abiertos, además de todo lo que ya listaban §12.5, §13.6 y §14.6: **C7** (`set` con
`serverTimestamp` que puede chocar con las reglas), **C8** (confirmación de existencia desde la
caché de Firestore), **C9** (detalles menores del bloque de consistencia), y todos los Grupos
2–6 hasta que su tanda los tome. Las decisiones de producto del cierre de la auditoría
(resolución de captura §3.4, solo-Wi-Fi para subir, retención de datos y de miniaturas) tampoco
se resuelven aquí.


---

## 16. Tanda del Grupo 2 — Privacidad y reglas (S3–S14)

Siguiente tanda tras el Grupo 1 (§15), según el orden de `AUDIT.md` §11. El modelo de
autorización (dueño por `custodioUid`, analista por correo en `config/analistas`, nada se borra)
está bien y **no se toca** (§10 de la auditoría). Los agujeros son la ausencia de validación de
forma/tamaño en las reglas, transiciones que no acotan qué más cambia, y el runtime de permisos.
S1 y S2 ya se cerraron en §12.

### 16.1 Hallazgos confirmados contra el código

| ID | Hallazgo | Evidencia |
| --- | --- | --- |
| **S3** | Storage sin tope de tamaño ni `contentType` | `storage.rules`: `allow create, update: if autenticado() && duenoDelReporte(reporteId)` — sin `request.resource.size` ni `contentType` |
| **S4** | `update` en Storage permite sobrescribir evidencia cerrada | misma regla; `update` sin condición, sin mirar el `ciclo` |
| **S5** | Firestore sin validación de forma/tipo | `create` de reporte y de medio solo validan dueño/pertenencia; sin `hasOnly()`, sin tipos, sin anclar `storagePath` |
| **S6** | Contadores libres, sin cota | `transicionValida()` restringe qué llaves cambian, no a qué valores: `mediosSubidos` inflado/retrocedido, `completo:true` sin archivos, `subida` fuera de enum |
| **S7** | `ACTIVO→CERRADO` no acota qué más cambia | la rama de cierre no lleva `soloCambia`: se puede reescribir `placas`/`custodioUid`/`fechaLocal` en el mismo `update` |
| **S8** | `COARSE` declarado pero no pedido | `permisosSolicitados()` = `CAMERA/RECORD_AUDIO/ACCESS_FINE_LOCATION`; `permisosActuales()` no consulta ubicación. `ProveedorUbicacion` ya hace `checkSelfPermission` (B3) pero solo `PRIORITY_HIGH_ACCURACY` |
| **S9** | Sin rationale ni salida del "no volver a preguntar" | no existe `shouldShowRequestPermissionRationale`; el re-lanzamiento solo está detrás de "cámara denegada" |
| **S10** | Sin micrófono no hay forma de concederlo desde Audio | `RECORD_AUDIO` solo se pide en `CapturaEvidenciaScreen` |
| **S11** | Residuo al rotar de custodio | grueso ya resuelto por E6 (§13, sin purga por decisión); lo que queda (miniaturas inmortales) es **retención → Producto** |
| **S12** | `ModoEmulador` por `meta-data`, no `BuildConfig.DEBUG` | `ModoEmulador.activo()` lee un `<meta-data>` |
| **S13** | Crashlytics sin contexto, recolección incondicional | `firebase_crashlytics_collection_enabled=true`; sin `setUserId`/`setCustomKey`; parcialmente adelantado por `RegistroDeFallos` (§13) |
| **S14** | `reglas.test.js` no cubre S3–S7 | además es un script con estado compartido por orden de ejecución (frágil) |

### 16.2 Decisiones de diseño

**D1 — Aquí se cambian y prueban las reglas contra el Emulator Suite; el despliegue real es
manual (carril A / A3).** La tanda modifica `firestore.rules`/`storage.rules` y `reglas.test.js`
y corre `cd firebase && npm test`, pero **no despliega** a la consola: eso lo hace el humano en
el carril A. Respeta la regla de no tocar Firebase real.

**D2 — Cada regla nueva entra con su caso en `reglas.test.js` (S14).** Es lo único que evita la
regresión. Se endurece el arnés para que insertar un caso no rompa los siguientes.

**D3 — `update` de Storage se niega, no se acota (S4).** `allow create: if … && resource == null`
y reintento idempotente contra ausencia del objeto. Alternativa registrada: condicionar `update`
a `ciclo=='ACTIVO'` si se decide conservarlo.

**D4 — `soloCambia` por transición, con lista blanca (S7).** Cierre:
`['ciclo','closedAt','comentario','hayNovedad','mediosEsperados','resumenMedios','subida','mediosSubidos','completo']`;
descarte: `['ciclo','closedAt','motivoDescarte']`; contadores: los tres de hoy. `custodioUid`/`placas`
nunca en `affectedKeys()`.

**D5 — Cotas de contadores (S6), coordinadas con C3 del Grupo 1.** `nuevo.mediosSubidos >=
previo.mediosSubidos`, `<= previo.mediosEsperados`, `subida in [enum]`, `completo ==
(mediosSubidos == mediosEsperados)`. La escritura monótona del cliente (C3) y la cota monótona de
la regla (S6) tienen que cuadrar: hacer S6 **después** de C3 o en la misma ventana.

**D6 — S13/S11 acotados.** S13 se cierra junto con T7 (contexto de Crashlytics) en el Grupo 6;
aquí solo queda escrito que la recolección incondicional es defendible en equipos de empresa. S11
pasa a Producto como decisión de retención.

**Pausa de Sync:** las reglas y `reglas.test.js` no tocan Gradle. `BuildConfig.DEBUG` para S12 **sí**
requiere habilitar `buildConfig` en `build.gradle.kts` → **pausa de Sync** antes de tocar S12.

### 16.3 Tareas (TDD; cada regla con su prueba primero)

| # | Tarea | Estado |
| --- | --- | --- |
| G2-0 | Endurecer el arnés de `reglas.test.js` (aislar estado compartido) — habilita el resto (S14) | pendiente |
| G2-1 | **S7** — `soloCambia` en `ACTIVO→CERRADO` y `ACTIVO→DESCARTADO` | pendiente |
| G2-2 | **S6** — cotas y monotonía de contadores + enum de `subida` (coordina con C3) | pendiente |
| G2-3 | **S5** — `hasOnly()`, tipos, rango de `mediosEsperados`, `storagePath` anclado | pendiente |
| G2-4 | **S3** — tope de tamaño y lista blanca de `contentType` en Storage | pendiente |
| G2-5 | **S4** — negar `update` en Storage (reintento contra ausencia del objeto) | pendiente |
| G2-6 | **S8** — pedir y consultar `COARSE`; degradar a `PRIORITY_BALANCED` (encaja con R13) | pendiente |
| G2-7 | **S9 + S10** — rationale + ruta a Ajustes, re-chequeo en `ON_RESUME`, micrófono desde Audio | pendiente |
| G2-8 | **S12** (tras pausa de Sync) — `BuildConfig.DEBUG && activo(context)` | pendiente |
| G2-9 | Cierre: marcas en `AUDIT.md` (S11→Producto, S13→Grupo 6), §16, `CHANGELOG.md`; recordar el despliegue manual de reglas (carril A) | pendiente |

**Dependencias:** G2-0 primero. G2-1…G2-5 comparten arnés. G2-2 coordina con C3. G2-6/G2-7 son
independientes de las reglas. G2-8 al final por la pausa de Sync.

**Fuera de alcance:** todo lo de los Grupos 3–6, S11 (retención → Producto), y el despliegue real
de reglas (carril A).

---

## 17. Tandas restantes — Grupos 3 a 6 (secuenciadas, a detallar al arrancar cada una)

Estas cuatro tandas cierran el backlog de `AUDIT.md`. Se dejan a nivel de tanda —decisión de
alcance, clústeres de tarea y criterio de cierre— y se **detallarán en modo TDD cuando arranquen**,
igual que §15 y §16, para no escribir detalle que se desactualice antes de ejecutarlo (el repo
avanza de a una tanda). El orden sale de `AUDIT.md` §11.

### 17.1 Grupo 3 — Que la app sea usable en campo

**Meta:** acercar el estándar de 5 s de §7 y quitar la fricción contra WhatsApp. Es la tanda que
más nota el custodio.

- **Abrir en el visor y estado de carga:** R1 (abrir en la cámara, no en la lista), R2 (estado de
  carga en Reporte para matar el falso "Sin reporte activo").
- **La ráfaga y el etiquetado:** U1 (disparo libre con etiqueta diferida y salida de la hoja), U2
  (etiqueta heredada del último uso, quitar el "Guardar" redundante).
- **Confirmación no visual:** U3 (háptica + sonido de obturador + tic de encolado).
- **Estado de subida visible:** U4 (mostrar estado **y** etiqueta en Pendientes), U5 (marcar
  "enviado" en la rejilla, no por ausencia), U6 (separar "sin etiquetar"/"en cola"/"fallido" en el
  badge).
- **Navegación y estado:** U8 (orden del `when` de Historial + cablear `onReintentar`), U10
  (`launchSingleTop`), U11 (`rememberSaveable` en cierre/placa/etiqueta) — **U11 se comparte con
  el Grupo 5/A9**, conviene hacerlo una vez.
- **Errores y avisos:** U7 (acciones de recuperación en `DialogoError`), U9 (franja de sin conexión
  en todo el flujo, no solo en Reporte), U12 (motivos de descarte en chips), U13/U14/U15
  (vocabulario y jerarquía).
- **Permisos que ya toca el Grupo 2:** S9/S10 si no se cerraron allí.
**Cierre:** recorrido completo en dispositivo real cronometrando el flujo de 5 s; `testDebugUnitTest`
verde; algunas pruebas de Compose (flujo de captura, cierre, ajustes). Sin pausa de Sync salvo que
U-algo pida una dependencia nueva.

### 17.2 Grupo 4 — Rendimiento

**Meta:** que el estándar de 5 s se cumpla de forma fiable y que una subida de 50 medios no queme
CPU/batería/RAM. Nota de coordinación: si Producto sube la resolución (§3.4), R4/R5 empeoran, así
que **arreglar el pipeline de imagen va antes de subir la resolución**.

- **La lectura de estado más cara:** R3 (conteo de pendientes fuera del cuerpo de `AppNavHost`,
  `StateFlow<Int>` con `COUNT`) — el cambio más rentable del grupo.
- **Ciclo de vida y Room:** R6 (`lifecycle-runtime-compose` + `collectAsStateWithLifecycle` →
  **pausa de Sync**), R9 (`@Index` + `distinctUntilChanged` — **coordina con T3/T4 del Grupo 6**,
  que aporta el export de esquema y la migración), R8 (`@Immutable` + `contentType`).
- **El pipeline de imagen:** R4 (no decodificar/rotar/recomprimir; EXIF por tag), R5 (`putFile`
  reanudable, SHA en streaming, concurrencia 1) — **coordina con C2/C5 del Grupo 1**.
- **Detalles:** R7 (flash en `graphicsLayer`), R13 (fix de GPS compartido con TTL + `lastLocation`
  — **encaja con S8 del Grupo 2**), R14 (constraints de red + `NET_CAPABILITY_VALIDATED`), R11
  (config de Coil), R12/R15/R16, T6 (baseline profile propio → **pausa de Sync**).
**Cierre:** medición antes/después donde se pueda (arranque en frío, subida de 50); `testDebugUnitTest`
verde. Varias pausas de Sync (R6, T6) — agrupar.

### 17.3 Grupo 5 — Accesibilidad y adaptación

**Meta:** que la app se use a pleno sol, con guantes, con escala de texto grande y con el teléfono
en cualquier orientación (`fullSensor` ya está activo).

- **Lo primero, por `fullSensor`:** A3.9 + U11 (`verticalScroll` en Login/ReporteAbierto/Audio/Ajustes
  y `rememberSaveable` en el formulario de cierre) — ascendido porque hoy se rompe al **girar** el
  teléfono, no solo al 200 % de escala.
- **Contraste:** A2 (dejar de usar `colorScheme.outline` como color de texto — un `sed`; luego
  recalcular la receta de chips/`FranjaAviso` **para tema claro**, que es donde falla).
- **Insets y teclado:** A4 (`imePadding` + `navigationBarsPadding` en los 5 destinos que faltan,
  empezando por Login) — **coordina con U11**.
- **Táctil y overflow:** A1 (los cuatro blancos táctiles críticos a ≥48/56dp), A3 resto (`overflow`
  donde hay `maxLines=1`).
- **Semántica:** A5 (`contentDescription` del reintento y celda con `mergeDescendants`), A6
  (`liveRegion` en cronómetro/badge/aviso de encolado; `Role.Tab`, `toggleable`), A7/A8 (error por
  color, teclado/capitalización/foco).
- **Strings:** A10 (residuo de `values-es-rMX/` — ya podado en B3; verificar).
**Cierre:** revisión en dispositivo real a pleno sol y de noche, escala de texto 130/200 %, y una
pasada de TalkBack; prueba de Compose transversal de A11y (`assertHeightIsAtLeast(48.dp)` +
`contentDescription`).

### 17.4 Grupo 6 — Proceso, build y deuda

**Meta:** que nada de lo anterior se regrese sin que se note, y cerrar la deuda de release.

- **Red de seguridad:** T1 (CI que corra `assembleDebug testDebugUnitTest lintDebug` + reglas;
  ojo con la libSQLite `.dylib`→`.so` en runner Linux) — **habilita todo lo demás**.
- **Room bajo control:** T3 (export de esquema + `app/schemas/1.json` de línea base + política de
  solo-aditivas + prueba de migración por bump), T4 (índice único solo en `onCreate`/migración) —
  **coordina con R9 del Grupo 4 y con la migración de §14**. **Pausa de Sync** (plugin de Room).
- **Versionado y firma:** T2 (`versionCode` derivado), T9 (firma v3 **antes** del despliegue), T8
  (`applicationIdSuffix` de debug), T12 (`deviceId` propio en vez de `ANDROID_ID`).
- **Crashlytics y R8:** T7 (`setUserId`/custom keys/`recordException` — **cierra S13 del Grupo 2**),
  T10 (recortar ABIs), T5 (activar R8 midiendo, y resolver antes el mapping de Crashlytics con AGP 9)
  — **pausa de Sync**; T11 (dos runtimes de Room, lo resuelve R8).
- **Evidencia y ciclo de vida que quedaron del grupo E:** E8 (rescate de `EN_PROGRESO` — **coordina
  con C4/C5 del Grupo 1**), E9 (barrido de huérfanos y purga de miniaturas — **coordina con S11 y
  la decisión de retención**), E10 (sacar el trabajo crítico de `viewModelScope`), E11 (sesión
  perdida en caliente).
- **Pruebas:** P2 restante (suite de contrato que alinee los fakes), P3 (aserciones débiles),
  P4 (escenarios sin prueba), P5 (determinismo), P6 (las cinco de mayor retorno), P7 (ampliar
  `reglas.test.js`, se solapa con S14).
**Cierre:** CI en verde sobre `main`; APK de release firmado v3 con `versionCode` derivado;
`app/schemas/` versionado. Varias pausas de Sync (T3, T5) — agrupar.

### 17.5 Dependencias entre tandas y decisiones de Producto pendientes

```
Grupo 0 (§12) ─ hecho
Grupo E (§13) ─ hecho
Etiquetas (§14) ─ hecho
Grupo 1 (§15) ─ planeado ──► Grupo 2 (§16) ──► Grupo 3 (§17.1) ──► Grupo 4 (§17.2) ──► Grupo 5 (§17.3) ──► Grupo 6 (§17.4)
```

El orden es el de `AUDIT.md` §11, pero hay solapes que conviene explotar: **C3↔S6** (contadores),
**C2/C5↔R4/R5** (pipeline de imagen), **S8↔R13** (GPS), **U11↔A3.9/A4** (scroll + estado),
**R9↔T3/T4** (índices + esquema), **S13↔T7** (Crashlytics), **E8↔C4/C5** (rescate de subidas),
**E9↔S11** (limpieza + retención). Cuando dos tandas tocan lo mismo, se hace una vez en la que
llegue primero y la otra solo verifica.

**Decisiones de Producto que ninguna tanda toma** (del cierre de la auditoría): resolución de
captura (§3.4), retención de datos y de miniaturas (§3.2, S11, E9), solo-Wi-Fi para subir (R14). El
catálogo de etiquetas (§3.1) ya se resolvió en §14.
