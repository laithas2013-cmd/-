package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreMessage
import com.example.data.model.CallIceCandidate
import com.example.data.model.CallLog
import com.example.data.model.ContactEntry
import com.example.data.model.GroupMessage
import com.example.data.model.GroupRoom
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserStatus
import com.example.data.model.UsernameDirectoryEntry
import com.example.data.repository.CommunityRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(
  private val communityRepository: CommunityRepository,
  val currentUserId: String,
) : ViewModel() {

  val statusesState: StateFlow<UiState<List<UserStatus>>> =
    communityRepository
      .observeStatuses(currentUserId)
      .map<List<UserStatus>, UiState<List<UserStatus>>> { UiState.Success(it) }
      .catch { error ->
        val fsError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing statuses", fsError ?: error)
        emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load statuses")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val groupsState: StateFlow<UiState<List<GroupRoom>>> =
    communityRepository
      .observeGroups(currentUserId)
      .map<List<GroupRoom>, UiState<List<GroupRoom>>> { UiState.Success(it) }
      .catch { error ->
        val fsError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing groups", fsError ?: error)
        emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load groups")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  private val _activeGroupId = MutableStateFlow<String?>(null)
  val activeGroupId = _activeGroupId.asStateFlow()

  val activeGroup: StateFlow<GroupRoom?> =
    _activeGroupId
      .flatMapLatest { groupId ->
        if (groupId.isNullOrBlank()) flowOf(null)
        else communityRepository.observeGroup(groupId).catch { emit(null) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val activeGroupMessagesState: StateFlow<UiState<List<GroupMessage>>> =
    _activeGroupId
      .flatMapLatest { groupId ->
        if (groupId.isNullOrBlank()) {
          flowOf(UiState.Success(emptyList()))
        } else {
          communityRepository
            .observeGroupMessages(groupId, currentUserId)
            .map<List<GroupMessage>, UiState<List<GroupMessage>>> { UiState.Success(it) }
            .catch { error ->
              val fsError =
                generateSequence(error) { it.cause }
                  .filterIsInstance<FirebaseFirestoreException>()
                  .firstOrNull()
              if (fsError == null && error is CancellationException) throw error
              emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load group messages")))
            }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val callsState: StateFlow<UiState<List<CallLog>>> =
    communityRepository
      .observeCalls(currentUserId)
      .map<List<CallLog>, UiState<List<CallLog>>> { UiState.Success(it) }
      .catch { error ->
        val fsError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing calls", fsError ?: error)
        emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load calls")))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val activeLiveCall: StateFlow<CallLog?> =
    callsState
      .map { state ->
        val calls = (state as? UiState.Success)?.data ?: emptyList()
        calls.firstOrNull { it.status == "ringing" || it.status == "active" }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val activeCallIceCandidates: StateFlow<List<CallIceCandidate>> =
    activeLiveCall
      .flatMapLatest { call ->
        if (call == null) flowOf(emptyList())
        else communityRepository.observeIceCandidates(call.id, currentUserId).catch { emit(emptyList()) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  private val _busy = MutableStateFlow(false)
  val busy = _busy.asStateFlow()

  private val _snackbar = MutableStateFlow<String?>(null)
  val snackbar = _snackbar.asStateFlow()

  fun showSnackbar(message: String) {
    _snackbar.value = message
  }

  fun consumeSnackbar() {
    _snackbar.value = null
  }

  // --- Statuses ---
  fun publishStatus(
    author: UserProfile,
    contacts: List<ContactEntry>,
    type: String,
    text: String,
    imageData: String?,
    backgroundHex: String,
    privacy: String,
    onPublished: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _busy.value = true
      val res =
        communityRepository.publishStatus(
          author = author,
          contactUids = contacts.map { it.contactUid },
          type = type,
          text = text,
          imageData = imageData,
          backgroundHex = backgroundHex,
          privacy = privacy,
        )
      _busy.value = false
      res.onSuccess { onPublished() }.onFailure { _snackbar.value = it.localizedMessage }
    }
  }

  fun markStatusViewed(status: UserStatus) {
    if (status.viewedBy.contains(currentUserId)) return
    viewModelScope.launch {
      communityRepository.markStatusViewed(status.id, currentUserId)
    }
  }

  fun deleteStatus(statusId: String) {
    viewModelScope.launch {
      communityRepository.deleteStatus(statusId).onFailure { _snackbar.value = it.localizedMessage }
    }
  }

  // --- Groups ---
  fun openGroup(groupId: String) {
    _activeGroupId.value = groupId
  }

  fun closeGroup() {
    _activeGroupId.value = null
  }

  fun createGroup(
    creator: UserProfile,
    name: String,
    description: String,
    selectedContacts: List<ContactEntry>,
    successMsg: String,
    onCreated: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _busy.value = true
      val members = selectedContacts.map { it.contactUid to it.displayName }
      val res = communityRepository.createGroup(creator, name, description, members)
      _busy.value = false
      res
        .onSuccess { groupId ->
          _snackbar.value = successMsg
          _activeGroupId.value = groupId
          onCreated()
        }
        .onFailure { _snackbar.value = it.localizedMessage }
    }
  }

  fun sendGroupMessage(myProfile: UserProfile, text: String) {
    val group = activeGroup.value ?: return
    viewModelScope.launch {
      communityRepository
        .sendGroupMessage(group, currentUserId, myProfile.displayName, text)
        .onFailure { _snackbar.value = it.localizedMessage }
    }
  }

  fun sendGroupMediaMessage(
    myProfile: UserProfile,
    type: String,
    mediaData: String,
    previewText: String,
    caption: String = "",
    fileName: String? = null,
    mimeType: String? = null,
    fileSizeBytes: Long? = null,
    mediaDurationMs: Long? = null,
  ) {
    val group = activeGroup.value ?: return
    viewModelScope.launch {
      communityRepository
        .sendGroupMediaMessage(
          group = group,
          senderId = currentUserId,
          senderName = myProfile.displayName,
          type = type,
          mediaData = mediaData,
          previewText = previewText,
          caption = caption,
          fileName = fileName,
          mimeType = mimeType,
          fileSizeBytes = fileSizeBytes,
          mediaDurationMs = mediaDurationMs,
        )
        .onFailure { _snackbar.value = it.localizedMessage }
    }
  }

  fun addMemberToActiveGroup(entry: UsernameDirectoryEntry, addedMsg: String) {
    val group = activeGroup.value ?: return
    viewModelScope.launch {
      communityRepository
        .addMemberToGroup(group, entry)
        .onSuccess { _snackbar.value = addedMsg }
        .onFailure { _snackbar.value = it.localizedMessage }
    }
  }

  fun joinGroupByInviteCode(
    code: String,
    myProfile: UserProfile,
    joinedMsg: String,
    invalidMsg: String,
    onJoined: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _busy.value = true
      val res = communityRepository.joinGroupByInviteCode(code, myProfile)
      _busy.value = false
      res
        .onSuccess { groupId ->
          _snackbar.value = joinedMsg
          _activeGroupId.value = groupId
          onJoined()
        }
        .onFailure { err ->
          val msg =
            if (err.message == "INVALID_CODE" || err.message == "INVITE_NOT_FOUND") invalidMsg
            else err.localizedMessage ?: invalidMsg
          _snackbar.value = msg
        }
    }
  }

  // --- Calls ---
  fun startCall(
    caller: UserProfile,
    calleeUid: String,
    calleeName: String,
    calleeAvatar: String?,
    callType: String,
  ) {
    viewModelScope.launch {
      communityRepository
        .startCall(caller, calleeUid, calleeName, calleeAvatar, callType)
        .onFailure { _snackbar.value = it.localizedMessage }
    }
  }

  fun acceptCall(call: CallLog) {
    viewModelScope.launch {
      communityRepository.updateCallStatus(call.id, "active", 0L).onFailure {
        _snackbar.value = it.localizedMessage
      }
    }
  }

  fun declineCall(call: CallLog) {
    viewModelScope.launch {
      communityRepository.updateCallStatus(call.id, "declined", 0L).onFailure {
        _snackbar.value = it.localizedMessage
      }
    }
  }

  fun endCall(call: CallLog, elapsedSeconds: Long) {
    val finalStatus =
      if (call.status == "ringing" && call.callerId == currentUserId) "missed" else "ended"
    viewModelScope.launch {
      communityRepository.updateCallStatus(call.id, finalStatus, elapsedSeconds).onFailure {
        _snackbar.value = it.localizedMessage
      }
    }
  }

  fun publishOfferSdp(callId: String, offerSdp: String) {
    viewModelScope.launch {
      communityRepository.updateCallOffer(callId, offerSdp)
    }
  }

  fun publishAnswerSdp(callId: String, answerSdp: String) {
    viewModelScope.launch {
      communityRepository.updateCallAnswer(callId, answerSdp)
    }
  }

  fun sendIceCandidate(
    call: CallLog,
    candidate: String,
    sdpMid: String?,
    sdpMLineIndex: Int,
  ) {
    viewModelScope.launch {
      communityRepository.addIceCandidate(
        callId = call.id,
        senderId = currentUserId,
        participantIds = call.participantIds,
        candidate = candidate,
        sdpMid = sdpMid,
        sdpMLineIndex = sdpMLineIndex,
      )
    }
  }

  private companion object {
    const val TAG = "CommunityVM"
    const val STOP_TIMEOUT_MS = 5000L
  }
}
