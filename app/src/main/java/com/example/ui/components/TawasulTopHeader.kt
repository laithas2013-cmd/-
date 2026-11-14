package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalAppStrings
import com.example.core.i18n.ThemeMode
import com.example.data.model.UserProfile

@Composable
fun TawasulTopHeader(
  title: String,
  subtitle: String?,
  myProfile: UserProfile?,
  currentLanguage: AppLanguage,
  currentThemeMode: ThemeMode,
  searchExpanded: Boolean,
  searchQuery: String,
  showBackButton: Boolean = false,
  onBackClick: () -> Unit = {},
  onAvatarClick: () -> Unit,
  onContactsClick: () -> Unit,
  onToggleSearch: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleLanguage: () -> Unit,
  onCycleTheme: () -> Unit,
  onSignOut: () -> Unit,
) {
  val strings = LocalAppStrings.current
  var settingsExpanded by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (showBackButton) {
          IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(48.dp).testTag("header_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
          }
        } else {
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onAvatarClick)
                .testTag("header_profile_avatar_button"),
            contentAlignment = Alignment.Center,
          ) {
            TawasulAvatar(
              displayName = myProfile?.displayName ?: strings.appName,
              avatarUrl = myProfile?.avatarUrl,
              size = 40.dp,
              isOnline = true,
              showOnlineDot = true,
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (!subtitle.isNullOrBlank()) {
            Text(
              text = subtitle,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        if (!showBackButton) {
          IconButton(
            onClick = onToggleSearch,
            modifier = Modifier.size(48.dp).testTag("header_search_toggle"),
          ) {
            Icon(
              imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
              contentDescription = strings.searchButton,
            )
          }

          IconButton(
            onClick = onContactsClick,
            modifier = Modifier.size(48.dp).testTag("header_contacts_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.PersonSearch,
              contentDescription = strings.openContacts,
            )
          }

          Box {
            IconButton(
              onClick = { settingsExpanded = true },
              modifier = Modifier.size(48.dp).testTag("header_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = strings.settingsMenu,
              )
            }

            DropdownMenu(
              expanded = settingsExpanded,
              onDismissRequest = { settingsExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text(strings.openProfileSettings) },
                leadingIcon = { Icon(Icons.Rounded.AccountCircle, contentDescription = null) },
                onClick = {
                  settingsExpanded = false
                  onAvatarClick()
                },
              )
              DropdownMenuItem(
                text = { Text(strings.openContacts) },
                leadingIcon = { Icon(Icons.Rounded.PersonSearch, contentDescription = null) },
                onClick = {
                  settingsExpanded = false
                  onContactsClick()
                },
              )
              DropdownMenuItem(
                text = {
                  val nextLang =
                    if (currentLanguage == AppLanguage.AR) strings.languageEnglish
                    else strings.languageArabic
                  Text("${strings.languageTitle}: $nextLang")
                },
                leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                onClick = {
                  settingsExpanded = false
                  onToggleLanguage()
                },
              )
              DropdownMenuItem(
                text = {
                  val modeLabel =
                    when (currentThemeMode) {
                      ThemeMode.SYSTEM -> strings.themeSystem
                      ThemeMode.LIGHT -> strings.themeLight
                      ThemeMode.DARK -> strings.themeDark
                    }
                  Text("${strings.appearanceTitle}: $modeLabel")
                },
                leadingIcon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                onClick = {
                  settingsExpanded = false
                  onCycleTheme()
                },
              )
              HorizontalDivider()
              DropdownMenuItem(
                text = { Text(strings.signOut, color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                  Icon(
                    Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                  )
                },
                onClick = {
                  settingsExpanded = false
                  onSignOut()
                },
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = searchExpanded && !showBackButton) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text(strings.searchGeneralPlaceholder) },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.background,
              unfocusedContainerColor = MaterialTheme.colorScheme.background,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
          modifier =
            Modifier.fillMaxWidth()
              .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
              .testTag("header_search_input"),
        )
      }
    }
  }
}
