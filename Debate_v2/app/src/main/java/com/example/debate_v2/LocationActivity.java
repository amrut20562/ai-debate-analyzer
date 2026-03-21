package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationActivity extends AppCompatActivity {

    private LinearLayout locationKarad, locationSatara, locationPune, locationMumbai, locationSangali, locationKolhapur;
    private ImageView checkKarad, checkSatara, checkPune, checkMumbai, checkSangali, checkKolhapur;
    private CardView nextButton;
    private TextView skipButton;
    private CheckBox showLocationCheckbox;
    private ImageView backButton;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<String> selectedLocations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        locationKarad = findViewById(R.id.locationKarad);
        locationSatara = findViewById(R.id.locationSatara);
        locationPune = findViewById(R.id.locationPune);
        locationMumbai = findViewById(R.id.locationMumbai);
        locationSangali = findViewById(R.id.locationSangali);
        locationKolhapur = findViewById(R.id.locationKolhapur);

        checkKarad = findViewById(R.id.checkKarad);
        checkSatara = findViewById(R.id.checkSatara);
        checkPune = findViewById(R.id.checkPune);
        checkMumbai = findViewById(R.id.checkMumbai);
        checkSangali = findViewById(R.id.checkSangali);
        checkKolhapur = findViewById(R.id.checkKolhapur);

        nextButton = findViewById(R.id.nextButton);
        skipButton = findViewById(R.id.skipButton);
        showLocationCheckbox = findViewById(R.id.showLocationCheckbox);
        backButton = findViewById(R.id.backButton);

        // Location clicks
        locationKarad.setOnClickListener(v -> toggleLocation("Karad", checkKarad));
        locationSatara.setOnClickListener(v -> toggleLocation("Satara", checkSatara));
        locationPune.setOnClickListener(v -> toggleLocation("Pune", checkPune));
        locationMumbai.setOnClickListener(v -> toggleLocation("Mumbai", checkMumbai));
        locationSangali.setOnClickListener(v -> toggleLocation("Sangali", checkSangali));
        locationKolhapur.setOnClickListener(v -> toggleLocation("Kolhapur", checkKolhapur));

        // Next button click
        nextButton.setOnClickListener(v -> {
            if (!selectedLocations.isEmpty()) {
                saveToFirestore();
            } else {
                Toast.makeText(this, "Please select at least one location", Toast.LENGTH_SHORT).show();
            }
        });

        // Skip button click
        skipButton.setOnClickListener(v -> {
            Intent intent = new Intent(LocationActivity.this, InterestActivity.class);
            startActivity(intent);
        });

        // Back button click
        backButton.setOnClickListener(v -> finish());
    }

    private void toggleLocation(String location, ImageView checkIcon) {
        if (selectedLocations.contains(location)) {
            selectedLocations.remove(location);
            checkIcon.setVisibility(View.GONE);
        } else {
            selectedLocations.add(location);
            checkIcon.setVisibility(View.VISIBLE);
        }
    }

    private void saveToFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("locations", selectedLocations);
            data.put("showLocation", showLocationCheckbox.isChecked());

            db.collection("users").document(user.getUid())
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Intent intent = new Intent(LocationActivity.this, InterestActivity.class);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
