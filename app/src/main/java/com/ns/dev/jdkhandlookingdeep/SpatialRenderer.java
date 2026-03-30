package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main LibGDX renderer. Creates the 3D environment, glass‑morphic buttons,
 * carousel, and video screen. Integrates with hand tracking and media playback.
 */
public class SpatialRenderer implements ApplicationListener {

    private static final String TAG = "SpatialRenderer";

    // 3D rendering
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;

    // Models and instances
    private Map<GestureController.ButtonType, ModelInstance> buttonModels;
    private CarouselRenderer carousel;
    private ModelInstance curvedScreen;
    private ModelInstance vinylRecord;          // for audio mode
    private ModelInstance currentMediaModel;    // active model (vinyl or screen)

    // Lighting
    private PointLight focusLight;

    // State
    private boolean isAudioMode = true;
    private float rotationAngle = 0f;            // for vinyl spin
    private boolean mediaLoaded = false;
    private String currentMediaPath = null;
    private ExoPlayer exoPlayer;
    private SurfaceTexture videoSurfaceTexture;
    private Texture videoTexture;                // LibGDX texture from SurfaceTexture
    private boolean isPlaying = false;

    // Hand tracking & gestures
    private GestureController gestureController;

    // Android context (for media loading)
    private Context androidContext;

    // Listener to receive camera once it's ready (for GestureController)
    public interface CameraReadyCallback {
        void onCameraReady(PerspectiveCamera camera);
    }
    private CameraReadyCallback cameraReadyCallback;

    public SpatialRenderer(Context context) {
        this.androidContext = context;
    }

    @Override
    public void create() {
        // Camera setup
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 1.5f, 3);
        camera.lookAt(0, 1, 0);
        camera.near = 0.1f;
        camera.far = 100f;
        camera.update();

        // Notify any waiting callback (for GestureController)
        if (cameraReadyCallback != null) cameraReadyCallback.onCameraReady(camera);

        // Model batch and environment
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.4f, 1f));
        DirectionalLight ambientLight = new DirectionalLight();
        ambientLight.setColor(0.5f, 0.5f, 0.6f, 1f);
        ambientLight.setDirection(-0.5f, -1f, -0.5f);
        environment.add(ambientLight);

        // Focus light (will follow hand)
        focusLight = new PointLight();
        focusLight.setColor(1f, 0.8f, 0.6f, 1f);
        focusLight.setIntensity(1.5f);
        environment.add(focusLight);

        // Create glass‑morphic buttons
        createGlassButtons();

        // Create carousel
        carousel = new CarouselRenderer(camera);
        carousel.setVisible(true); // will be shown when media list is set

        // Create video screen and vinyl record
        createCurvedScreen();
        createVinylRecord();

        // Start with audio mode (vinyl)
        setMode(true);

        // Initialize ExoPlayer
        initExoPlayer();
    }

    private void initExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(androidContext).build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    // Video will start playing; we'll handle texture update in render()
                } else if (playbackState == Player.STATE_ENDED) {
                    // Loop or stop? We'll just stop for now.
                    isPlaying = false;
                }
            }
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "ExoPlayer error", error);
            }
        });
    }

    private void createGlassButtons() {
        ModelBuilder builder = new ModelBuilder();
        buttonModels = new HashMap<>();

        Material glassMaterial = new Material(
                ColorAttribute.createDiffuse(1, 1, 1, 0.6f),
                ColorAttribute.createSpecular(1, 1, 1, 1f),
                new BlendingAttribute(true),                      // enable blending for transparency
                FloatAttribute.createAlphaTest(0.1f)              // discard fragments with alpha < 0.1
        );

        // Define positions for four buttons (play/pause, next, volume up, volume down)
        Vector3[] positions = {
                new Vector3(-1.5f, 1.2f, 1.0f), // Play/Pause
                new Vector3(0f, 1.2f, 1.0f),    // Next
                new Vector3(1.5f, 1.2f, 1.0f),  // Volume Up
                new Vector3(1.5f, 0.6f, 1.0f)   // Volume Down
        };
        GestureController.ButtonType[] types = {
                GestureController.ButtonType.PLAY_PAUSE,
                GestureController.ButtonType.NEXT,
                GestureController.ButtonType.VOLUME_UP,
                GestureController.ButtonType.VOLUME_DOWN
        };

        for (int i = 0; i < types.length; i++) {
            Model model = builder.createBox(0.6f, 0.6f, 0.2f, glassMaterial,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
            ModelInstance instance = new ModelInstance(model);
            instance.transform.setTranslation(positions[i]);
            buttonModels.put(types[i], instance);
        }
    }

    private void createVinylRecord() {
        ModelBuilder builder = new ModelBuilder();
        Material discMaterial = new Material(
                ColorAttribute.createDiffuse(0.1f, 0.1f, 0.1f, 1),
                ColorAttribute.createSpecular(0.5f, 0.5f, 0.5f, 1),
                FloatAttribute.createShininess(32f)
        );
        // Main disc: cylinder
        Model discModel = builder.createCylinder(1.6f, 0.05f, 1.6f, 32, discMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        vinylRecord = new ModelInstance(discModel);
        vinylRecord.transform.setTranslation(0, 0.5f, -1.2f);
    }

    private void createCurvedScreen() {
        // For now, create a simple flat screen with a placeholder texture.
        // Later we can replace with a curved mesh.
        Texture placeholder = new Texture(Gdx.files.internal("media_placeholder.png"));
        Material screenMaterial = new Material(
                TextureAttribute.createDiffuse(placeholder),
                ColorAttribute.createSpecular(0.8f, 0.8f, 0.8f, 1)
        );
        ModelBuilder builder = new ModelBuilder();
        Model screenModel = builder.createBox(3.0f, 1.8f, 0.05f, screenMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
        curvedScreen = new ModelInstance(screenModel);
        curvedScreen.transform.setTranslation(0, 0.8f, -2.0f);
    }

    public void setMode(boolean audio) {
        isAudioMode = audio;
        if (audio) {
            currentMediaModel = vinylRecord;
        } else {
            currentMediaModel = curvedScreen;
        }
        // Update textures if needed (e.g., show video frame)
    }

    public void setMediaList(List<MediaItem> items) {
        carousel.setItems(items);
        // Optionally select first item or show carousel
    }

    public void setOnCameraReadyCallback(CameraReadyCallback callback) {
        this.cameraReadyCallback = callback;
        if (camera != null) callback.onCameraReady(camera);
    }

    public Map<GestureController.ButtonType, ModelInstance> getButtonModels() {
        return buttonModels;
    }

    public void setGestureController(GestureController controller) {
        this.gestureController = controller;
        if (buttonModels != null) controller.setButtonModels(buttonModels);
    }

    // Media control methods called by GestureListener
    public void mediaPlay() {
        if (exoPlayer != null && currentMediaPath != null) {
            exoPlayer.play();
            isPlaying = true;
        }
    }

    public void mediaPause() {
        if (exoPlayer != null) {
            exoPlayer.pause();
            isPlaying = false;
        }
    }

    public void mediaNext() {
        // For carousel selection: rotate to next item and play
        // This is a placeholder – implement according to your UI logic
    }

    public void mediaPrevious() {
        // Placeholder
    }

    public void setCarouselTargetAngle(float angle) {
        if (carousel != null) {
            carousel.setTargetAngle(angle);
        }
    }

    public void loadMedia(String filePath) {
        currentMediaPath = filePath;
        Uri uri = Uri.parse(filePath);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
        isPlaying = true;

        // Determine if audio or video based on MIME type? Here we assume video.
        // For audio, we would keep the vinyl spinning, not the screen.
        // For simplicity, we'll set mode based on file extension.
        if (filePath.toLowerCase().endsWith(".mp4")) {
            setMode(false); // video mode
            // Set up video texture: we need to get the SurfaceTexture from ExoPlayer.
            // For that we'd need to create a TextureView or SurfaceView and extract the SurfaceTexture.
            // This is complex; we'll leave it as a placeholder.
        } else {
            setMode(true); // audio mode
        }
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        // Update carousel rotation based on hand position (via GestureController)
        // The carousel is updated inside its own update() method, which we call with delta.
        carousel.update(Gdx.graphics.getDeltaTime());

        // Update vinyl rotation in audio mode
        if (isAudioMode && mediaLoaded) {
            rotationAngle += Gdx.graphics.getDeltaTime() * 60; // speed in deg/sec
            vinylRecord.transform.setToRotation(0, 1, 0, rotationAngle);
        }

        // Render all models
        modelBatch.begin(camera);
        // Render carousel (if visible)
        carousel.render();
        // Render glass buttons
        for (ModelInstance instance : buttonModels.values()) {
            modelBatch.render(instance, environment);
        }
        // Render active media model (vinyl or screen)
        if (currentMediaModel != null) {
            modelBatch.render(currentMediaModel, environment);
        }
        modelBatch.end();

        // Update focus light position (if gestureController provides hand position)
        // For now, we'll simulate with mouse (you can later replace with real hand position)
        simulateHandTracking();
    }

    private void simulateHandTracking() {
        // Placeholder: move light with mouse
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        float x = (mouseX / Gdx.graphics.getWidth() - 0.5f) * 4;
        float y = (1 - mouseY / Gdx.graphics.getHeight() - 0.5f) * 3 + 1;
        float z = 1.5f;
        focusLight.setPosition(x, y, z);
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
        carousel.dispose();
        for (ModelInstance instance : buttonModels.values()) {
            instance.model.dispose();
        }
        if (vinylRecord != null) vinylRecord.model.dispose();
        if (curvedScreen != null) curvedScreen.model.dispose();
        if (exoPlayer != null) exoPlayer.release();
    }

    @Override
    public void pause() { }
    @Override
    public void resume() { }
}
