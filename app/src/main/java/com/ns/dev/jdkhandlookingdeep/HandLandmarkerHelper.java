package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class to process camera frames with MediaPipe Hand Landmarker.
 * Uses MediaPipe Tasks Vision 0.10.14.
 */
public class HandLandmarkerHelper {

    private static final String TAG = "HandLandmarkerHelper";
    private static final String MODEL_FILE = "hand_landmarker.task";

    private final Context context;
    private final CameraXHelper.HandLandmarkListener listener;
    private HandLandmarker handLandmarker;
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();

    public HandLandmarkerHelper(Context context, CameraXHelper.HandLandmarkListener listener) {
        this.context = context;
        this.listener = listener;
        initHandLandmarker();
    }

    /**
     * Initializes the HandLandmarker with the model from assets and LIVE_STREAM mode.
     */
    private void initHandLandmarker() {
        try {
            // Build BaseOptions using the model file from assets
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_FILE)
                    .build();

            HandLandmarkerOptions options = HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onResult)
                    .setErrorListener(this::onError)
                    .setNumHands(2)                 // detect up to 2 hands
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(context, options);
            Log.d(TAG, "HandLandmarker initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize HandLandmarker", e);
        }
    }

    /**
     * Called when a hand landmark result is available.
     */
    private void onResult(HandLandmarkerResult result, MPImage mpImage, long timestamp) {
        if (listener != null) {
            // Dispatch result on the main thread
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> listener.onHandLandmarks(result));
        }
    }

    /**
     * Called when an error occurs during landmark detection.
     */
    private void onError(RuntimeException error) {
        Log.e(TAG, "HandLandmarker error: " + error.getMessage());
    }

    /**
     * Processes an ImageProxy frame from CameraX.
     * Converts it to MPImage and runs async detection on a background thread.
     * Must be called on the camera thread.
     *
     * @param imageProxy the frame to process (will be closed after use)
     */
    public void processImageProxy(ImageProxy imageProxy) {
        inferenceExecutor.execute(() -> {
            if (handLandmarker == null) {
                imageProxy.close();
                return;
            }

            // 1. Convert ImageProxy to Bitmap with correct orientation
            Bitmap bitmap = convertImageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                imageProxy.close();
                return;
            }

            // 2. Build MPImage from the bitmap
            MPImage mpImage = new BitmapImageBuilder(bitmap).build();

            // 3. Get timestamp in milliseconds (CameraX provides nanoseconds)
            long timestampMs = imageProxy.getImageInfo().getTimestamp() / 1_000_000L;

            // 4. Run async detection
            handLandmarker.detectAsync(mpImage, timestampMs);

            // 5. Close the ImageProxy when done
            imageProxy.close();
        });
    }

    /**
     * Converts an ImageProxy to a Bitmap with proper orientation.
     * Handles YUV_420_888 format (common for CameraX) and other formats.
     *
     * @param imageProxy the frame to convert
     * @return a correctly rotated Bitmap, or null if conversion fails
     */
    private Bitmap convertImageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        // Get the image dimensions and format
        int width = image.getWidth();
        int height = image.getHeight();
        int format = image.getFormat();

        Bitmap bitmap = null;

        if (format == ImageFormat.YUV_420_888) {
            // Convert YUV_420_888 to RGB using YuvImage (simpler for JPEG, but we need ARGB)
            // Alternative: use RenderScript or manual conversion, but for simplicity we use YuvImage to JPEG.
            // This is not the most efficient but works for demonstration.
            try {
                // Extract Y, U, V planes
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer yBuffer = planes[0].getBuffer();
                ByteBuffer uBuffer = planes[1].getBuffer();
                ByteBuffer vBuffer = planes[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();

                byte[] yData = new byte[ySize];
                byte[] uData = new byte[uSize];
                byte[] vData = new byte[vSize];
                yBuffer.get(yData);
                uBuffer.get(uData);
                vBuffer.get(vData);

                // Convert YUV to RGB manually (simplified)
                int[] rgb = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int yIndex = y * planes[0].getRowStride() + x;
                        int uvIndex = (y / 2) * planes[1].getRowStride() + (x / 2);
                        int Y = yData[yIndex] & 0xFF;
                        int U = uData[uvIndex] & 0xFF;
                        int V = vData[uvIndex] & 0xFF;

                        // YUV to RGB conversion (BT.601)
                        int R = (int) (Y + 1.402 * (V - 128));
                        int G = (int) (Y - 0.344 * (U - 128) - 0.714 * (V - 128));
                        int B = (int) (Y + 1.772 * (U - 128));

                        R = Math.max(0, Math.min(255, R));
                        G = Math.max(0, Math.min(255, G));
                        B = Math.max(0, Math.min(255, B));

                        rgb[y * width + x] = (0xFF << 24) | (R << 16) | (G << 8) | B;
                    }
                }

                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.setPixels(rgb, 0, width, 0, 0, width, height);
            } catch (Exception e) {
                Log.e(TAG, "Error converting YUV_420_888 to Bitmap", e);
            }
        } else if (format == ImageFormat.JPEG) {
            // For JPEG, we can decode directly (rare with CameraX)
            try {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception e) {
                Log.e(TAG, "Error decoding JPEG", e);
            }
        } else {
            Log.w(TAG, "Unsupported image format: " + format);
        }

        if (bitmap == null) return null;

        // Apply rotation to correct orientation
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            // If rotation is 90 or 270, swap width/height
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                int newWidth = bitmap.getHeight();
                int newHeight = bitmap.getWidth();
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                // Resize to original dimensions? Actually MPImage expects original dimensions, but rotation already accounted.
                // We keep the rotated bitmap as is.
            } else {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
        }

        return bitmap;
    }

    /**
     * Releases resources and shuts down the executor.
     */
    public void close() {
        if (handLandmarker != null) {
            handLandmarker.close();
        }
        inferenceExecutor.shutdown();
    }
}
