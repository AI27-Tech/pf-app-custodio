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
  const analista = testEnv.authenticatedContext('uid-an', { email: 'ana@ai27.mx' });
  const anonimo = testEnv.unauthenticatedContext();

  // Nota: firestore()/storage() se obtienen UNA vez por contexto (repetir la llamada
  // vuelve a aplicar useEmulator() sobre una instancia iniciada y lanza el error
  // "Firestore has already been started").
  const dbA = alicia.firestore();
  const dbB = bruno.firestore();
  const dbAn = analista.firestore();
  const dbSinAuth = anonimo.firestore();
  const storageA = alicia.storage();
  const storageB = bruno.storage();
  const storageAn = analista.storage();
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
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const dbBase = ctx.firestore();
    await dbBase.doc('reportes/r-b').set(reporteB);
    await dbBase.doc('reportes/r-a').set(reporteA);
    await dbBase.doc('catalogos/c1').set({ etiquetas: ['carga', 'descarga', 'novedad'] });
    await dbBase.doc('medios/m-seed').set(medioA);
    await dbBase.doc('usuarios/uid-a').set({ correo: 'alicia@custodio.cl', rol: 'custodio', activo: true });
    // La lista que autoriza al analista, ahora por CORREOS. En producción se crea a
    // mano en la consola (ver firestore.rules, match /config).
    await dbBase.doc('config/analistas').set({ correos: ['ana@ai27.mx'] });
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

  // ============ EL ANALISTA DEL PANEL (PLAN §5.3) ============
  // Lee todo, escribe nada. La autorización sale de config/analistas.correos y no
  // depende del documento consultado, que es lo que permite las consultas de lista.
  // Las escrituras de custodio no consultan esta lista (subir evidencia no depende
  // de la lista); el analista queda igualmente sin escribir por dueño/nada-borra.

  // Lectura de documentos ajenos
  await comprobar('analista lee el reporte de alicia', assertSucceeds(dbAn.doc('reportes/r-a').get()));
  await comprobar('analista lee el reporte de bruno', assertSucceeds(dbAn.doc('reportes/r-b').get()));
  await comprobar('analista lee un medio ajeno', assertSucceeds(dbAn.doc('medios/m-seed').get()));
  await comprobar('analista lee catalogos', assertSucceeds(dbAn.doc('catalogos/c1').get()));
  await comprobar('analista lee el directorio de usuarios', assertSucceeds(dbAn.doc('usuarios/uid-a').get()));

  // Consultas de lista: es aquí donde una regla mal escrita rompe el panel entero.
  // El analista consulta SIN filtro de custodio y debe pasar; el custodio no.
  await comprobar('analista consulta la coleccion de reportes sin filtro', assertSucceeds(dbAn.collection('reportes').get()));
  await comprobar('analista consulta la coleccion de medios sin filtro', assertSucceeds(dbAn.collection('medios').get()));
  await comprobar('custodio no consulta reportes sin filtrar por el suyo', assertFails(dbA.collection('reportes').get()));
  await comprobar('custodio si consulta reportes filtrando por el suyo', assertSucceeds(dbA.collection('reportes').where('custodioUid', '==', 'uid-a').get()));

  // Escritura: negada en todo (por dueño en lo escrito, por `delete: if false`)
  await comprobar('analista no inyecta un reporte de otra persona', assertFails(dbAn.doc('reportes/r-inyectado').set(reporteA)));
  await comprobar('analista no actualiza reporte', assertFails(dbAn.doc('reportes/r-a').update({ comentario: 'editado por el analista' })));
  await comprobar('analista no borra reporte', assertFails(dbAn.doc('reportes/r-a').delete()));
  await comprobar('analista no crea medio', assertFails(dbAn.doc('medios/m-an').set({ ...medioA, custodioUid: 'uid-an' })));
  await comprobar('analista no actualiza medio', assertFails(dbAn.doc('medios/m-seed').update({ etiqueta: 'otra' })));
  await comprobar('analista no borra medio', assertFails(dbAn.doc('medios/m-seed').delete()));
  await comprobar('analista no escribe catalogos', assertFails(dbAn.doc('catalogos/c1').set({ etiquetas: [] })));

  // La lista de autorización no la toca ningún cliente, ni el analista
  await comprobar('analista no lee config/analistas', assertFails(dbAn.doc('config/analistas').get()));
  await comprobar('analista no se agrega a config/analistas', assertFails(dbAn.doc('config/analistas').update({ uids: ['uid-an', 'uid-a'] })));
  await comprobar('custodio no lee config/analistas', assertFails(dbA.doc('config/analistas').get()));
  await comprobar('custodio no se agrega a config/analistas', assertFails(dbA.doc('config/analistas').set({ uids: ['uid-a'] })));

  // Regresión: estar autenticado no basta. Un custodio que no está en la lista sigue
  // sin ver lo ajeno aunque la regla del analista exista.
  await comprobar('bruno no lee el medio de alicia por la puerta del analista', assertFails(dbB.doc('medios/m-seed').get()));

  // ============ REGLAS STORAGE ============
  const bytes = Buffer.from('foto-de-prueba');

  await comprobar('dueño sube archivo de su reporte', assertSucceeds(storageA.ref('medios/r-a').child('m-2.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('dueño sobrescribe en reintento (update)', assertSucceeds(storageA.ref('medios/r-a').child('m-2.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('no sube a un reporte de otro custodio', assertFails(storageB.ref('medios/r-a').child('m-3.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('sin auth no sube', assertFails(storageSinAuth.ref('medios/r-a').child('m-4.jpg').put(bytes, { contentType: 'image/jpeg' })));
  await comprobar('dueño no borra', assertFails(storageA.ref('medios/r-a').child('m-2.jpg').delete()));
  await comprobar('ajeno no lee', assertFails(storageB.ref('medios/r-a').child('m-2.jpg').getDownloadURL()));

  // El panel del analista visualiza la evidencia: lee, no escribe.
  await comprobar('analista lee el archivo del reporte de alicia', assertSucceeds(storageAn.ref('medios/r-a').child('m-2.jpg').getDownloadURL()));
  await comprobar('analista no sube a storage', assertFails(storageAn.ref('medios/r-a').child('m-x.jpg').put(bytes, { contentType: 'image/jpeg' })));

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