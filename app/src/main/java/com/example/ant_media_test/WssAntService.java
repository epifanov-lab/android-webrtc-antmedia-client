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

public class WssAntService {

  /* todo remove listener */
  interface WssAntEventListener {
    default void onConnected(){}
    default void onMyStreamIdReceive(String myStreamId){}
    default void onRemoteStreamsIdReceive(List<String> remoteStreamsIds){}
    default void onStart(String streamId){}
    default void onTakeCandidate(String streamId, IceCandidate candidate){}
    default void onTakeConfiguration(String streamId, SessionDescription sdp){}
  }

  public static final String SERVER_URL = "https://twilio.rtt.space/ant-ws/";

  private WebSocket ws;

  //public DirectProcessor<String> receiver = DirectProcessor.create();
  //public DirectProcessor<String> sender = DirectProcessor.create();

  private List<WssAntEventListener> listeners = new ArrayList<>();

  private String streamId;
  private List<String> remoteStreamsIds = new ArrayList<>();

  public void addListener(WssAntEventListener listener) {
    System.out.println("##### listener added");
    listeners.add(listener);
  }

  public void removeListener(WssAntEventListener listener) {
    listeners.remove(listener);
  }

  public void initialize() {
    System.out.println("##### WssAntService.initialize");
    try {
      connect();
      //sender.subscribe(this::send);

    } catch (IOException | WebSocketException e) {
      System.out.println("##### WssAntService.initialize exception: " + e.getMessage());
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
        System.out.println("##### WssAntService.onConnected");
        listeners.forEach(listener -> listener.onConnected());
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
        System.out.println("##### WssAntService.onDisconnected: serverCloseFrame = " + serverCloseFrame + ", clientCloseFrame = " + clientCloseFrame + ", closedByServer = " + closedByServer);
      }

      @Override
      public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        super.onSendingFrame(websocket, frame);
        if (!frame.getPayloadText().contains("ping"))
        System.out.println("##### WssAntService >>> " + frame.getPayloadText());
      }

      @Override
      public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        super.onFrame(websocket, frame);
        if (!frame.getPayloadText().contains("pong"))
          System.out.println("##### WssAntService <<< " + frame.getPayloadText());
      }

      @Override
      public void onTextMessage(WebSocket websocket, String message) throws Exception {
        JSONObject object = new JSONObject(message);
        //receiver.onNext(message);

        streamId = object.getString("streamId");

        switch (object.getString("command")) {
          case "notification":
            switch (object.getString("definition")) {
              case "joinedTheRoom":

                JSONArray array = object.optJSONArray("streams");
                if (array != null) {
                  for (int i = 0; i < array.length(); i++) {
                    String remoteStreamId = (String) array.get(i);
                    remoteStreamsIds.add(remoteStreamId);
                  }
                }

                System.out.println("##### joinedTheRoom: my " + streamId);
                System.out.println("##### joinedTheRoom: remote " + remoteStreamsIds);

                listeners.forEach(listener -> {
                  System.out.println("##### emit");
                  listener.onMyStreamIdReceive(streamId);
                  listener.onRemoteStreamsIdReceive(remoteStreamsIds);
                });

                break;
              case "streamJoined":
                String remoteStreamId = object.getString("streamId");
                remoteStreamsIds.add(remoteStreamId);
                listeners.forEach(listener -> listener.onRemoteStreamsIdReceive(remoteStreamsIds));

                break;
              case "streamLeaved":
                remoteStreamsIds.remove(object.getString("streamId"));
                //ignore
                break;
            }
            break;

          case "start":
            listeners.forEach(listener -> listener.onStart(streamId));

            break;
          case "takeCandidate":
            //"candidate":"candidate:4065707667 1 udp 2122260223 172.31.46.199 45920 typ host generation 0 ufrag p4pe network-id 1 network-cost 50",
            //"streamId":"ZUtrstEMAOHxnVOL","label":0,"id":"audio","command":"takeCandidate"
            IceCandidate candidate = new IceCandidate(object.getString("id"),
              object.getInt("label"), object.getString("candidate"));
            System.out.println("##### ws takeCandidate " + candidate);
            listeners.forEach(listener -> listener.onTakeCandidate(streamId, candidate));

            break;
          case "takeConfiguration":
            SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(object.getString("type"));
            SessionDescription sdp = new SessionDescription(type, object.getString("sdp"));
            System.out.println("##### ws takeConfiguration: " + sdp.type);
            listeners.forEach(listener -> listener.onTakeConfiguration(streamId, sdp));
            break;
        }
    }});

    ws.connectAsynchronously();
  }

  public void send(String text) {
    ws.sendText(text);
  }

  public void commandJoinRoom() {
    send("{\"command\":\"joinRoom\", \"room\":\"test-room\"}");
  }

  public void commandPlayRemote(String remoteStreamId) {
    send("{" +
      "\"command\":\"play\", " +
      "\"streamId\":\"" + remoteStreamId + "\", " +
      "\"token\":null, " +
      "\"room\":\"test-room\"" +
      "}");
  }

  public void commandPublishOwn(String myStreamId) {
    send("{" +
      "\"command\":\"publish\", " +
      "\"streamId\":\"" + myStreamId + "\", " +
      "\"token\":null, " +
      "\"video\":true, " +
      "\"audio\":true" +
      "}");
  }

  public void commandSendSdp(String streamId, SessionDescription sdp) {
    System.out.println("##### WS SEND SDP: " + streamId + " " + sdp.type);
    //sendData({"command": "takeConfiguration", "streamId": widget.streamId, "type": "offer", "sdp": description.sdp});
    send("{" +
      "\"command\":\"takeConfiguration\", " +
      "\"streamId\":\"" + streamId + "\", " +
      "\"type\":\"" + sdp.type.canonicalForm() + "\", " +
      "\"sdp\":\"" + sdp.description + "\"" +
      "}");
  }

  public void commandSendIceCandidate(IceCandidate candidate) {
    //candidate:1931329575 1 udp 1686052607 18.156.135.165 50053 typ srflx raddr 172.31.46.199 rport 50053 generation 0 ufrag OrfT network-id 1 network-cost 50
    if (candidate != null) {
      send("{" +
        "\"command\":\"takeCandidate\", " +
        "\"streamId\":\"" + streamId + "\", " +
        "\"label\":" + candidate.sdpMLineIndex + ", " +
        "\"id\":\"" + candidate.sdpMid + "\", " +
        "\"candidate\":\"" + candidate + "\"" +
        "}");
    }
  }

}
