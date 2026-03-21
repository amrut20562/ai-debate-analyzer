package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Collections;

public class NameActivity extends AppCompatActivity {

    private EditText nameInput;
    private CardView nextButton;
    private ImageView backButton;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_name);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        nameInput = findViewById(R.id.nameInput);
        nextButton = findViewById(R.id.nextButton);
        backButton = findViewById(R.id.backButton);

        String Name = getIntent().getStringExtra("NAME");
        if (Name != null) {
            nameInput.setText(Name);
        }

        // Next button click
        nextButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (!name.isEmpty()) {
                saveToFirestore(name);
            } else {
                nameInput.setError("Name is required");
            }
        });

        // Back button click
        backButton.setOnClickListener(v -> finish());
    }

    private void saveToFirestore(String name) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .set(Collections.singletonMap("name", name), SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Intent intent = new Intent(NameActivity.this, BirthDateActivity.class);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        nameInput.setError("Failed to save: " + e.getMessage());
                    });
        }
    }
}
