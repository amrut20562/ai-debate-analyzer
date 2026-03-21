package com.example.debate_v2;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "video_call_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "🔑 NEW FCM TOKEN: " + token);
        saveFCMToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "📨 Message received from: " + remoteMessage.getFrom());

        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            String type = data.get("type");

            Log.d(TAG, "📦 Data payload: " + data.toString());

            if ("video_call".equals(type)) {
                String callerName = data.get("callerName");
                String channelName = data.get("channelName");
                String callerId = data.get("callerId");

                Log.d(TAG, "📞 Incoming call from: " + callerName);
                showVideoCallNotification(callerName, channelName, callerId);
            }
        }
    }

    private void showVideoCallNotification(String callerName, String channelName, String callerId) {
        Log.d(TAG, "🔔 Showing notification for caller: " + callerName);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Video Call Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for incoming video calls");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            channel.setShowBadge(true);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "✅ Notification channel created");
        }

        // CHANGED: Intent to open IncomingCallActivity
        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.putExtra("CALLER_NAME", callerName);
        intent.putExtra("CHANNEL_NAME", channelName);
        intent.putExtra("CALLER_ID", callerId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("📞 Incoming Video Call")
                .setContentText(callerName + " is calling you")
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setOngoing(false);

        Log.d(TAG, "📤 Sending notification...");

        try {
            notificationManager.notify(12345, notificationBuilder.build());
            Log.d(TAG, "✅ Notification displayed!");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to display notification: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void saveFCMToken(String token) {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (userId == null) {
            Log.e(TAG, "❌ Cannot save token - user not logged in");
            return;
        }

        Log.d(TAG, "💾 Saving token for user: " + userId);

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("fcmToken", token);
        tokenData.put("updatedAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .set(tokenData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ FCM token saved");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save token: " + e.getMessage());
                });
    }
}
