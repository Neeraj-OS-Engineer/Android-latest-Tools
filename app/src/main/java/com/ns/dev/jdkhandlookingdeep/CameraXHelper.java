package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.util.Log;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class to initialize CameraX and pass frames to a MediaPipe HandLandmarker.
 */
public class CameraXHelper {

    private static final String TAG = "CameraXHelper";
    private final Context context;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private HandLandmarkerHelper handLandmarkerHelper; // custom class that wraps MediaPipe

    // Executor for image analysis (non-UI thread)
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    public interface HandLandmarkListener {
        void onHandLandmarks(HandLandmarkerResult result);
    }

    private HandLandmarkListener listener;

    public CameraXHelper(Context context, HandLandmarkListener listener) {
        this.context = context;
        this.listener = listener;
        // Initialize MediaPipe HandLandmarker (you need to implement HandLandmarkerHelper)
        handLandmarkerHelper = new HandLandmarkerHelper(context, listener);
    }

    /**
     * Start the camera and begin analyzing frames.
     */
    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases() {
        // Select front camera for hand tracking (or back camera if needed)
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        // Preview (optional) – can be added to a PreviewView if you want to show camera feed
        Preview preview = new Preview.Builder().build();

        // ImageAnalysis: analyze frames at a fixed resolution
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        // Bind to lifecycle (the activity lifecycle is passed in via the context)
        cameraProvider.bindToLifecycle((androidx.lifecycle.LifecycleOwner) context,
                cameraSelector, preview, imageAnalysis);
    }

    /**
     * Convert ImageProxy to a format suitable for MediaPipe and send to the hand landmarker.
     */
    private void analyzeImage(ImageProxy image) {
        if (handLandmarkerHelper == null) {
            image.close();
            return;
        }

        // Convert ImageProxy to a MediaPipe Image (or a byte array)
        // For simplicity, we'll get a ByteBuffer from the first plane and pass it.
        // In a real implementation, you'd convert the YUV_420_888 format to RGB or use MediaPipe's utilities.
        // Here we assume HandLandmarkerHelper has a method that accepts ImageProxy directly.
        handLandmarkerHelper.processImageProxy(image);

        // Close the image after processing
        image.close();
    }

    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (handLandmarkerHelper != null) {
            handLandmarkerHelper.close();
        }
        cameraExecutor.shutdown();
    }
}
