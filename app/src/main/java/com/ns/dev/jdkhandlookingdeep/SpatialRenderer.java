package com.ns.dev.jdkhandlookingdeep;

import android.content.Context;
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
import java.util.List;      // <-- ADDED MISSING IMPORT
import java.util.Map;

public class SpatialRenderer implements ApplicationListener {

    private static final String TAG = "SpatialRenderer";
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private Map<GestureController.ButtonType, ModelInstance> buttonModels;
    private CarouselRenderer carousel;
    private ModelInstance curvedScreen;
    private ModelInstance vinylRecord;
    private ModelInstance currentMediaModel;
    private PointLight focusLight;
    private boolean isAudioMode = true;
    private float rotationAngle = 0f;
    private boolean mediaLoaded = false;
    private String currentMediaPath = null;
    private ExoPlayer exoPlayer;
    private boolean isPlaying = false;
    private Context androidContext;
    private GestureController gestureController;

    public interface CameraReadyCallback {
        void onCameraReady(PerspectiveCamera camera);
    }
    private CameraReadyCallback cameraReadyCallback;

    public SpatialRenderer(Context context) {
        this.androidContext = context;
    }

    @Override
    public void create() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();
        camera = new PerspectiveCamera(67, screenWidth, screenHeight);
        camera.position.set(0, 1.5f, 3.5f);
        camera.lookAt(0, 1, 0);
        camera.near = 0.1f;
        camera.far = 100f;
        camera.update();

        if (cameraReadyCallback != null) cameraReadyCallback.onCameraReady(camera);

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.4f, 1f));
        DirectionalLight ambientLight = new DirectionalLight();
        ambientLight.setColor(0.5f, 0.5f, 0.6f, 1f);
        ambientLight.setDirection(-0.5f, -1f, -0.5f);
        environment.add(ambientLight);

        focusLight = new PointLight();
        focusLight.setColor(1f, 0.8f, 0.6f, 1f);
        focusLight.setIntensity(1.5f);
        environment.add(focusLight);

        createGlassButtons();
        carousel = new CarouselRenderer(camera);
        carousel.setVisible(true);
        createCurvedScreen();
        createVinylRecord();
        setMode(true);
        initExoPlayer();
    }

    private void initExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(androidContext).build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) isPlaying = false;
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
                new BlendingAttribute()
        );
        Vector3[] positions = {
                new Vector3(-1.2f, 0.8f, 1.2f),
                new Vector3(0f, 0.8f, 1.2f),
                new Vector3(1.2f, 0.8f, 1.2f),
                new Vector3(1.2f, 0.2f, 1.2f)
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
                new FloatAttribute(FloatAttribute.Shininess, 32f)
        );
        Model discModel = builder.createCylinder(1.4f, 0.05f, 1.4f, 32, discMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        vinylRecord = new ModelInstance(discModel);
        vinylRecord.transform.setTranslation(0, 0.2f, -1.5f);
    }

    private void createCurvedScreen() {
        Texture placeholder = new Texture(Gdx.files.internal("media_placeholder.png"));
        Material screenMaterial = new Material(
                TextureAttribute.createDiffuse(placeholder),
                ColorAttribute.createSpecular(0.8f, 0.8f, 0.8f, 1)
        );
        ModelBuilder builder = new ModelBuilder();
        Model screenModel = builder.createBox(2.5f, 1.5f, 0.05f, screenMaterial,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
        curvedScreen = new ModelInstance(screenModel);
        curvedScreen.transform.setTranslation(0, 0.5f, -1.8f);
    }

    public void setMode(boolean audio) {
        isAudioMode = audio;
        currentMediaModel = audio ? vinylRecord : curvedScreen;
    }

    public void setMediaList(List<com.ns.dev.jdkhandlookingdeep.MediaItem> items) {
        carousel.setItems(items);
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

    public void mediaPlay() { if (exoPlayer != null && currentMediaPath != null) exoPlayer.play(); }
    public void mediaPause() { if (exoPlayer != null) exoPlayer.pause(); }
    public void mediaNext() { }
    public void mediaPrevious() { }
    public void setCarouselTargetAngle(float angle) { if (carousel != null) carousel.setTargetAngle(angle); }

    public void loadMedia(String filePath) {
        currentMediaPath = filePath;
        Uri uri = Uri.parse(filePath);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
        isPlaying = true;
        setMode(!filePath.toLowerCase().endsWith(".mp4"));
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);
        carousel.update(Gdx.graphics.getDeltaTime());
        if (isAudioMode && mediaLoaded) {
            rotationAngle += Gdx.graphics.getDeltaTime() * 60;
            vinylRecord.transform.setToRotation(0, 1, 0, rotationAngle);
        }
        modelBatch.begin(camera);
        carousel.render();
        for (ModelInstance instance : buttonModels.values()) modelBatch.render(instance, environment);
        if (currentMediaModel != null) modelBatch.render(currentMediaModel, environment);
        modelBatch.end();
        simulateHandTracking();
    }

    private void simulateHandTracking() {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        float x = (mouseX / Gdx.graphics.getWidth() - 0.5f) * 3.5f;
        float y = (1 - mouseY / Gdx.graphics.getHeight() - 0.5f) * 2.5f + 0.8f;
        float z = 1.2f;
        focusLight.setPosition(x, y, z);
    }

    @Override public void resize(int width, int height) { camera.viewportWidth = width; camera.viewportHeight = height; camera.update(); }
    @Override public void dispose() { modelBatch.dispose(); carousel.dispose(); for (ModelInstance i : buttonModels.values()) i.model.dispose(); if (vinylRecord != null) vinylRecord.model.dispose(); if (curvedScreen != null) curvedScreen.model.dispose(); if (exoPlayer != null) exoPlayer.release(); }
    @Override public void pause() { }
    @Override public void resume() { }
}
