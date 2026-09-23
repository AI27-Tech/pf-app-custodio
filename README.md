# App Custodio

Captura en campo para los custodios de AI27: fotos y notas de voz con metadatos (fecha, hora, GPS, autor, placa y etiqueta) que reemplazan el envío por WhatsApp. Cada archivo sube de inmediato a Firebase; el reporte (un viaje) es el contenedor que los agrupa.

Para el diseño, el contrato de datos y el roadmap completo: `docs/PLAN.md`.

## Estado

- B1 andamiaje, B2 dominio + persistencia local, B3 captura, B4 autenticación, B5 pipeline de subida (reglas de seguridad validadas con el Emulator Suite), B6 historial/estados y B7 release/documentación: completados según `docs/PLAN.md` §9.
- El **carril A** (consola de Firebase) sigue abierto: ver `docs/PLAN.md` §9 — carril A. Sin él no se puede desplegar el APK al campo.

## Build y verificación

```bash
./gradlew assembleDebug          # APK de desarrollo
./gradlew testDebugUnitTest      # pruebas JVM (JUnit4, sin dispositivo)
./gradlew assembleRelease        # APK firmado para el campo
./gradlew installDebug           # instalar en dispositivo conectado
cd firebase && npm test          # reglas de Firestore/Storage contra el Emulator Suite
```

### Pruebas instrumentadas contra el Emulator Suite

`app/src/androidTest/.../data/remote/RemotoEmuladorTest.kt` ejercita `FirebaseReporteRemoto`
y `FirebaseMedioRemoto` contra los emuladores. Es la red de regresión del hallazgo **B1** de
`docs/AUDIT.md` (las escrituras a Firestore no funcionaban y ninguna prueba lo detectaba),
así que conviene correrla antes de cada release.

Requiere **tres pasos**, y el segundo es el que se olvida:

```bash
# 1. Levantar los emuladores en la máquina de desarrollo
cd firebase && npx firebase emulators:start --only firestore,storage,auth

# 2. Abrir los túneles al dispositivo (en otra terminal)
adb reverse tcp:8080 tcp:8080    # Firestore
adb reverse tcp:9199 tcp:9199    # Storage
adb reverse tcp:9099 tcp:9099    # Auth

# 3. Correr las pruebas
./gradlew connectedDebugAndroidTest
```

**Por qué hacen falta los túneles.** `ModoEmulador.HOST` es `127.0.0.1`, y dentro de un
teléfono eso apunta al propio teléfono, no a tu Mac. `adb reverse` hace que el puerto local
del dispositivo salga por el cable hacia el host, así que la constante sigue sirviendo sin
cambiarla. En un emulador de Android la alternativa sería `10.0.2.2`, pero `adb reverse`
funciona en los dos casos y evita tener dos configuraciones. Sin los túneles las pruebas
fallan con `SinConexion` y parece un problema de las reglas cuando no lo es.

Los túneles se caen al desconectar el cable; `adb reverse --list` los muestra.

Para correr una sola clase:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ai27.app.custodio.data.remote.RemotoEmuladorTest
```

(Nota: `connectedDebugAndroidTest` no acepta `--tests`; se filtra con esa propiedad.)

## Provisión de equipos y cuentas

### Prerrequisitos del carril A (consola Firebase, `custodio-906e7268`)

1. **A1 — Habilitar servicios:** Authentication (**phone sign-in**), Firestore en modo producción y Storage. El proyecto Firebase al que apunta la app lo define `app/google-services.json`. El SMS OTP exige el plan **Blaze** (Identity Platform): en Spark el emulador sirve para probar el flujo completo, pero los SMS reales no salen.
2. **A3 — Desplegar reglas de seguridad** (`docs/PLAN.md` §5.3):

   ```bash
   cd firebase && firebase deploy --only firestore,storage
   ```

   Se validan localmente antes con `npm test` (Emulator Suite, sin tocar la consola).
3. **A4 — Cargar el catálogo de etiquetas** (PLAN §3.1). El gancho remoto está en la app: `catalogos/etiquetas` → campo `valores` (`[{ id, nombre, categoria, orden, activo }]`, `categoria ∈ SITUACION|EVIDENCIA`); se cachea por la persistencia de Firestore y la app cae al catálogo empaquetado si la lectura falla o el doc está malformado. El seed idempotente de `provisionar.js` lo siembra con el vocabulario provisional:

   ```bash
   cd firebase/provision && node provisionar.js telefonos.csv
   ```

   **Sin esto no hay etiquetas que elegir al capturar** (los valores provisionales al final hay que sustituirlos por los reales derivados del tráfico del grupo, R4). Cambiar el doc no requiere recompilar ni publicar APK.

### Alta y baja de custodios (teléfono)

La identidad del custodio es su **número de teléfono verificado por SMS** (PHONE_AUTH_PLAN): sin correo ni PIN. Las altas/bajas son manuales por diseño (37 personas; `docs/PLAN.md` §2, E5).

- **Alta:** correr el aprovisionamiento (Admin SDK), idempotente — crea la cuenta de Authentication por teléfono y escribe la allowlist `config/telefonos.telefonos` (E.164, prefijo +52):

  ```bash
  export GOOGLE_APPLICATION_CREDENTIALS="$HOME/custodio-service-account.json"   # gitignored
  cd firebase/provision && node provisionar.js telefonos.csv
  ```

- **Baja:** Firestore → `config/telefonos.telefonos` → quitar el número, y revocar la cuenta en Authentication. Los documentos ya subidos no se tocan (inmutabilidad, §6.1).
- El **candado real** en producción es la función `beforeCreate` (`firebase/functions`): autoriza únicamente teléfonos de la allowlist. **Se despliega solo cuando exista el plan Blaze** (`cd firebase/functions && firebase deploy --only functions`). En Spark, la app igualmente cierra la sesión de quien no esté en la lista (verificación tras el OTP, opción B).
- En el **emulador** no hay SMS reales: el Auth emulator simula el envío y la verificación. El seed (`firebase/seed`) ya trae el custodio de demostración `+525544880512`.

### Alta de analistas (panel web, solo lectura)

El analista es quien tiene su **correo** en `config/analistas.correos` (Firestore). No hay custom claims: agregar un analista es añadir su correo a esa lista. El panel (otra app, separada) lee todo de Firestore y Storage; no escribe nada.

- **Producción:** Consola Firebase → Firestore → documento `config/analistas` → editar el arreglo `correos` y añadir el correo exacto (los correos se guardan en minúsculas). Aplica de inmediato, sin redesplegar reglas.
- **Emulador:** en la Emulator UI (`http://127.0.0.1:4000/firestore`) sobre `config/analistas.correos`, o por REST con el token de dueño:

  ```bash
  curl -X PATCH "http://127.0.0.1:8080/v1/projects/custodio-906e7268/databases/(default)/documents/config/analistas?updateMask.fieldPaths=correos" \
       -H "Authorization: Bearer owner" \
       -d '{"fields":{"correos":{"arrayValue":{"values":[{"stringValue":"ana@ai27.mx"}]}}}}'
  ```

- La cuenta del analista debe existir en Authentication con ESE correo (en producción se crea igual que un custodio). Si el correo no está en la lista, el panel no ve nada.

### Instalación en equipos (sideload)

1. Construir el APK firmado de release:

   ```bash
   ./gradlew assembleRelease
   # app/build/outputs/apk/release/app-release.apk
   ```

2. Copiarlo al equipo (cable, pendrive o App Distribution) e instalarlo. En Android la instalación requiere permitir "orígenes desconocidos" para el instalador usado; decidir con el responsable de los equipos si los dispositivos se gestionan (MDM) para centralizar la instalación y las actualizaciones.

3. Vehículo de distribución preferido para las actualizaciones: **App Distribution** (ver abajo).

### Firma de release — qué resguardar

La firma vive en `app/keystore/release.jks` con sus contraseñas en `keystore.properties`. **Ambos están gitignored a propósito** y son la pieza que permite actualizar instalaciones ya desplegadas sin desinstalar:

- **Respaldo obligatorio:** copiar `app/keystore/release.jks` y `keystore.properties` fuera del repo (gestor de secretos o caja fuerte) y anotar quién los custodia.
- Si se pierden, cualquier APK nuevo firmado con otra llave no podrá pisotear una instalación previa: habría que desinstalar y perder lo que haya en cola sin subir.
- No se suben al repo ni al CI mientras no exista un mecanismo seguro de secretos.

### App Distribution

Requisitos: `firebase login` con una cuenta con acceso al proyecto, la app registrada en la consola (Firebase → Project settings → Your apps → `mobilesdk_app_id`) y un **grupo de testers** creado. Repartir el APK a un grupo:

```bash
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app <MOBILE_SDK_APP_ID> \
  --groups <nombre-del-grupo> \
  --release-notes "Cambios de esta versión"
```

Flujo sugerido: grupo `testers` (verificación) primero y grupo `campo` (los 37 custodios) cuando el reporte de prueba esté aprobado. Las notas de versión van en español (México).

## Crashlytics

Integrado desde B7: plugin de Gradle aplicado y SDK inicializado en `AppCustodioApplication`. La recolección está explícitamente habilitada en el manifest. **El reporte real de crashes queda operativo cuando exista la consola (carril A)**; sin proyecto configurado, el SDK arranca en silencio —no registra arriba, pero tampoco rompe la app—.

## Verificación manual del ciclo completo

Con un equipo real y el carril A disponible:

1. Iniciar sesión, crear un viaje por placa y capturar foto/audio con GPS.
2. Cerrar el viaje y comprobar que aparece en Historial (y su detalle en solo lectura).
3. Descartar otro viaje con motivo y verificar que se sincroniza y que no sube evidencia nueva.
4. En modo avión: capturar, recuperar red y ver cómo la cola sube sola; reintentar manualmente una celda FALLIDA.
5. Derribar la app con una excepción y confirmar el crash en Crashlytics (cuando exista la consola).