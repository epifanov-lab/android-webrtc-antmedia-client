package com.example.ant_media_test;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public class WssAntService {

  interface WssAntEventListener {
    void onConnected();
    void onMyStreamIdReceive(String myStreamId);
    void onRemoteStreamsIdReceive(List<String> remoteStreamsIds);
    void onStart();
    void onTakeCandidate(IceCandidate candidate);
    void onTakeConfiguration(SessionDescription sdp);
  }

  public static final String SERVER_URL = "https://twilio.rtt.space/ant-ws/";

  private WebSocket ws;

  public DirectProcessor<String> receiver = DirectProcessor.create();
  public DirectProcessor<String> sender = DirectProcessor.create();
  private WssAntEventListener listener;

  private String myStreamId;
  private List<String> remoteStreamsIds = new ArrayList<>();

  public void setListener(WssAntEventListener listener) {
    this.listener = listener;
  }

  public void initialize() {
    System.out.println("WssAntService.initialize");
    try {
      connect();
      sender.subscribe(this::send);

    } catch (IOException | WebSocketException e) {
      System.out.println("WssAntService.initialize exception: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void connect() throws IOException, WebSocketException {
    WebSocketFactory factory = new WebSocketFactory();
    ws = factory.createSocket(SERVER_URL, 5000);
    ws.addListener(new WebSocketAdapter() {

      @Override
      public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        super.onConnected(websocket, headers);
        System.out.println("WssAntService.onConnected");
        listener.onConnected();
        new Timer().scheduleAtFixedRate(new TimerTask() {
          @Override
          public void run() {
            ws.sendText("{\"command\": \"ping\"}");
          }
        }, 2000, 2000);
      }

      @Override
      public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
        System.out.println("WssAntService.onDisconnected: serverCloseFrame = " + serverCloseFrame + ", clientCloseFrame = " + clientCloseFrame + ", closedByServer = " + closedByServer);
      }

      @Override
      public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        super.onSendingFrame(websocket, frame);
        if (!frame.getPayloadText().contains("ping"))
        System.out.println("WssAntService >>> " + frame.getPayloadText());
      }

      @Override
      public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        super.onFrame(websocket, frame);
        if (!frame.getPayloadText().contains("pong"))
          System.out.println("WssAntService <<< " + frame.getPayloadText());
      }

      @Override
      public void onTextMessage(WebSocket websocket, String message) throws Exception {
        JSONObject object = new JSONObject(message);

        receiver.onNext(message);

        if (object.getString("command").equals("notification")) {
          if (object.getString("definition").equals("joinedTheRoom")) {

            myStreamId = object.getString("streamId");
            listener.onMyStreamIdReceive(myStreamId);

            JSONArray array = object.getJSONArray("streams");
            for (int i = 0; i < array.length(); i++) {
              String remoteStreamId = (String) array.get(i);
              remoteStreamsIds.add(remoteStreamId);
            }
            listener.onRemoteStreamsIdReceive(remoteStreamsIds);

          } else if (object.getString("definition").equals("streamJoined")) {
            String remoteStreamId = object.getString("streamId");
            remoteStreamsIds.add(remoteStreamId);
            listener.onRemoteStreamsIdReceive(remoteStreamsIds);

          } else if (object.getString("definition").equals("streamLeaved")) {
            remoteStreamsIds.remove(object.getString("streamId"));
            //ignore
          }
        } else if (object.getString("command").equals("start")) {
          listener.onStart();

        } else if (object.getString("command").equals("takeCandidate")) {
          IceCandidate candidate = new IceCandidate("", 0, ""); //TODO IMPL
          listener.onTakeCandidate(candidate);

        } else if (object.getString("command").equals("takeConfiguration")) {
          SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(object.getString("type"));
          SessionDescription sdp = new SessionDescription(type, object.getString("sdp"));
          listener.onTakeConfiguration(sdp);
        }
    }});

    ws.connectAsynchronously();
  }

  private void send(String text) {
    ws.sendText(text);
  }

  public void commandJoinRoom() {
    send("{\"command\": \"joinRoom\", \"room\": \"test-room\"}");
  }

  public void commandPlayRemote(String remoteStreamId) {
    send("{" +
      "\"command\": \"play\", " +
      "\"streamId\": \"" + remoteStreamId + "\", " +
      "\"token\": null, " +
      "\"room\": \"test-room\"" +
      "}");
  }

  public void commandPublishOwn(String myStreamId) {
    send("{" +
      "\"command\": \"publish\", " +
      "\"streamId\": \"" + myStreamId + "\", " +
      "\"token\": null, " +
      "\"video\": true, " +
      "\"audio\": true" +
      "}");
  }

  public void commandSendIceCandidate(IceCandidate candidate) {
    //candidate:1931329575 1 udp 1686052607 18.156.135.165 50053 typ srflx raddr 172.31.46.199 rport 50053 generation 0 ufrag OrfT network-id 1 network-cost 50
    if (candidate != null) {
      send("{" +
        "\"command\": \"takeCandidate\", " +
        "\"streamId\": \"" + myStreamId + "\", " +
        "\"label\": \"" + candidate.sdpMLineIndex + "\", " +
        "\"id\": \"" + candidate.sdpMid + "\", " +
        "\"candidate\": \"" + candidate + "\", " +
        "}");
    }
  }

}
