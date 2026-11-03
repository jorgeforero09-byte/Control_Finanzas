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

function validTxPayload(uid) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    title: "Supermercado Éxito",
    amount: 125.5,
    type: "EXPENSE",
    category: "FOOD",
    accountId: "acc_1",
    accountName: "Cuenta Nómina",
    yearMonth: "2026-11",
    dateString: "2026-11-02",
    notes: "Compra semanal",
    isBankSynced: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validBankPayload(uid) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    bankName: "BBVA Digital",
    accountType: "CHECKING",
    maskedNumber: "**** 4829",
    balance: 3450.0,
    currency: "USD",
    autoSyncEnabled: true,
    externalInstitutionId: "inst_bbva_01",
    lastSyncSummary: "Sincronizado hoy",
    createdAt: now,
    updatedAt: now,
  };
}

function validBudgetPayload(uid) {
  const now = new Date(Date.now() - 5000);
  return {
    userId: uid,
    category: "FOOD",
    monthlyLimit: 500.0,
    yearMonth: "2026-11",
    createdAt: now,
    updatedAt: now,
  };
}

test("1. Unauthenticated user cannot read or list transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("2. Authenticated user can create and read their own transaction", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1");
  await assertSucceeds(txRef.set(validTxPayload(ALICE_UID)));
  await assertSucceeds(txRef.get());
});

test("3. Authenticated user cannot read another user's transaction", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore()
      .collection("users").doc(BOB_UID)
      .collection("transactions").doc("tx_bob")
      .set(validTxPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("transactions").doc("tx_bob").get()
  );
});

test("4. Identity spoofing: Alice cannot create transaction with Bob's userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_spoof");
  await assertFails(txRef.set(validTxPayload(BOB_UID)));
});

test("5. Shadow update: extra ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_shadow");
  await assertSucceeds(txRef.set(validTxPayload(ALICE_UID)));
  await assertFails(txRef.update({ title: "Updated", isVerified: true }));
});

test("6. Negative transaction amount is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_neg");
  const bad = { ...validTxPayload(ALICE_UID), amount: -25.0 };
  await assertFails(txRef.set(bad));
});

test("7. Invalid transaction type is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_type");
  const bad = { ...validTxPayload(ALICE_UID), type: "INVALID_TYPE" };
  await assertFails(txRef.set(bad));
});

test("8. Malformed yearMonth is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_ym");
  const bad = { ...validTxPayload(ALICE_UID), yearMonth: "2026/11" };
  await assertFails(txRef.set(bad));
});

test("9. BankAccount: owner can create, update, and list own bank accounts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const accRef = aliceDb.collection("users").doc(ALICE_UID).collection("bank_accounts").doc("acc_1");
  await assertSucceeds(accRef.set(validBankPayload(ALICE_UID)));
  await assertSucceeds(accRef.update({ balance: 4200.0, updatedAt: new Date(Date.now() - 1000) }));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bank_accounts").where("userId", "==", ALICE_UID).get()
  );
});

test("10. CategoryBudget: owner can create and update budget, cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const budgetRef = aliceDb.collection("users").doc(ALICE_UID).collection("budgets").doc("bud_FOOD");
  await assertSucceeds(budgetRef.set(validBudgetPayload(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("budgets").doc("bud_FOOD").get()
  );
});
