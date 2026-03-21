package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    private TextView summaryTitle, summaryBudget, paymentAmount;
    private RadioGroup paymentMethodGroup;
    private CardView payButton;
    private ImageView backButton;

    private String ownerName, title, description, category;
    private double budget, latitude, longitude;
    private static final double POSTING_FEE = 99.0;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Get data from intent
        Intent intent = getIntent();
        ownerName = intent.getStringExtra("ownerName");
        title = intent.getStringExtra("title");
        description = intent.getStringExtra("description");
        budget = intent.getDoubleExtra("budget", 0);
        category = intent.getStringExtra("category");
        latitude = intent.getDoubleExtra("latitude", 0);
        longitude = intent.getDoubleExtra("longitude", 0);

        // Initialize views
        summaryTitle = findViewById(R.id.summaryTitle);
        summaryBudget = findViewById(R.id.summaryBudget);
        paymentAmount = findViewById(R.id.paymentAmount);
        paymentMethodGroup = findViewById(R.id.paymentMethodGroup);
        payButton = findViewById(R.id.payButton);
        backButton = findViewById(R.id.backButton);

        // Set summary data
        summaryTitle.setText(title);
        summaryBudget.setText("₹" + String.format("%.2f", budget));
        paymentAmount.setText("₹" + String.format("%.2f", POSTING_FEE));

        // Back button
        backButton.setOnClickListener(v -> finish());

        // Pay button
        payButton.setOnClickListener(v -> processPayment());
    }

    private void processPayment() {
        // Get selected payment method
        int selectedId = paymentMethodGroup.getCheckedRadioButtonId();
        String paymentMethod = "UPI"; // default

        if (selectedId == R.id.cardOption) {
            paymentMethod = "Card";
        } else if (selectedId == R.id.netBankingOption) {
            paymentMethod = "Net Banking";
        }

        // Here you would integrate with a payment gateway like Razorpay, Paytm, etc.
        // For now, we'll simulate a successful payment
        simulatePaymentSuccess(paymentMethod);
    }

    private void simulatePaymentSuccess(String paymentMethod) {
        Toast.makeText(this, "Processing payment...", Toast.LENGTH_SHORT).show();

        // Simulate payment delay
        new android.os.Handler().postDelayed(() -> {
            // Payment successful - save idea to Firestore
            saveIdeaToFirestore(paymentMethod);
        }, 2000);
    }

    private void saveIdeaToFirestore(String paymentMethod) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create idea data
        Map<String, Object> ideaData = new HashMap<>();
        ideaData.put("ownerId", currentUser.getUid());
        ideaData.put("ownerName", ownerName);
        ideaData.put("clientName", ownerName); // For display in popup
        ideaData.put("title", title);
        ideaData.put("description", description);
        ideaData.put("budget", budget);
        ideaData.put("category", category);
        ideaData.put("latitude", latitude);
        ideaData.put("longitude", longitude);
        ideaData.put("createdAt", System.currentTimeMillis());
        ideaData.put("joinedCount", 0);
        ideaData.put("onlineCount", 0);
        ideaData.put("status", "active");
        ideaData.put("paymentMethod", paymentMethod);
        ideaData.put("postingFee", POSTING_FEE);

        // Save to Firestore
        db.collection("ideas")
                .add(ideaData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Idea posted successfully!", Toast.LENGTH_LONG).show();

                    // Navigate to MainActivity
                    Intent intent = new Intent(PaymentActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to post idea: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
