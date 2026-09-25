# Plan — Allowlist por documento (B1): la app lee su propio teléfono

**Estado:** Implementado el 2026-09-25 (Tareas 1-3 hechas; Tarea 4 verificada con `testDebugUnitTest` 239/239 y `assembleDebug`; queda la verificación funcional manual, pendiente del re-export del seed del emulador en infra).
**Origen:** paso B1 del despliegue de `pf-infra-firebase` (hallazgo P1-6 de la auditoría de infra). Brief de dependencia: `pf-infra-firebase/.scratch/telefonos-por-documento/dependencia-app-custodio.md`.
**Alcance:** una sola capa de datos de esta app. No cambia la interfaz de dominio ni la UI.

---

## Por qué

En infra, la allowlist de teléfonos dejó de ser un único documento enumerable
(`config/telefonos` con un arreglo `telefonos`) y pasó a un **documento por teléfono**
en la colección `telefonos_autorizados/{e164}` (el id del documento es el número en
E.164). El motivo: con el modelo viejo, la regla `allow read: if autenticado()` dejaba
que **cualquier sesión autenticada enumerara los teléfonos de todos los custodios** (PII).
La regla nueva solo deja que cada custodio lea el documento de SU propio número:

```
match /telefonos_autorizados/{e164} {
  allow read: if autenticado()
    && 'phone_number' in request.auth.token
    && request.auth.token.phone_number == e164;
  allow write: if false;
}
```

Esta app hoy lee el modelo viejo. Cuando infra despliegue las reglas nuevas (paso B3),
el documento `config/telefonos` deja de existir y las apps que aún lo lean **no podrán
verificar su número tras el OTP**. Por eso este cambio es el prerequisito B1: debe salir
publicado **antes o a la vez** que las reglas nuevas.

---

## Estado actual (verificado en el código)

La lectura de la allowlist está **completamente aislada** en un método:

- `data/remote/FirestoreAllowlistRepositorio.kt` → `contiene(telefono)`: hoy hace
  `get()` a `config/telefonos`, saca el arreglo `telefonos` y evalúa `telefono in telefonos`.
  Cierre seguro por omisión: si el documento no existe, devuelve `false`.
- La interfaz `domain/repository/AllowlistRepositorio.kt` (`contiene(telefono): Result<Boolean>`)
  **no cambia**.
- Por encima de la interfaz nada asume el modelo de datos:
  - `ui/login/LoginViewModel.kt` llama `validarAllowlist(...)` → `allowlistRepositorio.contiene(...)`.
  - `di/PipelineModule.kt` construye `FirestoreAllowlistRepositorio(firestore)`.
  - Los tests del login usan `FakeAllowlistRepositorio` (opera a nivel de interfaz).
- No hay test unitario ni instrumentado que lea el modelo real de la allowlist en esta
  app. La cobertura de reglas del modelo nuevo vive en el arnés de `pf-infra-firebase`.

Conclusión: el cambio toca **un cuerpo de método**. Nada más necesita cambiar para
funcionar.

---

## El cambio, en una frase

`contiene(telefono)` deja de buscar el número dentro del arreglo de `config/telefonos` y
pasa a consultar la **existencia** del documento `telefonos_autorizados/{telefono}`.

---

## Tareas

### Tarea 1 — Reescribir `FirestoreAllowlistRepositorio.contiene()`

- Reemplazar la lectura del arreglo por un `get()` al documento
  `telefonos_autorizados/{telefono}` y devolver `documento.exists()`.
- El id del documento es el teléfono en E.164 (p. ej. `+525544880512`) — el mismo string
  que ya recibe `contiene()`. No hay transformación ni normalización nueva.
- Conservar el envoltorio `ejecutarRemoto { }` (traduce el error a `Result`) y el cierre
  seguro por omisión (doc inexistente → `false`).
- Actualizar el KDoc de la clase (hoy describe el arreglo `config/telefonos`) y las
  constantes: quitar `CONFIG`/`TELEFONOS`, dejar el nombre de la colección
  `telefonos_autorizados`.
- Nota de reglas: la lectura del propio documento está permitida porque la app se
  autentica por SMS con ese mismo número (`request.auth.token.phone_number == e164`).
  Solo se necesita `exists()`, no leer campos.

### Tarea 2 — Confirmar que nada más asume el modelo viejo

- `grep` de `config/telefonos` en `app/src/` para confirmar que la única referencia es
  este repositorio.
- Confirmar (esperado: sin cambios) que interfaz, DI, `LoginViewModel` y
  `FakeAllowlistRepositorio` no requieren tocarse.

### Tarea 3 — Documentación del repo

- Ajustar la línea de `AGENTS.md` que describe la allowlist
  (`config/telefonos.telefonos verificada DESPUÉS del OTP`) al modelo
  `telefonos_autorizados/{e164}`.
- Alinear `README.md` / `docs/PLAN.md` si describen el modelo de allowlist.
- Entrada en `CHANGELOG.md` por cada cambio (regla de steering del repo).

### Tarea 4 — Verificar

- `./gradlew testDebugUnitTest`: los tests del login usan el fake y deben seguir verdes
  **sin cambios** — esa es la señal de que el aislamiento se respetó.
- `./gradlew assembleDebug`: confirmar que compila.
- Verificación funcional (manual, tuya): con `telefonos_autorizados/{miNumero}` sembrado
  en el emulador/proyecto, el login pasa; sin él, se rechaza. El debug apunta al emulador
  vía `modo_emulador`; el login SMS real usa el APK release.

---

## Fuera de alcance (deliberado)

- **No cambia la interfaz** `AllowlistRepositorio` ni la UI ni el DI.
- **No hay migración de datos aquí:** la app solo lee. Poblar `telefonos_autorizados` es
  trabajo del aprovisionamiento en infra (paso B2, `provisionar.js`).
- **No decide el orden de despliegue**, solo lo respeta (ver abajo).

---

## Riesgo y orden de despliegue

El único riesgo es de **secuencia**, no de código:

- Si esta versión de la app sale **después** de que infra despliegue las reglas nuevas
  (B3), hay una ventana en la que apps viejas —leyendo `config/telefonos`, ya borrado— no
  pueden verificar su número.
- **Mitigación:** publicar esta versión de la app **antes o a la vez** que B3. El orden
  seguro de todo el corte es: **app (B1) → poblar datos (B2) → reglas (B3) → función (B4)
  → prueba (B5)**.

---

## Prerequisito de ejecución

Este repo marca `docs/GENERIC_RULES.md` como lectura obligatoria antes de modificar
código, y `AGENTS.md` fija las convenciones de Android (AGP 9, workflow de pausa para
Sync, etc.). Para **implementar** este plan hay que aplicarlas; para el diseño de este
documento ya se leyeron.

---

## Resultado de la implementación (2026-09-25)

- **Tarea 1.** `FirestoreAllowlistRepositorio.contiene()` quedó en un `get()` a
  `telefonos_autorizados/{telefono}` con `exists()`, dentro de `ejecutarRemoto`, con
  cierre seguro por omisión. KDoc y constantes actualizados.
- **Tarea 2.** `grep` de `config/telefonos` en `app/src/`: solo quedaban dos KDoc
  (`AllowlistRepositorio`, `DominioError.TelefonoNoAutorizado`), alineados al modelo nuevo.
  Interfaz, DI, `LoginViewModel` y `FakeAllowlistRepositorio` sin tocar.
- **Tarea 3.** Actualizados `AGENTS.md`, `README.md` (alta/baja de custodios) y
  `docs/PLAN.md` (§5.1, §5.3, §5.4, §9 A2/A3), con entrada en `CHANGELOG.md`.
- **Tarea 4.** `./gradlew testDebugUnitTest` → **239/239** (los 11 de `LoginViewModelTest`
  corrieron sin cambios: el aislamiento se respetó) y `./gradlew assembleDebug` verde.
  La **verificación funcional** sigue pendiente y no se puede cerrar en este repo: el
  seed del emulador (`../pf-infra-firebase/seed`) es una exportación del modelo viejo —lee
  `config/telefonos`— y hay que re-exportarlo en infra (o crear el documento a mano en la
  Emulator UI) para que el login pase contra el emulador.

Nota: el brief citado en el encabezado
(`pf-infra-firebase/.scratch/telefonos-por-documento/dependencia-app-custodio.md`) **no
existe** en el repo de infra al momento de implementar; este plan se aplicó contra las
reglas y el aprovisionamiento reales de `pf-infra-firebase` (`firestore.rules`
`match /telefonos_autorizados/{e164}` y `provision/provisionar.js`).
