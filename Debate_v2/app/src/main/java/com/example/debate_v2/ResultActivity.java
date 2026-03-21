package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    TextView ideaTitleText, winnerText, summaryText;
    Button leaderboardBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ideaTitleText = findViewById(R.id.ideaTitleResult);
        winnerText = findViewById(R.id.winnerText);
        summaryText = findViewById(R.id.summaryText);
        leaderboardBtn = findViewById(R.id.leaderboardBtn);

        String ideaTitle = getIntent().getStringExtra("ideaTitle");
        String winnerName = getIntent().getStringExtra("winnerName");
        //String summary = getIntent().getStringExtra("summary");
        String ideaId = getIntent().getStringExtra("ideaId");

        ideaTitleText.setText(ideaTitle);
        winnerText.setText("🏆 Winner: " + winnerName);
        //summaryText.setText(summary);
        String summaryRaw = getIntent().getStringExtra("summary");

        try {

            org.json.JSONObject summary = new org.json.JSONObject(summaryRaw);

            org.json.JSONObject metadata =
                    summary.getJSONObject("metadata");

            org.json.JSONObject rankings =
                    summary.getJSONObject("speaker_rankings");

            org.json.JSONObject sentiment =
                    summary.getJSONObject("debate_sentiment_distribution");
            org.json.JSONObject scores =
                    summary.getJSONObject("scores");

            org.json.JSONObject features =
                    summary.getJSONObject("features");

            int speakers = metadata.getInt("total_speakers");
            int messages = metadata.getInt("total_segments");

            double positive = sentiment.getDouble("positive_percent");
            double negative = sentiment.getDouble("negative_percent");
            double neutral = sentiment.getDouble("neutral_percent");

            String aggressiveUid = rankings.getString("most_aggressive_speaker");
            String constructiveUid = rankings.getString("most_constructive_speaker");
            String emotionalUid = rankings.getString("most_emotional_speaker");

            String aggressive = resolveUsername(aggressiveUid);
            String constructive = resolveUsername(constructiveUid);
            String emotional = resolveUsername(emotionalUid);

            StringBuilder performance = new StringBuilder();

            java.util.Iterator<String> keys = scores.keys();

            while (keys.hasNext()) {

                String uid = keys.next();

                String name = resolveUsername(uid);

                double score = scores.getDouble(uid);

                org.json.JSONObject f = features.getJSONObject(uid);

                int words = f.getInt("word_count");
                int sentences = f.getInt("sentence_count");
                int confidence = f.getInt("confidence_score");

                performance.append(name)
                        .append(" — Score: ").append(score)
                        .append(" | Words: ").append(words)
                        .append(" | Sentences: ").append(sentences)
                        .append("\n");
            }

            String formatted =
                    "📊 Debate Statistics\n\n" +
                            "Speakers: " + speakers + "\n" +
                            "Messages: " + messages + "\n\n" +

                            "🧠 Speaker Performance\n\n" +
                            performance.toString() + "\n" +

                            "🔥 Speaker Rankings\n\n" +
                            "Most Aggressive: " + aggressive + "\n" +
                            "Most Constructive: " + constructive + "\n" +
                            "Most Emotional: " + emotional + "\n\n" +

                            "🧠 Debate Sentiment\n\n" +
                            "Positive: " + positive + "%\n" +
                            "Negative: " + negative + "%\n" +
                            "Neutral: " + neutral + "%";

            summaryText.setText(formatted);

        } catch (Exception e) {

            summaryText.setText("Summary unavailable");

        }

        leaderboardBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, LeaderboardActivity.class);
            intent.putExtra("ideaId", ideaId);
            startActivity(intent);
        });
    }
    private String resolveUsername(String uid) {

        try {

            org.json.JSONArray participants =
                    new org.json.JSONArray(getIntent().getStringExtra("participants"));

            for (int i = 0; i < participants.length(); i++) {

                org.json.JSONObject p = participants.getJSONObject(i);

                if (p.getString("firebaseUid").equals(uid)) {
                    return p.getString("username");
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return uid;
    }
}
