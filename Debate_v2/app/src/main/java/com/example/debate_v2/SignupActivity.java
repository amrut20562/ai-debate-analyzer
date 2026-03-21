package com.example.debate_v2;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignupActivity extends AppCompatActivity {

    private Button loginButton;
    private EditText emailInput, passwordInput, nameInput;
    private TextView signupLink, loginPrompt;
    private View nameCard;
    private View bottomPrompt;
    private FirebaseAuth mAuth;

    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // User is signed in, proceed to main activity
            proceedToMain();
            return; // Finish onCreate early
        }

        setContentView(R.layout.activity_signup);

        loginButton = findViewById(R.id.loginbtn);
        signupLink = findViewById(R.id.signupLink);
        loginPrompt = findViewById(R.id.loginPrompt);
        bottomPrompt = findViewById(R.id.bottomPrompt);
        nameInput = findViewById(R.id.nameInput);
        nameCard = findViewById(R.id.nameCard);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);

        setupModeUI();

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLoginMode) {
                    loginUser();
                } else {
                    registerUser();
                }
            }
        });

        signupLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMode();
            }
        });
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        setupModeUI();
    }

    private void setupModeUI() {
        if (isLoginMode) {
            // Login mode
            nameCard.setVisibility(View.GONE);
            loginButton.setText("Log In");
            signupLink.setText("Signup");
            loginPrompt.setText("Don't have an account? ");
            bottomPrompt.setVisibility(View.VISIBLE);
        } else {
            // Signup mode
            nameCard.setVisibility(View.VISIBLE);
            loginButton.setText("Sign Up");
            signupLink.setText("Log In");
            loginPrompt.setText("");
            bottomPrompt.setVisibility(View.GONE); // hide prompt for signup mode
        }
        emailInput.setText("");
        passwordInput.setText("");
        nameInput.setText("");
        emailInput.setError(null);
        passwordInput.setError(null);
        nameInput.setError(null);
    }

    private void loginUser() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required.");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required.");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            proceedToMain();
                        } else {
                            Toast.makeText(SignupActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void registerUser() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            nameInput.setError("Name is required.");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required.");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Intent intent = new Intent(SignupActivity.this, NameActivity.class);
                            intent.putExtra("NAME", name);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(SignupActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }



    private void proceedToMain() {
        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
