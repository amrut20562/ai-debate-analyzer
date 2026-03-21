package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IdeaActivity extends AppCompatActivity {

    private static final String TAG = "IdeaActivity";

    private ImageView backButton,btnCallHistory;
    private TextView ideaTitle, ideaDescription, clientName;
    private RecyclerView membersRecyclerView;
    private Button groupCallButton,groupChatButton;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String title;

    private String ideaId;
    private String currentUserId;


    private MembersAdapter membersAdapter;
    private final List<Member> membersList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idea);
        Log.d(TAG, "onCreate started");


        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();



        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = auth.getCurrentUser().getUid();

        ideaId = getIntent().getStringExtra("IDEA_ID");

        if (ideaId == null) {
            Toast.makeText(this, "Invalid idea", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Idea ID: " + ideaId);

        // -------- UI --------
        backButton = findViewById(R.id.backButton);
        ideaTitle = findViewById(R.id.ideaTitle);
        ideaDescription = findViewById(R.id.ideaDescription);
        clientName = findViewById(R.id.clientName);
        membersRecyclerView = findViewById(R.id.membersRecyclerView);
        groupCallButton = findViewById(R.id.groupCallButton);
        groupChatButton = findViewById(R.id.groupChatButton);
        btnCallHistory = findViewById(R.id.btnCallHistory);


        membersAdapter = new MembersAdapter(membersList, this::onChatClicked);
        membersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        membersRecyclerView.setAdapter(membersAdapter);

        backButton.setOnClickListener(v -> finish());

        btnCallHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, CallHistoryActivity.class);
            intent.putExtra("IDEA_ID", ideaId);
            startActivity(intent);
        });
        groupChatButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupChatActivity.class);
            intent.putExtra("ideaId", ideaId);
            intent.putExtra("ideaName", title);

            startActivity(intent);
        });


        // ✅ ONLY responsibility of call button now
        groupCallButton.setOnClickListener(v ->checkOrCreateGroupCall());

        loadIdeaData();
        loadMembers();
    }

    // =================================================
    // Call Lobby Navigation
    // =================================================
    private void checkOrCreateGroupCall() {

        DocumentReference callRef = db
                .collection("ideas")
                .document(ideaId)
                .collection("groupCall")
                .document("active");

        db.runTransaction(transaction -> {

            DocumentSnapshot snapshot = transaction.get(callRef);

            boolean isInitiator;
            String channelName;

            if (!snapshot.exists() || Boolean.FALSE.equals(snapshot.getBoolean("isActive"))) {

                clearOldParticipants();
                // 👑 FIRST USER WINS
                channelName = "idea_" + ideaId;

                Map<String, Object> callData = new HashMap<>();
                callData.put("channelName", channelName);
                callData.put("initiatorUid", currentUserId);
                callData.put("isActive", true);
                callData.put("startedAt", System.currentTimeMillis());

                transaction.set(callRef, callData);
                isInitiator = true;

            } else {
                // 👥 Call already exists
                channelName = snapshot.getString("channelName");
                String initiatorUid = snapshot.getString("initiatorUid");
                isInitiator = currentUserId.equals(initiatorUid);
            }

            // ✅ RETURN result (IMPORTANT)
            Map<String, Object> result = new HashMap<>();
            result.put("channelName", channelName);
            result.put("isInitiator", isInitiator);
            return result;

        }).addOnSuccessListener(result -> {

            String channelName = (String) result.get("channelName");
            boolean isInitiator = (boolean) result.get("isInitiator");

            Log.d("CALL_DEBUG", "Transaction result → isInitiator=" + isInitiator);

            launchCallLobby(channelName, isInitiator);

        }).addOnFailureListener(e -> {
            Log.e("CALL_DEBUG", "Failed to create/join call", e);
            Toast.makeText(this, "Failed to start call", Toast.LENGTH_SHORT).show();
        });
    }



    private void launchCallLobby(String channelName, boolean isInitiator) {
        Intent intent = new Intent(this, CallLobbyActivity.class);
        intent.putExtra("IDEA_ID", ideaId);
        intent.putExtra("CHANNEL_NAME", channelName);
        intent.putExtra("IS_INITIATOR", isInitiator);
        startActivity(intent);
    }

    private void clearOldParticipants() {
        FirebaseFirestore.getInstance()
                .collection("ideas")
                .document(ideaId)
                .collection("groupCall")
                .document("active")
                .collection("participants")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (var doc : snapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                    Log.d("CALL_DEBUG", "Old participants cleared");
                });
    }




    // =================================================
    // Firestore data
    // =================================================

    private void loadIdeaData() {
        db.collection("ideas").document(ideaId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        title = doc.getString("title");

                        ideaTitle.setText(title);
                        ideaDescription.setText(doc.getString("description"));
                        clientName.setText(doc.getString("clientName"));
                    }
                });
    }

    private void loadMembers() {
        db.collection("ideas").document(ideaId)
                .collection("members")
                .get()
                .addOnSuccessListener(querySnapshots -> {

                    List<Task<DocumentSnapshot>> userFetchTasks = new ArrayList<>();
                    Map<String, Boolean> onlineStatusMap = new HashMap<>();

                    for (QueryDocumentSnapshot document : querySnapshots) {
                        String userId = document.getString("userId");
                        Boolean isOnline = document.getBoolean("isOnline");

                        if (userId != null) {
                            onlineStatusMap.put(userId, isOnline != null && isOnline);
                            userFetchTasks.add(
                                    db.collection("users").document(userId).get()
                            );
                        }
                    }

                    Tasks.whenAllSuccess(userFetchTasks)
                            .addOnSuccessListener(results -> {
                                membersList.clear();

                                List<Member> online = new ArrayList<>();
                                List<Member> offline = new ArrayList<>();

                                for (Object obj : results) {
                                    DocumentSnapshot userDoc = (DocumentSnapshot) obj;
                                    if (userDoc.exists()) {
                                        String uid = userDoc.getId();
                                        String name = userDoc.getString("name");
                                        if (name == null || name.isEmpty()) {
                                            name = "Unknown User";
                                        }

                                        boolean isOnline = onlineStatusMap.get(uid) != null
                                                && onlineStatusMap.get(uid);

                                        Member member = new Member(uid, name, isOnline);
                                        if (isOnline) online.add(member);
                                        else offline.add(member);
                                    }
                                }

                                membersList.addAll(online);
                                membersList.addAll(offline);
                                membersAdapter.notifyDataSetChanged();
                            });
                });
    }

    private void onChatClicked(Member member) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("USER_ID", member.getUserId());
        intent.putExtra("USER_NAME", member.getName());
        startActivity(intent);
    }

    // =================================================
    // Model
    // =================================================

    public static class Member {
        private final String userId;
        private final String name;
        private final boolean isOnline;

        public Member(String userId, String name, boolean isOnline) {
            this.userId = userId;
            this.name = name;
            this.isOnline = isOnline;
        }

        public String getUserId() { return userId; }
        public String getName() { return name; }
        public boolean isOnline() { return isOnline; }
    }
}
