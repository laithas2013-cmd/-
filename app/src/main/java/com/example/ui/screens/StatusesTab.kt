package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.i18n.LocalAppStrings
import com.example.core.util.compressUriToDataUri
import com.example.core.util.decodeDataUriToBitmap
import com.example.data.model.ContactEntry
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserStatus
import com.example.ui.components.TawasulAvatar
import com.example.ui.components.formatChatListTime
import com.example.ui.theme.StatusSwatchColors
import kotlinx.coroutines.launch

@Composable
fun StatusesTab(
  currentUserId: String,
  myProfile: UserProfile?,
  contacts: List<ContactEntry>,
  statusesState: UiState<List<UserStatus>>,
  searchQuery: String,
  busy: Boolean,
  isArabic: Boolean,
  onPublishStatus: (
    type: String,
    text: String,
    imageData: String?,
    backgroundHex: String,
    privacy: String,
    onDone: () -> Unit,
  ) -> Unit,
  onViewStatus: (UserStatus) -> Unit,
  onDeleteStatus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  var showCreateDialog by remember { mutableStateOf(false) }
  var viewingStatus by remember { mutableStateOf<UserStatus?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showCreateDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("add_status_fab"),
      ) {
        Icon(Icons.Rounded.AddAPhoto, contentDescription = strings.addStatus)
      }
    },
  ) { innerPadding ->
    when (statusesState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
      is UiState.Error -> {
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
          Text(statusesState.message, color = MaterialTheme.colorScheme.error)
        }
      }
      is UiState.Success -> {
        val q = searchQuery.trim().lowercase()
        val allStatuses =
          statusesState.data.filter { status ->
            q.isEmpty() ||
              status.authorName.lowercase().contains(q) ||
              status.authorUsername.lowercase().contains(q.removePrefix("@")) ||
              status.text.lowercase().contains(q)
          }
        val myStatuses = allStatuses.filter { it.authorId == currentUserId }
        val friendStatuses = allStatuses.filter { it.authorId != currentUserId }

        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
          contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoStories,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = strings.statusExpires24h,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }

          if (myProfile != null) {
            item {
              Text(
                text = strings.myStatus,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
            if (myStatuses.isEmpty()) {
              item {
                Surface(
                  onClick = { showCreateDialog = true },
                  shape = RoundedCornerShape(20.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.fillMaxWidth().testTag("my_empty_status_card"),
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    TawasulAvatar(
                      displayName = myProfile.displayName,
                      avatarUrl = myProfile.avatarUrl,
                      size = 52.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = strings.addStatus,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = strings.statusExpires24h,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            } else {
              items(myStatuses, key = { it.id }) { status ->
                StatusCardItem(
                  status = status,
                  isMine = true,
                  isViewed = true,
                  isArabic = isArabic,
                  onClick = {
                    viewingStatus = status
                    onViewStatus(status)
                  },
                  onDelete = { onDeleteStatus(status.id) },
                )
              }
            }
          }

          item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = strings.recentUpdates,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.secondary,
            )
          }

          if (friendStatuses.isEmpty() && myStatuses.isEmpty()) {
            item {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  text = strings.noStatusesTitle,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = strings.noStatusesSubtitle,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }
            }
          } else {
            items(friendStatuses, key = { it.id }) { status ->
              StatusCardItem(
                status = status,
                isMine = false,
                isViewed = status.viewedBy.contains(currentUserId),
                isArabic = isArabic,
                onClick = {
                  viewingStatus = status
                  onViewStatus(status)
                },
                onDelete = null,
              )
            }
          }
        }
      }
    }
  }

  if (showCreateDialog && myProfile != null) {
    CreateStatusDialog(
      busy = busy,
      onDismiss = { showCreateDialog = false },
      onPublish = { type, text, imageData, bgHex, privacy ->
        onPublishStatus(type, text, imageData, bgHex, privacy) {
          showCreateDialog = false
        }
      },
    )
  }

  viewingStatus?.let { status ->
    StatusViewerDialog(
      status = status,
      isMine = status.authorId == currentUserId,
      contacts = contacts,
      isArabic = isArabic,
      onDismiss = { viewingStatus = null },
      onDelete = {
        onDeleteStatus(status.id)
        viewingStatus = null
      },
    )
  }
}

@Composable
private fun StatusCardItem(
  status: UserStatus,
  isMine: Boolean,
  isViewed: Boolean,
  isArabic: Boolean,
  onClick: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  val strings = LocalAppStrings.current
  val ringColor =
    if (isViewed) MaterialTheme.colorScheme.outlineVariant
    else MaterialTheme.colorScheme.primary

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().testTag("status_item_${status.id}"),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.border(2.5.dp, ringColor, CircleShape).padding(3.dp)
      ) {
        TawasulAvatar(
          displayName = status.authorName,
          avatarUrl = status.authorAvatar,
          size = 48.dp,
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (isMine) strings.myStatus else status.authorName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            status.text.ifBlank { strings.statusTypePhoto } +
              " • " +
              formatChatListTime(status.createdAt, isArabic),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (isMine) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = (status.viewedBy.size - 1).coerceAtLeast(0).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
          if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = strings.delete,
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CreateStatusDialog(
  busy: Boolean,
  onDismiss: () -> Unit,
  onPublish: (type: String, text: String, imageData: String?, bgHex: String, privacy: String) -> Unit,
) {
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var isPhotoMode by remember { mutableStateOf(false) }
  var text by remember { mutableStateOf("") }
  var selectedHex by remember { mutableStateOf(StatusSwatchColors.keys.first()) }
  var privacy by remember { mutableStateOf("contacts") }
  var imageData by remember { mutableStateOf<String?>(null) }
  var processing by remember { mutableStateOf(false) }

  val pickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          processing = true
          val compressed = compressUriToDataUri(context, uri, maxDimension = 320, maxDataUriLength = 25000)
          processing = false
          if (compressed != null) {
            imageData = compressed
            isPhotoMode = true
          }
        }
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.addStatus, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = !isPhotoMode,
            onClick = { isPhotoMode = false },
            label = { Text(strings.statusTypeText) },
          )
          FilterChip(
            selected = isPhotoMode,
            onClick = { isPhotoMode = true },
            label = { Text(strings.statusTypePhoto) },
          )
        }

        if (isPhotoMode) {
          OutlinedButton(
            onClick = {
              pickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            enabled = !processing,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("status_pick_photo_btn"),
          ) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (processing) strings.processingImage else strings.pickPhoto)
          }
          val bmp = remember(imageData) { decodeDataUriToBitmap(imageData) }
          if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = strings.statusTypePhoto,
              modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)),
              contentScale = ContentScale.Crop,
            )
          }
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusSwatchColors.forEach { (hex, color) ->
              Box(
                modifier =
                  Modifier.size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                      width = if (selectedHex == hex) 3.dp else 1.dp,
                      color =
                        if (selectedHex == hex) MaterialTheme.colorScheme.onSurface
                        else Color.Transparent,
                      shape = CircleShape,
                    )
                    .clickable { selectedHex = hex }
              )
            }
          }
        }

        OutlinedTextField(
          value = text,
          onValueChange = { text = it.take(500) },
          placeholder = { Text(strings.statusTextHint) },
          maxLines = 4,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("status_text_input"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = privacy == "contacts",
            onClick = { privacy = "contacts" },
            leadingIcon = { Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text(strings.privacyContacts) },
          )
          FilterChip(
            selected = privacy == "only_me",
            onClick = { privacy = "only_me" },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text(strings.privacyOnlyMe) },
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onPublish(
            if (isPhotoMode && !imageData.isNullOrBlank()) "image" else "text",
            text,
            imageData,
            selectedHex,
            privacy,
          )
        },
        enabled =
          !busy && !processing &&
            (if (isPhotoMode) !imageData.isNullOrBlank() else text.isNotBlank()),
        modifier = Modifier.testTag("publish_status_button"),
      ) {
        Text(strings.publishStatus)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel) }
    },
  )
}

@Composable
private fun StatusViewerDialog(
  status: UserStatus,
  isMine: Boolean,
  contacts: List<ContactEntry>,
  isArabic: Boolean,
  onDismiss: () -> Unit,
  onDelete: () -> Unit,
) {
  val strings = LocalAppStrings.current
  val bgColor = StatusSwatchColors[status.backgroundHex] ?: MaterialTheme.colorScheme.primary
  val bmp = remember(status.imageData) { decodeDataUriToBitmap(status.imageData) }
  val viewerNames = remember(status.viewedBy, contacts) {
    val map = contacts.associateBy { it.contactUid }
    status.viewedBy.filter { it != status.authorId }.map { uid ->
      map[uid]?.displayName ?: "@${uid.take(6)}"
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = if (bmp != null) MaterialTheme.colorScheme.scrim else bgColor,
    ) {
      Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            TawasulAvatar(
              displayName = status.authorName,
              avatarUrl = status.authorAvatar,
              size = 44.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = status.authorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Text(
                text = formatChatListTime(status.createdAt, isArabic),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
              )
            }
          }

          Row {
            if (isMine) {
              IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = strings.delete, tint = Color.White)
              }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Rounded.Close, contentDescription = strings.cancel, tint = Color.White)
            }
          }
        }

        // Content
        Column(
          modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = status.text.ifBlank { status.authorName },
              modifier = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(20.dp)),
              contentScale = ContentScale.Fit,
            )
            if (status.text.isNotBlank()) {
              Spacer(modifier = Modifier.height(16.dp))
            }
          }
          if (status.text.isNotBlank()) {
            Text(
              text = status.text,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              textAlign = TextAlign.Center,
            )
          }
        }

        // Views footer
        if (isMine) {
          Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.45f),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = strings.statusViewsCount(viewerNames.size),
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.labelLarge,
                )
              }
              if (viewerNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "${strings.viewedByTitle}: ${viewerNames.joinToString(", ")}",
                  color = Color.White.copy(alpha = 0.9f),
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
      }
    }
  }
}
