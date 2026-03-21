package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatListActivity extends AppCompatActivity {

    private static final String TAG = "ChatListActivity";

    private RecyclerView chatsRecyclerView;
    private ImageView backButton;
    private TextView emptyStateText;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ChatsAdapter adapter;
    private List<ChatItem> chatsList = new ArrayList<>();
    private Map<String, ChatItem> chatsMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        backButton = findViewById(R.id.backButton);
        chatsRecyclerView = findViewById(R.id.chatsRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);

        adapter = new ChatsAdapter(chatsList, this::openChat);
        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatsRecyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());

        loadChats();
    }

    private void loadChats() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        Log.d(TAG, "Loading chats for user: " + userId);

        // Get all chat documents
        db.collection("chats")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null || queryDocumentSnapshots == null) {
                        Log.e(TAG, "Error loading chats: " + (error != null ? error.getMessage() : "null"));
                        return;
                    }

                    chatsMap.clear();

                    for (QueryDocumentSnapshot chatDoc : queryDocumentSnapshots) {
                        String chatId = chatDoc.getId();

                        // Check if current user is part of this chat
                        if (chatId.contains(userId)) {
                            // Extract other user ID from chat ID
                            String otherUserId = getOtherUserId(chatId, userId);

                            if (otherUserId != null && !chatsMap.containsKey(chatId)) {
                                // Load the last message from this chat
                                loadLastMessage(chatId, otherUserId);
                            }
                        }
                    }

                    if (chatsMap.isEmpty() && queryDocumentSnapshots.isEmpty()) {
                        showEmptyState();
                    }
                });
    }

    private void loadLastMessage(String chatId, String otherUserId) {
        db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null || queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        return;
                    }

                    DocumentSnapshot lastMessage = queryDocumentSnapshots.getDocuments().get(0);
                    String message = lastMessage.getString("message");
                    Long timestamp = lastMessage.getLong("timestamp");

                    // Load other user details
                    loadUserDetails(chatId, otherUserId, message, timestamp != null ? timestamp : 0);
                });
    }

    private void loadUserDetails(String chatId, String otherUserId, String lastMessage, long timestamp) {
        db.collection("users").document(otherUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");

                        ChatItem chatItem = new ChatItem(otherUserId,
                                name != null ? name : "Unknown User",
                                lastMessage != null ? lastMessage : "",
                                timestamp);

                        // Update or add chat item
                        if (!chatsMap.containsKey(chatId)) {
                            chatsMap.put(chatId, chatItem);
                            chatsList.add(chatItem);

                            // Sort by timestamp (most recent first)
                            chatsList.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                            adapter.notifyDataSetChanged();
                            hideEmptyState();
                        } else {
                            // Update existing chat item
                            ChatItem existing = chatsMap.get(chatId);
                            int index = chatsList.indexOf(existing);
                            if (index >= 0) {
                                chatsList.set(index, chatItem);
                                chatsMap.put(chatId, chatItem);

                                // Re-sort
                                chatsList.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user details: " + e.getMessage());
                });
    }

    private String getOtherUserId(String chatId, String currentUserId) {
        String[] userIds = chatId.split("_");
        if (userIds.length == 2) {
            return userIds[0].equals(currentUserId) ? userIds[1] : userIds[0];
        }
        return null;
    }

    private void showEmptyState() {
        emptyStateText.setVisibility(View.VISIBLE);
        chatsRecyclerView.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        emptyStateText.setVisibility(View.GONE);
        chatsRecyclerView.setVisibility(View.VISIBLE);
    }

    private void openChat(ChatItem chatItem) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("USER_ID", chatItem.getUserId());
        intent.putExtra("USER_NAME", chatItem.getUserName());
        startActivity(intent);
    }

    // Chat item model
    static class ChatItem {
        private String userId;
        private String userName;
        private String lastMessage;
        private long timestamp;

        public ChatItem(String userId, String userName, String lastMessage, long timestamp) {
            this.userId = userId;
            this.userName = userName;
            this.lastMessage = lastMessage;
            this.timestamp = timestamp;
        }

        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getLastMessage() { return lastMessage; }
        public long getTimestamp() { return timestamp; }
    }

    // Adapter for chats list
    static class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ViewHolder> {
        private List<ChatItem> chats;
        private OnChatClickListener listener;

        interface OnChatClickListener {
            void onChatClick(ChatItem chatItem);
        }

        public ChatsAdapter(List<ChatItem> chats, OnChatClickListener listener) {
            this.chats = chats;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatItem chat = chats.get(position);
            holder.userName.setText(chat.getUserName());
            holder.lastMessage.setText(chat.getLastMessage());
            holder.timestamp.setText(getTimeAgo(chat.getTimestamp()));

            holder.itemCard.setOnClickListener(v -> listener.onChatClick(chat));
        }

        private String getTimeAgo(long timestamp) {
            if (timestamp == 0) return "Just now";

            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) return days + "d ago";
            if (hours > 0) return hours + "h ago";
            if (minutes > 0) return minutes + "m ago";
            return "Just now";
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CardView itemCard;
            TextView userName, lastMessage, timestamp;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                itemCard = itemView.findViewById(R.id.itemCard);
                userName = itemView.findViewById(R.id.userName);
                lastMessage = itemView.findViewById(R.id.lastMessage);
                timestamp = itemView.findViewById(R.id.timestamp);
            }
        }
    }
}
