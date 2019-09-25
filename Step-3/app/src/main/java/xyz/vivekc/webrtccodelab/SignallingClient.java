package xyz.vivekc.webrtccodelab;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */

class SignallingClient {
    private static SignallingClient instance;
    private String roomName = null;
    private Socket socket;
    boolean isChannelReady = false;
    boolean isInitiator = false;
    boolean isStarted = false;
    private SignalingInterface callback;
    private static final String TAG = "FICLOG: Sign Client";

    //This piece of code should not go into production!!
    //This will help in cases where the node server is running in non-https server and you want to ignore the warnings
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain,
                                       String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authType) {
        }
    }};

    public static SignallingClient getInstance() {
        if (instance == null) {
            instance = new SignallingClient();
        }
        if (instance.roomName == null) {
            //set the room name here
            instance.roomName = "webfic";
        }
        return instance;
    }

    public void init(SignalingInterface signalingInterface) {
        this.callback = signalingInterface;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            //set the socket.io url here
            socket = IO.socket("https://pbh.sytes.net:8443");
            socket.connect();
            Log.d(TAG, "init() called");

            if (!roomName.isEmpty()) {
                emitInitStatement(roomName);
            }


            //room created event.
            socket.on("created", args -> {
                Log.d(TAG, "created call() called with: args = [" + Arrays.toString(args) + "]");
                isInitiator = true;
                callback.onCreatedRoom();
            });

            //room is full event
            socket.on("full", args -> Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]"));

            //peer joined event
            socket.on("join", args -> {
                Log.d(TAG, "join call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                callback.onNewPeerJoined();
            });

            //when you joined a chat room successfully
            socket.on("joined", args -> {
                Log.d(TAG, "joined call() called with: args = [" + Arrays.toString(args) + "]");
                isChannelReady = true;
                callback.onJoinedRoom();
            });

            //log event
            socket.on("log", args -> Log.d("SignallingClient", "log call() called with: args = [" + Arrays.toString(args) + "]"));

            //bye event
            socket.on("bye", args -> callback.onRemoteHangUp((String) args[0]));

            //messages - SDP and ICE candidates are transferred through this
            socket.on("message", args -> {
                Log.d(TAG, "message call() called with: args = [" + Arrays.toString(args) + "]");
                if (args[0] instanceof String) {
                    Log.d(TAG, "String received :: " + args[0]);
                    String data = (String) args[0];
                    if (data.equalsIgnoreCase("got user media")) {
                        Log.d(TAG, "****** onTryToStart() from got user media");
                        callback.onTryToStart();
                    } else
                    if (data.equalsIgnoreCase("bye")) {
                        callback.onRemoteHangUp(data);
                    }else{
                        try {
                            JSONObject dataJson = new JSONObject(data);
                            Log.d("SignallingClient", "Json Received :: " + dataJson.toString());
                            String type = dataJson.getString("type");
                            if (type.equalsIgnoreCase("offer")) {
                                callback.onOfferReceived(dataJson);
                            } else if (type.equalsIgnoreCase("answer") && isStarted) {
                                callback.onAnswerReceived(dataJson);
                            } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                                callback.onIceCandidateReceived(dataJson);
                            }
                        } catch (JSONException e) {
                            Log.d("SignallingClient", "JSON Exception error");
                            e.printStackTrace();
                        }
                    }
                }
                else {
                    if(args[0] instanceof JSONObject){
                        try {
                            JSONObject dataJson = (JSONObject)args[0];
                            Log.d("SignallingClient", "Json Received :: " + dataJson.toString());
                            String type = dataJson.getString("type");
                            if (type.equalsIgnoreCase("offer")) {
                                callback.onOfferReceived(dataJson);
                            } else if (type.equalsIgnoreCase("answer") && isStarted) {
                                callback.onAnswerReceived(dataJson);
                            } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                                callback.onIceCandidateReceived(dataJson);
                            }
                        } catch (JSONException e) {
                            Log.d("SignallingClient", "JSON Exception error");
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(String message) {
        Log.d(TAG, "emitInitStatement() called with: event = [" + "create or join" + "], message = [" + message + "]");
        socket.emit("create or join", message);
    }

    public void emitMessage(String message) {
        Log.d(TAG, "emitMessage() called with: message = [" + message + "]");
        socket.emit("message", message);
    }

    public void emitMessage(SessionDescription message) {
        try {
            Log.d(TAG, "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
            Log.d(TAG, obj.toString());
            socket.emit("message", obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            Log.d(TAG, "Emit ice candidate: message:" + object.toString());
            socket.emit("message", object);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    
    public void close() {
        socket.emit("bye", roomName);
        socket.disconnect();
        socket.close();
    }


    interface SignalingInterface {
        void onRemoteHangUp(String msg);

        void onOfferReceived(JSONObject data);

        void onAnswerReceived(JSONObject data);

        void onIceCandidateReceived(JSONObject data);

        void onTryToStart();

        void onCreatedRoom();

        void onJoinedRoom();

        void onNewPeerJoined();
    }
}
