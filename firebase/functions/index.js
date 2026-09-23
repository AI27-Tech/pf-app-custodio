// Bloqueo de altas de teléfonos no autorizados (PHONE_AUTH_PLAN Task 8).
//
// En el plan Spark Firebase no permite configurar phone sign-in sin Identity Platform:
// el OTP por SMS de producción exige el plan Blaze. Esta función es el candado REAL de
// la allowlist en producción — SIN ella, cualquier teléfono que complete un OTP ampliaría
// la sesión como custodio, aunque la app le mostrara "no autorizado".
//
// La función recorre la MISMA lista que la app consulta después de autenticar
// (`config/telefonos.telefonos`, la escribe `firebase/provision/provisionar.js`), así
// que las altas del admin no tocan el code.
//
// DESPLIEGUE (solo cuando exista el plan Blaze):
//   cd firebase/functions && npm install && firebase deploy --only functions
//
// Aviso (2026-09-23): la migración a Identity Platform (GCIP) es IRREVERSIBLE y se
// dejó como decisión de producto. El proyecto sigue en `subtype: FIREBASE_AUTH`, así
// que aunque esta función esté desplegada en la nube NO se registra como trigger
// (la API responde "Blocking Functions may only be configured for GCIP projects")
// y por tanto NO se invoca. Registrarla después del upgrade:
//   PATCH .../admin/v2/projects/{project}/config?updateMask=blockingFunctions.triggers.beforeCreate
//   {"blockingFunctions":{"triggers":{"beforeCreate":{"functionUri":
//     "https://us-central1-<project>.cloudfunctions.net/bloquearTelefonoNoAutorizado"}}}}
//
// Con el proyecto aún en Spark esta carpeta NO se despliega: la app igualmente cierra
// la sesión de teléfonos que no estén en la allowlist (Task 4, opción B).
'use strict';

const { beforeUserCreated } = require('firebase-functions/v2/identity');
const { HttpsError } = require('firebase-functions/v2/https');
const { getFirestore } = require('firebase-admin/firestore');

exports.bloquearTelefonoNoAutorizado = beforeUserCreated(async (event) => {
  const telefono = event.data.phoneNumber;

  if (!telefono) {
    // El producto es 100% por teléfono: ni los analistas (consola/Admin SDK) se dan de
    // alta por correo una vez que esta puerta existe. Agregar un analista NO crea una
    // cuenta de la app, solo agrega su correo a `config/analistas.correos`.
    throw new HttpsError('invalid-argument', 'Solo se admiten cuentas con teléfono.');
  }

  const doc = await getFirestore().doc('config/telefonos').get();
  const autorizados = (doc.exists && Array.isArray(doc.data().telefonos))
    ? doc.data().telefonos
    : [];
  if (!autorizados.includes(telefono)) {
    // FirebaseAuth cancela la creación antes de que el custodio escriba nada; en el
    // cliente la verificación del OTP simplemente no llega a completarse.
    throw new HttpsError('permission-denied', 'Teléfono no autorizado: ' + telefono);
  }
});