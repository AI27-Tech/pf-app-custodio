// Pruebas de las reglas de seguridad del PLAN §5.3 y §16 contra el Emulator Suite.
// Ejecutar desde firebase/:
//   npm run test   (== firebase emulators:exec --only firestore,storage --project demo-custodio "node test/reglas.test.js")
//
// Arnés (G2-0): `node:test` con un solo entorno y un `beforeEach` que limpia y
// resiembra Firestore y Storage. Cada caso parte de un estado conocido; el estado
// compartido de la suite antigua (un par de `assertSucceeds` encadenados) desaparece.

const fs = require('fs');
const path = require('path');
const { describe, it, before, beforeEach, after } = require('node:test');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { serverTimestamp } = require('firebase/firestore');

const PROJECT_ID = 'demo-custodio';

// ---- Fixtures del CONTRATO real (DocumentoMappers.kt) --------------------------
// El create del reporte recién nacido va con `mediosEsperados`, `closedAt`,
// `comentario` y `motivoDescarte` en null (campo null ES un valor de Firestore;
// `sinNulos()` solo filtra los updates). Lleva `createdAtServer` como sentinel.
// Regresión viva: si la app empezara a crear con otra forma, este fixture (y no las
// reglas) es lo que hay que cambiar.

function nuevoReporteA(overrides = {}) {
  return {
    placas: 'ABC-123',
    placasCapturadas: 'ABC-123',
    custodioUid: 'uid-a',
    custodioTelefono: '+525544880501',
    clienteId: 'cli-tmp',
    deviceId: 'dev-1',
    fechaLocal: '2026-09-18',
    createdAtDevice: new Date(),
    createdAtServer: serverTimestamp(),
    ciclo: 'ACTIVO',
    subida: 'PENDIENTE',
    completo: false,
    mediosEsperados: null,
    mediosSubidos: 0,
    resumenMedios: { fotos: 0, audios: 0, durationMsTotal: 0 },
    schemaVersion: 1,
    closedAt: null,
    comentario: null,
    hayNovedad: false,
    motivoDescarte: null,
    ...overrides,
  };
}

function nuevoMedioA(overrides = {}) {
  return {
    reporteId: 'r-a',
    tipo: 'FOTO',
    etiquetas: ['carga'],
    storagePath: 'medios/r-a/m-1.jpg',
    thumbnailPath: 'medios/r-a/m-1-min.jpg',
    createdAtDevice: new Date(),
    createdAtServer: serverTimestamp(),
    // Nulos reales de la app (Medio.aDocumentoFirestore): la foto no lleva gps cuando
    // el GPS falla, ni texto, ni comentario; durationMs solo aplica a AUDIO.
    gps: null,
    ubicacionTexto: null,
    comentario: null,
    contentType: 'image/jpeg',
    sizeBytes: 2048,
    durationMs: null,
    sha256: null,
    origen: 'CAMARA',
    placas: 'ABC-123',
    custodioUid: 'uid-a',
    clienteId: 'cli-tmp',
    fechaLocal: '2026-09-18',
    schemaVersion: 1,
    ...overrides,
  };
}

// ---- Sujeto de prueba -----------------------------------------------------------

let testEnv;
let alicia, bruno, analista, anonimo;
let dbA, dbB, dbAn, dbSinAuth;
let storageA, storageB, storageAn, storageSinAuth;

describe('Reglas de seguridad (PLAN §5.3 y §16)', () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8'),
      },
      storage: {
        rules: fs.readFileSync(path.join(__dirname, '..', 'storage.rules'), 'utf8'),
      },
    });

    alicia = testEnv.authenticatedContext('uid-a', { email: 'alicia@custodio.cl' });
    bruno = testEnv.authenticatedContext('uid-b', { email: 'bruno@custodio.cl' });
    analista = testEnv.authenticatedContext('uid-an', { email: 'ana@ai27.mx' });
    anonimo = testEnv.unauthenticatedContext();

    // firestore()/storage() se obtienen UNA vez por contexto (repetir la llamada
    // vuelve a aplicar useEmulator() sobre una instancia iniciada y lanza el error
    // "Firestore has already been started").
    dbA = alicia.firestore();
    dbB = bruno.firestore();
    dbAn = analista.firestore();
    dbSinAuth = anonimo.firestore();
    storageA = alicia.storage();
    storageB = bruno.storage();
    storageAn = analista.storage();
    storageSinAuth = anonimo.storage();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.clearStorage();
    // Estado base sin aplicar reglas: dos reportes ACTIVO (uno por custodio), uno
    // DESCARTADO, un medio de alicia, catálogos y configuración.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const dbBase = ctx.firestore();
      await dbBase.doc('reportes/r-a').set(nuevoReporteA());
      await dbBase.doc('reportes/r-b').set(
        nuevoReporteA({
          placas: 'ZZZ-999',
          custodioUid: 'uid-b',
          custodioTelefono: '+525544880502',
        }),
      );
      await dbBase.doc('reportes/r-d').set(
        nuevoReporteA({
          ciclo: 'DESCARTADO',
          closedAt: new Date(),
          motivoDescarte: 'sin evidencia',
        }),
      );
      await dbBase.doc('catalogos/c1').set({ etiquetas: ['carga', 'descarga', 'novedad'] });
      await dbBase.doc('medios/m-seed').set(nuevoMedioA());
      await dbBase.doc('usuarios/uid-a').set({ correo: 'alicia@custodio.cl', rol: 'custodio', activo: true });
      // La lista que autoriza al analista, ahora por CORREOS. En producción se crea a
      // mano en la consola (ver firestore.rules, match /config).
      await dbBase.doc('config/analistas').set({ correos: ['ana@ai27.mx'] });
      // Allowlist de teléfonos de custodios (PHONE_AUTH_PLAN Task 4, opción B): se lee
      // DESPUÉS de autenticar. Un custodio no autorizado es rechazado en la app.
      await dbBase.doc('config/telefonos').set({ telefonos: ['+525544880501', '+525544880502'] });
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  // ============ AUTENTICACIÓN ============

  it('sin auth no crea reporte', () => assertFails(dbSinAuth.doc('reportes/r-c').set(nuevoReporteA())));
  it('sin auth no lee reporte', () => assertFails(dbSinAuth.doc('reportes/r-a').get()));
  it('sin auth no lee catalogos', () => assertFails(dbSinAuth.doc('catalogos/c1').get()));

  // ============ CREACIÓN DE REPORTES ============

  it('custodio crea su reporte ACTIVO', () => assertSucceeds(dbA.doc('reportes/r-c').set(nuevoReporteA())));
  it('no se crea un reporte CERRADO directo', () =>
    assertFails(dbA.doc('reportes/r-c2').set(nuevoReporteA({ ciclo: 'CERRADO' }))));
  it('no se crea un reporte de otro custodio', () =>
    assertFails(dbA.doc('reportes/r-b').set(nuevoReporteA({ custodioUid: 'uid-b' }))));
  it('no se crea reporte sin custodioTelefono', () => {
    const { custodioTelefono: _sinTel, ...reporteSinTelefono } = nuevoReporteA();
    return assertFails(dbA.doc('reportes/r-sin-tel').set(reporteSinTelefono));
  });

  // S5: forma del documento
  it('S5 no se crea reporte con un campo extra (hasOnly)', () =>
    assertFails(dbA.doc('reportes/r-x').set(nuevoReporteA({ campa\u00f1a: 'x' }))));
  it('S5 no se crea reporte con contador no entero', () =>
    assertFails(dbA.doc('reportes/r-x').set(nuevoReporteA({ mediosSubidos: 'muchos' }))));
  it('S5 no se crea reporte con subida fuera del enum', () =>
    assertFails(dbA.doc('reportes/r-x').set(nuevoReporteA({ subida: 'VOLADA' }))));
  it('S5 no se crea reporte con mediosEsperados fuera de rango', () =>
    assertFails(dbA.doc('reportes/r-x').set(nuevoReporteA({ mediosEsperados: 51 }))));
  it('S5 no se crea reporte con resumenMedios de tipo incorrecto', () =>
    assertFails(dbA.doc('reportes/r-x').set(nuevoReporteA({ resumenMedios: { fotos: '0' } }))));

  // ============ LECTURA ENTRE CUSTODIOS ============

  it('alicia lee su reporte', () => assertSucceeds(dbA.doc('reportes/r-a').get()));
  it('alicia no lee el reporte de bruno', () => assertFails(dbA.doc('reportes/r-b').get()));
  it('bruno no lee el reporte de alicia', () => assertFails(dbB.doc('reportes/r-a').get()));
  it('get de reporte inexistente no falla', () => assertSucceeds(dbA.doc('reportes/inexistente').get()));
  it('cualquier custodio lee catalogos', () => assertSucceeds(dbA.doc('catalogos/c1').get()));
  it('sin auth no escribe catalogos', () => assertFails(dbSinAuth.doc('catalogos/c1').set({ etiquetas: [] })));

  // ============ TRANSICIONES DE CICLO (S7) Y CONTADORES (S6) ============

  it('ACTIVO -> CERRADO por due\u00f1o', () =>
    assertSucceeds(dbA.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date() })));
  it('ACTIVO -> CERRADO con contadores y novedad (paso real de la app)', () =>
    assertSucceeds(
      dbA.doc('reportes/r-a').update({
        ciclo: 'CERRADO',
        closedAt: new Date(),
        comentario: 'sin novedades',
        hayNovedad: false,
        mediosEsperados: 1,
        resumenMedios: { fotos: 1, audios: 0, durationMsTotal: 0 },
        subida: 'COMPLETA',
        mediosSubidos: 1,
        completo: true,
      }),
    ));
  it('ACTIVO -> CERRADO sin lograr completar los subidos', () =>
    assertSucceeds(
      dbA.doc('reportes/r-a').update({
        ciclo: 'CERRADO',
        closedAt: new Date(),
        mediosEsperados: 1,
        subida: 'EN_PROGRESO',
        mediosSubidos: 0,
        completo: false,
      }),
    ));
  it('S6 ACTIVO -> CERRADO con subidos por encima de lo esperado', () =>
    assertFails(
      dbA.doc('reportes/r-a').update({
        ciclo: 'CERRADO',
        closedAt: new Date(),
        mediosEsperados: 1,
        subida: 'COMPLETA',
        mediosSubidos: 2,
        completo: true,
      }),
    ));
  it('S6 ACTIVO -> CERRADO mintiendo en completo (0 subidos, dice completo)', () =>
    assertFails(
      dbA.doc('reportes/r-a').update({
        ciclo: 'CERRADO',
        closedAt: new Date(),
        mediosEsperados: 1,
        subida: 'COMPLETA',
        mediosSubidos: 0,
        completo: true,
      }),
    ));
  it('S6 ACTIVO -> CERRADO con subida fuera del enum', () =>
    assertFails(
      dbA.doc('reportes/r-a').update({
        ciclo: 'CERRADO',
        closedAt: new Date(),
        mediosEsperados: 1,
        subida: 'VOLADA',
        mediosSubidos: 0,
        completo: false,
      }),
    ));
  it('S7 ACTIVO -> CERRADO cambiando placas', () =>
    assertFails(dbA.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date(), placas: 'OTRO-000' })));
  it('S7 ACTIVO -> CERRADO por otro custodio', () =>
    assertFails(dbB.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date() })));
  it('ACTIVO -> DESCARTADO por due\u00f1o', () =>
    assertSucceeds(
      dbB.doc('reportes/r-b').update({ ciclo: 'DESCARTADO', motivoDescarte: 'sin evidencia', closedAt: new Date() }),
    ));
  it('S7 ACTIVO -> DESCARTADO sin motivo', () =>
    assertFails(dbA.doc('reportes/r-a').update({ ciclo: 'DESCARTADO', closedAt: new Date() })));
  it('S7 ACTIVO -> DESCARTADO con un campo extra', () =>
    assertFails(
      dbA.doc('reportes/r-a').update({ ciclo: 'DESCARTADO', motivoDescarte: 'x', closedAt: new Date(), comentario: 'audit' }),
    ));
  it('CERRADO solo avanza contadores', async () => {
    await assertSucceeds(
      dbA.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date(), mediosEsperados: 1 }),
    );
    await assertSucceeds(dbA.doc('reportes/r-a').update({ subida: 'COMPLETA', mediosSubidos: 1, completo: true }));
  });
  it('S7 CERRADO no edita placas', async () => {
    await assertSucceeds(dbA.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date() }));
    await assertFails(dbA.doc('reportes/r-a').update({ placas: 'OTRO-000', ciclo: 'CERRADO' }));
  });
  it('S7 CERRADO no se reabre a ACTIVO', async () => {
    await assertSucceeds(dbA.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date() }));
    await assertFails(dbA.doc('reportes/r-a').update({ ciclo: 'ACTIVO', subida: 'PENDIENTE' }));
  });
  it('S7 CERRADO no agrega comentario (solo contadores)', async () => {
    await assertSucceeds(dbA.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date() }));
    await assertFails(dbA.doc('reportes/r-a').update({ comentario: 'edicion' }));
  });
  it('S6 CERRADO no retrocede los subidos', async () => {
    await assertSucceeds(
      dbA.doc('reportes/r-a').update({
        ciclo: 'CERRADO',
        closedAt: new Date(),
        mediosEsperados: 1,
        subida: 'COMPLETA',
        mediosSubidos: 1,
        completo: true,
      }),
    );
    await assertFails(dbA.doc('reportes/r-a').update({ subida: 'EN_PROGRESO', mediosSubidos: 0, completo: false }));
  });
  it('DESCARTADO es inmutable', () =>
    assertFails(dbA.doc('reportes/r-d').update({ motivoDescarte: 'otro' })));
  it('S7 DESCARTADO no se reabre a ACTIVO', () =>
    assertFails(dbA.doc('reportes/r-d').update({ ciclo: 'ACTIVO', subida: 'PENDIENTE' })));

  // ============ BORRADO ============

  it('no se borra un reporte', () => assertFails(dbA.doc('reportes/r-a').delete()));

  // ============ MEDIOS ============

  it('due\u00f1o crea un medio sobre su reporte', () => assertSucceeds(dbA.doc('medios/m-1').set(nuevoMedioA())));
  it('due\u00f1o crea un medio de AUDIO (sin miniatura, con gps/durationMs nulos)', () =>
    assertSucceeds(dbA.doc('medios/m-audio').set(nuevoMedioA({
      tipo: 'AUDIO',
      storagePath: 'medios/r-a/m-audio.m4a',
      thumbnailPath: null,
      contentType: 'audio/mp4',
      sizeBytes: 51200,
      durationMs: 5210,
      origen: 'GRABADORA',
    }))));
  it('no se crea un medio sobre reporte ajeno', () =>
    assertFails(dbB.doc('medios/m-x').set(nuevoMedioA({ custodioUid: 'uid-b' }))));
  it('no se crea un medio cuyo reporte no existe', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ reporteId: 'r-inexistente' }))));
  it('no se crea un medio sobre un reporte DESCARTADO', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ reporteId: 'r-d' }))));
  it('S5 no se crea un medio con etiquetas de tipo incorrecto', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ etiquetas: 'carga' }))));
  it('S5 no se crea un medio con storagePath de otro reporte', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ storagePath: 'medios/r-otro/m.jpg' }))));
  it('S5 no se crea un medio con storagePath fuera de la carpeta media', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ storagePath: 'medios/r-a/../m.jpg' }))));
  it('S5 no se crea un medio con un campo extra (hasOnly)', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ firma: 'x' }))));
  it('S5 no se crea un medio con origen fuera del enum', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ origen: 'CELULAR' }))));
  it('S5 no se crea un medio con gps de tipo incorrecto', () =>
    assertFails(dbA.doc('medios/m-x').set(nuevoMedioA({ gps: { lat: 'norte' } }))));
  it('no se actualiza un medio', async () => {
    await assertSucceeds(dbA.doc('medios/m-1').set(nuevoMedioA()));
    await assertFails(dbA.doc('medios/m-1').set({ ...nuevoMedioA(), etiquetas: ['novedad'] }));
  });
  it('no se borra un medio', () => assertFails(dbA.doc('medios/m-1').delete()));
  it('due\u00f1o lee su medio', () => assertSucceeds(dbA.doc('medios/m-1').get()));
  it('ajeno no lee el medio', async () => {
    await assertSucceeds(dbA.doc('medios/m-1').set(nuevoMedioA()));
    await assertFails(dbB.doc('medios/m-1').get());
  });

  // ============ EL ANALISTA DEL PANEL (PLAN §5.3) ============
  // Lee todo, escribe nada. La autorización sale de config/analistas.correos y no
  // depende del documento consultado, que es lo que permite las consultas de lista.
  // Las escrituras de custodio no consultan esta lista (subir evidencia no depende
  // de la lista); el analista queda igualmente sin escribir por dueño/nada-borra.

  it('analista lee el reporte de alicia', () => assertSucceeds(dbAn.doc('reportes/r-a').get()));
  it('analista lee el reporte de bruno', () => assertSucceeds(dbAn.doc('reportes/r-b').get()));
  it('analista lee un medio ajeno', () => assertSucceeds(dbAn.doc('medios/m-seed').get()));
  it('analista lee catalogos', () => assertSucceeds(dbAn.doc('catalogos/c1').get()));
  it('analista lee el directorio de usuarios', () => assertSucceeds(dbAn.doc('usuarios/uid-a').get()));

  // Consultas de lista: es aquí donde una regla mal escrita rompe el panel entero.
  // El analista consulta SIN filtro de custodio y debe pasar; el custodio no.
  it('analista consulta la coleccion de reportes sin filtro', () =>
    assertSucceeds(dbAn.collection('reportes').get()));
  it('analista consulta la coleccion de medios sin filtro', () => assertSucceeds(dbAn.collection('medios').get()));
  it('custodio no consulta reportes sin filtrar por el suyo', () =>
    assertFails(dbA.collection('reportes').get()));
  it('custodio si consulta reportes filtrando por el suyo', () =>
    assertSucceeds(dbA.collection('reportes').where('custodioUid', '==', 'uid-a').get()));

  // Escritura: negada en todo (por dueño en lo escrito, por `delete: if false`)
  it('analista no inyecta un reporte de otra persona', () =>
    assertFails(dbAn.doc('reportes/r-inyectado').set(nuevoReporteA())));
  it('analista no actualiza reporte', () =>
    assertFails(dbAn.doc('reportes/r-a').update({ comentario: 'editado por el analista' })));
  it('analista no borra reporte', () => assertFails(dbAn.doc('reportes/r-a').delete()));
  it('analista no crea medio', () => assertFails(dbAn.doc('medios/m-an').set(nuevoMedioA({ custodioUid: 'uid-an' }))));
  it('analista no actualiza medio', () => assertFails(dbAn.doc('medios/m-seed').update({ etiquetas: ['otra'] })));
  it('analista no borra medio', () => assertFails(dbAn.doc('medios/m-seed').delete()));
  it('analista no escribe catalogos', () => assertFails(dbAn.doc('catalogos/c1').set({ etiquetas: [] })));

  // La lista de autorización no la toca ningún cliente, ni el analista
  it('analista no lee config/analistas', () => assertFails(dbAn.doc('config/analistas').get()));
  it('analista no se agrega a config/analistas', () =>
    assertFails(dbAn.doc('config/analistas').update({ uids: ['uid-an', 'uid-a'] })));
  it('custodio no lee config/analistas', () => assertFails(dbA.doc('config/analistas').get()));
  it('custodio no se agrega a config/analistas', () =>
    assertFails(dbA.doc('config/analistas').set({ uids: ['uid-a'] })));

  // Allowlist de teléfonos (PHONE_AUTH_PLAN Task 4, opción B): cualquier autenticado
  // lee `config/telefonos` para verificar su número tras el OTP; nadie la escribe.
  it('custodio lee config/telefonos', () => assertSucceeds(dbA.doc('config/telefonos').get()));
  it('sin auth no lee config/telefonos', () => assertFails(dbSinAuth.doc('config/telefonos').get()));
  it('custodio no modifica config/telefonos', () =>
    assertFails(dbA.doc('config/telefonos').update({ telefonos: ['+525544880503'] })));
  it('analista no modifica config/telefonos', () =>
    assertFails(dbAn.doc('config/telefonos').update({ telefonos: [] })));

  // Regresión: estar autenticado no basta. Un custodio que no está en la lista sigue
  // sin ver lo ajeno aunque la regla del analista exista.
  it('bruno no lee el medio de alicia por la puerta del analista', () =>
    assertFails(dbB.doc('medios/m-seed').get()));

  // ============ REGLAS STORAGE ============
  // Cada caso usa un NOMBRE de archivo propio: el arnés no puede confiar en que
  // `clearStorage()` deje el bucket vacío entre tests (el emulador lo ignora a
  // veces), así que la suite es independiente del orden y de los restos.

  const bytes = Buffer.from('foto-de-prueba');
  const subirFoto = (storage, nombre) => (storage || storageA).ref('medios/r-a').child(nombre).put(bytes, { contentType: 'image/jpeg' });

  it('due\u00f1o sube archivo de su reporte', () => assertSucceeds(subirFoto(storageA, 'm-2.jpg')));
  it('due\u00f1o sube una nota de voz', () =>
    assertSucceeds(storageA.ref('medios/r-a').child('n-1.m4a').put(bytes, { contentType: 'audio/mp4' })));
  it('S4 el reintento sobre una ruta ya existente se niega (create-only)', async () => {
    await assertSucceeds(subirFoto(storageA, 's4.jpg'));
    await assertFails(subirFoto(storageA, 's4.jpg'));
  });
  it('S3 se rechaza un contentType no permitido', () =>
    assertFails(storageA.ref('medios/r-a').child('doc.html').put(bytes, { contentType: 'text/html' })));
  it('S3 se rechaza una imagen por encima del tope (12 MB + 1)', () =>
    assertFails(storageA.ref('medios/r-a').child('grande.jpg').put(Buffer.alloc(12 * 1024 * 1024 + 1), { contentType: 'image/jpeg' })));
  it('S3 se rechaza un audio por encima del tope (25 MB + 1)', () =>
    assertFails(storageA.ref('medios/r-a').child('larga.m4a').put(Buffer.alloc(25 * 1024 * 1024 + 1), { contentType: 'audio/mp4' })));
  it('no sube a un reporte de otro custodio', () =>
    assertFails(subirFoto(storageB, 'm-3.jpg')));
  it('sin auth no sube', () =>
    assertFails(subirFoto(storageSinAuth, 'm-4.jpg')));
  it('due\u00f1o no borra', async () => {
    await assertSucceeds(subirFoto(storageA, 'nb.jpg'));
    await assertFails(storageA.ref('medios/r-a').child('nb.jpg').delete());
  });
  it('ajeno no lee', async () => {
    await assertSucceeds(subirFoto(storageA, 'nl.jpg'));
    await assertFails(storageB.ref('medios/r-a').child('nl.jpg').getDownloadURL());
  });

  // El panel del analista visualiza la evidencia: lee, no escribe.
  it('analista lee el archivo del reporte de alicia', async () => {
    await assertSucceeds(subirFoto(storageA, 'al.jpg'));
    await assertSucceeds(storageAn.ref('medios/r-a').child('al.jpg').getDownloadURL());
  });
  it('analista no sube a storage', () =>
    assertFails(subirFoto(storageAn, 'm-x.jpg')));
});