package com.example.debate_v2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.messaging.FirebaseMessaging;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 200;
    public static final String FLASK_BASE_URL = "http://192.168.1.7:8000";


    // Required permissions
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA

    };

    // UI Components
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView menuButton;

    // Map
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Map<Marker, String> markerIdeaMap;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration callListener;
    private boolean isListeningForCalls = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        BlurView blurView = findViewById(R.id.blurDrawer);

        View decorView = getWindow().getDecorView();
        ViewGroup rootView = (ViewGroup) decorView.findViewById(android.R.id.content);

        if (blurView != null) {
            blurView.setupWith(rootView, new RenderScriptBlur(this))
                    .setFrameClearDrawable(decorView.getBackground())
                    .setBlurRadius(25f);
        }

        Log.d(TAG, "onCreate: MainActivity started");

        // Initialize Firebase App Check
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());

        // Request notification permission for Android 13+
        requestNotificationPermission();

        // Generate FCM token
        generateFCMToken();

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize map marker storage
        markerIdeaMap = new HashMap<>();

        // Initialize views
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        menuButton = findViewById(R.id.menuButton);

        // Elastic Drawer + Content Animation + Depth
        setupDrawerAnimations(blurView);

        // Request permissions if needed
        if (!hasAllPermissions()) {
            Log.d(TAG, "onCreate: Requesting permissions");
            requestPermissions();
        } else {
            Log.d(TAG, "onCreate: All permissions granted, initializing features");
            initializeFeatures();
        }

        // Setup navigation drawer
        setupNavigationDrawer();

        // Menu button click
        menuButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Load user data in nav header
        loadUserData();

        // START INCOMING CALL LISTENER
        startCallListener();
    }

    private void setupDrawerAnimations(BlurView blurView) {
        View content = findViewById(R.id.mapFragment);
        if (content != null) {
            content.setCameraDistance(8000); // 🌍 Map Parallax + Depth illusion
        }
        
        drawerLayout.setDrawerElevation(20f); // 💥 Physics feel

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                // 🌊 Elastic interpolation
                float elastic = (float) (1 - Math.pow(1 - slideOffset, 3));

                // 1. Blur intensity (Clamped safely)
                if (blurView != null) {
                    float blur = 5f + (20f * elastic);
                    blur = Math.max(1f, Math.min(25f, blur));
                    blurView.setBlurRadius(blur);
                    
                    // Smooth alpha
                    blurView.setAlpha(0.6f + (0.4f * elastic));
                }

                // 2. Content Animation (Scale + Translation + Rotation)
                if (content != null) {
                    float scale = 1f - (0.1f * elastic);
                    content.setScaleX(scale);
                    content.setScaleY(scale);

                    content.setTranslationX(220 * elastic);
                    content.setRotationY(-5f * elastic);
                }
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                drawerView.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(120)
                        .withEndAction(() ->
                                drawerView.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        );
            }
        });
    }

    /**
     * Check if all required permissions are granted
     */
    private boolean hasAllPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "hasAllPermissions: Missing permission - " + permission);
                return false;
            }
        }
        Log.d(TAG, "hasAllPermissions: All permissions granted");
        return true;
    }

    /**
     * Request required permissions from user
     */
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "requestPermissions: Requesting permissions");
            ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    /**
     * Handle permission request results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "onRequestPermissionsResult: All permissions granted");
                initializeFeatures();
            } else {
                Log.e(TAG, "onRequestPermissionsResult: Some permissions denied");
                Toast.makeText(this, "Some permissions were denied. Some features may be limited.",
                        Toast.LENGTH_SHORT).show();
                // Continue anyway with limited features
                initializeFeatures();
            }
        }
    }

    /**
     * Initialize features after permissions are verified
     */
    private void initializeFeatures() {
        Log.d(TAG, "initializeFeatures: Initializing main app features");

        // Setup map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    /**
     * Setup navigation drawer
     */
    private void setupNavigationDrawer() {
        navigationView.setItemIconTintList(null);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            // Neon Glow Pulse (Selected Item)
            View itemView = navigationView.findViewById(id);
            if (itemView != null) {
                Animation pulse = AnimationUtils.loadAnimation(this, R.anim.glow_pulse);
                itemView.startAnimation(pulse);
            }

            if (id == R.id.nav_profile) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            }
            else if (id == R.id.nav_chats) {
                startActivity(new Intent(MainActivity.this, ChatListActivity.class));
            } else if (id == R.id.nav_settings) {
                Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_postidea) {  // CORRECTED - was nav_post_idea
                startActivity(new Intent(MainActivity.this, PostIdeaActivity.class));
            } else if (id == R.id.nav_joined_ideas) {
                startActivity(new Intent(MainActivity.this, JoinedIdeasActivity.class));
            } else if (id == R.id.nav_logout) {
                mAuth.signOut();
                startActivity(new Intent(MainActivity.this, SignupActivity.class));
                finish();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }



    /**
     * Request notification permission for Android 13+
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    /**
     * Generate and save FCM token
     */
    private void generateFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Failed to get FCM token", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);

                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        Map<String, Object> tokenData = new HashMap<>();
                        tokenData.put("fcmToken", token);
                        tokenData.put("updatedAt", System.currentTimeMillis());

                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(currentUser.getUid())
                                .set(tokenData, SetOptions.merge())
                                .addOnSuccessListener(aVoid ->
                                        Log.d(TAG, "Token saved"));
                    }
                });
    }

    /**
     * Load user data in navigation header
     */
    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            View headerView = navigationView.getHeaderView(0);
            TextView navHeaderName = headerView.findViewById(R.id.navHeaderName);
            TextView navHeaderEmail = headerView.findViewById(R.id.navHeaderEmail);
            ImageView navHeaderAvatar = headerView.findViewById(R.id.navHeaderAvatar);
            ShimmerFrameLayout shimmer = headerView.findViewById(R.id.shimmerLayout);

            if (navHeaderEmail != null) navHeaderEmail.setText(currentUser.getEmail());
            
            db.collection("users")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            String profileUrl = documentSnapshot.getString("profileImageUrl");

                            if (name != null && !name.isEmpty() && navHeaderName != null) {
                                navHeaderName.setText(name);
                            }

                            if (profileUrl != null && navHeaderAvatar != null && shimmer != null) {
                                shimmer.startShimmer();
                                Glide.with(this)
                                        .load(profileUrl)
                                        .circleCrop()
                                        .into(new CustomTarget<Drawable>() {
                                            @Override
                                            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                                shimmer.stopShimmer();
                                                shimmer.setShimmer(null); // Optional: clear shimmer to save resources
                                                navHeaderAvatar.setImageDrawable(resource);
                                            }

                                            @Override
                                            public void onLoadCleared(@Nullable Drawable placeholder) {}
                                        });
                            }
                        }
                    });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        Log.d(TAG, "onMapReady: Map initialized");

        // Request location permission
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        enableMyLocation();
        loadIdeasFromFirestore();
    }

    /**
     * Enable my location on map
     */
    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);

            // Move camera to user's location
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng userLocation = new LatLng(
                                    location.getLatitude(),
                                    location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 13));
                        }
                    });
        }
    }

    private BitmapDescriptor bitmapFromVector(int resId) {
        Drawable drawable = ContextCompat.getDrawable(this, resId);

        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 120;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 120;

        drawable.setBounds(0, 0, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void animateMarkerDrop(Marker marker) {
        Handler handler = new Handler();
        long start = System.currentTimeMillis();
        long duration = 600;

        Interpolator interpolator = new BounceInterpolator();

        handler.post(new Runnable() {
            @Override
            public void run() {
                float elapsed = (float) (System.currentTimeMillis() - start) / duration;
                float t = Math.max(1 - interpolator.getInterpolation(elapsed), 0);

                marker.setAnchor(0.5f, 1.0f + t);

                if (t > 0.0) {
                    handler.postDelayed(this, 16);
                }
            }
        });
    }

    private void animateMarkerPulse(Marker marker) {
        ValueAnimator animator = ValueAnimator.ofFloat(1f, 1.2f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);

        animator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            marker.setAnchor(0.5f, 0.5f);
            marker.setAlpha(scale);
        });

        animator.start();
    }

    /**
     * Load ideas from Firestore and display on map
     */
    private void loadIdeasFromFirestore() {
        db.collection("ideas")
                .whereEqualTo("status", "active")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading ideas", error);
                        return;
                    }

                    // Clear existing markers
                    if (mMap != null) {
                        mMap.clear();
                        markerIdeaMap.clear();

                        if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                String ideaId = document.getId();
                                String title = document.getString("title");
                                Double latitude = document.getDouble("latitude");
                                Double longitude = document.getDouble("longitude");

                                if (latitude != null && longitude != null) {
                                    LatLng position = new LatLng(latitude, longitude);

                                    Marker marker = mMap.addMarker(new MarkerOptions()
                                            .position(position)
                                            .title(title)
                                            .anchor(0.5f, 0.8f)
                                            .icon(bitmapFromVector(R.drawable.clay_marker)));

                                    if (marker != null) {
                                        markerIdeaMap.put(marker, ideaId);
                                        animateMarkerDrop(marker);
                                        animateMarkerPulse(marker); // 📍 Live Marker Pulse
                                    }
                                }
                            }

                            // Set marker click listener
                            mMap.setOnMarkerClickListener(marker -> {
                                String ideaId = markerIdeaMap.get(marker);
                                if (ideaId != null) {
                                    showIdeaPopup(ideaId);
                                }
                                return false;
                            });
                        }
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Ensuring call listener is active");

        // Ensure call listener is active
        startCallListener();

        // Refresh ideas when returning
        if (mMap != null) {
            loadIdeasFromFirestore();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Keeping call listener active");
        // IMPORTANT: DON'T stop listener when paused - we need it for incoming calls!
    }

    /**
     * START INCOMING CALL LISTENER
     */
    private void startCallListener() {
        if (isListeningForCalls) {
            Log.d(TAG, "Call listener already active");
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "No user logged in, skipping call listener");
            return;
        }

        Log.d(TAG, "STARTING CALL LISTENER for " + currentUser.getUid());

        callListener = db.collection("calls")
                .whereEqualTo("receiverId", currentUser.getUid())
                .whereEqualTo("status", "ringing")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening for calls", error);
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String channelName = doc.getString("channelName");
                            String callerName = doc.getString("callerName");
                            String callerId = doc.getString("callerId");

                            // Show incoming call activity
                            Intent intent = new Intent(MainActivity.this, IncomingCallActivity.class);
                            intent.putExtra("CHANNEL_NAME", channelName);
                            intent.putExtra("CALLER_NAME", callerName);
                            intent.putExtra("CALLER_ID", callerId);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);

                            // Only handle first call
                            break;
                        }
                    }
                });
        isListeningForCalls = true;
    }

    /**
     * Stop call listener
     */
    private void stopCallListener() {
        if (callListener != null) {
            callListener.remove();
            callListener = null;
            isListeningForCalls = false;
            Log.d(TAG, "Call listener stopped");
        }
    }

    /**
     * Show idea popup
     */
    private void showIdeaPopup(String ideaId) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.idea_popup_bottom_sheet, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Initialize popup views
        TextView popupClientName = bottomSheetView.findViewById(R.id.popupClientName);
        TextView popupIdeaTitle = bottomSheetView.findViewById(R.id.popupIdeaTitle);
        TextView popupDescription = bottomSheetView.findViewById(R.id.popupDescription);
        TextView popupJoinedCount = bottomSheetView.findViewById(R.id.popupJoinedCount);
        TextView popupJoinButtonText = bottomSheetView.findViewById(R.id.popupJoinButtonText);
        CardView popupJoinButton = bottomSheetView.findViewById(R.id.popupJoinButton);
        CardView popupOpenButton = bottomSheetView.findViewById(R.id.popupOpenButton);

        // Load idea data from Firestore
        db.collection("ideas").document(ideaId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        String description = documentSnapshot.getString("description");
                        String clientName = documentSnapshot.getString("clientName");
                        Long joinedCount = documentSnapshot.getLong("joinedCount");

                        if (popupIdeaTitle != null) popupIdeaTitle.setText(title);
                        if (popupDescription != null) popupDescription.setText(description);
                        if (popupClientName != null) popupClientName.setText(clientName);
                        if (popupJoinedCount != null) popupJoinedCount.setText((joinedCount != null ? joinedCount : 0) + " joined");

                        // Check if user has already joined
                        checkIfUserJoined(ideaId, popupJoinButtonText);
                    }
                });

        // Join/Leave button click with Animation
        if (popupJoinButton != null) {
            popupJoinButton.setOnClickListener(v -> {
                v.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(80)
                        .withEndAction(() -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        })
                        .start();

                toggleJoinIdea(ideaId, popupJoinButtonText, bottomSheetDialog);
            });
        }

        // Open button click
        if (popupOpenButton != null) {
            popupOpenButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, IdeaActivity.class);
                intent.putExtra("IDEA_ID", ideaId);
                startActivity(intent);
                bottomSheetDialog.dismiss();
            });
        }

        bottomSheetDialog.show();

        View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

        if (bottomSheet != null) {
            bottomSheet.setBackground(null);

            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            // 🔥 Spring-like feel
            behavior.setHalfExpandedRatio(0.6f);
            behavior.setPeekHeight(300);

            bottomSheet.setScaleX(0.95f);
            bottomSheet.setScaleY(0.95f);
            bottomSheet.setAlpha(0f);

            bottomSheet.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(300)
                    .start();
        }
    }

    /**
     * Check if user has already joined an idea
     */
    private void checkIfUserJoined(String ideaId, TextView joinButtonText) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && joinButtonText != null) {
            db.collection("ideas")
                    .document(ideaId)
                    .collection("members")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            joinButtonText.setText("Leave");
                        } else {
                            joinButtonText.setText("Join");
                        }
                    });
        }
    }

    /**
     * Toggle join/leave idea
     */
    private void toggleJoinIdea(String ideaId, TextView joinButtonText, BottomSheetDialog dialog) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        final String userId = currentUser.getUid();
        final DocumentReference ideaRef = db.collection("ideas").document(ideaId);
        final DocumentReference memberRef = ideaRef.collection("members").document(userId);

        db.runTransaction((Transaction.Function<Boolean>) transaction -> {
            DocumentSnapshot ideaSnapshot = transaction.get(ideaRef);
            DocumentSnapshot memberSnapshot = transaction.get(memberRef);

            long currentJoinedCount = 0;
            if (ideaSnapshot.contains("joinedCount")) {
                Long count = ideaSnapshot.getLong("joinedCount");
                if (count != null) {
                    currentJoinedCount = count;
                }
            }

            if (memberSnapshot.exists()) {
                // User is a member, so leave
                transaction.update(ideaRef, "joinedCount", currentJoinedCount - 1);
                transaction.delete(memberRef);
                return false; // User has left
            } else {
                // User is not a member, so join
                transaction.update(ideaRef, "joinedCount", currentJoinedCount + 1);
                Map<String, Object> memberData = new HashMap<>();
                memberData.put("userId", currentUser.getUid());
                memberData.put("joinedAt", System.currentTimeMillis());
                memberData.put("isOnline", true);
                transaction.set(memberRef, memberData);
                return true; // User has joined
            }
        }).addOnSuccessListener(isJoined -> {
            if (joinButtonText != null) {
                if (isJoined) {
                    joinButtonText.setText("Leave");
                    Toast.makeText(MainActivity.this, "Joined the idea", Toast.LENGTH_SHORT).show();
                } else {
                    joinButtonText.setText("Join");
                    Toast.makeText(MainActivity.this, "Left the idea", Toast.LENGTH_SHORT).show();
                }
            }
        }).addOnFailureListener(e ->
                Toast.makeText(MainActivity.this, "Failed to update: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCallListener();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
