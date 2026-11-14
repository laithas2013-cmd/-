package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.AppStrings
import com.example.core.i18n.ArabicStrings
import com.example.core.i18n.EnglishStrings
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.PreferencesManager
import com.example.core.i18n.ThemeMode
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOutUser
import com.example.data.model.UiState
import com.example.data.repository.ChatRepository
import com.example.data.repository.CommunityRepository
import com.example.data.repository.UserRepository
import com.example.notifications.AppVisibilityState
import com.example.notifications.CallForegroundService
import com.example.notifications.TawasulBackgroundSyncService
import com.example.notifications.TawasulNotificationHelper
import com.example.ui.components.TawasulTopHeader
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallsTab
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListPane
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.GroupDetailScreen
import com.example.ui.screens.GroupsTab
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StatusesTab
import com.example.ui.theme.TawasulGold
import com.example.ui.theme.TawasulGoldDeep
import com.example.ui.theme.TawasulOnGold
import com.example.ui.theme.TawasulPlusTheme
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.TawasulViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class MainTab {
  CHATS,
  STATUSES,
  CALLS,
  GROUPS,
}

private enum class SecondaryOverlay {
  NONE,
  CONTACTS,
  PROFILE,
}

class MainActivity : ComponentActivity() {
  private val latestIntentState = mutableStateOf<Intent?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    latestIntentState.value = intent
    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current
      val preferencesManager = remember(context) { PreferencesManager(context.applicationContext) }
      val language by
        preferencesManager.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.AR)
      val themeMode by
        preferencesManager.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
      val scope = rememberCoroutineScope()

      val darkTheme =
        when (themeMode) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }
      val strings = if (language == AppLanguage.AR) ArabicStrings else EnglishStrings
      val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

      CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection,
      ) {
        TawasulPlusTheme(
          darkTheme = darkTheme,
          isArabic = language == AppLanguage.AR,
        ) {
          TawasulAppRoot(
            preferencesManager = preferencesManager,
            currentLanguage = language,
            currentThemeMode = themeMode,
            isDarkTheme = darkTheme,
            pendingIntent = latestIntentState.value,
            onConsumeIntent = { latestIntentState.value = null },
            onLanguageChanged = { lang -> scope.launch { preferencesManager.setLanguage(lang) } },
            onThemeChanged = { mode -> scope.launch { preferencesManager.setThemeMode(mode) } },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    latestIntentState.value = intent
  }
}

@Composable
private fun TawasulAppRoot(
  preferencesManager: PreferencesManager,
  currentLanguage: AppLanguage,
  currentThemeMode: ThemeMode,
  isDarkTheme: Boolean,
  pendingIntent: Intent?,
  onConsumeIntent: () -> Unit,
  onLanguageChanged: (AppLanguage) -> Unit,
  onThemeChanged: (ThemeMode) -> Unit,
) {
  val context = LocalContext.current
  var showSplash by rememberSaveable { mutableStateOf(true) }
  val currentUser by
    Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser == null) {
      val credentialManager = CredentialManager.create(context)
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = this,
      )
    }
  }

  Crossfade(targetState = showSplash, label = "splash_transition") { splash ->
    if (splash) {
      SplashScreen(onSplashFinished = { showSplash = false })
    } else {
      val user = currentUser
      if (user == null) {
        AuthScreen(
          currentLanguage = currentLanguage,
          isDarkTheme = isDarkTheme,
          onLanguageSelected = onLanguageChanged,
          onToggleTheme = onThemeChanged,
          onAuthSuccess = {},
        )
      } else {
        key(user.uid) {
          TawasulAuthenticatedShell(
            currentUserId = user.uid,
            preferencesManager = preferencesManager,
            currentLanguage = currentLanguage,
            currentThemeMode = currentThemeMode,
            pendingIntent = pendingIntent,
            onConsumeIntent = onConsumeIntent,
          )
        }
      }
    }
  }
}

@Composable
private fun TawasulAuthenticatedShell(
  currentUserId: String,
  preferencesManager: PreferencesManager,
  currentLanguage: AppLanguage,
  currentThemeMode: ThemeMode,
  pendingIntent: Intent?,
  onConsumeIntent: () -> Unit,
) {
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: TawasulViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            TawasulViewModel(
              userRepository = UserRepository(db),
              chatRepository = ChatRepository(db),
              preferencesManager = preferencesManager,
              currentUserId = currentUserId,
            )
          }
        },
    )

  val communityViewModel: CommunityViewModel =
    viewModel(
      key = "community_$currentUserId",
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            CommunityViewModel(
              communityRepository = CommunityRepository(db),
              currentUserId = currentUserId,
            )
          }
        },
    )

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, currentUserId) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          AppVisibilityState.isForeground = true
          viewModel.setOnlinePresence(true)
        }
        Lifecycle.Event.ON_STOP -> {
          AppVisibilityState.isForeground = false
          viewModel.setOnlinePresence(false)
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      AppVisibilityState.isForeground = false
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val myProfileState by viewModel.myProfileState.collectAsStateWithLifecycle()
  val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
  val filteredChats by viewModel.filteredChats.collectAsStateWithLifecycle()
  val archivedCount by viewModel.archivedChatsCount.collectAsStateWithLifecycle()
  val partnersOnline by viewModel.chatPartnersOnline.collectAsStateWithLifecycle()
  val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
  val activeChatId by viewModel.activeChatId.collectAsStateWithLifecycle()
  val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
  val activeOtherPresence by viewModel.activeOtherPresence.collectAsStateWithLifecycle()
  val activeMessagesState by viewModel.activeMessagesState.collectAsStateWithLifecycle()
  val lookupLoading by viewModel.lookupLoading.collectAsStateWithLifecycle()
  val lookupResult by viewModel.lookupResult.collectAsStateWithLifecycle()
  val lookupError by viewModel.lookupError.collectAsStateWithLifecycle()
  val profileBusy by viewModel.profileBusy.collectAsStateWithLifecycle()
  val usernameAvailability by viewModel.usernameAvailability.collectAsStateWithLifecycle()
  val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
  val ensureError by viewModel.ensureProfileError.collectAsStateWithLifecycle()

  val statusesState by communityViewModel.statusesState.collectAsStateWithLifecycle()
  val groupsState by communityViewModel.groupsState.collectAsStateWithLifecycle()
  val activeGroupId by communityViewModel.activeGroupId.collectAsStateWithLifecycle()
  val activeGroup by communityViewModel.activeGroup.collectAsStateWithLifecycle()
  val activeGroupMessagesState by communityViewModel.activeGroupMessagesState.collectAsStateWithLifecycle()
  val callsState by communityViewModel.callsState.collectAsStateWithLifecycle()
  val activeLiveCall by communityViewModel.activeLiveCall.collectAsStateWithLifecycle()
  val activeCallIceCandidates by communityViewModel.activeCallIceCandidates.collectAsStateWithLifecycle()
  val communityBusy by communityViewModel.busy.collectAsStateWithLifecycle()
  val communitySnackbar by communityViewModel.snackbar.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(snackbarMessage) {
    val msg = snackbarMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeSnackbar()
    }
  }
  LaunchedEffect(communitySnackbar) {
    val msg = communitySnackbar
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      communityViewModel.consumeSnackbar()
    }
  }

  val myProfile = (myProfileState as? UiState.Success)?.data
  val contactsList = (contactsState as? UiState.Success)?.data ?: emptyList()

  // First-time onboarding gate after Google Sign-In
  if (myProfile != null && !myProfile.onboardingCompleted) {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
      ProfileSetupScreen(
        profile = myProfile,
        currentLanguage = currentLanguage,
        currentThemeMode = currentThemeMode,
        busy = profileBusy,
        usernameAvailability = usernameAvailability,
        onCheckUsername = viewModel::checkUsername,
        onResetUsernameAvailability = viewModel::resetUsernameAvailability,
        onCompleteSetup = { name, uname, bio, avatar, lang, mode ->
          viewModel.saveProfile(
            displayName = name,
            username = uname,
            bio = bio,
            avatarUrl = avatar,
            language = lang,
            themeMode = mode,
            savedMessage = strings.profileSaved,
            takenMessage = strings.usernameTaken,
            markOnboardingComplete = true,
          )
        },
        modifier = Modifier.padding(pad),
      )
    }
    return
  }

  var activeTab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
  var secondaryOverlay by rememberSaveable { mutableStateOf(SecondaryOverlay.NONE) }
  var headerSearchExpanded by rememberSaveable { mutableStateOf(false) }
  var dismissedNotifBanner by rememberSaveable { mutableStateOf(false) }

  var notifPermissionGranted by remember {
    mutableStateOf(TawasulNotificationHelper.hasNotificationPermission(context))
  }
  val notifPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notifPermissionGranted = granted || TawasulNotificationHelper.hasNotificationPermission(context)
      if (notifPermissionGranted) {
        TawasulNotificationHelper.ensureChannels(context, strings)
        TawasulBackgroundSyncService.startIfAllowed(context)
      }
    }

  LaunchedEffect(currentUserId, notifPermissionGranted) {
    TawasulNotificationHelper.ensureChannels(context, strings)
    if (notifPermissionGranted) {
      TawasulBackgroundSyncService.startIfAllowed(context)
    }
  }

  LaunchedEffect(activeChatId, activeGroupId, activeLiveCall?.id) {
    AppVisibilityState.activeChatId = activeChatId
    AppVisibilityState.activeGroupId = activeGroupId
    AppVisibilityState.activeCallId = activeLiveCall?.id
  }

  LaunchedEffect(pendingIntent) {
    val intent = pendingIntent ?: return@LaunchedEffect
    val targetChat = intent.getStringExtra(TawasulNotificationHelper.EXTRA_OPEN_CHAT_ID)
    val targetGroup = intent.getStringExtra(TawasulNotificationHelper.EXTRA_OPEN_GROUP_ID)
    val targetCall = intent.getStringExtra(TawasulNotificationHelper.EXTRA_CALL_ID)
    when {
      !targetChat.isNullOrBlank() -> {
        secondaryOverlay = SecondaryOverlay.NONE
        activeTab = MainTab.CHATS
        viewModel.openChat(targetChat)
      }
      !targetGroup.isNullOrBlank() -> {
        secondaryOverlay = SecondaryOverlay.NONE
        activeTab = MainTab.GROUPS
        communityViewModel.openGroup(targetGroup)
      }
      !targetCall.isNullOrBlank() -> {
        secondaryOverlay = SecondaryOverlay.NONE
        activeTab = MainTab.CALLS
      }
    }
    onConsumeIntent()
  }

  if (secondaryOverlay != SecondaryOverlay.NONE) {
    BackHandler { secondaryOverlay = SecondaryOverlay.NONE }
  }

  val isArabic = currentLanguage == AppLanguage.AR
  val allChats = (chatsState as? UiState.Success)?.data ?: emptyList()
  val totalUnreadChats = allChats.sumOf { it.unreadFor(currentUserId).toInt() }

  val toggleLanguageAction = {
    viewModel.setLanguage(if (currentLanguage == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR)
  }
  val cycleThemeAction = {
    val next =
      when (currentThemeMode) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
      }
    viewModel.setThemeMode(next)
  }
  val signOutAction = {
    CallForegroundService.stop(context)
    TawasulBackgroundSyncService.stop(context)
    viewModel.setOnlinePresence(false)
    signOutUser(credentialManager, scope)
  }
  val startCallFromChat: (String, String, String?, String) -> Unit = { uid, name, avatar, callType ->
    val prof = myProfile
    if (prof != null) {
      communityViewModel.startCall(prof, uid, name, avatar, callType)
    }
  }

  activeLiveCall?.let { liveCall ->
    ActiveCallOverlay(
      call = liveCall,
      currentUserId = currentUserId,
      iceCandidates = activeCallIceCandidates,
      onPublishOffer = { sdp -> communityViewModel.publishOfferSdp(liveCall.id, sdp) },
      onPublishAnswer = { sdp -> communityViewModel.publishAnswerSdp(liveCall.id, sdp) },
      onSendIceCandidate = { cand, mid, idx ->
        communityViewModel.sendIceCandidate(liveCall, cand, mid, idx)
      },
      onAccept = { communityViewModel.acceptCall(liveCall) },
      onDecline = { communityViewModel.declineCall(liveCall) },
      onEnd = { elapsed -> communityViewModel.endCall(liveCall, elapsed) },
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 840.dp

    if (secondaryOverlay != SecondaryOverlay.NONE) {
      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
          TawasulTopHeader(
            title =
              if (secondaryOverlay == SecondaryOverlay.CONTACTS) strings.tabContacts
              else strings.profileTitle,
            subtitle = strings.appName,
            myProfile = myProfile,
            currentLanguage = currentLanguage,
            currentThemeMode = currentThemeMode,
            searchExpanded = false,
            searchQuery = "",
            showBackButton = true,
            onBackClick = { secondaryOverlay = SecondaryOverlay.NONE },
            onAvatarClick = { secondaryOverlay = SecondaryOverlay.PROFILE },
            onContactsClick = { secondaryOverlay = SecondaryOverlay.CONTACTS },
            onToggleSearch = {},
            onSearchQueryChange = {},
            onToggleLanguage = toggleLanguageAction,
            onCycleTheme = cycleThemeAction,
            onSignOut = signOutAction,
          )
        },
      ) { innerPadding ->
        when (secondaryOverlay) {
          SecondaryOverlay.CONTACTS -> {
            ContactsScreen(
              contactsState = contactsState,
              lookupLoading = lookupLoading,
              lookupResult = lookupResult,
              lookupError = lookupError,
              onSearchUsername = { q ->
                viewModel.searchUserByUsername(q, strings.userNotFound, strings.cannotChatSelf)
              },
              onStartChatWithEntry = { entry ->
                viewModel.startChatWith(entry) {
                  activeTab = MainTab.CHATS
                  secondaryOverlay = SecondaryOverlay.NONE
                }
              },
              onStartChatWithContact = { contact ->
                viewModel.startChatWithContact(contact) {
                  activeTab = MainTab.CHATS
                  secondaryOverlay = SecondaryOverlay.NONE
                }
              },
              onSaveContact = { entry -> viewModel.saveContact(entry, strings.addContactAction) },
              onRemoveContact = viewModel::removeContact,
              modifier = Modifier.padding(innerPadding),
            )
          }
          SecondaryOverlay.PROFILE -> {
            ProfileScreen(
              profileState = myProfileState,
              currentLanguage = currentLanguage,
              currentThemeMode = currentThemeMode,
              profileBusy = profileBusy,
              usernameAvailability = usernameAvailability,
              onCheckUsername = viewModel::checkUsername,
              onResetUsernameAvailability = viewModel::resetUsernameAvailability,
              onSaveProfile = { name, uname, bio, avatar, lang, mode ->
                viewModel.saveProfile(
                  displayName = name,
                  username = uname,
                  bio = bio,
                  avatarUrl = avatar,
                  language = lang,
                  themeMode = mode,
                  savedMessage = strings.profileSaved,
                  takenMessage = strings.usernameTaken,
                )
              },
              onLanguageChange = viewModel::setLanguage,
              onThemeModeChange = viewModel::setThemeMode,
              onSetOfflineBeforeSignOut = { viewModel.setOnlinePresence(false) },
              modifier = Modifier.padding(innerPadding),
            )
          }
          SecondaryOverlay.NONE -> Unit
        }
      }
    } else if (!isExpanded && activeChatId != null) {
      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
      ) { innerPadding ->
        ChatDetailScreen(
          currentUserId = currentUserId,
          room = activeChat,
          otherPresence = activeOtherPresence,
          messagesState = activeMessagesState,
          allChats = allChats,
          isArabic = isArabic,
          showBackButton = true,
          onBack = { viewModel.closeChat() },
          onSendMessage = { text, reply -> viewModel.sendMessage(text, reply) },
          onSendMediaMessage = { type, mediaData, preview, caption, fileName, mime, size, dur, reply ->
            viewModel.sendMediaMessage(
              type = type,
              mediaData = mediaData,
              previewText = preview,
              caption = caption,
              fileName = fileName,
              mimeType = mime,
              fileSizeBytes = size,
              mediaDurationMs = dur,
              replyTo = reply,
            )
          },
          onEditMessage = { id, text -> viewModel.editMessage(id, text) },
          onDeleteMessage = { id -> viewModel.deleteMessage(id) },
          onToggleReaction = { msg, emoji -> viewModel.toggleReaction(msg, emoji) },
          onForwardMessage = { target, msg ->
            viewModel.forwardMessageToChat(target, msg, strings.forwardedLabel)
          },
          onMarkRead = { chatId, msgs -> viewModel.markMessagesRead(chatId, msgs) },
          onTypingChanged = { text -> viewModel.onComposerTextChanged(text) },
          onTogglePin = { viewModel.togglePinChat(it) },
          onToggleArchive = { viewModel.toggleArchiveChat(it) },
          onToggleMute = { viewModel.toggleMuteChat(it) },
          onStartAudioCall = { uid, name, avatar -> startCallFromChat(uid, name, avatar, "audio") },
          onStartVideoCall = { uid, name, avatar -> startCallFromChat(uid, name, avatar, "video") },
          onShowMessage = { msg -> viewModel.showSnackbar(msg) },
          modifier = Modifier.padding(innerPadding),
        )
      }
    } else if (!isExpanded && activeGroupId != null) {
      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
      ) { innerPadding ->
        GroupDetailScreen(
          currentUserId = currentUserId,
          group = activeGroup,
          messagesState = activeGroupMessagesState,
          lookupLoading = lookupLoading,
          lookupResult = lookupResult,
          lookupError = lookupError,
          isArabic = isArabic,
          onBack = { communityViewModel.closeGroup() },
          onSendMessage = { text ->
            if (myProfile != null) communityViewModel.sendGroupMessage(myProfile, text)
          },
          onSendMediaMessage = { type, mediaData, preview, caption, fileName, mime, size, dur ->
            if (myProfile != null) {
              communityViewModel.sendGroupMediaMessage(
                myProfile = myProfile,
                type = type,
                mediaData = mediaData,
                previewText = preview,
                caption = caption,
                fileName = fileName,
                mimeType = mime,
                fileSizeBytes = size,
                mediaDurationMs = dur,
              )
            }
          },
          onSearchUsername = { q ->
            viewModel.searchUserByUsername(q, strings.userNotFound, strings.cannotChatSelf)
          },
          onClearLookup = viewModel::clearLookup,
          onAddMember = { entry ->
            communityViewModel.addMemberToActiveGroup(entry, strings.addContactAction)
          },
          onInviteCopied = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
          onShowMessage = { msg -> communityViewModel.showSnackbar(msg) },
          modifier = Modifier.padding(innerPadding),
        )
      }
    } else if (isExpanded) {
      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
      ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            header = {
              Box(
                modifier =
                  Modifier.padding(top = 12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(TawasulGold, TawasulGoldDeep))),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.ChatBubble,
                  contentDescription = strings.appName,
                  tint = TawasulOnGold,
                )
              }
            },
          ) {
            Spacer(modifier = Modifier.height(16.dp))
            MainTab.entries.forEach { tab ->
              NavigationRailItem(
                selected = activeTab == tab,
                onClick = { activeTab = tab },
                icon = { TabIconWithBadge(tab, totalUnreadChats, strings) },
                label = { Text(tabLabel(tab, strings)) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  ),
              )
            }
          }

          Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TawasulTopHeader(
              title = strings.appName,
              subtitle = tabLabel(activeTab, strings),
              myProfile = myProfile,
              currentLanguage = currentLanguage,
              currentThemeMode = currentThemeMode,
              searchExpanded = headerSearchExpanded,
              searchQuery = searchQuery,
              onAvatarClick = { secondaryOverlay = SecondaryOverlay.PROFILE },
              onContactsClick = { secondaryOverlay = SecondaryOverlay.CONTACTS },
              onToggleSearch = {
                headerSearchExpanded = !headerSearchExpanded
                if (!headerSearchExpanded) viewModel.updateSearchQuery("")
              },
              onSearchQueryChange = viewModel::updateSearchQuery,
              onToggleLanguage = toggleLanguageAction,
              onCycleTheme = cycleThemeAction,
              onSignOut = signOutAction,
            )

            when (activeTab) {
              MainTab.CHATS -> {
                Row(modifier = Modifier.fillMaxSize()) {
                  Column(
                    modifier =
                      Modifier.width(360.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                  ) {
                    ChatsListPane(
                      currentUserId = currentUserId,
                      chatsState = chatsState,
                      filteredChats = filteredChats,
                      archivedCount = archivedCount,
                      showArchived = showArchived,
                      searchQuery = searchQuery,
                      selectedChatId = activeChatId,
                      partnersOnline = partnersOnline,
                      isArabic = isArabic,
                      lookupLoading = lookupLoading,
                      lookupResult = lookupResult,
                      lookupError = lookupError,
                      onSearchQueryChange = viewModel::updateSearchQuery,
                      onToggleShowArchived = viewModel::toggleShowArchived,
                      onSelectChat = { viewModel.openChat(it.id) },
                      onTogglePin = viewModel::togglePinChat,
                      onToggleArchive = viewModel::toggleArchiveChat,
                      onToggleMute = viewModel::toggleMuteChat,
                      onSearchUsername = { q ->
                        viewModel.searchUserByUsername(q, strings.userNotFound, strings.cannotChatSelf)
                      },
                      onClearLookup = viewModel::clearLookup,
                      onStartChatWithEntry = { entry -> viewModel.startChatWith(entry) },
                      onSaveContact = { entry ->
                        viewModel.saveContact(entry, strings.addContactAction)
                      },
                    )
                  }
                  Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeChatId == null) {
                      SelectChatEmptyPane()
                    } else {
                      ChatDetailScreen(
                        currentUserId = currentUserId,
                        room = activeChat,
                        otherPresence = activeOtherPresence,
                        messagesState = activeMessagesState,
                        allChats = allChats,
                        isArabic = isArabic,
                        showBackButton = false,
                        onBack = { viewModel.closeChat() },
                        onSendMessage = { text, reply -> viewModel.sendMessage(text, reply) },
                        onSendMediaMessage = { type, mediaData, preview, caption, fileName, mime, size, dur, reply ->
                          viewModel.sendMediaMessage(
                            type = type,
                            mediaData = mediaData,
                            previewText = preview,
                            caption = caption,
                            fileName = fileName,
                            mimeType = mime,
                            fileSizeBytes = size,
                            mediaDurationMs = dur,
                            replyTo = reply,
                          )
                        },
                        onEditMessage = { id, text -> viewModel.editMessage(id, text) },
                        onDeleteMessage = { id -> viewModel.deleteMessage(id) },
                        onToggleReaction = { msg, emoji -> viewModel.toggleReaction(msg, emoji) },
                        onForwardMessage = { target, msg ->
                          viewModel.forwardMessageToChat(target, msg, strings.forwardedLabel)
                        },
                        onMarkRead = { chatId, msgs -> viewModel.markMessagesRead(chatId, msgs) },
                        onTypingChanged = { text -> viewModel.onComposerTextChanged(text) },
                        onTogglePin = viewModel::togglePinChat,
                        onToggleArchive = viewModel::toggleArchiveChat,
                        onToggleMute = viewModel::toggleMuteChat,
                        onStartAudioCall = { uid, name, avatar ->
                          startCallFromChat(uid, name, avatar, "audio")
                        },
                        onStartVideoCall = { uid, name, avatar ->
                          startCallFromChat(uid, name, avatar, "video")
                        },
                        onShowMessage = { msg -> viewModel.showSnackbar(msg) },
                      )
                    }
                  }
                }
              }
              MainTab.STATUSES -> {
                StatusesTab(
                  currentUserId = currentUserId,
                  myProfile = myProfile,
                  contacts = contactsList,
                  statusesState = statusesState,
                  searchQuery = searchQuery,
                  busy = communityBusy,
                  isArabic = isArabic,
                  onPublishStatus = { type, text, img, hex, priv, done ->
                    if (myProfile != null) {
                      communityViewModel.publishStatus(
                        myProfile, contactsList, type, text, img, hex, priv, done
                      )
                    }
                  },
                  onViewStatus = communityViewModel::markStatusViewed,
                  onDeleteStatus = communityViewModel::deleteStatus,
                )
              }
              MainTab.CALLS -> {
                CallsTab(
                  currentUserId = currentUserId,
                  callsState = callsState,
                  contacts = contactsList,
                  searchQuery = searchQuery,
                  isArabic = isArabic,
                  onStartCall = { uid, name, avatar, type ->
                    if (myProfile != null) {
                      communityViewModel.startCall(myProfile, uid, name, avatar, type)
                    }
                  },
                )
              }
              MainTab.GROUPS -> {
                Row(modifier = Modifier.fillMaxSize()) {
                  Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                    GroupsTab(
                      currentUserId = currentUserId,
                      myProfile = myProfile,
                      contacts = contactsList,
                      groupsState = groupsState,
                      searchQuery = searchQuery,
                      busy = communityBusy,
                      isArabic = isArabic,
                      onOpenGroup = communityViewModel::openGroup,
                      onCreateGroup = { name, desc, members, done ->
                        if (myProfile != null) {
                          communityViewModel.createGroup(
                            myProfile, name, desc, members, strings.groupCreatedMsg, done
                          )
                        }
                      },
                      onJoinByInviteCode = { code, done ->
                        if (myProfile != null) {
                          communityViewModel.joinGroupByInviteCode(
                            code, myProfile, strings.joinedGroupMsg, strings.invalidInviteCode, done
                          )
                        }
                      },
                    )
                  }
                  Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeGroupId == null) {
                      SelectChatEmptyPane()
                    } else {
                      GroupDetailScreen(
                        currentUserId = currentUserId,
                        group = activeGroup,
                        messagesState = activeGroupMessagesState,
                        lookupLoading = lookupLoading,
                        lookupResult = lookupResult,
                        lookupError = lookupError,
                        isArabic = isArabic,
                        onBack = { communityViewModel.closeGroup() },
                        onSendMessage = { text ->
                          if (myProfile != null) communityViewModel.sendGroupMessage(myProfile, text)
                        },
                        onSendMediaMessage = { type, mediaData, preview, caption, fileName, mime, size, dur ->
                          if (myProfile != null) {
                            communityViewModel.sendGroupMediaMessage(
                              myProfile = myProfile,
                              type = type,
                              mediaData = mediaData,
                              previewText = preview,
                              caption = caption,
                              fileName = fileName,
                              mimeType = mime,
                              fileSizeBytes = size,
                              mediaDurationMs = dur,
                            )
                          }
                        },
                        onSearchUsername = { q ->
                          viewModel.searchUserByUsername(q, strings.userNotFound, strings.cannotChatSelf)
                        },
                        onClearLookup = viewModel::clearLookup,
                        onAddMember = { entry ->
                          communityViewModel.addMemberToActiveGroup(entry, strings.addContactAction)
                        },
                        onInviteCopied = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        onShowMessage = { msg -> communityViewModel.showSnackbar(msg) },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else {
      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
          TawasulTopHeader(
            title = strings.appName,
            subtitle = tabLabel(activeTab, strings),
            myProfile = myProfile,
            currentLanguage = currentLanguage,
            currentThemeMode = currentThemeMode,
            searchExpanded = headerSearchExpanded,
            searchQuery = searchQuery,
            onAvatarClick = { secondaryOverlay = SecondaryOverlay.PROFILE },
            onContactsClick = { secondaryOverlay = SecondaryOverlay.CONTACTS },
            onToggleSearch = {
              headerSearchExpanded = !headerSearchExpanded
              if (!headerSearchExpanded) viewModel.updateSearchQuery("")
            },
            onSearchQueryChange = viewModel::updateSearchQuery,
            onToggleLanguage = toggleLanguageAction,
            onCycleTheme = cycleThemeAction,
            onSignOut = signOutAction,
          )
        },
        bottomBar = {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            windowInsets = WindowInsets.navigationBars,
          ) {
            MainTab.entries.forEach { tab ->
              NavigationBarItem(
                selected = activeTab == tab,
                onClick = { activeTab = tab },
                icon = { TabIconWithBadge(tab, totalUnreadChats, strings) },
                label = {
                  Text(
                    text = tabLabel(tab, strings),
                    fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                  )
                },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
              )
            }
          }
        },
      ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          if (!notifPermissionGranted && !dismissedNotifBanner && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
              shape = RoundedCornerShape(16.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = strings.enableNotificationsTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                  Text(
                    text = strings.enableNotificationsDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                  )
                }
                TextButton(onClick = { dismissedNotifBanner = true }) {
                  Text(strings.cancel)
                }
                Button(
                  onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                  modifier = Modifier.testTag("enable_notifications_banner_button"),
                ) {
                  Text(strings.enableNotificationsAction)
                }
              }
            }
          }

          if (!ensureError.isNullOrBlank()) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              shape = RoundedCornerShape(14.dp),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = ensureError!!,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.weight(1f),
                )
                Button(onClick = { viewModel.initializeUserProfile() }) {
                  Text(strings.retry)
                }
              }
            }
          }

          when (activeTab) {
            MainTab.CHATS -> {
              ChatsListPane(
                currentUserId = currentUserId,
                chatsState = chatsState,
                filteredChats = filteredChats,
                archivedCount = archivedCount,
                showArchived = showArchived,
                searchQuery = searchQuery,
                selectedChatId = activeChatId,
                partnersOnline = partnersOnline,
                isArabic = isArabic,
                lookupLoading = lookupLoading,
                lookupResult = lookupResult,
                lookupError = lookupError,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onToggleShowArchived = viewModel::toggleShowArchived,
                onSelectChat = { viewModel.openChat(it.id) },
                onTogglePin = viewModel::togglePinChat,
                onToggleArchive = viewModel::toggleArchiveChat,
                onToggleMute = viewModel::toggleMuteChat,
                onSearchUsername = { q ->
                  viewModel.searchUserByUsername(q, strings.userNotFound, strings.cannotChatSelf)
                },
                onClearLookup = viewModel::clearLookup,
                onStartChatWithEntry = { entry -> viewModel.startChatWith(entry) },
                onSaveContact = { entry -> viewModel.saveContact(entry, strings.addContactAction) },
              )
            }
            MainTab.STATUSES -> {
              StatusesTab(
                currentUserId = currentUserId,
                myProfile = myProfile,
                contacts = contactsList,
                statusesState = statusesState,
                searchQuery = searchQuery,
                busy = communityBusy,
                isArabic = isArabic,
                onPublishStatus = { type, text, img, hex, priv, done ->
                  if (myProfile != null) {
                    communityViewModel.publishStatus(
                      myProfile, contactsList, type, text, img, hex, priv, done
                    )
                  }
                },
                onViewStatus = communityViewModel::markStatusViewed,
                onDeleteStatus = communityViewModel::deleteStatus,
              )
            }
            MainTab.CALLS -> {
              CallsTab(
                currentUserId = currentUserId,
                callsState = callsState,
                contacts = contactsList,
                searchQuery = searchQuery,
                isArabic = isArabic,
                onStartCall = { uid, name, avatar, type ->
                  if (myProfile != null) {
                    communityViewModel.startCall(myProfile, uid, name, avatar, type)
                  }
                },
              )
            }
            MainTab.GROUPS -> {
              GroupsTab(
                currentUserId = currentUserId,
                myProfile = myProfile,
                contacts = contactsList,
                groupsState = groupsState,
                searchQuery = searchQuery,
                busy = communityBusy,
                isArabic = isArabic,
                onOpenGroup = communityViewModel::openGroup,
                onCreateGroup = { name, desc, members, done ->
                  if (myProfile != null) {
                    communityViewModel.createGroup(
                      myProfile, name, desc, members, strings.groupCreatedMsg, done
                    )
                  }
                },
                onJoinByInviteCode = { code, done ->
                  if (myProfile != null) {
                    communityViewModel.joinGroupByInviteCode(
                      code, myProfile, strings.joinedGroupMsg, strings.invalidInviteCode, done
                    )
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TabIconWithBadge(tab: MainTab, unreadChats: Int, strings: AppStrings) {
  val icon =
    when (tab) {
      MainTab.CHATS -> Icons.AutoMirrored.Rounded.Chat
      MainTab.STATUSES -> Icons.Rounded.AutoStories
      MainTab.CALLS -> Icons.Rounded.Call
      MainTab.GROUPS -> Icons.Rounded.Groups
    }
  if (tab == MainTab.CHATS && unreadChats > 0) {
    BadgedBox(
      badge = {
        Badge(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Text(if (unreadChats > 99) "99+" else unreadChats.toString())
        }
      }
    ) {
      Icon(imageVector = icon, contentDescription = tabLabel(tab, strings))
    }
  } else {
    Icon(imageVector = icon, contentDescription = tabLabel(tab, strings))
  }
}

@Composable
private fun SelectChatEmptyPane() {
  val strings = LocalAppStrings.current
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier =
        Modifier.size(84.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.Chat,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(40.dp),
      )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = strings.selectChatPrompt,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = strings.selectChatSubtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun tabLabel(tab: MainTab, strings: AppStrings) =
  when (tab) {
    MainTab.CHATS -> strings.tabChats
    MainTab.STATUSES -> strings.tabStatuses
    MainTab.CALLS -> strings.tabCalls
    MainTab.GROUPS -> strings.tabGroups
  }
