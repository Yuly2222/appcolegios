/*
Node script to migrate base64 image fields in Firestore documents to Firebase Storage.

Usage:
  1) Install dependencies in your machine (outside Android project):
     npm install firebase-admin

  2) Create a service account JSON key in Firebase console and save it locally.

  3) Run:
     node migrate_base64_photos.js /path/to/serviceAccountKey.json your-project-id.appspot.com

This script will scan the collections: users, students, teachers.
For each document, if it finds fields named photoBase64, avatarBase64 or similar, it will upload the decoded bytes
to Storage under a folder per collection and update the document with the corresponding URL (photoUrl/avatarUrl).
Finally it will delete the original base64 field to avoid documents exceeding 1MB.

WARNING: Run first on a small sample and review results. Make backups if needed.
*/

const admin = require('firebase-admin');
const fs = require('fs');

if (process.argv.length < 4) {
  console.error('Usage: node migrate_base64_photos.js <serviceAccountKey.json> <storageBucket> [--dry-run] [collections comma separated]');
  process.exit(1);
}

const serviceAccountPath = process.argv[2];
const storageBucket = process.argv[3];
const dryRun = process.argv.includes('--dry-run');
const collectionsArg = process.argv.find(arg => arg && arg.includes(',') && !arg.startsWith('--'));
let collectionsToProcess = ['users','students','teachers'];
if (collectionsArg) {
  collectionsToProcess = collectionsArg.split(',').map(s => s.trim()).filter(s => s.length>0);
}

if (!fs.existsSync(serviceAccountPath)) {
  console.error('serviceAccountKey.json not found at', serviceAccountPath);
  process.exit(1);
}

const serviceAccount = require(serviceAccountPath);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  storageBucket: storageBucket
});

const db = admin.firestore();
const bucket = admin.storage().bucket();

async function processCollection(collectionName) {
  console.log('Scanning collection', collectionName);
  const snapshot = await db.collection(collectionName).get();
  console.log('Found', snapshot.size, 'docs in', collectionName);
  let migrated = 0;
  for (const doc of snapshot.docs) {
    const data = doc.data();
    const id = doc.id;
    const updates = {};
    let changed = false;

    // candidate fields
    const candidates = [
      { base: 'photoBase64', url: 'photoUrl' },
      { base: 'avatarBase64', url: 'avatarUrl' },
      { base: 'avatar', url: 'avatarUrl' },
      { base: 'photo', url: 'photoUrl' }
    ];

    for (const c of candidates) {
      const b = data[c.base];
      if (b && typeof b === 'string' && b.length > 200) { // heuristic: base64 are long
        console.log(`Doc ${collectionName}/${id} has ${c.base} (${b.length} chars)${dryRun? ' [dry-run]' : ' - migrating...'}`);
        if (dryRun) {
          // in dry-run mode, we only list candidates and skip upload/update
          changed = true; // mark as candidate
          continue;
        }
        // strip data URI prefix if present
        const base64Part = b.includes(',') ? b.substring(b.indexOf(',') + 1) : b;
        const buffer = Buffer.from(base64Part, 'base64');
        const filename = `${collectionName}/${id}/${c.url}_${Date.now()}.jpg`;
        const file = bucket.file(filename);
        try {
          await file.save(buffer, { resumable: false, contentType: 'image/jpeg', metadata: { firebaseStorageDownloadTokens: Date.now().toString() } });
          // construct a public URL (Note: you may prefer to generate a signed URL or set proper security rules)
          const publicUrl = `https://storage.googleapis.com/${bucket.name}/${filename}`;
          updates[c.url] = publicUrl;
          updates[c.base] = admin.firestore.FieldValue.delete();
          changed = true;
          console.log(`Uploaded to ${publicUrl}`);
        } catch (e) {
          console.error('Upload failed for', collectionName + '/' + id, e.message || e);
        }
      }
    }

    if (changed && !dryRun) {
      try {
        await db.collection(collectionName).doc(id).set(updates, { merge: true });
        migrated++;
        console.log(`Updated doc ${collectionName}/${id}`);
      } catch (e) {
        console.error('Failed to update doc', collectionName + '/' + id, e.message || e);
      }
    } else if (changed && dryRun) {
      // count as candidate but do not modify
      migrated++;
    }
  }
  console.log(`Collection ${collectionName}: ${dryRun? 'candidates found' : 'migrated'} ${migrated} documents.`);
}

(async () => {
  try {
    for (const c of collectionsToProcess) {
      await processCollection(c);
    }
    console.log('Migration finished');
    if (dryRun) console.log('Dry-run mode: no documents were modified.');
    process.exit(0);
  } catch (e) {
    console.error('Migration failed', e.message || e);
    process.exit(1);
  }
})();
