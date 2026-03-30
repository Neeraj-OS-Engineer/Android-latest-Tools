package com.ns.dev.jdkhandlookingdeep;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GestureController interprets hand landmarks from MediaPipe,
 * maps them to 3D world space, detects gestures, and triggers
 * media player commands.
 */
public class GestureController {

    // Listener interface for gesture events
    public interface GestureListener {
        void onPlay();
        void onPause();
        void onNext();
        void onPrevious();
        void onButtonTouch(ButtonType buttonType); // optional for air‑touch
    }

    // Button types (matching your renderer)
    public enum ButtonType {
        PLAY_PAUSE, NEXT, VOLUME_UP, VOLUME_DOWN
    }

    private Camera camera;
    private Map<ButtonType, ModelInstance> buttonModels; // for ray‑casting
    private GestureListener listener;

    // State for gesture edge detection
    private boolean wasPlayGesture = false;
    private boolean wasPauseGesture = false;
    private boolean wasNextGesture = false;
    private boolean wasPrevGesture = false;

    // For ray‑based button selection
    private ButtonType lastTouchedButton = null;

    // Landmark indices for fingers (MediaPipe hand landmarks)
    private static final int WRIST = 0;
    private static final int THUMB_CMC = 1;
    private static final int THUMB_MCP = 2;
    private static final int THUMB_IP = 3;
    private static final int THUMB_TIP = 4;
    private static final int INDEX_MCP = 5;
    private static final int INDEX_PIP = 6;
    private static final int INDEX_DIP = 7;
    private static final int INDEX_TIP = 8;
    private static final int MIDDLE_MCP = 9;
    private static final int MIDDLE_PIP = 10;
    private static final int MIDDLE_DIP = 11;
    private static final int MIDDLE_TIP = 12;
    private static final int RING_MCP = 13;
    private static final int RING_PIP = 14;
    private static final int RING_DIP = 15;
    private static final int RING_TIP = 16;
    private static final int PINKY_MCP = 17;
    private static final int PINKY_PIP = 18;
    private static final int PINKY_DIP = 19;
    private static final int PINKY_TIP = 20;

    public GestureController(Camera camera, GestureListener listener) {
        this.camera = camera;
        this.listener = listener;
        this.buttonModels = new HashMap<>();
    }

    /**
     * Call this every frame with the latest hand landmark results.
     * @param result MediaPipe HandLandmarkerResult containing one or two hands.
     */
    public void update(HandLandmarkerResult result) {
        if (result == null || result.landmarks().isEmpty()) {
            // No hands detected – reset gesture states
            resetGestureStates();
            return;
        }

        // There may be up to two hands: we need to identify left and right.
        // MediaPipe doesn't label handedness in the result object directly in the task API?
        // In the solution, you get a list of Handedness. We'll assume we have that info.
        // For simplicity, we'll pass left and right landmarks separately.
        // We'll implement a helper to separate them if needed.

        // For this example, we assume the caller has already separated left and right hands.
        // But in a real integration, you can iterate over result.landmarks() and result.handedness()
        // to get handedness.

        // We'll provide two separate methods for left and right, but for the sake of this class,
        // we'll accept the result and extract. However, to keep code focused, we'll assume the caller
        // passes leftHandLandmarks and rightHandLandmarks. For flexibility, we can include a method
        // that takes the full result and does the separation.

        // For now, we'll create a method that receives separate lists.
    }

    /**
     * Convenience method to update with separated hands.
     * @param leftLandmarks list of left hand landmarks (or null if not present)
     * @param rightLandmarks list of right hand landmarks (or null)
     */
    public void updateHands(List<NormalizedLandmark> leftLandmarks, List<NormalizedLandmark> rightLandmarks) {
        // Detect Play/Pause gesture (only on one hand, probably right hand for simplicity)
        if (rightLandmarks != null) {
            boolean isFist = isFist(rightLandmarks);
            if (isFist) {
                boolean thumbUp = isThumbUp(rightLandmarks);
                boolean thumbDown = isThumbDown(rightLandmarks);
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
        } else {
            // No right hand, reset those gesture states
            wasPlayGesture = false;
            wasPauseGesture = false;
        }

        // Detect Next/Previous gesture using both hands
        if (leftLandmarks != null && rightLandmarks != null) {
            boolean leftOpen = isHandOpen(leftLandmarks);
            boolean rightOpen = isHandOpen(rightLandmarks);
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
        } else {
            wasNextGesture = false;
            wasPrevGesture = false;
        }

        // Map the index finger tip to a ray for button interaction
        // Use the right hand if available, else left hand
        List<NormalizedLandmark> handForTouch = (rightLandmarks != null) ? rightLandmarks : leftLandmarks;
        if (handForTouch != null) {
            Ray ray = getFingerTipRay(handForTouch);
            ButtonType touched = findTouchedButton(ray);
            if (touched != null && touched != lastTouchedButton) {
                if (listener != null) listener.onButtonTouch(touched);
                lastTouchedButton = touched;
            } else if (touched == null) {
                lastTouchedButton = null;
            }
        }
    }

    /**
     * Checks if the hand is closed (fist). We consider a fist if all finger tips
     * are close to their respective bases (MCP joints).
     */
    private boolean isFist(List<NormalizedLandmark> landmarks) {
        // Define thresholds – these may need tuning based on actual landmark positions
        final float TIP_TO_BASE_THRESHOLD = 0.05f; // in normalized coordinates

        // Check index, middle, ring, pinky
        boolean indexFolded = distance(landmarks.get(INDEX_TIP), landmarks.get(INDEX_MCP)) < TIP_TO_BASE_THRESHOLD;
        boolean middleFolded = distance(landmarks.get(MIDDLE_TIP), landmarks.get(MIDDLE_MCP)) < TIP_TO_BASE_THRESHOLD;
        boolean ringFolded = distance(landmarks.get(RING_TIP), landmarks.get(RING_MCP)) < TIP_TO_BASE_THRESHOLD;
        boolean pinkyFolded = distance(landmarks.get(PINKY_TIP), landmarks.get(PINKY_MCP)) < TIP_TO_BASE_THRESHOLD;
        // Thumb may also be folded, but for play/pause we check thumb separately
        return indexFolded && middleFolded && ringFolded && pinkyFolded;
    }

    /**
     * Determines if the thumb is pointing up (tip y > base y, assuming hand orientation upright).
     * We use the difference in y coordinates between tip and base (MCP).
     * Since hand may be rotated, we could use a more robust method like comparing to wrist orientation,
     * but for simplicity we assume the hand is roughly upright.
     */
    private boolean isThumbUp(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark thumbTip = landmarks.get(THUMB_TIP);
        NormalizedLandmark thumbBase = landmarks.get(THUMB_MCP);
        // In normalized coordinates, y increases downward (0 at top). So "up" means lower y.
        return thumbTip.getY() < thumbBase.getY() - 0.02f; // threshold to avoid noise
    }

    private boolean isThumbDown(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark thumbTip = landmarks.get(THUMB_TIP);
        NormalizedLandmark thumbBase = landmarks.get(THUMB_MCP);
        return thumbTip.getY() > thumbBase.getY() + 0.02f;
    }

    /**
     * Detects if the hand is open. An open hand has all finger tips far from their bases.
     */
    private boolean isHandOpen(List<NormalizedLandmark> landmarks) {
        final float TIP_TO_BASE_OPEN_THRESHOLD = 0.08f;
        // Check all fingers except thumb (thumb is handled separately if needed)
        boolean indexExtended = distance(landmarks.get(INDEX_TIP), landmarks.get(INDEX_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        boolean middleExtended = distance(landmarks.get(MIDDLE_TIP), landmarks.get(MIDDLE_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        boolean ringExtended = distance(landmarks.get(RING_TIP), landmarks.get(RING_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        boolean pinkyExtended = distance(landmarks.get(PINKY_TIP), landmarks.get(PINKY_MCP)) > TIP_TO_BASE_OPEN_THRESHOLD;
        // Optionally check thumb – for open hand we might require thumb also extended, but we'll keep simple
        return indexExtended && middleExtended && ringExtended && pinkyExtended;
    }

    /**
     * Helper to compute Euclidean distance between two landmarks (in normalized coordinates).
     */
    private float distance(NormalizedLandmark a, NormalizedLandmark b) {
        float dx = a.getX() - b.getX();
        float dy = a.getY() - b.getY();
        float dz = a.getZ() - b.getZ();
        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    /**
     * Converts the index finger tip landmark to a world‑space ray.
     * @param landmarks list of hand landmarks (must contain at least INDEX_TIP)
     * @return Ray from camera through the finger tip point in world space.
     */
    private Ray getFingerTipRay(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark tip = landmarks.get(INDEX_TIP);
        // Convert normalized coordinates to screen coordinates (pixels)
        float screenX = tip.getX() * Gdx.graphics.getWidth();
        float screenY = tip.getY() * Gdx.graphics.getHeight();
        // LibGDX screen coordinate origin is bottom-left, but MediaPipe's y is top-left.
        // So we need to invert Y:
        screenY = Gdx.graphics.getHeight() - screenY;
        // Get the ray from the camera through this screen point
        return camera.getPickRay(screenX, screenY);
    }

    /**
     * Intersects the ray with all registered button models and returns the first hit.
     * @param ray the ray in world space
     * @return the ButtonType that was hit, or null if none.
     */
    private ButtonType findTouchedButton(Ray ray) {
        Vector3 intersection = new Vector3();
        float closestDist = Float.MAX_VALUE;
        ButtonType hitType = null;

        for (Map.Entry<ButtonType, ModelInstance> entry : buttonModels.entrySet()) {
            ModelInstance instance = entry.getValue();
            // Calculate bounding box (we assume each instance has a bounds)
            // For simplicity, we'll use the transform to get a bounding sphere or box.
            // We'll compute the bounding sphere from the model's dimensions.
            // Alternatively, use Intersector.intersectRayBoundsFast with a temporary bounding box.
            // Since ModelInstance doesn't have direct bounds, we can use instance.calculateBoundingBox(boundingBox) once.
            // For performance, we should pre‑compute bounding boxes per instance.
            // Here we'll do a simple sphere test.
            Vector3 center = instance.transform.getTranslation(new Vector3());
            float radius = 0.5f; // approximate, should be set per button
            float dist = Intersector.intersectRaySphere(ray, center, radius, intersection);
            if (dist != -1f && dist < closestDist) {
                closestDist = dist;
                hitType = entry.getKey();
            }
        }
        return hitType;
    }

    /**
     * Register button models for ray intersection.
     * @param buttons map of button types to their ModelInstance
     */
    public void setButtonModels(Map<ButtonType, ModelInstance> buttons) {
        this.buttonModels.clear();
        this.buttonModels.putAll(buttons);
    }

    private void resetGestureStates() {
        wasPlayGesture = false;
        wasPauseGesture = false;
        wasNextGesture = false;
        wasPrevGesture = false;
        lastTouchedButton = null;
    }
}
