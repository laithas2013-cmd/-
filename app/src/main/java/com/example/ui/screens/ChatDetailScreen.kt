package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.LocalAppStrings
import com.example.core.media.VoicePlaybackState
import com.example.core.media.VoicePlayerController
import com.example.core.media.VoiceRecorderHelper
import com.example.core.util.FileReadResult
import com.example.core.util.compressUriToDataUri
import com.example.core.util.readDocumentToAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.UiState
import com.example.data.model.UsernameDirectoryEntry
import com.example.ui.components.FileAttachmentContent
import com.example.ui.components.ImageAttachmentContent
import com.example.ui.components.TawasulAvatar
import com.example.ui.components.VoiceMessageContent
import com.example.ui.components.VoiceRecordingBar
import com.example.ui.components.formatLastSeenTime
import com.example.ui.components.formatMessageTime
import com.example.ui.theme.TawasulOnlineGreen
import com.example.ui.theme.TawasulReadBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
  currentUserId: String,
  room: ChatRoom?,
  otherPresence: UsernameDirectoryEntry?,
  messagesState: UiState<List<ChatMessage>>,
  allChats: List<ChatRoom>,
  isArabic: Boolean,
  showBackButton: Boolean,
  onBack: () -> Unit,
  onSendMessage: (String, ChatMessage?) -> Unit,
  onSendMediaMessage: (
    type: String,
    mediaData: String,
    previewText: String,
    caption: String,
    fileName: String?,
    mimeType: String?,
    fileSizeBytes: Long?,
    mediaDurationMs: Long?,
    replyTo: ChatMessage?,
  ) -> Unit,
  onEditMessage: (String, String) -> Unit,
  onDeleteMessage: (String) -> Unit,
  onToggleReaction: (ChatMessage, String) -> Unit,
  onForwardMessage: (ChatRoom, ChatMessage) -> Unit,
  onMarkRead: (String, List<ChatMessage>) -> Unit,
  onTypingChanged: (String) -> Unit,
  onTogglePin: (ChatRoom) -> Unit,
  onToggleArchive: (ChatRoom) -> Unit,
  onToggleMute: (ChatRoom) -> Unit,
  onStartAudioCall: (uid: String, name: String, avatar: String?) -> Unit,
  onStartVideoCall: (uid: String, name: String, avatar: String?) -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  BackHandler(enabled = showBackButton) { onBack() }

  if (room == null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
    return
  }

  val otherUid = room.otherParticipantId(currentUserId)
  val otherName = otherPresence?.displayName ?: room.otherDisplayName(currentUserId)
  val otherAvatar = otherPresence?.avatarUrl ?: room.otherAvatarUrl(currentUserId)
  val isOnline = otherPresence?.isOnline == true
  val isOtherTyping = room.isOtherTyping(currentUserId)

  val statusText =
    when {
      isOtherTyping -> strings.typingNow
      isOnline -> strings.onlineNow
      otherPresence?.lastSeen != null ->
        strings.lastSeenAt(formatLastSeenTime(otherPresence.lastSeen, isArabic))
      else -> strings.offline
    }

  var composerText by remember(room.id) { mutableStateOf("") }
  var replyingTo by remember(room.id) { mutableStateOf<ChatMessage?>(null) }
  var editingMessage by remember(room.id) { mutableStateOf<ChatMessage?>(null) }
  var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
  var messageToForward by remember { mutableStateOf<ChatMessage?>(null) }
  var topMenuExpanded by remember { mutableStateOf(false) }
  var attachMenuExpanded by remember { mutableStateOf(false) }
  var isRecording by remember(room.id) { mutableStateOf(false) }
  var recordingElapsedMs by remember(room.id) { mutableLongStateOf(0L) }

  val voiceRecorder = remember(room.id) { VoiceRecorderHelper(context) }
  val voicePlayer = remember(room.id) { VoicePlayerController(context, scope) }
  val voicePlaybackState by voicePlayer.state.collectAsStateWithLifecycle()

  DisposableEffect(room.id) {
    onDispose {
      voiceRecorder.stopAndDiscard()
      voicePlayer.stop()
    }
  }

  LaunchedEffect(isRecording) {
    if (isRecording) {
      recordingElapsedMs = 0L
      while (isRecording) {
        delay(200L)
        recordingElapsedMs += 200L
      }
    }
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        if (voiceRecorder.start()) {
          isRecording = true
        } else {
          onShowMessage(strings.errorGeneric)
        }
      } else {
        onShowMessage(strings.micPermissionDenied)
      }
    }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val dataUri =
            compressUriToDataUri(
              context = context,
              uri = uri,
              maxDimension = 640,
              maxDataUriLength = 380_000,
            )
          if (dataUri != null) {
            val caption = composerText.trim()
            onSendMediaMessage(
              "image",
              dataUri,
              strings.photoMessagePreview,
              caption,
              "photo_${System.currentTimeMillis()}.jpg",
              "image/jpeg",
              null,
              null,
              replyingTo,
            )
            composerText = ""
            replyingTo = null
          } else {
            onShowMessage(strings.imagePickError)
          }
        }
      }
    }

  val filePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          when (val result = readDocumentToAttachment(context, uri)) {
            is FileReadResult.Success -> {
              onSendMediaMessage(
                "file",
                result.dataUri,
                strings.fileMessagePreview(result.fileName),
                "",
                result.fileName,
                result.mimeType,
                result.sizeBytes,
                null,
                replyingTo,
              )
              replyingTo = null
            }
            FileReadResult.TooLarge -> onShowMessage(strings.fileTooLargeError)
            FileReadResult.Error -> onShowMessage(strings.errorGeneric)
          }
        }
      }
    }

  val listState = rememberLazyListState()

  // Auto-mark messages as read and scroll to latest message
  LaunchedEffect(room.id, messagesState) {
    val msgs = (messagesState as? UiState.Success)?.data ?: return@LaunchedEffect
    onMarkRead(room.id, msgs)
    if (msgs.isNotEmpty()) {
      listState.animateScrollToItem(msgs.lastIndex)
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = 8.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (showBackButton) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.size(48.dp).testTag("chat_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = strings.back,
              )
            }
          } else {
            Spacer(modifier = Modifier.width(8.dp))
          }

          TawasulAvatar(
            displayName = otherName,
            avatarUrl = otherAvatar,
            size = 44.dp,
            isOnline = isOnline,
            showOnlineDot = true,
          )

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = otherName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = statusText,
              style = MaterialTheme.typography.labelMedium,
              color =
                when {
                  isOtherTyping -> MaterialTheme.colorScheme.secondary
                  isOnline -> TawasulOnlineGreen
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
              fontWeight = if (isOtherTyping || isOnline) FontWeight.SemiBold else FontWeight.Normal,
            )
          }

          if (otherUid.isNotBlank()) {
            IconButton(
              onClick = { onStartAudioCall(otherUid, otherName, otherAvatar) },
              modifier = Modifier.size(48.dp).testTag("chat_audio_call_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = strings.audioCall,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            IconButton(
              onClick = { onStartVideoCall(otherUid, otherName, otherAvatar) },
              modifier = Modifier.size(48.dp).testTag("chat_video_call_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = strings.videoCall,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }

          Box {
            IconButton(
              onClick = { topMenuExpanded = true },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = strings.pinChat,
              )
            }
            DropdownMenu(
              expanded = topMenuExpanded,
              onDismissRequest = { topMenuExpanded = false },
            ) {
              val isPinned = room.isPinnedBy(currentUserId)
              val isMuted = room.isMutedBy(currentUserId)
              val isArchived = room.isArchivedBy(currentUserId)
              DropdownMenuItem(
                text = { Text(if (isPinned) strings.unpinChat else strings.pinChat) },
                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                onClick = {
                  topMenuExpanded = false
                  onTogglePin(room)
                },
              )
              DropdownMenuItem(
                text = { Text(if (isMuted) strings.unmuteChat else strings.muteChat) },
                leadingIcon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
                onClick = {
                  topMenuExpanded = false
                  onToggleMute(room)
                },
              )
              DropdownMenuItem(
                text = { Text(if (isArchived) strings.unarchiveChat else strings.archiveChat) },
                leadingIcon = {
                  Icon(
                    if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    contentDescription = null,
                  )
                },
                onClick = {
                  topMenuExpanded = false
                  onToggleArchive(room)
                },
              )
            }
          }
        }
      }
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.navigationBarsPadding().imePadding(),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
          // Reply or Editing Banner
          if (replyingTo != null || editingMessage != null) {
            Surface(
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text =
                      if (editingMessage != null) strings.editingMessage
                      else strings.replyingTo(replyingTo?.senderName.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                  Text(
                    text = (editingMessage?.text ?: replyingTo?.text).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = {
                    replyingTo = null
                    if (editingMessage != null) {
                      editingMessage = null
                      composerText = ""
                    }
                  },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                }
              }
            }
          }

          if (isRecording) {
            VoiceRecordingBar(
              elapsedMs = recordingElapsedMs,
              onCancel = {
                voiceRecorder.stopAndDiscard()
                isRecording = false
              },
              onStopAndSend = {
                val clip = voiceRecorder.stopAndEncode()
                isRecording = false
                if (clip != null) {
                  onSendMediaMessage(
                    "voice",
                    clip.dataUri,
                    strings.voiceMessageLabel,
                    "",
                    "voice_${System.currentTimeMillis()}.m4a",
                    "audio/mp4",
                    clip.sizeBytes,
                    clip.durationMs,
                    replyingTo,
                  )
                  replyingTo = null
                } else {
                  onShowMessage(strings.voiceTooShortOrLarge)
                }
              },
            )
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.Bottom,
            ) {
              if (editingMessage == null) {
                Box {
                  IconButton(
                    onClick = { attachMenuExpanded = true },
                    modifier = Modifier.size(48.dp).testTag("chat_attach_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.AttachFile,
                      contentDescription = strings.attachTooltip,
                      tint = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  DropdownMenu(
                    expanded = attachMenuExpanded,
                    onDismissRequest = { attachMenuExpanded = false },
                  ) {
                    DropdownMenuItem(
                      text = { Text(strings.attachPhoto) },
                      leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                      onClick = {
                        attachMenuExpanded = false
                        photoPickerLauncher.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                      },
                    )
                    DropdownMenuItem(
                      text = { Text(strings.attachFile) },
                      leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                      onClick = {
                        attachMenuExpanded = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                      },
                    )
                  }
                }
              }

              OutlinedTextField(
                value = composerText,
                onValueChange = { newText ->
                  composerText = newText
                  if (editingMessage == null) {
                    onTypingChanged(newText)
                  }
                },
                modifier = Modifier.weight(1f).testTag("message_composer_input"),
                placeholder = { Text(strings.writeMessageHint) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors =
                  OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                  ),
              )

              Spacer(modifier = Modifier.width(8.dp))

              val canSendText = composerText.isNotBlank() || editingMessage != null
              if (canSendText) {
                IconButton(
                  onClick = {
                    val clean = composerText.trim()
                    if (clean.isEmpty()) return@IconButton
                    val editTarget = editingMessage
                    if (editTarget != null) {
                      onEditMessage(editTarget.id, clean)
                      editingMessage = null
                    } else {
                      onSendMessage(clean, replyingTo)
                      replyingTo = null
                    }
                    composerText = ""
                  },
                  enabled = composerText.isNotBlank(),
                  modifier =
                    Modifier.size(50.dp)
                      .clip(CircleShape)
                      .background(
                        if (composerText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                      )
                      .testTag("send_message_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = strings.send,
                    tint =
                      if (composerText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              } else {
                IconButton(
                  onClick = {
                    val hasMic =
                      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                      if (voiceRecorder.start()) {
                        isRecording = true
                      } else {
                        onShowMessage(strings.errorGeneric)
                      }
                    } else {
                      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                  },
                  modifier =
                    Modifier.size(50.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primaryContainer)
                      .testTag("chat_record_voice_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = strings.recordVoiceTooltip,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    when (messagesState) {
      is UiState.Loading -> {
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
      is UiState.Error -> {
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.Center,
        ) {
          Text(messagesState.message, color = MaterialTheme.colorScheme.error)
        }
      }
      is UiState.Success -> {
        val messages = messagesState.data
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(messages, key = { it.id }) { msg ->
            MessageBubble(
              message = msg,
              isMine = msg.senderId == currentUserId,
              otherUid = otherUid,
              isArabic = isArabic,
              voicePlaybackState = voicePlaybackState,
              onToggleVoicePlay = {
                voicePlayer.togglePlay(msg.id, msg.mediaData, msg.mediaDurationMs ?: 0L)
              },
              onFileOpenError = { onShowMessage(strings.openFileError) },
              onLongOrClick = {
                if (!msg.isDeleted) selectedMessageForActions = msg
              },
              onReactionTap = { emoji ->
                if (!msg.isDeleted) onToggleReaction(msg, emoji)
              },
            )
          }
        }
      }
    }
  }

  // Message Actions Bottom Sheet
  val targetMsg = selectedMessageForActions
  if (targetMsg != null) {
    ModalBottomSheet(
      onDismissRequest = { selectedMessageForActions = null },
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        // Quick Emoji Reaction Picker Row
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          QUICK_REACTIONS.forEach { emoji ->
            val selected = targetMsg.reactions[currentUserId] == emoji
            Surface(
              onClick = {
                onToggleReaction(targetMsg, emoji)
                selectedMessageForActions = null
              },
              shape = CircleShape,
              color =
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.size(48.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = 22.sp)
              }
            }
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ActionSheetRow(
          icon = Icons.AutoMirrored.Rounded.Reply,
          label = strings.reply,
          onClick = {
            replyingTo = targetMsg
            editingMessage = null
            selectedMessageForActions = null
          },
        )
        ActionSheetRow(
          icon = Icons.AutoMirrored.Rounded.Forward,
          label = strings.forward,
          onClick = {
            messageToForward = targetMsg
            selectedMessageForActions = null
          },
        )
        if (targetMsg.senderId == currentUserId) {
          if (targetMsg.type == "text") {
            ActionSheetRow(
              icon = Icons.Rounded.Edit,
              label = strings.edit,
              onClick = {
                editingMessage = targetMsg
                replyingTo = null
                composerText = targetMsg.text
                selectedMessageForActions = null
              },
            )
          }
          ActionSheetRow(
            icon = Icons.Rounded.Delete,
            label = strings.delete,
            tint = MaterialTheme.colorScheme.error,
            onClick = {
              onDeleteMessage(targetMsg.id)
              selectedMessageForActions = null
            },
          )
        }
      }
    }
  }

  // Forward Message Dialog
  val forwardMsg = messageToForward
  if (forwardMsg != null) {
    AlertDialog(
      onDismissRequest = { messageToForward = null },
      title = { Text(strings.forwardToTitle, fontWeight = FontWeight.Bold) },
      text = {
        val candidates = allChats.filter { it.id != room.id }
        if (candidates.isEmpty()) {
          Text(strings.noChatsTitle)
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(candidates, key = { it.id }) { targetRoom ->
              Surface(
                onClick = {
                  onForwardMessage(targetRoom, forwardMsg)
                  messageToForward = null
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  TawasulAvatar(
                    displayName = targetRoom.otherDisplayName(currentUserId),
                    avatarUrl = targetRoom.otherAvatarUrl(currentUserId),
                    size = 40.dp,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = targetRoom.otherDisplayName(currentUserId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { messageToForward = null }) { Text(strings.cancel) }
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
  message: ChatMessage,
  isMine: Boolean,
  otherUid: String,
  isArabic: Boolean,
  voicePlaybackState: VoicePlaybackState,
  onToggleVoicePlay: () -> Unit,
  onFileOpenError: () -> Unit,
  onLongOrClick: () -> Unit,
  onReactionTap: (String) -> Unit,
) {
  val strings = LocalAppStrings.current
  val bubbleShape =
    RoundedCornerShape(
      topStart = 20.dp,
      topEnd = 20.dp,
      bottomStart = if (isMine) 20.dp else 6.dp,
      bottomEnd = if (isMine) 6.dp else 20.dp,
    )
  val containerColor =
    if (isMine) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
  val textColor =
    if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

  val isReadByOther = message.readBy.contains(otherUid)

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
  ) {
    Surface(
      modifier =
        Modifier.widthIn(max = 310.dp)
          .clip(bubbleShape)
          .combinedClickable(
            onClick = onLongOrClick,
            onLongClick = onLongOrClick,
          )
          .testTag("message_bubble_${message.id}"),
      shape = bubbleShape,
      color = containerColor,
      border =
        if (!isMine) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else null,
      tonalElevation = if (isMine) 0.dp else 1.dp,
    ) {
      Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (message.isForwarded && !message.isDeleted) {
          Text(
            text = "↪ ${strings.forwardedLabel}",
            style = MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }

        if (!message.replyToId.isNullOrBlank() && !message.isDeleted) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
          ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
              Text(
                text = message.replyToSenderName.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
              )
              Text(
                text = message.replyToText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (message.isDeleted) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Block,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = strings.messageDeletedText,
              style = MaterialTheme.typography.bodyMedium,
              fontStyle = FontStyle.Italic,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          when (message.type) {
            "image" ->
              ImageAttachmentContent(
                mediaData = message.mediaData,
                caption = message.text,
                textColor = textColor,
              )
            "voice" ->
              VoiceMessageContent(
                messageId = message.id,
                mediaData = message.mediaData,
                durationMs = message.mediaDurationMs,
                playbackState = voicePlaybackState,
                onTogglePlay = onToggleVoicePlay,
              )
            "file" ->
              FileAttachmentContent(
                fileName = message.fileName,
                mimeType = message.mimeType,
                fileSizeBytes = message.fileSizeBytes,
                mediaData = message.mediaData,
                onOpenError = onFileOpenError,
              )
            else ->
              Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
              )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (message.isEdited && !message.isDeleted) {
            Text(
              text = strings.editedLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = formatMessageTime(message.createdAt, isArabic),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (isMine && !message.isDeleted) {
            Icon(
              imageVector = if (isReadByOther) Icons.Rounded.DoneAll else Icons.Rounded.Done,
              contentDescription = null,
              tint =
                if (isReadByOther) TawasulReadBlue
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    // Reactions row
    if (message.reactions.isNotEmpty() && !message.isDeleted) {
      val grouped = message.reactions.values.groupingBy { it }.eachCount()
      Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        grouped.forEach { (emoji, count) ->
          Surface(
            onClick = { onReactionTap(emoji) },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(text = emoji, fontSize = 13.sp)
              if (count > 1) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = count.toString(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ActionSheetRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(16.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium,
      color = tint,
    )
  }
}
