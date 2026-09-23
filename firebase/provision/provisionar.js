// Aprovisionamiento de custodios (PHONE_AUTH_PLAN Task 8).
//
// Lee `telefonos.csv` (E.164, prefijo +52) y:
//   1. Crea la cuenta de Authentication de cada teléfono (idempotente: si ya existe,
//      la ignora). Sin Identity Platform/plan Blaze la consola NO deja agregar usuarios
//      por teléfono; este script (Admin SDK) es el único camino de alta.
//   2. Escribe `config/telefonos.telefonos` con la lista, la allowlist que consulta la
//      app tras el OTP y la que valida la función `beforeCreate` (ver ../functions).
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

main().catch((e) => {
  console.error('Fallo al provisionar:', e);
  process.exit(1);
});