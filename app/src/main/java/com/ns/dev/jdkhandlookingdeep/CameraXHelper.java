package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.core.CameraInfoUnavailableException;   // <- Add this import
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraXHelper {

    private static final String TAG = "CameraXHelper";
    private final Context context;
    private final HandLandmarkListener listener;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private HandLandmarkerHelper handLandmarkerHelper;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private boolean isCameraStarted = false;

    public interface HandLandmarkListener {
        void onHandLandmarks(HandLandmarkerResult result);
    }

    public CameraXHelper(Context context, HandLandmarkListener listener) {
        this.context = context;
        this.listener = listener;
        handLandmarkerHelper = new HandLandmarkerHelper(context, listener);
    }

    public void startCamera() {
        if (isCameraStarted) {
            Log.d(TAG, "Camera already started");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                isCameraStarted = true;
                Log.d(TAG, "Camera started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Camera binding failed", e);
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "Failed to start camera: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases() throws CameraInfoUnavailableException {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider is null");
            return;
        }

        // Try front camera first, then back camera if not available
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        // Check if front camera exists – this can throw CameraInfoUnavailableException
        if (!cameraProvider.hasCamera(cameraSelector)) {
            Log.w(TAG, "Front camera not available, using back camera");
            cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();
        }

        // Preview (optional – we don't show it, but it's required to keep the camera alive)
        Preview preview = new Preview.Builder().build();

        // ImageAnalysis – set to RGB format if possible for faster conversion
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) // Prefer RGBA
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        // Unbind all use cases before binding new ones
        cameraProvider.unbindAll();

        // Bind to lifecycle (the activity lifecycle is passed in via the context)
        Camera camera = cameraProvider.bindToLifecycle(
                (androidx.lifecycle.LifecycleOwner) context,
                cameraSelector,
                preview,
                imageAnalysis
        );
        Log.d(TAG, "Camera bound: " + camera.getCameraInfo().getLensFacing());
    }

    private void analyzeImage(ImageProxy image) {
        handLandmarkerHelper.processImageProxy(image);
    }

    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        if (handLandmarkerHelper != null) {
            handLandmarkerHelper.close();
        }
        isCameraStarted = false;
        Log.d(TAG, "Camera stopped");
    }
}
