package com.example.debate_v2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

public class AgoraVideoCallActivity extends AppCompatActivity {

    private static final String TAG = "AgoraVideoCall";
    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private RtcEngine mRtcEngine;
    private String roomId;
    private String ideaId;
    private String userId;
    private boolean isInitiator;
    private Button endCallButton;
    private TextView callStatusText;
    private FrameLayout localVideoContainer;
    private FrameLayout remoteVideoContainer;
    private FirebaseFirestore db;

    private boolean videoEnabled = true;
    private boolean audioEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agora_video_call);
        Log.d(TAG, "onCreate: Video Call Activity started");

        db = FirebaseFirestore.getInstance();

        // Get call parameters from intent
        Intent intent = getIntent();
        roomId = intent.getStringExtra("ROOM_ID");
        ideaId = intent.getStringExtra("IDEA_ID");
        userId = intent.getStringExtra("USER_ID");
        isInitiator = intent.getBooleanExtra("IS_INITIATOR", false);

        Log.d(TAG, "onCreate: roomId=" + roomId + ", userId=" + userId + ", isInitiator=" + isInitiator);

        // Initialize UI - use correct IDs from your XML
        endCallButton = findViewById(R.id.endCallButton);
        //callStatusText = findViewById(R.id.callStatusText);
        localVideoContainer = findViewById(R.id.localContainer);
        remoteVideoContainer = findViewById(R.id.remoteContainer);

        if (endCallButton != null) {
            endCallButton.setOnClickListener(v -> endCall());
        }

        // Check and request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (hasAllPermissions()) {
                initializeAgoraEngine();
            } else {
                requestPermissions(REQUESTED_PERMISSIONS, PERMISSION_REQ_ID);
            }
        } else {
            initializeAgoraEngine();
        }
    }

    private boolean hasAllPermissions() {
        for (String permission : REQUESTED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "hasAllPermissions: Missing permission - " + permission);
                return false;
            }
        }
        return true;
    }

    private void initializeAgoraEngine() {
        Log.d(TAG, "initializeAgoraEngine: Starting initialization");
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = "ba74e68c7c324f48a38d7284a223c861"; // Replace with your Agora App ID
            config.mEventHandler = mRtcEngineEventHandler;

            mRtcEngine = RtcEngine.create(config);
            Log.d(TAG, "initializeAgoraEngine: RtcEngine created successfully");

            // Video + Audio configuration
            mRtcEngine.enableVideo();
            mRtcEngine.enableAudio();
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);

            // Enable high-quality video
            mRtcEngine.setVideoEncoderConfiguration(new io.agora.rtc2.video.VideoEncoderConfiguration(
                    io.agora.rtc2.video.VideoEncoderConfiguration.VD_640x480,
                    io.agora.rtc2.video.VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                    io.agora.rtc2.video.VideoEncoderConfiguration.STANDARD_BITRATE,
                    io.agora.rtc2.video.VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            ));

            // Setup local video preview - CORRECT CONSTRUCTOR
            if (localVideoContainer != null) {
                VideoCanvas localCanvas = new VideoCanvas(localVideoContainer, VideoCanvas.RENDER_MODE_HIDDEN, 0);
                mRtcEngine.setupLocalVideo(localCanvas);
                Log.d(TAG, "initializeAgoraEngine: Local video setup complete");
            }

            // Join channel after engine is ready
            mRtcEngine.joinChannel(null, roomId, "", 0);
            Log.d(TAG, "initializeAgoraEngine: Joining channel - " + roomId);

        } catch (Exception e) {
            Log.e(TAG, "initializeAgoraEngine Exception", e);
            Toast.makeText(this, "Failed to initialize Agora: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleVideo() {
        if (mRtcEngine == null) return;

        videoEnabled = !videoEnabled;
        if (videoEnabled) {
            mRtcEngine.enableVideo();
        } else {
            mRtcEngine.disableVideo();
        }
        Log.d(TAG, "toggleVideo: Video is now " + (videoEnabled ? "ON" : "OFF"));
    }

    private void toggleMic() {
        if (mRtcEngine == null) return;

        audioEnabled = !audioEnabled;
        mRtcEngine.muteLocalAudioStream(!audioEnabled);
        Log.d(TAG, "toggleMic: Mic is now " + (audioEnabled ? "ON" : "OFF"));
    }

    private void updateCallStatus(String status) {
        if (callStatusText != null) {
            callStatusText.setText(status);
        }
    }

    private void endCall() {
        Log.d(TAG, "endCall: Ending call");

        // Leave Agora channel
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
        }

        // Update Firestore
        if (isInitiator) {
            db.collection("ideas").document(ideaId)
                    .collection("groupCalls")
                    .document("activeCall")
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "endCall: Call record deleted from Firestore");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "endCall: Failed to delete call record", e);
                    });
        } else {
            db.collection("ideas").document(ideaId)
                    .collection("groupCalls")
                    .document("activeCall")
                    .update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "endCall: User removed from call");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "endCall: Failed to remove user from call", e);
                    });
        }

        finish();
    }

    private final IRtcEngineEventHandler mRtcEngineEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.d(TAG, "onJoinChannelSuccess: channel=" + channel + ", uid=" + uid);
            runOnUiThread(() -> {
                updateCallStatus("Connected");
                Toast.makeText(AgoraVideoCallActivity.this, "Joined channel: " + channel, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            Log.d(TAG, "onUserJoined: uid=" + uid);
            runOnUiThread(() -> {
                updateCallStatus("Call in progress");
                // Setup remote user video - CORRECT CONSTRUCTOR
                if (remoteVideoContainer != null) {
                    VideoCanvas remoteCanvas = new VideoCanvas(remoteVideoContainer, VideoCanvas.RENDER_MODE_HIDDEN, uid);
                    mRtcEngine.setupRemoteVideo(remoteCanvas);
                    Log.d(TAG, "onUserJoined: Remote video setup for uid=" + uid);
                }
                Toast.makeText(AgoraVideoCallActivity.this, "User joined: " + uid, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            Log.d(TAG, "onUserOffline: uid=" + uid + ", reason=" + reason);
            runOnUiThread(() -> {
                Toast.makeText(AgoraVideoCallActivity.this, "User left: " + uid, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onError(int err) {
            Log.e(TAG, "onError: " + err + ", desc=" + RtcEngine.getErrorDescription(err));
            runOnUiThread(() -> {
                Toast.makeText(AgoraVideoCallActivity.this, "Agora error: " + RtcEngine.getErrorDescription(err), Toast.LENGTH_SHORT).show();
            });
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                initializeAgoraEngine();
            } else {
                Toast.makeText(this, "Permissions required for video call", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted(int[] grantResults) {
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Cleaning up");

        // Destroy Agora engine
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
        }
    }
}
