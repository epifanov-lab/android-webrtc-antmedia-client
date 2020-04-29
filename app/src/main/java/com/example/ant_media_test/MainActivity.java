package com.example.ant_media_test;

import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {

  public static final String SERVER_URL = "https://twilio.rtt.space/ant-ws/";

  private String mMyStreamId;
  private List<String> mStreamsIds = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    try {
      connect();
    } catch (IOException | WebSocketException e) {
      System.out.println("@@@@@ CONNECT ERROR: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void connect() throws IOException, WebSocketException {
    WebSocketFactory factory = new WebSocketFactory();
    final WebSocket ws = factory.createSocket(SERVER_URL, 5000);
    ws.addListener(new WebSocketAdapter() {

      @Override
      public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        super.onConnected(websocket, headers);
        System.out.println("@@@@@ onConnected: " + headers);

        new Timer().scheduleAtFixedRate(new TimerTask() {
          @Override
          public void run() {
            ws.sendText("{\"command\": \"ping\"}");
          }
        }, 2000, 2000);

        ws.sendText("{\"command\": \"joinRoom\", \"room\": \"test-room\"}");
      }

      @Override
      public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
        System.out.println("@@@@@ onDisconnected: " + clientCloseFrame + " " + closedByServer);
      }

      @Override
      public void onTextMessage(WebSocket websocket, String message) throws Exception {
        JSONObject object = new JSONObject(message);
        if (!message.contains("pong")) System.out.println("@@@@@ onTextMessage: " + object);
        if (object.getString("command").equals("notification")) {

          if (object.getString("definition").equals("joinedRoom")) {
            mMyStreamId = object.getString("streamId");
            JSONArray array = object.getJSONArray("streams");
            for (int i = 0; i < array.length(); i++) mStreamsIds.add((String) array.get(i));

          } else if (object.getString("definition").equals("streamJoined")) {
              mStreamsIds.add(object.getString("streamId"));

          } else if (object.getString("definition").equals("streamLeaved")) {
              mStreamsIds.remove(object.getString("streamId"));
          }
        }
      }
    });

    ws.connectAsynchronously();
  }

}
