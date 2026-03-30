package com.ns.dev.jdkhandlookingdeep;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.List;

public class CarouselRenderer {

    private ModelBatch modelBatch;
    private Environment environment;
    private PerspectiveCamera camera;
    private List<CarouselItem> items = new ArrayList<>();
    private float radius = 2.5f;      // radius of the carousel circle
    private float currentAngle = 0f;   // carousel rotation in radians
    private float targetAngle = 0f;
    private float rotationSpeed = 2f;   // rad per second

    public CarouselRenderer(PerspectiveCamera camera) {
        this.camera = camera;
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -0.5f, -1f, -0.5f));
    }

    public void setItems(List<MediaItem> mediaList) {
        items.clear();
        ModelBuilder builder = new ModelBuilder();
        for (int i = 0; i < mediaList.size(); i++) {
            MediaItem media = mediaList.get(i);
            // Create a simple box with a texture (you can load album art or thumbnail)
            Texture texture = new Texture(Gdx.files.internal("placeholder.png")); // placeholder
            Material material = new Material(
                    TextureAttribute.createDiffuse(texture),
                    ColorAttribute.createSpecular(1, 1, 1, 1)
            );
            Model model = builder.createBox(1.2f, 1.2f, 0.1f, material,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
            CarouselItem item = new CarouselItem();
            item.media = media;
            item.model = new ModelInstance(model);
            // Compute initial angle evenly spaced
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
            // Optional: rotate each item to face center
            item.model.transform.rotate(Vector3.Y, (float) Math.toDegrees(effectiveAngle));
        }
    }

    public void update(float delta) {
        // Smoothly rotate carousel toward target angle
        float diff = targetAngle - currentAngle;
        float maxDelta = rotationSpeed * delta;
        if (Math.abs(diff) > maxDelta) {
            currentAngle += Math.signum(diff) * maxDelta;
        } else {
            currentAngle = targetAngle;
        }
        updatePositions();
    }

    public void rotate(float deltaAngle) {
        // deltaAngle in radians
        targetAngle += deltaAngle;
        // Keep within 0..2π to avoid large numbers
        while (targetAngle > Math.PI * 2) targetAngle -= Math.PI * 2;
        while (targetAngle < 0) targetAngle += Math.PI * 2;
    }

    public void render() {
        modelBatch.begin(camera);
        for (CarouselItem item : items) {
            modelBatch.render(item.model, environment);
        }
        modelBatch.end();
    }

    public void dispose() {
        modelBatch.dispose();
        for (CarouselItem item : items) {
            item.model.model.dispose();
        }
    }
}
