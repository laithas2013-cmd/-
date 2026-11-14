package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.ThemeMode
import com.example.core.util.compressUriToDataUri
import com.example.data.firebase.signOutUser
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.notifications.CallForegroundService
import com.example.notifications.TawasulBackgroundSyncService
import com.example.notifications.TawasulNotificationHelper
import com.example.ui.components.TawasulAvatar
import com.example.ui.theme.TawasulOnlineGreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private val AVATAR_PRESETS = listOf("preset:sun", "preset:spark", "preset:bolt", "preset:heart")

@Composable
fun ProfileScreen(
  profileState: UiState<UserProfile?>,
  currentLanguage: AppLanguage,
  currentThemeMode: ThemeMode,
  profileBusy: Boolean,
  usernameAvailability: Boolean?,
  onCheckUsername: (String) -> Unit,
  onResetUsernameAvailability: () -> Unit,
  onSaveProfile: (
    displayName: String,
    username: String,
    bio: String,
    avatarUrl: String?,
    language: AppLanguage,
    themeMode: ThemeMode,
  ) -> Unit,
  onLanguageChange: (AppLanguage) -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  onSetOfflineBeforeSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  when (profileState) {
    is UiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    }
    is UiState.Error -> {
      Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(profileState.message, color = MaterialTheme.colorScheme.error)
      }
    }
    is UiState.Success -> {
      val profile = profileState.data
      if (profile == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
      }

      var displayName by remember(profile.userId, profile.displayName) { mutableStateOf(profile.displayName) }
      var username by remember(profile.userId, profile.username) { mutableStateOf(profile.username) }
      var bio by remember(profile.userId, profile.bio) { mutableStateOf(profile.bio.orEmpty()) }
      var selectedAvatar by remember(profile.userId, profile.avatarUrl) { mutableStateOf(profile.avatarUrl) }
      var processingPhoto by remember { mutableStateOf(false) }
      val googlePhotoUrl = remember { Firebase.auth.currentUser?.photoUrl?.toString() }

      val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
          if (uri != null) {
            scope.launch {
              processingPhoto = true
              val compressed = compressUriToDataUri(context, uri)
              processingPhoto = false
              if (compressed != null) selectedAvatar = compressed
            }
          }
        }

      LaunchedEffect(username) {
        onResetUsernameAvailability()
      }

      val isUsernameValid = remember(username) {
        Regex("^[a-z0-9_]{3,30}$").matches(username.trim().removePrefix("@").lowercase())
      }

      Column(
        modifier =
          modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Profile Header Card
        Surface(
          modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            TawasulAvatar(
              displayName = displayName.ifBlank { profile.displayName },
              avatarUrl = selectedAvatar,
              size = 88.dp,
              isOnline = true,
              showOnlineDot = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = displayName.ifBlank { profile.displayName },
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "@${username.trim().removePrefix("@").lowercase()}",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.secondary,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "${strings.emailLabel}: ${profile.email}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              enabled = !processingPhoto && !profileBusy,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.height(48.dp).testTag("profile_pick_photo_button"),
            ) {
              Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (processingPhoto) strings.processingImage else strings.pickFromGallery)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = strings.avatarPresetsTitle,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (!googlePhotoUrl.isNullOrBlank()) {
                val isSelected = selectedAvatar == googlePhotoUrl
                Box(
                  modifier =
                    Modifier.clip(CircleShape)
                      .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color =
                          if (isSelected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                      )
                      .clickable { selectedAvatar = googlePhotoUrl }
                      .padding(2.dp)
                ) {
                  TawasulAvatar(
                    displayName = profile.displayName,
                    avatarUrl = googlePhotoUrl,
                    size = 44.dp,
                  )
                }
              }
              AVATAR_PRESETS.forEach { preset ->
                val isSelected = selectedAvatar == preset
                Box(
                  modifier =
                    Modifier.clip(CircleShape)
                      .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color =
                          if (isSelected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                      )
                      .clickable { selectedAvatar = preset }
                      .padding(2.dp)
                ) {
                  TawasulAvatar(
                    displayName = profile.displayName,
                    avatarUrl = preset,
                    size = 44.dp,
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edit Profile Card
        Surface(
          modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text(
              text = strings.editProfile,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
              value = displayName,
              onValueChange = { displayName = it },
              label = { Text(strings.displayNameLabel) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("profile_display_name_input"),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OutlinedTextField(
                  value = username,
                  onValueChange = { username = it.lowercase() },
                  label = { Text(strings.usernameLabel) },
                  singleLine = true,
                  isError = username.isNotBlank() && !isUsernameValid,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f).testTag("profile_username_input"),
                  colors =
                    OutlinedTextFieldDefaults.colors(
                      focusedBorderColor = MaterialTheme.colorScheme.primary,
                      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                  onClick = { onCheckUsername(username) },
                  enabled = isUsernameValid && !profileBusy,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.height(52.dp).testTag("check_username_button"),
                ) {
                  Text(strings.checkAvailability)
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              if (username.isNotBlank() && !isUsernameValid) {
                Text(
                  text = strings.usernameInvalid,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.error,
                )
              } else if (usernameAvailability != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector =
                      if (usernameAvailability) Icons.Rounded.CheckCircle
                      else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint =
                      if (usernameAvailability) TawasulOnlineGreen
                      else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text =
                      if (usernameAvailability) strings.usernameAvailable
                      else strings.usernameTaken,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (usernameAvailability) TawasulOnlineGreen
                      else MaterialTheme.colorScheme.error,
                  )
                }
              } else {
                Text(
                  text = strings.usernameRulesHint,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            OutlinedTextField(
              value = bio,
              onValueChange = { bio = it },
              label = { Text(strings.bioLabel) },
              placeholder = { Text(strings.bioPlaceholder) },
              maxLines = 3,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("profile_bio_input"),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Button(
              onClick = {
                onSaveProfile(
                  displayName,
                  username,
                  bio,
                  selectedAvatar,
                  currentLanguage,
                  currentThemeMode,
                )
              },
              enabled = !profileBusy && displayName.isNotBlank() && isUsernameValid,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile_button"),
            ) {
              if (profileBusy) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.save, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance & Language Card
        Surface(
          modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text(
              text = strings.appearanceTitle,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )

            // Theme selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ThemeChoicePill(
                label = strings.themeSystem,
                icon = Icons.Rounded.PhoneAndroid,
                selected = currentThemeMode == ThemeMode.SYSTEM,
                modifier = Modifier.weight(1f).testTag("theme_system"),
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
              )
              ThemeChoicePill(
                label = strings.themeLight,
                icon = Icons.Rounded.LightMode,
                selected = currentThemeMode == ThemeMode.LIGHT,
                modifier = Modifier.weight(1f).testTag("theme_light"),
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
              )
              ThemeChoicePill(
                label = strings.themeDark,
                icon = Icons.Rounded.DarkMode,
                selected = currentThemeMode == ThemeMode.DARK,
                modifier = Modifier.weight(1f).testTag("theme_dark"),
                onClick = { onThemeModeChange(ThemeMode.DARK) },
              )
            }

            // Language selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ThemeChoicePill(
                label = strings.languageArabic,
                icon = Icons.Rounded.Language,
                selected = currentLanguage == AppLanguage.AR,
                modifier = Modifier.weight(1f).testTag("profile_lang_ar"),
                onClick = { onLanguageChange(AppLanguage.AR) },
              )
              ThemeChoicePill(
                label = strings.languageEnglish,
                icon = Icons.Rounded.Language,
                selected = currentLanguage == AppLanguage.EN,
                modifier = Modifier.weight(1f).testTag("profile_lang_en"),
                onClick = { onLanguageChange(AppLanguage.EN) },
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications & Out-of-app Call Screen Card
        var notifGranted by remember { mutableStateOf(TawasulNotificationHelper.hasNotificationPermission(context)) }
        val fullScreenAllowed = remember { TawasulNotificationHelper.canUseFullScreenIntent(context) }
        val notifLauncher =
          rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notifGranted = granted || TawasulNotificationHelper.hasNotificationPermission(context)
            if (notifGranted) {
              TawasulNotificationHelper.ensureChannels(context, strings)
              TawasulBackgroundSyncService.startIfAllowed(context)
            }
          }

        Surface(
          modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = strings.enableNotificationsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }

            Text(
              text = strings.enableNotificationsDesc,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (notifGranted) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = TawasulOnlineGreen,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = strings.fullScreenCallsEnabled,
                  style = MaterialTheme.typography.labelMedium,
                  color = TawasulOnlineGreen,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            } else {
              Button(
                onClick = {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    val settingsIntent =
                      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                      }
                    context.startActivity(settingsIntent)
                  }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_enable_notifications_button"),
              ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.enableNotificationsAction, fontWeight = FontWeight.Bold)
              }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !fullScreenAllowed) {
              OutlinedButton(
                onClick = {
                  val fsiIntent =
                    Intent(
                      Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                      Uri.parse("package:${context.packageName}"),
                    )
                  context.startActivity(fsiIntent)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_enable_fullscreen_calls_button"),
              ) {
                Icon(Icons.Rounded.RingVolume, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.enableFullScreenCalls)
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sign Out Button
        OutlinedButton(
          onClick = {
            CallForegroundService.stop(context)
            TawasulBackgroundSyncService.stop(context)
            onSetOfflineBeforeSignOut()
            signOutUser(credentialManager, scope)
          },
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp).height(50.dp).testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(strings.signOut, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun ThemeChoicePill(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.height(48.dp),
    shape = RoundedCornerShape(14.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.surfaceContainer,
    border =
      BorderStroke(
        1.dp,
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}
