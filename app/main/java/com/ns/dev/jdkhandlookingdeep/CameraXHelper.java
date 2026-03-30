package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.util.Log;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

public class CameraXHelper {
    private Context context;
    private ExecutorService executor;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private FrameProcessor frameProcessor;

    public interface FrameProcessor {
        void process(byte[] data, int width, int height, long timestamp);
    }

    public CameraXHelper(Context context, ExecutorService executor) {
        this.context = context;
        this.executor = executor;
    }

    public void setFrameProcessor(FrameProcessor processor) {
        this.frameProcessor = processor;
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e("CameraXHelper", "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        // Preview is optional; you can add a PreviewView if you want to show camera feed
        Preview preview = new Preview.Builder().build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(executor, this::analyzeImage);

        cameraProvider.bindToLifecycle((androidx.lifecycle.LifecycleOwner) context,
                cameraSelector, preview, imageAnalysis);
    }

    private void analyzeImage(ImageProxy image) {
        if (frameProcessor == null) {
            image.close();
            return;
        }
        // Convert ImageProxy to byte array (RGB or RGBA)
        // For simplicity, we'll convert to a format MediaPipe expects.
        // MediaPipe tasks usually accept Bitmap or ImageProxy directly via utilities.
        // This is a placeholder: you'd need to convert the image.
        // For demonstration, we assume the following:
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        // Note: This is not the full conversion; you need to handle YUV_420_888 correctly.
        // Use a utility like ImageUtils.convertYUVToRGB or similar.
        frameProcessor.process(data, image.getWidth(), image.getHeight(), System.currentTimeMillis());
        image.close();
    }

    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
