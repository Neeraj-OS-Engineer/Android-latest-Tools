package com.ns.dev.jdkhandlookingdeep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerOptions;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AndroidApplication {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    // Required permissions: Camera, Read external storage (for media files)
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO      // if needed for video audio
    };

    private SpatialRenderer spatialRenderer;
    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private CameraXHelper cameraXHelper;  // Custom helper to manage CameraX and feed frames to MediaPipe
    private GestureController gestureController;
    private TextView debugText;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the layout
        setContentView(R.layout.activity_main);

        debugText = findViewById(R.id.debug_text);

        // Check and request permissions
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        } else {
            initializeApp();
        }
    }

    private boolean hasPermissions() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
                initializeApp();
            } else {
                Toast.makeText(this, "Permissions are required to run the app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeApp() {
        // Start background media scanning service
        startMediaScanService();

        // Initialize LibGDX with the SpatialRenderer
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;

        // Pass the Android context to the renderer
        spatialRenderer = new SpatialRenderer(this);
        initialize(spatialRenderer, config);

        // Initialize MediaPipe Hand Landmarker
        initHandLandmarker();

        // Set up CameraX to feed frames to MediaPipe
        cameraXHelper = new CameraXHelper(this, cameraExecutor);
        cameraXHelper.setFrameProcessor(this::processFrame);

        // Connect GestureController (it will be passed the camera from the renderer later)
        // We'll wait until the renderer's camera is available.
        // In SpatialRenderer, after camera is created, we can set a callback.
        spatialRenderer.setOnCameraReadyCallback(camera -> {
            gestureController = new GestureController(camera, new GestureController.GestureListener() {
                @Override
                public void onPlay() { spatialRenderer.mediaPlay(); }
                @Override
                public void onPause() { spatialRenderer.mediaPause(); }
                @Override
                public void onNext() { spatialRenderer.mediaNext(); }
                @Override
                public void onPrevious() { spatialRenderer.mediaPrevious(); }
                @Override
                public void onButtonTouch(GestureController.ButtonType buttonType) {
                    // Handle direct button touches if needed
                }
            });
            // Provide button models for ray-casting
            gestureController.setButtonModels(spatialRenderer.getButtonModels());
        });

        // Load media list when service finishes scanning
        loadMediaListFromService();
    }

    private void startMediaScanService() {
        Intent serviceIntent = new Intent(this, MediaScanService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void loadMediaListFromService() {
        // Wait for service to complete scanning (e.g., via broadcast or binding)
        // For simplicity, we'll use a broadcast receiver or simply read the file when service is done.
        // Here we assume the service saves the list to file. We'll poll the file in a background thread.
        new Thread(() -> {
            File mediaFile = new File(getFilesDir(), "media_list.json");
            while (!mediaFile.exists()) {
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
            // Read the file with Gson and pass to renderer
            runOnUiThread(() -> {
                List<MediaItem> mediaList = MediaItem.loadFromFile(getApplicationContext());
                if (mediaList != null && !mediaList.isEmpty()) {
                    spatialRenderer.setMediaList(mediaList);
                } else {
                    Toast.makeText(MainActivity.this, "No media found", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void initHandLandmarker() {
        try {
            AndroidAssetUtil.initializeNativeAssetManager(this);
            HandLandmarkerOptions options = HandLandmarkerOptions.builder()
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onHandLandmarkResult)
                    .setNumHands(2)
                    .build();
            handLandmarker = HandLandmarker.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create HandLandmarker", e);
            Toast.makeText(this, "Hand tracking not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void processFrame(byte[] imageData, int width, int height, long timestamp) {
        if (handLandmarker != null) {
            handLandmarker.detectAsync(imageData, width, height, timestamp);
        }
    }

    private void onHandLandmarkResult(HandLandmarkerResult result, long timestamp) {
        // Convert result to separated left/right hands
        List<NormalizedLandmark> left = null, right = null;
        if (result.landmarks() != null) {
            for (int i = 0; i < result.landmarks().size(); i++) {
                // Handedness is available in result.handedness()
                if (result.handedness().get(i).get(0).categoryName().equals("Left")) {
                    left = result.landmarks().get(i);
                } else {
                    right = result.landmarks().get(i);
                }
            }
        }
        if (gestureController != null) {
            gestureController.updateHands(left, right);
        }

        // Optional: display debug info
        if (debugText.getVisibility() == View.VISIBLE) {
            String debug = "Hands: " + (left != null ? "L " : "") + (right != null ? "R" : "");
            mainHandler.post(() -> debugText.setText(debug));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraXHelper != null) {
            cameraXHelper.startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraXHelper != null) {
            cameraXHelper.stopCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handLandmarker != null) {
            handLandmarker.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
