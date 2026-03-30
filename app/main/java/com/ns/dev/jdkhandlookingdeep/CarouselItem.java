package com.ns.dev.jdkhandlookingdeep;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;

public class CarouselItem {
    public MediaItem media;
    public ModelInstance model;
    public Vector3 position = new Vector3();
    public float angle; // angle in radians around Y-axis
}
