package com.fluxsyum.cullify;

import net.minecraftforge.common.ForgeConfigSpec;

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

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.EnumValue<CullingShape> CULLING_SHAPE;
    public static final ForgeConfigSpec.BooleanValue CULL_GRASS;
    public static final ForgeConfigSpec.BooleanValue CULL_FLOWERS;
    public static final ForgeConfigSpec.BooleanValue CULL_OTHER_PLANTS;
    public static final ForgeConfigSpec.IntValue GRASS_CULL_DISTANCE;
    public static final ForgeConfigSpec.IntValue FLOWER_CULL_DISTANCE;
    public static final ForgeConfigSpec.IntValue OTHER_PLANT_CULL_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

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

        builder.pop();
        SPEC = builder.build();
    }

}

