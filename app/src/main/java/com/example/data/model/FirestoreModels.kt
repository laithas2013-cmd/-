package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val username: String = "",
  val displayName: String = "",
  val bio: String? = null,
  val avatarUrl: String? = null,
  val onboardingCompleted: Boolean = false,
  val preferredLanguage: String = "ar",
  val themeMode: String = "system",
  @get:PropertyName("isOnline") @set:PropertyName("isOnline") var isOnline: Boolean = false,
  @ServerTimestamp val lastSeen: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UsernameDirectoryEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val username: String = "",
  val displayName: String = "",
  val bio: String? = null,
  val avatarUrl: String? = null,
  @get:PropertyName("isOnline") @set:PropertyName("isOnline") var isOnline: Boolean = false,
  @ServerTimestamp val lastSeen: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ContactEntry(
  @DocumentId val id: String = "",
  val contactUid: String = "",
  val username: String = "",
  val displayName: String = "",
  val avatarUrl: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatRoom(
  @DocumentId val id: String = "",
  val participantIds: List<String> = emptyList(),
  val participantNames: Map<String, String> = emptyMap(),
  val participantUsernames: Map<String, String> = emptyMap(),
  val participantAvatars: Map<String, String> = emptyMap(),
  val lastMessageText: String = "",
  val lastMessageSenderId: String? = null,
  @ServerTimestamp val lastMessageAt: Timestamp? = null,
  val unreadCounts: Map<String, Long> = emptyMap(),
  val pinnedBy: List<String> = emptyList(),
  val archivedBy: List<String> = emptyList(),
  val mutedBy: List<String> = emptyList(),
  val typingBy: Map<String, Boolean> = emptyMap(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun otherParticipantId(myUid: String): String =
    participantIds.firstOrNull { it != myUid } ?: myUid

  fun otherDisplayName(myUid: String): String =
    participantNames[otherParticipantId(myUid)] ?: "User"

  fun otherUsername(myUid: String): String =
    participantUsernames[otherParticipantId(myUid)] ?: ""

  fun otherAvatarUrl(myUid: String): String? =
    participantAvatars[otherParticipantId(myUid)]?.takeIf { it.isNotBlank() }

  fun unreadFor(myUid: String): Long = unreadCounts[myUid] ?: 0L

  fun isPinnedBy(myUid: String): Boolean = pinnedBy.contains(myUid)

  fun isArchivedBy(myUid: String): Boolean = archivedBy.contains(myUid)

  fun isMutedBy(myUid: String): Boolean = mutedBy.contains(myUid)

  fun isOtherTyping(myUid: String): Boolean = typingBy[otherParticipantId(myUid)] == true
}

@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val chatId: String = "",
  val senderId: String = "",
  val senderName: String = "",
  val participantIds: List<String> = emptyList(),
  val text: String = "",
  val type: String = "text",
  val mediaData: String? = null,
  val fileName: String? = null,
  val mimeType: String? = null,
  val fileSizeBytes: Long? = null,
  val mediaDurationMs: Long? = null,
  val replyToId: String? = null,
  val replyToText: String? = null,
  val replyToSenderName: String? = null,
  @get:PropertyName("isForwarded") @set:PropertyName("isForwarded") var isForwarded: Boolean = false,
  @get:PropertyName("isEdited") @set:PropertyName("isEdited") var isEdited: Boolean = false,
  @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
  val reactions: Map<String, String> = emptyMap(),
  val readBy: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UserStatus(
  @DocumentId val id: String = "",
  val authorId: String = "",
  val authorName: String = "",
  val authorUsername: String = "",
  val authorAvatar: String? = null,
  val type: String = "text",
  val text: String = "",
  val imageData: String? = null,
  val backgroundHex: String = "#F5B700",
  val privacy: String = "contacts",
  val visibleToUids: List<String> = emptyList(),
  val viewedBy: List<String> = emptyList(),
  val expiresAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val exp = expiresAt?.toDate()?.time ?: return false
    return exp <= nowMillis
  }
}

@IgnoreExtraProperties
data class GroupRoom(
  @DocumentId val id: String = "",
  val name: String = "",
  val description: String? = null,
  val creatorId: String = "",
  val adminIds: List<String> = emptyList(),
  val memberIds: List<String> = emptyList(),
  val memberNames: Map<String, String> = emptyMap(),
  val inviteCode: String = "",
  val lastMessageText: String = "",
  val lastMessageSenderId: String? = null,
  @ServerTimestamp val lastMessageAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isAdmin(uid: String): Boolean = adminIds.contains(uid)
}

@IgnoreExtraProperties
data class GroupInvite(
  @DocumentId val id: String = "",
  val inviteCode: String = "",
  val groupId: String = "",
  val groupName: String = "",
  val creatorId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class GroupMessage(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val senderId: String = "",
  val senderName: String = "",
  val memberIds: List<String> = emptyList(),
  val text: String = "",
  val type: String = "text",
  val mediaData: String? = null,
  val fileName: String? = null,
  val mimeType: String? = null,
  val fileSizeBytes: Long? = null,
  val mediaDurationMs: Long? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CallLog(
  @DocumentId val id: String = "",
  val callerId: String = "",
  val callerName: String = "",
  val callerAvatar: String? = null,
  val calleeId: String = "",
  val calleeName: String = "",
  val calleeAvatar: String? = null,
  val participantIds: List<String> = emptyList(),
  val callType: String = "audio",
  val status: String = "ringing",
  val durationSeconds: Long = 0L,
  val offerSdp: String? = null,
  val answerSdp: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun otherName(myUid: String): String =
    if (callerId == myUid) calleeName else callerName

  fun otherAvatar(myUid: String): String? =
    if (callerId == myUid) calleeAvatar else callerAvatar

  fun isOutgoing(myUid: String): Boolean = callerId == myUid
}

@IgnoreExtraProperties
data class CallIceCandidate(
  @DocumentId val id: String = "",
  val callId: String = "",
  val senderId: String = "",
  val participantIds: List<String> = emptyList(),
  val candidate: String = "",
  val sdpMid: String? = null,
  val sdpMLineIndex: Int = 0,
  @ServerTimestamp val createdAt: Timestamp? = null,
)
