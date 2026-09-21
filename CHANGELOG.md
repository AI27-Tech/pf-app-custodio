# CHANGELOG

Registro de cambios del proyecto App Custodio. Las entradas más recientes van primero.

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
