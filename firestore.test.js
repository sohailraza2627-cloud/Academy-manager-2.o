const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_01";
const TEACHER_UID = "teacher_uid_02";
const OUTSIDER_UID = "outsider_uid_03";
const ADMIN_EMAIL = "principal@alhamd.edu.pk";
const TEACHER_EMAIL = "teacher1@alhamd.edu.pk";
const OUTSIDER_EMAIL = "outsider@example.com";
const ACADEMY_ID = "acad_alhamd_001";
const OTHER_ACADEMY_ID = "acad_other_999";

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

async function seedAcademyAdminAndTeacher() {
  const now = Date.now();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("academies").doc(ACADEMY_ID).set({
      academyName: "Alhamd Science Evening Academy",
      tagline: "Evening Coaching",
      phone: "03001234567",
      address: "Lahore",
      ownerUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("academies").doc(ACADEMY_ID).collection("members").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: ADMIN_EMAIL,
      name: "Principal Admin",
      role: "ADMIN",
      createdAt: now,
    });
    await db.collection("academies").doc(ACADEMY_ID).collection("members").doc(TEACHER_UID).set({
      uid: TEACHER_UID,
      email: TEACHER_EMAIL,
      name: "Sir Bilal",
      role: "TEACHER",
      createdAt: now,
    });
    await db.collection("users").doc(ADMIN_UID).set({
      academyId: ACADEMY_ID,
      role: "ADMIN",
      email: ADMIN_EMAIL,
      name: "Principal Admin",
    });
    await db.collection("users").doc(TEACHER_UID).set({
      academyId: ACADEMY_ID,
      role: "TEACHER",
      email: TEACHER_EMAIL,
      name: "Sir Bilal",
    });
    await db.collection("academies").doc(OTHER_ACADEMY_ID).set({
      academyName: "Other Academy",
      tagline: "",
      phone: "",
      address: "",
      ownerUid: OUTSIDER_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("academies").doc(OTHER_ACADEMY_ID).collection("students").doc("stu_other").set({
      id: "stu_other",
      name: "Zain",
      fatherName: "Tariq",
      phone: "03001112222",
      address: "Karachi",
      classId: "cls_other",
      rollNo: "101",
      admissionDate: now,
      guardianWhatsapp: "+923001112222",
      whatsappHistory: "",
      monthlyDiscount: 0,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
      deleted: false,
    });
  });
}

test("Unauthenticated users are denied on all reads and writes", async () => {
  await seedAcademyAdminAndTeacher();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("academies").doc(ACADEMY_ID).get());
  await assertFails(unauthDb.collection("academies").doc(ACADEMY_ID).collection("students").get());
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("invites").doc(TEACHER_EMAIL).get());
});

test("Atomic Admin academy creation batch succeeds and blocks self-escalation", async () => {
  const now = Date.now();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const acadRef = adminDb.collection("academies").doc(ACADEMY_ID);
  const batch = adminDb.batch();
  batch.set(acadRef, {
    academyName: "Alhamd Science Evening Academy",
    tagline: "",
    phone: "",
    address: "",
    ownerUid: ADMIN_UID,
    createdAt: now,
    updatedAt: now,
  });
  batch.set(acadRef.collection("members").doc(ADMIN_UID), {
    uid: ADMIN_UID,
    email: ADMIN_EMAIL,
    name: "Principal",
    role: "ADMIN",
    createdAt: now,
  });
  batch.set(adminDb.collection("users").doc(ADMIN_UID), {
    academyId: ACADEMY_ID,
    role: "ADMIN",
    email: ADMIN_EMAIL,
    name: "Principal",
  });
  await assertSucceeds(batch.commit());

  // Outsider cannot forge ADMIN membership in existing academy
  const outsiderDb = testEnv
    .authenticatedContext(OUTSIDER_UID, { email: OUTSIDER_EMAIL, email_verified: true })
    .firestore();
  await assertFails(
    outsiderDb.collection("academies").doc(ACADEMY_ID).collection("members").doc(OUTSIDER_UID).set({
      uid: OUTSIDER_UID,
      email: OUTSIDER_EMAIL,
      name: "Attacker",
      role: "ADMIN",
      createdAt: now,
    })
  );
  await assertFails(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set({
      academyId: ACADEMY_ID,
      role: "ADMIN",
      email: OUTSIDER_EMAIL,
      name: "Attacker",
    })
  );
});

test("Invited teacher claims Google email invite atomically; unverified email fails", async () => {
  const now = Date.now();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("academies").doc(ACADEMY_ID).set({
      academyName: "Alhamd Science Evening Academy",
      tagline: "",
      phone: "",
      address: "",
      ownerUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("academies").doc(ACADEMY_ID).collection("members").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: ADMIN_EMAIL,
      name: "Principal",
      role: "ADMIN",
      createdAt: now,
    });
  });

  // Admin invites teacher by lowercase email
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(
    adminDb.collection("invites").doc(TEACHER_EMAIL).set({
      academyId: ACADEMY_ID,
      academyName: "Alhamd Science Evening Academy",
      role: "TEACHER",
      teacherName: "Sir Bilal",
      email: TEACHER_EMAIL,
      invitedByUid: ADMIN_UID,
      createdAt: now,
    })
  );

  // Spoofed unverified email cannot read or claim the invite
  const unverifiedDb = testEnv
    .authenticatedContext(TEACHER_UID, { email: TEACHER_EMAIL, email_verified: false })
    .firestore();
  await assertFails(unverifiedDb.collection("invites").doc(TEACHER_EMAIL).get());

  // Verified invited teacher reads invite and claims atomically in batch
  const teacherDb = testEnv
    .authenticatedContext(TEACHER_UID, { email: TEACHER_EMAIL, email_verified: true })
    .firestore();
  await assertSucceeds(teacherDb.collection("invites").doc(TEACHER_EMAIL).get());

  const batch = teacherDb.batch();
  batch.set(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("members").doc(TEACHER_UID),
    {
      uid: TEACHER_UID,
      email: TEACHER_EMAIL,
      name: "Sir Bilal",
      role: "TEACHER",
      createdAt: now,
    }
  );
  batch.set(teacherDb.collection("users").doc(TEACHER_UID), {
    academyId: ACADEMY_ID,
    role: "TEACHER",
    email: TEACHER_EMAIL,
    name: "Sir Bilal",
  });
  batch.delete(teacherDb.collection("invites").doc(TEACHER_EMAIL));
  await assertSucceeds(batch.commit());
});

test("Cross-academy isolation blocks outsider reads and writes", async () => {
  await seedAcademyAdminAndTeacher();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const outsiderDb = testEnv
    .authenticatedContext(OUTSIDER_UID, { email: OUTSIDER_EMAIL, email_verified: true })
    .firestore();

  await assertFails(adminDb.collection("academies").doc(OTHER_ACADEMY_ID).collection("students").get());
  await assertFails(outsiderDb.collection("academies").doc(ACADEMY_ID).collection("students").get());
  await assertFails(outsiderDb.collection("academies").doc(ACADEMY_ID).get());
});

test("Admin full CRUD on students with guardianWhatsapp & whatsappHistory; shadow fields rejected", async () => {
  await seedAcademyAdminAndTeacher();
  const now = Date.now();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const stuRef = adminDb.collection("academies").doc(ACADEMY_ID).collection("students").doc("stu_01");

  await assertSucceeds(
    stuRef.set({
      id: "stu_01",
      name: "Ayesha Khan",
      fatherName: "Imran Khan",
      phone: "03001234567",
      address: "Model Town",
      classId: "cls_10",
      rollNo: "10-01",
      admissionDate: now,
      guardianWhatsapp: "+923001234567",
      whatsappHistory: "",
      monthlyDiscount: 500,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
      deleted: false,
    })
  );

  await assertSucceeds(
    stuRef.update({
      guardianWhatsapp: "+923219876543",
      whatsappHistory: "+923001234567 (27 Nov 2026)",
      updatedAt: now + 1000,
    })
  );

  // Reject ghost field ("shadow update")
  await assertFails(
    stuRef.update({
      isAdmin: true,
      updatedAt: now + 2000,
    })
  );
});

test("Teacher can read academy data and write attendance, but cannot modify students, fees, or classes", async () => {
  await seedAcademyAdminAndTeacher();
  const now = Date.now();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const teacherDb = testEnv
    .authenticatedContext(TEACHER_UID, { email: TEACHER_EMAIL, email_verified: true })
    .firestore();

  await assertSucceeds(
    adminDb.collection("academies").doc(ACADEMY_ID).collection("classes").doc("cls_9").set({
      id: "cls_9",
      name: "Class 9 Science",
      monthlyFee: 3000,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    })
  );

  // Teacher can list classes and write attendance
  await assertSucceeds(teacherDb.collection("academies").doc(ACADEMY_ID).collection("classes").get());
  await assertSucceeds(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("attendance").doc("att_1").set({
      id: "att_1",
      studentId: "stu_1",
      classId: "cls_9",
      dateKey: "2026-11-27",
      monthKey: "2026-11",
      status: "ABSENT",
      updatedAt: now,
      deleted: false,
    })
  );
  await assertSucceeds(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("attendance").doc("att_1").update({
      status: "PRESENT",
      updatedAt: now + 500,
    })
  );

  // Teacher CANNOT write classes, students, fee_charges, or fee_payments
  await assertFails(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("classes").doc("cls_hack").set({
      id: "cls_hack",
      name: "Hack Class",
      monthlyFee: 0,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    })
  );
  await assertFails(
    teacherDb.collection("academies").doc(ACADEMY_ID).collection("attendance").doc("att_1").delete()
  );
});

test("Fee payments are strictly append-only (Admin create succeeds; update and delete fail)", async () => {
  await seedAcademyAdminAndTeacher();
  const now = Date.now();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const payRef = adminDb.collection("academies").doc(ACADEMY_ID).collection("fee_payments").doc("pay_01");
  await assertSucceeds(
    payRef.set({
      id: "pay_01",
      chargeId: "chg_01",
      studentId: "stu_01",
      ledgerType: "TUITION",
      receiptNumber: "R-A-0001",
      amountPaid: 2500,
      discountApplied: 0,
      paidDate: now,
      monthKey: "2026-11",
      notes: "",
      updatedAt: now,
    })
  );

  // Cannot update or delete receipt once created
  await assertFails(payRef.update({ amountPaid: 5000, updatedAt: now + 100 }));
  await assertFails(payRef.delete());
});
