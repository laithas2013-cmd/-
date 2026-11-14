package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ArabicStrings
import com.example.core.i18n.EnglishStrings
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.PreferencesManager
import com.example.data.model.CallLog
import com.example.data.repository.CommunityRepository
import com.example.notifications.TawasulNotificationHelper
import com.example.ui.components.TawasulAvatar
import com.example.ui.theme.TawasulGold
import com.example.ui.theme.TawasulGoldSoft
import com.example.ui.theme.TawasulOnlineGreen
import com.example.ui.theme.TawasulPlusTheme
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class IncomingCallActivity : ComponentActivity() {
  private var ringPlayer: MediaPlayer? = null
  private var vibrator: Vibrator? = null
  private var callListener: ListenerRegistration? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    configureLockScreenWindow()

    val callId = intent.getStringExtra(TawasulNotificationHelper.EXTRA_CALL_ID).orEmpty()
    val callerName = intent.getStringExtra(TawasulNotificationHelper.EXTRA_CALLER_NAME).orEmpty()
    val callerAvatar = intent.getStringExtra(TawasulNotificationHelper.EXTRA_CALLER_AVATAR)
    val callType = intent.getStringExtra(TawasulNotificationHelper.EXTRA_CALL_TYPE) ?: "audio"

    if (callId.isBlank()) {
      finish()
      return
    }

    startRingingAudioAndVibration()
    watchCallStatus(callId)

    val prefs = PreferencesManager(applicationContext)

    setContent {
      val lang by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.AR)
      val strings = if (lang == AppLanguage.AR) ArabicStrings else EnglishStrings
      val dir = if (lang.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

      CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides dir,
      ) {
        TawasulPlusTheme(darkTheme = false, isArabic = lang == AppLanguage.AR) {
          IncomingCallRingingScreen(
            callerName = callerName.ifBlank { strings.appName },
            callerAvatar = callerAvatar,
            isVideo = callType == "video",
            onAccept = { acceptAndOpenMain(callId) },
            onDecline = { declineAndFinish(callId) },
          )
        }
      }
    }
  }

  private fun configureLockScreenWindow() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
          WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
      )
    }
  }

  private fun watchCallStatus(callId: String) {
    val dbId = getString(R.string.firestore_database_id)
    val db = FirebaseFirestore.getInstance(dbId)
    callListener =
      db.collection("calls").document(callId).addSnapshotListener { snap, _ ->
        val call =
          snap?.toObject(CallLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (call != null && call.status != "ringing") {
          TawasulNotificationHelper.cancelCallNotification(this, callId)
          finish()
        }
      }
  }

  private fun acceptAndOpenMain(callId: String) {
    stopRinging()
    TawasulNotificationHelper.cancelCallNotification(this, callId)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      km?.requestDismissKeyguard(this, null)
    }
    lifecycleScope.launch {
      CommunityRepository(applicationContext).updateCallStatus(callId, "active", 0L)
      val mainIntent =
        Intent(this@IncomingCallActivity, MainActivity::class.java).apply {
          putExtra(TawasulNotificationHelper.EXTRA_CALL_ID, callId)
          addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_SINGLE_TOP or
              Intent.FLAG_ACTIVITY_CLEAR_TOP
          )
        }
      startActivity(mainIntent)
      finish()
    }
  }

  private fun declineAndFinish(callId: String) {
    stopRinging()
    TawasulNotificationHelper.cancelCallNotification(this, callId)
    lifecycleScope.launch {
      CommunityRepository(applicationContext).updateCallStatus(callId, "declined", 0L)
      finish()
    }
  }

  private fun startRingingAudioAndVibration() {
    try {
      val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      ringPlayer =
        MediaPlayer().apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .setLegacyStreamType(AudioManager.STREAM_RING)
              .build()
          )
          setDataSource(applicationContext, ringtoneUri)
          isLooping = true
          prepare()
          start()
        }
    } catch (_: Exception) {}

    try {
      val vib =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
          vm?.defaultVibrator
        } else {
          @Suppress("DEPRECATION")
          getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
      vibrator = vib
      val pattern = longArrayOf(0L, 650L, 450L)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        @Suppress("DEPRECATION")
        vib?.vibrate(pattern, 0)
      }
    } catch (_: Exception) {}
  }

  private fun stopRinging() {
    try {
      ringPlayer?.stop()
    } catch (_: Exception) {}
    try {
      ringPlayer?.release()
    } catch (_: Exception) {}
    ringPlayer = null
    try {
      vibrator?.cancel()
    } catch (_: Exception) {}
    vibrator = null
  }

  override fun onDestroy() {
    callListener?.remove()
    stopRinging()
    super.onDestroy()
  }
}

@Composable
private fun IncomingCallRingingScreen(
  callerName: String,
  callerAvatar: String?,
  isVideo: Boolean,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
) {
  val strings = LocalAppStrings.current
  val pulseTransition = rememberInfiniteTransition(label = "ring_pulse")
  val pulseScale by
    pulseTransition.animateFloat(
      initialValue = 0.94f,
      targetValue = 1.14f,
      animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
      label = "ring_scale",
    )

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), TawasulGoldSoft, Color(0xFFFFFFFF))
          )
        )
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(28.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
          color = TawasulGold.copy(alpha = 0.25f),
          shape = CircleShape,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "${strings.appName} • ${if (isVideo) strings.videoCall else strings.audioCall}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
          Box(
            modifier =
              Modifier.size(156.dp)
                .scale(pulseScale)
                .background(TawasulGold.copy(alpha = 0.22f), CircleShape)
          )
          Box(
            modifier =
              Modifier.size(134.dp)
                .background(TawasulGold.copy(alpha = 0.35f), CircleShape)
          )
          TawasulAvatar(
            displayName = callerName,
            avatarUrl = callerAvatar,
            size = 112.dp,
            isOnline = true,
            showOnlineDot = true,
          )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
          text = callerName,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = strings.incomingCall,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.secondary,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        Button(
          onClick = onDecline,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = CircleShape,
          modifier = Modifier.height(60.dp).testTag("ring_screen_decline_button"),
        ) {
          Icon(Icons.Rounded.CallEnd, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(strings.declineCall, fontWeight = FontWeight.Bold)
        }

        Button(
          onClick = onAccept,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = TawasulOnlineGreen,
              contentColor = Color.White,
            ),
          shape = CircleShape,
          modifier = Modifier.height(60.dp).testTag("ring_screen_accept_button"),
        ) {
          Icon(
            if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(strings.acceptCall, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
