package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RocketLaunch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.ThemeMode
import com.example.core.util.compressUriToDataUri
import com.example.data.model.UserProfile
import com.example.ui.components.TawasulAvatar
import com.example.ui.theme.TawasulOnlineGreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private val SETUP_AVATAR_PRESETS = listOf("preset:sun", "preset:spark", "preset:bolt", "preset:heart")

@Composable
fun ProfileSetupScreen(
  profile: UserProfile,
  currentLanguage: AppLanguage,
  currentThemeMode: ThemeMode,
  busy: Boolean,
  usernameAvailability: Boolean?,
  onCheckUsername: (String) -> Unit,
  onResetUsernameAvailability: () -> Unit,
  onCompleteSetup: (
    displayName: String,
    username: String,
    bio: String,
    avatarUrl: String?,
    language: AppLanguage,
    themeMode: ThemeMode,
  ) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var displayName by remember(profile.userId) { mutableStateOf(profile.displayName) }
  var username by remember(profile.userId) { mutableStateOf(profile.username) }
  var bio by remember(profile.userId) { mutableStateOf(profile.bio.orEmpty()) }
  var selectedAvatar by remember(profile.userId) { mutableStateOf(profile.avatarUrl) }
  var processingPhoto by remember { mutableStateOf(false) }
  var photoError by remember { mutableStateOf<String?>(null) }
  val googlePhotoUrl = remember { Firebase.auth.currentUser?.photoUrl?.toString() }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          processingPhoto = true
          photoError = null
          val dataUri = compressUriToDataUri(context, uri)
          processingPhoto = false
          if (dataUri != null) {
            selectedAvatar = dataUri
          } else {
            photoError = strings.imagePickError
          }
        }
      }
    }

  LaunchedEffect(username) {
    onResetUsernameAvailability()
  }

  val isUsernameValid = remember(username) {
    Regex("^[a-z0-9_]{3,30}$").matches(username.trim().removePrefix("@").lowercase())
  }

  Box(
    modifier =
      modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                MaterialTheme.colorScheme.background,
              )
          )
        )
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = strings.setupTitle,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = strings.setupSubtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 520.dp),
      )

      Spacer(modifier = Modifier.height(20.dp))

      Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          TawasulAvatar(
            displayName = displayName.ifBlank { profile.displayName },
            avatarUrl = selectedAvatar,
            size = 92.dp,
            isOnline = true,
            showOnlineDot = true,
          )

          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            enabled = !processingPhoto && !busy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(48.dp).testTag("setup_pick_photo_button"),
          ) {
            if (processingPhoto) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text(strings.processingImage)
            } else {
              Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(strings.pickFromGallery)
            }
          }

          if (!photoError.isNullOrBlank()) {
            Text(
              text = photoError!!,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
            )
          }

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
                TawasulAvatar(displayName = profile.displayName, avatarUrl = googlePhotoUrl, size = 44.dp)
              }
            }
            SETUP_AVATAR_PRESETS.forEach { preset ->
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
                TawasulAvatar(displayName = profile.displayName, avatarUrl = preset, size = 44.dp)
              }
            }
          }

          OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(strings.displayNameLabel) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("setup_display_name_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              ),
          )

          Column(modifier = Modifier.fillMaxWidth()) {
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
                modifier = Modifier.weight(1f).testTag("setup_username_input"),
                colors =
                  OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                  ),
              )
              Spacer(modifier = Modifier.width(8.dp))
              OutlinedButton(
                onClick = { onCheckUsername(username) },
                enabled = isUsernameValid && !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp).testTag("setup_check_username_button"),
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
            modifier = Modifier.fillMaxWidth().testTag("setup_bio_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              ),
          )

          Button(
            onClick = {
              onCompleteSetup(
                displayName,
                username,
                bio,
                selectedAvatar,
                currentLanguage,
                currentThemeMode,
              )
            },
            enabled = !busy && !processingPhoto && displayName.isNotBlank() && isUsernameValid,
            shape = RoundedCornerShape(16.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("complete_setup_button"),
          ) {
            if (busy) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
            } else {
              Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(strings.finishSetup, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}
