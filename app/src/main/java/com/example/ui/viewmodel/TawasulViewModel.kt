package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.PreferencesManager
import com.example.core.i18n.ThemeMode
import com.example.data.firebase.unwrapFirestoreMessage
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.ContactEntry
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UsernameDirectoryEntry
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TawasulViewModel(
  private val userRepository: UserRepository,
  private val chatRepository: ChatRepository,
  private val preferencesManager: PreferencesManager,
  val currentUserId: String,
) : ViewModel() {

  private val _ensureProfileError = MutableStateFlow<String?>(null)
  val ensureProfileError = _ensureProfileError.asStateFlow()

  val myProfileState: StateFlow<UiState<UserProfile?>> =
    userRepository
      .observeMyProfile(currentUserId)
      .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
      .catch { error ->
        val fsError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing user profile", fsError ?: error)
        emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load profile")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  val chatsState: StateFlow<UiState<List<ChatRoom>>> =
    chatRepository
      .observeChats(currentUserId)
      .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
      .catch { error ->
        val fsError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing chats", fsError ?: error)
        emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load chats")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  val contactsState: StateFlow<UiState<List<ContactEntry>>> =
    userRepository
      .observeContacts(currentUserId)
      .map<List<ContactEntry>, UiState<List<ContactEntry>>> { UiState.Success(it) }
      .catch { error ->
        val fsError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing contacts", fsError ?: error)
        emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load contacts")))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  private val _activeChatId = MutableStateFlow<String?>(null)
  val activeChatId = _activeChatId.asStateFlow()

  val activeChat: StateFlow<ChatRoom?> =
    _activeChatId
      .flatMapLatest { chatId ->
        if (chatId.isNullOrBlank()) flowOf(null)
        else chatRepository.observeChat(chatId).catch { emit(null) }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null,
      )

  val activeOtherPresence: StateFlow<UsernameDirectoryEntry?> =
    activeChat
      .flatMapLatest { room ->
        val otherUsername = room?.otherUsername(currentUserId).orEmpty()
        if (otherUsername.isBlank()) flowOf(null)
        else userRepository.observeUsernameEntry(otherUsername).catch { emit(null) }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null,
      )

  val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> =
    _activeChatId
      .flatMapLatest { chatId ->
        if (chatId.isNullOrBlank()) {
          flowOf(UiState.Success(emptyList()))
        } else {
          chatRepository
            .observeMessages(chatId, currentUserId)
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
            .catch { error ->
              val fsError =
                generateSequence(error) { it.cause }
                  .filterIsInstance<FirebaseFirestoreException>()
                  .firstOrNull()
              if (fsError == null && error is CancellationException) throw error
              emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load messages")))
            }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading,
      )

  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  private val _showArchived = MutableStateFlow(false)
  val showArchived = _showArchived.asStateFlow()

  val filteredChats: StateFlow<List<ChatRoom>> =
    combine(chatsState, _searchQuery, _showArchived) { state, query, showArchived ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        val q = query.trim().lowercase()
        list.filter { room ->
          val archived = room.isArchivedBy(currentUserId)
          val archiveMatch = if (showArchived) archived else !archived
          val queryMatch =
            if (q.isEmpty()) true
            else {
              room.otherDisplayName(currentUserId).lowercase().contains(q) ||
                room.otherUsername(currentUserId).lowercase().contains(q.removePrefix("@")) ||
                room.lastMessageText.lowercase().contains(q)
            }
          archiveMatch && queryMatch
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
      )

  val archivedChatsCount: StateFlow<Int> =
    chatsState
      .map { state ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        list.count { it.isArchivedBy(currentUserId) }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = 0,
      )

  val chatPartnersOnline: StateFlow<Map<String, Boolean>> =
    chatsState
      .flatMapLatest { state ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        val usernames = list.map { it.otherUsername(currentUserId) }.filter { it.isNotBlank() }
        userRepository.observePresenceByUsernames(usernames)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyMap(),
      )

  // Username lookup state
  private val _lookupLoading = MutableStateFlow(false)
  val lookupLoading = _lookupLoading.asStateFlow()

  private val _lookupResult = MutableStateFlow<UsernameDirectoryEntry?>(null)
  val lookupResult = _lookupResult.asStateFlow()

  private val _lookupError = MutableStateFlow<String?>(null)
  val lookupError = _lookupError.asStateFlow()

  // Profile edit feedback
  private val _profileBusy = MutableStateFlow(false)
  val profileBusy = _profileBusy.asStateFlow()

  private val _usernameAvailability = MutableStateFlow<Boolean?>(null)
  val usernameAvailability = _usernameAvailability.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage = _snackbarMessage.asStateFlow()

  private var typingJob: Job? = null

  init {
    initializeUserProfile()
  }

  fun initializeUserProfile() {
    val firebaseUser = Firebase.auth.currentUser ?: return
    viewModelScope.launch {
      _ensureProfileError.value = null
      val result = userRepository.ensureUserProfile(firebaseUser)
      if (result.isFailure) {
        _ensureProfileError.value =
          result.exceptionOrNull()?.localizedMessage ?: "Failed to initialize account"
      } else {
        val profile = result.getOrNull()
        if (profile != null) {
          preferencesManager.setLanguage(AppLanguage.fromCode(profile.preferredLanguage))
          preferencesManager.setThemeMode(ThemeMode.fromValue(profile.themeMode))
        }
      }
    }
  }

  fun setOnlinePresence(online: Boolean) {
    val profile = (myProfileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      userRepository.setOnlineStatus(currentUserId, profile.username, online)
    }
  }

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun toggleShowArchived() {
    _showArchived.value = !_showArchived.value
  }

  fun openChat(chatId: String) {
    _activeChatId.value = chatId
  }

  fun closeChat() {
    val currentChat = _activeChatId.value
    if (!currentChat.isNullOrBlank()) {
      viewModelScope.launch { chatRepository.setTyping(currentChat, currentUserId, false) }
    }
    _activeChatId.value = null
  }

  fun clearLookup() {
    _lookupResult.value = null
    _lookupError.value = null
    _lookupLoading.value = false
  }

  fun searchUserByUsername(rawQuery: String, notFoundMsg: String, selfMsg: String) {
    val clean = userRepository.normalizeUsername(rawQuery)
    if (clean.isBlank()) return
    viewModelScope.launch {
      _lookupLoading.value = true
      _lookupError.value = null
      _lookupResult.value = null
      val res = userRepository.findUserByUsername(clean)
      _lookupLoading.value = false
      res
        .onSuccess { entry ->
          when {
            entry == null -> _lookupError.value = notFoundMsg
            entry.userId == currentUserId -> _lookupError.value = selfMsg
            else -> _lookupResult.value = entry
          }
        }
        .onFailure { err -> _lookupError.value = err.localizedMessage ?: notFoundMsg }
    }
  }

  fun startChatWith(entry: UsernameDirectoryEntry, onOpened: (String) -> Unit = {}) {
    val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res = chatRepository.getOrCreateDirectChat(myProfile, entry)
      res
        .onSuccess { chatId ->
          userRepository.addContact(currentUserId, entry)
          _activeChatId.value = chatId
          onOpened(chatId)
        }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage }
    }
  }

  fun startChatWithContact(contact: ContactEntry, onOpened: (String) -> Unit = {}) {
    val entry =
      UsernameDirectoryEntry(
        userId = contact.contactUid,
        username = contact.username,
        displayName = contact.displayName,
        avatarUrl = contact.avatarUrl,
      )
    startChatWith(entry, onOpened)
  }

  fun saveContact(entry: UsernameDirectoryEntry, savedText: String) {
    viewModelScope.launch {
      userRepository
        .addContact(currentUserId, entry)
        .onSuccess { _snackbarMessage.value = savedText }
        .onFailure { _snackbarMessage.value = it.localizedMessage }
    }
  }

  fun removeContact(contactUid: String) {
    viewModelScope.launch {
      userRepository.removeContact(currentUserId, contactUid).onFailure {
        _snackbarMessage.value = it.localizedMessage
      }
    }
  }

  fun sendMessage(
    text: String,
    replyTo: ChatMessage? = null,
    isForwarded: Boolean = false,
  ) {
    val room = activeChat.value ?: return
    val myProfile = (myProfileState.value as? UiState.Success)?.data
    val myName = myProfile?.displayName ?: room.participantNames[currentUserId] ?: "User"
    val otherUid = room.otherParticipantId(currentUserId)
    typingJob?.cancel()
    viewModelScope.launch {
      chatRepository
        .sendMessage(
          chatId = room.id,
          myUid = currentUserId,
          myName = myName,
          participantIds = room.participantIds,
          otherUid = otherUid,
          text = text,
          replyTo = replyTo,
          isForwarded = isForwarded,
        )
        .onFailure { _snackbarMessage.value = it.localizedMessage }
    }
  }

  fun sendMediaMessage(
    type: String,
    mediaData: String,
    previewText: String,
    caption: String = "",
    fileName: String? = null,
    mimeType: String? = null,
    fileSizeBytes: Long? = null,
    mediaDurationMs: Long? = null,
    replyTo: ChatMessage? = null,
  ) {
    val room = activeChat.value ?: return
    val myProfile = (myProfileState.value as? UiState.Success)?.data
    val myName = myProfile?.displayName ?: room.participantNames[currentUserId] ?: "User"
    val otherUid = room.otherParticipantId(currentUserId)
    viewModelScope.launch {
      chatRepository
        .sendMediaMessage(
          chatId = room.id,
          myUid = currentUserId,
          myName = myName,
          participantIds = room.participantIds,
          otherUid = otherUid,
          type = type,
          mediaData = mediaData,
          previewText = previewText,
          caption = caption,
          fileName = fileName,
          mimeType = mimeType,
          fileSizeBytes = fileSizeBytes,
          mediaDurationMs = mediaDurationMs,
          replyTo = replyTo,
        )
        .onFailure { _snackbarMessage.value = it.localizedMessage }
    }
  }

  fun forwardMessageToChat(targetRoom: ChatRoom, message: ChatMessage, forwardedNotice: String) {
    val myProfile = (myProfileState.value as? UiState.Success)?.data
    val myName = myProfile?.displayName ?: targetRoom.participantNames[currentUserId] ?: "User"
    val otherUid = targetRoom.otherParticipantId(currentUserId)
    viewModelScope.launch {
      val res =
        if (message.type != "text" && !message.mediaData.isNullOrBlank()) {
          chatRepository.sendMediaMessage(
            chatId = targetRoom.id,
            myUid = currentUserId,
            myName = myName,
            participantIds = targetRoom.participantIds,
            otherUid = otherUid,
            type = message.type,
            mediaData = message.mediaData,
            previewText = message.fileName ?: message.text.ifBlank { message.type },
            caption = message.text,
            fileName = message.fileName,
            mimeType = message.mimeType,
            fileSizeBytes = message.fileSizeBytes,
            mediaDurationMs = message.mediaDurationMs,
          )
        } else {
          chatRepository.sendMessage(
            chatId = targetRoom.id,
            myUid = currentUserId,
            myName = myName,
            participantIds = targetRoom.participantIds,
            otherUid = otherUid,
            text = message.text,
            replyTo = null,
            isForwarded = true,
          )
        }
      res
        .onSuccess { _snackbarMessage.value = forwardedNotice }
        .onFailure { _snackbarMessage.value = it.localizedMessage }
    }
  }

  fun editMessage(messageId: String, newText: String) {
    val chatId = _activeChatId.value ?: return
    viewModelScope.launch {
      chatRepository.editMessage(chatId, messageId, newText).onFailure {
        _snackbarMessage.value = it.localizedMessage
      }
    }
  }

  fun deleteMessage(messageId: String) {
    val chatId = _activeChatId.value ?: return
    viewModelScope.launch {
      chatRepository.softDeleteMessage(chatId, messageId).onFailure {
        _snackbarMessage.value = it.localizedMessage
      }
    }
  }

  fun toggleReaction(message: ChatMessage, emoji: String) {
    val chatId = _activeChatId.value ?: return
    viewModelScope.launch {
      chatRepository
        .toggleReaction(chatId, message.id, currentUserId, message.reactions, emoji)
        .onFailure { _snackbarMessage.value = it.localizedMessage }
    }
  }

  fun markMessagesRead(chatId: String, messages: List<ChatMessage>) {
    val unread = messages.filter { it.senderId != currentUserId && !it.readBy.contains(currentUserId) }
    val room = activeChat.value
    val hasUnreadCount = (room?.unreadFor(currentUserId) ?: 0L) > 0L
    if (unread.isEmpty() && !hasUnreadCount) return
    viewModelScope.launch { chatRepository.markChatAsRead(chatId, currentUserId, unread) }
  }

  fun onComposerTextChanged(text: String) {
    val chatId = _activeChatId.value ?: return
    typingJob?.cancel()
    if (text.isNotBlank()) {
      viewModelScope.launch { chatRepository.setTyping(chatId, currentUserId, true) }
      typingJob =
        viewModelScope.launch {
          delay(2500L)
          chatRepository.setTyping(chatId, currentUserId, false)
        }
    } else {
      viewModelScope.launch { chatRepository.setTyping(chatId, currentUserId, false) }
    }
  }

  fun togglePinChat(room: ChatRoom) {
    viewModelScope.launch {
      chatRepository.togglePinChat(room.id, currentUserId, room.isPinnedBy(currentUserId))
    }
  }

  fun toggleArchiveChat(room: ChatRoom) {
    viewModelScope.launch {
      chatRepository.toggleArchiveChat(room.id, currentUserId, room.isArchivedBy(currentUserId))
    }
  }

  fun toggleMuteChat(room: ChatRoom) {
    viewModelScope.launch {
      chatRepository.toggleMuteChat(room.id, currentUserId, room.isMutedBy(currentUserId))
    }
  }

  fun checkUsername(candidate: String) {
    val profile = (myProfileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res = userRepository.checkUsernameAvailable(candidate, profile.username)
      _usernameAvailability.value = res.getOrNull()
    }
  }

  fun resetUsernameAvailability() {
    _usernameAvailability.value = null
  }

  fun saveProfile(
    displayName: String,
    username: String,
    bio: String,
    avatarUrl: String?,
    language: AppLanguage,
    themeMode: ThemeMode,
    savedMessage: String,
    takenMessage: String,
    markOnboardingComplete: Boolean = true,
  ) {
    val profile = (myProfileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      _profileBusy.value = true
      val res =
        userRepository.updateProfile(
          uid = currentUserId,
          currentUsername = profile.username,
          newUsernameRaw = username,
          displayName = displayName,
          bio = bio,
          avatarUrl = avatarUrl,
          preferredLanguage = language.code,
          themeMode = themeMode.value,
          onboardingCompleted = markOnboardingComplete || profile.onboardingCompleted,
        )
      _profileBusy.value = false
      res
        .onSuccess {
          preferencesManager.setLanguage(language)
          preferencesManager.setThemeMode(themeMode)
          _snackbarMessage.value = savedMessage
        }
        .onFailure { err ->
          val msg =
            if (err.message?.contains("USERNAME_TAKEN") == true) takenMessage
            else err.localizedMessage ?: takenMessage
          _snackbarMessage.value = msg
        }
    }
  }

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch {
      preferencesManager.setLanguage(language)
      val profile = (myProfileState.value as? UiState.Success)?.data
      if (profile != null) {
        userRepository.updatePreferences(
          currentUserId,
          language.code,
          profile.themeMode,
        )
      }
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      preferencesManager.setThemeMode(mode)
      val profile = (myProfileState.value as? UiState.Success)?.data
      if (profile != null) {
        userRepository.updatePreferences(
          currentUserId,
          profile.preferredLanguage,
          mode.value,
        )
      }
    }
  }

  fun showSnackbar(message: String) {
    _snackbarMessage.value = message
  }

  fun consumeSnackbar() {
    _snackbarMessage.value = null
  }

  private companion object {
    const val TAG = "TawasulVM"
    const val STOP_TIMEOUT_MS = 5000L
  }
}
