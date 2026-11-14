package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.data.model.ChatRoom
import com.example.data.model.UiState
import com.example.data.model.UsernameDirectoryEntry
import com.example.ui.components.TawasulAvatar
import com.example.ui.components.formatChatListTime

@Composable
fun ChatsListPane(
  currentUserId: String,
  chatsState: UiState<List<ChatRoom>>,
  filteredChats: List<ChatRoom>,
  archivedCount: Int,
  showArchived: Boolean,
  searchQuery: String,
  selectedChatId: String?,
  partnersOnline: Map<String, Boolean>,
  isArabic: Boolean,
  lookupLoading: Boolean,
  lookupResult: UsernameDirectoryEntry?,
  lookupError: String?,
  onSearchQueryChange: (String) -> Unit,
  onToggleShowArchived: () -> Unit,
  onSelectChat: (ChatRoom) -> Unit,
  onTogglePin: (ChatRoom) -> Unit,
  onToggleArchive: (ChatRoom) -> Unit,
  onToggleMute: (ChatRoom) -> Unit,
  onSearchUsername: (String) -> Unit,
  onClearLookup: () -> Unit,
  onStartChatWithEntry: (UsernameDirectoryEntry) -> Unit,
  onSaveContact: (UsernameDirectoryEntry) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  var showNewChatDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          onClearLookup()
          showNewChatDialog = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("new_chat_fab"),
      ) {
        Icon(
          imageVector = Icons.Rounded.AddComment,
          contentDescription = strings.startNewChat,
        )
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // Search Field
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth().testTag("chats_search_input"),
        placeholder = {
          Text(
            text = strings.searchChatsPlaceholder,
            style = MaterialTheme.typography.bodyMedium,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = strings.searchButton,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.cancel,
              )
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          ),
      )

      // Archived Chats Toggle Bar
      AnimatedVisibility(visible = archivedCount > 0 || showArchived) {
        Surface(
          onClick = onToggleShowArchived,
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("toggle_archived_chats"),
          shape = RoundedCornerShape(14.dp),
          color =
            if (showArchived) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (showArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text =
                  if (showArchived) strings.hideArchived
                  else strings.archivedCount(archivedCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      when (chatsState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
        is UiState.Error -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
              text = chatsState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        is UiState.Success -> {
          if (filteredChats.isEmpty()) {
            EmptyChatsPlaceholder(
              onNewChat = {
                onClearLookup()
                showNewChatDialog = true
              }
            )
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 88.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filteredChats, key = { it.id }) { room ->
                val otherUsername = room.otherUsername(currentUserId).lowercase()
                val isOtherOnline = partnersOnline[otherUsername] == true
                ChatListItemCard(
                  room = room,
                  currentUserId = currentUserId,
                  isSelected = room.id == selectedChatId,
                  isOtherOnline = isOtherOnline,
                  isArabic = isArabic,
                  onClick = { onSelectChat(room) },
                  onTogglePin = { onTogglePin(room) },
                  onToggleArchive = { onToggleArchive(room) },
                  onToggleMute = { onToggleMute(room) },
                )
              }
            }
          }
        }
      }
    }
  }

  if (showNewChatDialog) {
    NewChatByUsernameDialog(
      lookupLoading = lookupLoading,
      lookupResult = lookupResult,
      lookupError = lookupError,
      onDismiss = {
        onClearLookup()
        showNewChatDialog = false
      },
      onSearch = onSearchUsername,
      onStartChat = { entry ->
        onStartChatWithEntry(entry)
        onClearLookup()
        showNewChatDialog = false
      },
      onSaveContact = onSaveContact,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItemCard(
  room: ChatRoom,
  currentUserId: String,
  isSelected: Boolean,
  isOtherOnline: Boolean,
  isArabic: Boolean,
  onClick: () -> Unit,
  onTogglePin: () -> Unit,
  onToggleArchive: () -> Unit,
  onToggleMute: () -> Unit,
) {
  val strings = LocalAppStrings.current
  var menuExpanded by remember { mutableStateOf(false) }

  val otherName = room.otherDisplayName(currentUserId)
  val otherUsername = room.otherUsername(currentUserId)
  val otherAvatar = room.otherAvatarUrl(currentUserId)
  val unreadCount = room.unreadFor(currentUserId)
  val isPinned = room.isPinnedBy(currentUserId)
  val isArchived = room.isArchivedBy(currentUserId)
  val isMuted = room.isMutedBy(currentUserId)
  val isOtherTyping = room.isOtherTyping(currentUserId)

  val previewText =
    when {
      isOtherTyping -> strings.typingNow
      room.lastMessageText.isBlank() -> "@$otherUsername"
      room.lastMessageSenderId == currentUserId -> "${strings.youPrefix}${room.lastMessageText}"
      else -> room.lastMessageText
    }

  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .combinedClickable(
          onClick = onClick,
          onLongClick = { menuExpanded = true },
        )
        .testTag("chat_item_${room.id}"),
    shape = RoundedCornerShape(20.dp),
    color =
      if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
      else MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = if (isSelected) 1.5.dp else 1.dp,
        color =
          if (isSelected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TawasulAvatar(
        displayName = otherName,
        avatarUrl = otherAvatar,
        size = 52.dp,
        isOnline = isOtherOnline,
        showOnlineDot = true,
      )

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = otherName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (isPinned) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = strings.pinnedLabel,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(15.dp),
              )
            }
            if (isMuted) {
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Rounded.NotificationsOff,
                contentDescription = strings.mutedLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
              )
            }
          }

          Text(
            text = formatChatListTime(room.lastMessageAt ?: room.createdAt, isArabic),
            style = MaterialTheme.typography.labelSmall,
            color =
              if (unreadCount > 0) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = previewText,
            style = MaterialTheme.typography.bodyMedium,
            color =
              if (isOtherTyping) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isOtherTyping || unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
            if (unreadCount > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier =
                  Modifier.clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = unreadCount.toString(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              }
            }

            Box {
              IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.MoreVert,
                  contentDescription = strings.pinChat,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
              DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
              ) {
                DropdownMenuItem(
                  text = { Text(if (isPinned) strings.unpinChat else strings.pinChat) },
                  leadingIcon = {
                    Icon(Icons.Rounded.PushPin, contentDescription = null)
                  },
                  onClick = {
                    menuExpanded = false
                    onTogglePin()
                  },
                )
                DropdownMenuItem(
                  text = { Text(if (isMuted) strings.unmuteChat else strings.muteChat) },
                  leadingIcon = {
                    Icon(Icons.Rounded.NotificationsOff, contentDescription = null)
                  },
                  onClick = {
                    menuExpanded = false
                    onToggleMute()
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
                    menuExpanded = false
                    onToggleArchive()
                  },
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
private fun EmptyChatsPlaceholder(onNewChat: () -> Unit) {
  val strings = LocalAppStrings.current
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
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
      text = strings.noChatsTitle,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = strings.noChatsSubtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(
      onClick = onNewChat,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.height(48.dp).testTag("empty_new_chat_button"),
    ) {
      Icon(Icons.Rounded.AddComment, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(strings.startNewChat, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
fun NewChatByUsernameDialog(
  lookupLoading: Boolean,
  lookupResult: UsernameDirectoryEntry?,
  lookupError: String?,
  onDismiss: () -> Unit,
  onSearch: (String) -> Unit,
  onStartChat: (UsernameDirectoryEntry) -> Unit,
  onSaveContact: (UsernameDirectoryEntry) -> Unit,
) {
  val strings = LocalAppStrings.current
  var query by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = strings.newChatTitle,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f).testTag("username_lookup_input"),
            placeholder = { Text(strings.enterUsernameHint, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { onSearch(query) },
            enabled = query.isNotBlank() && !lookupLoading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(52.dp).testTag("username_lookup_button"),
          ) {
            Text(strings.searchButton)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (lookupLoading) {
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
          }
        } else if (!lookupError.isNullOrBlank()) {
          Text(
            text = lookupError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        } else if (lookupResult != null) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                TawasulAvatar(
                  displayName = lookupResult.displayName,
                  avatarUrl = lookupResult.avatarUrl,
                  size = 48.dp,
                  isOnline = lookupResult.isOnline,
                  showOnlineDot = true,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = lookupResult.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "@${lookupResult.username}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                }
              }
              if (!lookupResult.bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = lookupResult.bio,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = { onSaveContact(lookupResult) },
                  modifier = Modifier.weight(1f).height(48.dp),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(strings.addContactAction, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                  onClick = { onStartChat(lookupResult) },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("start_chat_confirm_button"),
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                  Text(strings.startChatAction, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(strings.cancel)
      }
    },
  )
}
