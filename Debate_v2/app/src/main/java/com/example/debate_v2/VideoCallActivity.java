package com.example.debate_v2;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class VideoCallActivity extends AppCompatActivity {
    private static final String TAG = "VideoCallActivity";

    // Agora variables
    private RtcEngine mRtcEngine;
    private String agoraToken;
    private int agoraUid;

    // UI elements
    private FrameLayout localVideoContainer;
    private FrameLayout remoteVideoContainer;
    private TextView connectingText;

    // Call information
    private String channelName;
    private String otherUserId;
    private String otherUserName;
    private String callerId;
    private boolean isCaller;
    private boolean isMuted = false;
    private boolean isVideoEnabled = true;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private DocumentReference callDocRef;
    private ListenerRegistration callListener;

    // ✅ Event handler as class member
    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() -> {
                Log.d(TAG, "✅ Successfully joined channel: " + channel + " with UID: " + uid);
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                Log.d(TAG, "🎉🎉🎉 REMOTE USER JOINED!!! UID: " + uid);
                setupRemoteVideo(uid);
                Toast.makeText(VideoCallActivity.this, "🎉 " + otherUserName + " joined!", Toast.LENGTH_LONG).show();
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> {
                Log.d(TAG, "Remote user offline: " + uid + ", reason: " + reason);
                remoteVideoContainer.removeAllViews();
                Toast.makeText(VideoCallActivity.this, otherUserName + " left the call", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onError(int err) {
            runOnUiThread(() -> {
                Log.e(TAG, "❌ Agora error " + err + ": " + getErrorMessage(err));
                if (err != 110) {
                    Toast.makeText(VideoCallActivity.this, "Error: " + err, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onTokenPrivilegeWillExpire(String token) {
            runOnUiThread(() -> {
                Log.w(TAG, "⏰ Token expiring, renewing...");
                renewToken();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.d(TAG, "VideoCallActivity STARTED");

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        channelName = getIntent().getStringExtra("channelName");
        otherUserId = getIntent().getStringExtra("otherUserId");
        otherUserName = getIntent().getStringExtra("otherUserName");
        callerId = getIntent().getStringExtra("callerId");

        FirebaseUser currentUser = mAuth.getCurrentUser();
        isCaller = currentUser != null && currentUser.getUid().equals(callerId);

        Log.d(TAG, "Channel: " + channelName);
        Log.d(TAG, "Other user: " + otherUserName);
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        initializeUI();

        if (channelName != null) {
            callDocRef = db.collection("calls").document(channelName);
            setupCallListener();
        }

        initializeAndJoinChannel();
    }

    private void initializeUI() {
        localVideoContainer = findViewById(R.id.localVideoContainer);
        remoteVideoContainer = findViewById(R.id.remoteVideoContainer);
        connectingText = findViewById(R.id.callStatus);

        // ✅ CardViews, not ImageViews!
        CardView muteCard = findViewById(R.id.toggleMicButton);
        CardView videoCard = findViewById(R.id.toggleCameraButton);
        CardView endCallCard = findViewById(R.id.endCallButton);
        CardView switchCameraCard = findViewById(R.id.switchCameraButton);

        Log.d(TAG, "✅ All views found");

        if (muteCard != null) {
            muteCard.setOnClickListener(v -> toggleMute());
        }

        if (videoCard != null) {
            videoCard.setOnClickListener(v -> toggleVideo());
        }

        if (endCallCard != null) {
            endCallCard.setOnClickListener(v -> endCall());
        }

        if (switchCameraCard != null) {
            switchCameraCard.setOnClickListener(v -> switchCamera());
        }
    }

    private void setupCallListener() {
        callListener = callDocRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error listening to call: " + error.getMessage());
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                String status = snapshot.getString("status");
                Log.d(TAG, "Call status changed: " + status);

                if ("ended".equals(status)) {
                    finish();
                }
            }
        });
    }

    private void initializeAndJoinChannel() {
        getOrGenerateAgoraUid((uid) -> {
            agoraUid = uid;
            Log.d(TAG, "✅ Retrieved existing Agora UID: " + agoraUid);

            AgoraTokenManager.getInstance(this).getValidToken(channelName, agoraUid, new AgoraTokenManager.TokenCallback() {
                @Override
                public void onSuccess(String token) {
                    agoraToken = token;

                    try {
                        initializeAgoraEngine();
                        setupVideoConfig();
                        setupLocalVideo();
                        joinChannel();

                        Log.d(TAG, "✅ Agora initialized successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize Agora: " + e.getMessage());
                        Toast.makeText(VideoCallActivity.this,
                                "Failed to initialize video call", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "❌ Failed to get token: " + error);
                    Toast.makeText(VideoCallActivity.this,
                            "Failed to connect to server", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    private void getOrGenerateAgoraUid(UidCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onUidRetrieved(new Random().nextInt(Integer.MAX_VALUE));
            return;
        }

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("agoraUid")) {
                        Long uid = documentSnapshot.getLong("agoraUid");
                        if (uid != null) {
                            callback.onUidRetrieved(uid.intValue());
                            return;
                        }
                    }

                    int newUid = new Random().nextInt(Integer.MAX_VALUE);
                    db.collection("users").document(currentUser.getUid())
                            .update("agoraUid", newUid)
                            .addOnSuccessListener(aVoid -> callback.onUidRetrieved(newUid))
                            .addOnFailureListener(e -> callback.onUidRetrieved(newUid));
                })
                .addOnFailureListener(e -> {
                    callback.onUidRetrieved(new Random().nextInt(Integer.MAX_VALUE));
                });
    }

    private void initializeAgoraEngine() throws Exception {
        RtcEngineConfig config = new RtcEngineConfig();
        config.mContext = getBaseContext();
        config.mAppId = AgoraConfig.APP_ID;
        config.mEventHandler = mRtcEventHandler;

        mRtcEngine = RtcEngine.create(config);
        Log.d(TAG, "✅ RtcEngine created successfully");
    }

    private void setupVideoConfig() {
        mRtcEngine.enableVideo();
        mRtcEngine.enableAudio();

        VideoEncoderConfiguration config = new VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
        );
        mRtcEngine.setVideoEncoderConfiguration(config);
        Log.d(TAG, "✅ Video config set");
    }

    private void setupLocalVideo() {
        SurfaceView surfaceView = new SurfaceView(getBaseContext());
        surfaceView.setZOrderMediaOverlay(true);
        localVideoContainer.addView(surfaceView);

        mRtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
        mRtcEngine.startPreview();
        Log.d(TAG, "✅ Local video setup complete");
    }

    private void setupRemoteVideo(int uid) {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.d(TAG, "SETTING UP REMOTE VIDEO for UID: " + uid);
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            SurfaceView surfaceView = new SurfaceView(getBaseContext());
            surfaceView.setZOrderMediaOverlay(false);
            remoteVideoContainer.removeAllViews();
            remoteVideoContainer.addView(surfaceView);

            mRtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid));

            if (connectingText != null) {
                connectingText.setVisibility(View.GONE);
            }

            Log.d(TAG, "✅ Remote video setup complete for uid: " + uid);
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to setup remote video: " + e.getMessage());
        }
    }

    private void joinChannel() {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log.d(TAG, "📞 Attempting to join channel...");
        Log.d(TAG, "   Channel name: " + channelName);
        Log.d(TAG, "   My Agora UID: " + agoraUid);
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.autoSubscribeAudio = true;
        options.autoSubscribeVideo = true;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
        options.publishCameraTrack = true;
        options.publishMicrophoneTrack = true;

        int result = mRtcEngine.joinChannel(agoraToken, channelName, agoraUid, options);

        Log.d(TAG, "   Join channel result code: " + result);

        if (result == 0) {
            Log.d(TAG, "✅ Join channel call successful");
        } else {
            Log.e(TAG, "❌ Join channel call failed with code: " + result);
        }
    }

    private void renewToken() {
        AgoraTokenManager.getInstance(this).getValidToken(channelName, agoraUid, new AgoraTokenManager.TokenCallback() {
            @Override
            public void onSuccess(String token) {
                agoraToken = token;
                mRtcEngine.renewToken(token);
                Log.d(TAG, "✅ Token renewed successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to renew token: " + error);
            }
        });
    }

    private void toggleMute() {
        isMuted = !isMuted;
        mRtcEngine.muteLocalAudioStream(isMuted);
        Toast.makeText(this, isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void toggleVideo() {
        isVideoEnabled = !isVideoEnabled;

        if (isVideoEnabled) {
            // Turn video ON
            mRtcEngine.enableLocalVideo(true);
            mRtcEngine.muteLocalVideoStream(false);

            // Show local preview again
            if (localVideoContainer.getChildCount() == 0) {
                setupLocalVideo();
            }

            Toast.makeText(this, "Video On", Toast.LENGTH_SHORT).show();
        } else {
            // Turn video OFF
            mRtcEngine.enableLocalVideo(false);
            mRtcEngine.muteLocalVideoStream(true);

            // Remove local preview
            localVideoContainer.removeAllViews();

            Toast.makeText(this, "Video Off", Toast.LENGTH_SHORT).show();
        }
    }


    private void switchCamera() {
        mRtcEngine.switchCamera();
        Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show();
    }

    private void endCall() {
        Log.d(TAG, "Ending call");

        if (callDocRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "ended");
            updates.put("endedAt", System.currentTimeMillis());

            callDocRef.update(updates);
        }

        finish();
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case 110: return "Channel timeout - network issue";
            case 121: return "Invalid user ID";
            case 124: return "Attempted to join same channel twice";
            default: return "Unknown error";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "VideoCallActivity DESTROYED");

        if (callListener != null) {
            callListener.remove();
        }

        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
        }
    }

    interface UidCallback {
        void onUidRetrieved(int uid);
    }
}
