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
import com.google.mediapipe.tasks.core.BaseOptions;               // Correct import
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
                    .setResultListener(this::onResult)
                    .setErrorListener(this::onError)
                    .setNumHands(2)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(context, options);
            Log.d(TAG, "HandLandmarker initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize HandLandmarker", e);
        }
    }

    private void onResult(HandLandmarkerResult result, MPImage mpImage, long timestamp) {
        if (listener != null) {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> listener.onHandLandmarks(result));
        }
    }

    private void onError(RuntimeException error) {
        Log.e(TAG, "HandLandmarker error: " + error.getMessage());
    }

    public void processImageProxy(ImageProxy imageProxy) {
        inferenceExecutor.execute(() -> {
            if (handLandmarker == null) {
                imageProxy.close();
                return;
            }

            Bitmap bitmap = convertImageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                imageProxy.close();
                return;
            }

            MPImage mpImage = new BitmapImageBuilder(bitmap).build();

            long timestampMs = imageProxy.getImageInfo().getTimestamp() / 1_000_000L;
            handLandmarker.detectAsync(mpImage, timestampMs);

            imageProxy.close();
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

                int[] rgb = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int yIndex = y * planes[0].getRowStride() + x;
                        int uvIndex = (y / 2) * planes[1].getRowStride() + (x / 2);
                        int Y = yData[yIndex] & 0xFF;
                        int U = uData[uvIndex] & 0xFF;
                        int V = vData[uvIndex] & 0xFF;

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
                Log.e(TAG, "Error converting YUV_420_888", e);
            }
        } else if (format == ImageFormat.JPEG) {
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
