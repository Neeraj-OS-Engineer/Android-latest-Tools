package com.ns.dev.jdkhandlookingdeep;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.io.File;
import java.util.List;

public class MainActivity extends AndroidApplication implements CameraXHelper.HandLandmarkListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    private SpatialRenderer spatialRenderer;
    private GestureController gestureController;
    private CameraXHelper cameraHelper;
    private HandLandmarkerHelper handLandmarkerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check permissions
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        } else {
            initApp();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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
                initApp();
            } else {
                Toast.makeText(this, "Permissions are required to run the app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initApp() {
        // Start media scanning service
        startMediaScanService();

        // Configure LibGDX
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;

        // Create the renderer
        spatialRenderer = new SpatialRenderer(this);
        initialize(spatialRenderer, config);

        // When the renderer's camera is ready, create the gesture controller
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
                    // Handle button touch (optional)
                }
                @Override
                public void onHandXChange(float normalizedX) {
                    // Map hand horizontal position to carousel angle
                    if (spatialRenderer != null) {
                        float targetAngle = normalizedX * 2 * (float) Math.PI;
                        spatialRenderer.setCarouselTargetAngle(targetAngle);
                    }
                }
            });
            spatialRenderer.setGestureController(gestureController);
        });

        // Start camera and hand landmarker
        cameraHelper = new CameraXHelper(this, this);
        cameraHelper.startCamera();

        // Load media list after service finishes
        loadMediaList();
    }

    private void startMediaScanService() {
        // Start foreground service
        android.content.Intent serviceIntent = new android.content.Intent(this, MediaScanService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void loadMediaList() {
        // Read the JSON file when it's ready (polling for simplicity)
        new Thread(() -> {
            File mediaFile = new File(getFilesDir(), "media_list.json");
            while (!mediaFile.exists()) {
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
            List<MediaItem> items = MediaItem.loadFromFile(mediaFile);
            if (items != null && !items.isEmpty()) {
                runOnUiThread(() -> spatialRenderer.setMediaList(items));
            } else {
                runOnUiThread(() -> Toast.makeText(this, "No media found", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Called by CameraXHelper when a hand landmark result is ready
    @Override
    public void onHandLandmarks(HandLandmarkerResult result) {
        if (gestureController != null) {
            gestureController.update(result);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraHelper != null) {
            cameraHelper.startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraHelper != null) {
            cameraHelper.stopCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handLandmarkerHelper != null) {
            handLandmarkerHelper.close();
        }
        if (cameraHelper != null) {
            cameraHelper.stopCamera();
        }
    }
}
