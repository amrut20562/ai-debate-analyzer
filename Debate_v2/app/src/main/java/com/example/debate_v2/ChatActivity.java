package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int VIDEO_CALL_PERMISSION_REQUEST = 100;

    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private ImageView backButton, videoCallButton;
    private CardView sendButton;
    private TextView chatName;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private MessagesAdapter adapter;
    private List<Message> messagesList = new ArrayList<>();

    private String otherUserId;
    private String otherUserName;
    private String chatId;

    private ListenerRegistration messagesListener;
    private ListenerRegistration callListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Get other user info from intent
        otherUserId = getIntent().getStringExtra("USER_ID");
        otherUserName = getIntent().getStringExtra("USER_NAME");

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || otherUserId == null) {
            finish();
            return;
        }

        // Generate chat ID
        chatId = getChatId(currentUser.getUid(), otherUserId);

        // Initialize views
        backButton = findViewById(R.id.backButton);
        videoCallButton = findViewById(R.id.videoCallButton);
        chatName = findViewById(R.id.chatName);
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        chatName.setText(otherUserName);

        // Setup RecyclerView
        adapter = new MessagesAdapter(messagesList, currentUser.getUid());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());
        videoCallButton.setOnClickListener(v -> startVideoCall());
        sendButton.setOnClickListener(v -> sendMessage());

        // Load messages in real-time
        loadMessages();

        // ✅ Listen for incoming calls while in chat
        listenForIncomingCalls();
    }

    private String getChatId(String userId1, String userId2) {
        return userId1.compareTo(userId2) < 0 ?
                userId1 + "_" + userId2 : userId2 + "_" + userId1;
    }

    private void loadMessages() {
        messagesListener = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null || queryDocumentSnapshots == null) return;

                    messagesList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String messageId = doc.getId();
                        String senderId = doc.getString("senderId");
                        String text = doc.getString("message");
                        Long timestamp = doc.getLong("timestamp");

                        if (text != null && senderId != null) {
                            Message message = new Message(messageId, senderId, text,
                                    timestamp != null ? timestamp : 0);
                            messagesList.add(message);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (!messagesList.isEmpty()) {
                        messagesRecyclerView.scrollToPosition(messagesList.size() - 1);
                    }
                });
    }

    private void sendMessage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        long timestamp = System.currentTimeMillis();
        Map<String, Object> message = new HashMap<>();
        message.put("senderId", currentUser.getUid());
        message.put("receiverId", otherUserId);
        message.put("message", text);
        message.put("timestamp", timestamp);

        // Create/update the parent chat document
        Map<String, Object> chatDoc = new HashMap<>();
        chatDoc.put("lastMessage", text);
        chatDoc.put("timestamp", timestamp);
        chatDoc.put("participants", java.util.Arrays.asList(currentUser.getUid(), otherUserId));

        db.collection("chats").document(chatId)
                .set(chatDoc)
                .addOnSuccessListener(aVoid -> {
                    db.collection("chats").document(chatId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(documentReference -> {
                                messageInput.setText("");
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
                            });
                });
    }

    // ============================================
    // ✅ VIDEO CALL FUNCTIONALITY (NEW CODE)
    // ============================================

    private void startVideoCall() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkVideoCallPermissions()) {
            requestVideoCallPermissions();
            return;
        }

        Log.d(TAG, "Starting video call...");
        String channelName = "call_" + System.currentTimeMillis();

        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String callerName = doc.getString("name");
                    if (callerName == null) callerName = "Unknown User";

                    Log.d(TAG, "Caller: " + callerName);
                    Log.d(TAG, "Channel: " + channelName);

                    Map<String, Object> callData = new HashMap<>();
                    callData.put("channelName", channelName);
                    callData.put("callerId", currentUser.getUid());
                    callData.put("callerName", callerName);
                    callData.put("receiverId", otherUserId);
                    callData.put("receiverName", otherUserName);
                    callData.put("status", "ringing");
                    callData.put("timestamp", System.currentTimeMillis());
                    callData.put("type", "video");

                    String finalCallerName = callerName;

                    db.collection("calls").document(channelName)
                            .set(callData)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Call document created successfully");
                                sendCallNotification(channelName, finalCallerName);

                                // ✅ FIXED: Match the keys that VideoCallActivity expects!
                                Intent intent = new Intent(ChatActivity.this, VideoCallActivity.class);
                                intent.putExtra("channelName", channelName);  // ✅ Changed from CHANNEL_NAME
                                intent.putExtra("otherUserName", otherUserName);  // ✅ Changed from OTHER_USER_NAME
                                intent.putExtra("callerId", currentUser.getUid());  // ✅ Changed from CALLER_ID
                                intent.putExtra("otherUserId", otherUserId);  // ✅ Added this too!
                                startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to create call", e);
                                Toast.makeText(this, "Failed to start call", Toast.LENGTH_SHORT).show();
                            });
                });
    }


    // ✅ NEW: Send FCM notification via Cloud Function
    private void sendCallNotification(String channelName, String callerName) {
        Log.d(TAG, "Sending call notification to " + otherUserId);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("receiverId", otherUserId);
        notificationData.put("callerName", callerName);
        notificationData.put("channelName", channelName);
        notificationData.put("type", "video_call");
        notificationData.put("timestamp", System.currentTimeMillis());

        db.collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✅ Notification document created");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to create notification: " + e.getMessage());
                });
    }


    // Listen for incoming calls while user is in chat
    private void listenForIncomingCalls() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        Log.d(TAG, "Starting incoming call listener in ChatActivity");

        callListener = db.collection("calls")
                .whereEqualTo("receiverId", currentUser.getUid())
                .whereEqualTo("status", "ringing")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening for calls", error);
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String channelName = doc.getString("channelName");
                            String callerName = doc.getString("callerName");
                            String callerId = doc.getString("callerId");

                            Log.d(TAG, "🔔 Incoming call detected in ChatActivity!");
                            Log.d(TAG, "From: " + callerName);
                            Log.d(TAG, "Channel: " + channelName);

                            // Show incoming call activity
                            Intent intent = new Intent(ChatActivity.this, IncomingCallActivity.class);
                            intent.putExtra("CHANNEL_NAME", channelName);
                            intent.putExtra("CALLER_NAME", callerName);
                            intent.putExtra("CALLER_ID", callerId);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            break;
                        }
                    }
                });
    }

    private boolean checkVideoCallPermissions() {
        return checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestVideoCallPermissions() {
        requestPermissions(new String[]{
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
        }, VIDEO_CALL_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VIDEO_CALL_PERMISSION_REQUEST) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startVideoCall();
            } else {
                Toast.makeText(this, "Camera and microphone permissions are required for video calls", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
        }
        if (callListener != null) {
            callListener.remove();
        }
    }

    // Message model class
    public static class Message {
        private String id;
        private String senderId;
        private String text;
        private long timestamp;

        public Message(String id, String senderId, String text, long timestamp) {
            this.id = id;
            this.senderId = senderId;
            this.text = text;
            this.timestamp = timestamp;
        }

        public String getId() { return id; }
        public String getSenderId() { return senderId; }
        public String getText() { return text; }
        public long getTimestamp() { return timestamp; }
    }
}
