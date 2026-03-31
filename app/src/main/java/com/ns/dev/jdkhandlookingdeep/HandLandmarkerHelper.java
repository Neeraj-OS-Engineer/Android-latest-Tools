package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HandLandmarkerHelper {

    private static final String TAG = "HandLandmarkerHelper";
    private static final String MODEL_FILE = "hand_landmarker.task";

    private final Context context;
    private final CameraXHelper.HandLandmarkListener listener;
    private HandLandmarker handLandmarker;
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public HandLandmarkerHelper(Context context, CameraXHelper.HandLandmarkListener listener) {
        this.context = context;
        this.listener = listener;
        initHandLandmarker();
    }

    private void initHandLandmarker() {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_FILE)
                    .build();

            HandLandmarkerOptions options = HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    // FIX: Ab ye error nahi dega kyunki onResult method update kar diya gaya hai
                    .setResultListener(this::onResult)
                    .setErrorListener(this::onError)
                    .setNumHands(2)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(context, options);
            Log.d(TAG, "HandLandmarker initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize HandLandmarker", e);
        }
    }

    // FIX: MediaPipe 0.10.14 mein sirf 2 parameters hote hain
    private void onResult(HandLandmarkerResult result, MPImage mpImage) {
        if (listener != null) {
            mainHandler.post(() -> listener.onHandLandmarks(result));
        }
    }

    private void onError(RuntimeException error) {
        Log.e(TAG, "HandLandmarker error: " + error.getMessage());
    }

    public void processImageProxy(ImageProxy imageProxy) {
        if (handLandmarker == null) {
            imageProxy.close();
            return;
        }

        inferenceExecutor.execute(() -> {
            try {
                Bitmap bitmap = convertImageProxyToBitmap(imageProxy);
                if (bitmap != null) {
                    MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                    // Nanoseconds to Milliseconds
                    long timestampMs = imageProxy.getImageInfo().getTimestamp() / 1_000_000;
                    
                    // detectAsync call karein
                    handLandmarker.detectAsync(mpImage, timestampMs);
                }
            } catch (Exception e) {
                Log.e(TAG, "Detection failed", e);
            } finally {
                // Hamesha close karein varna camera hang ho jayega
                imageProxy.close();
            }
        });
    }

    private Bitmap convertImageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        int width = image.getWidth();
        int height = image.getHeight();
        int format = image.getFormat();

        Bitmap bitmap = null;

        if (format == ImageFormat.YUV_420_888) {
            try {
                // Efficient YUV to Bitmap conversion can be complex, 
                // Using a simplified version for this context
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer yBuffer = planes[0].getBuffer();
                ByteBuffer uBuffer = planes[1].getBuffer();
                ByteBuffer vBuffer = planes[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();

                byte[] nv21 = new byte[ySize + uSize + vSize];
                yBuffer.get(nv21, 0, ySize);
                vBuffer.get(nv21, ySize, vSize);
                uBuffer.get(nv21, ySize + vSize, uSize);

                android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 100, out);
                byte[] imageBytes = out.toByteArray();
                bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            } catch (Exception e) {
                Log.e(TAG, "YUV Conversion error", e);
            }
        }

        if (bitmap == null) return null;

        // Handle Rotation
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        return bitmap;
    }

    public void close() {
        if (handLandmarker != null) {
            handLandmarker.close();
        }
        inferenceExecutor.shutdown();
    }
}
