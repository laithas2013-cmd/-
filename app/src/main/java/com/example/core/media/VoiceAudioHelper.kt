package com.example.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import com.example.core.util.decodeDataUriBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class RecordedVoiceClip(
  val dataUri: String,
  val durationMs: Long,
  val sizeBytes: Long,
)

class VoiceRecorderHelper(private val context: Context) {
  private var recorder: MediaRecorder? = null
  private var outputFile: File? = null
  private var startTimeMs: Long = 0L

  fun start(): Boolean {
    stopAndDiscard()
    return try {
      val dir = File(context.cacheDir, "voice").apply { mkdirs() }
      val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
      outputFile = file

      val mr =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(context)
        } else {
          @Suppress("DEPRECATION")
          MediaRecorder()
        }
      mr.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(16_000)
        setAudioEncodingBitRate(24_000)
        setMaxDuration(90_000)
        setMaxFileSize(350_000L)
        setOutputFile(file.absolutePath)
        prepare()
        start()
      }
      recorder = mr
      startTimeMs = System.currentTimeMillis()
      true
    } catch (_: Exception) {
      stopAndDiscard()
      false
    }
  }

  fun maxAmplitudeRatio(): Float {
    return try {
      val amp = recorder?.maxAmplitude ?: 0
      (amp / 18000f).coerceIn(0.1f, 1f)
    } catch (_: Exception) {
      0.15f
    }
  }

  fun stopAndEncode(): RecordedVoiceClip? {
    val file = outputFile
    val elapsed = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(400L)
    try {
      recorder?.stop()
    } catch (_: Exception) {
      stopAndDiscard()
      return null
    } finally {
      try {
        recorder?.release()
      } catch (_: Exception) {}
      recorder = null
    }

    if (file == null || !file.exists() || file.length() <= 0L) {
      file?.delete()
      outputFile = null
      return null
    }

    return try {
      val bytes = file.readBytes()
      file.delete()
      outputFile = null
      if (bytes.size > 365_000) return null
      val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
      val uri = "data:audio/mp4;base64,$base64"
      if (uri.length > 515_000) null
      else RecordedVoiceClip(dataUri = uri, durationMs = elapsed, sizeBytes = bytes.size.toLong())
    } catch (_: Exception) {
      file.delete()
      outputFile = null
      null
    }
  }

  fun stopAndDiscard() {
    try {
      recorder?.stop()
    } catch (_: Exception) {}
    try {
      recorder?.release()
    } catch (_: Exception) {}
    recorder = null
    outputFile?.delete()
    outputFile = null
  }
}

data class VoicePlaybackState(
  val playingMessageId: String? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
)

class VoicePlayerController(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  private var player: MediaPlayer? = null
  private var progressJob: Job? = null

  private val _state = MutableStateFlow(VoicePlaybackState())
  val state = _state.asStateFlow()

  fun togglePlay(messageId: String, mediaData: String?, fallbackDurationMs: Long = 0L) {
    val current = _state.value
    if (current.playingMessageId == messageId && player != null) {
      if (current.isPlaying) {
        try {
          player?.pause()
          progressJob?.cancel()
          _state.value = current.copy(isPlaying = false)
        } catch (_: Exception) {
          stop()
        }
      } else {
        try {
          player?.start()
          _state.value = current.copy(isPlaying = true)
          startProgressLoop(messageId)
        } catch (_: Exception) {
          stop()
        }
      }
      return
    }

    stop()
    val bytes = decodeDataUriBytes(mediaData) ?: return
    try {
      val dir = File(context.cacheDir, "voice").apply { mkdirs() }
      val tempFile = File(dir, "play_${messageId.take(24)}.m4a")
      FileOutputStream(tempFile).use { it.write(bytes) }

      val mp = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        setDataSource(tempFile.absolutePath)
        prepare()
        setOnCompletionListener {
          stop()
        }
        start()
      }
      player = mp
      val dur = mp.duration.toLong().takeIf { it > 0L } ?: fallbackDurationMs
      _state.value = VoicePlaybackState(
        playingMessageId = messageId,
        isPlaying = true,
        positionMs = 0L,
        durationMs = dur,
      )
      startProgressLoop(messageId)
    } catch (_: Exception) {
      stop()
    }
  }

  private fun startProgressLoop(messageId: String) {
    progressJob?.cancel()
    progressJob = scope.launch(Dispatchers.Main) {
      while (player != null && _state.value.playingMessageId == messageId && _state.value.isPlaying) {
        val pos = try { player?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }
        val dur = try { player?.duration?.toLong()?.takeIf { it > 0L } ?: _state.value.durationMs } catch (_: Exception) { _state.value.durationMs }
        _state.value = _state.value.copy(positionMs = pos, durationMs = dur)
        delay(120L)
      }
    }
  }

  fun stop() {
    progressJob?.cancel()
    progressJob = null
    try {
      player?.stop()
    } catch (_: Exception) {}
    try {
      player?.release()
    } catch (_: Exception) {}
    player = null
    _state.value = VoicePlaybackState()
  }
}

fun formatDurationMs(ms: Long?): String {
  val totalSeconds = ((ms ?: 0L) / 1000L).coerceAtLeast(0L)
  val mins = totalSeconds / 60
  val secs = totalSeconds % 60
  return "%d:%02d".format(mins, secs)
}
