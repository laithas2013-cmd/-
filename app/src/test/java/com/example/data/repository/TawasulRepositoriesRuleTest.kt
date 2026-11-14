package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TawasulRepositoriesRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureUserProfile_and_findByUsername_succeeds_and_protectsCrossUser() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(6).lowercase()
    val aliceEmail = "alice_$suffix@test.com"
    val bobEmail = "bob_$suffix@test.com"

    val aliceUid = signInTestUser(aliceEmail)
    val aliceUser = checkNotNull(auth.currentUser)
    val userRepo = UserRepository(firestore)

    val aliceProfileResult = withTimeout(TIMEOUT_MS) { userRepo.ensureUserProfile(aliceUser, "ar", "light") }
    assertTrue("Alice profile creation should succeed: ${aliceProfileResult.exceptionOrNull()}", aliceProfileResult.isSuccess)
    val aliceProfile = aliceProfileResult.getOrThrow()
    assertEquals(aliceUid, aliceProfile.userId)
    assertTrue(aliceProfile.username.isNotBlank())

    // Update profile and change username
    val newUsername = "alice_new_$suffix"
    val updateResult =
      withTimeout(TIMEOUT_MS) {
        userRepo.updateProfile(
          uid = aliceUid,
          currentUsername = aliceProfile.username,
          newUsernameRaw = newUsername,
          displayName = "أليس المطوّرة",
          bio = "تواصل بلس",
          avatarUrl = null,
          preferredLanguage = "ar",
          themeMode = "light",
        )
      }
    assertTrue("Profile update should succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)

    // Old username should be released
    val oldLookup = withTimeout(TIMEOUT_MS) { userRepo.findUserByUsername(aliceProfile.username) }
    assertNull(oldLookup.getOrNull())

    // Sign in as Bob and find Alice by new @username
    val bobUid = signInTestUser(bobEmail)
    val bobUser = checkNotNull(auth.currentUser)
    val bobProfile = withTimeout(TIMEOUT_MS) { userRepo.ensureUserProfile(bobUser, "en", "dark").getOrThrow() }

    val foundAlice = withTimeout(TIMEOUT_MS) { userRepo.findUserByUsername(newUsername).getOrThrow() }
    assertNotNull(foundAlice)
    assertEquals(aliceUid, foundAlice?.userId)

    // Bob can save Alice as a contact
    val contactResult = withTimeout(TIMEOUT_MS) { userRepo.addContact(bobUid, foundAlice!!) }
    assertTrue(contactResult.isSuccess)

    // Bob starts a chat and sends a message to Alice
    val chatRepo = ChatRepository(firestore)
    val chatId = withTimeout(TIMEOUT_MS) { chatRepo.getOrCreateDirectChat(bobProfile, foundAlice!!).getOrThrow() }
    val sendResult =
      withTimeout(TIMEOUT_MS) {
        chatRepo.sendMessage(
          chatId = chatId,
          myUid = bobUid,
          myName = bobProfile.displayName,
          participantIds = listOf(aliceUid, bobUid).sorted(),
          otherUid = aliceUid,
          text = "مرحبا أليس!",
        )
      }
    assertTrue("Send message should succeed: ${sendResult.exceptionOrNull()}", sendResult.isSuccess)

    val observedMessages =
      withTimeout(FLOW_TIMEOUT_MS) {
        chatRepo.observeMessages(chatId, bobUid).first { it.isNotEmpty() }
      }
    assertEquals(1, observedMessages.size)
    assertEquals("مرحبا أليس!", observedMessages.first().text)

    // Bob edits and reacts to message
    val msgId = observedMessages.first().id
    assertTrue(withTimeout(TIMEOUT_MS) { chatRepo.editMessage(chatId, msgId, "تم التعديل").isSuccess })
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        chatRepo.toggleReaction(chatId, msgId, bobUid, emptyMap(), "👍").isSuccess
      }
    )

    // Bob sends a voice note and a file attachment in direct chat
    val voiceRes =
      withTimeout(TIMEOUT_MS) {
        chatRepo.sendMediaMessage(
          chatId = chatId,
          myUid = bobUid,
          myName = bobProfile.displayName,
          participantIds = listOf(aliceUid, bobUid).sorted(),
          otherUid = aliceUid,
          type = "voice",
          mediaData = "data:audio/mp4;base64,AAAA",
          previewText = "🎤 رسالة صوتية",
          caption = "",
          fileName = "voice.m4a",
          mimeType = "audio/mp4",
          fileSizeBytes = 1024L,
          mediaDurationMs = 3500L,
        )
      }
    assertTrue("Send voice message should succeed: ${voiceRes.exceptionOrNull()}", voiceRes.isSuccess)

    // Charlie (3rd user) gets zero messages with his own filter, and PERMISSION_DENIED on direct read, Bob's filter, or sending
    signInTestUser("charlie_$suffix@test.com")
    val charlieUid = auth.currentUser!!.uid
    val charlieOwnFilter =
      withTimeout(FLOW_TIMEOUT_MS) {
        chatRepo.observeMessages(chatId, charlieUid).first()
      }
    assertTrue("Charlie's own filter must return 0 messages", charlieOwnFilter.isEmpty())

    var crossFilterDenied = false
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        chatRepo.observeMessages(chatId, bobUid).first()
      }
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        crossFilterDenied = true
      }
    }
    assertTrue("Charlie querying with Bob's UID must receive PERMISSION_DENIED", crossFilterDenied)

    // Non-participant Charlie must be denied sending to Alice & Bob's chat
    var sendDenied = false
    val unauthorizedSend =
      withTimeout(TIMEOUT_MS) {
        chatRepo.sendMessage(
          chatId = chatId,
          myUid = charlieUid,
          myName = "Charlie",
          participantIds = listOf(aliceUid, bobUid).sorted(),
          otherUid = bobUid,
          text = "Intruder",
        )
      }
    val sendEx = unauthorizedSend.exceptionOrNull()?.unwrapFirestoreException()
    if (sendEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
      sendDenied = true
    }
    assertTrue("Non-participant Charlie must be denied sending to Alice & Bob's chat", sendDenied)
  }

  @Test
  fun communityRepository_onboarding_statuses_groups_calls_succeed_and_enforceRules() = runBlocking {
    val suffix = UUID.randomUUID().toString().replace("-", "").take(6).lowercase()
    val aliceUid = signInTestUser("c_alice_$suffix@test.com")
    val userRepo = UserRepository(firestore)
    val aliceInitial =
      withTimeout(TIMEOUT_MS) { userRepo.ensureUserProfile(auth.currentUser!!, "ar", "light").getOrThrow() }
    assertEquals(false, aliceInitial.onboardingCompleted)

    val aliceHandle = "alice_onb_$suffix"
    val completeRes =
      withTimeout(TIMEOUT_MS) {
        userRepo.completeOnboarding(
          uid = aliceUid,
          currentUsername = aliceInitial.username,
          newUsernameRaw = aliceHandle,
          displayName = "أليس المكتملة",
          bio = "مرحبا بكم في تواصل بلس",
          avatarUrl = "preset:sun",
          preferredLanguage = "ar",
          themeMode = "light",
        )
      }
    assertTrue("completeOnboarding failed: ${completeRes.exceptionOrNull()}", completeRes.isSuccess)
    val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) { userRepo.observeMyProfile(aliceUid).first { it?.onboardingCompleted == true } }!!
    assertEquals(aliceHandle, aliceProfile.username)

    // Bob signs in
    val bobUid = signInTestUser("c_bob_$suffix@test.com")
    val bobProfile = withTimeout(TIMEOUT_MS) { userRepo.ensureUserProfile(auth.currentUser!!, "en", "dark").getOrThrow() }

    // Switch back to Alice to publish a status, create a group, and initiate a call
    signInTestUser("c_alice_$suffix@test.com")
    val communityRepo = CommunityRepository(firestore)

    val statusId =
      withTimeout(TIMEOUT_MS) {
        communityRepo
          .publishStatus(
            author = aliceProfile,
            contactUids = listOf(bobUid),
            type = "text",
            text = "حالة جديدة على تواصل بلس",
            imageData = null,
            backgroundHex = "#F5B700",
            privacy = "contacts",
          )
          .getOrThrow()
      }
    assertTrue(statusId.isNotBlank())

    val groupId =
      withTimeout(TIMEOUT_MS) {
        communityRepo
          .createGroup(
            creator = aliceProfile,
            name = "مجموعة الأصدقاء",
            description = "تواصل مستمر",
            initialMembers = listOf(bobUid to bobProfile.displayName),
          )
          .getOrThrow()
      }
    val group = withTimeout(FLOW_TIMEOUT_MS) { communityRepo.observeGroup(groupId).first { it != null } }!!
    assertTrue(group.inviteCode.length >= 4)

    val groupMsgRes =
      withTimeout(TIMEOUT_MS) {
        communityRepo.sendGroupMessage(group, aliceUid, aliceProfile.displayName, "أهلاً بكم!")
      }
    assertTrue("sendGroupMessage failed: ${groupMsgRes.exceptionOrNull()}", groupMsgRes.isSuccess)

    val groupFileRes =
      withTimeout(TIMEOUT_MS) {
        communityRepo.sendGroupMediaMessage(
          group = group,
          senderId = aliceUid,
          senderName = aliceProfile.displayName,
          type = "file",
          mediaData = "data:application/pdf;base64,JVBERi0xLjQ=",
          previewText = "📎 ملف: spec.pdf",
          fileName = "spec.pdf",
          mimeType = "application/pdf",
          fileSizeBytes = 128L,
        )
      }
    assertTrue("sendGroupMediaMessage failed: ${groupFileRes.exceptionOrNull()}", groupFileRes.isSuccess)

    val call =
      withTimeout(TIMEOUT_MS) {
        communityRepo
          .startCall(aliceProfile, bobUid, bobProfile.displayName, bobProfile.avatarUrl, "video")
          .getOrThrow()
      }
    assertTrue(withTimeout(TIMEOUT_MS) { communityRepo.updateCallOffer(call.id, "v=0\no=- 1 1 IN IP4 127.0.0.1\n").isSuccess })
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        communityRepo
          .addIceCandidate(call.id, aliceUid, call.participantIds, "candidate:1 1 UDP 1 127.0.0.1 9 typ host", "0", 0)
          .isSuccess
      }
    )

    // Sign in as Bob: view status, read group messages, exchange WebRTC answer, accept & end call
    signInTestUser("c_bob_$suffix@test.com")
    val bobStatuses = withTimeout(FLOW_TIMEOUT_MS) { communityRepo.observeStatuses(bobUid).first { s -> s.any { it.id == statusId } } }
    assertTrue(bobStatuses.any { it.id == statusId })
    assertTrue(withTimeout(TIMEOUT_MS) { communityRepo.markStatusViewed(statusId, bobUid).isSuccess })

    val bobGroupMsgs = withTimeout(FLOW_TIMEOUT_MS) { communityRepo.observeGroupMessages(groupId, bobUid).first { it.size >= 2 } }
    assertEquals("أهلاً بكم!", bobGroupMsgs.first().text)
    assertEquals("file", bobGroupMsgs[1].type)

    val bobCandidates = withTimeout(FLOW_TIMEOUT_MS) { communityRepo.observeIceCandidates(call.id, bobUid).first { it.isNotEmpty() } }
    assertEquals(1, bobCandidates.size)
    assertTrue(withTimeout(TIMEOUT_MS) { communityRepo.updateCallStatus(call.id, "active", 0L).isSuccess })
    assertTrue(withTimeout(TIMEOUT_MS) { communityRepo.updateCallAnswer(call.id, "v=0\no=- 2 1 IN IP4 127.0.0.1\n").isSuccess })
    assertTrue(withTimeout(TIMEOUT_MS) { communityRepo.updateCallStatus(call.id, "ended", 32L).isSuccess })

    // Charlie joins via invite code
    val charlieUid = signInTestUser("c_charlie_$suffix@test.com")
    val charlieProfile = withTimeout(TIMEOUT_MS) { userRepo.ensureUserProfile(auth.currentUser!!, "ar", "system").getOrThrow() }
    val joinRes = withTimeout(TIMEOUT_MS) { communityRepo.joinGroupByInviteCode(group.inviteCode, charlieProfile) }
    assertTrue("joinGroupByInviteCode failed: ${joinRes.exceptionOrNull()}", joinRes.isSuccess)
    assertEquals(groupId, joinRes.getOrThrow())

    // Charlie cannot publish ICE candidates into Alice & Bob's call
    val forbiddenIce =
      withTimeout(TIMEOUT_MS) {
        communityRepo.addIceCandidate(call.id, charlieUid, call.participantIds, "candidate:fake", "0", 0)
      }
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      forbiddenIce.exceptionOrNull()?.unwrapFirestoreException()?.code,
    )

    // Charlie's own status query does NOT expose Alice's contacts-only status
    val charlieStatuses = withTimeout(FLOW_TIMEOUT_MS) { communityRepo.observeStatuses(charlieUid).first() }
    assertTrue("Charlie must not see Alice's contacts-only status", charlieStatuses.none { it.id == statusId })
  }

  @Test
  fun observeChats_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val chatRepo = ChatRepository(firestore)

    var denied = false
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        chatRepo.observeChats("some_uid").first()
      }
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        denied = true
      }
    }
    assertTrue("Unauthenticated caller must receive PERMISSION_DENIED", denied)
  }

  private companion object {
    const val TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
