package com.example.debate_v2;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
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

public class GenderActivity extends AppCompatActivity {

    private static final String TAG = "GenderActivity";

    private CardView womanOption, manOption, othersOption, nextButton;
    private CheckBox showGenderCheckbox;
    private ImageView backButton;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String selectedGender = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gender);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        womanOption = findViewById(R.id.womanOption);
        manOption = findViewById(R.id.manOption);
        othersOption = findViewById(R.id.othersOption);
        nextButton = findViewById(R.id.nextButton);
        showGenderCheckbox = findViewById(R.id.showGenderCheckbox);
        backButton = findViewById(R.id.backButton);

        womanOption.setOnClickListener(v -> selectGender("Woman", womanOption));
        manOption.setOnClickListener(v -> selectGender("Man", manOption));
        othersOption.setOnClickListener(v -> selectGender("Others", othersOption));

        nextButton.setOnClickListener(v -> {
            if (!selectedGender.isEmpty()) {
                saveToFirestore();
            } else {
                Toast.makeText(this, "Please select a gender", Toast.LENGTH_SHORT).show();
            }
        });

        backButton.setOnClickListener(v -> finish());
    }

    private void selectGender(String gender, CardView selectedCard) {
        selectedGender = gender;

        // Reset all cards
        resetCard(womanOption);
        resetCard(manOption);
        resetCard(othersOption);

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
        if (user != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("gender", selectedGender);
            data.put("showGender", showGenderCheckbox.isChecked());

            db.collection("users").document(user.getUid())
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Intent intent = new Intent(GenderActivity.this, LocationActivity.class);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
