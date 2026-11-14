package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.data.model.ContactEntry
import com.example.data.model.UiState
import com.example.data.model.UsernameDirectoryEntry
import com.example.ui.components.TawasulAvatar

@Composable
fun ContactsScreen(
  contactsState: UiState<List<ContactEntry>>,
  lookupLoading: Boolean,
  lookupResult: UsernameDirectoryEntry?,
  lookupError: String?,
  onSearchUsername: (String) -> Unit,
  onStartChatWithEntry: (UsernameDirectoryEntry) -> Unit,
  onStartChatWithContact: (ContactEntry) -> Unit,
  onSaveContact: (UsernameDirectoryEntry) -> Unit,
  onRemoveContact: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  var usernameQuery by remember { mutableStateOf("") }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    // Quick @username Lookup Card
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.PersonSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = strings.newChatTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = usernameQuery,
            onValueChange = { usernameQuery = it },
            modifier = Modifier.weight(1f).testTag("contacts_username_input"),
            placeholder = { Text(strings.enterUsernameHint, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              ),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { onSearchUsername(usernameQuery) },
            enabled = usernameQuery.isNotBlank() && !lookupLoading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(52.dp).testTag("contacts_search_button"),
          ) {
            Text(strings.searchButton, fontWeight = FontWeight.Bold)
          }
        }

        if (lookupLoading) {
          Spacer(modifier = Modifier.height(12.dp))
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(26.dp))
          }
        } else if (!lookupError.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = lookupError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        } else if (lookupResult != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              TawasulAvatar(
                displayName = lookupResult.displayName,
                avatarUrl = lookupResult.avatarUrl,
                size = 44.dp,
                isOnline = lookupResult.isOnline,
                showOnlineDot = true,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = lookupResult.displayName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "@${lookupResult.username}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
              Button(
                onClick = {
                  onSaveContact(lookupResult)
                  onStartChatWithEntry(lookupResult)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp),
              ) {
                Text(strings.startChatAction)
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    when (contactsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
      is UiState.Error -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(contactsState.message, color = MaterialTheme.colorScheme.error)
        }
      }
      is UiState.Success -> {
        val contacts = contactsState.data
        if (contacts.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Box(
              modifier =
                Modifier.size(72.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = strings.contactsEmptyTitle,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = strings.contactsEmptySubtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(contacts, key = { it.contactUid }) { contact ->
              Surface(
                onClick = { onStartChatWithContact(contact) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("contact_item_${contact.contactUid}"),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  TawasulAvatar(
                    displayName = contact.displayName,
                    avatarUrl = contact.avatarUrl,
                    size = 48.dp,
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = contact.displayName,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = "@${contact.username}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  IconButton(
                    onClick = { onStartChatWithContact(contact) },
                    modifier = Modifier.size(44.dp),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Chat,
                      contentDescription = strings.startChatAction,
                      tint = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  IconButton(
                    onClick = { onRemoveContact(contact.contactUid) },
                    modifier = Modifier.size(44.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DeleteOutline,
                      contentDescription = strings.removeContact,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
}
