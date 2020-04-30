package com.example.ant_media_test;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

  private static final String LOG_TAG = "MAIN_LOG";
  private static final int PERMISSIONS_CODE = 100;

  private WebRtcView mLocalView;
  private WebRtcView mRemoteView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mLocalView = findViewById(R.id.camera_renderer);
    mRemoteView = findViewById(R.id.remote_renderer);

    requestPermissions();
 }

  private void start() {
    WssAntService wss = new WssAntService();

    wss.setListener(new WssAntService.WssAntEventListener() {
      @Override
      public void onConnected() {
        wss.commandJoinRoom();
      }

      @Override
      public void onMyStreamIdReceive(String myStreamId) {
        wss.commandPublishOwn(myStreamId);
        mLocalView.config(true, myStreamId, wss.receiver, wss.sender);
      }

      @Override
      public void onRemoteStreamsIdReceive(List<String> remoteStreamsIds) {
        remoteStreamsIds.forEach(wss::commandPlayRemote);
        mRemoteView.config(false, remoteStreamsIds.get(0), wss.receiver, wss.sender);
      }

      @Override
      public void onStart() {
        //createPeer();
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
