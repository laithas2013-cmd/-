package com.example.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface FileReadResult {
  data class Success(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dataUri: String,
  ) : FileReadResult

  data object TooLarge : FileReadResult
  data object Error : FileReadResult
}

private const val MAX_INLINE_FILE_BYTES = 360_000L

suspend fun readDocumentToAttachment(
  context: Context,
  uri: Uri,
): FileReadResult = withContext(Dispatchers.IO) {
  try {
    val resolver = context.contentResolver
    var displayName = "attachment"
    var reportedSize = -1L

    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIdx >= 0) {
            displayName = cursor.getString(nameIdx)?.trim()?.take(180)?.ifBlank { "attachment" } ?: "attachment"
          }
          if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
            reportedSize = cursor.getLong(sizeIdx)
          }
        }
      }

    if (reportedSize > MAX_INLINE_FILE_BYTES) {
      return@withContext FileReadResult.TooLarge
    }

    val bytes =
      resolver.openInputStream(uri)?.use { stream ->
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        while (true) {
          val read = stream.read(buffer)
          if (read <= 0) break
          total += read
          if (total > MAX_INLINE_FILE_BYTES) {
            return@withContext FileReadResult.TooLarge
          }
          out.write(buffer, 0, read)
        }
        out.toByteArray()
      } ?: return@withContext FileReadResult.Error

    val mimeType =
      resolver.getType(uri)?.take(100)
        ?: MimeTypeMap.getFileExtensionFromUrl(displayName)
          ?.lowercase()
          ?.let { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }
        ?: "application/octet-stream"

    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    val dataUri = "data:$mimeType;base64,$base64"
    if (dataUri.length > 515_000) {
      return@withContext FileReadResult.TooLarge
    }

    FileReadResult.Success(
      fileName = displayName,
      mimeType = mimeType,
      sizeBytes = bytes.size.toLong(),
      dataUri = dataUri,
    )
  } catch (_: Exception) {
    FileReadResult.Error
  }
}

fun decodeDataUriBytes(dataUri: String?): ByteArray? {
  if (dataUri.isNullOrBlank()) return null
  return try {
    val commaIdx = dataUri.indexOf(',')
    if (commaIdx <= 0) return null
    val base64Part = dataUri.substring(commaIdx + 1)
    Base64.decode(base64Part, Base64.DEFAULT)
  } catch (_: Exception) {
    null
  }
}

fun openAttachmentWithSystem(
  context: Context,
  fileName: String?,
  mimeType: String?,
  dataUri: String?,
): Boolean {
  val bytes = decodeDataUriBytes(dataUri) ?: return false
  return try {
    val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
    val safeName =
      (fileName ?: "file.bin")
        .replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF ]"), "_")
        .take(100)
        .ifBlank { "file.bin" }
    val outFile = File(dir, safeName)
    FileOutputStream(outFile).use { it.write(bytes) }

    val contentUri =
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: "*/*"

    val viewIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, resolvedMime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    if (viewIntent.resolveActivity(context.packageManager) != null) {
      context.startActivity(viewIntent)
    } else {
      val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = resolvedMime
          putExtra(Intent.EXTRA_STREAM, contentUri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      context.startActivity(
        Intent.createChooser(shareIntent, safeName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
    true
  } catch (_: Exception) {
    false
  }
}

fun formatFileSize(bytes: Long?): String {
  val b = (bytes ?: 0L).coerceAtLeast(0L)
  return when {
    b < 1024L -> "$b B"
    b < 1024L * 1024L -> "${(b / 1024.0).let { "%.1f".format(it) }} KB"
    else -> "${(b / (1024.0 * 1024.0)).let { "%.1f".format(it) }} MB"
  }
}
