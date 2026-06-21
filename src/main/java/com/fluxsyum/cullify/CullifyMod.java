package com.fluxsyum.cullify;

import com.fluxsyum.cullify.duck.CullifyBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CullifyMod.MOD_ID)
public class CullifyMod {
    public static final String MOD_ID = "cullify";

    // Config version to track settings changes
    public static volatile int configVersion = 0;

    public static void incrementConfigVersion() {
        configVersion++;
    }

    // Camera coordinates updated on client tick
    public static volatile double playerX = 0;
    public static volatile double playerY = 0;
    public static volatile double playerZ = 0;
    public static volatile boolean hasPlayer = false;

    public CullifyMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CullifyConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.fluxsyum.cullify.client.ClientModBusSubscriber.register(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }

    private void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // Detect Sodium/Embeddium using the same logic as the mixin plugin.
        // The mixin plugin already ran Class.forName checks at transform time;
        // we replicate them here so the debug HUD reflects the correct renderer.
        boolean sodiumFound = false;

        String[] sodiumClasses = {
            "me.jellysquid.mods.sodium.client.world.LevelSlice",
            "org.embeddedt.embeddium.client.world.LevelSlice",
            "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext"
        };
        for (String cls : sodiumClasses) {
            try {
                Class.forName(cls, false, this.getClass().getClassLoader());
                sodiumFound = true;
                break;
            } catch (Throwable ignored) {}
        }

        // Fallback: LoadingModList (may be ready by this point)
        if (!sodiumFound) {
            try {
                net.minecraftforge.fml.loading.LoadingModList list = net.minecraftforge.fml.loading.LoadingModList.get();
                if (list != null && (list.getModFileById("sodium") != null || list.getModFileById("embeddium") != null)) {
                    sodiumFound = true;
                }
            } catch (Throwable ignored) {}
        }

        CullifyDebugManager.sodiumDetected = sodiumFound;

        // Try to register Sodium Options API integration
        try {
            Class.forName("toni.sodiumoptionsapi.api.OptionGUIConstruction", false, this.getClass().getClassLoader());
            Class<?> compatClass = Class.forName("com.fluxsyum.cullify.compat.SodiumCompat");
            compatClass.getMethod("registerOptions").invoke(null);
        } catch (Throwable ignored) {
        }
    }


    private void onConfigReload(ModConfigEvent event) {
        if (event.getConfig().getModId().equals(MOD_ID)) {
            CullifyDebugManager.syncFromConfig();
            scheduleWorldReload();
        }
    }

    public static void scheduleWorldReload() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.levelRenderer != null) {
                    mc.levelRenderer.allChanged();
                }
            });
        }
    }

    public enum PlantType {
        GRASS,
        FLOWER,
        OTHER,
        NONE
    }

    public static PlantType computePlantType(BlockState state) {
        Block block = state.getBlock();

        // Class exclusions for tree components, saplings, mushrooms
        if (block instanceof LeavesBlock || 
            block instanceof SaplingBlock || 
            block instanceof MushroomBlock || 
            block instanceof FungusBlock || 
            block instanceof PinkPetalsBlock) {
            return PlantType.NONE;
        }

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return PlantType.NONE;
        }
        String path = id.getPath();

        // Keyword exceptions for mod compatibility
        if (path.contains("leaves") || path.contains("petal") || path.contains("sapling") || path.contains("mushroom")) {
            return PlantType.NONE;
        }

        if (state.is(BlockTags.FLOWERS)) {
            return PlantType.FLOWER;
        }

        if (path.contains("grass") || path.contains("fern")) {
            if (!path.contains("block")) {
                return PlantType.GRASS;
            }
        }

        if (block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN) {
            return PlantType.GRASS;
        }

        if (block == Blocks.SUNFLOWER || block == Blocks.LILAC ||
            block == Blocks.ROSE_BUSH || block == Blocks.PEONY) {
            return PlantType.FLOWER;
        }

        if (block == Blocks.KELP || block == Blocks.KELP_PLANT || block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS) {
            return PlantType.OTHER;
        }

        if (block == Blocks.DEAD_BUSH || block == Blocks.SPORE_BLOSSOM || block == Blocks.HANGING_ROOTS) {
            return PlantType.OTHER;
        }

        if (block instanceof BushBlock && !(block instanceof CropBlock)) {
            return PlantType.OTHER;
        }

        return PlantType.NONE;
    }

    public static PlantType getPlantType(BlockState state) {
        return ((CullifyBlockState) (Object) state).cullify$getPlantType();
    }

    public static BlockState getCulledState(BlockState state) {
        CullifyBlockState duck = (CullifyBlockState) (Object) state;
        if (duck.cullify$hasFluid()) {
            CullifyDebugManager.waterReplacements.increment();
            return duck.cullify$getFluidBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    public static double getCullDistance(PlantType type) {
        switch (type) {
            case GRASS:
                return CullifyConfig.CULL_GRASS.get() ? CullifyConfig.GRASS_CULL_DISTANCE.get() : -1;
            case FLOWER:
                return CullifyConfig.CULL_FLOWERS.get() ? CullifyConfig.FLOWER_CULL_DISTANCE.get() : -1;
            case OTHER:
                return CullifyConfig.CULL_OTHER_PLANTS.get() ? CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get() : -1;
            default:
                return -1;
        }
    }

    public static double getCullDistanceSq(PlantType type) {
        double limit = getCullDistance(type);
        if (limit < 0) return -1;
        return limit * limit;
    }

    public static boolean shouldCull(BlockState state, BlockPos pos) {
        return shouldCull(state, pos.getX(), pos.getY(), pos.getZ());
    }



    public static boolean isInShape(double dx, double dy, double dz, double limit, CullifyConfig.CullingShape shape) {
        switch (shape) {
            case TRIANGLE:
                return (dz >= -limit / 2.0) && (dz <= limit - Math.abs(dx) * 1.73205080757);
            case HEXAGON:
                return Math.abs(dx) <= limit && (Math.abs(dx) * 0.5 + Math.abs(dz) * 0.86602540378 <= limit);
            case STAR: {
                boolean inTriangleUp = (dz >= -limit / 2.0) && (dz <= limit - Math.abs(dx) * 1.73205080757);
                boolean inTriangleDown = (dz <= limit / 2.0) && (dz >= -limit + Math.abs(dx) * 1.73205080757);
                return inTriangleUp || inTriangleDown;
            }
            case SQUARE:
                return Math.abs(dx) <= limit && Math.abs(dz) <= limit;
            case CIRCLE:
            case CYLINDER:
                return (dx * dx + dz * dz) <= limit * limit;
            case BOX:
                return Math.abs(dx) <= limit && Math.abs(dy) <= limit && Math.abs(dz) <= limit;
            case SPHERE:
            default:
                return (dx * dx + dy * dy + dz * dz) <= limit * limit;
        }
    }

    public static boolean isOutsideShape(double dx, double dy, double dz, double limit) {
        return !isInShape(dx, dy, dz, limit, CullifyConfig.CULLING_SHAPE.get());
    }

    public static boolean shouldCull(BlockState state, int x, int y, int z) {
        if (!CullifyConfig.ENABLED.get() || !hasPlayer) {
            return false;
        }

        PlantType type = getPlantType(state);
        if (type == PlantType.NONE) {
            return false;
        }

        double limit = getCullDistance(type);
        if (limit < 0) {
            return false;
        }

        double dx = x + 0.5 - playerX;
        double dy = y + 0.5 - playerY;
        double dz = z + 0.5 - playerZ;

        if (isOutsideShape(dx, dy, dz, limit)) {
            CullifyDebugManager.culledBlocks.increment();
            return true;
        }
        return false;
    }

    public static boolean shouldCullNoCount(BlockState state, BlockPos pos) {
        return shouldCullNoCount(state, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean shouldCullNoCount(BlockState state, int x, int y, int z) {
        if (!CullifyConfig.ENABLED.get() || !hasPlayer) {
            return false;
        }

        PlantType type = getPlantType(state);
        if (type == PlantType.NONE) {
            return false;
        }

        double limit = getCullDistance(type);
        if (limit < 0) {
            return false;
        }

        double dx = x + 0.5 - playerX;
        double dy = y + 0.5 - playerY;
        double dz = z + 0.5 - playerZ;

        return isOutsideShape(dx, dy, dz, limit);
    }
}

