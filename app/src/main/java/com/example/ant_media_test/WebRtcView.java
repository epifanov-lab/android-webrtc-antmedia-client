package com.example.ant_media_test;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import static org.webrtc.PeerConnectionFactory.*;
import static org.webrtc.PeerConnectionFactory.builder;

public class WebRtcView extends FrameLayout {

  private static final String LOCAL_TRACK_ID = "local_track_id";
  private static final String LOCAL_STREAM_ID = "local_stream_id";
  private SurfaceViewRenderer renderer;

  private EglBase rootEglBase;
  private PeerConnectionFactory peerConnectionFactory;
  private PeerConnection peerConnection;
  private MediaConstraints constraints;

  private boolean isLocal;
  private String streamId;
  private WssAntService wss;

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

  public void initialize(boolean isHoster, String streamId, WssAntService service) {
    this.isLocal = isHoster;
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

    constraints = new MediaConstraints();
    constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

    if (isHoster) {
      wss.commandPublishOwn(streamId);
    } else {
      //createPeerConnecton();
      wss.commandPlayRemote(streamId);
    }

    wss.addListener(new WssAntService.WssAntEventListener() {
      @Override
      public void onStart() {
        ((Activity) getContext()).runOnUiThread(() -> createPeerConnecton());
      }

      @Override
      public void onTakeCandidate(IceCandidate candidate) {
        peerConnection.addIceCandidate(candidate);
      }

      @Override
      public void onTakeConfiguration(SessionDescription sdp) {
        peerConnection.setRemoteDescription(new SdpObserver() {
          @Override
          public void onCreateSuccess(SessionDescription sdp) {
            System.out.println("##### setRemoteDescription onCreateSuccess: " + sdp);
          }

          @Override
          public void onSetSuccess() {
            System.out.println("##### setRemoteDescription onSetSuccess");
          }

          @Override
          public void onCreateFailure(String s) {
            System.out.println("##### setRemoteDescription onCreateFailure: " + s);
          }

          @Override
          public void onSetFailure(String s) {
            System.out.println("##### setRemoteDescription onSetFailure: " + s);
          }
        }, sdp);

        if (!isLocal) {
          peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
              System.out.println("##### remote createAnswer onCreateSuccess: " + sdp);
              setLocalSdp(sdp);
              wss.commandSendSdp(streamId, sdp);
            }

            @Override
            public void onSetSuccess() {
              System.out.println("##### remote createAnswer onSetSuccess");
            }

            @Override
            public void onCreateFailure(String s) {
              System.out.println("##### remote createAnswer onCreateFailure: " + s);
            }

            @Override
            public void onSetFailure(String s) {
              System.out.println("##### remote createAnswer onSetFailure: " + s);
            }
          }, constraints);
        }
      }
    });
  }

  private void createPeerConnecton() {
    List<PeerConnection.IceServer> stunServer = Arrays.asList(
      PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    );

    PeerConnection.Observer observer = new PeerConnection.Observer() {
      @Override
      public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        System.out.println("##### PeerConnection.Observer.onSignalingChange");
      }

      @Override
      public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        System.out.println("##### PeerConnection.Observer.onIceConnectionChange");
      }

      @Override
      public void onIceConnectionReceivingChange(boolean b) {
        System.out.println("##### PeerConnection.Observer.onIceConnectionReceivingChange");
      }

      @Override
      public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        System.out.println("##### PeerConnection.Observer.onIceGatheringChange");
      }

      @Override
      public void onIceCandidate(IceCandidate iceCandidate) {
        System.out.println("##### PeerConnection.Observer.onIceCandidate");
        wss.commandSendIceCandidate(iceCandidate);
      }

      @Override
      public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        System.out.println("##### PeerConnection.Observer.onIceCandidatesRemoved");
      }

      @Override
      public void onAddStream(MediaStream mediaStream) {
        System.out.println("##### PeerConnection.Observer.onAddStream");
        if (!isLocal) mediaStream.videoTracks.get(0).addSink(renderer);
      }

      @Override
      public void onRemoveStream(MediaStream mediaStream) {
        System.out.println("##### PeerConnection.Observer.onRemoveStream");
      }

      @Override
      public void onDataChannel(DataChannel dataChannel) {
        System.out.println("##### PeerConnection.Observer.onDataChannel");
      }

      @Override
      public void onRenegotiationNeeded() {
        System.out.println("##### PeerConnection.Observer.onRenegotiationNeeded");
      }

      @Override
      public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        System.out.println("##### PeerConnection.Observer.onAddTrack");
      }
    };

    peerConnection = peerConnectionFactory.createPeerConnection(stunServer, observer);

    if (isLocal) {
      VideoCapturer cameraCapturer = createCameraCapturer(new Camera1Enumerator());
      VideoSource localVideoSource = peerConnectionFactory.createVideoSource(false);
      SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
      cameraCapturer.initialize(surfaceTextureHelper, getContext(), localVideoSource.getCapturerObserver());
      cameraCapturer.startCapture(1280, 720, 30);
      VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource);
      localVideoTrack.addSink(renderer);
      MediaStream localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
      localStream.addTrack(localVideoTrack);
      peerConnection.addStream(localStream);

      renderer.setMirror(true);
      renderer.setEnableHardwareScaler(true);
      renderer.init(rootEglBase.getEglBaseContext(), null);

      peerConnection.createOffer(new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sdp) {
          System.out.println("##### localSdpObserver.onCreateSuccess: " + sdp);
          setLocalSdp(sdp);
        }

        @Override
        public void onSetSuccess() {
          System.out.println("##### localSdpObserver.onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
          System.out.println("##### localSdpObserver.onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
          System.out.println("##### localSdpObserver.onSetFailure: " + s);
        }
      }, constraints);
    }
  }

  private void setLocalSdp(SessionDescription sdp) {
    peerConnection.setLocalDescription(new SdpObserver() {
      @Override
      public void onCreateSuccess(SessionDescription sdp) {
        System.out.println("##### setLocalSdp.onCreateSuccess: " + sdp);
      }

      @Override
      public void onSetSuccess() {
        System.out.println("##### setLocalSdp.onSetSuccess");
        wss.commandSendSdp(streamId, sdp);
      }

      @Override
      public void onCreateFailure(String s) {
        System.out.println("##### setLocalSdp.onCreateFailure: " + s);
      }

      @Override
      public void onSetFailure(String s) {
        System.out.println("##### setLocalSdp.onSetFailure: " + s);
      }
    }, sdp);
  }

  private CameraVideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    String[] deviceNames = enumerator.getDeviceNames();
    for (String deviceName : deviceNames) {
      System.out.println("WebRtcClient.createCameraCapturer cameras: " + deviceName);
      if (enumerator.isFrontFacing(deviceName)) {
        CameraVideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
        if (videoCapturer != null) return videoCapturer;
      }
    }

    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        System.out.println("Creating other camera capturer.");
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
