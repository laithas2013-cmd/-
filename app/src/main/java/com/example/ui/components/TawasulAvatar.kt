package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.util.decodeDataUriToBitmap
import com.example.ui.theme.TawasulGold
import com.example.ui.theme.TawasulGoldDeep
import com.example.ui.theme.TawasulOnGold
import com.example.ui.theme.TawasulOnlineGreen
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TawasulAvatar(
  displayName: String,
  avatarUrl: String?,
  size: Dp = 48.dp,
  isOnline: Boolean = false,
  showOnlineDot: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val decodedBitmap = remember(avatarUrl) {
    if (avatarUrl?.startsWith("data:image/") == true) decodeDataUriToBitmap(avatarUrl) else null
  }

  Box(modifier = modifier.size(size)) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .clip(CircleShape)
          .background(
            Brush.linearGradient(
              colors = listOf(TawasulGold, TawasulGoldDeep)
            )
          )
          .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      when {
        decodedBitmap != null -> {
          Image(
            bitmap = decodedBitmap.asImageBitmap(),
            contentDescription = displayName,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop,
          )
        }
        avatarUrl == "preset:sun" -> {
          Icon(
            imageVector = Icons.Rounded.WbSunny,
            contentDescription = displayName,
            tint = TawasulOnGold,
            modifier = Modifier.size(size * 0.55f),
          )
        }
        avatarUrl == "preset:spark" -> {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = displayName,
            tint = TawasulOnGold,
            modifier = Modifier.size(size * 0.55f),
          )
        }
        avatarUrl == "preset:bolt" -> {
          Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = displayName,
            tint = TawasulOnGold,
            modifier = Modifier.size(size * 0.55f),
          )
        }
        avatarUrl == "preset:heart" -> {
          Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = displayName,
            tint = TawasulOnGold,
            modifier = Modifier.size(size * 0.52f),
          )
        }
        !avatarUrl.isNullOrBlank() && avatarUrl.startsWith("http") -> {
          AsyncImage(
            model = avatarUrl,
            contentDescription = displayName,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop,
          )
        }
        else -> {
          val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"
          Text(
            text = initial,
            color = TawasulOnGold,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp,
          )
        }
      }
    }

    if (showOnlineDot) {
      val dotColor = if (isOnline) TawasulOnlineGreen else Color.Gray.copy(alpha = 0.6f)
      Box(
        modifier =
          Modifier.size((size.value * 0.28f).dp.coerceAtLeast(10.dp))
            .align(Alignment.BottomEnd)
            .offset(x = 1.dp, y = 1.dp)
            .clip(CircleShape)
            .background(dotColor)
            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
      )
    }
  }
}

fun formatMessageTime(timestamp: Timestamp?, isArabic: Boolean): String {
  val date = timestamp?.toDate() ?: return ""
  val locale = if (isArabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
  val fmt = SimpleDateFormat("h:mm a", locale)
  return fmt.format(date)
}

fun formatChatListTime(timestamp: Timestamp?, isArabic: Boolean): String {
  val date = timestamp?.toDate() ?: return ""
  val now = Calendar.getInstance()
  val target = Calendar.getInstance().apply { time = date }
  val locale = if (isArabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
  return if (
    now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
      now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
  ) {
    SimpleDateFormat("h:mm a", locale).format(date)
  } else {
    SimpleDateFormat("MMM d", locale).format(date)
  }
}

fun formatLastSeenTime(timestamp: Timestamp?, isArabic: Boolean): String {
  val date: Date = timestamp?.toDate() ?: return ""
  val locale = if (isArabic) Locale.forLanguageTag("ar") else Locale.ENGLISH
  return SimpleDateFormat("h:mm a", locale).format(date)
}
