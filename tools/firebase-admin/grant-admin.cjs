// Run only on your trusted computer. Never include a service-account key in the app.
const fs = require('node:fs');
const { initializeApp, cert, deleteApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');

async function main() {
  const [projectId, uid] = process.argv.slice(2);
  if (!projectId || !uid || process.argv.length !== 4) {
    throw new Error('Usage: node tools/firebase-admin/grant-admin.cjs PROJECT_ID USER_UID');
  }
  const path = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!path) throw new Error('Set GOOGLE_APPLICATION_CREDENTIALS to the service-account JSON path.');
  let credentials;
  try { credentials = JSON.parse(fs.readFileSync(path, 'utf8').replace(/^\uFEFF/, '')); }
  catch { throw new Error('Could not read the service-account JSON.'); }
  if (credentials.type !== 'service_account' || credentials.project_id !== projectId) {
    throw new Error('The service-account key does not belong to the specified project.');
  }
  const app = initializeApp({ credential: cert(credentials), projectId });
  try {
    const auth = getAuth(app);
    const user = await auth.getUser(uid);
    if (user.disabled) throw new Error('Enable this user in Firebase Authentication first.');
    await auth.setCustomUserClaims(uid, { ...user.customClaims, admin: true });
    const updated = await auth.getUser(uid);
    if (updated.customClaims?.admin !== true) throw new Error('Admin claim verification failed.');
    console.log(`Admin access verified for UID ${updated.uid} in project ${projectId}. Sign in again in the app.`);
  } finally {
    await deleteApp(app);
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
