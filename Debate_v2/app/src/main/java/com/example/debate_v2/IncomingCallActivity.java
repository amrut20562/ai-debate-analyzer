package com.example.debate_v2;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class IncomingCallActivity extends AppCompatActivity {
    private static final String TAG = "IncomingCallActivity";

    private TextView callerNameText;
    private CardView acceptButton, declineButton;

    private String callerName, channelName, callerId;
    private Ringtone ringtone;
    private Handler timeoutHandler = new Handler();

    private FirebaseFirestore db;
    private DocumentReference callDocRef;  // ✅ ADDED THIS!

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        db = FirebaseFirestore.getInstance();

        callerNameText = findViewById(R.id.callerNameText);
        acceptButton = findViewById(R.id.acceptButton);
        declineButton = findViewById(R.id.declineButton);

        // Get call details
        callerName = getIntent().getStringExtra("CALLER_NAME");
        channelName = getIntent().getStringExtra("CHANNEL_NAME");
        callerId = getIntent().getStringExtra("CALLER_ID");

        // ✅ ADDED: Setup call document reference
        if (channelName != null) {
            callDocRef = db.collection("calls").document(channelName);
        }

        if (callerName != null) {
            callerNameText.setText(callerName + " is calling...");
        } else {
            callerNameText.setText("Incoming call...");
        }

        playRingtone();
        timeoutHandler.postDelayed(() -> declineCall(), 30000);

        acceptButton.setOnClickListener(v -> acceptCall());
        declineButton.setOnClickListener(v -> declineCall());
    }

    private void playRingtone() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
            ringtone.play();
        } catch (Exception e) {
            Log.e(TAG, "Error playing ringtone: " + e.getMessage());
        }
    }

    private void stopRingtone() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private void acceptCall() {
        Log.d(TAG, "Call accepted");

        // Stop ringtone
        stopRingtone();
        timeoutHandler.removeCallbacksAndMessages(null);

        // Update call status in Firestore
        if (callDocRef != null) {
            callDocRef.update("status", "accepted")
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Call status updated: accepted"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update call status", e));
        }

        // ✅ FIXED: Pass ALL required extras to VideoCallActivity
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra("channelName", channelName);      // ✅ Correct key
        intent.putExtra("otherUserId", callerId);         // ✅ Correct key
        intent.putExtra("otherUserName", callerName);     // ✅ Correct key
        intent.putExtra("callerId", callerId);            // ✅ Correct key
        startActivity(intent);

        finish();
    }

    private void declineCall() {
        Log.d(TAG, "❌ Call declined");
        stopRingtone();
        timeoutHandler.removeCallbacksAndMessages(null);

        // Update call status to "declined"
        updateCallStatus("declined");

        finish();
    }

    private void updateCallStatus(String status) {
        if (channelName == null) return;

        Map<String, Object> callData = new HashMap<>();  // ✅ FIXED: Added type parameters
        callData.put("status", status);
        callData.put("timestamp", System.currentTimeMillis());

        db.collection("calls").document(channelName)
                .update(callData)  // ✅ Changed from .set() to .update()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Call status updated: " + status);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update call status: " + e.getMessage());
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRingtone();
        timeoutHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        // Prevent back button - user must choose accept or decline
    }
}
