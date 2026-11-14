package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Compresses an image picked via Android Photo Picker (PickVisualMedia) into a compact
 * JPEG data URI (< 24,000 chars) suitable for safe Firestore storage without extra permissions.
 */
suspend fun compressUriToDataUri(
  context: Context,
  uri: Uri,
  maxDimension: Int = 180,
  maxDataUriLength: Int = 24000,
): String? = withContext(Dispatchers.IO) {
  try {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, bounds)
    }

    var sampleSize = 1
    val largest = max(bounds.outWidth, bounds.outHeight)
    while (largest / sampleSize > maxDimension * 2) {
      sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded =
      resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOpts)
      } ?: return@withContext null

    val scale =
      maxDimension.toFloat() / max(decoded.width, decoded.height).coerceAtLeast(1).toFloat()
    val scaled =
      if (scale < 1f) {
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, w, h, true).also {
          if (it != decoded) decoded.recycle()
        }
      } else {
        decoded
      }

    var quality = 78
    var resultUri: String? = null
    while (quality >= 25) {
      val out = ByteArrayOutputStream()
      scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
      val bytes = out.toByteArray()
      val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
      val candidate = "data:image/jpeg;base64,$base64"
      if (candidate.length <= maxDataUriLength) {
        resultUri = candidate
        break
      }
      quality -= 12
    }
    scaled.recycle()
    resultUri
  } catch (_: Exception) {
    null
  }
}

fun decodeDataUriToBitmap(dataUri: String?): Bitmap? {
  if (dataUri.isNullOrBlank() || !dataUri.startsWith("data:image/")) return null
  return try {
    val commaIndex = dataUri.indexOf(',')
    if (commaIndex <= 0) return null
    val base64Part = dataUri.substring(commaIndex + 1)
    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  } catch (_: Exception) {
    null
  }
}
