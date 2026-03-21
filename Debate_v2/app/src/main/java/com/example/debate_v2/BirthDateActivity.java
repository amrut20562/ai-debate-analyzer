package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Collections;

public class BirthDateActivity extends AppCompatActivity {

    private EditText dayInput, monthInput, yearInput;
    private CardView keyNext;
    private ImageView backButton;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_birth_date);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        dayInput = findViewById(R.id.dayInput);
        monthInput = findViewById(R.id.monthInput);
        yearInput = findViewById(R.id.yearInput);
        keyNext = findViewById(R.id.keyNext);
        backButton = findViewById(R.id.backButton);

        // Next button click
        keyNext.setOnClickListener(v -> {
            String day = dayInput.getText().toString().trim();
            String month = monthInput.getText().toString().trim();
            String year = yearInput.getText().toString().trim();

            if (day.isEmpty() || month.isEmpty() || year.isEmpty()) {
                Toast.makeText(this, "Please enter complete date", Toast.LENGTH_SHORT).show();
                return;
            }

            String birthDate = day + "/" + month + "/" + year;
            saveToFirestore(birthDate);
        });

        // Back button click
        backButton.setOnClickListener(v -> finish());
    }

    private void saveToFirestore(String birthDate) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .set(Collections.singletonMap("birthDate", birthDate), SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Intent intent = new Intent(BirthDateActivity.this, GenderActivity.class);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
