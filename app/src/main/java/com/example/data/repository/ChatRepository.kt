package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.UserProfile
import com.example.data.model.UsernameDirectoryEntry
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  fun deterministicChatId(uid1: String, uid2: String): String {
    return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
  }

  fun observeChats(uid: String): Flow<List<ChatRoom>> {
    val colRef = db.collection("chats")
    return colRef
      .whereArrayContains("participantIds", uid)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedWith(
            compareByDescending<ChatRoom> { it.isPinnedBy(uid) }
              .thenByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
          )
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  fun observeChat(chatId: String): Flow<ChatRoom?> {
    val docRef = db.collection("chats").document(chatId)
    return docRef
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.GET, docRef.path)
        throw error
      }
  }

  suspend fun getOrCreateDirectChat(
    myProfile: UserProfile,
    target: UsernameDirectoryEntry,
  ): Result<String> {
    val myUid = myProfile.userId
    val otherUid = target.userId
    if (myUid == otherUid) {
      return Result.failure(IllegalArgumentException("Cannot chat with yourself"))
    }
    val chatId = deterministicChatId(myUid, otherUid)
    val chatRef = db.collection("chats").document(chatId)
    return try {
      val existing = chatRef.get().await()
      if (!existing.exists()) {
        val sortedIds = if (myUid < otherUid) listOf(myUid, otherUid) else listOf(otherUid, myUid)
        val avatars = mutableMapOf<String, String>()
        myProfile.avatarUrl?.takeIf { it.isNotBlank() }?.let { avatars[myUid] = it }
        target.avatarUrl?.takeIf { it.isNotBlank() }?.let { avatars[otherUid] = it }

        val payload =
          mutableMapOf<String, Any>(
            "participantIds" to sortedIds,
            "participantNames" to mapOf(myUid to myProfile.displayName, otherUid to target.displayName),
            "participantUsernames" to mapOf(myUid to myProfile.username, otherUid to target.username),
            "lastMessageText" to "",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts" to mapOf(myUid to 0L, otherUid to 0L),
            "pinnedBy" to emptyList<String>(),
            "archivedBy" to emptyList<String>(),
            "mutedBy" to emptyList<String>(),
            "typingBy" to mapOf(myUid to false, otherUid to false),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        if (avatars.isNotEmpty()) {
          payload["participantAvatars"] = avatars
        }
        chatRef.set(payload).await()
      }
      Result.success(chatId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, chatRef.path)
      Result.failure(e)
    }
  }

  fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> {
    val colRef = db.collection("chats").document(chatId).collection("messages")
    return colRef
      .whereArrayContains("participantIds", uid)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.createdAt ?: Timestamp(0, 0) }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  suspend fun sendMessage(
    chatId: String,
    myUid: String,
    myName: String,
    participantIds: List<String>,
    otherUid: String,
    text: String,
    replyTo: ChatMessage? = null,
    isForwarded: Boolean = false,
  ): Result<String> {
    val cleanText = text.trim().take(4000)
    if (cleanText.isEmpty()) {
      return Result.failure(IllegalArgumentException("Empty message"))
    }
    val chatRef = db.collection("chats").document(chatId)
    val msgRef = chatRef.collection("messages").document()
    return try {
      val msgPayload =
        mutableMapOf<String, Any>(
          "chatId" to chatId,
          "senderId" to myUid,
          "senderName" to myName.take(60),
          "participantIds" to participantIds,
          "text" to cleanText,
          "type" to "text",
          "isForwarded" to isForwarded,
          "isEdited" to false,
          "isDeleted" to false,
          "reactions" to emptyMap<String, String>(),
          "readBy" to listOf(myUid),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (replyTo != null && replyTo.id.isNotBlank()) {
        msgPayload["replyToId"] = replyTo.id
        msgPayload["replyToText"] = replyTo.text.take(500)
        msgPayload["replyToSenderName"] = replyTo.senderName.take(60)
      }
      msgRef.set(msgPayload).await()

      chatRef
        .update(
          mapOf(
            "lastMessageText" to cleanText.take(1000),
            "lastMessageSenderId" to myUid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts.$otherUid" to FieldValue.increment(1L),
            "typingBy.$myUid" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      Result.success(msgRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun sendMediaMessage(
    chatId: String,
    myUid: String,
    myName: String,
    participantIds: List<String>,
    otherUid: String,
    type: String,
    mediaData: String,
    previewText: String,
    caption: String = "",
    fileName: String? = null,
    mimeType: String? = null,
    fileSizeBytes: Long? = null,
    mediaDurationMs: Long? = null,
    replyTo: ChatMessage? = null,
  ): Result<String> {
    if (type !in setOf("image", "voice", "file") || mediaData.isBlank()) {
      return Result.failure(IllegalArgumentException("Invalid media attachment"))
    }
    val chatRef = db.collection("chats").document(chatId)
    val msgRef = chatRef.collection("messages").document()
    return try {
      val msgPayload =
        mutableMapOf<String, Any>(
          "chatId" to chatId,
          "senderId" to myUid,
          "senderName" to myName.take(60),
          "participantIds" to participantIds,
          "text" to caption.trim().take(4000),
          "type" to type,
          "mediaData" to mediaData.take(519_000),
          "isForwarded" to false,
          "isEdited" to false,
          "isDeleted" to false,
          "reactions" to emptyMap<String, String>(),
          "readBy" to listOf(myUid),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!fileName.isNullOrBlank()) msgPayload["fileName"] = fileName.take(200)
      if (!mimeType.isNullOrBlank()) msgPayload["mimeType"] = mimeType.take(120)
      if (fileSizeBytes != null && fileSizeBytes >= 0L) {
        msgPayload["fileSizeBytes"] = fileSizeBytes.coerceAtMost(600_000L)
      }
      if (mediaDurationMs != null && mediaDurationMs >= 0L) {
        msgPayload["mediaDurationMs"] = mediaDurationMs.coerceAtMost(600_000L)
      }
      if (replyTo != null && replyTo.id.isNotBlank()) {
        msgPayload["replyToId"] = replyTo.id
        msgPayload["replyToText"] = replyTo.text.ifBlank { previewText }.take(500)
        msgPayload["replyToSenderName"] = replyTo.senderName.take(60)
      }
      msgRef.set(msgPayload).await()

      chatRef
        .update(
          mapOf(
            "lastMessageText" to previewText.take(1000),
            "lastMessageSenderId" to myUid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCounts.$otherUid" to FieldValue.increment(1L),
            "typingBy.$myUid" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      Result.success(msgRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun editMessage(chatId: String, messageId: String, newText: String): Result<Unit> {
    val clean = newText.trim().take(4000)
    if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty"))
    val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
    return try {
      msgRef
        .update(
          mapOf(
            "text" to clean,
            "isEdited" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun softDeleteMessage(chatId: String, messageId: String): Result<Unit> {
    val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
    return try {
      msgRef
        .update(
          mapOf(
            "text" to "",
            "type" to "deleted",
            "mediaData" to FieldValue.delete(),
            "fileName" to FieldValue.delete(),
            "mimeType" to FieldValue.delete(),
            "fileSizeBytes" to FieldValue.delete(),
            "mediaDurationMs" to FieldValue.delete(),
            "isDeleted" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun toggleReaction(
    chatId: String,
    messageId: String,
    uid: String,
    currentReactions: Map<String, String>,
    emoji: String,
  ): Result<Unit> {
    val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
    return try {
      val updated = currentReactions.toMutableMap()
      if (updated[uid] == emoji) {
        updated.remove(uid)
      } else {
        updated[uid] = emoji
      }
      msgRef
        .update(
          mapOf(
            "reactions" to updated,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun markChatAsRead(
    chatId: String,
    uid: String,
    unreadMessages: List<ChatMessage>,
  ) {
    val chatRef = db.collection("chats").document(chatId)
    try {
      val batch = db.batch()
      batch.update(
        chatRef,
        mapOf(
          "unreadCounts.$uid" to 0L,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      unreadMessages.take(200).forEach { msg ->
        if (msg.id.isNotBlank() && !msg.readBy.contains(uid)) {
          val msgRef = chatRef.collection("messages").document(msg.id)
          batch.update(
            msgRef,
            mapOf(
              "readBy" to FieldValue.arrayUnion(uid),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
    }
  }

  suspend fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
    val chatRef = db.collection("chats").document(chatId)
    try {
      chatRef
        .update(
          mapOf(
            "typingBy.$uid" to isTyping,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
    }
  }

  suspend fun togglePinChat(chatId: String, uid: String, currentlyPinned: Boolean): Result<Unit> {
    return updateChatArrayField(chatId, "pinnedBy", uid, add = !currentlyPinned)
  }

  suspend fun toggleArchiveChat(chatId: String, uid: String, currentlyArchived: Boolean): Result<Unit> {
    return updateChatArrayField(chatId, "archivedBy", uid, add = !currentlyArchived)
  }

  suspend fun toggleMuteChat(chatId: String, uid: String, currentlyMuted: Boolean): Result<Unit> {
    return updateChatArrayField(chatId, "mutedBy", uid, add = !currentlyMuted)
  }

  private suspend fun updateChatArrayField(
    chatId: String,
    field: String,
    uid: String,
    add: Boolean,
  ): Result<Unit> {
    val chatRef = db.collection("chats").document(chatId)
    return try {
      chatRef
        .update(
          mapOf(
            field to if (add) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
      Result.failure(e)
    }
  }
}
