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
| Auth | Firebase Auth, correo+contraseña por persona en equipos compartidos; sesión persistente, logout visible |
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
  correo, nombre, rol, activo

catalogos/etiquetas
  valores: [{ id, nombre, orden, activo }]      ← pendiente §3.1

reportes/{reporteId}                             ← contenedor de un viaje
  placas            string   normalizada: mayúsculas, sin espacios ni guiones
  placasCapturadas  string   lo que el custodio tecleó, tal cual
  custodioUid       string
  custodioCorreo    string
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
- El acceso de lectura del dashboard se resuelve con *custom claims* de rol cuando se construya. Fuera de este plan, pero las reglas se escriben desde ahora para no dejarlas abiertas "temporalmente".

### 5.4 Identidad e idempotencia

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

- **A1.** Habilitar Auth (correo+contraseña), Firestore (modo producción) y Storage en `custodio-906e7268`.
- **A2.** Crear las 37 cuentas de custodio. Alta y baja manuales por un admin, documentado en el README.
- **A3.** Desplegar las reglas de seguridad de §5.3.
- **A4.** Entregar el catálogo de etiquetas (§3.1) — lo que destraba la salida a producción.

### Carril B — app

| # | Fase | Termina cuando |
|---|---|---|
| **B0** | Higiene del repo: mover `google-services.json` de la raíz a `app/` (donde está ahora no lo lee nadie), primer commit, README inicial | `assembleDebug` compila con el plugin de google-services activo |
| **B1** | Dependencias y andamiaje: Hilt, Room, WorkManager, Navigation, CameraX, Coil, Firebase BoM, Crashlytics en `libs.versions.toml`; estructura de paquetes; `strings.xml` es-MX; tema claro/oscuro | La app arranca con navegación vacía en los dos temas |
| **B2** | Dominio y persistencia local: modelos, casos de uso, Room con el índice único de reporte activo, almacén de archivos, errores sellados. **Sin Firebase, con fakes** | Pruebas unitarias en verde, incluida la carrera de dos reportes activos |
| **B3** | Captura: CameraX, grabadora con tope y temporizador, GPS por archivo con precisión y antigüedad, etiquetado con chips, miniaturas, rejilla, borrado previo a la subida | Se capturan y etiquetan 50 archivos sin red y sobreviven a reiniciar la app |
| **B4** | Autenticación: login, sesión persistente, logout bloqueado con pendientes, `authStateChanges` dirigiendo la navegación | Se entra y se sale; la sesión sobrevive al reinicio |
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

**D4 — Cinco pantallas fuera de familia.** Pendiente al implementar. `ajustes_ai27_custodio_reparado`, `ajustes_cierre_de_sesi_n_deshabilitado_reparado`, `cerrar_reporte_estructura_reparado`, `estado_vac_o_base_reparado` y `reporte_abierto_sin_conexi_n_reparado` son de la ronda anterior. Al llegar a esas pantallas en C4 se adaptan a Nocturne en lugar de copiarlas; con D1 resuelto, la adaptación es mecánica.

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

### 11.6 Fase C4 — Pantallas, una por una

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
11. `ajustes_*` → pantalla nueva: toggle de tema, correo, pendientes y cierre de sesión con su variante deshabilitada.
12. `iniciar_sesi_n_*` → aplicar el lenguaje al login, que hoy es un `Column` sin identidad.

### 11.7 Fase C5 — Estados que el diseño exige y el código no tiene

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
C1 tokens y tema ──► C2 componentes ──► C3 navegación y descomposición
                                              │
                                              ▼
                                        C4 pantallas ──► C5 estados
                                              │
                                              ▼
                                     C6 strings y accesibilidad ──► C7 verificación
```

C2 puede empezar en cuanto C1 tenga los tokens, aunque falten formas y tipografía. C3 no depende de C2 para la parte de navegación, así que la barra inferior y los destinos nuevos se pueden adelantar. C5 y C6 son transversales y se cierran al final, no pantalla por pantalla.

