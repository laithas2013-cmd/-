package com.example.core.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.model.CallIceCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

enum class WebRtcConnectionState {
  IDLE,
  INITIALIZING,
  WAITING_PEER,
  CONNECTING,
  CONNECTED,
  DISCONNECTED,
  FAILED,
}

class TawasulWebRtcClient(
  private val context: Context,
  private val isVideoCall: Boolean,
  private val onLocalOfferReady: (String) -> Unit,
  private val onLocalAnswerReady: (String) -> Unit,
  private val onLocalIceCandidate: (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
) {
  private val appContext = context.applicationContext
  private val eglBase: EglBase? = try {
    EglBase.create()
  } catch (e: Throwable) {
    Log.w(TAG, "EglBase unavailable", e)
    null
  }

  private var peerConnectionFactory: PeerConnectionFactory? = null
  private var peerConnection: PeerConnection? = null
  private var audioSource: AudioSource? = null
  private var localAudioTrack: AudioTrack? = null
  private var videoCapturer: CameraVideoCapturer? = null
  private var surfaceTextureHelper: SurfaceTextureHelper? = null
  private var videoSource: VideoSource? = null
  private var localVideoTrack: VideoTrack? = null
  private var remoteVideoTrack: VideoTrack? = null

  private var localRenderer: SurfaceViewRenderer? = null
  private var remoteRenderer: SurfaceViewRenderer? = null

  private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
  private val appliedRemoteCandidateIds = mutableSetOf<String>()
  private var hasRemoteDescription = false
  private var hasSetRemoteOfferOrAnswer = false
  private var disposed = false

  private val _connectionState = MutableStateFlow(WebRtcConnectionState.IDLE)
  val connectionState = _connectionState.asStateFlow()

  private val _remoteVideoAvailable = MutableStateFlow(false)
  val remoteVideoAvailable = _remoteVideoAvailable.asStateFlow()

  val eglContext: EglBase.Context? get() = eglBase?.eglBaseContext

  fun initialize(hasAudioPermission: Boolean, hasCameraPermission: Boolean) {
    if (disposed || peerConnectionFactory != null || eglBase == null) return
    _connectionState.value = WebRtcConnectionState.INITIALIZING

    try {
      PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(appContext)
          .setEnableInternalTracer(false)
          .createInitializationOptions()
      )

      val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
      val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

      val factory =
        PeerConnectionFactory.builder()
          .setVideoEncoderFactory(encoderFactory)
          .setVideoDecoderFactory(decoderFactory)
          .createPeerConnectionFactory()
      peerConnectionFactory = factory

      val iceServers =
        listOf(
          PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
          PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
          PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        )
      val rtcConfig =
        PeerConnection.RTCConfiguration(iceServers).apply {
          sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
          continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

      peerConnection =
        factory.createPeerConnection(
          rtcConfig,
          object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
              when (state) {
                PeerConnection.IceConnectionState.CHECKING ->
                  _connectionState.value = WebRtcConnectionState.CONNECTING
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED ->
                  _connectionState.value = WebRtcConnectionState.CONNECTED
                PeerConnection.IceConnectionState.DISCONNECTED ->
                  _connectionState.value = WebRtcConnectionState.DISCONNECTED
                PeerConnection.IceConnectionState.FAILED ->
                  _connectionState.value = WebRtcConnectionState.FAILED
                else -> Unit
              }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: IceCandidate?) {
              if (candidate != null && !disposed) {
                onLocalIceCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
              }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) {
              val track = stream?.videoTracks?.firstOrNull()
              if (track != null) attachRemoteTrack(track)
            }

            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
              val track = receiver?.track()
              if (track is VideoTrack) {
                attachRemoteTrack(track)
              }
            }
          },
        )

      if (hasAudioPermission) {
        val audioConstraints = MediaConstraints()
        val src = factory.createAudioSource(audioConstraints)
        audioSource = src
        val track = factory.createAudioTrack("ARDAMSa0", src)
        track.setEnabled(true)
        localAudioTrack = track
        peerConnection?.addTrack(track, listOf("ARDAMS"))
      }

      if (isVideoCall && hasCameraPermission) {
        startCameraCapture(factory)
      }

      configureAudioMode(inCall = true, speakerOn = isVideoCall)
      _connectionState.value = WebRtcConnectionState.WAITING_PEER
    } catch (e: Throwable) {
      Log.w(TAG, "WebRTC initialization failed", e)
      _connectionState.value = WebRtcConnectionState.FAILED
    }
  }

  private fun startCameraCapture(factory: PeerConnectionFactory) {
    val enumerator = Camera2Enumerator(appContext)
    val deviceNames = enumerator.deviceNames
    val selectedCamera =
      deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        ?: deviceNames.firstOrNull()
        ?: return

    val capturer = enumerator.createCapturer(selectedCamera, null) ?: return
    videoCapturer = capturer
    val textureHelper = SurfaceTextureHelper.create("TawasulCaptureThread", eglBase?.eglBaseContext)
    surfaceTextureHelper = textureHelper
    val src = factory.createVideoSource(capturer.isScreencast)
    videoSource = src
    capturer.initialize(textureHelper, appContext, src.capturerObserver)
    try {
      capturer.startCapture(640, 480, 24)
    } catch (e: Throwable) {
      Log.w(TAG, "Camera capture start failed", e)
    }
    val track = factory.createVideoTrack("ARDAMSv0", src)
    track.setEnabled(true)
    localVideoTrack = track
    localRenderer?.let { track.addSink(it) }
    peerConnection?.addTrack(track, listOf("ARDAMS"))
  }

  fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
    val ctx = eglBase?.eglBaseContext ?: return
    if (local != null && localRenderer != local) {
      localRenderer = local
      try {
        local.init(ctx, null)
        local.setMirror(true)
        local.setZOrderMediaOverlay(true)
        localVideoTrack?.addSink(local)
      } catch (_: Throwable) {}
    }
    if (remote != null && remoteRenderer != remote) {
      remoteRenderer = remote
      try {
        remote.init(ctx, null)
        remote.setMirror(false)
        remoteVideoTrack?.addSink(remote)
      } catch (_: Throwable) {}
    }
  }

  private fun attachRemoteTrack(track: VideoTrack) {
    remoteVideoTrack = track
    track.setEnabled(true)
    remoteRenderer?.let { renderer ->
      try {
        track.addSink(renderer)
      } catch (_: Throwable) {}
    }
    _remoteVideoAvailable.value = true
  }

  fun createOffer() {
    val pc = peerConnection ?: return
    val constraints =
      MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(
          MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false")
        )
      }
    pc.createOffer(
      object : SimpleSdpObserver() {
        override fun onCreateSuccess(desc: SessionDescription?) {
          if (desc == null || disposed) return
          pc.setLocalDescription(
            object : SimpleSdpObserver() {
              override fun onSetSuccess() {
                onLocalOfferReady(desc.description)
              }
            },
            desc,
          )
        }
      },
      constraints,
    )
  }

  fun handleRemoteOfferAndCreateAnswer(offerSdp: String) {
    if (hasSetRemoteOfferOrAnswer || offerSdp.isBlank()) return
    val pc = peerConnection ?: return
    hasSetRemoteOfferOrAnswer = true
    _connectionState.value = WebRtcConnectionState.CONNECTING
    val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
    pc.setRemoteDescription(
      object : SimpleSdpObserver() {
        override fun onSetSuccess() {
          hasRemoteDescription = true
          flushPendingCandidates()
          val constraints =
            MediaConstraints().apply {
              mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
              mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false")
              )
            }
          pc.createAnswer(
            object : SimpleSdpObserver() {
              override fun onCreateSuccess(answerDesc: SessionDescription?) {
                if (answerDesc == null || disposed) return
                pc.setLocalDescription(
                  object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                      onLocalAnswerReady(answerDesc.description)
                    }
                  },
                  answerDesc,
                )
              }
            },
            constraints,
          )
        }
      },
      remoteDesc,
    )
  }

  fun handleRemoteAnswer(answerSdp: String) {
    if (hasSetRemoteOfferOrAnswer || answerSdp.isBlank()) return
    val pc = peerConnection ?: return
    hasSetRemoteOfferOrAnswer = true
    _connectionState.value = WebRtcConnectionState.CONNECTING
    val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
    pc.setRemoteDescription(
      object : SimpleSdpObserver() {
        override fun onSetSuccess() {
          hasRemoteDescription = true
          flushPendingCandidates()
        }
      },
      remoteDesc,
    )
  }

  fun addRemoteIceCandidates(candidates: List<CallIceCandidate>, myUid: String) {
    val pc = peerConnection ?: return
    candidates
      .filter { it.senderId != myUid && it.candidate.isNotBlank() && appliedRemoteCandidateIds.add(it.id) }
      .forEach { item ->
        val ice = IceCandidate(item.sdpMid ?: "0", item.sdpMLineIndex, item.candidate)
        if (hasRemoteDescription) {
          pc.addIceCandidate(ice)
        } else {
          pendingRemoteCandidates.add(ice)
        }
      }
  }

  private fun flushPendingCandidates() {
    val pc = peerConnection ?: return
    pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
    pendingRemoteCandidates.clear()
  }

  fun setMicEnabled(enabled: Boolean) {
    localAudioTrack?.setEnabled(enabled)
  }

  fun setCameraEnabled(enabled: Boolean) {
    localVideoTrack?.setEnabled(enabled)
  }

  fun switchCamera() {
    try {
      videoCapturer?.switchCamera(null)
    } catch (_: Throwable) {}
  }

  fun configureAudioMode(inCall: Boolean, speakerOn: Boolean) {
    try {
      val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
      if (inCall) {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = speakerOn
      } else {
        am.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
      }
    } catch (_: Throwable) {}
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    configureAudioMode(inCall = false, speakerOn = false)
    try {
      localRenderer?.let { localVideoTrack?.removeSink(it) }
      remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
    } catch (_: Throwable) {}
    try {
      localRenderer?.release()
    } catch (_: Throwable) {}
    try {
      remoteRenderer?.release()
    } catch (_: Throwable) {}
    localRenderer = null
    remoteRenderer = null

    try {
      videoCapturer?.stopCapture()
    } catch (_: Throwable) {}
    try {
      videoCapturer?.dispose()
    } catch (_: Throwable) {}
    videoCapturer = null

    try {
      surfaceTextureHelper?.dispose()
    } catch (_: Throwable) {}
    surfaceTextureHelper = null

    try {
      localVideoTrack?.dispose()
    } catch (_: Throwable) {}
    try {
      localAudioTrack?.dispose()
    } catch (_: Throwable) {}
    try {
      videoSource?.dispose()
    } catch (_: Throwable) {}
    try {
      audioSource?.dispose()
    } catch (_: Throwable) {}
    try {
      peerConnection?.close()
      peerConnection?.dispose()
    } catch (_: Throwable) {}
    peerConnection = null
    try {
      peerConnectionFactory?.dispose()
    } catch (_: Throwable) {}
    peerConnectionFactory = null
    try {
      eglBase?.release()
    } catch (_: Throwable) {}
  }

  private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
  }

  private companion object {
    const val TAG = "TawasulWebRtc"
  }
}
