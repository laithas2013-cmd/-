package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.IncomingCallActivity
import com.example.MainActivity
import com.example.R
import com.example.core.i18n.AppStrings
import com.example.data.model.CallLog

object AppVisibilityState {
  @Volatile var isForeground: Boolean = false
  @Volatile var activeChatId: String? = null
  @Volatile var activeGroupId: String? = null
  @Volatile var activeCallId: String? = null
}

object TawasulNotificationHelper {
  const val CHANNEL_INCOMING_CALLS = "tawasul_incoming_calls_v1"
  const val CHANNEL_ONGOING_CALLS = "tawasul_ongoing_calls_v1"
  const val CHANNEL_MESSAGES = "tawasul_messages_v1"
  const val CHANNEL_SYNC = "tawasul_background_sync_v1"

  const val EXTRA_CALL_ID = "extra_call_id"
  const val EXTRA_CALLER_NAME = "extra_caller_name"
  const val EXTRA_CALLER_AVATAR = "extra_caller_avatar"
  const val EXTRA_CALL_TYPE = "extra_call_type"
  const val EXTRA_OPEN_CHAT_ID = "extra_open_chat_id"
  const val EXTRA_OPEN_GROUP_ID = "extra_open_group_id"

  const val ACTION_ACCEPT_CALL = "com.example.action.ACCEPT_CALL"
  const val ACTION_DECLINE_CALL = "com.example.action.DECLINE_CALL"
  const val ACTION_END_CALL = "com.example.action.END_CALL"

  const val SYNC_NOTIFICATION_ID = 9001

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun canUseFullScreenIntent(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.canUseFullScreenIntent() ?: true
    } else {
      true
    }
  }

  fun ensureChannels(context: Context, strings: AppStrings) {
    val nm = NotificationManagerCompat.from(context)
    val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    val msgSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    val incomingChannel =
      NotificationChannelCompat.Builder(
          CHANNEL_INCOMING_CALLS,
          NotificationManagerCompat.IMPORTANCE_HIGH,
        )
        .setName(strings.notifChannelIncomingCalls)
        .setDescription(strings.notifChannelIncomingCallsDesc)
        .setVibrationEnabled(true)
        .setVibrationPattern(longArrayOf(0L, 600L, 400L, 600L))
        .setSound(
          ringtoneUri,
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_RING)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build(),
        )
        .setShowBadge(false)
        .build()

    val ongoingChannel =
      NotificationChannelCompat.Builder(
          CHANNEL_ONGOING_CALLS,
          NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
        .setName(strings.notifChannelOngoingCalls)
        .setDescription(strings.notifChannelOngoingCallsDesc)
        .setSound(null, null)
        .build()

    val messagesChannel =
      NotificationChannelCompat.Builder(
          CHANNEL_MESSAGES,
          NotificationManagerCompat.IMPORTANCE_HIGH,
        )
        .setName(strings.notifChannelMessages)
        .setDescription(strings.notifChannelMessagesDesc)
        .setVibrationEnabled(true)
        .setSound(
          msgSoundUri,
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build(),
        )
        .build()

    val syncChannel =
      NotificationChannelCompat.Builder(
          CHANNEL_SYNC,
          NotificationManagerCompat.IMPORTANCE_LOW,
        )
        .setName(strings.notifChannelSync)
        .setDescription(strings.notifChannelSyncDesc)
        .setSound(null, null)
        .setShowBadge(false)
        .build()

    nm.createNotificationChannelsCompat(
      listOf(incomingChannel, ongoingChannel, messagesChannel, syncChannel)
    )
  }

  fun buildSyncNotification(context: Context, strings: AppStrings): Notification {
    ensureChannels(context, strings)
    val launchIntent =
      PendingIntent.getActivity(
        context,
        100,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(context, CHANNEL_SYNC)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(strings.appName)
      .setContentText(strings.notifSyncActive)
      .setContentIntent(launchIntent)
      .setOngoing(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  fun callNotificationId(callId: String): Int = 3000 + (callId.hashCode() and 0x0FFF)

  fun showIncomingCallNotification(context: Context, call: CallLog, strings: AppStrings) {
    ensureChannels(context, strings)
    val fullScreenIntent =
      Intent(context, IncomingCallActivity::class.java).apply {
        putExtra(EXTRA_CALL_ID, call.id)
        putExtra(EXTRA_CALLER_NAME, call.callerName)
        putExtra(EXTRA_CALLER_AVATAR, call.callerAvatar)
        putExtra(EXTRA_CALL_TYPE, call.callType)
        addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
      }

    val notifId = callNotificationId(call.id)
    val fullScreenPendingIntent =
      PendingIntent.getActivity(
        context,
        notifId,
        fullScreenIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val acceptPendingIntent =
      PendingIntent.getBroadcast(
        context,
        notifId + 10_000,
        Intent(context, CallActionReceiver::class.java).apply {
          action = ACTION_ACCEPT_CALL
          putExtra(EXTRA_CALL_ID, call.id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val declinePendingIntent =
      PendingIntent.getBroadcast(
        context,
        notifId + 20_000,
        Intent(context, CallActionReceiver::class.java).apply {
          action = ACTION_DECLINE_CALL
          putExtra(EXTRA_CALL_ID, call.id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val callerPerson =
      Person.Builder()
        .setName(call.callerName.ifBlank { strings.appName })
        .setImportant(true)
        .build()

    val callTypeText = if (call.callType == "video") strings.videoCall else strings.audioCall

    val notification =
      NotificationCompat.Builder(context, CHANNEL_INCOMING_CALLS)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(call.callerName)
        .setContentText("${strings.incomingCall} • $callTypeText")
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setAutoCancel(false)
        .setContentIntent(fullScreenPendingIntent)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .addPerson(callerPerson)
        .setStyle(
          NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            acceptPendingIntent,
          ).setIsVideo(call.callType == "video")
        )
        .build()

    if (hasNotificationPermission(context)) {
      try {
        NotificationManagerCompat.from(context).notify(notifId, notification)
      } catch (_: SecurityException) {}
    }

    // Best-effort direct launch if permitted; FullScreenIntent also handles locked/background state
    if (!AppVisibilityState.isForeground) {
      try {
        context.startActivity(fullScreenIntent)
      } catch (_: Exception) {}
    }
  }

  fun buildOngoingCallNotification(
    context: Context,
    callId: String,
    peerName: String,
    isVideo: Boolean,
    strings: AppStrings,
  ): Notification {
    ensureChannels(context, strings)
    val notifId = callNotificationId(callId)

    val contentIntent =
      PendingIntent.getActivity(
        context,
        notifId + 30_000,
        Intent(context, MainActivity::class.java).apply {
          putExtra(EXTRA_CALL_ID, callId)
          addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val hangupIntent =
      PendingIntent.getBroadcast(
        context,
        notifId + 40_000,
        Intent(context, CallActionReceiver::class.java).apply {
          action = ACTION_END_CALL
          putExtra(EXTRA_CALL_ID, callId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val peerPerson =
      Person.Builder()
        .setName(peerName.ifBlank { strings.appName })
        .setImportant(true)
        .build()

    return NotificationCompat.Builder(context, CHANNEL_ONGOING_CALLS)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(peerName)
      .setContentText(strings.connectedStatus)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setOngoing(true)
      .setContentIntent(contentIntent)
      .addPerson(peerPerson)
      .setStyle(
        NotificationCompat.CallStyle.forOngoingCall(peerPerson, hangupIntent)
          .setIsVideo(isVideo)
      )
      .build()
  }

  fun cancelCallNotification(context: Context, callId: String) {
    if (callId.isBlank()) return
    NotificationManagerCompat.from(context).cancel(callNotificationId(callId))
  }

  fun showMessageNotification(
    context: Context,
    title: String,
    body: String,
    chatId: String? = null,
    groupId: String? = null,
    strings: AppStrings,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureChannels(context, strings)

    val targetKey = chatId ?: groupId ?: "msg"
    val notifId = 5000 + (targetKey.hashCode() and 0x0FFF)
    val tapIntent =
      Intent(context, MainActivity::class.java).apply {
        if (!chatId.isNullOrBlank()) putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        if (!groupId.isNullOrBlank()) putExtra(EXTRA_OPEN_GROUP_ID, groupId)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }

    val pendingIntent =
      PendingIntent.getActivity(
        context,
        notifId,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_MESSAGES)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    try {
      NotificationManagerCompat.from(context).notify(notifId, notification)
    } catch (_: SecurityException) {}
  }
}
