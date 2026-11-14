package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.i18n.LocalAppStrings
import com.example.core.webrtc.TawasulWebRtcClient
import com.example.core.webrtc.WebRtcConnectionState
import com.example.data.model.CallIceCandidate
import com.example.data.model.CallLog
import com.example.data.model.ContactEntry
import com.example.data.model.UiState
import com.example.notifications.CallForegroundService
import com.example.notifications.TawasulNotificationHelper
import com.example.ui.components.TawasulAvatar
import com.example.ui.components.formatChatListTime
import com.example.ui.theme.TawasulOnlineGreen
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallsTab(
  currentUserId: String,
  callsState: UiState<List<CallLog>>,
  contacts: List<ContactEntry>,
  searchQuery: String,
  isArabic: Boolean,
  onStartCall: (calleeUid: String, calleeName: String, calleeAvatar: String?, callType: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  var showNewCallDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showNewCallDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("new_call_fab"),
      ) {
        Icon(Icons.Rounded.AddIcCall, contentDescription = strings.startCall)
      }
    },
  ) { innerPadding ->
    when (callsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
      is UiState.Error -> {
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
          Text(callsState.message, color = MaterialTheme.colorScheme.error)
        }
      }
      is UiState.Success -> {
        val q = searchQuery.trim().lowercase()
        val calls =
          callsState.data.filter { call ->
            q.isEmpty() || call.otherName(currentUserId).lowercase().contains(q)
          }

        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
          contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (calls.isEmpty()) {
            item {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  modifier =
                    Modifier.size(80.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primaryContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                  )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = strings.noCallsTitle,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = strings.noCallsSubtitle,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }
            }
          } else {
            items(calls, key = { it.id }) { call ->
              val isOutgoing = call.isOutgoing(currentUserId)
              val otherUid = if (isOutgoing) call.calleeId else call.callerId
              val otherName = call.otherName(currentUserId)
              val otherAvatar = call.otherAvatar(currentUserId)
              val statusLabel =
                when (call.status) {
                  "ringing" -> strings.callingStatus
                  "active" -> strings.connectedStatus
                  "declined" -> strings.callDeclined
                  "missed" -> strings.callMissed
                  else -> strings.callEnded
                }

              Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("call_log_${call.id}"),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  TawasulAvatar(displayName = otherName, avatarUrl = otherAvatar, size = 48.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = otherName,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector =
                          if (isOutgoing) Icons.AutoMirrored.Rounded.CallMade
                          else Icons.AutoMirrored.Rounded.CallReceived,
                        contentDescription = null,
                        tint =
                          if (call.status == "missed" || call.status == "declined")
                            MaterialTheme.colorScheme.error
                          else TawasulOnlineGreen,
                        modifier = Modifier.size(14.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      val durationStr =
                        if (call.durationSeconds > 0L) " • ${formatDuration(call.durationSeconds)}" else ""
                      Text(
                        text = "$statusLabel$durationStr • ${formatChatListTime(call.createdAt, isArabic)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  IconButton(
                    onClick = { onStartCall(otherUid, otherName, otherAvatar, "audio") },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Call,
                      contentDescription = strings.audioCall,
                      tint = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  IconButton(
                    onClick = { onStartCall(otherUid, otherName, otherAvatar, "video") },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Videocam,
                      contentDescription = strings.videoCall,
                      tint = MaterialTheme.colorScheme.secondary,
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

  if (showNewCallDialog) {
    AlertDialog(
      onDismissRequest = { showNewCallDialog = false },
      title = { Text(strings.startCall, fontWeight = FontWeight.Bold) },
      text = {
        if (contacts.isEmpty()) {
          Text(strings.contactsEmptySubtitle)
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.contactUid }) { contact ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                TawasulAvatar(displayName = contact.displayName, avatarUrl = contact.avatarUrl, size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(contact.displayName, fontWeight = FontWeight.SemiBold)
                  Text("@${contact.username}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(
                  onClick = {
                    showNewCallDialog = false
                    onStartCall(contact.contactUid, contact.displayName, contact.avatarUrl, "audio")
                  },
                  modifier = Modifier.size(48.dp),
                ) {
                  Icon(Icons.Rounded.Call, contentDescription = strings.audioCall, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                  onClick = {
                    showNewCallDialog = false
                    onStartCall(contact.contactUid, contact.displayName, contact.avatarUrl, "video")
                  },
                  modifier = Modifier.size(48.dp),
                ) {
                  Icon(Icons.Rounded.Videocam, contentDescription = strings.videoCall, tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNewCallDialog = false }) { Text(strings.cancel) }
      },
    )
  }
}

@Composable
fun ActiveCallOverlay(
  call: CallLog,
  currentUserId: String,
  iceCandidates: List<CallIceCandidate>,
  onPublishOffer: (String) -> Unit,
  onPublishAnswer: (String) -> Unit,
  onSendIceCandidate: (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
  onEnd: (Long) -> Unit,
) {
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val isVideo = call.callType == "video"
  val isIncomingRinging = call.calleeId == currentUserId && call.status == "ringing"
  val isCaller = call.callerId == currentUserId

  var isMuted by remember(call.id) { mutableStateOf(false) }
  var isCameraOn by remember(call.id) { mutableStateOf(isVideo) }
  var isSpeakerOn by remember(call.id) { mutableStateOf(isVideo) }
  var elapsedSeconds by remember(call.id) { mutableLongStateOf(0L) }

  var hasAudioPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
      hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
    }

  LaunchedEffect(call.id, call.status) {
    if (!isIncomingRinging || call.status == "active") {
      val needed = mutableListOf<String>()
      if (!hasAudioPermission) needed += Manifest.permission.RECORD_AUDIO
      if (isVideo && !hasCameraPermission) needed += Manifest.permission.CAMERA
      if (needed.isNotEmpty()) {
        permissionLauncher.launch(needed.toTypedArray())
      }
    }
  }

  val webRtcClient =
    remember(call.id) {
      TawasulWebRtcClient(
        context = context,
        isVideoCall = isVideo,
        onLocalOfferReady = onPublishOffer,
        onLocalAnswerReady = onPublishAnswer,
        onLocalIceCandidate = onSendIceCandidate,
      )
    }

  val connectionState by webRtcClient.connectionState.collectAsStateWithLifecycle()
  val remoteVideoAvailable by webRtcClient.remoteVideoAvailable.collectAsStateWithLifecycle()

  var initializedRtc by remember(call.id) { mutableStateOf(false) }
  var localRendererRef by remember(call.id) { mutableStateOf<SurfaceViewRenderer?>(null) }
  var remoteRendererRef by remember(call.id) { mutableStateOf<SurfaceViewRenderer?>(null) }

  // Initialize WebRTC engine once caller initiates or callee answers and audio permission is ready
  LaunchedEffect(call.id, call.status, hasAudioPermission, hasCameraPermission) {
    val shouldStartRtc = (isCaller || call.status == "active") && hasAudioPermission
    if (shouldStartRtc && !initializedRtc) {
      initializedRtc = true
      webRtcClient.initialize(
        hasAudioPermission = hasAudioPermission,
        hasCameraPermission = hasCameraPermission,
      )
      webRtcClient.attachRenderers(localRendererRef, remoteRendererRef)
      if (isCaller && call.offerSdp.isNullOrBlank()) {
        webRtcClient.createOffer()
      }
    }
  }

  // Exchange SDP offer / answer via Firestore
  LaunchedEffect(initializedRtc, call.offerSdp, call.answerSdp, call.status) {
    if (!initializedRtc) return@LaunchedEffect
    if (!isCaller && call.status == "active" && !call.offerSdp.isNullOrBlank()) {
      webRtcClient.handleRemoteOfferAndCreateAnswer(call.offerSdp)
    } else if (isCaller && !call.answerSdp.isNullOrBlank()) {
      webRtcClient.handleRemoteAnswer(call.answerSdp)
    }
  }

  // Feed incoming ICE candidates into WebRTC PeerConnection
  LaunchedEffect(initializedRtc, iceCandidates) {
    if (initializedRtc && iceCandidates.isNotEmpty()) {
      webRtcClient.addRemoteIceCandidates(iceCandidates, currentUserId)
    }
  }

  // Foreground call notification during active call
  LaunchedEffect(call.id, call.status, hasAudioPermission) {
    if (call.status == "active" && hasAudioPermission) {
      TawasulNotificationHelper.cancelCallNotification(context, call.id)
      CallForegroundService.start(
        context = context,
        callId = call.id,
        peerName = call.otherName(currentUserId),
        callType = call.callType,
      )
    }
  }

  DisposableEffect(call.id) {
    onDispose {
      CallForegroundService.stop(context)
      TawasulNotificationHelper.cancelCallNotification(context, call.id)
      webRtcClient.dispose()
    }
  }

  LaunchedEffect(call.id, call.status) {
    if (call.status == "active") {
      while (true) {
        delay(1000L)
        elapsedSeconds += 1L
      }
    }
  }

  Dialog(
    onDismissRequest = {},
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
      ),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = if (isVideo) Color(0xFF101214) else MaterialTheme.colorScheme.surface,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // Remote full-screen WebRTC Video Renderer
        if (isVideo && (isCaller || call.status == "active")) {
          AndroidView(
            factory = { ctx ->
              SurfaceViewRenderer(ctx).apply {
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                remoteRendererRef = this
                webRtcClient.attachRenderers(localRendererRef, this)
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
        }

        // Local corner PiP WebRTC Video Preview
        if (isVideo && isCameraOn && hasCameraPermission && (isCaller || call.status == "active")) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier =
              Modifier.align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp)
                .size(width = 112.dp, height = 158.dp),
          ) {
            AndroidView(
              factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                  setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                  setEnableHardwareScaler(true)
                  localRendererRef = this
                  webRtcClient.attachRenderers(this, remoteRendererRef)
                }
              },
              modifier = Modifier.fillMaxSize(),
            )
          }
        }

        Column(
          modifier =
            Modifier.fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          // Top info + Avatar when remote video not yet streaming
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer,
            ) {
              Text(
                text = if (isVideo) strings.videoCall else strings.audioCall,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
              )
            }

            if (!isVideo || !remoteVideoAvailable) {
              Spacer(modifier = Modifier.height(28.dp))
              TawasulAvatar(
                displayName = call.otherName(currentUserId),
                avatarUrl = call.otherAvatar(currentUserId),
                size = 110.dp,
                isOnline = true,
                showOnlineDot = true,
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = call.otherName(currentUserId),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isVideo) Color.White else MaterialTheme.colorScheme.onSurface,
              )
            } else {
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = call.otherName(currentUserId),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val rtcLabel =
              when {
                isIncomingRinging -> strings.incomingCall
                call.status == "ringing" -> strings.callingStatus
                connectionState == WebRtcConnectionState.CONNECTED ->
                  "${strings.webrtcConnected} • ${formatDuration(elapsedSeconds)}"
                connectionState == WebRtcConnectionState.CONNECTING ->
                  "${strings.webrtcConnecting} • ${formatDuration(elapsedSeconds)}"
                else -> "${strings.connectedStatus} • ${formatDuration(elapsedSeconds)}"
              }
            Text(
              text = rtcLabel,
              style = MaterialTheme.typography.titleMedium,
              color = if (isVideo) Color(0xFFFFD54F) else MaterialTheme.colorScheme.secondary,
              textAlign = TextAlign.Center,
            )

            if (!hasAudioPermission && !isIncomingRinging) {
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
              ) {
                Text(
                  text = strings.micPermissionDenied,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  textAlign = TextAlign.Center,
                )
              }
            }
          }

          // Bottom Call Action Controls
          if (isIncomingRinging) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
              Button(
                onClick = onDecline,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = CircleShape,
                modifier = Modifier.height(58.dp).testTag("decline_call_button"),
              ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.declineCall, fontWeight = FontWeight.Bold)
              }
              Button(
                onClick = {
                  val needed = mutableListOf<String>()
                  if (!hasAudioPermission) needed += Manifest.permission.RECORD_AUDIO
                  if (isVideo && !hasCameraPermission) needed += Manifest.permission.CAMERA
                  if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
                  onAccept()
                },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = TawasulOnlineGreen,
                    contentColor = Color.White,
                  ),
                shape = CircleShape,
                modifier = Modifier.height(58.dp).testTag("accept_call_button"),
              ) {
                Icon(Icons.Rounded.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.acceptCall, fontWeight = FontWeight.Bold)
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                onClick = {
                  isMuted = !isMuted
                  webRtcClient.setMicEnabled(!isMuted)
                },
                shape = CircleShape,
                color =
                  if (isMuted) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(56.dp).testTag("mute_call_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = if (isMuted) strings.unmuteMic else strings.muteMic,
                  )
                }
              }

              Surface(
                onClick = {
                  isSpeakerOn = !isSpeakerOn
                  webRtcClient.configureAudioMode(inCall = true, speakerOn = isSpeakerOn)
                },
                shape = CircleShape,
                color =
                  if (isSpeakerOn) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(56.dp).testTag("speaker_call_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector =
                      if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp
                      else Icons.Rounded.Hearing,
                    contentDescription = if (isSpeakerOn) strings.speakerOn else strings.speakerOff,
                  )
                }
              }

              if (isVideo) {
                Surface(
                  onClick = {
                    isCameraOn = !isCameraOn
                    webRtcClient.setCameraEnabled(isCameraOn)
                  },
                  shape = CircleShape,
                  color =
                    if (!isCameraOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainer,
                  modifier = Modifier.size(56.dp).testTag("camera_call_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                      contentDescription = if (isCameraOn) strings.cameraOn else strings.cameraOff,
                    )
                  }
                }

                Surface(
                  onClick = { webRtcClient.switchCamera() },
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  modifier = Modifier.size(56.dp).testTag("switch_camera_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Rounded.Cameraswitch,
                      contentDescription = strings.switchCamera,
                    )
                  }
                }
              }

              Surface(
                onClick = { onEnd(elapsedSeconds) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(62.dp).testTag("end_call_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Rounded.CallEnd,
                    contentDescription = strings.endCall,
                    tint = Color.White,
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

private fun formatDuration(totalSeconds: Long): String {
  val mins = totalSeconds / 60
  val secs = totalSeconds % 60
  return "%02d:%02d".format(mins, secs)
}
