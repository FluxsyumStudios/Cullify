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

import java.util.concurrent.atomic.AtomicInteger;

@Mod(CullifyMod.MOD_ID)
public class CullifyMod {
    public static final String MOD_ID = "cullify";
    public static String VERSION = "1.4.0";
    public static final net.minecraft.resources.ResourceLocation LOGO = new net.minecraft.resources.ResourceLocation(MOD_ID, "textures/gui/logo.png");

    // Config version — incremented when settings change to invalidate caches.
    // Uses AtomicInteger so increment is thread-safe across render/background
    // threads without locks.
    private static final AtomicInteger configVersionAtomic = new AtomicInteger(0);

    /** Read by chunk-builder threads — returns the current version number. */
    public static volatile int configVersion = 0;

    public static void incrementConfigVersion() {
        configVersion = configVersionAtomic.incrementAndGet();
    }

    // Camera coordinates updated on client tick (volatile for cross-thread reads)
    public static volatile double playerX = 0;
    public static volatile double playerY = 0;
    public static volatile double playerZ = 0;
    public static volatile boolean hasPlayer = false;

    // Config cache — avoids repeated ModConfigSpec.get() calls in the hot path.
    // Updated by updateConfigCache() every time settings change.
    public static volatile boolean cachedEnabled       = true;
    public static volatile boolean cachedCullGrass     = true;
    public static volatile boolean cachedCullFlowers   = true;
    public static volatile boolean cachedCullOther     = true;
    public static volatile double  cachedGrassDist     = 48;
    public static volatile double  cachedFlowerDist    = 48;
    public static volatile double  cachedOtherDist     = 32;

    public static volatile double  cachedGrassDistScaled   = 48;
    public static volatile double  cachedFlowerDistScaled  = 48;
    public static volatile double  cachedOtherDistScaled   = 32;

    public static volatile double  cachedGrassDistSqScaled   = 48 * 48;
    public static volatile double  cachedFlowerDistSqScaled  = 48 * 48;
    public static volatile double  cachedOtherDistSqScaled   = 32 * 32;

    public static volatile CullifyConfig.CullingShape cachedShape = CullifyConfig.CullingShape.SPHERE;
    /** LOD density threshold: 0 = cull all, 100 = cull nothing at near-border (disabled) */
    public static volatile int     cachedLodDensity    = 100;
    /** Smart Scale: whether to auto-reduce distances when FPS drops */
    public static volatile boolean cachedSmartScale    = false;
    /** Target FPS for the Smart Scale system */
    public static volatile int     cachedTargetFps     = 60;
    /** Light-Aware Culling: whether to reduce distances in dark areas */
    public static volatile boolean cachedLightAware    = false;

    /**
     * Current Smart Scale multiplier in [0.5, 1.0].
     * Written by the main thread every second; read by background rebuild thread.
     */
    public static volatile float smartScaleFactor = 1.0f;

    /**
     * Per-section light importance factor, in [0.3, 1.0].
     * Key = SectionPos.asLong(sx, sy, sz). Written by the background classifier thread.
     */
    public static final java.util.concurrent.ConcurrentHashMap<Long, Float> sectionLightFactors =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static volatile boolean benchmarkBypass = false;

    public static void updateScaledDistances() {
        float scale = cachedSmartScale ? smartScaleFactor : 1.0f;
        cachedGrassDistScaled = cachedGrassDist * scale;
        cachedFlowerDistScaled = cachedFlowerDist * scale;
        cachedOtherDistScaled = cachedOtherDist * scale;
        cachedGrassDistSqScaled = cachedGrassDistScaled * cachedGrassDistScaled;
        cachedFlowerDistSqScaled = cachedFlowerDistScaled * cachedFlowerDistScaled;
        cachedOtherDistSqScaled = cachedOtherDistScaled * cachedOtherDistScaled;
    }

    /**
     * Refresh all cached config values. Called whenever a config change is detected.
     * This must only be called from the main client thread.
     */
    public static void updateConfigCache() {
        cachedEnabled       = CullifyConfig.ENABLED.get() && !benchmarkBypass;
        cachedCullGrass     = CullifyConfig.CULL_GRASS.get();
        cachedCullFlowers   = CullifyConfig.CULL_FLOWERS.get();
        cachedCullOther     = CullifyConfig.CULL_OTHER_PLANTS.get();
        cachedGrassDist     = CullifyConfig.GRASS_CULL_DISTANCE.get();
        cachedFlowerDist    = CullifyConfig.FLOWER_CULL_DISTANCE.get();
        cachedOtherDist     = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
        cachedShape         = CullifyConfig.CULLING_SHAPE.get();
        cachedLodDensity    = CullifyConfig.LOD_DENSITY.get();
        cachedSmartScale    = CullifyConfig.SMART_SCALE.get();
        cachedTargetFps     = CullifyConfig.TARGET_FPS.get();
        cachedLightAware    = CullifyConfig.LIGHT_AWARE_CULLING.get();

        updateScaledDistances();
    }

    public CullifyMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CullifyConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.fluxsyum.cullify.client.ClientModBusSubscriber.register(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }

    private void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
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

        if (!sodiumFound) {
            try {
                net.minecraftforge.fml.loading.LoadingModList list = net.minecraftforge.fml.loading.LoadingModList.get();
                if (list != null && (list.getModFileById("sodium") != null || list.getModFileById("embeddium") != null)) {
                    sodiumFound = true;
                }
            } catch (Throwable ignored) {}
        }

        CullifyDebugManager.sodiumDetected = sodiumFound;

        try {
            Class.forName("toni.sodiumoptionsapi.api.OptionGUIConstruction", false, this.getClass().getClassLoader());
            Class<?> compatClass = Class.forName("com.fluxsyum.cullify.compat.SodiumCompat");
            compatClass.getMethod("registerOptions").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private void onConfigReload(ModConfigEvent event) {
        if (event.getConfig().getModId().equals(MOD_ID)) {
            updateConfigCache();
            CullifyDebugManager.syncFromConfig();
            scheduleWorldReload();
        }
    }

    public static volatile boolean reloadRequired = false;

    public static void scheduleWorldReload() {
        reloadRequired = true;
    }

    // -----------------------------------------------------------------------
    // LOD Density — deterministic per-block hash using large primes so that
    // grass appearance does NOT flicker on camera rotation or jitter.
    // density=100 -> keep all blocks; density=50 -> keep 50% of grass blocks.
    // -----------------------------------------------------------------------
    private static final long PRIME_X = 31222091L;
    private static final long PRIME_Z = 49247003L;
    private static final long PRIME_Y = 17369191L;

    public static boolean passesLodDensity(int x, int y, int z, int density) {
        if (density >= 100) return true;
        if (density <= 0)   return false;
        long hash = (x * PRIME_X) ^ (y * PRIME_Y) ^ (z * PRIME_Z);
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        // Fast-range division check (mapped 0-99)
        int bucket = (int) (((hash & 0xFFFFFFFFL) * 100) >>> 32);
        return bucket < density;
    }

    public enum PlantType {
        GRASS,
        FLOWER,
        OTHER,
        NONE
    }

    public static PlantType computePlantType(BlockState state) {
        Block block = state.getBlock();

        // 1. Direct block checks first (extremely fast reference comparisons)
        // In Minecraft 1.20.1:
        // - Blocks.GRASS is the short 1-block tall grass.
        // - Blocks.TALL_GRASS is the double-tall 2-block grass.
        // - Blocks.FERN is the short 1-block tall fern.
        // - Blocks.LARGE_FERN is the double-tall 2-block fern.
        if (block == Blocks.GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN || block == Blocks.LARGE_FERN) {
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

        // 2. Class exclusions for tree components, saplings, mushrooms, crops
        if (block instanceof LeavesBlock || 
            block instanceof SaplingBlock || 
            block instanceof MushroomBlock || 
            block instanceof FungusBlock || 
            block instanceof PinkPetalsBlock ||
            block instanceof CropBlock) {
            return PlantType.NONE;
        }

        if (block instanceof BushBlock) {
            return PlantType.OTHER;
        }

        // 3. Flower Tag Check
        if (state.is(BlockTags.FLOWERS)) {
            return PlantType.FLOWER;
        }

        // 4. Registry lookup check (modded compatibility)
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null) {
            String path = id.getPath();

            // Keyword exclusions
            if (path.contains("leaves") || path.contains("petal") || path.contains("sapling") || path.contains("mushroom")) {
                return PlantType.NONE;
            }

            if (path.contains("grass") || path.contains("fern")) {
                if (!path.contains("block")) {
                    return PlantType.GRASS;
                }
            }

            if (path.contains("flower") || path.contains("blossom")) {
                return PlantType.FLOWER;
            }
        }

        // 5. Fallback for any other general bush block
        if (block instanceof BushBlock) {
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

    // -----------------------------------------------------------------------
    // Cull distance helpers — now use config cache
    // -----------------------------------------------------------------------
    public static double getCullDistance(PlantType type) {
        switch (type) {
            case GRASS:
                return cachedCullGrass   ? cachedGrassDistScaled  : -1;
            case FLOWER:
                return cachedCullFlowers ? cachedFlowerDistScaled : -1;
            case OTHER:
                return cachedCullOther   ? cachedOtherDistScaled  : -1;
            default:
                return -1;
        }
    }

    public static double getCullDistanceSq(PlantType type) {
        switch (type) {
            case GRASS:
                return cachedCullGrass   ? cachedGrassDistSqScaled  : -1;
            case FLOWER:
                return cachedCullFlowers ? cachedFlowerDistSqScaled : -1;
            case OTHER:
                return cachedCullOther   ? cachedOtherDistSqScaled  : -1;
            default:
                return -1;
        }
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
        return !isInShape(dx, dy, dz, limit, cachedShape);
    }

    public static boolean shouldCull(BlockState state, BlockPos pos) {
        return shouldCull(state, pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean shouldCullInternal(BlockState state, int x, int y, int z, boolean countStats, boolean applyLod, float lightFactor) {
        if (!cachedEnabled || !hasPlayer) {
            return false;
        }

        PlantType type = getPlantType(state);
        if (type == PlantType.NONE) {
            return false;
        }

        if (((com.fluxsyum.cullify.duck.CullifyBlockState) (Object) state).cullify$isDoubleBlockUpperHalf()) {
            y -= 1;
        }

        double limit = getCullDistance(type);
        if (limit < 0) {
            return false;
        }

        if (cachedLightAware) {
            limit *= lightFactor;
        }

        double dx = x + 0.5 - playerX;
        double dy = y + 0.5 - playerY;
        double dz = z + 0.5 - playerZ;

        if (applyLod) {
            int lodDensity = cachedLodDensity;
            if (lodDensity < 100) {
                double distSq = dx * dx + dz * dz;
                double halfLimitSq = (limit * 0.5) * (limit * 0.5);
                if (distSq > halfLimitSq && !passesLodDensity(x, y, z, lodDensity)) {
                     if (countStats) {
                         CullifyDebugManager.culledBlocks.increment();
                     }
                     return true;
                }
            }
        }

        boolean isOutside;
        if (cachedShape == CullifyConfig.CullingShape.SPHERE) {
            isOutside = (dx * dx + dy * dy + dz * dz) > limit * limit;
        } else if (cachedShape == CullifyConfig.CullingShape.CYLINDER || cachedShape == CullifyConfig.CullingShape.CIRCLE) {
            isOutside = (dx * dx + dz * dz) > limit * limit;
        } else {
            isOutside = !isInShape(dx, dy, dz, limit, cachedShape);
        }

        if (isOutside) {
            if (countStats) {
                CullifyDebugManager.culledBlocks.increment();
            }
            return true;
        }
        return false;
    }

    public static boolean shouldCull(BlockState state, int x, int y, int z) {
        float lightFactor = 1.0f;
        if (cachedLightAware) {
            int sx = x >> 4;
            int sy = y >> 4;
            int sz = z >> 4;
            long secKey = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
            Float factor = sectionLightFactors.get(secKey);
            if (factor != null) {
                lightFactor = factor;
            }
        }
        return shouldCullInternal(state, x, y, z, true, true, lightFactor);
    }

    public static boolean shouldCull(BlockState state, int x, int y, int z, float lightFactor) {
        return shouldCullInternal(state, x, y, z, true, true, lightFactor);
    }

    public static boolean shouldCullNoCount(BlockState state, BlockPos pos) {
        return shouldCullNoCount(state, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean shouldCullNoCount(BlockState state, int x, int y, int z) {
        float lightFactor = 1.0f;
        if (cachedLightAware) {
            int sx = x >> 4;
            int sy = y >> 4;
            int sz = z >> 4;
            long secKey = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
            Float factor = sectionLightFactors.get(secKey);
            if (factor != null) {
                lightFactor = factor;
            }
        }
        return shouldCullInternal(state, x, y, z, false, true, lightFactor);
    }
}
