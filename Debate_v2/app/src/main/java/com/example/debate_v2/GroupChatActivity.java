package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import android.widget.Toast;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class GroupChatActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendBtn;
    private TextView ideaTitle;
    private TextView calculateBtn;


    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private String ideaId;
    private String ideaName;
    private String currentUsername;


    private GroupChatAdapter adapter;
    private CollectionReference messagesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        recyclerView = findViewById(R.id.recyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendBtn = findViewById(R.id.sendBtn);
        ideaTitle = findViewById(R.id.ideaTitle);
        calculateBtn = findViewById(R.id.calculateBtn);


        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        // Fetch username from Firestore
        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentUsername = doc.getString("name");
                    }
                });


        ideaId = getIntent().getStringExtra("ideaId");
        ideaName = getIntent().getStringExtra("ideaName");

        ideaTitle.setText(ideaName);

        messagesRef = db.collection("ideas")
                .document(ideaId)
                .collection("messages");

        adapter = new GroupChatAdapter(this, currentUser.getUid());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        listenForMessages();

        sendBtn.setOnClickListener(v -> sendMessage());
        calculateBtn.setOnClickListener(v -> sendIdeaToFlask());

    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        if (currentUsername == null) {
            currentUsername = "Unknown";
        }

        Map<String, Object> message = new HashMap<>();
        message.put("message", text);
        message.put("username", currentUsername);
        message.put("firebaseUid", currentUser.getUid());
        message.put("timestamp", System.currentTimeMillis());

        messagesRef.add(message);
        messageInput.setText("");
    }



    private void listenForMessages() {
        messagesRef.orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    adapter.clearMessages();

                    value.forEach(doc -> {

                        String msg = doc.getString("message");
                        String user = doc.getString("username");
                        String uid = doc.getString("firebaseUid");
                        Long time = doc.getLong("timestamp");

                        if (msg == null) msg = "";
                        if (user == null) user = "Unknown";
                        if (uid == null) uid = "";
                        if (time == null) time = System.currentTimeMillis();

                        adapter.addMessage(msg, user, uid, time);
                    });



                    recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                });
    }

    private void sendIdeaToFlask() {

        Toast.makeText(this, "Calculating result...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {

                URL url = new URL(MainActivity.FLASK_BASE_URL + "/analyze/groupchat");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("ideaId", ideaId);

                OutputStream os = conn.getOutputStream();
                os.write(jsonObject.toString().getBytes());
                os.flush();
                os.close();

                if (conn.getResponseCode() == 200) {

                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject resultJson = new JSONObject(response.toString());
                    JSONObject result = resultJson.getJSONObject("result");

                    String winnerName = result.getJSONObject("winner").getString("username");
                    String summary = result.getJSONObject("summary").toString();
                    String ideaTitle = result.getJSONObject("idea").getString("title");

                    // send participants for UID→username mapping
                    String participants = result.getJSONArray("participants").toString();

                    runOnUiThread(() -> {

                        Intent intent = new Intent(GroupChatActivity.this, ResultActivity.class);
                        intent.putExtra("winnerName", winnerName);
                        intent.putExtra("summary", summary);
                        intent.putExtra("ideaTitle", ideaTitle);
                        intent.putExtra("ideaId", ideaId);
                        intent.putExtra("participants", participants);

                        startActivity(intent);

                    });
                }

                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


}
