package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
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
                    .setResultListener(this::onResult)   // Only 2 parameters: result, mpImage
                    .setErrorListener(this::onError)
                    .setNumHands(2)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(context, options);
            Log.d(TAG, "HandLandmarker initialized");
        } catch (Exception e) {
            Log.e(TAG, "Init failed", e);
        }
    }

    // CRITICAL: Only 2 parameters – matches MediaPipe 0.10.14
    private void onResult(HandLandmarkerResult result, MPImage mpImage) {
        if (listener != null) {
            mainHandler.post(() -> listener.onHandLandmarks(result));
        }
    }

    private void onError(RuntimeException error) {
        Log.e(TAG, "Landmarker error", error);
    }

    public void processImageProxy(ImageProxy imageProxy) {
        if (handLandmarker == null) {
            imageProxy.close();
            return;
        }

        inferenceExecutor.execute(() -> {
            try {
                Bitmap bitmap = convertYuv420888ToBitmap(imageProxy);
                if (bitmap != null) {
                    MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                    long timestampMs = imageProxy.getImageInfo().getTimestamp() / 1_000_000;
                    handLandmarker.detectAsync(mpImage, timestampMs);
                }
            } catch (Exception e) {
                Log.e(TAG, "Detection error", e);
            } finally {
                // Must close to prevent camera freeze
                imageProxy.close();
            }
        });
    }

    /**
     * Fast conversion from YUV_420_888 to RGB Bitmap.
     * No rotation is applied – the image remains in its natural orientation
     * (which is already correct for portrait).
     */
    private Bitmap convertYuv420888ToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        int width = image.getWidth();
        int height = image.getHeight();

        // Get YUV planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int[] rgb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Y index
                int yIndex = y * yRowStride + x;
                int Y = yBuffer.get(yIndex) & 0xFF;

                // UV index (simple subsampling)
                int uvX = x / 2;
                int uvY = y / 2;
                int uvIndex = uvY * uvRowStride + uvX * uvPixelStride;
                int U = uBuffer.get(uvIndex) & 0xFF;
                int V = vBuffer.get(uvIndex) & 0xFF;

                // YUV to RGB (BT.601)
                int R = (int) (Y + 1.402 * (V - 128));
                int G = (int) (Y - 0.344 * (U - 128) - 0.714 * (V - 128));
                int B = (int) (Y + 1.772 * (U - 128));

                R = Math.max(0, Math.min(255, R));
                G = Math.max(0, Math.min(255, G));
                B = Math.max(0, Math.min(255, B));

                rgb[y * width + x] = (0xFF << 24) | (R << 16) | (G << 8) | B;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(rgb, 0, width, 0, 0, width, height);
        return bitmap;
    }

    public void close() {
        if (handLandmarker != null) handLandmarker.close();
        inferenceExecutor.shutdown();
    }
}
