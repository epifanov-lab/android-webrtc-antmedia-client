package com.example.ant_media_test;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

  private static final String LOG_TAG = "MAIN_LOG";
  private static final int PERMISSIONS_CODE = 100;

  private LinearLayout mContainer;
  private WebRtcView mLocalView;
  private WebRtcView mRemoteView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mContainer = findViewById(R.id.container);
    mLocalView = findViewById(R.id.camera_renderer);
    mRemoteView = findViewById(R.id.remote_renderer);

    requestPermissions();
 }

  private void start() {
    WssAntService wss = new WssAntService();

    wss.addListener(new WssAntService.WssAntEventListener() {
      @Override
      public void onConnected() {
        wss.commandJoinRoom();
      }

      @Override
      public void onMyStreamIdReceive(String myStreamId) {
        mLocalView.initialize(true, myStreamId, wss);
      }

      @Override
      public void onRemoteStreamsIdReceive(List<String> remoteStreamsIds) {
        for (int i = 0; i < remoteStreamsIds.size(); i++) {
          String remoteStreamId = remoteStreamsIds.get(i);
          if (i == 0) mRemoteView.initialize(false, remoteStreamId, wss);
        }
      }
    });

    wss.initialize();
  }

  @AfterPermissionGranted(PERMISSIONS_CODE)
  private void requestPermissions() {
    String[] perms = { Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
    if (EasyPermissions.hasPermissions(this, perms)) {
      start();
    } else {
      EasyPermissions.requestPermissions(this,
        "Permissions needed",
        PERMISSIONS_CODE, perms);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
      Toast.makeText(this, "Changes accepted", Toast.LENGTH_SHORT).show();
    }
  }
}
