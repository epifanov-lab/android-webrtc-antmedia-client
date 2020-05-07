package com.example.ant_media_test;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.Arrays;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static org.webrtc.PeerConnectionFactory.InitializationOptions;
import static org.webrtc.PeerConnectionFactory.builder;

public class WebRtcView extends FrameLayout {

  private static final String LOCAL_VIDEO_TRACK_ID = "local_video_track_id";
  private static final String LOCAL_AUDIO_TRACK_ID = "local_audio_track_id";
  private static final String LOCAL_STREAM_ID = "local_stream_id";
  private SurfaceViewRenderer renderer;

  private EglBase rootEglBase;
  private PeerConnectionFactory peerConnectionFactory;
  private PeerConnection peerConnection;

  private MediaConstraints offerSdpConstraints;
  private MediaConstraints audioConstraints;

  private boolean isLocal;
  private String streamId;
  private WssAntService wss;

  private boolean initialized = false;

  public WebRtcView(@NonNull Context context) {
    this(context, null);
  }

  public WebRtcView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public WebRtcView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    renderer = new SurfaceViewRenderer(context);
    renderer.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
    addView(renderer);
  }

  public void initialize(boolean isLocal, String streamId, WssAntService service) {
    System.out.println(WebRtcView.this.hashCode() + " " + streamId + " ##### @@@@@ WebRtcView initialize " + isLocal + "  ---  " + streamId);
    this.isLocal = isLocal;
    this.streamId = streamId;
    this.wss = service;
    /* --- */

    rootEglBase = EglBase.create();

    InitializationOptions options = InitializationOptions.builder(getContext())
      .setEnableInternalTracer(true)
      .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
      .createInitializationOptions();

    PeerConnectionFactory.initialize(options);

    peerConnectionFactory = builder()
      .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
      .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true))
      .createPeerConnectionFactory();

    offerSdpConstraints = new MediaConstraints();
    offerSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    offerSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

    audioConstraints = new MediaConstraints();
    audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("audio", "true"));

    if (isLocal) {
      wss.commandPublishOwn(streamId);
    } else {
      ((Activity) getContext()).runOnUiThread(() -> createPeerConnection());
      wss.commandPlayRemote(streamId);
    }

    wss.addListener(new WssAntService.WssAntEventListener() {
      @Override
      public void onStart(String streamId) {
        if (WebRtcView.this.streamId.equals(streamId))
        ((Activity) getContext()).runOnUiThread(() -> createPeerConnection());
      }

      @Override
      public void onTakeCandidate(String streamId, IceCandidate candidate) {
        if (WebRtcView.this.streamId.equals(streamId))
          peerConnection.addIceCandidate(candidate);
      }

      @Override
      public void onTakeConfiguration(String streamId, SessionDescription sdp) {
        if (WebRtcView.this.streamId.equals(streamId)){
          peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
              System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setRemoteDescription onCreateSuccess: " + sdp);
            }

            @Override
            public void onSetSuccess() {
              System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setRemoteDescription onSetSuccess");
            }

            @Override
            public void onCreateFailure(String s) {
              System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setRemoteDescription onCreateFailure: " + s);
            }

            @Override
            public void onSetFailure(String s) {
              System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setRemoteDescription onSetFailure: " + s);
            }
          }, sdp);

          if (!WebRtcView.this.isLocal) {
            peerConnection.createAnswer(new SdpObserver() {
              @Override
              public void onCreateSuccess(SessionDescription sdp) {
                System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView remote createAnswer onCreateSuccess: " + sdp);
                wss.commandSendSdp(streamId, sdp);
                setLocalSdp(sdp);
              }

              @Override
              public void onSetSuccess() {
                System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView remote createAnswer onSetSuccess");
              }

              @Override
              public void onCreateFailure(String s) {
                System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView remote createAnswer onCreateFailure: " + s);
              }

              @Override
              public void onSetFailure(String s) {
                System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView remote createAnswer onSetFailure: " + s);
              }
            }, offerSdpConstraints);
          }
        }
      }
    });
  }

  private void createPeerConnection() {
    System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView @@@@@ PeerConnection.createPeerConnection");
    if (initialized) return;
    initialized = true;

    List<PeerConnection.IceServer> stunServers = Arrays.asList(
      PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    );

    PeerConnection.Observer observer = new PeerConnection.Observer() {
      @Override
      public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onSignalingChange: " + signalingState);
      }

      @Override
      public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onIceConnectionChange: " + iceConnectionState);
      }

      @Override
      public void onIceConnectionReceivingChange(boolean b) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onIceConnectionReceivingChange: " + b);
      }

      @Override
      public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onIceGatheringChange: " + iceGatheringState);
      }

      @Override
      public void onIceCandidate(IceCandidate iceCandidate) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onIceCandidate");
        wss.commandSendIceCandidate(iceCandidate);
      }

      @Override
      public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onIceCandidatesRemoved");
      }

      @Override
      public void onAddStream(MediaStream mediaStream) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onAddStream: " + mediaStream);
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onAddStream: " + renderer);
        if (!isLocal) mediaStream.videoTracks.get(0).addSink(renderer);
      }

      @Override
      public void onRemoveStream(MediaStream mediaStream) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onRemoveStream: " + mediaStream);
      }

      @Override
      public void onDataChannel(DataChannel dataChannel) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onDataChannel: " + dataChannel);
      }

      @Override
      public void onRenegotiationNeeded() {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onRenegotiationNeeded");
      }

      @Override
      public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView PeerConnection.Observer.onAddTrack: " + rtpReceiver + " " + mediaStreams);
      }
    };

    peerConnection = peerConnectionFactory.createPeerConnection(stunServers, observer);

    if (isLocal) {
      SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
      VideoCapturer cameraCapturer = createCameraCapturer(new Camera1Enumerator());
      VideoSource localVideoSource = peerConnectionFactory.createVideoSource(false);
      cameraCapturer.initialize(surfaceTextureHelper, getContext(), localVideoSource.getCapturerObserver());
      cameraCapturer.startCapture(1280, 720, 60);
      VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_VIDEO_TRACK_ID, localVideoSource);
      MediaStream localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
      localStream.addTrack(localVideoTrack);
      System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView, isLocal, add stream: " + localStream);
      localStream.videoTracks.get(0).addSink(renderer);

      peerConnection.setAudioRecording(true);
      peerConnection.setAudioPlayout(true);

      localStream.addTrack(createAudioTrack());

      peerConnection.addStream(localStream);

      peerConnection.createOffer(new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sdp) {
          System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView localSdpObserver.onCreateSuccess: " + sdp);
          setLocalSdp(sdp);
        }

        @Override
        public void onSetSuccess() {
          System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView localSdpObserver.onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
          System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView localSdpObserver.onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
          System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView localSdpObserver.onSetFailure: " + s);
        }
      }, offerSdpConstraints);
    }

    renderer.setMirror(isLocal);
    renderer.setEnableHardwareScaler(true);
    renderer.init(rootEglBase.getEglBaseContext(), null);
  }

  private void setLocalSdp(SessionDescription sdp) {
    peerConnection.setLocalDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setLocalSdp.onCreateSuccess: " + sdp);
      }

      @Override
      public void onSetSuccess() {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setLocalSdp.onSetSuccess");
        wss.commandSendSdp(streamId, sdp);
      }

      @Override
      public void onCreateFailure(String s) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setLocalSdp.onCreateFailure: " + s);
      }

      @Override
      public void onSetFailure(String s) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView setLocalSdp.onSetFailure: " + s);
      }
    }, sdp);
  }

  private AudioTrack createAudioTrack() {
    AudioSource localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
    AudioTrack localAudioTrack = peerConnectionFactory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, localAudioSource);
    localAudioTrack.setEnabled(true);
    return localAudioTrack;
  }

  private CameraVideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    String[] deviceNames = enumerator.getDeviceNames();
    for (String deviceName : deviceNames) {
      System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView.createCameraCapturer cameras: " + deviceName);
      if (enumerator.isFrontFacing(deviceName)) {
        CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
        if (videoCapturer != null) return videoCapturer;
      }
    }

    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        System.out.println(WebRtcView.this.hashCode() + " " + WebRtcView.this.streamId + " ##### WebRtcView Creating other camera capturer.");
        CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
        if (videoCapturer != null) return videoCapturer;
      }
    }

    return null;
  }

  /*
  *todo dispose onDestroy
  */

}
