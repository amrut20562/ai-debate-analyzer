package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SetupFinalActivity extends AppCompatActivity {

    private TextView userName, userLocation;
    private ImageView profilePic;
    private Button postIdeaButton, exploreButton;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setupfinal);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        userName = findViewById(R.id.userName);
        userLocation = findViewById(R.id.userLocation);
        profilePic = findViewById(R.id.profilePic);
        postIdeaButton = findViewById(R.id.postIdeaButton);
        exploreButton = findViewById(R.id.exploreButton);

        // Load user info from Firestore
        loadUserInfo();

        // Post Idea button
        postIdeaButton.setOnClickListener(v -> {
            Intent intent = new Intent(SetupFinalActivity.this, PostIdeaActivity.class);
            startActivity(intent);
        });

        // Explore button
        exploreButton.setOnClickListener(v -> {
            Intent intent = new Intent(SetupFinalActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void loadUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            Object locationsObj = documentSnapshot.get("locations");

                            if (name != null) {
                                userName.setText(name);
                            }

                            if (locationsObj != null) {
                                String locations = locationsObj.toString()
                                        .replace("[", "")
                                        .replace("]", "");
                                userLocation.setText(locations);
                            }
                        }
                    });
        }
    }
}
