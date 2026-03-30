package com.ns.dev.jdkhandlookingdeep;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a 3D carousel of media items (boxes with textures).
 * Rotates smoothly in response to hand‑wave gestures.
 */
public class CarouselRenderer {

    private ModelBatch modelBatch;
    private Environment environment;
    private PerspectiveCamera camera;
    private List<CarouselItem> items = new ArrayList<>();
    private float radius = 2.5f;          // radius of the carousel circle
    private float currentAngle = 0f;       // carousel rotation in radians
    private float targetAngle = 0f;
    private float rotationSpeed = 2f;       // rad per second
    private boolean visible = true;

    // Simple data class for each item
    private static class CarouselItem {
        MediaItem media;
        ModelInstance model;
        Vector3 position = new Vector3();
        float angle; // angle in radians around Y-axis
    }

    public CarouselRenderer(PerspectiveCamera camera) {
        this.camera = camera;
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -0.5f, -1f, -0.5f));
    }

    /**
     * Builds the carousel from a list of media items.
     * Each item gets a simple 3D box with a placeholder texture.
     */
    public void setItems(List<MediaItem> mediaList) {
        items.clear();
        ModelBuilder builder = new ModelBuilder();

        // Load a placeholder texture (you must have media_placeholder.png in assets)
        Texture placeholder = new Texture(Gdx.files.internal("media_placeholder.png"));

        for (int i = 0; i < mediaList.size(); i++) {
            MediaItem media = mediaList.get(i);

            // Material with diffuse texture
            Material material = new Material(
                    TextureAttribute.createDiffuse(placeholder),
                    ColorAttribute.createSpecular(1, 1, 1, 1),
                    FloatAttribute.createShininess(32f)
            );

            // Create a box (width, height, depth)
            Model model = builder.createBox(1.2f, 1.2f, 0.1f, material,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);

            CarouselItem item = new CarouselItem();
            item.media = media;
            item.model = new ModelInstance(model);
            // Evenly distribute items around the circle
            item.angle = (float) (2 * Math.PI * i / mediaList.size());
            items.add(item);
        }
        updatePositions();
    }

    private void updatePositions() {
        for (CarouselItem item : items) {
            float effectiveAngle = item.angle + currentAngle;
            float x = (float) Math.sin(effectiveAngle) * radius;
            float z = (float) Math.cos(effectiveAngle) * radius;
            item.position.set(x, 0, z);
            item.model.transform.setTranslation(item.position);
            // Optional: rotate each item to face the center
            item.model.transform.rotate(Vector3.Y, (float) Math.toDegrees(effectiveAngle));
        }
    }

    /**
     * Call this in the render loop to smoothly rotate towards the target angle.
     * @param delta time since last frame (seconds)
     */
    public void update(float delta) {
        if (!visible) return;
        float diff = targetAngle - currentAngle;
        float maxDelta = rotationSpeed * delta;
        if (Math.abs(diff) > maxDelta) {
            currentAngle += Math.signum(diff) * maxDelta;
        } else {
            currentAngle = targetAngle;
        }
        updatePositions();
    }

    /**
     * Rotates the carousel by a delta angle (radians). Typically called from hand gesture.
     * @param deltaAngle amount to rotate (positive = clockwise, negative = anticlockwise)
     */
    public void rotate(float deltaAngle) {
        targetAngle += deltaAngle;
        // Keep within 0..2π to avoid floating‑point drift
        while (targetAngle > Math.PI * 2) targetAngle -= Math.PI * 2;
        while (targetAngle < 0) targetAngle += Math.PI * 2;
    }

    /**
     * Directly set the target angle (e.g., from hand position mapping).
     * @param angle angle in radians
     */
    public void setTargetAngle(float angle) {
        targetAngle = angle;
    }

    public float getCurrentAngle() {
        return currentAngle;
    }

    public void render() {
        if (!visible) return;
        modelBatch.begin(camera);
        for (CarouselItem item : items) {
            modelBatch.render(item.model, environment);
        }
        modelBatch.end();
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void dispose() {
        modelBatch.dispose();
        for (CarouselItem item : items) {
            item.model.model.dispose();
        }
    }
}
