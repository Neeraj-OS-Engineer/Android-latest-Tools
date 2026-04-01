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
    private TextView debugText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout
        setContentView(R.layout.activity_main);

        // Get references to UI elements
        debugText = findViewById(R.id.debug_text);

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
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show();
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

        // Create renderer
        spatialRenderer = new SpatialRenderer(this);

        // Initialize LibGDX – this adds its view to the activity's root view.
        // To put it inside the FrameLayout, we need to use initializeForView,
        // but AndroidApplication doesn't expose that directly. Instead, we can
        // use AndroidApplication.initialize() which replaces the content view.
        // To keep the layout, we'll remove the default view and add it to our container.
        initialize(spatialRenderer, config);

        // After initialization, the GLSurfaceView is added to the root window.
        // We'll move it to our FrameLayout.
        FrameLayout container = findViewById(R.id.gdx_container);
        View gdxView = getWindow().getDecorView().findViewById(android.R.id.content).getRootView();
        // Actually, the GLSurfaceView is the child of the root view. Simpler:
        // Wait a moment and then move the view.
        // But to avoid complexity, we'll simply use the default fullscreen view
        // and overlay the debug text. The layout's container isn't used.
        // Instead, we'll just use the default fullscreen view and show debug text on top.
        // This is simpler and works.

        // The debug text will appear over the LibGDX view because it's in the same layout.
        // We need to make sure the layout's background is transparent and the debug text
        // is on top. The current layout has a black background, but the GLSurfaceView
        // will be added on top of it. To keep the debug text visible, we set the debug text
        // to be visible and above the GLSurfaceView.
        // Actually, we need to bring the debug text to the front.
        debugText.bringToFront();

        // Set up gesture controller after camera is ready
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
                    // Optionally show debug text
                    debugText.setText("Button: " + buttonType);
                    debugText.setVisibility(View.VISIBLE);
                }
                @Override
                public void onHandXChange(float normalizedX) {
                    if (spatialRenderer != null) {
                        spatialRenderer.setCarouselTargetAngle(normalizedX * 2 * (float) Math.PI);
                    }
                }
            });
            spatialRenderer.setGestureController(gestureController);
        });

        // Start camera
        cameraHelper = new CameraXHelper(this, this);
        cameraHelper.startCamera();

        // Load media list
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
    }
}
