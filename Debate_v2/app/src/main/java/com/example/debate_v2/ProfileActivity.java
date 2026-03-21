package com.example.debate_v2;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
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

public class ProfileActivity extends AppCompatActivity {

    private ImageView backButton, editButton, changeAvatarButton;
    private TextView profileName, profileUsername, profileEmail, profileGender, profileBirthDate, profileInterest, profileLocations;
    private EditText profileNameEdit, profileEmailEdit, profileBirthDateEdit;
    private LinearLayout genderOptionsLayout, interestOptionsLayout, locationsOptionsLayout;
    private CardView womanOption, manOption, othersOption;
    private CardView grabInfoOption, earningOption;
    private ImageView womanCheck, manCheck, othersCheck, grabInfoCheck, earningCheck;
    private CheckBox satara, karad, kolhapur, sangli, pune, mumbai;
    private CardView saveButton;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private boolean isEditMode = false;
    private String selectedGender = "";
    private String selectedInterest = "";
    private List<String> selectedLocations = new ArrayList<>();
    private static final int MAX_LOCATIONS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        backButton = findViewById(R.id.backButton);
        editButton = findViewById(R.id.editButton);
        changeAvatarButton = findViewById(R.id.changeAvatarButton);

        profileName = findViewById(R.id.profileName);
        profileUsername = findViewById(R.id.profileUsername);
        profileEmail = findViewById(R.id.profileEmail);
        profileGender = findViewById(R.id.profileGender);
        profileBirthDate = findViewById(R.id.profileBirthDate);
        profileInterest = findViewById(R.id.profileInterest);
        profileLocations = findViewById(R.id.profileLocations);

        profileNameEdit = findViewById(R.id.profileNameEdit);
        profileEmailEdit = findViewById(R.id.profileEmailEdit);
        profileBirthDateEdit = findViewById(R.id.profileBirthDateEdit);

        genderOptionsLayout = findViewById(R.id.genderOptionsLayout);
        interestOptionsLayout = findViewById(R.id.interestOptionsLayout);
        locationsOptionsLayout = findViewById(R.id.locationsOptionsLayout);

        // Gender options
        womanOption = findViewById(R.id.womanOption);
        manOption = findViewById(R.id.manOption);
        othersOption = findViewById(R.id.othersOption);
        womanCheck = findViewById(R.id.womanCheck);
        manCheck = findViewById(R.id.manCheck);
        othersCheck = findViewById(R.id.othersCheck);

        // Interest options
        grabInfoOption = findViewById(R.id.grabInfoOption);
        earningOption = findViewById(R.id.earningOption);
        grabInfoCheck = findViewById(R.id.grabInfoCheck);
        earningCheck = findViewById(R.id.earningCheck);

        // Location checkboxes
        satara = findViewById(R.id.checkSatara);
        karad = findViewById(R.id.checkKarad);
        kolhapur = findViewById(R.id.checkKolhapur);
        sangli = findViewById(R.id.checkSangli);
        pune = findViewById(R.id.checkPune);
        mumbai = findViewById(R.id.checkMumbai);

        saveButton = findViewById(R.id.saveButton);

        // Setup gender option clicks
        womanOption.setOnClickListener(v -> selectGender("Woman", womanOption, womanCheck));
        manOption.setOnClickListener(v -> selectGender("Man", manOption, manCheck));
        othersOption.setOnClickListener(v -> selectGender("Others", othersOption, othersCheck));

        // Setup interest option clicks
        grabInfoOption.setOnClickListener(v -> selectInterest("Grab information", grabInfoOption, grabInfoCheck));
        earningOption.setOnClickListener(v -> selectInterest("Earning", earningOption, earningCheck));

        // Setup location checkbox listeners
        setupLocationCheckbox(satara, "Satara");
        setupLocationCheckbox(karad, "Karad");
        setupLocationCheckbox(kolhapur, "Kolhapur");
        setupLocationCheckbox(sangli, "Sangli");
        setupLocationCheckbox(pune, "Pune");
        setupLocationCheckbox(mumbai, "Mumbai");

        // Load user data
        loadUserProfile();

        // Back button
        backButton.setOnClickListener(v -> finish());

        // Edit button
        editButton.setOnClickListener(v -> toggleEditMode());

        // Save button
        saveButton.setOnClickListener(v -> saveChanges());
    }

    private void setupLocationCheckbox(CheckBox checkBox, String location) {
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // If trying to check and already have 3 locations
                if (selectedLocations.size() >= MAX_LOCATIONS) {
                    // Don't allow checking
                    buttonView.setChecked(false);
                    Toast.makeText(this, "Maximum 3 locations allowed. Uncheck one to select another.", Toast.LENGTH_SHORT).show();
                } else {
                    // Add location to list
                    selectedLocations.add(location);
                }
            } else {
                // Always allow unchecking
                selectedLocations.remove(location);
            }
        });
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            profileEmail.setText(currentUser.getEmail());

            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            String username = documentSnapshot.getString("username");
                            String gender = documentSnapshot.getString("gender");
                            String birthDate = documentSnapshot.getString("birthDate");
                            String interest = documentSnapshot.getString("interest");
                            Object locationsObj = documentSnapshot.get("locations");

                            if (name != null) profileName.setText(name);
                            if (username != null) profileUsername.setText("@" + username);
                            else if (name != null) profileUsername.setText("@" + name);

                            if (gender != null) {
                                profileGender.setText(gender);
                                selectedGender = gender;
                            }

                            if (birthDate != null) profileBirthDate.setText(birthDate);

                            if (interest != null) {
                                profileInterest.setText(interest);
                                selectedInterest = interest;
                            }

                            if (locationsObj != null) {
                                if (locationsObj instanceof List) {
                                    List<String> locations = (List<String>) locationsObj;
                                    selectedLocations.clear();
                                    selectedLocations.addAll(locations);
                                    profileLocations.setText(String.join(", ", locations));
                                } else {
                                    String locationsStr = locationsObj.toString()
                                            .replace("[", "")
                                            .replace("]", "");
                                    profileLocations.setText(locationsStr);
                                }
                            }
                        }
                    });
        }
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;

        if (isEditMode) {
            // Enter edit mode
            changeAvatarButton.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.VISIBLE);
            editButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);

            // Name
            profileName.setVisibility(View.GONE);
            profileNameEdit.setVisibility(View.VISIBLE);
            profileNameEdit.setText(profileName.getText().toString());

            // Email
            profileEmail.setVisibility(View.GONE);
            profileEmailEdit.setVisibility(View.VISIBLE);
            profileEmailEdit.setText(profileEmail.getText().toString());

            // Birth Date
            profileBirthDate.setVisibility(View.GONE);
            profileBirthDateEdit.setVisibility(View.VISIBLE);
            profileBirthDateEdit.setText(profileBirthDate.getText().toString());

            // Gender
            profileGender.setVisibility(View.GONE);
            genderOptionsLayout.setVisibility(View.VISIBLE);
            preselectGender(selectedGender);

            // Interest
            profileInterest.setVisibility(View.GONE);
            interestOptionsLayout.setVisibility(View.VISIBLE);
            preselectInterest(selectedInterest);

            // Locations - Keep previous selections and allow editing
            profileLocations.setVisibility(View.GONE);
            locationsOptionsLayout.setVisibility(View.VISIBLE);
            preselectLocations(selectedLocations);
            Toast.makeText(this, "Modify your locations (max 3)", Toast.LENGTH_SHORT).show();

        } else {
            // Exit edit mode
            changeAvatarButton.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            editButton.setImageResource(android.R.drawable.ic_menu_edit);

            // Name
            profileName.setVisibility(View.VISIBLE);
            profileNameEdit.setVisibility(View.GONE);

            // Email
            profileEmail.setVisibility(View.VISIBLE);
            profileEmailEdit.setVisibility(View.GONE);

            // Birth Date
            profileBirthDate.setVisibility(View.VISIBLE);
            profileBirthDateEdit.setVisibility(View.GONE);

            // Gender
            profileGender.setVisibility(View.VISIBLE);
            genderOptionsLayout.setVisibility(View.GONE);

            // Interest
            profileInterest.setVisibility(View.VISIBLE);
            interestOptionsLayout.setVisibility(View.GONE);

            // Locations
            profileLocations.setVisibility(View.VISIBLE);
            locationsOptionsLayout.setVisibility(View.GONE);
        }
    }

    private void selectGender(String gender, CardView selectedCard, ImageView selectedCheck) {
        selectedGender = gender;

        // Reset all
        resetGenderOption(womanOption, womanCheck);
        resetGenderOption(manOption, manCheck);
        resetGenderOption(othersOption, othersCheck);

        // Highlight selected
        highlightOption(selectedCard, selectedCheck);
    }

    private void selectInterest(String interest, CardView selectedCard, ImageView selectedCheck) {
        selectedInterest = interest;

        // Reset all
        resetInterestOption(grabInfoOption, grabInfoCheck);
        resetInterestOption(earningOption, earningCheck);

        // Highlight selected
        highlightOption(selectedCard, selectedCheck);
    }

    private void resetGenderOption(CardView card, ImageView check) {
        card.setCardBackgroundColor(Color.WHITE);
        TextView textView = findTextViewRecursive(card);
        if (textView != null) {
            textView.setBackgroundColor(Color.WHITE);
            textView.setTextColor(Color.parseColor("#1F2121"));
        }
        check.setVisibility(View.GONE);
    }

    private void resetInterestOption(CardView card, ImageView check) {
        card.setCardBackgroundColor(Color.WHITE);
        TextView textView = findTextViewRecursive(card);
        if (textView != null) {
            textView.setBackgroundColor(Color.WHITE);
            textView.setTextColor(Color.parseColor("#1F2121"));
        }
        check.setVisibility(View.GONE);
    }

    private void highlightOption(CardView card, ImageView check) {
        card.setCardBackgroundColor(Color.parseColor("#A020F0"));
        TextView textView = findTextViewRecursive(card);
        if (textView != null) {
            textView.setBackgroundColor(Color.parseColor("#A020F0"));
            textView.setTextColor(Color.WHITE);
        }
        check.setVisibility(View.VISIBLE);
    }

    private void preselectGender(String gender) {
        if (gender == null) return;

        switch (gender) {
            case "Woman":
                selectGender("Woman", womanOption, womanCheck);
                break;
            case "Man":
                selectGender("Man", manOption, manCheck);
                break;
            case "Others":
                selectGender("Others", othersOption, othersCheck);
                break;
        }
    }

    private void preselectInterest(String interest) {
        if (interest == null) return;

        switch (interest) {
            case "Grab information":
                selectInterest("Grab information", grabInfoOption, grabInfoCheck);
                break;
            case "Earning":
                selectInterest("Earning", earningOption, earningCheck);
                break;
        }
    }

    private void preselectLocations(List<String> locations) {
        // Clear all checkboxes first
        satara.setChecked(false);
        karad.setChecked(false);
        kolhapur.setChecked(false);
        sangli.setChecked(false);
        pune.setChecked(false);
        mumbai.setChecked(false);

        // Clear the selected list and rebuild it
        selectedLocations.clear();

        // Check selected ones
        for (String location : locations) {
            selectedLocations.add(location); // Add back to the list
            switch (location) {
                case "Satara":
                    satara.setChecked(true);
                    break;
                case "Karad":
                    karad.setChecked(true);
                    break;
                case "Kolhapur":
                    kolhapur.setChecked(true);
                    break;
                case "Sangli":
                    sangli.setChecked(true);
                    break;
                case "Pune":
                    pune.setChecked(true);
                    break;
                case "Mumbai":
                    mumbai.setChecked(true);
                    break;
            }
        }
    }

    private TextView findTextViewRecursive(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = findTextViewRecursive(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private void saveChanges() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String name = profileNameEdit.getText().toString().trim();
            String email = profileEmailEdit.getText().toString().trim();
            String birthDate = profileBirthDateEdit.getText().toString().trim();

            if (name.isEmpty()) {
                profileNameEdit.setError("Name is required");
                return;
            }

            if (selectedGender.isEmpty()) {
                Toast.makeText(this, "Please select a gender", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedInterest.isEmpty()) {
                Toast.makeText(this, "Please select an interest", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedLocations.isEmpty()) {
                Toast.makeText(this, "Please select at least one location", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("gender", selectedGender);
            updates.put("birthDate", birthDate);
            updates.put("interest", selectedInterest);
            updates.put("locations", selectedLocations);

            db.collection("users").document(currentUser.getUid())
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                        toggleEditMode();
                        loadUserProfile();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update profile: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
