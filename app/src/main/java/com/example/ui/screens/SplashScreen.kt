package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.i18n.LocalAppStrings
import com.example.ui.theme.TawasulGold
import com.example.ui.theme.TawasulGoldDeep
import com.example.ui.theme.TawasulOnGold
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  onSplashFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalAppStrings.current
  val scaleAnim = remember { Animatable(0.82f) }
  val alphaAnim = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    scaleAnim.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
    )
  }
  LaunchedEffect(Unit) {
    alphaAnim.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 480),
    )
    delay(550L)
    onSplashFinished()
  }

  Box(
    modifier =
      modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.background,
              )
          )
        )
        .testTag("splash_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.padding(32.dp)
          .scale(scaleAnim.value)
          .alpha(alphaAnim.value),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        modifier =
          Modifier.size(112.dp)
            .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = TawasulGold,
        shadowElevation = 8.dp,
      ) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(Brush.linearGradient(listOf(TawasulGold, TawasulGoldDeep))),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_launcher_art),
            contentDescription = strings.appName,
            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(26.dp)),
            contentScale = ContentScale.Crop,
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = strings.appName,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(4.dp))

      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Text(
          text = strings.appTagline,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = TawasulOnGold,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = strings.splashSubtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(32.dp))

      LinearProgressIndicator(
        modifier =
          Modifier.width(168.dp)
            .height(5.dp)
            .clip(CircleShape)
            .testTag("splash_loading_indicator"),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primaryContainer,
      )
    }
  }
}
