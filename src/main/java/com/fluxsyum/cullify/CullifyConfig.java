package com.fluxsyum.cullify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

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

    public static class ConfigValue<T> {
        private T value;

        public ConfigValue(T defaultValue) {
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public void set(T val) {
            this.value = val;
        }
    }

    public static final ConfigValue<Boolean> ENABLED = new ConfigValue<>(true);
    public static final ConfigValue<CullingShape> CULLING_SHAPE = new ConfigValue<>(CullingShape.SPHERE);
    public static final ConfigValue<Boolean> CULL_GRASS = new ConfigValue<>(true);
    public static final ConfigValue<Boolean> CULL_FLOWERS = new ConfigValue<>(true);
    public static final ConfigValue<Boolean> CULL_OTHER_PLANTS = new ConfigValue<>(true);
    public static final ConfigValue<Integer> GRASS_CULL_DISTANCE = new ConfigValue<>(48);
    public static final ConfigValue<Integer> FLOWER_CULL_DISTANCE = new ConfigValue<>(48);
    public static final ConfigValue<Integer> OTHER_PLANT_CULL_DISTANCE = new ConfigValue<>(32);
    public static final ConfigValue<Boolean> DEBUG_MODE = new ConfigValue<>(false);
    public static final ConfigValue<Integer> LOD_DENSITY = new ConfigValue<>(100);

    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "cullify.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                if (data.enabled != null) ENABLED.set(data.enabled);
                if (data.cullingShape != null) CULLING_SHAPE.set(data.cullingShape);
                if (data.cullGrass != null) CULL_GRASS.set(data.cullGrass);
                if (data.cullFlowers != null) CULL_FLOWERS.set(data.cullFlowers);
                if (data.cullOtherPlants != null) CULL_OTHER_PLANTS.set(data.cullOtherPlants);
                if (data.grassCullDistance != null) GRASS_CULL_DISTANCE.set(data.grassCullDistance);
                if (data.flowerCullDistance != null) FLOWER_CULL_DISTANCE.set(data.flowerCullDistance);
                if (data.otherPlantCullDistance != null) OTHER_PLANT_CULL_DISTANCE.set(data.otherPlantCullDistance);
                if (data.debugMode != null) DEBUG_MODE.set(data.debugMode);
                if (data.lodDensity != null) LOD_DENSITY.set(data.lodDensity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        ConfigData data = new ConfigData();
        data.enabled = ENABLED.get();
        data.cullingShape = CULLING_SHAPE.get();
        data.cullGrass = CULL_GRASS.get();
        data.cullFlowers = CULL_FLOWERS.get();
        data.cullOtherPlants = CULL_OTHER_PLANTS.get();
        data.grassCullDistance = GRASS_CULL_DISTANCE.get();
        data.flowerCullDistance = FLOWER_CULL_DISTANCE.get();
        data.otherPlantCullDistance = OTHER_PLANT_CULL_DISTANCE.get();
        data.debugMode = DEBUG_MODE.get();
        data.lodDensity = LOD_DENSITY.get();

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        Boolean enabled;
        CullingShape cullingShape;
        Boolean cullGrass;
        Boolean cullFlowers;
        Boolean cullOtherPlants;
        Integer grassCullDistance;
        Integer flowerCullDistance;
        Integer otherPlantCullDistance;
        Boolean debugMode;
        Integer lodDensity;
    }
}
