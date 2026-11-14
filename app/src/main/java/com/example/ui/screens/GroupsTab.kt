package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.LocalAppStrings
import com.example.core.media.VoicePlayerController
import com.example.core.media.VoiceRecorderHelper
import com.example.core.util.FileReadResult
import com.example.core.util.compressUriToDataUri
import com.example.core.util.readDocumentToAttachment
import com.example.data.model.ContactEntry
import com.example.data.model.GroupMessage
import com.example.data.model.GroupRoom
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UsernameDirectoryEntry
import com.example.ui.components.FileAttachmentContent
import com.example.ui.components.ImageAttachmentContent
import com.example.ui.components.TawasulAvatar
import com.example.ui.components.VoiceMessageContent
import com.example.ui.components.VoiceRecordingBar
import com.example.ui.components.formatChatListTime
import com.example.ui.components.formatMessageTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GroupsTab(
  currentUserId: String,
  myProfile: UserProfile?,
  contacts: List<ContactEntry>,
  groupsState: UiState<List<GroupRoom>>,
  searchQuery: String,
  busy: Boolean,
  isArabic: Boolean,
  onOpenGroup: (String) -> Unit,
  onCreateGroup: (name: String, description: String, members: List<ContactEntry>, onDone: () -> Unit) -> Unit,
  onJoinByInviteCode: (code: String, onDone: () -> Unit) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  var showCreateDialog by remember { mutableStateOf(false) }
  var showJoinDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showCreateDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("create_group_fab"),
      ) {
        Icon(Icons.Rounded.GroupAdd, contentDescription = strings.createGroup)
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { showJoinDialog = true },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("join_group_code_button"),
        ) {
          Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.joinGroup, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
          onClick = { showCreateDialog = true },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("new_group_button"),
        ) {
          Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.createGroup, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      when (groupsState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
        is UiState.Error -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(groupsState.message, color = MaterialTheme.colorScheme.error)
          }
        }
        is UiState.Success -> {
          val q = searchQuery.trim().lowercase()
          val groups =
            groupsState.data.filter { group ->
              q.isEmpty() ||
                group.name.lowercase().contains(q) ||
                group.lastMessageText.lowercase().contains(q)
            }

          if (groups.isEmpty()) {
            Column(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Box(
                modifier =
                  Modifier.size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Groups,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(38.dp),
                )
              }
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = strings.noGroupsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = strings.noGroupsSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 92.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(groups, key = { it.id }) { group ->
                Surface(
                  onClick = { onOpenGroup(group.id) },
                  shape = RoundedCornerShape(20.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.fillMaxWidth().testTag("group_item_${group.id}"),
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    TawasulAvatar(displayName = group.name, avatarUrl = null, size = 50.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = group.name,
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f),
                        )
                        Text(
                          text = formatChatListTime(group.lastMessageAt ?: group.createdAt, isArabic),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                        text =
                          group.lastMessageText.ifBlank {
                            strings.groupMembersCount(group.memberIds.size)
                          },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showCreateDialog && myProfile != null) {
    CreateGroupDialog(
      contacts = contacts,
      busy = busy,
      onDismiss = { showCreateDialog = false },
      onCreate = { name, desc, selected ->
        onCreateGroup(name, desc, selected) { showCreateDialog = false }
      },
    )
  }

  if (showJoinDialog && myProfile != null) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showJoinDialog = false },
      title = { Text(strings.joinByCodeTitle, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = code,
          onValueChange = { code = it.uppercase() },
          label = { Text(strings.inviteCodeLabel) },
          placeholder = { Text(strings.enterInviteCodeHint) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("join_group_code_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = { onJoinByInviteCode(code) { showJoinDialog = false } },
          enabled = !busy && code.trim().length >= 4,
          modifier = Modifier.testTag("confirm_join_group_button"),
        ) {
          Text(strings.joinGroup)
        }
      },
      dismissButton = {
        TextButton(onClick = { showJoinDialog = false }) { Text(strings.cancel) }
      },
    )
  }
}

@Composable
fun GroupDetailScreen(
  currentUserId: String,
  group: GroupRoom?,
  messagesState: UiState<List<GroupMessage>>,
  lookupLoading: Boolean,
  lookupResult: UsernameDirectoryEntry?,
  lookupError: String?,
  isArabic: Boolean,
  onBack: () -> Unit,
  onSendMessage: (String) -> Unit,
  onSendMediaMessage: (
    type: String,
    mediaData: String,
    previewText: String,
    caption: String,
    fileName: String?,
    mimeType: String?,
    fileSizeBytes: Long?,
    mediaDurationMs: Long?,
  ) -> Unit,
  onSearchUsername: (String) -> Unit,
  onClearLookup: () -> Unit,
  onAddMember: (UsernameDirectoryEntry) -> Unit,
  onInviteCopied: (String) -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val groupKey = group?.id ?: "none"

  var inputText by remember(groupKey) { mutableStateOf("") }
  var showAddMemberDialog by remember { mutableStateOf(false) }
  var attachMenuExpanded by remember { mutableStateOf(false) }
  var isRecording by remember(groupKey) { mutableStateOf(false) }
  var recordingElapsedMs by remember(groupKey) { mutableLongStateOf(0L) }

  val voiceRecorder = remember(groupKey) { VoiceRecorderHelper(context) }
  val voicePlayer = remember(groupKey) { VoicePlayerController(context, scope) }
  val voicePlaybackState by voicePlayer.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()

  DisposableEffect(groupKey) {
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

  LaunchedEffect(groupKey, messagesState) {
    val msgs = (messagesState as? UiState.Success)?.data ?: return@LaunchedEffect
    if (msgs.isNotEmpty()) {
      listState.animateScrollToItem(msgs.lastIndex)
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
            val caption = inputText.trim()
            onSendMediaMessage(
              "image",
              dataUri,
              strings.photoMessagePreview,
              caption,
              "photo_${System.currentTimeMillis()}.jpg",
              "image/jpeg",
              null,
              null,
            )
            inputText = ""
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
              )
            }
            FileReadResult.TooLarge -> onShowMessage(strings.fileTooLargeError)
            FileReadResult.Error -> onShowMessage(strings.errorGeneric)
          }
        }
      }
    }

  Column(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
      Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
        }
        TawasulAvatar(displayName = group?.name ?: "G", avatarUrl = null, size = 40.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = group?.name ?: "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (group != null) {
            Text(
              text = "${strings.groupMembersCount(group.memberIds.size)} • ${group.inviteCode}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
        if (group != null) {
          IconButton(
            onClick = {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              cm?.setPrimaryClip(ClipData.newPlainText("invite", group.inviteCode))
              onInviteCopied(strings.inviteCodeCopied)
            },
            modifier = Modifier.size(48.dp),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = strings.shareInviteCode)
          }
          if (group.isAdmin(currentUserId)) {
            IconButton(
              onClick = {
                onClearLookup()
                showAddMemberDialog = true
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(Icons.Rounded.PersonAdd, contentDescription = strings.addMemberTitle)
            }
          }
        }
      }
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (messagesState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
        is UiState.Error -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(messagesState.message, color = MaterialTheme.colorScheme.error)
          }
        }
        is UiState.Success -> {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(messagesState.data, key = { it.id }) { msg ->
              val isMe = msg.senderId == currentUserId
              val textColor =
                if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
              ) {
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color =
                    if (isMe) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.widthIn(max = 310.dp),
                ) {
                  Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!isMe) {
                      Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                    }
                    when (msg.type) {
                      "image" ->
                        ImageAttachmentContent(
                          mediaData = msg.mediaData,
                          caption = msg.text,
                          textColor = textColor,
                        )
                      "voice" ->
                        VoiceMessageContent(
                          messageId = msg.id,
                          mediaData = msg.mediaData,
                          durationMs = msg.mediaDurationMs,
                          playbackState = voicePlaybackState,
                          onTogglePlay = {
                            voicePlayer.togglePlay(msg.id, msg.mediaData, msg.mediaDurationMs ?: 0L)
                          },
                        )
                      "file" ->
                        FileAttachmentContent(
                          fileName = msg.fileName,
                          mimeType = msg.mimeType,
                          fileSizeBytes = msg.fileSizeBytes,
                          mediaData = msg.mediaData,
                          onOpenError = { onShowMessage(strings.openFileError) },
                        )
                      else ->
                        Text(
                          text = msg.text,
                          style = MaterialTheme.typography.bodyMedium,
                          color = textColor,
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = formatMessageTime(msg.createdAt, isArabic),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.align(Alignment.End),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 3.dp,
      modifier = Modifier.navigationBarsPadding(),
    ) {
      Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                )
              } else {
                onShowMessage(strings.voiceTooShortOrLarge)
              }
            },
          )
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box {
              IconButton(
                onClick = { attachMenuExpanded = true },
                modifier = Modifier.size(48.dp).testTag("group_attach_button"),
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

            OutlinedTextField(
              value = inputText,
              onValueChange = { inputText = it },
              placeholder = { Text(strings.writeMessageHint) },
              maxLines = 3,
              shape = RoundedCornerShape(22.dp),
              modifier = Modifier.weight(1f).testTag("group_message_input"),
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (inputText.isNotBlank()) {
              Surface(
                onClick = {
                  val clean = inputText.trim()
                  if (clean.isNotEmpty()) {
                    onSendMessage(clean)
                    inputText = ""
                  }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp).testTag("send_group_message_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = strings.send,
                    tint = MaterialTheme.colorScheme.onPrimary,
                  )
                }
              }
            } else {
              Surface(
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
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp).testTag("group_record_voice_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Rounded.Mic,
                    contentDescription = strings.recordVoiceTooltip,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showAddMemberDialog) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = {
        onClearLookup()
        showAddMemberDialog = false
      },
      title = { Text(strings.addMemberTitle, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = q,
              onValueChange = { q = it },
              placeholder = { Text(strings.enterUsernameHint) },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onSearchUsername(q) }, enabled = q.isNotBlank() && !lookupLoading) {
              Text(strings.searchButton)
            }
          }
          if (lookupLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
          } else if (!lookupError.isNullOrBlank()) {
            Text(lookupError, color = MaterialTheme.colorScheme.error)
          } else if (lookupResult != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              TawasulAvatar(displayName = lookupResult.displayName, avatarUrl = lookupResult.avatarUrl, size = 40.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(lookupResult.displayName, fontWeight = FontWeight.Bold)
                Text("@${lookupResult.username}", style = MaterialTheme.typography.labelSmall)
              }
              Button(
                onClick = {
                  onAddMember(lookupResult)
                  onClearLookup()
                  showAddMemberDialog = false
                }
              ) {
                Text(strings.addContactAction)
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          onClearLookup()
          showAddMemberDialog = false
        }) { Text(strings.cancel) }
      },
    )
  }
}

@Composable
private fun CreateGroupDialog(
  contacts: List<ContactEntry>,
  busy: Boolean,
  onDismiss: () -> Unit,
  onCreate: (name: String, description: String, members: List<ContactEntry>) -> Unit,
) {
  val strings = LocalAppStrings.current
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  val selectedUids = remember { mutableStateListOf<String>() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.createGroup, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(80) },
          label = { Text(strings.groupNameLabel) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("create_group_name_input"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it.take(200) },
          label = { Text(strings.groupDescLabel) },
          maxLines = 2,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        if (contacts.isNotEmpty()) {
          Text(strings.selectMembersLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          LazyColumn(modifier = Modifier.height(140.dp)) {
            items(contacts, key = { it.contactUid }) { contact ->
              val checked = selectedUids.contains(contact.contactUid)
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      if (checked) selectedUids.remove(contact.contactUid)
                      else selectedUids.add(contact.contactUid)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = { isChecked ->
                    if (isChecked) {
                      if (!selectedUids.contains(contact.contactUid)) selectedUids.add(contact.contactUid)
                    } else {
                      selectedUids.remove(contact.contactUid)
                    }
                  },
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("${contact.displayName} (@${contact.username})", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val members = contacts.filter { selectedUids.contains(it.contactUid) }
          onCreate(name, description, members)
        },
        enabled = !busy && name.isNotBlank(),
        modifier = Modifier.testTag("confirm_create_group_button"),
      ) {
        Text(strings.createGroup)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel) }
    },
  )
}
