package com.example.ant_media_test;

import android.content.Context;

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
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.Arrays;
import java.util.List;

import static org.webrtc.PeerConnection.IceConnectionState;
import static org.webrtc.PeerConnection.IceGatheringState;
import static org.webrtc.PeerConnection.IceServer;
import static org.webrtc.PeerConnection.Observer;
import static org.webrtc.PeerConnection.SignalingState;
import static org.webrtc.PeerConnectionFactory.InitializationOptions;
import static org.webrtc.PeerConnectionFactory.builder;
import static org.webrtc.PeerConnectionFactory.initialize;

public class WebRtcClient {

  interface WebRtcEventsListener {
    void onAddStream(MediaStream mediaStream);
    void onRemoveStream(MediaStream mediaStream);
    void onIceCandidate(IceCandidate candidate);
  }

  private static final String LOCAL_TRACK_ID = "local_video_track";

  private Context context;
  private SurfaceViewRenderer cameraRenderer;
  private SurfaceViewRenderer remoteRenderer;

  private PeerConnectionFactory peerConnectionFactory;
  private PeerConnection peerConnection;
  private WebRtcEventsListener listener;

  public WebRtcClient(Context context, SurfaceViewRenderer cameraRenderer, SurfaceViewRenderer remoteRenderer) {
    this.context = context;
    this.cameraRenderer = cameraRenderer;
    this.remoteRenderer = remoteRenderer;
  }

  public void setListener(WebRtcEventsListener listener) {
    this.listener = listener;
  }



  public void init() {
    EglBase rootEglBase = EglBase.create();

    InitializationOptions options = InitializationOptions.builder(context)
      .setEnableInternalTracer(true)
      .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
      .createInitializationOptions();

    initialize(options);

    peerConnectionFactory = builder()
      .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
      .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true))
      .createPeerConnectionFactory();

    VideoCapturer cameraCapturer = createCameraCapturer(new Camera1Enumerator());
    VideoSource localVideoSource = peerConnectionFactory.createVideoSource(false);
    SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
    cameraCapturer.initialize(surfaceTextureHelper, context, localVideoSource.getCapturerObserver());
    cameraCapturer.startCapture(1280, 720, 60);
    VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource);
    localVideoTrack.addSink(cameraRenderer);

    cameraRenderer.setMirror(true);
    cameraRenderer.setEnableHardwareScaler(true);
    cameraRenderer.init(rootEglBase.getEglBaseContext(), null);

  }

  public void createPeerConnecton() {
    List<IceServer> stunServer = Arrays.asList(
      IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    );

    Observer observer = new Observer() {
      @Override
      public void onSignalingChange(SignalingState signalingState) {

      }

      @Override
      public void onIceConnectionChange(IceConnectionState iceConnectionState) {

      }

      @Override
      public void onIceConnectionReceivingChange(boolean b) {

      }

      @Override
      public void onIceGatheringChange(IceGatheringState iceGatheringState) {

      }

      @Override
      public void onIceCandidate(IceCandidate iceCandidate) {
        listener.onIceCandidate(iceCandidate);
      }

      @Override
      public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

      }

      @Override
      public void onAddStream(MediaStream mediaStream) {
        listener.onAddStream(mediaStream);
        //mediaStream.videoTracks.get(0).addSink(remoteRenderer);
      }

      @Override
      public void onRemoveStream(MediaStream mediaStream) {
        listener.onRemoveStream(mediaStream);
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
}
