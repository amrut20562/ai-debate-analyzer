package com.example.debate_v2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class PostIdeaActivity extends AppCompatActivity implements OnMapReadyCallback {

    private EditText ownerNameInput, ideaTitleInput, ideaDescriptionInput, budgetInput;
    private Spinner categorySpinner;
    private TextView selectedLocationText;
    private CardView proceedToPaymentButton;
    private ImageView backButton;
    private GoogleMap mMap;
    private LatLng selectedLocation;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_idea);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        ownerNameInput = findViewById(R.id.ownerNameInput);
        ideaTitleInput = findViewById(R.id.ideaTitleInput);
        ideaDescriptionInput = findViewById(R.id.ideaDescriptionInput);
        budgetInput = findViewById(R.id.budgetInput);
        categorySpinner = findViewById(R.id.categorySpinner);
        selectedLocationText = findViewById(R.id.selectedLocationText);
        proceedToPaymentButton = findViewById(R.id.proceedToPaymentButton);
        backButton = findViewById(R.id.backButton);
        ownerNameInput.post(() ->
                ownerNameInput.startAnimation(
                        AnimationUtils.loadAnimation(this, R.anim.fade_slide_up)
                )
        );

        // Setup category spinner
        String[] categories = {"Technology", "Business", "Education", "Healthcare", "Entertainment", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        // Load user name
        loadUserName();

        // Setup map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Back button
        backButton.setOnClickListener(v -> finish());

        // Proceed to payment button
        proceedToPaymentButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });
        proceedToPaymentButton.setOnClickListener(v -> validateAndProceed());
    }

    private void loadUserName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (name != null && !name.isEmpty()) {
                                ownerNameInput.setText(name);
                            }
                        }
                    });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Default location (India center)
        LatLng defaultLocation = new LatLng(20.5937, 78.9629);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 5));

        // Enable location if permission granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            if (mMap != null &&
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                mMap.setMyLocationEnabled(true);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    101);
        }

        // Map click listener to select location
        mMap.setOnMapClickListener(latLng -> {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
            selectedLocation = latLng;
            selectedLocationText.setText(String.format("Selected: %.6f, %.6f",
                    latLng.latitude, latLng.longitude));
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
        }
    }

    private void validateAndProceed() {
        String ownerName = ownerNameInput.getText().toString().trim();
        String title = ideaTitleInput.getText().toString().trim();
        String description = ideaDescriptionInput.getText().toString().trim();
        String budgetStr = budgetInput.getText().toString().trim();
        String category = categorySpinner.getSelectedItem().toString();

        // Validate inputs
        if (TextUtils.isEmpty(ownerName)) {
            ownerNameInput.setError("Owner name is required");
            ownerNameInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(title)) {
            ideaTitleInput.setError("Title is required");
            ideaTitleInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(description)) {
            ideaDescriptionInput.setError("Description is required");
            ideaDescriptionInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(budgetStr)) {
            budgetInput.setError("Budget is required");
            budgetInput.requestFocus();
            return;
        }

        if (selectedLocation == null) {
            Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show();
            return;
        }

        double budget;
        try {
            budget = Double.parseDouble(budgetStr);
        } catch (Exception e) {
            budgetInput.setError("Invalid budget");
            return;
        }
        // Pass data to payment activity
        Intent intent = new Intent(PostIdeaActivity.this, PaymentActivity.class);
        intent.putExtra("ownerName", ownerName);
        intent.putExtra("title", title);
        intent.putExtra("description", description);
        intent.putExtra("budget", budget);
        intent.putExtra("category", category);
        intent.putExtra("latitude", selectedLocation.latitude);
        intent.putExtra("longitude", selectedLocation.longitude);
        startActivity(intent);
    }
}
