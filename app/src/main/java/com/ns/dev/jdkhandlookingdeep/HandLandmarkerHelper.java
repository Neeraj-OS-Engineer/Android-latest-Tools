package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.util.Log;
import androidx.camera.core.ImageProxy;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerOptions;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wrapper for MediaPipe HandLandmarker.
 * Receives ImageProxy frames from CameraX, processes them, and forwards results.
 */
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
                    .setResultListener(this::onResult)
                    .setErrorListener(this::onError)
                    .setNumHands(2)
                    .build();
            handLandmarker = HandLandmarker.createFromOptions(context, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create HandLandmarker", e);
        }
    }

    /**
     * Process an ImageProxy from CameraX.
     * This method is called on a background thread.
     */
    public void processImageProxy(ImageProxy imageProxy) {
        if (handLandmarker == null) return;

        // Convert ImageProxy to a Bitmap or MediaPipe Image.
        // For simplicity, we'll convert to Bitmap (RGB) using the utility method.
        Bitmap bitmap = imageProxyToBitmap(imageProxy);
        if (bitmap == null) return;

        // MediaPipe's HandLandmarker expects a MediaPipe Image.
        // Using MediaPipe's AndroidImageBitmapBuilder.
        // Note: This requires the 'mediapipe-tasks-vision' dependency which we have.
        com.google.mediapipe.framework.image.BitmapImageBuilder bitmapBuilder =
                new com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap);
        com.google.mediapipe.framework.image.MPImage mpImage = bitmapBuilder.build();

        // Timestamp in milliseconds (required for LIVE_STREAM mode)
        long timestamp = System.currentTimeMillis();

        // Run inference asynchronously
        handLandmarker.detectAsync(mpImage, timestamp);
    }

    /**
     * Converts an ImageProxy to a Bitmap (RGB).
     * This is a simplified conversion; for production you may want to use
     * MediaPipe's built-in converters or a more efficient method.
     */
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        // Get the YUV_420_888 planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();

        // Simple YUV to RGB conversion (not efficient but works for demo)
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yIndex = y * planes[0].getRowStride() + x;
                int uvIndex = (y / 2) * planes[1].getRowStride() + (x / 2);
                int Y = yBuffer.get(yIndex) & 0xFF;
                int U = uBuffer.get(uvIndex) & 0xFF;
                int V = vBuffer.get(uvIndex) & 0xFF;

                // Convert YUV to RGB (BT.601)
                int R = (int) (Y + 1.402 * (V - 128));
                int G = (int) (Y - 0.344 * (U - 128) - 0.714 * (V - 128));
                int B = (int) (Y + 1.772 * (U - 128));

                R = Math.max(0, Math.min(255, R));
                G = Math.max(0, Math.min(255, G));
                B = Math.max(0, Math.min(255, B));

                pixels[y * width + x] = (0xFF << 24) | (R << 16) | (G << 8) | B;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void onResult(HandLandmarkerResult result, long timestamp) {
        // Forward the result to the listener on the main thread (or the thread where listener expects)
        // We'll use the main thread for simplicity.
        if (listener != null) {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> listener.onHandLandmarks(result));
        }
    }

    private void onError(RuntimeException error) {
        Log.e(TAG, "HandLandmarker error: " + error.getMessage());
    }

    public void close() {
        if (handLandmarker != null) {
            handLandmarker.close();
        }
        inferenceExecutor.shutdown();
    }
}
