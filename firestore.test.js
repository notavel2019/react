const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const crypto = require("node:crypto");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "owner_admin_uid";
const ADMIN_EMAIL = "lojadolar@gmail.com";
const ALICE_UID = "alice_uid_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_uid_456";
const BOB_EMAIL = "bob@example.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function emailToDocId(emailLower) {
  return crypto.createHash("sha256").update(emailLower, "utf8").digest("hex");
}

function adminDb() {
  return testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function userDb(uid, email, emailVerified = true) {
  return testEnv
    .authenticatedContext(uid, {
      email,
      email_verified: emailVerified,
    })
    .firestore();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied all reads and writes on access_controls", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const docId = emailToDocId(ALICE_EMAIL);
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("access_controls").get());
  await assertFails(
    unauthDb
      .collection("access_controls")
      .where("emailLower", "==", ALICE_EMAIL)
      .limit(1)
      .get()
  );
  await assertFails(
    unauthDb.collection("access_controls").doc(docId).set({
      emailLower: ALICE_EMAIL,
      status: "ALLOWED",
      note: "Unauth attempt",
      updatedByUid: "anon",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Red Team (Email Spoofing): unverified admin email is denied all operations", async () => {
  const spoofedAdminDb = userDb("attacker_uid", ADMIN_EMAIL, false);
  const docId = emailToDocId(ALICE_EMAIL);
  const now = new Date(Date.now() - 1000);

  await assertFails(spoofedAdminDb.collection("access_controls").get());
  await assertFails(
    spoofedAdminDb.collection("access_controls").doc(docId).set({
      emailLower: ALICE_EMAIL,
      status: "ALLOWED",
      note: "Spoofed admin",
      updatedByUid: "attacker_uid",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Verified Owner Admin: Kotlin-mirrored create, list, partial update, and delete succeed", async () => {
  const db = adminDb();
  const docId = emailToDocId(ALICE_EMAIL);
  const created = new Date(Date.now() - 10000);

  // Mirror AccessControlRepository.upsertAccessControl (create path, NO 'id' field)
  await assertSucceeds(
    db.collection("access_controls").doc(docId).set({
      emailLower: ALICE_EMAIL,
      status: "ALLOWED",
      note: "Testador beta",
      updatedByUid: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Mirror AccessControlRepository.observeAllAccessControls query
  await assertSucceeds(
    db.collection("access_controls").orderBy("updatedAt", "desc").get()
  );

  // Mirror AccessControlRepository.upsertAccessControl (partial update path)
  await assertSucceeds(
    db.collection("access_controls").doc(docId).update({
      status: "BLOCKED",
      note: "Acesso suspenso",
      updatedByUid: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Mirror AccessControlRepository.deleteAccessControl
  await assertSucceeds(db.collection("access_controls").doc(docId).delete());
});

test("Owner Admin validation bounds: rejects ghost fields, invalid enum, future timestamp, oversized note, and immutable changes", async () => {
  const db = adminDb();
  const docId = emailToDocId(ALICE_EMAIL);
  const created = new Date(Date.now() - 10000);

  // Reject ghost '@DocumentId' id field on create
  await assertFails(
    db.collection("access_controls").doc(docId).set({
      id: docId,
      emailLower: ALICE_EMAIL,
      status: "ALLOWED",
      note: "Ghost ID",
      updatedByUid: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid enum status
  await assertFails(
    db.collection("access_controls").doc(docId).set({
      emailLower: ALICE_EMAIL,
      status: "SUPER_VIP",
      note: "",
      updatedByUid: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    db.collection("access_controls").doc(docId).set({
      emailLower: ALICE_EMAIL,
      status: "ALLOWED",
      note: "",
      updatedByUid: ADMIN_UID,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject oversized note (> 140 chars)
  await assertFails(
    db.collection("access_controls").doc(docId).set({
      emailLower: ALICE_EMAIL,
      status: "ALLOWED",
      note: "a".repeat(141),
      updatedByUid: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Seed valid doc, then assert immutable emailLower and createdAt cannot be modified
  await assertSucceeds(
    db.collection("access_controls").doc(docId).set({
      emailLower: ALICE_EMAIL,
      status: "ALLOWED",
      note: "Valid",
      updatedByUid: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    db.collection("access_controls").doc(docId).update({
      emailLower: BOB_EMAIL,
      updatedByUid: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    db.collection("access_controls").doc(docId).update({
      createdAt: new Date(Date.now() - 2000),
      updatedByUid: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Verified Regular User: can query ONLY own emailLower; denied unfiltered list, other users, and all writes", async () => {
  const aliceDocId = emailToDocId(ALICE_EMAIL);
  const bobDocId = emailToDocId(BOB_EMAIL);
  const created = new Date(Date.now() - 10000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const seededDb = context.firestore();
    await seededDb.collection("access_controls").doc(aliceDocId).set({
      emailLower: ALICE_EMAIL,
      status: "BLOCKED",
      note: "Suspenso",
      updatedByUid: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    });
    await seededDb.collection("access_controls").doc(bobDocId).set({
      emailLower: BOB_EMAIL,
      status: "ALLOWED",
      note: "Tester",
      updatedByUid: ADMIN_UID,
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = userDb(ALICE_UID, ALICE_EMAIL, true);

  // Mirror AccessControlRepository.observeMyAccessControl(emailLower)
  await assertSucceeds(
    aliceDb
      .collection("access_controls")
      .where("emailLower", "==", ALICE_EMAIL)
      .limit(1)
      .get()
  );

  // Regular user reading own existing doc directly succeeds
  await assertSucceeds(aliceDb.collection("access_controls").doc(aliceDocId).get());

  // Denied unfiltered list & Bob's filtered query & Bob's doc
  await assertFails(aliceDb.collection("access_controls").get());
  await assertFails(
    aliceDb
      .collection("access_controls")
      .where("emailLower", "==", BOB_EMAIL)
      .limit(1)
      .get()
  );
  await assertFails(aliceDb.collection("access_controls").doc(bobDocId).get());

  // Denied self-unblock, self-grant, or delete
  await assertFails(
    aliceDb.collection("access_controls").doc(aliceDocId).update({
      status: "ALLOWED",
      note: "Self unblocked",
      updatedByUid: ALICE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("access_controls").doc(aliceDocId).delete());

  // Unverified user denied even own query
  const unverifiedAliceDb = userDb(ALICE_UID, ALICE_EMAIL, false);
  await assertFails(
    unverifiedAliceDb
      .collection("access_controls")
      .where("emailLower", "==", ALICE_EMAIL)
      .limit(1)
      .get()
  );
});
