package com.example.ant_media_test;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.SurfaceViewRenderer;

import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

  private static final String LOG_TAG = "MAIN_LOG";
  private static final int PERMISSIONS_CODE = 100;

  private SurfaceViewRenderer mCameraRenderer;
  private SurfaceViewRenderer mRemoteRenderer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mCameraRenderer = findViewById(R.id.camera_renderer);
    mRemoteRenderer = findViewById(R.id.remote_renderer);

    requestPermissions();
 }

 private void start() {
   WebRtcClient webRtcClient = new WebRtcClient(this, mCameraRenderer, mRemoteRenderer);
   webRtcClient.init();
 }

  @AfterPermissionGranted(PERMISSIONS_CODE)
  private void requestPermissions() {
    String[] perms = { Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
    if (EasyPermissions.hasPermissions(this, perms)) {
      // Already have permission, do the thing
      start();
    } else {
      // Do not have permissions, request them now
      EasyPermissions.requestPermissions(this,
        "Permissions needed",
        PERMISSIONS_CODE, perms);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    // Forward results to EasyPermissions
    EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
  }

  @Override
  public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
    System.out.println("MainActivity.onPermissionsGranted: " + perms);
  }

  @Override
  public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
    System.out.println("MainActivity.onPermissionsDenied: " + perms);

    if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
      new AppSettingsDialog.Builder(this).build().show();
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
      // Do something after user returned from app settings screen, like showing a Toast.
      Toast.makeText(this, "Changes accepted", Toast.LENGTH_SHORT).show();
    }
  }
}
