package com.fluxsyum.cullify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class CullifyConfig {
    public enum CullingShape {
        SPHERE,
        CYLINDER,
        BOX,
        TRIANGLE,
        HEXAGON,
        STAR,
        SQUARE,
        CIRCLE
    }

    public static final Property<Boolean> ENABLED = new Property<>(true);
    public static final Property<CullingShape> CULLING_SHAPE = new Property<>(CullingShape.SPHERE);
    public static final Property<Boolean> CULL_GRASS = new Property<>(true);
    public static final Property<Boolean> CULL_FLOWERS = new Property<>(true);
    public static final Property<Boolean> CULL_OTHER_PLANTS = new Property<>(true);
    public static final Property<Integer> GRASS_CULL_DISTANCE = new Property<>(48);
    public static final Property<Integer> FLOWER_CULL_DISTANCE = new Property<>(48);
    public static final Property<Integer> OTHER_PLANT_CULL_DISTANCE = new Property<>(32);
    public static final Property<Boolean> DEBUG_MODE = new Property<>(false);
    public static final Property<Integer> LOD_DENSITY = new Property<>(100);
    public static final Property<Boolean> SMART_SCALE = new Property<>(false);
    public static final Property<Integer> TARGET_FPS = new Property<>(60);
    public static final Property<Boolean> LIGHT_AWARE_CULLING = new Property<>(false);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File getConfigFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve("cullify.json").toFile();
    }

    public static void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            if (json.has("enabled")) ENABLED.set(json.get("enabled").getAsBoolean());
            if (json.has("cullingShape")) {
                try {
                    CULLING_SHAPE.set(CullingShape.valueOf(json.get("cullingShape").getAsString().toUpperCase()));
                } catch (Exception ignored) {}
            }
            if (json.has("cullGrass")) CULL_GRASS.set(json.get("cullGrass").getAsBoolean());
            if (json.has("cullFlowers")) CULL_FLOWERS.set(json.get("cullFlowers").getAsBoolean());
            if (json.has("cullOtherPlants")) CULL_OTHER_PLANTS.set(json.get("cullOtherPlants").getAsBoolean());
            if (json.has("grassCullDistance")) GRASS_CULL_DISTANCE.set(json.get("grassCullDistance").getAsInt());
            if (json.has("flowerCullDistance")) FLOWER_CULL_DISTANCE.set(json.get("flowerCullDistance").getAsInt());
            if (json.has("otherPlantCullDistance")) OTHER_PLANT_CULL_DISTANCE.set(json.get("otherPlantCullDistance").getAsInt());
            if (json.has("debugMode")) DEBUG_MODE.set(json.get("debugMode").getAsBoolean());
            if (json.has("lodDensity")) LOD_DENSITY.set(json.get("lodDensity").getAsInt());
            if (json.has("smartScale")) SMART_SCALE.set(json.get("smartScale").getAsBoolean());
            if (json.has("targetFps")) TARGET_FPS.set(json.get("targetFps").getAsInt());
            if (json.has("lightAwareCulling")) LIGHT_AWARE_CULLING.set(json.get("lightAwareCulling").getAsBoolean());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        File file = getConfigFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("enabled", ENABLED.get());
            json.addProperty("cullingShape", CULLING_SHAPE.get().name());
            json.addProperty("cullGrass", CULL_GRASS.get());
            json.addProperty("cullFlowers", CULL_FLOWERS.get());
            json.addProperty("cullOtherPlants", CULL_OTHER_PLANTS.get());
            json.addProperty("grassCullDistance", GRASS_CULL_DISTANCE.get());
            json.addProperty("flowerCullDistance", FLOWER_CULL_DISTANCE.get());
            json.addProperty("otherPlantCullDistance", OTHER_PLANT_CULL_DISTANCE.get());
            json.addProperty("debugMode", DEBUG_MODE.get());
            json.addProperty("lodDensity", LOD_DENSITY.get());
            json.addProperty("smartScale", SMART_SCALE.get());
            json.addProperty("targetFps", TARGET_FPS.get());
            json.addProperty("lightAwareCulling", LIGHT_AWARE_CULLING.get());

            // Write to a temp file and move into place so a crash mid-write
            // can never leave a truncated/corrupted config behind
            File tmp = new File(file.getAbsolutePath() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(json, writer);
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Property<T> {
        private T value;

        public Property(T defaultValue) {
            this.value = defaultValue;
        }

        public T get() {
            return this.value;
        }

        public void set(T newValue) {
            this.value = newValue;
        }
    }
}
