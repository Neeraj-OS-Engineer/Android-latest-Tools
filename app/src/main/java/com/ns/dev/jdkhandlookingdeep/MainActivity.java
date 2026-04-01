package com.ns.dev.jdkhandlookingdeep;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private TextView debugText;
    private FrameLayout gdxContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gdxContainer = findViewById(R.id.gdx_container);
        debugText = findViewById(R.id.debug_text);

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
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initApp() {
        startMediaScanService();

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;

        // Create renderer
        spatialRenderer = new SpatialRenderer(this);

        // Get the LibGDX view without replacing the content view
        View gdxView = initializeForView(spatialRenderer, config);

        // Add it to our container
        gdxContainer.addView(gdxView);
        // Ensure debug text stays on top
        debugText.bringToFront();

        // Setup gesture controller after camera is ready (on GL thread)
        spatialRenderer.setOnCameraReadyCallback(camera -> {
            // GestureController will run on GL thread – safe
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
                    runOnUiThread(() -> {
                        debugText.setText("Button: " + buttonType);
                        debugText.setVisibility(View.VISIBLE);
                    });
                }
                @Override
                public void onHandXChange(float normalizedX) {
                    spatialRenderer.setCarouselTargetAngle(normalizedX * 2 * (float) Math.PI);
                }
            });
            spatialRenderer.setGestureController(gestureController);
        });

        // Start camera (on main thread)
        cameraHelper = new CameraXHelper(this, this);
        cameraHelper.startCamera();

        // Load media list in background
        loadMediaList();
    }

    private void startMediaScanService() {
        android.content.Intent serviceIntent = new android.content.Intent(this, MediaScanService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void loadMediaList() {
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

    @Override
    public void onHandLandmarks(HandLandmarkerResult result) {
        // Called on main thread from HandLandmarkerHelper
        // Pass to GestureController (which is thread‑safe via its update method)
        if (gestureController != null) {
            gestureController.update(result);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraHelper != null) cameraHelper.startCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraHelper != null) cameraHelper.stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraHelper != null) cameraHelper.stopCamera();
        // LibGDX view will be disposed automatically via AndroidApplication
    }
}
