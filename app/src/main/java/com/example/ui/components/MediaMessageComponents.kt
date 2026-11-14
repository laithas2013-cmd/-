package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.i18n.LocalAppStrings
import com.example.core.media.VoicePlaybackState
import com.example.core.media.formatDurationMs
import com.example.core.util.decodeDataUriToBitmap
import com.example.core.util.formatFileSize
import com.example.core.util.openAttachmentWithSystem

@Composable
fun ImageAttachmentContent(
  mediaData: String?,
  caption: String,
  textColor: Color,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val bitmap = remember(mediaData) { decodeDataUriToBitmap(mediaData) }
  var showFullPreview by remember { mutableStateOf(false) }

  Column(modifier = modifier) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = strings.photoMessagePreview,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 140.dp, max = 240.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { showFullPreview = true },
      )
    }
    if (caption.isNotBlank()) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = caption,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
      )
    }
  }

  if (showFullPreview && bitmap != null) {
    Dialog(
      onDismissRequest = { showFullPreview = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = strings.photoMessagePreview,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize().padding(16.dp),
        )
        IconButton(
          onClick = { showFullPreview = false },
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(20.dp)
              .size(48.dp)
              .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = strings.cancel, tint = Color.White)
        }
      }
    }
  }
}

@Composable
fun VoiceMessageContent(
  messageId: String,
  mediaData: String?,
  durationMs: Long?,
  playbackState: VoicePlaybackState,
  onTogglePlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val isActive = playbackState.playingMessageId == messageId
  val isPlaying = isActive && playbackState.isPlaying
  val totalMs =
    if (isActive && playbackState.durationMs > 0L) playbackState.durationMs
    else (durationMs ?: 0L).coerceAtLeast(1000L)
  val progress =
    if (isActive && totalMs > 0L) {
      (playbackState.positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      onClick = onTogglePlay,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(42.dp).testTag("voice_play_$messageId"),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
          contentDescription = strings.voiceMessageLabel,
          tint = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (isActive && playbackState.positionMs > 0L)
              "${formatDurationMs(playbackState.positionMs)} / ${formatDurationMs(totalMs)}"
            else formatDurationMs(totalMs),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
          imageVector = Icons.Rounded.Mic,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
    }
  }
}

@Composable
fun FileAttachmentContent(
  fileName: String?,
  mimeType: String?,
  fileSizeBytes: Long?,
  mediaData: String?,
  onOpenError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val displayName = fileName?.ifBlank { "file" } ?: "file"
  val icon =
    when {
      mimeType?.contains("pdf", ignoreCase = true) == true ||
        displayName.endsWith(".pdf", ignoreCase = true) -> Icons.Rounded.PictureAsPdf
      mimeType?.startsWith("text/") == true -> Icons.Rounded.Description
      else -> Icons.Rounded.InsertDriveFile
    }

  Surface(
    onClick = {
      val ok = openAttachmentWithSystem(context, displayName, mimeType, mediaData)
      if (!ok) onOpenError()
    },
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = displayName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${formatFileSize(fileSizeBytes)} • ${strings.tapToOpenFile}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
fun VoiceRecordingBar(
  elapsedMs: Long,
  onCancel: () -> Unit,
  onStopAndSend: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val infinite = rememberInfiniteTransition(label = "voice_rec")
  val dotScale by
    infinite.animateFloat(
      initialValue = 0.8f,
      targetValue = 1.25f,
      animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
      label = "rec_dot_scale",
    )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = onCancel,
      modifier = Modifier.size(48.dp).testTag("cancel_voice_recording_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Delete,
        contentDescription = strings.cancelRecording,
        tint = MaterialTheme.colorScheme.error,
      )
    }

    Box(
      modifier =
        Modifier.size(12.dp)
          .scale(dotScale)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.error)
    )
    Spacer(modifier = Modifier.width(10.dp))
    Text(
      text = "${strings.recordingVoiceHint} ${formatDurationMs(elapsedMs)}",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )

    IconButton(
      onClick = onStopAndSend,
      modifier =
        Modifier.size(48.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary)
          .testTag("send_voice_recording_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.Send,
        contentDescription = strings.stopAndSendVoice,
        tint = MaterialTheme.colorScheme.onPrimary,
      )
    }
  }
}
