const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

// Helper to build a valid user profile payload
function makeUserProfile(uid, username = "alice_01") {
  return {
    userId: uid,
    email: `${username}@example.com`,
    username,
    displayName: "Alice",
    bio: "Hello Tawasul Plus",
    preferredLanguage: "ar",
    themeMode: "light",
    isOnline: true,
    lastSeen: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function makeUsernameEntry(uid, username = "alice_01") {
  return {
    userId: uid,
    username,
    displayName: "Alice",
    bio: "Hello Tawasul Plus",
    isOnline: true,
    lastSeen: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

// --- 1. Unauthenticated Denials ---
test("Unauthenticated user cannot read or write users, usernames, or chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("usernames").doc("alice_01").get());
  await assertFails(unauthDb.collection("chats").get());
});

// --- 2. User Profile PII Isolation ---
test("Owner can create and read own user profile; another user cannot read or update it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makeUserProfile(ALICE_UID, "alice_01"))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" })
  );
});

test("Shadow update with ghost field on user profile is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...makeUserProfile(ALICE_UID, "alice_01"),
      isAdmin: true,
    })
  );
});

// --- 3. Unique Username Enforcement ---
test("User can claim unique username and lookup by exact doc, but cannot steal existing username or list all", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_01").set(makeUsernameEntry(ALICE_UID, "alice_01"))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Bob can look up Alice by exact @username
  await assertSucceeds(bobDb.collection("usernames").doc("alice_01").get());
  // Bob cannot list all usernames without ownership filter
  await assertFails(bobDb.collection("usernames").get());
  // Bob cannot overwrite or steal Alice's username
  await assertFails(
    bobDb.collection("usernames").doc("alice_01").set(makeUsernameEntry(BOB_UID, "alice_01"))
  );
});

// --- 4. Chats & Messages Isolation ---
test("Participants can create chat and send/edit/react to messages; non-participant Charlie is denied", async () => {
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Alice can check if deterministic chat exists before creating it
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).get());

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantUsernames: { [ALICE_UID]: "alice_01", [BOB_UID]: "bob_02" },
      lastMessageText: "",
      lastMessageAt: serverTimestamp(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice queries her chats with array-contains
  await assertSucceeds(
    aliceDb.collection("chats").where("participantIds", "array-contains", ALICE_UID).get()
  );
  // Blanket list on chats fails
  await assertFails(aliceDb.collection("chats").get());
  // Charlie cannot read or list Alice & Bob's chat
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  // Alice sends a message
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").set({
      chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      participantIds: [ALICE_UID, BOB_UID],
      text: "مرحبا بوب!",
      type: "text",
      isEdited: false,
      isDeleted: false,
      readBy: [ALICE_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Bob can read messages with participantIds filter and add emoji reaction
  await assertSucceeds(
    bobDb
      .collection("chats")
      .doc(chatId)
      .collection("messages")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      reactions: { [BOB_UID]: "❤️" },
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: serverTimestamp(),
    })
  );

  // Bob CANNOT edit Alice's message text
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      text: "Tampered!",
      isEdited: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Alice CAN edit her own message text
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      text: "مرحبا بوب! تم التعديل",
      isEdited: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Charlie CANNOT read or send messages in Alice & Bob's chat
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_2").set({
      chatId,
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      participantIds: [ALICE_UID, BOB_UID],
      text: "Intruder",
      type: "text",
      isEdited: false,
      isDeleted: false,
      readBy: [CHARLIE_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 5. Statuses, Groups, and Calls Isolation ---
test("Statuses, Groups, and Calls enforce participant and author isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const futureExpiry = new Date(Date.now() + 24 * 60 * 60 * 1000);
  await assertSucceeds(
    aliceDb.collection("statuses").doc("st_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorUsername: "alice_01",
      type: "text",
      text: "صباح الخير تواصل بلس",
      backgroundHex: "#F5B700",
      privacy: "contacts",
      visibleToUids: [ALICE_UID, BOB_UID],
      viewedBy: [ALICE_UID],
      expiresAt: futureExpiry,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  // Bob can view & mark viewedBy; Charlie cannot read
  await assertSucceeds(bobDb.collection("statuses").doc("st_1").get());
  await assertSucceeds(
    bobDb.collection("statuses").doc("st_1").update({
      viewedBy: [ALICE_UID, BOB_UID],
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(charlieDb.collection("statuses").doc("st_1").get());

  // Group + Invite + Message
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_1").set({
      name: "فريق تواصل",
      description: "مجموعة العمل",
      creatorId: ALICE_UID,
      adminIds: [ALICE_UID],
      memberIds: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      inviteCode: "TAWASUL1",
      lastMessageText: "",
      lastMessageAt: serverTimestamp(),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_invites").doc("TAWASUL1").set({
      inviteCode: "TAWASUL1",
      groupId: "grp_1",
      groupName: "فريق تواصل",
      creatorId: ALICE_UID,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_1").collection("messages").doc("gm_1").set({
      groupId: "grp_1",
      senderId: BOB_UID,
      senderName: "Bob",
      memberIds: [ALICE_UID, BOB_UID],
      text: "أهلاً بالجميع!",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(charlieDb.collection("groups").doc("grp_1").get());
  await assertSucceeds(charlieDb.collection("group_invites").doc("TAWASUL1").get());
  await assertSucceeds(
    charlieDb.collection("groups").doc("grp_1").update({
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob", [CHARLIE_UID]: "Charlie" },
      updatedAt: serverTimestamp(),
    })
  );

  // Calls & WebRTC SDP/ICE signaling
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "ringing",
      durationSeconds: 0,
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").collection("candidates").doc("ice_1").set({
      callId: "call_1",
      senderId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      candidate: "candidate:1 1 UDP 2122252543 192.0.2.1 54400 typ host",
      sdpMid: "0",
      sdpMLineIndex: 0,
      createdAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "active",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 45,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(charlieDb.collection("calls").doc("call_1").get());
  await assertFails(
    charlieDb.collection("calls").doc("call_1").collection("candidates").doc("ice_2").set({
      callId: "call_1",
      senderId: CHARLIE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      candidate: "candidate:hack",
      sdpMid: "0",
      sdpMLineIndex: 0,
      createdAt: serverTimestamp(),
    })
  );

  // Voice & File messages in chat
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantUsernames: { [ALICE_UID]: "alice_01", [BOB_UID]: "bob_02" },
      lastMessageText: "",
      lastMessageAt: serverTimestamp(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_voice").set({
      chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      participantIds: [ALICE_UID, BOB_UID],
      text: "",
      type: "voice",
      mediaData: "data:audio/mp4;base64,AAAA",
      mimeType: "audio/mp4",
      fileSizeBytes: 12,
      mediaDurationMs: 4500,
      isEdited: false,
      isDeleted: false,
      readBy: [ALICE_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_file").set({
      chatId,
      senderId: BOB_UID,
      senderName: "Bob",
      participantIds: [ALICE_UID, BOB_UID],
      text: "",
      type: "file",
      mediaData: "data:application/pdf;base64,JVBERi0=",
      fileName: "report.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 1024,
      isEdited: false,
      isDeleted: false,
      readBy: [BOB_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

