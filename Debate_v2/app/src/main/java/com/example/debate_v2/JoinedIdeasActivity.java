package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JoinedIdeasActivity extends AppCompatActivity {

    private static final String TAG = "JoinedIdeasActivity";

    private RecyclerView ideasRecyclerView;
    private ImageView backButton;
    private TextView emptyStateText;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private IdeasAdapter adapter;
    private List<Idea> ideasList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joined_ideas);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        backButton = findViewById(R.id.backButton);
        ideasRecyclerView = findViewById(R.id.ideasRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);

        adapter = new IdeasAdapter(ideasList, this::openIdeaActivity);
        ideasRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ideasRecyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());

        loadJoinedIdeas();
    }

    private void loadJoinedIdeas() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        Log.d(TAG, "Loading ideas for user: " + userId);

        // First, get all ideas
        db.collection("ideas")
                .whereEqualTo("status", "active")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Set<String> joinedIdeaIds = new HashSet<>();
                    int totalIdeas = queryDocumentSnapshots.size();
                    final int[] checkedCount = {0}; // Counter for checked ideas

                    Log.d(TAG, "Found " + totalIdeas + " active ideas");

                    if (totalIdeas == 0) {
                        showEmptyState();
                        return;
                    }

                    // Check each idea to see if user is a member
                    for (QueryDocumentSnapshot ideaDoc : queryDocumentSnapshots) {
                        String ideaId = ideaDoc.getId();

                        // Check if user is a member of this idea
                        db.collection("ideas").document(ideaId)
                                .collection("members")
                                .document(userId)
                                .get()
                                .addOnSuccessListener(memberDoc -> {
                                    checkedCount[0]++; // Increment checked counter

                                    if (memberDoc.exists()) {
                                        Log.d(TAG, "User is member of idea: " + ideaId);

                                        // User is a member, load the idea details
                                        String title = ideaDoc.getString("title");
                                        String description = ideaDoc.getString("description");
                                        String clientName = ideaDoc.getString("clientName");
                                        Long joinedCount = ideaDoc.getLong("joinedCount");
                                        Long onlineCount = ideaDoc.getLong("onlineCount");

                                        Idea idea = new Idea(ideaId, title, description, clientName,
                                                joinedCount != null ? joinedCount.intValue() : 0,
                                                onlineCount != null ? onlineCount.intValue() : 0);

                                        if (!joinedIdeaIds.contains(ideaId)) {
                                            joinedIdeaIds.add(ideaId);
                                            ideasList.add(idea);
                                            adapter.notifyDataSetChanged();
                                            hideEmptyState();
                                        }
                                    }

                                    // If all ideas have been checked and list is still empty
                                    if (checkedCount[0] == totalIdeas && joinedIdeaIds.isEmpty()) {
                                        showEmptyState();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    checkedCount[0]++; // Increment even on failure
                                    Log.e(TAG, "Error checking membership: " + e.getMessage());

                                    // If all ideas have been checked and list is still empty
                                    if (checkedCount[0] == totalIdeas && joinedIdeaIds.isEmpty()) {
                                        showEmptyState();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load ideas: " + e.getMessage());
                    Toast.makeText(this, "Failed to load ideas: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    showEmptyState();
                });
    }


    private void showEmptyState() {
        emptyStateText.setVisibility(View.VISIBLE);
        ideasRecyclerView.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        emptyStateText.setVisibility(View.GONE);
        ideasRecyclerView.setVisibility(View.VISIBLE);
    }

    private void openIdeaActivity(Idea idea) {
        Intent intent = new Intent(this, IdeaActivity.class);
        intent.putExtra("IDEA_ID", idea.getId());
        startActivity(intent);
    }

    // Idea model class
    static class Idea {
        private String id;
        private String title;
        private String description;
        private String clientName;
        private int joinedCount;
        private int onlineCount;

        public Idea(String id, String title, String description, String clientName,
                    int joinedCount, int onlineCount) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.clientName = clientName;
            this.joinedCount = joinedCount;
            this.onlineCount = onlineCount;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getClientName() { return clientName; }
        public int getJoinedCount() { return joinedCount; }
        public int getOnlineCount() { return onlineCount; }
    }

    // Adapter for ideas list
    static class IdeasAdapter extends RecyclerView.Adapter<IdeasAdapter.ViewHolder> {
        private List<Idea> ideas;
        private OnIdeaClickListener listener;

        interface OnIdeaClickListener {
            void onIdeaClick(Idea idea);
        }

        public IdeasAdapter(List<Idea> ideas, OnIdeaClickListener listener) {
            this.ideas = ideas;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_joined_idea, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Idea idea = ideas.get(position);
            holder.ideaTitle.setText(idea.getTitle());
            holder.ideaDescription.setText(idea.getDescription());
            holder.clientName.setText(idea.getClientName());
            holder.joinedCount.setText(idea.getJoinedCount() + " joined");
            holder.onlineCount.setText(idea.getOnlineCount() + " online");

            holder.itemCard.setOnClickListener(v -> listener.onIdeaClick(idea));
        }

        @Override
        public int getItemCount() {
            return ideas.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CardView itemCard;
            TextView ideaTitle, ideaDescription, clientName, joinedCount, onlineCount;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                itemCard = itemView.findViewById(R.id.itemCard);
                ideaTitle = itemView.findViewById(R.id.ideaTitle);
                ideaDescription = itemView.findViewById(R.id.ideaDescription);
                clientName = itemView.findViewById(R.id.clientName);
                joinedCount = itemView.findViewById(R.id.joinedCount);
                onlineCount = itemView.findViewById(R.id.onlineCount);
            }
        }
    }
}
