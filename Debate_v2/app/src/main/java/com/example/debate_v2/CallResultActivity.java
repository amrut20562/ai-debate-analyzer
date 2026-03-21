package com.example.debate_v2;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CallResultActivity extends AppCompatActivity {

    private String jobId;
    private String ideaId;
    private String callId;
    private JSONObject cachedSummary;
    private Map<String, String> uidToName = new HashMap<>();

    private TextView txtWinner;
    private TextView txtSummary;
    private TextView txtTranscript;

    private RecyclerView recyclerScoreboard;
    private ScoreboardAdapter scoreboardAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_result);

        txtWinner = findViewById(R.id.txtWinner);
        txtSummary = findViewById(R.id.txtSummary);
        txtTranscript = findViewById(R.id.txtTranscript);

        recyclerScoreboard = findViewById(R.id.recyclerScoreboard);
        recyclerScoreboard.setLayoutManager(new LinearLayoutManager(this));

        scoreboardAdapter = new ScoreboardAdapter();
        recyclerScoreboard.setAdapter(scoreboardAdapter);

        jobId = getIntent().getStringExtra("JOB_ID");
        ideaId = getIntent().getStringExtra("IDEA_ID");
        callId = getIntent().getStringExtra("CALL_ID");

        if (ideaId != null && callId != null) {
            fetchResultFromFirestore();
        } else if (jobId != null) {
            fetchResultFromApi();
        }
    }

    // ---------------------------------------------------------
    // Fetch result directly from Firestore
    // ---------------------------------------------------------

    private void fetchResultFromFirestore() {

        FirebaseFirestore.getInstance()
                .collection("ideas")
                .document(ideaId)
                .collection("groupCalls")
                .document("history")
                .collection("calls")
                .document(callId)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists())
                        return;

                    String winnerUid = doc.getString("winnerFirebaseUid");

                    JSONObject summary = null;

                    Object summaryObj = doc.get("summary");
                    if (summaryObj instanceof Map) {
                        summary = new JSONObject((Map) summaryObj);
                    }

                    List<ScoreboardItem> items = new ArrayList<>();

                    Object scoresObj = doc.get("scores");
                    if (scoresObj == null)
                        scoresObj = doc.get("scoreboard");

                    if (scoresObj == null) {
                        Object ui = doc.get("ui");
                        if (ui instanceof Map) {
                            scoresObj = ((Map<?, ?>) ui).get("scoreboard");
                            if (scoresObj == null)
                                scoresObj = ((Map<?, ?>) ui).get("scores");
                        }
                    }

                    if (scoresObj instanceof List) {

                        List<?> list = (List<?>) scoresObj;

                        for (Object obj : list) {

                            if (obj instanceof Map) {

                                Map<?, ?> map = (Map<?, ?>) obj;

                                ScoreboardItem item = new ScoreboardItem();

                                item.firebaseUid = getMapString(
                                        map,
                                        "firebaseUid",
                                        "uid",
                                        "userId",
                                        "user_id");

                                Object scoreVal = getMapValue(map, "score", "points", "value");

                                if (scoreVal instanceof Number) {
                                    item.score = ((Number) scoreVal).doubleValue();
                                }

                                if (item.firebaseUid != null) {
                                    items.add(item);
                                }
                            }
                        }

                    } else if (scoresObj instanceof Map) {

                        Map<?, ?> map = (Map<?, ?>) scoresObj;

                        for (Map.Entry<?, ?> entry : map.entrySet()) {

                            ScoreboardItem item = new ScoreboardItem();

                            item.firebaseUid = String.valueOf(entry.getKey());

                            if (entry.getValue() instanceof Number) {
                                item.score = ((Number) entry.getValue()).doubleValue();
                            }

                            items.add(item);
                        }
                    }

                    updateUI(winnerUid, summary, items);
                })
                .addOnFailureListener(e -> Log.e("CallResult", "Firestore error", e));
    }

    // ---------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------

    private String getMapString(Map<?, ?> map, String... keys) {

        for (String key : keys) {

            Object val = map.get(key);

            if (val instanceof String)
                return (String) val;
        }

        return null;
    }

    private Object getMapValue(Map<?, ?> map, String... keys) {

        for (String key : keys) {

            Object val = map.get(key);

            if (val != null)
                return val;
        }

        return null;
    }

    // ---------------------------------------------------------
    // Update UI
    // ---------------------------------------------------------

    private void updateUI(String winnerUid, JSONObject summary, List<ScoreboardItem> scoreboardItems) {

        if (winnerUid == null) {
            txtWinner.setText("Winner: —");
        } else {

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(winnerUid)
                    .get()
                    .addOnSuccessListener(doc -> {

                        String name = doc.getString("name");

                        txtWinner.setText("Winner: " + (name != null ? name : winnerUid));
                    });
        }

        if (summary != null) {

            try {

                JSONObject metadata = summary.getJSONObject("metadata");

                JSONObject rankings = summary.getJSONObject("speaker_rankings");

                JSONObject sentiment = summary.getJSONObject("debate_sentiment_distribution");

                int speakers = metadata.getInt("total_speakers");
                int messages = metadata.getInt("total_segments");

                double positive = sentiment.getDouble("positive_percent");
                double negative = sentiment.getDouble("negative_percent");
                double neutral = sentiment.getDouble("neutral_percent");

                JSONObject speakerMap = summary.optJSONObject("speaker_mapping");

                // Get speaker labels
                String aggressiveSpeaker = rankings.getString("most_aggressive_speaker");
                String constructiveSpeaker = rankings.getString("most_constructive_speaker");
                String emotionalSpeaker = rankings.getString("most_emotional_speaker");

                // Convert SPEAKER → Firebase UID
                String aggressiveUid = aggressiveSpeaker;
                String constructiveUid = constructiveSpeaker;
                String emotionalUid = emotionalSpeaker;

                if (speakerMap != null) {
                    if (speakerMap.has(aggressiveSpeaker)) {
                        aggressiveUid = speakerMap.getString(aggressiveSpeaker);
                    }
                    if (speakerMap.has(constructiveSpeaker)) {
                        constructiveUid = speakerMap.getString(constructiveSpeaker);
                    }
                    if (speakerMap.has(emotionalSpeaker)) {
                        emotionalUid = speakerMap.getString(emotionalSpeaker);
                    }
                }

                cachedSummary = summary;

            } catch (Exception e) {

                Log.e("Transcript", "Parsing error", e);
            }
        }

        scoreboardAdapter.setItems(scoreboardItems);

        resolveDisplayNames(scoreboardItems);
    }

    // ---------------------------------------------------------
    // Resolve Firebase UID → username
    // ---------------------------------------------------------

    private void resolveDisplayNames(List<ScoreboardItem> items) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        final int total = items.size();
        final int[] loaded = { 0 };

        for (ScoreboardItem item : items) {

            db.collection("users")
                    .document(item.firebaseUid)
                    .get()
                    .addOnSuccessListener(doc -> {

                        String name = doc.getString("name");

                        String finalName = (name != null) ? name : item.firebaseUid;

                        item.displayName = finalName;

                        // ✅ STORE IN MAP
                        uidToName.put(item.firebaseUid, finalName);

                        loaded[0]++;

                        // ✅ WHEN ALL USERS LOADED → BUILD TRANSCRIPT
                        if (loaded[0] == total) {
                            buildFullUI();
                        }

                        scoreboardAdapter.notifyDataSetChanged();
                    })
                    .addOnFailureListener(e -> {

                        item.displayName = item.firebaseUid;

                        loaded[0]++;

                        if (loaded[0] == total) {
                            buildFullUI();
                        }

                        scoreboardAdapter.notifyDataSetChanged();
                    });
        }
    }

    private void buildFullUI() {

        if (cachedSummary == null)
            return;

        try {

            JSONObject summary = cachedSummary;

            JSONObject metadata = summary.getJSONObject("metadata");
            JSONObject rankings = summary.getJSONObject("speaker_rankings");
            JSONObject sentiment = summary.getJSONObject("debate_sentiment_distribution");

            JSONObject speakerMap = summary.optJSONObject("speaker_mapping");
            JSONObject aiJudge = summary.optJSONObject("ai_judge");

            String judgeVerdict = "";
            String winnerReason = "";
            String aiWinner = "";

            if (aiJudge != null) {

                judgeVerdict = aiJudge.optString("judge_verdict", "");
                winnerReason = aiJudge.optString("winner_reason", "");
                aiWinner = aiJudge.optString("winner", "");

                // 🔥 Replace SPEAKER labels with real names
                judgeVerdict = replaceSpeakerLabels(judgeVerdict, speakerMap);
                winnerReason = replaceSpeakerLabels(winnerReason, speakerMap);
            }
            String aiWinnerName = resolveSpeaker(aiWinner, speakerMap);

            JSONArray keyMomentsArray = aiJudge.optJSONArray("key_moments");
            StringBuilder keyMomentsText = new StringBuilder();

            if (keyMomentsArray != null) {
                for (int i = 0; i < keyMomentsArray.length(); i++) {
                    String moment = keyMomentsArray.optString(i);
                    moment = replaceSpeakerLabels(moment, speakerMap);

                    keyMomentsText.append("• ").append(moment).append("\n");
                }
            }

            int speakers = metadata.getInt("total_speakers");
            int messages = metadata.getInt("total_segments");

            double positive = sentiment.getDouble("positive_percent");
            double negative = sentiment.getDouble("negative_percent");
            double neutral = sentiment.getDouble("neutral_percent");

            // 🔥 MAP RANKINGS PROPERLY
            String aggressive = resolveSpeaker(rankings.getString("most_aggressive_speaker"), speakerMap);
            String constructive = resolveSpeaker(rankings.getString("most_constructive_speaker"), speakerMap);
            String emotional = resolveSpeaker(rankings.getString("most_emotional_speaker"), speakerMap);

            String formatted = "📊 Debate Statistics\n\n" +
                    "Speakers: " + speakers + "\n" +
                    "Messages: " + messages + "\n\n" +

                    "🔥 Speaker Rankings\n\n" +
                    "Most Aggressive: " + aggressive + "\n" +
                    "Most Constructive: " + constructive + "\n" +
                    "Most Emotional: " + emotional + "\n\n" +

                    "🧠 Debate Sentiment\n\n" +
                    "Positive: " + positive + "%\n" +
                    "Negative: " + negative + "%\n" +
                    "Neutral: " + neutral + "%\n\n" +

                    "🤖 AI Judge\n\n" +
                    "1. AI Winner: " + aiWinnerName + "\n\n" +
                    "2. Reason:\n " + winnerReason + "\n\n" +
                    "3. Verdict:\n" + judgeVerdict + "\n\n" +

                    "🔥Key Moments:\n" + keyMomentsText.toString();

            txtSummary.setText(formatted);

            // ---------------- TRANSCRIPT ----------------

            JSONArray transcript = summary.getJSONArray("conversation_with_sentiment");

            StringBuilder transcriptText = new StringBuilder();

            for (int i = 0; i < transcript.length(); i++) {

                JSONObject seg = transcript.getJSONObject(i);

                String speakerLabel = seg.getString("speaker");
                String text = seg.getString("text");
                String sentimentLabel = seg.getString("sentiment");

                String name = resolveSpeaker(speakerLabel, speakerMap);

                String sentimentTag = "[NEUTRAL]";
                if ("Positive".equalsIgnoreCase(sentimentLabel)) {
                    sentimentTag = "[POSITIVE]";
                } else if ("Negative".equalsIgnoreCase(sentimentLabel)) {
                    sentimentTag = "[NEGATIVE]";
                }

                transcriptText
                        .append(name)
                        .append(": ")
                        .append(text)
                        .append(" ")
                        .append(sentimentTag)
                        .append("\n\n");
            }

            txtTranscript.setText("🗣 Transcript\n\n" + transcriptText.toString());

        } catch (Exception e) {
            Log.e("UI", "Build failed", e);
        }
    }

    private String resolveSpeaker(String speakerLabel, JSONObject speakerMap) {

        String uid = speakerLabel;

        if (speakerMap != null && speakerMap.has(speakerLabel)) {
            try {
                uid = speakerMap.getString(speakerLabel);
            } catch (Exception ignored) {
            }
        }

        return uidToName.getOrDefault(uid, uid);
    }

    private String replaceSpeakerLabels(String text, JSONObject speakerMap) {

        if (text == null) return "";

        try {
            if (speakerMap != null) {

                Iterator<String> keys = speakerMap.keys();

                while (keys.hasNext()) {
                    String speaker = keys.next(); // SPEAKER_00
                    String uid = speakerMap.getString(speaker);

                    String name = uidToName.getOrDefault(uid, uid);

                    text = text.replace(speaker, name);
                }
            }
        } catch (Exception e) {
            Log.e("AI_MAP", "Mapping error", e);
        }

        return text;
    }

    // ---------------------------------------------------------
    // API fallback
    // ---------------------------------------------------------

    private void fetchResultFromApi() {

        new Thread(() -> {

            try {

                String url = "http://192.168.1.8:8000/analyze/result/" + jobId;

                String response = HttpUtils.get(url);

                JSONObject json = new JSONObject(response);

                if ("done".equals(json.getString("status"))) {

                    JSONObject result = json.getJSONObject("result");

                    JSONObject ui = result.optJSONObject("ui");

                    if (ui == null) {
                        runOnUiThread(() -> txtWinner.setText("Result structure error"));
                        return;
                    }

                    JSONObject summary = result.getJSONObject("summary");

                    String winnerUid = ui.optString("winnerFirebaseUid", null);

                    JSONArray scoreboardJson = ui.getJSONArray("scoreboard");

                    List<ScoreboardItem> items = new ArrayList<>();

                    for (int i = 0; i < scoreboardJson.length(); i++) {

                        JSONObject o = scoreboardJson.getJSONObject(i);

                        ScoreboardItem item = new ScoreboardItem();

                        item.firebaseUid = o.getString("firebaseUid");
                        item.score = o.getDouble("score");

                        items.add(item);
                    }

                    runOnUiThread(() -> updateUI(winnerUid, summary, items));
                }

            } catch (Exception e) {

                Log.e("CallResult", "API error", e);
            }

        }).start();
    }

    // ---------------------------------------------------------
    // Resolve speaker name
    // ---------------------------------------------------------

    private String resolveName(String uid) {

        for (ScoreboardItem item : scoreboardAdapter.getItems()) {

            if (uid.equals(item.firebaseUid)) {

                if (item.displayName != null)
                    return item.displayName;
            }
        }

        return uid;
    }
}