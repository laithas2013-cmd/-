package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.R
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ArabicStrings
import com.example.core.i18n.EnglishStrings
import com.example.core.i18n.PreferencesManager
import com.example.data.repository.ChatRepository
import com.example.data.repository.CommunityRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TawasulBackgroundSyncService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var listenersJob: Job? = null

  private val seenRingingCalls = mutableSetOf<String>()
  private val lastChatMessageTimestamps = mutableMapOf<String, Long>()
  private val lastGroupMessageTimestamps = mutableMapOf<String, Long>()
  private var chatSnapshotPrimed = false
  private var groupSnapshotPrimed = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val user = Firebase.auth.currentUser
    if (user == null) {
      stopSelf()
      return START_NOT_STICKY
    }

    serviceScope.launch {
      val lang = PreferencesManager(applicationContext).languageFlow.first()
      val strings = if (lang == AppLanguage.AR) ArabicStrings else EnglishStrings
      val notification = TawasulNotificationHelper.buildSyncNotification(applicationContext, strings)
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          startForeground(
            TawasulNotificationHelper.SYNC_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
          )
        } else {
          startForeground(TawasulNotificationHelper.SYNC_NOTIFICATION_ID, notification)
        }
      } catch (_: Exception) {
        stopSelf()
        return@launch
      }

      startFirestoreWatchers(user.uid)
    }

    return START_STICKY
  }

  private fun startFirestoreWatchers(uid: String) {
    listenersJob?.cancel()
    val dbId = applicationContext.getString(R.string.firestore_database_id)
    val db = FirebaseFirestore.getInstance(dbId)
    val communityRepo = CommunityRepository(db)
    val chatRepo = ChatRepository(db)
    val prefs = PreferencesManager(applicationContext)

    listenersJob =
      serviceScope.launch {
        // Watch incoming calls
        launch {
          communityRepo
            .observeCalls(uid)
            .catch { }
            .collect { calls ->
              val lang = prefs.languageFlow.first()
              val strings = if (lang == AppLanguage.AR) ArabicStrings else EnglishStrings

              calls
                .filter { it.status == "ringing" && it.calleeId == uid }
                .forEach { incoming ->
                  if (seenRingingCalls.add(incoming.id) && !AppVisibilityState.isForeground) {
                    TawasulNotificationHelper.showIncomingCallNotification(
                      context = applicationContext,
                      call = incoming,
                      strings = strings,
                    )
                  }
                }

              calls
                .filter { it.status != "ringing" && seenRingingCalls.contains(it.id) }
                .forEach { ended ->
                  TawasulNotificationHelper.cancelCallNotification(applicationContext, ended.id)
                }
            }
        }

        // Watch direct chats for incoming messages
        launch {
          chatRepo
            .observeChats(uid)
            .catch { }
            .collect { rooms ->
              val lang = prefs.languageFlow.first()
              val strings = if (lang == AppLanguage.AR) ArabicStrings else EnglishStrings

              if (!chatSnapshotPrimed) {
                rooms.forEach { r ->
                  lastChatMessageTimestamps[r.id] = r.lastMessageAt?.toDate()?.time ?: 0L
                }
                chatSnapshotPrimed = true
                return@collect
              }

              rooms.forEach { room ->
                val ts = room.lastMessageAt?.toDate()?.time ?: 0L
                val prev = lastChatMessageTimestamps[room.id] ?: 0L
                lastChatMessageTimestamps[room.id] = ts

                val isViewingThisChat =
                  AppVisibilityState.isForeground && AppVisibilityState.activeChatId == room.id

                if (
                  ts > prev &&
                    room.lastMessageSenderId != null &&
                    room.lastMessageSenderId != uid &&
                    room.lastMessageText.isNotBlank() &&
                    !room.isMutedBy(uid) &&
                    !isViewingThisChat
                ) {
                  TawasulNotificationHelper.showMessageNotification(
                    context = applicationContext,
                    title = room.otherDisplayName(uid),
                    body = room.lastMessageText,
                    chatId = room.id,
                    strings = strings,
                  )
                }
              }
            }
        }

        // Watch groups for incoming messages
        launch {
          communityRepo
            .observeGroups(uid)
            .catch { }
            .collect { groups ->
              val lang = prefs.languageFlow.first()
              val strings = if (lang == AppLanguage.AR) ArabicStrings else EnglishStrings

              if (!groupSnapshotPrimed) {
                groups.forEach { g ->
                  lastGroupMessageTimestamps[g.id] = g.lastMessageAt?.toDate()?.time ?: 0L
                }
                groupSnapshotPrimed = true
                return@collect
              }

              groups.forEach { group ->
                val ts = group.lastMessageAt?.toDate()?.time ?: 0L
                val prev = lastGroupMessageTimestamps[group.id] ?: 0L
                lastGroupMessageTimestamps[group.id] = ts

                val isViewingThisGroup =
                  AppVisibilityState.isForeground && AppVisibilityState.activeGroupId == group.id

                if (
                  ts > prev &&
                    group.lastMessageSenderId != null &&
                    group.lastMessageSenderId != uid &&
                    group.lastMessageText.isNotBlank() &&
                    !isViewingThisGroup
                ) {
                  TawasulNotificationHelper.showMessageNotification(
                    context = applicationContext,
                    title = group.name,
                    body = group.lastMessageText,
                    groupId = group.id,
                    strings = strings,
                  )
                }
              }
            }
        }
      }
  }

  override fun onDestroy() {
    listenersJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    fun startIfAllowed(context: Context) {
      if (Firebase.auth.currentUser == null) return
      if (!TawasulNotificationHelper.hasNotificationPermission(context)) return
      try {
        val intent = Intent(context, TawasulBackgroundSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Exception) {}
    }

    fun stop(context: Context) {
      try {
        context.stopService(Intent(context, TawasulBackgroundSyncService::class.java))
      } catch (_: Exception) {}
    }
  }
}
