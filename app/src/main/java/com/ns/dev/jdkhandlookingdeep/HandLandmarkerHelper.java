package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HandLandmarkerHelper {

    private static final String TAG = "HandLandmarkerHelper";
    private final Context context;
    private HandLandmarker handLandmarker;
    private final CameraXHelper.HandLandmarkListener listener;
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();

    public HandLandmarkerHelper(Context context, CameraXHelper.HandLandmarkListener listener) {
        this.context = context;
        this.listener = listener;
        initHandLandmarker();
    }

    private void initHandLandmarker() {
        try {
            HandLandmarkerOptions options = HandLandmarkerOptions.builder()
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener((result, mpImage, timestamp) -> {
                        // Forward the result to the listener on the main thread
                        if (listener != null) {
                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> listener.onHandLandmarks(result));
                        }
                    })
                    .setErrorListener(this::onError)
                    .setNumHands(2)
                    .build();
            handLandmarker = HandLandmarker.createFromOptions(context, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create HandLandmarker", e);
        }
    }

    private void onError(RuntimeException error) {
        Log.e(TAG, "HandLandmarker error: " + error.getMessage());
    }

    public void processImageProxy(ImageProxy imageProxy) {
        // Placeholder – implement frame conversion when ready
        imageProxy.close();
    }

    public void close() {
        if (handLandmarker != null) {
            handLandmarker.close();
        }
        inferenceExecutor.shutdown();
    }
}
