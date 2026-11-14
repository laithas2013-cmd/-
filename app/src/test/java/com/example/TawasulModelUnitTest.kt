package com.example

import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ThemeMode
import com.example.core.media.formatDurationMs
import com.example.core.util.formatFileSize
import com.example.data.model.CallIceCandidate
import com.example.data.model.CallLog
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.GroupRoom
import com.example.data.model.UserStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class TawasulModelUnitTest {

  @Test
  fun chatRoom_participantHelpers_resolveCorrectly() {
    val room =
      ChatRoom(
        id = "alice_bob",
        participantIds = listOf("alice", "bob"),
        participantNames = mapOf("alice" to "Alice", "bob" to "Bob"),
        participantUsernames = mapOf("alice" to "alice_01", "bob" to "bob_02"),
        unreadCounts = mapOf("alice" to 3L, "bob" to 0L),
        pinnedBy = listOf("alice"),
        archivedBy = listOf("bob"),
        mutedBy = listOf("alice"),
        typingBy = mapOf("alice" to false, "bob" to true),
      )

    assertEquals("bob", room.otherParticipantId("alice"))
    assertEquals("Bob", room.otherDisplayName("alice"))
    assertEquals("bob_02", room.otherUsername("alice"))
    assertEquals(3L, room.unreadFor("alice"))
    assertTrue(room.isPinnedBy("alice"))
    assertFalse(room.isArchivedBy("alice"))
    assertTrue(room.isMutedBy("alice"))
    assertTrue(room.isOtherTyping("alice"))
    assertFalse(room.isOtherTyping("bob"))
  }

  @Test
  fun languageAndThemeEnums_parseSafely() {
    assertEquals(AppLanguage.AR, AppLanguage.fromCode(null))
    assertEquals(AppLanguage.AR, AppLanguage.fromCode("ar"))
    assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
    assertTrue(AppLanguage.AR.isRtl)
    assertFalse(AppLanguage.EN.isRtl)

    assertEquals(ThemeMode.SYSTEM, ThemeMode.fromValue(null))
    assertEquals(ThemeMode.LIGHT, ThemeMode.fromValue("light"))
    assertEquals(ThemeMode.DARK, ThemeMode.fromValue("dark"))
  }

  @Test
  fun statusGroupAndCallHelpers_behaveCorrectly() {
    val now = 1_700_000_000_000L
    val expired = UserStatus(expiresAt = Timestamp(Date(now - 10_000L)))
    val active = UserStatus(expiresAt = Timestamp(Date(now + 10_000L)))
    assertTrue(expired.isExpired(now))
    assertFalse(active.isExpired(now))

    val group = GroupRoom(adminIds = listOf("alice"), memberIds = listOf("alice", "bob"))
    assertTrue(group.isAdmin("alice"))
    assertFalse(group.isAdmin("bob"))

    val call =
      CallLog(
        callerId = "alice",
        callerName = "Alice",
        calleeId = "bob",
        calleeName = "Bob",
      )
    assertTrue(call.isOutgoing("alice"))
    assertFalse(call.isOutgoing("bob"))
    assertEquals("Bob", call.otherName("alice"))
    assertEquals("Alice", call.otherName("bob"))
  }

  @Test
  fun mediaFormatters_and_iceModels_formatAccurately() {
    assertEquals("0 B", formatFileSize(null))
    assertEquals("512 B", formatFileSize(512L))
    assertEquals("2.0 KB", formatFileSize(2048L))
    assertEquals("1.5 MB", formatFileSize(1_572_864L))

    assertEquals("0:00", formatDurationMs(null))
    assertEquals("0:05", formatDurationMs(5400L))
    assertEquals("1:05", formatDurationMs(65_000L))

    val voiceMsg =
      ChatMessage(
        id = "m1",
        type = "voice",
        mediaData = "data:audio/mp4;base64,AAAA",
        fileName = "voice.m4a",
        mimeType = "audio/mp4",
        fileSizeBytes = 1024L,
        mediaDurationMs = 4500L,
      )
    assertEquals("voice", voiceMsg.type)
    assertEquals(4500L, voiceMsg.mediaDurationMs)

    val candidate =
      CallIceCandidate(
        id = "c1",
        senderId = "alice",
        participantIds = listOf("alice", "bob"),
        candidate = "candidate:1 1 UDP 2122260223 192.0.2.1 54400 typ host",
        sdpMid = "0",
        sdpMLineIndex = 0,
      )
    assertEquals("0", candidate.sdpMid)
    assertEquals(2, candidate.participantIds.size)
  }
}
