const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTripPayload(uid, createdAt, updatedAt = createdAt) {
  return {
    userId: uid,
    destination: "Tokyo",
    country: "Japan",
    latitude: 35.6762,
    longitude: 139.6503,
    startDateEpochDay: 20500,
    endDateEpochDay: 20505,
    durationDays: 6,
    activities: ["City Walking", "Hiking"],
    calendarEventsSummary: ["Shibuya Dinner (Shibuya)"],
    weatherSummary: "Partly cloudy · 12°C–20°C · 25% rain · Winds up to 18 km/h",
    weatherTempMinC: 12.0,
    weatherTempMaxC: 20.0,
    weatherRainProbMax: 25,
    weatherAlertActive: false,
    weatherAlertMessage: "",
    weatherNotificationsEnabled: true,
    status: "active",
    resetCount: 0,
    createdAt,
    updatedAt,
  };
}

function validItemPayload(uid, tripId, createdAt, updatedAt = createdAt) {
  return {
    userId: uid,
    tripId,
    name: "Passport",
    category: "Documents",
    quantity: 1,
    isPacked: false,
    isAlwaysBring: true,
    sourceType: "always_bring",
    contextReason: "Always bring on every trip",
    createdAt,
    updatedAt,
  };
}

function validAlwaysBringPayload(uid, createdAt, updatedAt = createdAt) {
  return {
    userId: uid,
    name: "Phone Charger & USB-C Cable",
    category: "Tech",
    quantity: 1,
    notes: "Always bring on every trip",
    source: "default",
    createdAt,
    updatedAt,
  };
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

test("Unauthenticated user: cannot read or write trips, items, or alwaysBring", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/trips`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/trips`).doc("trip_1").set(validTripPayload(ALICE_UID, ts))
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/alwaysBring`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/alwaysBring`).doc("ab_1").set(validAlwaysBringPayload(ALICE_UID, ts))
  );
});

test("Authenticated owner: valid Kotlin create, partial updates, and reset payloads succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/trips/trip_1`).set(validTripPayload(ALICE_UID, created))
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/trips/trip_1/items/item_1`).set(validItemPayload(ALICE_UID, "trip_1", created))
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/alwaysBring/ab_1`).set(validAlwaysBringPayload(ALICE_UID, created))
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/trips/trip_1/items/item_1`).update({
      isPacked: true,
      updatedAt: updated,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/trips/trip_1`).update({
      weatherSummary: "Rain · 9°C–14°C · 80% rain · Winds up to 24 km/h",
      weatherTempMinC: 9.0,
      weatherTempMaxC: 14.0,
      weatherRainProbMax: 80,
      weatherAlertActive: true,
      weatherAlertMessage: "80% rain risk",
      status: "completed",
      resetCount: 1,
      updatedAt: updated,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/trips/trip_1`).update({
      travelGuideJson: JSON.stringify({ powerAdapterSummary: "Type A / B (100V)" }),
      headerImageUrl: "https://upload.wikimedia.org/wikipedia/commons/tokyo.jpg",
      updatedAt: updated,
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/trips`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/trips/trip_1/items`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/alwaysBring`).get());
});

test("Security & validation guards: rejects cross-user access, @DocumentId id field, immutable createdAt mutation, invalid travelGuideJson, and future timestamps", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`users/${BOB_UID}/trips/bob_trip`).set(validTripPayload(BOB_UID, created));
    await db.doc(`users/${BOB_UID}/trips/bob_trip/items/bob_item`).set(validItemPayload(BOB_UID, "bob_trip", created));
    await db.doc(`users/${ALICE_UID}/trips/alice_trip`).set(validTripPayload(ALICE_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(aliceDb.doc(`users/${BOB_UID}/trips/bob_trip`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/trips/bob_trip/items`).get());
  await assertFails(aliceDb.doc(`users/${BOB_UID}/trips/bob_trip`).update({ status: "completed" }));

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/trips/alice_trip_2`).set({
      id: "alice_trip_2",
      ...validTripPayload(ALICE_UID, created),
    })
  );

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/trips/alice_trip`).update({
      travelGuideJson: 12345,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/trips/alice_trip`).update({
      headerImageUrl: 999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/trips/alice_trip`).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  const future = new Date(Date.now() + 60000);
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/alwaysBring/ab_future`).set(validAlwaysBringPayload(ALICE_UID, future))
  );
});
