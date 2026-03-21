package com.example.debate_v2;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AgoraTokenManager {
    private static final String TAG = "AgoraTokenManager";

    // ✅ CHANGE THIS to your computer's IP address!
    private static final String TOKEN_SERVER_URL = "http://192.168.137.1:3000";

    private Context context;
    private RequestQueue requestQueue;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Singleton instance
    private static AgoraTokenManager instance;

    public static synchronized AgoraTokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new AgoraTokenManager(context.getApplicationContext());
        }
        return instance;
    }

    private AgoraTokenManager(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
    }

    /**
     * Get valid Agora token for a channel
     * ✅ FIXED: Always generates fresh token for each call
     */
    public void getValidToken(String channelName, int uid, TokenCallback callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("User not logged in");
            return;
        }

        // ✅ ALWAYS generate fresh token for each call
        Log.d(TAG, "🔄 Generating fresh token for each call (caching disabled)");
        generateNewToken(channelName, uid, callback);
    }

    /**
     * Generate new token from server
     */
    private void generateNewToken(String channelName, int uid, TokenCallback callback) {
        Log.d(TAG, "📡 Generating new Agora token...");
        Log.d(TAG, "   Server URL: " + TOKEN_SERVER_URL);
        Log.d(TAG, "   Channel: " + channelName);
        Log.d(TAG, "   UID: " + uid);

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("channelName", channelName);
            jsonBody.put("uid", uid);
        } catch (Exception e) {
            callback.onError("JSON error: " + e.getMessage());
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                TOKEN_SERVER_URL + "/generate-token",
                jsonBody,
                response -> {
                    try {
                        String token = response.getString("token");
                        long expiresAt = response.getLong("expiresAt");

                        Log.d(TAG, "✅ Token generated successfully");
                        Log.d(TAG, "   Token (first 30 chars): " + token.substring(0, Math.min(30, token.length())) + "...");
                        Log.d(TAG, "   Expires at: " + new java.util.Date(expiresAt * 1000));

                        // Optional: Save to Firestore for reference
                        saveTokenToFirestore(token, expiresAt, channelName);

                        callback.onSuccess(token);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing token response: " + e.getMessage());
                        callback.onError("Failed to parse token");
                    }
                },
                error -> {
                    String errorMsg = error.getMessage();
                    if (errorMsg == null) errorMsg = "Network error";
                    Log.e(TAG, "❌ Token generation failed: " + errorMsg);

                    // ✅ Better error logging
                    if (error.networkResponse != null) {
                        Log.e(TAG, "   Status code: " + error.networkResponse.statusCode);
                        Log.e(TAG, "   Response: " + new String(error.networkResponse.data));
                    } else {
                        Log.e(TAG, "   No network response - check server connectivity");
                    }

                    callback.onError("Failed to connect to token server: " + errorMsg);
                }
        );

        requestQueue.add(request);
    }

    /**
     * Save token to Firestore for reference/debugging
     */
    private void saveTokenToFirestore(String token, long expiresAt, String channelName) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("agoraToken", token);
        tokenData.put("tokenExpiresAt", expiresAt);
        tokenData.put("tokenUpdatedAt", System.currentTimeMillis() / 1000);
        tokenData.put("channelName", channelName);  // ✅ Store channel name

        db.collection("users")
                .document(currentUser.getUid())
                .update(tokenData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Token saved to Firestore for reference");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "⚠️ Failed to save token (non-critical): " + e.getMessage());
                });
    }

    /**
     * Callback interface for token operations
     */
    public interface TokenCallback {
        void onSuccess(String token);
        void onError(String error);
    }
}
