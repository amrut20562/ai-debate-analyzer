package com.example.debate_v2;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class InterestActivity extends AppCompatActivity {

    private static final String TAG = "InterestActivity";

    private CardView grabInformationOption, earningOption, nextButton;
    private ImageView backButton;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String selectedInterest = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interest);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        grabInformationOption = findViewById(R.id.grabInformationOption);
        earningOption = findViewById(R.id.earningOption);
        nextButton = findViewById(R.id.nextButton);
        backButton = findViewById(R.id.backButton);

        grabInformationOption.setOnClickListener(v -> selectInterest("Grab information", grabInformationOption));
        earningOption.setOnClickListener(v -> selectInterest("Earning", earningOption));

        nextButton.setOnClickListener(v -> {
            if (!selectedInterest.isEmpty()) {
                saveToFirestore();
            } else {
                Toast.makeText(this, "Please select an interest", Toast.LENGTH_SHORT).show();
            }
        });

        backButton.setOnClickListener(v -> finish());
    }

    private void selectInterest(String interest, CardView selectedCard) {
        selectedInterest = interest;

        // Reset all cards
        resetCard(grabInformationOption);
        resetCard(earningOption);

        // Highlight selected card
        highlightCard(selectedCard);

        // Enable next button
        enableNextButton();
    }

    private void resetCard(CardView card) {
        // Reset card background
        card.setCardBackgroundColor(Color.WHITE);

        // Find TextView and reset its background and text color
        TextView textView = findTextViewRecursive(card);
        if (textView != null) {
            textView.setBackgroundColor(Color.WHITE);
            textView.setTextColor(Color.parseColor("#1F2121"));
        }
    }

    private void highlightCard(CardView card) {
        // Set card background to purple
        card.setCardBackgroundColor(Color.parseColor("#A020F0"));

        // Find TextView and set its background and text color
        TextView textView = findTextViewRecursive(card);
        if (textView != null) {
            textView.setBackgroundColor(Color.parseColor("#A020F0"));
            textView.setTextColor(Color.WHITE);
        }
    }

    private void enableNextButton() {
        // Set next button background to purple
        nextButton.setCardBackgroundColor(Color.parseColor("#A020F0"));

        // Find the LinearLayout inside and set its background
        View child = nextButton.getChildAt(0);
        if (child != null) {
            child.setBackgroundColor(Color.parseColor("#A020F0"));
        }

        // Find all TextViews and ImageViews and set their colors
        setAllTextAndIconColors(nextButton, Color.WHITE);
    }

    private void setAllTextAndIconColors(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setAllTextAndIconColors(group.getChildAt(i), color);
            }
        }
    }

    private TextView findTextViewRecursive(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        }

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = findTextViewRecursive(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private void saveToFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("interest", selectedInterest);
        data.put("profileCompleted", true);
        data.put("email", user.getEmail());

        Log.d(TAG, "Saving final profile data for UID: " + user.getUid());

        db.collection("users").document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile completed successfully!");
                    Toast.makeText(this, "Profile setup complete!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(InterestActivity.this, SetupFinalActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error completing profile: " + e.getMessage());
                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
