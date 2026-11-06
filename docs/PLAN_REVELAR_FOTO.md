# PLAN_REVELAR_FOTO — Un módulo profundo para revelar la foto de evidencia

> **Alcance de este documento: la app `pf-app-custodio` (Echo).** Toda la implementación vive en
> este repo. Fuera del código quedan dos pasos humanos: el despliegue de `storage.rules`
> (`pf-infra-firebase`, ya mergeado en PR #6) y la prueba en un ZTE Blade A55/A54 real.
>
> **Requisito de lectura previa:** `docs/GENERIC_RULES.md` y `docs/PLAN_MARCA_DE_AGUA.md`.
>
> **Origen:** candidato 1 de `docs/REVISION_ARQUITECTURA_ZTE.html` (revisión del 2026-11-06).
>
> **Estado:** decisiones D1–D14 cerradas el 2026-11-06. Pendiente la confirmación final del humano
> antes de empezar la Task 1.

---

## Por qué

El 90% de los custodios usa un **ZTE Blade A55** y el resto un **A54**. Los dos llevan un Unisoc
SC9863A (8× Cortex-A55, 28 nm) con 4 GB de RAM; el A55 corre Android 14 Go. En ese chip cada
decode o encode de un bitmap de tamaño completo cuesta caro y la memoria nativa es escasa.

Hoy una foto pasa por siete módulos y hace este trabajo (verificado leyendo el código):

| Momento | Paso | Dónde |
| --- | --- | --- |
| Al disparar (Dispatchers.Default) | decode del JPEG del HAL → bitmap #1 | `VistaCamara.kt` `aBitmap()` |
| | rotar → bitmap #2, sin reciclar el #1 | `VistaCamara.kt` `aBitmap()` |
| | encode WebP q80 del original + miniatura q70 | `Miniaturas.fotoOriginalYMiniatura` |
| 0–14 s después (**hilo principal**) | leer el archivo completo | `GrabarMarcaDeAguaEnFoto` |
| | decode del WebP → bitmap #3 | `BitmapEstampadorFoto.estampar` |
| | `copy(ARGB_8888)` → bitmap #4 | `DibujanteMarcaDeAgua.estampar` |
| | encode WebP q80 otra vez (pérdida sobre pérdida) | `DibujanteMarcaDeAgua.estampar` |
| | reescribir el archivo | `RoomMedioRepository.reescribirOriginal` |

Consecuencias:

1. **Bug: la marca de agua corre en el hilo principal.** `CapturaViewModel.adjuntarGpsEnSegundoPlano`
   lanza en `viewModelScope` y ningún módulo de la cadena `GrabarMarcaDeAguaEnFoto →
   ComponerMarcaDeAgua → BitmapEstampadorFoto → DibujanteMarcaDeAgua` cambia de dispatcher
   (verificado con grep). En un SC9863A eso congela la vista previa y el obturador, con riesgo de
   ANR. Con WebP (PR #10, ya en `pf-appcus-d`) el encode es por software y más lento.
2. **Dos decodes y tres codificaciones con pérdida** por foto (JPEG del HAL → WebP → WebP).
3. **Hasta 4 bitmaps de tamaño completo vivos** si el custodio dispara mientras la foto anterior
   se estampa.
4. **La política de imagen está esparcida:** resolución en `VistaCamara`, calidad en `Miniaturas`,
   formato en `Miniaturas` + `CapturarMedio` + `RutasStorage` (que todavía nombra `.jpg` con
   contenido WebP), y el hilo depende de quién llame.
5. **Si Android Go mata el proceso** entre la captura y la marca, `reconciliar` sube la foto sin
   marca (inferido de la lectura del código; no reproducido).

## Requisitos que se conservan (de `PLAN_MARCA_DE_AGUA.md`)

- El obturador **no espera** al GPS ni al mapa.
- Sin fix de GPS se graba la foto igual, con fecha y sin coordenadas ni mapa.
- Si el mapa falla o llega en blanco, se omite solo el tile.
- La marca es irreversible y va sobre el original; **la miniatura queda limpia**.
- El mapa se genera con Google Maps SDK en el dispositivo. Tiles offline fuera de alcance.

## Qué NO cambia (fuera de alcance)

- Contrato de Firestore y reglas de Firestore/Storage (Storage ya acepta `image/webp`).
- El snapshot del mapa y el GPS por foto (eso es el candidato 2, otro plan).
- Mover toda la preparación al worker (candidato 3). Aquí solo entra la red de seguridad mínima.
- El audio.
- Fotos ya subidas: no se reconvierten.

## Solución propuesta

Un módulo profundo, `RevelarFoto` (nombre por confirmar, ver D7), dueño de **toda** la política de
imagen y del hilo donde corre. Dos momentos:

**Al disparar** (barato, sin decode completo):
- Se guardan tal cual los bytes JPEG que entrega el HAL, más los grados de rotación.
- Se genera la miniatura con un decode reducido (`inSampleSize`), que en JPEG es barato, se rota el
  bitmap chico y se codifica a WebP q70. La miniatura sigue limpia.

**Al revelar** (en segundo plano, cuando llega el GPS o vence el timeout):
- Un solo decode al tamaño objetivo, rotación, estampado de la marca sobre ese mismo bitmap, **un
  solo** encode WebP q80, reescritura del archivo y marca de "revelada".
- Corre siempre en `Dispatchers.Default`, fijado dentro del módulo: ningún llamador puede volver a
  ponerlo en el hilo principal.
- Idempotente: revelar una foto ya revelada no hace nada.
- Fija el tamaño final (lado largo ≤ 1600 px) aunque el HAL entregue más.

**Red de seguridad:** `SubidaMedioProceso` nunca sube una foto sin revelar. Si encuentra una, la
revela antes de subirla (ver D6).

Resultado esperado (inferido, no medido): obturador sin decode completo, una sola pérdida WebP,
pico de 1–2 bitmaps en vez de 4, política de imagen en un módulo.

## Desglose de tareas

Cada tarea termina en una comprobación y va en su propio PR hacia `pf-appcus-d` (D9). Todo sale
junto en un solo release, **1.0.4**, al cerrar la Task 7.

### Sin hotfix aparte (D1)
El bug del hilo principal **no** sale en un PR aparte: lo arregla el propio refactor, porque
`RevelarFoto` fija `Dispatchers.Default` dentro de su interfaz (Task 3) y la Task 5 cambia al
llamador. Producción (1.0.3) no tiene WebP, así que el costo de esperar es el bug que ya existe con
JPEG. Consecuencia: **no se construye un release desde `pf-appcus-d` hasta cerrar la Task 7**,
porque `-d` ya tiene WebP con el bug.

### Task 1 — Línea base sin ZTE físico
No hay un ZTE A55/A54 disponible para pruebas. La línea base sale de dos fuentes (ver D3):
- **Proxy en Firebase Test Lab: TECNO POP 7 (`TECNO-BF6`)**, dispositivo físico con el mismo chip
  (Unisoc SC9863A1), 2 GB de RAM (menos que el ZTE, así que es un proxy pesimista), Android 12 Go,
  pantalla 720×1612 y userspace **solo de 32 bits** (`armeabi-v7a`). Una prueba instrumentada mide
  el tratamiento de una foto de 1600×1200: decode, rotación, marca, encode WebP, tiempos y pico de
  memoria nativa. Corre sobre el código actual de `-d` (antes) y después del refactor, con el mismo
  fixture.
- **Fixture (D13):** una foto con licencia CC0 de una escena parecida a la evidencia (carretera o
  tráiler, sin placas legibles ni personas), versionada en `app/src/androidTest/assets` con su
  fuente y licencia anotadas. No se usa una imagen sintética: se comprime distinto y falsea los
  tiempos de WebP.
- **Tope de gasto (D14):** máximo 3 corridas en el TECNO POP 7 (antes, después y un reintento),
  ~15 minutos de dispositivo. Más corridas requieren preguntar antes.
- **Sin "antes" de campo (D10):** el 1.0.3 no tiene telemetría y no se saca un release solo para
  medir. La comparación antes/después sale de Test Lab; la telemetría de campo (Task 5) confirma
  el "después" en los A55 reales.
- Sin esta línea base no hay forma de probar que el refactor mejora algo.

### Task 2 — Esquema: estado de revelado
- Columnas nuevas en `MedioEntity` según D5, `MIGRACION_5_6` y su prueba.
- Filas existentes quedan como reveladas (ya pasaron por el flujo viejo).

### Task 3 — Núcleo de `RevelarFoto`
- Interfaz pequeña: miniatura rápida al disparar y `revelar(medioId)` en segundo plano.
- Implementación Android: `BitmapFactory` con `inSampleSize` + `inMutable`, `Matrix` para rotar,
  `DibujanteMarcaDeAgua` para dibujar sobre el bitmap (sin `copy`), `Miniaturas.webp` para codificar,
  reciclado explícito.
- Política concentrada: tamaño máximo, calidades, formato, extensión de Storage y `contentType`.
- Fallos (D12), conservando la regla "subir sin marca es mejor que no subir":
  - falla solo la marca → se revela sin marca (rotada, en WebP);
  - falla el decode (archivo corrupto) → se deja la foto cruda como `image/jpeg` con la orientación
    escrita en EXIF, para que no quede de lado, y se registra el fallo en `RegistroDeFallos`.
- Pruebas: JVM a través de la interfaz con el codec falso (ya existe el patrón en
  `ComponerMarcaDeAguaTest`); instrumentadas para el codec real.

### Task 4 — Obturador barato
- `tomarFoto` deja de decodificar: entrega bytes crudos + rotación.
- `CapturarMedio` persiste la foto cruda (`image/jpeg`, no revelada) y la miniatura rápida.

### Task 5 — Revelado diferido, telemetría y limpieza
- `adjuntarGpsEnSegundoPlano` llama a `RevelarFoto` en vez de `GrabarMarcaDeAguaEnFoto`.
- Telemetría (D11): evento `foto_revelada` vía `RegistroDeEventos`, sin PII, con
  `ms_disparo` (toque → obturador liberado), `ms_revelado` (decode → archivo escrito), `con_gps`,
  `con_mapa`, `origen` (`captura` | `subida`), `resultado` (enum cerrado: `OK`, `SIN_MARCA`,
  `FALLO_DECODE`) y `kb_final`. El modelo del dispositivo lo agrega Analytics.
- Se borran `GrabarMarcaDeAguaEnFoto`, el decode de `BitmapEstampadorFoto`,
  `Miniaturas.fotoOriginalYMiniatura` y `VistaCamara.aBitmap` si quedan sin llamadores.

### Task 6 — Red de seguridad en la subida y rutas
- `SubidaMedioProceso` revela antes de subir si hace falta (emite `foto_revelada` con
  `origen = subida`).
- `RutasStorage` toma la extensión del `contentType` (`.webp` para fotos nuevas, `.jpg` para las
  viejas pendientes).

### Task 7 — Verificación y release 1.0.4
- Repetir en el TECNO POP 7 de Test Lab la prueba de la Task 1 y comparar.
- Prueba manual en CPH2769: ráfaga de fotos, sin red, sin GPS, matar el proceso entre captura y
  subida, y actualizar desde 1.0.3 con fotos pendientes (prueba la migración).
- **Antes del APK:** desplegar `storage.rules` (`firebase deploy --only storage`, PR #6 de infra).
- Subir a 1.0.4 (versionCode 5), APK firmado desde `-d`, GitHub Release, `AGENTS.md` y `CHANGELOG.md`.
- Tras el rollout: revisar `foto_revelada` en la consola, filtrado por modelo ZTE.

## Decisiones

### Cerradas (2026-11-06)

| # | Decisión | Resuelto |
| --- | --- | --- |
| D1 | Hotfix aparte o dentro del refactor | Dentro del refactor; no se libera `-d` hasta la Task 7 |
| D2 | Liberar 1.0.4 = WebP + hotfix antes del refactor | No aplica (sale todo junto) |
| D3 | Medición sin ZTE físico | TECNO POP 7 en Test Lab (gasto autorizado) + telemetría de campo |
| D4 | Captura cruda | Copiar el búfer del `ImageProxy` y guardar con `AlmacenArchivos`; rotación en Room |
| D5 | Estado "revelada" | Columnas nuevas (`revelada`, `rotacionGrados`) con `MIGRACION_5_6`; filas existentes = reveladas |
| D6 | Proceso muerto antes de revelar | El worker revela con fecha y coordenadas, sin mapa |
| D7 | Nombre y lugar | `RevelarFoto` en `data/foto/`, sin glosario aparte |
| D8 | Tamaño final | El revelado fija el lado largo ≤ 1600 px |
| D9 | Cómo se parten los PRs | Un PR por tarea hacia `-d`; todo sale junto en el release 1.0.4 |
| D10 | Medición "antes" en campo | No hay; la telemetría va en 1.0.4 y el antes/después sale de Test Lab |
| D11 | Evento de telemetría | `foto_revelada` con `ms_disparo`, `ms_revelado`, `con_gps`, `con_mapa`, `origen`, `resultado`, `kb_final` |
| D12 | Si el revelado falla | Sin marca si falla la marca; cruda con EXIF si falla el decode; nunca se pierde la foto |
| D13 | Foto de prueba | CC0, escena parecida, sin placas ni personas, en `androidTest/assets` |
| D14 | Tope de Test Lab | 3 corridas, ~15 min de dispositivo |

Glosario mínimo (D7): **foto cruda** = los bytes JPEG del HAL tal como llegan, sin rotar ni marcar;
**revelar** = decode → rotación → marca → WebP, una sola vez; **foto revelada** = el WebP final que
se sube.

Hechos verificados que sostienen D4–D5: CameraX entrega el `ImageProxy` en JPEG **sin rotación
aplicada**; los grados vienen en `imageInfo.rotationDegrees` (documentación de
`ImageCapture.OnImageCapturedCallback`). Por eso la rotación se guarda en Room. Las pruebas de
migración del repo son de JVM (`MigracionTelefonoACorreoTest`), sin `MigrationTestHelper` ni
esquemas exportados.

## Notas / riesgos

- **No hay ZTE físico** y CPH2769 (el teléfono de pruebas) no usa el SC9863A. El TECNO POP 7 de
  Test Lab es el proxy más cercano disponible; la confirmación final viene de la telemetría de campo.
- **Userspace de 32 bits:** el TECNO POP 7 de Test Lab solo soporta `armeabi-v7a`. Es probable que
  el ZTE A55 (también Go) sea igual (inferido, no verificado). Cualquier filtro de ABI futuro debe
  conservar `armeabi-v7a`; filtrar a `arm64-v8a` dejaría a los custodios sin poder instalar.
- Firebase Test Lab se retira el 2027-09-30 (aviso de `gcloud`); sirve para este plan.
- `pf-appcus-d` ya tiene WebP (PR #10) con el bug del hilo principal: **no construir un release
  desde `-d` hasta cerrar la Task 7**. Producción (1.0.3) conserva el bug con JPEG hasta entonces.
- `storage.rules` con `image/webp` está mergeado (PR #6 de infra) pero hay que desplegarlo
  (`firebase deploy --only storage`) **antes** de liberar cualquier APK con WebP.
- Cambiar el esquema de Room afecta a instalaciones en campo: la migración se prueba antes de
  liberar.
