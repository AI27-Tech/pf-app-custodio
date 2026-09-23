// Aprovisionamiento de custodios (PHONE_AUTH_PLAN Task 8) + catálogo (PLAN §3.1 / A4).
//
// 1. Lee `telefonos.csv` (E.164, prefijo +52) y:
//    a. Crea la cuenta de Authentication de cada teléfono (idempotente: si ya existe,
//       la ignora). Sin Identity Platform/plan Blaze la consola NO deja agregar usuarios
//       por teléfono; este script (Admin SDK) es el único camino de alta.
//    b. Escribe `config/telefonos.telefonos` con la lista, la allowlist que consulta la
//       app tras el OTP y la que valida la función `beforeCreate` (ver ../functions).
// 2. Escribe `catalogos/etiquetas.valores` con el vocabulario curado del catálogo (id,
//    nombre, categoria SITUACION|EVIDENCIA, orden, activo). Debe coincidir con
//    `CATALOGO_ETIQUETAS` de la app (el respaldo offline). Idempotente: sobrescribe.
//
// Uso:
//   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/custodio-service-account.json"
//   node provisionar.js [telefonos.csv]
//
// La cuenta de servicio necesita:
//   - Service Account Token Creator / Cloud Functions Invoker (no aplica aquí)
//   - Roles de Firebase: "Firebase Authentication Admin" y "Cloud Datastore User".
//
// NO se sube la credencial de servicio al repo (gitignored); se genera desde
// Consola Firebase -> Project settings -> Service accounts.

const fs = require('fs');
const admin = require('firebase-admin');

admin.initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT });

function leerTelefonos(ruta) {
  const lineas = fs.readFileSync(ruta, 'utf8').split(/\r?\n/);
  return lineas
    .filter((linea) => linea.trim() !== '' && !linea.trim().startsWith('#'))
    .map((linea) => linea.split(',')[0].trim())
    .filter((telefono) => /^\+52\d{10}$/.test(telefono));
}

async function main() {
  const rutaCsv = process.argv[2] || 'telefonos.csv';
  const telefonos = leerTelefonos(rutaCsv);
  if (telefonos.length === 0) {
    console.error('No hay teléfonos E.164 (+52 + 10 dígitos) en', rutaCsv);
    process.exit(1);
  }

  console.log(`Creando ${telefonos.length} cuentas de Authentication...`);
  const errores = [];
  for (const telefono of telefonos) {
    try {
      await admin.auth().createUser({ phoneNumber: telefono });
      console.log('  creada', telefono);
    } catch (e) {
      if (e.code === 'auth/phone-number-already-exists') {
        console.log('  ya existe', telefono);
      } else {
        errores.push(`${telefono}: ${e.message}`);
      }
    }
  }

  console.log('Escribiendo config/telefonos (allowlist)...');
  await admin.firestore().doc('config/telefonos').set({ telefonos });

  if (errores.length > 0) {
    console.error('Errores:');
    for (const error of errores) console.error('  ', error);
    process.exit(1);
  }
  console.log('Listo: cuentas creadas y allowlist actualizada.');
}

const CATALOGO = [
  { id: 'sin-novedad', nombre: 'Sin novedad', categoria: 'SITUACION', orden: 0, activo: true },
  { id: 'en-recorrido', nombre: 'En recorrido', categoria: 'SITUACION', orden: 1, activo: true },
  { id: 'detenido', nombre: 'Detenido', categoria: 'SITUACION', orden: 2, activo: true },
  { id: 'parada-bano', nombre: 'Parada por baño', categoria: 'SITUACION', orden: 3, activo: true },
  { id: 'con-novedad', nombre: 'Con novedad', categoria: 'SITUACION', orden: 4, activo: true },
  { id: 'estado-transporte', nombre: 'Estado del transporte', categoria: 'EVIDENCIA', orden: 0, activo: true },
  { id: 'falla-mecanica', nombre: 'Falla mecánica', categoria: 'EVIDENCIA', orden: 1, activo: true },
  { id: 'accidente', nombre: 'Accidente', categoria: 'EVIDENCIA', orden: 2, activo: true },
  { id: 'robo-asalto', nombre: 'Robo o asalto', categoria: 'EVIDENCIA', orden: 3, activo: true },
  { id: 'desvio-ruta', nombre: 'Desvío de ruta', categoria: 'EVIDENCIA', orden: 4, activo: true },
  { id: 'otro', nombre: 'Otro', categoria: 'EVIDENCIA', orden: 5, activo: true },
];

async function sembrarCatalogo() {
  console.log('Escribiendo catalogos/etiquetas (catálogo curado)...');
  // El Admin SDK serializa `orden` como int (no Long como el cliente Android): el parser
  // de la app acepta ambos (ver CatalogoEtiquetasDocumentoTest).
  await admin.firestore().doc('catalogos/etiquetas').set({ valores: CATALOGO });
  console.log('Catálogo listo.');
}

main()
  .then(sembrarCatalogo)
  .catch((e) => {
    console.error('Fallo al provisionar:', e);
    process.exit(1);
  });