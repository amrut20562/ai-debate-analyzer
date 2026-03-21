package com.example.debate_v2;



import static io.agora.rtc2.Constants.POSITION_MIXED;
import static io.agora.rtc2.Constants.POSITION_PLAYBACK;
import static io.agora.rtc2.Constants.POSITION_RECORD;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;

import io.agora.rtc2.audio.AudioParams;
import io.agora.rtc2.IAudioFrameObserver;
import io.agora.rtc2.IAudioFrameObserver.*;






public class AgoraAudioCallActivity extends AppCompatActivity {

    private static final String TAG = "CALL_DEBUG";

    private RtcEngine engine;
    private String channelName;
    private String ideaId;
    private boolean isInitiator;
    private boolean callStarted = false;
    private boolean isMuted = false;
    private boolean isSpeakerOn = true;
    private long callStartTime;
    private boolean callListenerInitialized = false;




    private ListenerRegistration callEndListener;

    // Recording
    private File mixedAudioFile;
    private FileOutputStream audioFos;
    private long totalPcmBytes = 0;
    private boolean isRecording = false;
    private boolean recordingFinalized = false;
    private int mixedFrameCount = 0;
    private boolean hasJoinedAgora = false;
    private boolean isCallEnding = false;





    // =====================================================
    // Activity lifecycle
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agora_audio_call);

        isInitiator = getIntent().getBooleanExtra("IS_INITIATOR", false);
        channelName = getIntent().getStringExtra("CHANNEL_NAME");
        ideaId = getIntent().getStringExtra("IDEA_ID");
        Button btnMute = findViewById(R.id.btnMute);
        Button btnSpeaker = findViewById(R.id.btnSpeaker);
        TextView txtTimer = findViewById(R.id.txtCallTimer);

        btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;

            if (engine != null) {
                engine.muteLocalAudioStream(isMuted);
            }

            btnMute.setText(isMuted ? "Unmute" : "Mute");
        });

        btnSpeaker.setOnClickListener(v -> {
            isSpeakerOn = !isSpeakerOn;

            if (engine != null) {
                engine.setEnableSpeakerphone(isSpeakerOn);
            }

            btnSpeaker.setText(isSpeakerOn ? "Speaker" : "Earpiece");
        });




        if (channelName == null || ideaId == null) {
            Log.e(TAG, "Missing intent extras");
            finish();
            return;
        }

        Log.d(TAG, "isInitiator = " + isInitiator);
        Log.d(TAG, "Channel = " + channelName);

        initAgora();
        setupCallEndListener();

        Button btnEnd = findViewById(R.id.btnEndCall);
        btnEnd.setOnClickListener(v -> {
            if (isInitiator) {
                endCallForEveryone();
            } else {
                leaveCall();
            }
        });
    }

    private void startCallTimer(TextView timerView) {
        callStartTime = System.currentTimeMillis();

        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        long elapsed =
                                (System.currentTimeMillis() - callStartTime) / 1000;

                        long min = elapsed / 60;
                        long sec = elapsed % 60;

                        timerView.setText(
                                String.format("%02d:%02d", min, sec)
                        );

                        timerView.postDelayed(this, 1000);
                    }
                }, 1000);
    }


    // =====================================================
    // Agora setup
    // =====================================================

    private void initAgora() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = "799aa778517e45d0b26ef50208610206";
            config.mEventHandler = rtcEventHandler;

            engine = RtcEngine.create(config);
            Log.d(TAG, "RtcEngine created");




            engine.setMixedAudioFrameParameters(48000,
                    1,
                    1024);
            engine.registerAudioFrameObserver(audioFrameObserver);

        } catch (Exception e) {
            throw new RuntimeException("Agora init failed", e);
        }

        engine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
        engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);

        engine.enableAudio();
        engine.enableLocalAudio(true);
        engine.muteLocalAudioStream(false);
        engine.muteAllRemoteAudioStreams(false);

        engine.setEnableSpeakerphone(true);
        engine.adjustRecordingSignalVolume(100);
        engine.adjustPlaybackSignalVolume(100);

        int uid = (int) (System.currentTimeMillis() % 100000);

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.publishMicrophoneTrack = true;
        options.autoSubscribeAudio = true;

        int result = engine.joinChannel(null, channelName, uid, options);
        Log.d(TAG, "joinChannel result = " + result);

        engine.muteAllRemoteAudioStreams(false);
    }

    // =====================================================
    // Recording (Agora mixed audio)
    // =====================================================

    private void startMixedAudioRecording() {
        try {
            mixedAudioFile = new File(
                    getExternalFilesDir(null),
                    "agora_call_" + System.currentTimeMillis() + ".wav"
            );

            audioFos = new FileOutputStream(mixedAudioFile);
            writeWavHeader(audioFos);

            isRecording = true;
            totalPcmBytes = 0;

            Log.d(TAG, "Recording started: " + mixedAudioFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Start recording failed", e);
        }
    }

    private void stopMixedAudioRecording() {
        if (!isRecording || recordingFinalized || audioFos == null) return;

        recordingFinalized = true;
        isRecording = false;

        try {
            updateWavHeader(mixedAudioFile, totalPcmBytes);
            audioFos.flush();
            audioFos.close();
            audioFos = null;


            Log.d(TAG, "Recording finalized: " + mixedAudioFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Stop recording failed", e);
        }
    }
    private void saveRecordingMetadataWithParticipants(Runnable onDone) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1️⃣ Fetch idea info
        db.collection("ideas")
                .document(ideaId)
                .get()
                .addOnSuccessListener(ideaDoc -> {

                    if (!ideaDoc.exists()) return;

                    String ideaTitle = ideaDoc.getString("title");
                    String ideaDescription = ideaDoc.getString("description");

                    // 2️⃣ Fetch ONLY users who joined THIS call
                    db.collection("ideas")
                            .document(ideaId)
                            .collection("groupCall")
                            .document("active")
                            .collection("participants")
                            .get()
                            .addOnSuccessListener(participantsSnapshot -> {

                                try {
                                    JSONObject metadata = new JSONObject();

                                    metadata.put("ideaId", ideaId);
                                    metadata.put("ideaTitle", ideaTitle);
                                    metadata.put("ideaDescription", ideaDescription);
                                    metadata.put("channel", channelName);
                                    metadata.put("initiator", isInitiator);
                                    metadata.put("sampleRate", 48000);
                                    metadata.put("channels", 1);

                                    // 3️⃣ Build participants list
                                    org.json.JSONArray participants = new org.json.JSONArray();

                                    for (var doc : participantsSnapshot.getDocuments()) {
                                        JSONObject p = new JSONObject();
                                        p.put("firebaseUid", doc.getString("firebaseUid"));
                                        p.put("agoraUid", doc.getLong("agoraUid"));
                                        p.put("joinedAt", doc.getLong("joinedAt"));

                                        participants.put(p);
                                    }

                                    metadata.put("participants", participants);

                                    // 4️⃣ Save metadata file
                                    File metaFile = new File(
                                            getExternalFilesDir(null),
                                            mixedAudioFile.getName().replace(".wav", ".json")
                                    );

                                    FileOutputStream fos = new FileOutputStream(metaFile);
                                    fos.write(metadata.toString(2).getBytes());
                                    fos.close();

                                    Log.d(TAG, "Metadata saved with call participants");

                                    if (onDone != null) onDone.run();

                                } catch (Exception e) {
                                    Log.e(TAG, "Metadata creation failed", e);
                                }
                            });
                });
    }



    private void writeWavHeader(FileOutputStream out) throws IOException {
        out.write(new byte[44]);
    }

    private void updateWavHeader(File file, long pcmBytes) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "rw");

        int sampleRate = 48000;
        int channels = 1;
        long byteRate = sampleRate * channels * 2;
        long totalDataLen = pcmBytes + 36;

        raf.seek(0);
        raf.writeBytes("RIFF");
        raf.writeInt(Integer.reverseBytes((int) totalDataLen));
        raf.writeBytes("WAVEfmt ");
        raf.writeInt(Integer.reverseBytes(16));
        raf.writeShort(Short.reverseBytes((short) 1));
        raf.writeShort(Short.reverseBytes((short) channels));
        raf.writeInt(Integer.reverseBytes(sampleRate));
        raf.writeInt(Integer.reverseBytes((int) byteRate));
        raf.writeShort(Short.reverseBytes((short) (channels * 2)));
        raf.writeShort(Short.reverseBytes((short) 16));
        raf.writeBytes("data");
        raf.writeInt(Integer.reverseBytes((int) pcmBytes));

        raf.close();
    }

    // =====================================================
    // Agora callbacks
    // =====================================================

    private final IRtcEngineEventHandler rtcEventHandler =
            new IRtcEngineEventHandler() {

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    Log.d(TAG, "Remote user joined: " + uid);

                    if (engine != null) {
                        engine.muteRemoteAudioStream(uid, false);
                    }
                }
                @Override
                public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
                    Log.d(TAG, "Joined channel: " + channel + ", agoraUid=" + uid);

                    engine.setEnableSpeakerphone(true);
                    Log.d(TAG, "Speakerphone enabled");



                    TextView txtTimer = findViewById(R.id.txtCallTimer);
                    runOnUiThread(() -> startCallTimer(txtTimer));


                    callStarted = true;
                    hasJoinedAgora = true;


                    // 🔥 SAVE Agora UID → Firebase UID mapping
                    String firebaseUid = FirebaseAuth.getInstance().getUid();

                    Map<String, Object> participant = new HashMap<>();
                    participant.put("firebaseUid", firebaseUid);
                    participant.put("agoraUid", uid);
                    participant.put("joinedAt", System.currentTimeMillis());

                    FirebaseFirestore.getInstance()
                            .collection("ideas")
                            .document(ideaId)
                            .collection("groupCall")
                            .document("active")
                            .collection("participants")
                            .document(String.valueOf(uid))
                            .set(participant);

                    if (isInitiator) {
                        startMixedAudioRecording();
                    }
                }

            };
    private final IAudioFrameObserver audioFrameObserver =
            new IAudioFrameObserver() {

                @Override
                public boolean onMixedAudioFrame(


                        String channelId,
                        int type,
                        int samplesPerChannel,
                        int bytesPerSample,
                        int channels,
                        int samplesPerSec,
                        ByteBuffer buffer,
                        long renderTimeMs,
                        int avsync_type
                ) {

                    if (++mixedFrameCount <= 2) {
                        return true; // 🔥 skip warm-up frames
                    }


                    if (!isRecording || audioFos == null) return true;

                    try {

                        ByteBuffer readOnly = buffer.asReadOnlyBuffer();
                        byte[] data = new byte[readOnly.remaining()];
                        readOnly.get(data);

                        audioFos.write(data);
                        totalPcmBytes += data.length;

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to write mixed audio", e);
                    }

                    return true; // DO NOT return false
                }

                // -------- REQUIRED but unused --------


                @Override
                public boolean onRecordAudioFrame(
                        String channelId, int type, int samplesPerChannel,
                        int bytesPerSample, int channels, int samplesPerSec,
                        ByteBuffer buffer, long renderTimeMs, int avsync_type) {

                    if (buffer != null && buffer.remaining() > 0) {
                        Log.d(TAG, "🎤 Mic frame captured, size=" + buffer.remaining());
                    } else {
                        Log.d(TAG, "⚠️ Mic frame EMPTY");
                    }

                    return true;
                }


                @Override
                public boolean onPlaybackAudioFrame(
                        String channelId, int type, int samplesPerChannel,
                        int bytesPerSample, int channels, int samplesPerSec,
                        ByteBuffer buffer, long renderTimeMs, int avsync_type) {

                    if (buffer != null && buffer.remaining() > 0) {
                        Log.d(TAG, "🔊 Playback frame received, size=" + buffer.remaining());
                    }
                    return true;
                }

                @Override
                public boolean onPlaybackAudioFrameBeforeMixing(
                        String channelId, int uid, int type, int samplesPerChannel,
                        int bytesPerSample, int channels, int samplesPerSec,
                        ByteBuffer buffer, long renderTimeMs,
                        int avsync_type, int rtpTimestamp) {
                    return true;
                }

                @Override
                public int getObservedAudioFramePosition() {
                    return POSITION_RECORD | POSITION_PLAYBACK | POSITION_MIXED;
                }


                @Override
                public AudioParams getRecordAudioParams() {
                    return null;
                }

                @Override
                public AudioParams getPlaybackAudioParams() {
                    return null;
                }

                @Override
                public AudioParams getMixedAudioParams() {
                    return null;
                }

                @Override
                public AudioParams getEarMonitoringAudioParams() {
                    return null;
                }

                @Override
                public boolean onEarMonitoringAudioFrame(
                        int type, int samplesPerChannel,
                        int bytesPerSample, int channels,
                        int samplesPerSec, ByteBuffer buffer,
                        long renderTimeMs, int avsync_type) {
                    return true;
                }
            };

    // =====================================================
    // Call ending logic
    // =====================================================

    private void setupCallEndListener() {
        callEndListener = FirebaseFirestore.getInstance()
                .collection("ideas")
                .document(ideaId)
                .collection("groupCall")
                .document("active")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) return;

                    // Ignore early snapshot
                    if (!hasJoinedAgora) {
                        Log.d(TAG, "Ignoring callEnd listener (not joined Agora yet)");
                        return;
                    }

                    if (!callListenerInitialized) {
                        callListenerInitialized = true;
                        return; // ignore first snapshot
                    }


                    // 🔥 ONLY react when call is actually deleted
                    if (snapshot == null || !snapshot.exists()) {

                        if (isCallEnding) return;
                        isCallEnding = true;

                        Log.d(TAG, "Call ended (backend)");

                        stopMixedAudioRecording();

                        if (engine != null) {
                            engine.leaveChannel();
                        }
                        finish();
                    }
                });
    }

    private void leaveCall() {
        stopMixedAudioRecording();
        if (engine != null) engine.leaveChannel();
        finish();
    }

    private void endCallForEveryone() {

        if (isCallEnding) return;
        isCallEnding = true;

        Log.d(TAG, "Initiator ending call");

        // Stop recording safely (idempotent)
        stopMixedAudioRecording();

        // Save metadata first, then upload, then cleanup
        saveRecordingMetadataWithParticipants(() -> {

            File metaFile = new File(
                    getExternalFilesDir(null),
                    mixedAudioFile.getName().replace(".wav", ".json")
            );

            double durationSeconds = totalPcmBytes / (48000.0 * 2);

            if (durationSeconds < 2.0) {
                Log.w(TAG, "Recording too short (" + durationSeconds + "s), skipping upload");
                cleanupAndFinish();
                return;
            }

            Log.d(TAG, "Uploading recording, duration=" + durationSeconds + "s");

            // 🔥 END CALL FIRST (never wait for upload)
            cleanupAndFinish();

            // 🔁 Upload in background (fire-and-forget)
            AudioUploader.uploadCall(
                    mixedAudioFile,
                    metaFile,
                    () -> Log.d(TAG, "Upload finished in background")
            );
        });
    }

    private void clearCallParticipants() {
        FirebaseFirestore.getInstance()
                .collection("ideas")
                .document(ideaId)
                .collection("groupCall")
                .document("active")
                .collection("participants")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (var doc : snapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                });
    }
    private void cleanupAndFinish() {

        Log.d(TAG, "cleanupAndFinish() CALLED");

        if (engine != null) {
            engine.leaveChannel();
        }

// 🔥 Delay Firestore delete slightly so leave propagates
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    clearCallParticipants();
                    FirebaseFirestore.getInstance()
                            .collection("ideas")
                            .document(ideaId)
                            .collection("groupCall")
                            .document("active")
                            .delete();
                    finish();
                }, 400); // 300–500ms is enough
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // DO NOT stop recording here — already finalized safely
        if (engine != null) {
            engine.leaveChannel();
            RtcEngine.destroy();
            engine = null;
        }
        if (callEndListener != null) {
            callEndListener.remove();
            callEndListener = null;
        }
    }
}
