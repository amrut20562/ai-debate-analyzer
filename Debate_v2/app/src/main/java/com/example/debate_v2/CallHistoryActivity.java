package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class CallHistoryActivity extends AppCompatActivity
        implements CallHistoryAdapter.OnCallClickListener {

    private static final String TAG = "CallHistory";

    private String ideaId;
    private RecyclerView recyclerView;
    private CallHistoryAdapter adapter;
    private List<CallHistoryItem> calls = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_history);

        ideaId = getIntent().getStringExtra("IDEA_ID");

        recyclerView = findViewById(R.id.recyclerCalls);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new CallHistoryAdapter(calls, this);
        recyclerView.setAdapter(adapter);

        loadCalls();
    }

    private void loadCalls() {
        FirebaseFirestore.getInstance()
                .collection("ideas")
                .document(ideaId)
                .collection("groupCalls")
                .document("history")
                .collection("calls")          // ✅ MATCHES BACKEND
                .orderBy("createdAt")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Log.d(TAG, "Loaded calls = " + snapshot.size());
                    calls.clear();
                    for (var doc : snapshot.getDocuments()) {
                        CallHistoryItem item = new CallHistoryItem();
                        item.callId = doc.getId();
                        item.jobId = doc.getString("jobId");
                        item.winnerFirebaseUid = doc.getString("winnerFirebaseUid");
                        if (doc.getTimestamp("createdAt") != null) {
                            item.createdAt = doc.getTimestamp("createdAt").toDate().getTime();
                        }

                        calls.add(item);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    public void onCallClicked(CallHistoryItem item) {
        Intent intent = new Intent(this, CallResultActivity.class);
        intent.putExtra("JOB_ID", item.jobId);
        intent.putExtra("IDEA_ID", ideaId);
        intent.putExtra("CALL_ID", item.callId);
        startActivity(intent);
    }
}
