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
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
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
  private SurfaceViewRenderer renderer;

  private EglBase rootEglBase;
  private PeerConnectionFactory peerConnectionFactory;
  private PeerConnection peerConnection;

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
        //_peerConnection.addCandidate(candidate);
      }

      @Override
      public void onTakeConfiguration(SessionDescription sdp) {
       /*
       _peerConnection.setRemoteDescription(description);
         if (!widget.isHoster) {
           _peerConnection.createAnswer(_offerSdpConstraints).then((RTCSessionDescription answer) {
             _peerConnection.setLocalDescription(answer);
             sendData({"command": "takeConfiguration", "streamId": widget.streamId, "type": "answer", "sdp": answer.sdp});
           });
         }
       * */
      }
    });
  }

  private void createPeerConnecton() {

    if (isLocal) {
      VideoCapturer cameraCapturer = createCameraCapturer(new Camera1Enumerator());
      VideoSource localVideoSource = peerConnectionFactory.createVideoSource(false);
      SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
      cameraCapturer.initialize(surfaceTextureHelper, getContext(), localVideoSource.getCapturerObserver());
      cameraCapturer.startCapture(1280, 720, 60);
      VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource);
      localVideoTrack.addSink(renderer);

      renderer.setMirror(true);
      renderer.setEnableHardwareScaler(true);
      renderer.init(rootEglBase.getEglBaseContext(), null);
    }

    List<PeerConnection.IceServer> stunServer = Arrays.asList(
      PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    );

    PeerConnection.Observer observer = new PeerConnection.Observer() {
      @Override
      public void onSignalingChange(PeerConnection.SignalingState signalingState) {

      }

      @Override
      public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

      }

      @Override
      public void onIceConnectionReceivingChange(boolean b) {

      }

      @Override
      public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

      }

      @Override
      public void onIceCandidate(IceCandidate iceCandidate) {
        //
      }

      @Override
      public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

      }

      @Override
      public void onAddStream(MediaStream mediaStream) {
        //mediaStream.videoTracks.get(0).addSink(remoteRenderer);
      }

      @Override
      public void onRemoveStream(MediaStream mediaStream) {
        //
      }

      @Override
      public void onDataChannel(DataChannel dataChannel) {

      }

      @Override
      public void onRenegotiationNeeded() {

      }

      @Override
      public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

      }
    };

    peerConnection = peerConnectionFactory.createPeerConnection(stunServer, observer);
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
