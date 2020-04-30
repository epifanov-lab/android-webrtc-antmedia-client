package com.example.ant_media_test;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.SurfaceViewRenderer;


public class MainActivity extends AppCompatActivity {

  private SurfaceViewRenderer mCameraRenderer;
  private SurfaceViewRenderer mRemoteRenderer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mCameraRenderer = findViewById(R.id.camera_renderer);
    mRemoteRenderer = findViewById(R.id.remote_renderer);

    WebRtcClient webRtcClient = new WebRtcClient(this, mCameraRenderer, mRemoteRenderer);
    webRtcClient.init();

 }

}
