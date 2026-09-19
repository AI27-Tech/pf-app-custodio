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

## Provisión de equipos y cuentas

### Prerrequisitos del carril A (consola Firebase, `custodio-906e7268`)

1. **A1 — Habilitar servicios:** Authentication (correo+contraseña), Firestore en modo producción y Storage. El proyecto Firebase al que apunta la app lo define `app/google-services.json`.
2. **A3 — Desplegar reglas de seguridad** (`docs/PLAN.md` §5.3):

   ```bash
   cd firebase && firebase deploy --only firestore,storage
   ```

   Se validan localmente antes con `npm test` (Emulator Suite, sin tocar la consola).
3. **A4 — Cargar el catálogo de etiquetas** en `catalogos/etiquetas` (el valor puede ser una lista provisional de trabajo; la app cachea el catálogo en el dispositivo). **Sin esto, no hay etiquetas que eligir al capturar.**

### Alta y baja de custodios (manual, el admin)

Las altas/bajas son manuales por diseño (37 personas; `docs/PLAN.md` §2, E5).

- **Alta:** Consola Firebase → Authentication → "Agregar usuario" → correo + contraseña temporal. Entregar las credenciales al custodio; la contraseña temporal se cambia en el primer inicio de sesión.
- **Baja:** Firestore → `usuarios/{uid}` → `activo: false` y revocar acceso en Authentication. Los documentos ya subidos no se tocan (inmutabilidad, `docs/PLAN.md` §6.1).
- El correo de la cuenta es el autor de cada archivo (`custodioUid`/`custodioCorreo`); debe ser el mismo correo que el custodio usa para iniciar sesión.

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