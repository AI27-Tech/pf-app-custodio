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
| Media | Hasta 50 fotos por reporte a ~1MP calidad 75, más miniatura de 320px; audio hasta 5 min |
| Inmutabilidad | Un reporte cerrado no se edita ni se borra. Descartar es un estado, no un borrado |
| Distribución | APK release por sideload y App Distribution; textos en español (México) |

### Lo que cambió respecto de la versión anterior de este plan

- La **etiqueta se movió del reporte a cada archivo** (R3). Antes el reporte tenía un `tipo` único; eso no sirve cuando el analista busca "todas las fotos de candado" dentro de viajes que contienen de todo.
- El **documento del reporte se escribe al crearlo**, no al cerrarlo (B8 + R7). Antes un reporte cerrado en zona sin señal no existía en ningún lado fuera del dispositivo: si el equipo se formateaba, la evidencia desaparecía y nadie sabía que ese reporte existió.
- **La subida dejó de dispararse al cerrar** (R7). Antes la evidencia de un viaje entero no salía del dispositivo hasta medio día después, en una sola subida de 20MB. WhatsApp entrega cada foto al instante; esto era una regresión funcional contra la herramienta que venimos a reemplazar.
- **Se quitó el selector de galería** (R6). Pediste GPS y hora por archivo; una foto sacada de la galería puede ser de otro día y otro lugar, y vacía justamente el dato que da valor.
- **Se desnormalizan campos del reporte en cada documento de media** (B2). Firestore no hace joins: sin esto, "todos los audios de la placa XYZ la semana pasada" es imposible.
- **Las fotos subieron de 10 a 50** y bajaron a ~1MP (D3, R11).

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
| Por reporte, peor caso | 50 archivos × ~215KB (1MP q75 + miniatura) ≈ **11MB** |
| Por día | ≈ 400MB · ~1,850 documentos en `medios` |
| Por mes | ≈ **12GB** · ~55k documentos |
| Escrituras Firestore/día | ~3,700 — muy por debajo de cualquier límite relevante |
| Datos celulares | ~11MB por viaje por dispositivo, sobre plan empresarial |

El volumen es chico. No hacen falta agregados ni rollups: las consultas con `count()` sobre `medios` filtrando por `fechaLocal` alcanzan de sobra para los conteos diarios, semanales y mensuales. Tampoco hace falta exportar a BigQuery: el dashboard consulta Firestore directo.

El almacenamiento crece sin techo mientras no haya regla de retención (§3.2): unos 145GB al año, algo así como 4 USD al mes de almacenamiento después del primer año. Las miniaturas son lo que mantiene barato el costo de lectura del dashboard.

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
| 50 fotos a ~1MP; plan de datos empresarial | D3, R11 |
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
