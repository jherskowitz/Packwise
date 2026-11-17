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

function makeTripPayload(userId, overrides = {}) {
  const now = new Date();
  return {
    userId,
    destination: "Tokyo",
    country: "Japan",
    latitude: 35.6762,
    longitude: 139.6503,
    startDateEpochDay: 20770,
    endDateEpochDay: 20775,
    durationDays: 6,
    activities: ["City Walking", "Photography"],
    calendarEventsSummary: ["Team Dinner (Nov 16)"],
    weatherSummary: "12C-19C, 40% rain",
    weatherTempMinC: 12.0,
    weatherTempMaxC: 19.0,
    weatherRainProbMax: 40,
    weatherAlertActive: true,
    weatherAlertMessage: "Rain likely on Day 2 - pack waterproof shell",
    status: "active",
    resetCount: 0,
    weatherNotificationsEnabled: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function makePackingItemPayload(userId, tripId, overrides = {}) {
  const now = new Date();
  return {
    userId,
    tripId,
    name: "Waterproof Rain Jacket",
    category: "Outerwear",
    quantity: 1,
    isPacked: false,
    isAlwaysBring: false,
    contextReason: "40% rain forecast in Tokyo",
    sourceType: "weather",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function makeAlwaysBringPayload(userId, overrides = {}) {
  const now = new Date();
  return {
    userId,
    name: "Passport",
    category: "Documents",
    quantity: 1,
    notes: "Check expiration date",
    source: "default",
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated user: cannot list or write trips or alwaysBring", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trips").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("alwaysBring").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").set(makeTripPayload(ALICE_UID))
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's trips, items, or alwaysBring", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").set(makeTripPayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").collection("items").doc("item_1").set(makePackingItemPayload(ALICE_UID, "trip_1"));
    await db.collection("users").doc(ALICE_UID).collection("alwaysBring").doc("ab_1").set(makeAlwaysBringPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("trips").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").collection("items").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("alwaysBring").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("alwaysBring").doc("ab_2").set(makeAlwaysBringPayload(ALICE_UID)));
});

test("Owner CRUD & Shadow Update protection on AlwaysBring, Trip, and PackingItem", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Valid AlwaysBring create & update
  const abRef = aliceDb.collection("users").doc(ALICE_UID).collection("alwaysBring").doc("ab_passport");
  await assertSucceeds(abRef.set(makeAlwaysBringPayload(ALICE_UID)));
  await assertSucceeds(abRef.update({ quantity: 2, updatedAt: new Date() }));

  // Shadow update with ghost field must fail
  await assertFails(abRef.update({ ghostField: true, updatedAt: new Date() }));

  // Valid Trip create & weather update & reset
  const tripRef = aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_tokyo");
  await assertSucceeds(tripRef.set(makeTripPayload(ALICE_UID)));
  await assertSucceeds(
    tripRef.update({
      weatherSummary: "10C-17C, 80% heavy rain",
      weatherTempMinC: 10.0,
      weatherTempMaxC: 17.0,
      weatherRainProbMax: 80,
      weatherAlertActive: true,
      weatherAlertMessage: "Heavy rain alert - pack umbrella",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    tripRef.update({
      status: "completed",
      resetCount: 1,
      weatherAlertActive: false,
      weatherAlertMessage: "",
      updatedAt: new Date(),
    })
  );

  // Spoofed userId on create must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_spoof").set(makeTripPayload(BOB_UID))
  );

  // Orphaned PackingItem (non-existent trip) must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("nonexistent").collection("items").doc("item_orphan").set(makePackingItemPayload(ALICE_UID, "nonexistent"))
  );

  // Valid PackingItem create & toggle packed
  const itemRef = tripRef.collection("items").doc("item_jacket");
  await assertSucceeds(itemRef.set(makePackingItemPayload(ALICE_UID, "trip_tokyo")));
  await assertSucceeds(itemRef.update({ isPacked: true, updatedAt: new Date() }));
  // Invalid sourceType must fail
  await assertFails(
    tripRef.collection("items").doc("item_bad").set(makePackingItemPayload(ALICE_UID, "trip_tokyo", { sourceType: "invalid_source" }))
  );
});
