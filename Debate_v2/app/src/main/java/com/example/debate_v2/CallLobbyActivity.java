package com.example.debate_v2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CallLobbyActivity extends AppCompatActivity {

    private static final int REQ_AUDIO = 1001;
    private String ideaId;
    private static final String TAG = "CallLobbyActivity";
    boolean isInitiator;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate started");

        Log.d("CALL_DEBUG", "CallLobbyActivity onCreate");
        setContentView(R.layout.activity_call_lobby);

        ideaId = getIntent().getStringExtra("IDEA_ID");
        isInitiator = getIntent().getBooleanExtra("IS_INITIATOR", false);


        TextView txt = findViewById(R.id.tvJoinPrompt);
        Button btnJoin = findViewById(R.id.btnJoinCall);
        Button btnCancel = findViewById(R.id.btnCancelCall);

        txt.setText("Join group call for idea:\n" + ideaId);

        btnJoin.setOnClickListener(v -> {
            if (hasAudioPermission()) {
                startCall();
            } else {
                requestAudioPermission();
            }
        });

        btnCancel.setOnClickListener(v -> finish());
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQ_AUDIO
        );
    }

    private void startCall() {
        String channelName = "idea_" + ideaId;

        Log.d("CALL_DEBUG", "Launching call with channel: " + channelName);

        Intent intent = new Intent(this, AgoraAudioCallActivity.class);
        intent.putExtra("CHANNEL_NAME", channelName);

        intent.putExtra("IS_INITIATOR", isInitiator);
        intent.putExtra("IDEA_ID", ideaId);
        startActivity(intent);

    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_AUDIO &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCall();
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
        }
    }
}
