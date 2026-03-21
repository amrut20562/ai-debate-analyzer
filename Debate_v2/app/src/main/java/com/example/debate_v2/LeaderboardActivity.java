package com.example.debate_v2;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LeaderboardActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    LeaderboardAdapter adapter;
    List<LeaderboardModel> list = new ArrayList<>();
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        recyclerView = findViewById(R.id.leaderboardRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LeaderboardAdapter(list);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        String ideaId = getIntent().getStringExtra("ideaId");

        db.collection("ideas")
                .document(ideaId)
                .collection("debateResults")
                .document("latest")
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc.exists()) {
                        List<?> participants = (List<?>) doc.get("participants");

                        for (Object obj : participants) {
                            Map<String, Object> map = (Map<String, Object>) obj;

                            list.add(new LeaderboardModel(
                                    (String) map.get("username"),
                                    ((Number) map.get("score")).doubleValue()
                            ));
                        }

                        adapter.notifyDataSetChanged();
                    }
                });
    }
}
