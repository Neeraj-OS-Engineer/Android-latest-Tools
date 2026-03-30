package com.ns.dev.jdkhandlookingdeep;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interprets hand landmarks from MediaPipe, maps gestures to player commands,
 * and handles ray‑based button interaction.
 */
public class GestureController {

    // Listener interface for gesture events
    public interface GestureListener {
        void onPlay();
        void onPause();
        void onNext();
        void onPrevious();
        void onButtonTouch(ButtonType buttonType);
        void onHandXChange(float normalizedX);   // for carousel rotation
    }

    public enum ButtonType {
        PLAY_PAUSE, NEXT, VOLUME_UP, VOLUME_DOWN
    }

    private Camera camera;
    private Map<ButtonType, ModelInstance> buttonModels = new HashMap<>();
    private GestureListener listener;

    // Gesture state tracking (edge detection)
    private boolean wasPlayGesture = false;
    private boolean wasPauseGesture = false;
    private boolean wasNextGesture = false;
    private boolean wasPrevGesture = false;

    // For carousel rotation: store last hand X coordinate
    private float lastHandX = 0.5f;

    // Landmark indices (same as before)
    private static final int INDEX_TIP = 8;
    private static final int THUMB_TIP = 4;
    private static final int THUMB_MCP = 2;
    private static final int INDEX_MCP = 5;
    private static final int MIDDLE_MCP = 9;
    private static final int MIDDLE_TIP = 12;
    private static final int RING_MCP = 13;
    private static final int RING_TIP = 16;
    private static final int PINKY_MCP = 17;
    private static final int PINKY_TIP = 20;

    public GestureController(Camera camera, GestureListener listener) {
        this.camera = camera;
        this.listener = listener;
    }

    /**
     * Call this method with the result from MediaPipe hand landmarker.
     * It will extract left/right hands and process gestures.
     */
    public void update(HandLandmarkerResult result) {
        if (result == null || result.landmarks().isEmpty()) {
            resetGestureStates();
            return;
        }

        // Separate left and right hands using the handedness list.
        List<NormalizedLandmark> left = null, right = null;
        for (int i = 0; i < result.landmarks().size(); i++) {
            String handedness = result.handedness().get(i).get(0).categoryName();
            if ("Left".equals(handedness)) {
                left = result.landmarks().get(i);
            } else {
                right = result.landmarks().get(i);
            }
        }

        // Process gestures
        processPlayPause(right);
        processNextPrev(left, right);

        // Carousel rotation: use right hand if available, else left
        List<NormalizedLandmark> handForRotation = (right != null) ? right : left;
        if (handForRotation != null) {
            float handX = handForRotation.get(INDEX_TIP).x(); // 0..1
            if (listener != null && Math.abs(handX - lastHandX) > 0.01f) {
                listener.onHandXChange(handX);
            }
            lastHandX = handX;
        }

        // Ray‑casting for button selection (use right hand tip)
        if (right != null) {
            Ray ray = getFingerTipRay(right);
            ButtonType touched = findTouchedButton(ray);
            if (touched != null && listener != null) {
                listener.onButtonTouch(touched);
            }
        }
    }

    private void processPlayPause(List<NormalizedLandmark> hand) {
        if (hand == null) {
            wasPlayGesture = false;
            wasPauseGesture = false;
            return;
        }
        boolean isFist = isFist(hand);
        if (isFist) {
            boolean thumbUp = isThumbUp(hand);
            boolean thumbDown = isThumbDown(hand);
            if (thumbUp && !wasPlayGesture) {
                if (listener != null) listener.onPlay();
                wasPlayGesture = true;
            } else if (!thumbUp) {
                wasPlayGesture = false;
            }
            if (thumbDown && !wasPauseGesture) {
                if (listener != null) listener.onPause();
                wasPauseGesture = true;
            } else if (!thumbDown) {
                wasPauseGesture = false;
            }
        } else {
            wasPlayGesture = false;
            wasPauseGesture = false;
        }
    }

    private void processNextPrev(List<NormalizedLandmark> left, List<NormalizedLandmark> right) {
        if (left == null || right == null) {
            wasNextGesture = false;
            wasPrevGesture = false;
            return;
        }
        boolean leftOpen = isHandOpen(left);
        boolean rightOpen = isHandOpen(right);
        boolean leftClosed = !leftOpen;
        boolean rightClosed = !rightOpen;

        if (leftOpen && rightClosed && !wasNextGesture) {
            if (listener != null) listener.onNext();
            wasNextGesture = true;
        } else {
            wasNextGesture = false;
        }

        if (rightOpen && leftClosed && !wasPrevGesture) {
            if (listener != null) listener.onPrevious();
            wasPrevGesture = true;
        } else {
            wasPrevGesture = false;
        }
    }

    private boolean isFist(List<NormalizedLandmark> landmarks) {
        final float TIP_TO_BASE_THRESHOLD = 0.05f;
        boolean indexFolded = distance(landmarks.get(INDEX_TIP), landmarks.get(INDEX_MCP)) < TIP_TO_BASE_THRESHOLD;
        boolean middleFolded = distance(landmarks.get(MIDDLE_TIP), landmarks.get(MIDDLE_MCP)) < TIP_TO_BASE_THRESHOLD;
        boolean ringFolded = distance(landmarks.get(RING_TIP), landmarks.get(RING_MCP)) < TIP_TO_BASE_THRESHOLD;
        boolean pinkyFolded = distance(landmarks.get(PINKY_TIP), landmarks.get(PINKY_MCP)) < TIP_TO_BASE_THRESHOLD;
        return indexFolded && middleFolded && ringFolded && pinkyFolded;
    }

    private boolean isThumbUp(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark thumbTip = landmarks.get(THUMB_TIP);
        NormalizedLandmark thumbBase = landmarks.get(THUMB_MCP);
        // In normalized coordinates, y increases downward. "Up" means smaller y.
        return thumbTip.y() < thumbBase.y() - 0.02f;
    }

    private boolean isThumbDown(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark thumbTip = landmarks.get(THUMB_TIP);
        NormalizedLandmark thumbBase = landmarks.get(THUMB_MCP);
        return thumbTip.y() > thumbBase.y() + 0.02f;
    }

    private boolean isHandOpen(List<NormalizedLandmark> landmarks) {
        final float TIP_TO_BASE_OPEN_THRESHOLD = 0.08f;
        boolean indexExtended = distance(landmarks.get(INDEX_TIP), landmarks.get(INDEX_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        boolean middleExtended = distance(landmarks.get(MIDDLE_TIP), landmarks.get(MIDDLE_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        boolean ringExtended = distance(landmarks.get(RING_TIP), landmarks.get(RING_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        boolean pinkyExtended = distance(landmarks.get(PINKY_TIP), landmarks.get(PINKY_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        return indexExtended && middleExtended && ringExtended && pinkyExtended;
    }

    private float distance(NormalizedLandmark a, NormalizedLandmark b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float dz = a.z() - b.z();
        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private Ray getFingerTipRay(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark tip = landmarks.get(INDEX_TIP);
        float screenX = tip.x() * Gdx.graphics.getWidth();
        float screenY = tip.y() * Gdx.graphics.getHeight();
        screenY = Gdx.graphics.getHeight() - screenY; // invert Y for LibGDX
        return camera.getPickRay(screenX, screenY);
    }

    private ButtonType findTouchedButton(Ray ray) {
        Vector3 intersection = new Vector3();
        float closestDist = Float.MAX_VALUE;
        ButtonType hitType = null;

        for (Map.Entry<ButtonType, ModelInstance> entry : buttonModels.entrySet()) {
            ModelInstance instance = entry.getValue();
            Vector3 center = instance.transform.getTranslation(new Vector3());
            float radius = 0.5f; // approximate – adjust based on actual button size
            if (Intersector.intersectRaySphere(ray, center, radius, intersection)) {
                float dist = intersection.dst(ray.origin);
                if (dist < closestDist) {
                    closestDist = dist;
                    hitType = entry.getKey();
                }
            }
        }
        return hitType;
    }

    public void setButtonModels(Map<ButtonType, ModelInstance> buttons) {
        this.buttonModels.clear();
        this.buttonModels.putAll(buttons);
    }

    private void resetGestureStates() {
        wasPlayGesture = false;
        wasPauseGesture = false;
        wasNextGesture = false;
        wasPrevGesture = false;
    }
}
