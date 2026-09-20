// Pruebas de las reglas de seguridad del PLAN §5.3 contra el Emulator Suite.
// Ejecutar desde firebase/:
//   npm run test   (== firebase emulators:exec --only firestore,storage --project demo-custodio "node test/reglas.test.js")

const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

const PROJECT_ID = 'demo-custodio';

async function main() {
  const testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8'),
    },
    storage: {
      rules: fs.readFileSync(path.join(__dirname, '..', 'storage.rules'), 'utf8'),
    },
  });

  const alicia = testEnv.authenticatedContext('uid-a', { email: 'alicia@custodio.cl' });
  const bruno = testEnv.authenticatedContext('uid-b', { email: 'bruno@custodio.cl' });
  const anonimo = testEnv.unauthenticatedContext();

  // Nota: firestore()/storage() se obtienen UNA vez por contexto (repetir la llamada
  // vuelve a aplicar useEmulator() sobre una instancia iniciada y lanza el error
  // "Firestore has already been started").
  const dbA = alicia.firestore();
  const dbB = bruno.firestore();
  const dbSinAuth = anonimo.firestore();
  const storageA = alicia.storage();
  const storageB = bruno.storage();
  const storageSinAuth = anonimo.storage();

  const reporteA = {
    placas: 'ABC-123',
    placasCapturadas: 'ABC-123',
    custodioUid: 'uid-a',
    custodioCorreo: 'alicia@custodio.cl',
    clienteId: 'cli-tmp',
    deviceId: 'dev-1',
    fechaLocal: '2026-09-18',
    createdAtDevice: new Date(),
    ciclo: 'ACTIVO',
    subida: 'PENDIENTE',
    completo: false,
    mediosEsperados: 1,
    mediosSubidos: 0,
    resumenMedios: { fotos: 0, audios: 0, durationMsTotal: 0 },
    schemaVersion: 1,
  };

  const reporteB = { ...reporteA, custodioUid: 'uid-b', custodioCorreo: 'bruno@custodio.cl', placas: 'ZZZ-999' };
  const medioA = {
    reporteId: 'r-a',
    tipo: 'FOTO',
    etiqueta: 'carga',
    storagePath: 'medios/r-a/m-1.jpg',
    thumbnailPath: 'medios/r-a/m-1-min.jpg',
    createdAtDevice: new Date(),
    contentType: 'image/jpeg',
    origen: 'CAMARA',
    placas: 'ABC-123',
    custodioUid: 'uid-a',
    clienteId: 'cli-tmp',
    fechaLocal: '2026-09-18',
    schemaVersion: 1,
  };

  let fallos = 0;
  function comprobar(nombre, promesa) {
    return promesa.then(
      () => console.log(`ok    ${nombre}`),
      (err) => {
        fallos++;
        console.error(`FAIL  ${nombre}: ${err.message}`);
      },
    );
  }

  // ---- Datos de referencia sin reglas (el reporte de Alicia ya cerrado, por ejemplo) ----
  const semilla = await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const dbBase = ctx.firestore();
    await dbBase.doc('reportes/r-b').set(reporteB);
    await dbBase.doc('reportes/r-a').set(reporteA);
    await dbBase.doc('catalogos/c1').set({ etiquetas: ['carga', 'descarga', 'novedad'] });
  });

  // ============ REGLAS FIRESTORE ============

  // Sin autenticación
  await comprobar('sin auth no crea reporte', assertFails(dbSinAuth.doc('reportes/r-c').set(reporteA)));
  await comprobar('sin auth no lee reporte', assertFails(dbSinAuth.doc('reportes/r-a').get()));
  await comprobar('sin auth no lee catalogos', assertFails(dbSinAuth.doc('catalogos/c1').get()));

  // Creación de reportes
  await comprobar('custodio crea su reporte ACTIVO', assertSucceeds(dbA.doc('reportes/r-c').set(reporteA)));
  await comprobar('no se crea un reporte CERRADO directo', assertFails(dbA.doc('reportes/r-c2').set({ ...reporteA, ciclo: 'CERRADO' })));
  await comprobar('no se crea un reporte de otro custodio', assertFails(dbA.doc('reportes/r-b').set(reporteB)));

  // Lectura entre custodios
  await comprobar('alicia lee su reporte', assertSucceeds(dbA.doc('reportes/r-a').get()));
  await comprobar('alicia no lee el reporte de bruno', assertFails(dbA.doc('reportes/r-b').get()));
  await comprobar('bruno no lee el reporte de alicia', assertFails(dbB.doc('reportes/r-a').get()));
  await comprobar('get de reporte inexistente no falla', assertSucceeds(dbA.doc('reportes/inexistente').get()));
  await comprobar('cualquier custodio lee catalogos', assertSucceeds(dbA.doc('catalogos/c1').get()));
  await comprobar('sin auth no escribe catalogos', assertFails(dbSinAuth.doc('catalogos/c1').set({ etiquetas: [] })));

  // Transiciones de ciclo
  await comprobar('ACTIVO -> CERRADO por dueño', assertSucceeds(dbA.doc('reportes/r-a').update({ ciclo: 'CERRADO', closedAt: new Date() })));
  await comprobar('ACTIVO -> DESCARTADO por dueño', assertSucceeds(dbB.doc('reportes/r-b').update({ ciclo: 'DESCARTADO', motivoDescarte: 'sin evidencia', closedAt: new Date() })));
  await comprobar('ACTIVO -> CERRADO por otro custodio', assertFails(dbB.doc('reportes/r-a').update({ ciclo: 'CERRADO' })));
  await comprobar('CERRADO solo avanza contadores', assertSucceeds(dbA.doc('reportes/r-a').update({ subida: 'COMPLETA', mediosSubidos: 1, completo: true })));
  await comprobar('CERRADO no edita placas', assertFails(dbA.doc('reportes/r-a').update({ placas: 'OTRO-000', ciclo: 'CERRADO' })));
  await comprobar('DESCARTADO es inmutable', assertFails(dbB.doc('reportes/r-b').update({ motivoDescarte: 'otro', ciclo: 'DESCARTADO' })));

  // Borrado
  await comprobar('no se borra un reporte', assertFails(dbA.doc('reportes/r-a').delete()));

  // Medios
  await comprobar('dueño crea un medio sobre su reporte', assertSucceeds(dbA.doc('medios/m-1').set(medioA)));
  await comprobar('no se crea un medio sobre reporte ajeno', assertFails(dbB.doc('medios/m-x').set({ ...medioA, custodioUid: 'uid-b' })));
  await comprobar('no se actualiza un medio', assertFails(dbA.doc('medios/m-1').set({ ...medioA, etiqueta: 'novedad' })));
  await comprobar('no se borra un medio', assertFails(dbA.doc('medios/m-1').delete()));
  await comprobar('dueño lee su medio', assertSucceeds(dbA.doc('medios/m-1').get()));
  await comprobar('ajeno no lee el medio', assertFails(dbB.doc('medios/m-1').get()));

  // ============ REGLAS STORAGE ============
  const bytes = Buffer.from('foto-de-prueba');

  await comprobar('dueño sube archivo de su reporte', assertSucceeds(storageA.ref('medios/r-a').child('m-2.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('dueño sobrescribe en reintento (update)', assertSucceeds(storageA.ref('medios/r-a').child('m-2.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('no sube a un reporte de otro custodio', assertFails(storageB.ref('medios/r-a').child('m-3.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('sin auth no sube', assertFails(storageSinAuth.ref('medios/r-a').child('m-4.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('dueño no borra', assertFails(storageA.ref('medios/r-a').child('m-2.jpg').delete()));
  await comprobar('ajeno no lee', assertFails(storageB.ref('medios/r-a').child('m-2.jpg').getDownloadURL()));

  await testEnv.clearFirestore();
  await testEnv.clearStorage();
  await testEnv.cleanup();

  if (fallos > 0) {
    console.error(`\n${fallos} reglas fallaron.`);
    process.exit(1);
  }
  console.log('\nTodas las reglas pasaron.');
}

main().catch((e) => {
  console.error('Fallo al inicializar el entorno:', e);
  process.exit(1);
});