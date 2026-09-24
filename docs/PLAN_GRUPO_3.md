# PLAN — Grupo 3: que la app sea usable en campo (§17.1 de `docs/PLAN.md`)

Este documento **detalla en modo TDD** la tanda del Grupo 3, que `docs/PLAN.md` §17.1 dejó a
nivel de tanda a propósito ("se detallarán al arrancar cada una"). Sigue el mismo método que
§15 y §16: cada hallazgo con su prueba, clústeres con dependencias explícitas, y cierre con
marcas en `docs/AUDIT.md` + entrada en `CHANGELOG.md`.

**Origen:** `docs/AUDIT.md` §5 (R1, R2, R3) y §6 (U1–U16; U17 ya SOLUCIONADO). El orden y la
selección de hallazgos salen de `docs/PLAN.md` §17.1.

**Estado del repo al arrancar (verificado 2026-09-23):** Grupos 0/E/1/2 y §14 hechos y
commiteados; rama `pf-appcus-d`, árbol limpio. Algunas cosas que el AUDIT describía ya
derivaron y **hay que verificar antes de tocar**: `AppNavHost.navegarADestino` ya usa
`launchSingleTop = true` y `popUpTo(VIAJE)` para las pestañas; `ReporteScreen` ya pinta la
franja de sin conexión y la de "sin etiquetar". El resto del Grupo 3 sigue abierto.

---

## 1. Decisiones de producto (confirmadas con el usuario, 2026-09-23)

| # | Tema | Decisión |
|---|---|---|
| **P1** | **U1 — ráfaga con etiqueta diferida** | **FUERA DE ALCANCE por ahora.** Se difiere a una versión futura. El flujo de captura conserva la hoja de etiquetado obligatoria por captura. **Excepción incluida:** la *trampa sin salida* de U1 (catálogo vacío → la hoja no se puede cerrar de ninguna forma) sí se arregla, porque es un bug de robustez, no una decisión de producto. |
| **P2** | **U12 — motivo de descarte** | Chips de motivo predefinidos (**placa equivocada, viaje cancelado por el cliente, duplicado, prueba/error, otro**), con campo de texto libre visible **solo** cuando se elige "otro". |
| **P3** | **U14 — glosario** | Términos canónicos **"Reporte"** (el contenedor de un viaje) y **"Evidencias"** (lo capturado). Se elimina de la UI visible el uso mezclado de "viaje" y de "medios/archivos"; la pestaña sigue siendo **"Reporte"**. |

Consecuencia de P1: el clúster de ráfaga (lo que el AUDIT y §17.1 llamaban U1/U2 juntos) se
reduce a **U2** (etiqueta heredada + quitar el "Guardar" redundante) + la salida segura de la
hoja. La ráfaga libre no se implementa.

---

## 2. Alcance de la tanda

| # | Decisión |
|---|---|
| R1 | Hallazgos de campo del Grupo 3: **R1, R2, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15**. Se **excluye** U1 (ráfaga, P1) salvo la salida segura de la hoja, y U16 (deuda de biblioteca: chips duplicados, `MiniaturaMedio` muerto, previews rotos) que es limpieza sin impacto de campo y se lleva al Grupo 6. |
| R2 | **R3 entra aquí** (badge por `COUNT`, no por lista) porque toca `AppNavHost`/pendientes y es prerrequisito natural de U6. El resto del grupo R (rendimiento: R4–R16) queda para el **Grupo 4**. |
| R3 | **No se toca Firebase** ni sus reglas. Solo código de app y, donde haga falta, DAO/consulta (aditivo). Sin pausas de Sync salvo que un hallazgo pida una dependencia nueva; hoy no se prevé ninguna. |
| R4 | La verificación cronometrada de 5 s, TalkBack y escala de texto 130/200 % son **PENDIENTE-HW**: requieren dispositivo real, que este agente no acciona. Se dejan anotadas, no se marcan como hechas. |

---

## 3. Hallazgos, confirmados contra el código

| ID | Sev. | Hallazgo | Evidencia (archivo:línea al arrancar) |
|---|---|---|---|
| **R1** | ALTO | La app no abre en la cámara | `AppNavHost.kt` arranca en `LOGIN` y navega a `INICIO` (pestaña Reporte, no el visor) tras resolver sesión |
| **R2** | ALTO | "Sin reporte activo" se ve aunque haya viaje | `CapturaUiState()` arranca con `reporte = null`; `ReporteScreen` no distingue "cargando" de "no hay" |
| **R3** | ALTO | El grafo se reconstruye en cada emisión de la cola | `AppNavHost` observa `pendientes: List<Medio>` y pinta `pendientes.size` en la barra; existe ya `MedioDao.contarPendientes(): Flow<Int>` sin usar para el badge |
| **U2** | ALTO | Sin etiqueta recordada; "Guardar" redundante | `HojaEtiqueta.kt`: `seleccionadas` arranca vacío por `medio.id`, sin heredar la última |
| **U3** | ALTO | Cero confirmación no visual | grep: sin `performHapticFeedback`/`Vibrator`/`MediaActionSound` |
| **U4** | ALTO | Pendientes oculta el estado en el caso normal | `PendientesScreen.detalleDe` devuelve la etiqueta y oculta el estado si está etiquetado |
| **U5** | ALTO | "Enviado" se comunica por ausencia | `ArchivosScreen.CeldaArchivo` no dibuja nada para COMPLETA; enviada y sin etiquetar se ven igual |
| **U6** | ALTO | El badge mezcla tres cosas | `MedioDao.contarPendientes` = todo `!= COMPLETA` (sin etiquetar + en cola + fallido) |
| **U7** | ALTO | Errores sin salida | `Dialogos.DialogoError` tiene un solo botón "Entendido", sin acción de recuperación |
| **U8** | ALTO | Carga/error/vacío indistinguibles en Historial | `HistorialScreen`: el `when` evalúa `reportesTotales == 0` antes que `cargando`/`error`; `HistorialRoute` no cablea `onReintentar` |
| **U9** | ALTO | Franja de sin conexión solo en una pantalla | `ReporteScreen` la pinta; Captura/Audio/Archivos/Pendientes/Cierre no |
| **U10** | ALTO | Doble toque duplica pantallas | Las navegaciones a CAPTURA/AUDIO/ARCHIVOS/CIERRE/PENDIENTES/DETALLE usan `navigate(...)` sin `launchSingleTop` (las pestañas sí lo tienen) |
| **U11** | ALTO | El formulario de cierre se pierde con back/rotación | `remember` (no `rememberSaveable`) en Cierre/Placa/Archivos/HojaEtiqueta |
| **U12** | ALTO | Motivo de descarte: texto libre en diálogo | `DialogoDescarte`: `OutlinedTextField` libre, sin motivos predefinidos, sin foco |
| **U13** | MEDIO | `FranjaAviso` usada para texto informativo permanente | `AudioScreen`: aviso ámbar para "Máximo 5:00…" |
| **U14** | MEDIO | Vocabulario inconsistente | reporte/viaje; evidencias/archivos/medios; FALLIDA/Fallido/Subida fallida |
| **U15** | MEDIO | Repetición y jerarquía en `ReporteScreen` | el mismo número aparece 3 veces; no se destaca "cuántas faltan por subir" |

---

## 4. Clústeres y decisiones de diseño

Se agrupan por acoplamiento real (mismos archivos / mismo cambio conceptual), no por severidad.

### G3-A — Arranque en el visor y estado de carga (R1, R2)

**D-A1.** El arranque con sesión y viaje activo navega a `Rutas.CAPTURA` en lugar de a
`Rutas.INICIO`, dejando `INICIO` en la pila (back vuelve al hub Reporte). Si hay sesión pero
**no** hay viaje activo, se queda en `INICIO` (la pantalla de "iniciar reporte"): abrir la
cámara sin reporte no tiene sentido. Esto exige que la raíz sepa distinguir "cargando" de
"sin viaje" (R2), así que R2 va **antes** de R1 dentro del clúster.

**D-A2 (R2).** `CapturaUiState` gana un flag `cargando` (arranca `true`) que pasa a `false`
cuando el primer valor real de `reporteYMedios` llega. `ReporteScreen` pinta un estado de
carga (reutilizando `CajaCargaReportes` o un spinner centrado) mientras `cargando && reporte
== null`, en vez de saltar a "Sin reporte activo". El `LaunchedEffect` de arranque de
`AppNavHost` solo decide destino cuando `cargando == false`.

**Prueba:** unit de `CapturaViewModel`/`CapturaUiState` que verifica que el estado inicial es
`cargando = true, reporte = null` y que tras la primera emisión con reporte nulo pasa a
`cargando = false`. La navegación (R1) se cubre con una prueba de Compose de `AppNavHost` si
resulta barata; si no, queda como verificación manual documentada.

### G3-B — Etiqueta heredada y salida segura de la hoja (U2, salida de U1)

**D-B1 (U2).** `CapturaViewModel` recuerda la **última selección de Situación** confirmada del
viaje (`ultimaSituacion: List<String>` en `interno`), y `HojaEtiqueta` la usa como valor
inicial de `seleccionadas` cuando existe. Tocar un chip sigue permitiendo corregir. El botón
"Guardar" **se conserva** (es el punto donde se adjunta la ubicación y se encola), pero el
copy y el estado dejan claro que con la herencia ya suele bastar un toque. No se implementa
"tocar el chip confirma directo" porque rompe la multi-selección de §14 (una Situación + varias
Evidencias); la herencia es la que quita los ~100 toques.

**D-B2 (salida segura, sub-bug de U1).** Si el catálogo activo **no tiene ninguna etiqueta de
Situación** (doc remoto vacío/malformado y respaldo también degradado), la hoja hoy queda
atrapada. Se añade: cuando `idsSituacion` está vacío, la hoja **permite cerrarse** (`onCerrar`
disponible, gesto de baja habilitado) y el medio queda como "sin etiquetar" (ya existe la ruta
`retomarEtiquetado`/franja de Archivos). Con catálogo normal el comportamiento no cambia:
sigue exigiendo Situación.

**Prueba:** unit del ViewModel de que `guardarEtiqueta` con herencia produce el mismo
`Result.Success` y que `ultimaSituacion` se actualiza; prueba de Compose (o de lógica pura del
predicado) de que con `idsSituacion` vacío la hoja es descartable.

### G3-C — Confirmación no visual (U3)

**D-C1.** Un componente `Retroalimentacion` (en `ui/components/` o `data/`) que encapsula
`View.performHapticFeedback` + `MediaActionSound`/`AudioManager` para el obturador y un tic de
encolado. Se dispara desde la UI (no desde el ViewModel: necesita `View`/contexto). El disparo
del obturador da háptica corta + sonido; el encolado (cuando `avisoEncolado` incrementa), un
tic. Sin permisos nuevos (`performHapticFeedback` y `MediaActionSound` no los piden).

**Prueba:** es efecto de plataforma; no hay aserción JVM útil. Se cubre con verificación manual
documentada (PENDIENTE-HW) y se deja el disparo detrás de un punto único testeable (que
`avisoEncolado` incrementa ya está cubierto por el ViewModel).

### G3-D — Estado de subida visible y badge desglosado (U4, U5, U6, y R3)

**D-D1 (U4).** `PendientesScreen.detalleDe` pasa a mostrar **siempre el estado** (En cola /
Enviando… / Subida fallida / Falta etiqueta) y usa la etiqueta como información secundaria, no
en su lugar. El botón de reintento se muestra solo en FALLIDA (correcto), pero el renglón
fallido deja de verse igual que el en-cola.

**D-D2 (U5).** `ArchivosScreen.CeldaArchivo` dibuja el `ChipEstadoSubida` de la biblioteca
(icono + color) para COMPLETA/EN_PROGRESO/FALLIDA, de modo que "enviada" ya no sea ausencia de
adorno. "Sin etiquetar" se distingue de "enviada".

**D-D3 (U6 + R3).** Se separa el conteo del badge en sus tres componentes. Consulta(s) DAO
nuevas y aditivas:
- `contarSinEtiquetar(uid): Flow<Int>` — `tieneSituacion = 0 AND estadoSubida != 'COMPLETA'`.
- `contarEnCola(uid): Flow<Int>` — `tieneSituacion = 1 AND estadoSubida IN ('PENDIENTE','EN_PROGRESO')`.
- `contarFallidos(uid): Flow<Int>` — `estadoSubida = 'FALLIDA'`.

El badge de la barra (R3) lee un `StateFlow<Int>` de **total pendiente** derivado de un `COUNT`
(el `contarPendientes` existente o la suma de los tres), **no** de `pendientes: List<Medio>`.
`AppNavHost` deja de observar la lista completa para el badge; `PendientesScreen` sigue
observando la lista (la necesita para pintarla) pero el `builder` del `NavHost` ya no captura
la lista, matando la reconstrucción del grafo.

**Prueba:** unit/contrato de los tres `COUNT` contra Room y fakes (patrón `ContratoRepositoriosTest`);
unit del `detalleDe` de Pendientes (extraer a función pura para poder aseverar el estado).

### G3-E — Navegación robusta y estado que sobrevive (U8, U10, U11)

**D-E1 (U8).** Reordenar el `when` de `HistorialScreen`: `cargando` y `error` **antes** de
`reportesTotales == 0`. Cablear `onReintentar` en `HistorialRoute` → `viewModel::onReintentar`
(el ViewModel ya lo expone). `CajaCargaReportes`/`CajaErrorHistorial` dejan de ser código
muerto.

**D-E2 (U10).** Añadir `launchSingleTop = true` a las navegaciones `navigate(Rutas.CAPTURA/
AUDIO/ARCHIVOS/CIERRE/PENDIENTES)` y a `Rutas.detalle(...)`. Doble toque deja de apilar dos
cámaras. El reintento en Pendientes/Archivos da señal (deshabilita/cambia aspecto) tras el
toque para no producir el diálogo "ya está en subida".

**D-E3 (U11).** Cambiar `remember` → `rememberSaveable` en el estado de formulario:
`CierreScreen` (novedad, comentario, motivoDescarte, visibilidad de diálogos), `ReporteScreen`
(`mostrarHoja`), `ArchivosScreen` (`medioABorrar` — guardando el id, que es `Saveable`, no el
`Medio`), `HojaEtiqueta` (`seleccionadas` como `List<String>`/`Set` serializable, `avisoVisible`).
Salir del cierre a medias con cambios pide confirmación de descarte.

**Prueba:** unit de `HistorialScreen` (o de la función que decide el estado visible) de que
`cargando`/`error` ganan a `totales == 0`; el resto de U10/U11 es de plataforma y se cubre con
prueba de Compose donde sea barata + verificación manual.

### G3-F — Errores, avisos y copy (U7, U9, U12, U13, U14, U15)

**D-F1 (U7).** `DialogoError` acepta una acción de recuperación opcional (`onReintentar`/
etiqueta): dos botones cuando hay acción ("Reintentar" + "Entendido"), uno cuando no. Los
errores que tienen reintento natural (sin conexión, fallo de subida, fallo de captura) lo
ofrecen.

**D-F2 (U9).** La franja de sin conexión se eleva a un envoltorio común del flujo (un
`Column { if (!conectado) FranjaAviso(...) ; contenido }` reutilizable, p. ej.
`ContenedorConAviso`) aplicado en Captura, Audio, Archivos, Pendientes y Cierre, no solo en
Reporte. `RootViewModel` deja de inicializar `conectado = true` "a ciegas": arranca en un valor
que no afirme conexión hasta que el monitor emita (p. ej. exponer el primer valor real o un
tri-estado; mínimo, no pintar "hay conexión" antes de saberlo).

**D-F3 (U12, decisión P2).** `DialogoDescarte` pasa a chips de motivo predefinidos + campo
libre solo para "otro". Los motivos viven en `strings.xml`. El estado del diálogo recuerda el
chip elegido (`rememberSaveable`, se solapa con U11). Confirmar exige un motivo (chip elegido, o
texto no vacío si es "otro").

**D-F4 (U13).** `AudioScreen` deja de usar `FranjaAviso` para el texto permanente "Máximo
5:00…": pasa a `Text` con estilo secundario (informativo), reservando `FranjaAviso` para
advertencias reales (sin conexión).

**D-F5 (U14, decisión P3).** Glosario único aplicado a los strings visibles: contenedor =
**"Reporte"**, capturado = **"Evidencias"**; estados de subida con una sola forma
("Fallido"/"Enviado"/"En cola"/"Enviando"). Se corrige `reporte_limite_alcanzado` vs
`error_limite_medios` (misma cosa, dos palabras). Cambios de `strings.xml` + los pocos literales
que queden. No se renombran claves de dominio ni de Firestore (solo texto de UI).

**D-F6 (U15).** `ReporteScreen` deja de repetir el mismo número tres veces: el encabezado
destaca **cuántas faltan por subir** (el dato accionable), y las celdas de "Evidencias" /
"Pendientes" dejan de duplicar el contador del encabezado. `ArchivosScreen` corrige el copy del
vacío para no mandar de vuelta a Reporte.

**Prueba:** los cambios de copy no piden prueba (cambio de recurso); U7/U12 tienen lógica
(cuándo hay botón de reintento, cuándo se habilita confirmar) que se cubre con unit de la
función de decisión o prueba de Compose barata.

### G3-G — Cierre

Marcas `✅ ATENDIDO` en `docs/AUDIT.md` por hallazgo (con nota de **cómo** quedó y qué sigue
abierto para los que se cierran con alcance distinto — U1 diferida, U16 al Grupo 6), resultado
y verificación en este archivo y en `docs/PLAN.md` §17.1, y entrada en `CHANGELOG.md`.

---

## 5. Orden y dependencias

```
G3-A (R2→R1)        ── raíz sabe distinguir cargando/sin-viaje, luego abre en cámara
G3-D (U6+R3, U4, U5) ── consultas de conteo + estado visible ; R3 desacopla el badge de la lista
   │
   ├── G3-B (U2, salida hoja)   [independiente de A/D]
   ├── G3-C (U3)                [independiente]
   ├── G3-E (U8, U10, U11)      [U8 independiente; U11 se solapa con U12 de F]
   └── G3-F (U7, U9, U12–U15)   [U12 usa el rememberSaveable de U11]
G3-G cierre
```

- **G3-A** primero: R2 desbloquea R1 y es la base del arranque.
- **G3-D** temprano: R3 (badge por COUNT) conviene antes de tocar más `AppNavHost`, y U6 define
  las consultas que U4/U5 muestran.
- **G3-B, G3-C, G3-E, G3-F** son mayormente independientes; **U11↔U12** comparten
  `rememberSaveable`, se hace una vez.
- Cada clúster cierra con `./gradlew assembleDebug` + `./gradlew testDebugUnitTest` en verde y
  un commit. Pruebas de Compose (`connectedDebugAndroidTest`) donde sean baratas; lo que exige
  ojo humano (5 s, sol/noche, escala 130/200 %, TalkBack) queda **PENDIENTE-HW**.

---

## 6. Riesgos

1. **Superficie ancha de UI.** Muchos archivos de `ui/` se tocan. Mitiga: clústeres pequeños,
   `assembleDebug` + `testDebugUnitTest` tras cada uno, y previews `@Preview` que ya existen para
   comparar.
2. **R3 cambia cómo `AppNavHost` obtiene el badge.** Hay que asegurar que `PendientesScreen`
   siga recibiendo la lista y que el badge use el `COUNT`; el riesgo es dejar dos fuentes de
   verdad. Mitiga: una sola función de conteo en el ViewModel de pendientes.
3. **U11 (`rememberSaveable`).** Los tipos guardados deben ser `Parcelable`/primitivos; `Medio`
   no lo es, por eso se guarda el `id`. Riesgo de `RuntimeException` de Saver si se guarda un
   tipo no soportado — se prueba compilando y con rotación manual.
4. **U3 (háptica/sonido).** Depende de `View`/contexto y de ajustes del sistema (sonido de
   obturador puede ser obligatorio por región). No verificable en JVM; queda PENDIENTE-HW.
5. **U9 (`conectado` inicial).** Cambiar el valor inicial de `RootViewModel.conectado` puede
   hacer parpadear la franja al abrir con red. Mitiga: usar el primer valor real del monitor y
   `distinctUntilChanged`.

---

## 7. Fuera de alcance — no dar por resuelto

Sigue abierto todo lo que ya listaban §12.5/§13.6/§14.6/§15.7 de `docs/PLAN.md`, y en
particular dentro del propio grupo de campo/rendimiento:

- **U1** (ráfaga con etiqueta diferida) — diferida a versión futura por decisión de producto
  (P1). Solo se cierra su sub-bug de "trampa sin salida".
- **U16** (deuda de biblioteca: `ChipEstado`/`ChipCiclo` duplicados de `ChipEstadoSubida`,
  `MiniaturaMedio` muerto, previews "BAJAR BÁGARO", `modifier` doble en Audio) → **Grupo 6**.
- **Todo el resto del grupo R** (R4–R16: pipeline de imagen, `@Immutable`, índices Room,
  `collectAsStateWithLifecycle`, GPS compartido, control de red) → **Grupo 4**.
- Accesibilidad (grupo A) → **Grupo 5**, salvo lo mínimo que toque el copy/estado aquí.
- Decisiones de Producto del cierre de la auditoría (resolución de captura §3.4, retención,
  solo-Wi-Fi) no se resuelven en esta tanda.
```