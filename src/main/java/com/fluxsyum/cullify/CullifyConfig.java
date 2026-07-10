package com.fluxsyum.cullify;

import net.neoforged.neoforge.common.ModConfigSpec;

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

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.EnumValue<CullingShape> CULLING_SHAPE;
    public static final ModConfigSpec.BooleanValue CULL_GRASS;
    public static final ModConfigSpec.BooleanValue CULL_FLOWERS;
    public static final ModConfigSpec.BooleanValue CULL_OTHER_PLANTS;
    public static final ModConfigSpec.IntValue GRASS_CULL_DISTANCE;
    public static final ModConfigSpec.IntValue FLOWER_CULL_DISTANCE;
    public static final ModConfigSpec.IntValue OTHER_PLANT_CULL_DISTANCE;
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;
    public static final ModConfigSpec.IntValue LOD_DENSITY;
    public static final ModConfigSpec.BooleanValue SMART_SCALE;
    public static final ModConfigSpec.IntValue TARGET_FPS;
    public static final ModConfigSpec.BooleanValue LIGHT_AWARE_CULLING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Cullify general configuration options").push("general");

        ENABLED = builder
                .comment("Enable or disable vegetation culling globally.")
                .define("enabled", true);

        CULLING_SHAPE = builder
                .comment("Shape of the culling boundary around the player (SPHERE, CYLINDER, BOX).")
                .defineEnum("cullingShape", CullingShape.SPHERE);

        CULL_GRASS = builder
                .comment("Enable culling of grass, ferns, and short grass.")
                .define("cullGrass", true);

        CULL_FLOWERS = builder
                .comment("Enable culling of flowers and double-height flowers.")
                .define("cullFlowers", true);

        CULL_OTHER_PLANTS = builder
                .comment("Enable culling of other decorative plants (dead bushes, kelp, seagrass, etc.).")
                .define("cullOtherPlants", true);

        GRASS_CULL_DISTANCE = builder
                .comment("Maximum distance in blocks to render grass, ferns, and tall grass.")
                .defineInRange("grassCullDistance", 48, 0, 512);

        FLOWER_CULL_DISTANCE = builder
                .comment("Maximum distance in blocks to render flowers and double-height flowers.")
                .defineInRange("flowerCullDistance", 48, 0, 512);

        OTHER_PLANT_CULL_DISTANCE = builder
                .comment("Maximum distance in blocks to render other decorative plants (dead bushes, kelp, seagrass, etc.).")
                .defineInRange("otherPlantCullDistance", 32, 0, 512);

        DEBUG_MODE = builder
                .comment("Enable debug overlay HUD to show culling stats and configuration status.")
                .define("debugMode", false);

        LOD_DENSITY = builder
                .comment("LOD density filter (0-100). 100 = keep all plants (disabled). Lower values thin out "
                        + "plants near the culling boundary using a deterministic hash — no flickering. "
                        + "50 = keep 50% of plants in the outer zone. Saves CPU at medium distances.")
                .defineInRange("lodDensity", 100, 0, 100);

        SMART_SCALE = builder
                .comment("When enabled, automatically reduces culling distances when FPS drops below TARGET_FPS, "
                        + "and slowly restores them when FPS recovers. Keeps frame rate stable in dense biomes.")
                .define("smartScale", false);

        TARGET_FPS = builder
                .comment("Target frames per second for the Smart Scale system. "
                        + "If current FPS falls below this, culling distances shrink to recover performance.")
                .defineInRange("targetFps", 60, 30, 240);

        LIGHT_AWARE_CULLING = builder
                .comment("When enabled, culling distances shrink in dark areas (forests, caves, night) "
                        + "proportionally to the ambient light level. Vegetation invisible in the dark is "
                        + "culled much sooner, saving CPU and GPU with no visual impact.")
                .define("lightAwareCulling", false);

        builder.pop();
        SPEC = builder.build();
    }

}

