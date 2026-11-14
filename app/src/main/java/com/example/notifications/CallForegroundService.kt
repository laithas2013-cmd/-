package com.example.notifications

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ArabicStrings
import com.example.core.i18n.EnglishStrings
import com.example.core.i18n.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CallForegroundService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val callId = intent?.getStringExtra(TawasulNotificationHelper.EXTRA_CALL_ID).orEmpty()
    val peerName = intent?.getStringExtra(TawasulNotificationHelper.EXTRA_CALLER_NAME).orEmpty()
    val isVideo = intent?.getStringExtra(TawasulNotificationHelper.EXTRA_CALL_TYPE) == "video"

    if (callId.isBlank()) {
      stopSelf()
      return START_NOT_STICKY
    }

    val lang = runBlocking { PreferencesManager(applicationContext).languageFlow.first() }
    val strings = if (lang == AppLanguage.AR) ArabicStrings else EnglishStrings

    val notification =
      TawasulNotificationHelper.buildOngoingCallNotification(
        context = this,
        callId = callId,
        peerName = peerName,
        isVideo = isVideo,
        strings = strings,
      )
    val notifId = TawasulNotificationHelper.callNotificationId(callId)

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val hasMic =
          ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hasCam =
          ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        var types = 0
        if (hasMic) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (isVideo && hasCam) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (types != 0) {
          startForeground(notifId, notification, types)
        } else {
          stopSelf()
          return START_NOT_STICKY
        }
      } else {
        startForeground(notifId, notification)
      }
    } catch (_: Exception) {
      stopSelf()
    }

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
  }

  companion object {
    fun start(context: Context, callId: String, peerName: String, callType: String) {
      val hasMic =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!hasMic) return
      val intent =
        Intent(context, CallForegroundService::class.java).apply {
          putExtra(TawasulNotificationHelper.EXTRA_CALL_ID, callId)
          putExtra(TawasulNotificationHelper.EXTRA_CALLER_NAME, peerName)
          putExtra(TawasulNotificationHelper.EXTRA_CALL_TYPE, callType)
        }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Exception) {}
    }

    fun stop(context: Context) {
      try {
        context.stopService(Intent(context, CallForegroundService::class.java))
      } catch (_: Exception) {}
    }
  }
}
