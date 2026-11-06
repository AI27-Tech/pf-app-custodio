# PLAN_REVELAR_FOTO — Un módulo profundo para revelar la foto de evidencia

> **Alcance de este documento: la app `pf-app-custodio` (Echo).** Toda la implementación vive en
> este repo. Fuera del código quedan dos pasos humanos: el despliegue de `storage.rules`
> (`pf-infra-firebase`, ya mergeado en PR #6) y la prueba en un ZTE Blade A55/A54 real.
>
> **Requisito de lectura previa:** `docs/GENERIC_RULES.md` y `docs/PLAN_MARCA_DE_AGUA.md`.
>
> **Origen:** candidato 1 de `docs/REVISION_ARQUITECTURA_ZTE.html` (revisión del 2026-11-06).
>
> **Estado:** BORRADOR. Las decisiones de la sección "Decisiones abiertas" están pendientes de
> confirmar con el humano. Nada de esto se implementa hasta cerrarlas.

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

## Desglose de tareas (propuesto; se ajusta al cerrar las decisiones)

Cada tarea termina en una comprobación y va en su propio PR hacia `pf-appcus-d`.

### Task 0 — Hotfix: sacar la marca de agua del hilo principal
- `GrabarMarcaDeAguaEnFoto`: envolver la composición en `withContext(Dispatchers.Default)`.
- Test JVM: la composición no corre en el hilo del llamador.
- Se libera como 1.0.4 junto con WebP (ver D2). La Task 5 borra este código después.

### Task 1 — Línea base sin ZTE físico
No hay un ZTE A55/A54 disponible para pruebas. La línea base sale de dos fuentes (ver D3):
- **Proxy en Firebase Test Lab: TECNO POP 7 (`TECNO-BF6`)**, dispositivo físico con el mismo chip
  (Unisoc SC9863A1), 2 GB de RAM (menos que el ZTE, así que es un proxy pesimista), Android 12 Go,
  pantalla 720×1612 y userspace **solo de 32 bits** (`armeabi-v7a`). Una prueba instrumentada mide
  el tratamiento de una foto de 1600×1200: decode, rotación, marca, encode WebP, tiempos y pico de
  memoria nativa. Corre antes y después del refactor con el mismo fixture.
- **Telemetría de campo** en los A55 reales de los custodios: un evento de analítica sin PII con la
  duración del disparo y del revelado. El modelo del dispositivo lo agrega Analytics solo.
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
- Pruebas: JVM a través de la interfaz con el codec falso (ya existe el patrón en
  `ComponerMarcaDeAguaTest`); instrumentadas para el codec real.

### Task 4 — Obturador barato
- `tomarFoto` deja de decodificar: entrega bytes crudos + rotación.
- `CapturarMedio` persiste la foto cruda (`image/jpeg`, no revelada) y la miniatura rápida.

### Task 5 — Revelado diferido y limpieza
- `adjuntarGpsEnSegundoPlano` llama a `RevelarFoto` en vez de `GrabarMarcaDeAguaEnFoto`.
- Se borran `GrabarMarcaDeAguaEnFoto`, el decode de `BitmapEstampadorFoto`,
  `Miniaturas.fotoOriginalYMiniatura` y `VistaCamara.aBitmap` si quedan sin llamadores.

### Task 6 — Red de seguridad en la subida y rutas
- `SubidaMedioProceso` revela antes de subir si hace falta.
- `RutasStorage` toma la extensión del `contentType` (`.webp` para fotos nuevas, `.jpg` para las
  viejas pendientes).

### Task 7 — Verificación y documentación
- Repetir en el TECNO POP 7 de Test Lab la prueba de la Task 1 y comparar; después, comparar la
  telemetría de campo de los A55 entre la versión anterior y la nueva.
- Prueba manual: ráfaga de fotos, sin red, sin GPS, matar el proceso entre captura y subida.
- `AGENTS.md`, `CHANGELOG.md`, release.

## Decisiones abiertas

| # | Decisión | Recomendación |
| --- | --- | --- |
| D1 | ¿Hotfix aparte (Task 0) ahora, o esperar a que el refactor lo arregle? | Hotfix aparte ahora |
| D2 | ¿Liberar 1.0.4 = WebP + hotfix antes del refactor? | Sí, tras desplegar `storage.rules` |
| D3 | Sin ZTE físico (confirmado): ¿medir con el TECNO POP 7 de Test Lab (tiene costo por minuto) y con telemetría de campo en 1.0.4? | Las dos |
| D4 | Captura cruda: copiar el búfer del `ImageProxy` a bytes, o que CameraX escriba el archivo | Copiar el búfer y usar `AlmacenArchivos` |
| D5 | Estado "revelada": columnas nuevas con migración, o reusar `contentType` como bandera | Columnas nuevas |
| D6 | Revelado en el worker (proceso muerto): con mapa, sin mapa, o no revelar | Sin mapa |
| D7 | Nombre y paquete del módulo, y si se crea un glosario | `RevelarFoto` en `data/foto/` |
| D8 | ¿El revelado fija el lado largo ≤ 1600 px aunque el HAL entregue más? | Sí |

## Notas / riesgos

- **No hay ZTE físico** y CPH2769 (el teléfono de pruebas) no usa el SC9863A. El TECNO POP 7 de
  Test Lab es el proxy más cercano disponible; la confirmación final viene de la telemetría de campo.
- **Userspace de 32 bits:** el TECNO POP 7 de Test Lab solo soporta `armeabi-v7a`. Es probable que
  el ZTE A55 (también Go) sea igual (inferido, no verificado). Cualquier filtro de ABI futuro debe
  conservar `armeabi-v7a`; filtrar a `arm64-v8a` dejaría a los custodios sin poder instalar.
- Firebase Test Lab se retira el 2027-09-30 (aviso de `gcloud`); sirve para este plan.
- `pf-appcus-d` ya tiene WebP (PR #10) con el bug del hilo principal: **no construir un release
  desde `-d` sin la Task 0**.
- `storage.rules` con `image/webp` está mergeado (PR #6 de infra) pero hay que desplegarlo
  (`firebase deploy --only storage`) **antes** de liberar cualquier APK con WebP.
- Cambiar el esquema de Room afecta a instalaciones en campo: la migración se prueba antes de
  liberar.
