package com.ns.dev.jdkhandlookingdeep;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.List;

public class MediaItem {
    private String title;
    private String path;
    private String mimeType;
    private long duration;
    private long size;

    public MediaItem(String title, String path, String mimeType, long duration, long size) {
        this.title = title;
        this.path = path;
        this.mimeType = mimeType;
        this.duration = duration;
        this.size = size;
    }

    // Getters and setters
    public String getTitle() { return title; }
    public String getPath() { return path; }
    public String getMimeType() { return mimeType; }
    public long getDuration() { return duration; }
    public long getSize() { return size; }

    // Static helper to load list from file
    public static List<MediaItem> loadFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<MediaItem>>(){}.getType();
            return new Gson().fromJson(reader, listType);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Static helper to save list to file
    public static void saveToFile(List<MediaItem> items, File file) {
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(items, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
