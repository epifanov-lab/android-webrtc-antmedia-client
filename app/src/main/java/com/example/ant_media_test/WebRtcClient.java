package com.example.ant_media_test;

import android.content.Context;

import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import static org.webrtc.PeerConnectionFactory.*;
import static org.webrtc.PeerConnectionFactory.builder;
import static org.webrtc.PeerConnectionFactory.initialize;

public class WebRtcClient {

  private static final String LOCAL_TRACK_ID = "local_video_track";

  private Context context;
  private SurfaceViewRenderer mCameraRenderer;
  private SurfaceViewRenderer mRemoteRenderer;

  public WebRtcClient(Context context, SurfaceViewRenderer mCameraRenderer, SurfaceViewRenderer mRemoteRenderer) {
    this.context = context;
    this.mCameraRenderer = mCameraRenderer;
    this.mRemoteRenderer = mRemoteRenderer;
  }

  public void init() {
    EglBase rootEglBase = EglBase.create();

    InitializationOptions options = InitializationOptions.builder(context)
      .setEnableInternalTracer(true)
      .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
      .createInitializationOptions();

    initialize(options);

    PeerConnectionFactory peerConnectionFactory = builder()
      .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
      .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true))
      .createPeerConnectionFactory();

    VideoCapturer cameraCapturer = createCameraCapturer(new Camera1Enumerator());
    VideoSource localVideoSource = peerConnectionFactory.createVideoSource(false);
    SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
    cameraCapturer.initialize(surfaceTextureHelper, context, localVideoSource.getCapturerObserver());;
    cameraCapturer.startCapture(360, 240, 60);
    VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource);
    localVideoTrack.addSink(mCameraRenderer);

    mCameraRenderer.setMirror(true);
    mCameraRenderer.setEnableHardwareScaler(true);
    mCameraRenderer.init(rootEglBase.getEglBaseContext(), null);

  }

  private CameraVideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    String[] deviceNames = enumerator.getDeviceNames();
    for (String deviceName : deviceNames) {
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
