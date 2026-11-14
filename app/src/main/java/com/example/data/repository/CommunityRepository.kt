package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CallIceCandidate
import com.example.data.model.CallLog
import com.example.data.model.GroupInvite
import com.example.data.model.GroupMessage
import com.example.data.model.GroupRoom
import com.example.data.model.UserProfile
import com.example.data.model.UserStatus
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
import java.util.Date
import java.util.UUID

class CommunityRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  // --- Statuses (24-hour stories) ---

  fun observeStatuses(uid: String): Flow<List<UserStatus>> {
    val colRef = db.collection("statuses")
    return colRef
      .whereArrayContains("visibleToUids", uid)
      .snapshots()
      .map { snapshot ->
        val now = System.currentTimeMillis()
        snapshot
          .toObjects(UserStatus::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .filter { !it.isExpired(now) }
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  suspend fun publishStatus(
    author: UserProfile,
    contactUids: List<String>,
    type: String,
    text: String,
    imageData: String?,
    backgroundHex: String,
    privacy: String,
  ): Result<String> {
    val cleanType = if (type == "image" && !imageData.isNullOrBlank()) "image" else "text"
    val cleanText = text.trim().take(500)
    if (cleanType == "text" && cleanText.isBlank()) {
      return Result.failure(IllegalArgumentException("Status text required"))
    }
    val visibleTo =
      if (privacy == "only_me") listOf(author.userId)
      else (listOf(author.userId) + contactUids).distinct().take(100)

    val statusId = "st_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("statuses").document(statusId)
    val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L))

    return try {
      val payload =
        mutableMapOf<String, Any>(
          "authorId" to author.userId,
          "authorName" to author.displayName,
          "authorUsername" to author.username,
          "type" to cleanType,
          "text" to cleanText,
          "backgroundHex" to backgroundHex.take(16),
          "privacy" to if (privacy == "only_me") "only_me" else "contacts",
          "visibleToUids" to visibleTo,
          "viewedBy" to listOf(author.userId),
          "expiresAt" to expiresAt,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!author.avatarUrl.isNullOrBlank()) {
        payload["authorAvatar"] = author.avatarUrl.take(29000)
      }
      if (cleanType == "image" && !imageData.isNullOrBlank()) {
        payload["imageData"] = imageData.take(29000)
      }
      docRef.set(payload).await()
      Result.success(statusId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun markStatusViewed(statusId: String, uid: String): Result<Unit> {
    val docRef = db.collection("statuses").document(statusId)
    return try {
      docRef
        .update(
          mapOf(
            "viewedBy" to FieldValue.arrayUnion(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteStatus(statusId: String): Result<Unit> {
    val docRef = db.collection("statuses").document(statusId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Groups & Invites ---

  fun observeGroups(uid: String): Flow<List<GroupRoom>> {
    val colRef = db.collection("groups")
    return colRef
      .whereArrayContains("memberIds", uid)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(GroupRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.lastMessageAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  fun observeGroup(groupId: String): Flow<GroupRoom?> {
    val docRef = db.collection("groups").document(groupId)
    return docRef
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(GroupRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

  fun observeGroupMessages(groupId: String, uid: String): Flow<List<GroupMessage>> {
    val colRef = db.collection("groups").document(groupId).collection("messages")
    return colRef
      .whereArrayContains("memberIds", uid)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(GroupMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  suspend fun createGroup(
    creator: UserProfile,
    name: String,
    description: String,
    initialMembers: List<Pair<String, String>>,
  ): Result<String> {
    val cleanName = name.trim().take(80)
    if (cleanName.isBlank()) {
      return Result.failure(IllegalArgumentException("Group name required"))
    }
    val cleanDesc = description.trim().take(200)
    val groupId = "grp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val inviteCode = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()

    val memberMap = linkedMapOf<String, String>()
    memberMap[creator.userId] = creator.displayName
    initialMembers.forEach { (uid, displayName) ->
      if (uid.isNotBlank() && displayName.isNotBlank()) {
        memberMap[uid] = displayName.take(60)
      }
    }
    val memberIds = memberMap.keys.toList().take(100)

    val groupRef = db.collection("groups").document(groupId)
    val inviteRef = db.collection("group_invites").document(inviteCode)

    return try {
      val batch = db.batch()
      batch.set(
        groupRef,
        mapOf(
          "name" to cleanName,
          "description" to cleanDesc,
          "creatorId" to creator.userId,
          "adminIds" to listOf(creator.userId),
          "memberIds" to memberIds,
          "memberNames" to memberMap,
          "inviteCode" to inviteCode,
          "lastMessageText" to "",
          "lastMessageAt" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        inviteRef,
        mapOf(
          "inviteCode" to inviteCode,
          "groupId" to groupId,
          "groupName" to cleanName,
          "creatorId" to creator.userId,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(groupId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, groupRef.path)
      Result.failure(e)
    }
  }

  suspend fun sendGroupMessage(
    group: GroupRoom,
    senderId: String,
    senderName: String,
    text: String,
  ): Result<String> {
    val cleanText = text.trim().take(4000)
    if (cleanText.isBlank()) {
      return Result.failure(IllegalArgumentException("Message cannot be empty"))
    }
    val groupRef = db.collection("groups").document(group.id)
    val msgId = "gm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val msgRef = groupRef.collection("messages").document(msgId)

    return try {
      // Read latest memberIds so the security rule exact match always aligns
      val freshMembers =
        (groupRef.get().await().get("memberIds") as? List<*>)
          ?.filterIsInstance<String>()
          ?.ifEmpty { group.memberIds }
          ?: group.memberIds

      val batch = db.batch()
      batch.set(
        msgRef,
        mapOf(
          "groupId" to group.id,
          "senderId" to senderId,
          "senderName" to senderName.take(60),
          "memberIds" to freshMembers,
          "text" to cleanText,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        groupRef,
        mapOf(
          "lastMessageText" to cleanText.take(1000),
          "lastMessageSenderId" to senderId,
          "lastMessageAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(msgId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun sendGroupMediaMessage(
    group: GroupRoom,
    senderId: String,
    senderName: String,
    type: String,
    mediaData: String,
    previewText: String,
    caption: String = "",
    fileName: String? = null,
    mimeType: String? = null,
    fileSizeBytes: Long? = null,
    mediaDurationMs: Long? = null,
  ): Result<String> {
    if (type !in setOf("image", "voice", "file") || mediaData.isBlank()) {
      return Result.failure(IllegalArgumentException("Invalid media attachment"))
    }
    val groupRef = db.collection("groups").document(group.id)
    val msgId = "gm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val msgRef = groupRef.collection("messages").document(msgId)

    return try {
      val freshMembers =
        (groupRef.get().await().get("memberIds") as? List<*>)
          ?.filterIsInstance<String>()
          ?.ifEmpty { group.memberIds }
          ?: group.memberIds

      val payload =
        mutableMapOf<String, Any>(
          "groupId" to group.id,
          "senderId" to senderId,
          "senderName" to senderName.take(60),
          "memberIds" to freshMembers,
          "text" to caption.trim().take(4000),
          "type" to type,
          "mediaData" to mediaData.take(519_000),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!fileName.isNullOrBlank()) payload["fileName"] = fileName.take(200)
      if (!mimeType.isNullOrBlank()) payload["mimeType"] = mimeType.take(120)
      if (fileSizeBytes != null && fileSizeBytes >= 0L) {
        payload["fileSizeBytes"] = fileSizeBytes.coerceAtMost(600_000L)
      }
      if (mediaDurationMs != null && mediaDurationMs >= 0L) {
        payload["mediaDurationMs"] = mediaDurationMs.coerceAtMost(600_000L)
      }

      val batch = db.batch()
      batch.set(msgRef, payload)
      batch.update(
        groupRef,
        mapOf(
          "lastMessageText" to previewText.take(1000),
          "lastMessageSenderId" to senderId,
          "lastMessageAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(msgId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun addMemberToGroup(
    group: GroupRoom,
    entry: UsernameDirectoryEntry,
  ): Result<Unit> {
    if (group.memberIds.contains(entry.userId)) return Result.success(Unit)
    val groupRef = db.collection("groups").document(group.id)
    return try {
      val updatedMemberIds = (group.memberIds + entry.userId).distinct().take(100)
      val updatedMemberNames = group.memberNames.toMutableMap().apply {
        put(entry.userId, entry.displayName.take(60))
      }
      groupRef
        .update(
          mapOf(
            "memberIds" to updatedMemberIds,
            "memberNames" to updatedMemberNames,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
      Result.failure(e)
    }
  }

  suspend fun joinGroupByInviteCode(
    rawCode: String,
    myProfile: UserProfile,
  ): Result<String> {
    val cleanCode = rawCode.trim().uppercase()
    if (cleanCode.length < 4) {
      return Result.failure(IllegalArgumentException("INVALID_CODE"))
    }
    val inviteRef = db.collection("group_invites").document(cleanCode)
    return try {
      val inviteSnap = inviteRef.get().await()
      val invite =
        if (inviteSnap.exists()) {
          inviteSnap.toObject(GroupInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else null
      if (invite == null || invite.groupId.isBlank()) {
        return Result.failure(IllegalArgumentException("INVITE_NOT_FOUND"))
      }

      val groupRef = db.collection("groups").document(invite.groupId)
      val alreadyMember =
        try {
          groupRef.get().await().exists()
        } catch (_: Exception) {
          false
        }
      if (!alreadyMember) {
        groupRef
          .update(
            mapOf(
              "memberIds" to FieldValue.arrayUnion(myProfile.userId),
              "memberNames.${myProfile.userId}" to myProfile.displayName.take(60),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(invite.groupId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, inviteRef.path)
      Result.failure(e)
    }
  }

  // --- Calls Signaling & History ---

  fun observeCalls(uid: String): Flow<List<CallLog>> {
    val colRef = db.collection("calls")
    return colRef
      .whereArrayContains("participantIds", uid)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(CallLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }

  suspend fun startCall(
    caller: UserProfile,
    calleeUid: String,
    calleeName: String,
    calleeAvatar: String?,
    callType: String,
  ): Result<CallLog> {
    if (calleeUid == caller.userId) {
      return Result.failure(IllegalArgumentException("Cannot call self"))
    }
    val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("calls").document(callId)
    val cleanType = if (callType == "video") "video" else "audio"

    return try {
      val payload =
        mutableMapOf<String, Any>(
          "callerId" to caller.userId,
          "callerName" to caller.displayName.take(60),
          "calleeId" to calleeUid,
          "calleeName" to calleeName.take(60),
          "participantIds" to listOf(caller.userId, calleeUid),
          "callType" to cleanType,
          "status" to "ringing",
          "durationSeconds" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!caller.avatarUrl.isNullOrBlank()) {
        payload["callerAvatar"] = caller.avatarUrl.take(29000)
      }
      if (!calleeAvatar.isNullOrBlank()) {
        payload["calleeAvatar"] = calleeAvatar.take(29000)
      }
      docRef.set(payload).await()
      Result.success(
        CallLog(
          id = callId,
          callerId = caller.userId,
          callerName = caller.displayName,
          callerAvatar = caller.avatarUrl,
          calleeId = calleeUid,
          calleeName = calleeName,
          calleeAvatar = calleeAvatar,
          participantIds = listOf(caller.userId, calleeUid),
          callType = cleanType,
          status = "ringing",
          durationSeconds = 0L,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateCallStatus(
    callId: String,
    status: String,
    durationSeconds: Long = 0L,
  ): Result<Unit> {
    val docRef = db.collection("calls").document(callId)
    return try {
      docRef
        .update(
          mapOf(
            "status" to status,
            "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateCallOffer(callId: String, offerSdp: String): Result<Unit> {
    val docRef = db.collection("calls").document(callId)
    return try {
      docRef
        .update(
          mapOf(
            "offerSdp" to offerSdp.take(24_500),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateCallAnswer(callId: String, answerSdp: String): Result<Unit> {
    val docRef = db.collection("calls").document(callId)
    return try {
      docRef
        .update(
          mapOf(
            "answerSdp" to answerSdp.take(24_500),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun addIceCandidate(
    callId: String,
    senderId: String,
    participantIds: List<String>,
    candidate: String,
    sdpMid: String?,
    sdpMLineIndex: Int,
  ): Result<Unit> {
    val colRef = db.collection("calls").document(callId).collection("candidates")
    val docId = "ice_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = colRef.document(docId)
    return try {
      val payload =
        mutableMapOf<String, Any>(
          "callId" to callId,
          "senderId" to senderId,
          "participantIds" to participantIds,
          "candidate" to candidate.take(1950),
          "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 64),
          "createdAt" to FieldValue.serverTimestamp(),
        )
      if (!sdpMid.isNullOrBlank()) {
        payload["sdpMid"] = sdpMid.take(64)
      }
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  fun observeIceCandidates(callId: String, uid: String): Flow<List<CallIceCandidate>> {
    val colRef = db.collection("calls").document(callId).collection("candidates")
    return colRef
      .whereArrayContains("participantIds", uid)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          CallIceCandidate::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        if (error is CancellationException && error.cause == null) throw error
        handleFirestoreError(error, OperationType.LIST, colRef.path)
        throw error
      }
  }
}
