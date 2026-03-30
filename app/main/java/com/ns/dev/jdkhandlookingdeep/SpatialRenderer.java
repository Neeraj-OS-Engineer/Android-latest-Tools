package com.ns.dev.jdkhandlookingdeep;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.Material;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * SpatialRenderer – The main 3D UI for the Apple Vision Pro‑inspired media player.
 * Implements glass‑morphic 3D icons, a point light that follows hand tracking,
 * and two modes: Audio (spinning vinyl) and Video (curved cinema screen).
 */
public class SpatialRenderer implements ApplicationListener {

    private static final String TAG = "SpatialRenderer";

    // ---- 3D rendering objects ----
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private CameraInputController cameraController;

    // ---- Models and instances ----
    private Map<ButtonType, ModelInstance> buttonInstances;
    private ModelInstance vinylRecord;          // Spinning vinyl for audio mode
    private ModelInstance curvedScreen;         // Curved screen for video mode
    private ModelInstance currentMediaModel;    // Active media model (vinyl or screen)

    // ---- Lighting ----
    private PointLight focusLight;
    private DirectionalLight ambientLight;

    // ---- Tracking and state ----
    private Vector3 trackedPosition = new Vector3(0, 0, 2); // simulated hand position
    private boolean isAudioMode = true;                     // true = audio, false = video
    private float rotationAngle = 0f;                       // for vinyl rotation
    private boolean mediaLoaded = false;                    // flag if media is ready

    // ---- Android context & media loading ----
    private Context androidContext;
    private ContentResolver contentResolver;
    private Uri currentMediaUri;            // URI of selected media

    // ---- Button types ----
    private enum ButtonType {
        PLAY_PAUSE, NEXT, VOLUME_UP, VOLUME_DOWN
    }

    // ---- Constructor receives Android context (from activity) ----
    public SpatialRenderer(Context context) {
        this.androidContext = context;
        this.contentResolver = context.getContentResolver();
    }

    @Override
    public void create() {
        // 1. Set up camera
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 1.5f, 3);
        camera.lookAt(0, 1, 0);
        camera.near = 0.1f;
        camera.far = 100f;
        camera.update();

        // 2. Model batch and environment
        modelBatch = new ModelBatch();
        environment = new Environment();

        // Ambient light (soft overall lighting)
        ambientLight = new DirectionalLight();
        ambientLight.setColor(0.3f, 0.3f, 0.4f, 1f);
        ambientLight.setDirection(-1f, -1f, -0.5f);
        environment.add(ambientLight);

        // Focus light – will follow hand position
        focusLight = new PointLight();
        focusLight.setColor(1f, 0.8f, 0.6f, 1f);
        focusLight.setIntensity(1.5f);
        environment.add(focusLight);

        // 3. Create glass‑morphic 3D icons
        createGlassIcons();

        // 4. Create audio and video models
        createVinylRecord();
        createCurvedScreen();
        setMode(isAudioMode); // start with audio mode

        // 5. Camera controller (optional – for debugging)
        cameraController = new CameraInputController(camera);
        Gdx.input.setInputProcessor(cameraController);
    }

    /**
     * Builds semi‑transparent, glass‑like icons using cubes with reflective/transparent material.
     * Positions them in a floating ring around the user.
     */
    private void createGlassIcons() {
        ModelBuilder modelBuilder = new ModelBuilder();
        buttonInstances = new HashMap<>();

        // Common glass material: transparent, slightly reflective, with specular highlights
        Material glassMaterial = new Material(
                ColorAttribute.createDiffuse(1, 1, 1, 0.6f),   // diffuse with alpha
                ColorAttribute.createSpecular(1, 1, 1, 1f),
                FloatAttribute.createAlphaTest(0.1f),
                BlendingAttribute.Translucent
        );

        // Define button positions in a semicircle (radius = 1.5, y = 1.2)
        Vector3[] positions = {
                new Vector3(-1.2f, 1.2f, 0.8f), // Play/Pause
                new Vector3(0, 1.2f, 0.8f),     // Next
                new Vector3(1.2f, 1.2f, 0.8f)   // Volume (two separate buttons? we'll combine)
        };
        // We'll create two volume buttons: one above, one below the Volume icon
        // Simpler: create four icons at distinct positions

        // Reset positions for four buttons
        positions = new Vector3[]{
                new Vector3(-1.5f, 1.2f, 1.0f), // Play/Pause
                new Vector3(0f, 1.2f, 1.0f),    // Next
                new Vector3(1.5f, 1.2f, 1.0f),  // Volume Up
                new Vector3(1.5f, 0.6f, 1.0f)   // Volume Down
        };

        ButtonType[] types = {
                ButtonType.PLAY_PAUSE, ButtonType.NEXT,
                ButtonType.VOLUME_UP, ButtonType.VOLUME_DOWN
        };

        for (int i = 0; i < types.length; i++) {
            // Use a rounded cube or sphere – here a simple cube with custom size
            Model model = modelBuilder.createBox(0.6f, 0.6f, 0.2f, glassMaterial,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
            ModelInstance instance = new ModelInstance(model);
            instance.transform.setTranslation(positions[i]);
            buttonInstances.put(types[i], instance);
        }

        // Add small glowing spheres around each button for extra effect (optional)
        // ...
    }

    /**
     * Creates a 3D vinyl record model (cylinder with texture).
     * We'll use a simple disc with a label texture (could be loaded later).
     */
    private void createVinylRecord() {
        ModelBuilder builder = new ModelBuilder();
        // Disc material: black glossy
        Material discMaterial = new Material(
                ColorAttribute.createDiffuse(0.1f, 0.1f, 0.1f, 1),
                ColorAttribute.createSpecular(0.5f, 0.5f, 0.5f, 1),
                FloatAttribute.createShininess(32f)
        );
        // Center label (red)
        Material labelMaterial = new Material(
                ColorAttribute.createDiffuse(0.8f, 0.2f, 0.2f, 1),
                ColorAttribute.createSpecular(0.2f, 0.2f, 0.2f, 1)
        );

        // Main disc cylinder: radius 0.8, height 0.05
        Model discModel = builder.createCylinder(1.6f, 0.05f, 1.6f, 32, discMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        vinylRecord = new ModelInstance(discModel);
        vinylRecord.transform.setTranslation(0, 0.5f, -1.2f);

        // Add label as a smaller cylinder on top
        Model labelModel = builder.createCylinder(0.6f, 0.02f, 0.6f, 32, labelMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ModelInstance label = new ModelInstance(labelModel);
        label.transform.setTranslation(0, 0.53f, -1.2f);
        // We'll attach label as part of vinylRecord for rotation (but can't directly; we'll update both)
        // For simplicity, we'll just render both separately in render().
    }

    /**
     * Creates a curved cinema screen using a cylinder with a texture (the video content).
     * We'll use a part of a cylinder (180 degrees) and map a texture to it.
     */
    private void createCurvedScreen() {
        ModelBuilder builder = new ModelBuilder();
        // Curved screen: a cylinder with a custom texture applied via UV mapping.
        // We'll create a cylinder with 90° arc and apply a placeholder texture.
        // For simplicity, we'll create a large flat rectangle with a video texture.
        // A proper curved screen would require a custom mesh, but here's a simple approximation.

        // Material with a placeholder texture (will be replaced when video loads)
        Texture placeholderTex = new Texture(Gdx.files.internal("placeholder.png")); // you'll need a placeholder
        Material screenMaterial = new Material(
                TextureAttribute.createDiffuse(placeholderTex),
                ColorAttribute.createSpecular(0.8f, 0.8f, 0.8f, 1)
        );

        // Use a flat panel for now – to be replaced with a curved mesh
        Model screenModel = builder.createBox(3.0f, 1.8f, 0.05f, screenMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
        curvedScreen = new ModelInstance(screenModel);
        curvedScreen.transform.setTranslation(0, 0.8f, -2.0f);
    }

    /**
     * Switches between Audio Mode (spinning vinyl) and Video Mode (curved screen).
     */
    private void setMode(boolean audio) {
        isAudioMode = audio;
        if (audio) {
            currentMediaModel = vinylRecord;
        } else {
            currentMediaModel = curvedScreen;
        }
        // If media is loaded, update textures accordingly (e.g., album art / video frame)
        if (mediaLoaded) {
            updateMediaTexture();
        }
    }

    /**
     * Loads a media file from local storage using ContentResolver and sets up ExoPlayer.
     * This method is called when user selects a file (e.g., from file picker).
     * @param uri The URI of the selected media
     */
    public void loadMedia(Uri uri) {
        this.currentMediaUri = uri;
        // Get the actual file path or stream for ExoPlayer
        String filePath = getPathFromUri(uri);
        if (filePath != null) {
            // Pass the file path to ExoPlayer (which is managed elsewhere)
            // We'll assume a callback to the main activity to start playback.
            // For now, just set a flag and update texture if possible.
            mediaLoaded = true;
            updateMediaTexture();
        } else {
            Log.e(TAG, "Failed to resolve URI: " + uri);
        }
    }

    /**
     * Resolves a content URI to a file path (for local files).
     * Works for MediaStore and file‑based URIs.
     */
    private String getPathFromUri(Uri uri) {
        if (uri.getScheme() == null) return null;
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return uri.getPath();
        }
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String[] projection = {MediaStore.MediaColumns.DATA};
            try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    return cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error resolving URI", e);
            }
        }
        return null;
    }

    /**
     * Updates the texture of the active media model based on the loaded media.
     * For audio mode, it could set album art; for video mode, it would set the video frame.
     * This is a placeholder – actual texture update depends on the media player.
     */
    private void updateMediaTexture() {
        if (currentMediaModel == null) return;
        // In a real implementation, you'd extract a frame from the video or album art from the audio file.
        // For demonstration, we'll just set a dummy texture.
        Texture dummy = new Texture(Gdx.files.internal("media_placeholder.png"));
        if (isAudioMode && vinylRecord != null) {
            // Apply texture to vinyl label or disc surface
            // (Requires modifying the model's material; simpler: create a new model instance)
        } else if (!isAudioMode && curvedScreen != null) {
            // Replace screen texture with video frame
            // curvedScreen.materials.get(0).set(TextureAttribute.createDiffuse(dummy));
        }
    }

    /**
     * Updates the position of the focus light to follow the tracked hand/finger.
     * This method should be called with the latest hand landmark position (from MediaPipe).
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     */
    public void updateFocusPosition(float x, float y, float z) {
        trackedPosition.set(x, y, z);
        focusLight.setPosition(trackedPosition);
    }

    @Override
    public void render() {
        // Clear screen with dark background (glassmorphic effect)
        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        // Update camera (if controller is used)
        cameraController.update();

        // Update focus light position (simulate hand following mouse for demonstration)
        // In production, this would be driven by MediaPipe hand tracking.
        simulateHandTracking();

        // Update vinyl rotation in audio mode
        if (isAudioMode && mediaLoaded) {
            rotationAngle += Gdx.graphics.getDeltaTime() * 60; // speed in degrees/sec
            vinylRecord.transform.setRotation(0, 1, 0, rotationAngle);
            // Also rotate the label if we had separate instance
        }

        // Render all models
        modelBatch.begin(camera);
        // Draw environment (lights etc are automatically applied if environment is set)
        modelBatch.render(buttonInstances.values().toArray(), environment);
        if (currentMediaModel != null) {
            modelBatch.render(currentMediaModel, environment);
        }
        // If we had a separate label for vinyl, render it
        if (isAudioMode && vinylRecord != null) {
            // For simplicity, we already rendered vinylRecord as currentMediaModel
        }
        modelBatch.end();
    }

    /**
     * Simulates hand tracking for demo purposes – makes the light follow the mouse.
     * Replace this with actual MediaPipe hand landmark data.
     */
    private void simulateHandTracking() {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        // Convert screen coordinates to world coordinates (approximate)
        Vector3 worldPos = new Vector3();
        camera.unproject(new Vector3(mouseX, mouseY, 0));
        // Keep within reasonable bounds
        float x = (mouseX / Gdx.graphics.getWidth() - 0.5f) * 4;
        float y = (1 - mouseY / Gdx.graphics.getHeight() - 0.5f) * 3 + 1;
        float z = 1.5f;
        updateFocusPosition(x, y, z);
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        // Dispose models, textures, etc.
        for (ModelInstance instance : buttonInstances.values()) {
            instance.model.dispose();
        }
        if (vinylRecord != null) vinylRecord.model.dispose();
        if (curvedScreen != null) curvedScreen.model.dispose();
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}
}
