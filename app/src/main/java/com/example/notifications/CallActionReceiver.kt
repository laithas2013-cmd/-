package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.data.repository.CommunityRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val callId = intent.getStringExtra(TawasulNotificationHelper.EXTRA_CALL_ID) ?: return
    val action = intent.action ?: return
    if (Firebase.auth.currentUser == null) {
      TawasulNotificationHelper.cancelCallNotification(context, callId)
      return
    }

    val pendingResult = goAsync()
    val repo = CommunityRepository(context)
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        when (action) {
          TawasulNotificationHelper.ACTION_ACCEPT_CALL -> {
            repo.updateCallStatus(callId, "active", 0L)
            TawasulNotificationHelper.cancelCallNotification(context, callId)
            val launch =
              Intent(context, MainActivity::class.java).apply {
                putExtra(TawasulNotificationHelper.EXTRA_CALL_ID, callId)
                addFlags(
                  Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
              }
            context.startActivity(launch)
          }
          TawasulNotificationHelper.ACTION_DECLINE_CALL -> {
            repo.updateCallStatus(callId, "declined", 0L)
            TawasulNotificationHelper.cancelCallNotification(context, callId)
          }
          TawasulNotificationHelper.ACTION_END_CALL -> {
            repo.updateCallStatus(callId, "ended", 0L)
            TawasulNotificationHelper.cancelCallNotification(context, callId)
            context.stopService(Intent(context, CallForegroundService::class.java))
          }
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
