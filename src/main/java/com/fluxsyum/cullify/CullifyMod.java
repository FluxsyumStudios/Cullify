package com.fluxsyum.cullify;

import com.fluxsyum.cullify.duck.CullifyBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CullifyMod.MOD_ID)
public class CullifyMod {
    public static final String MOD_ID = "cullify";
    public static String VERSION = "1.4.5";
    public static final Identifier LOGO = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/logo.png");

    // -----------------------------------------------------------------------
    // Config version — incremented when settings change to invalidate caches.
    // Uses AtomicInteger so increment is thread-safe across render/background
    // threads without locks.
    // -----------------------------------------------------------------------
    private static final AtomicInteger configVersionAtomic = new AtomicInteger(0);

    /** Read by chunk-builder threads — returns the current version number. */
    public static volatile int configVersion = 0;

    public static void incrementConfigVersion() {
        configVersion = configVersionAtomic.incrementAndGet();
    }

    // -----------------------------------------------------------------------
    // Camera coordinates updated on client tick (volatile for cross-thread reads)
    // -----------------------------------------------------------------------
    public static volatile double playerX = 0;
    public static volatile double playerY = 0;
    public static volatile double playerZ = 0;
    public static volatile boolean hasPlayer = false;

    // -----------------------------------------------------------------------
    // Config cache — avoids repeated ModConfigSpec.get() calls in the hot path.
    // Updated by updateConfigCache() every time settings change.
    // -----------------------------------------------------------------------
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

    // Voxel grid system removed for performance.

    // -----------------------------------------------------------------------
    // LOD Density — deterministic per-block hash using large primes so that
    // grass appearance does NOT flicker on camera rotation or jitter.
    // density=100 → keep all blocks; density=50 → keep 50% of grass blocks.
    // -----------------------------------------------------------------------
    private static final long PRIME_X = 31222091L;
    private static final long PRIME_Z = 49247003L;
    private static final long PRIME_Y = 17369191L;

    /**
     * Returns true if this block position passes the LOD density filter.
     * The result is deterministic: the same (x,y,z) always returns the same value.
     *
     * @param density threshold 0-100. 100 = keep everything (disabled).
     */
    public static boolean passesLodDensity(int x, int y, int z, int density) {
        if (density >= 100) return true;
        if (density <= 0)   return false;
        // Combine coordinates with large primes and fold into 0-99 range
        // (full fmix64 finalizer: both multiply rounds are needed for a clean
        // avalanche, otherwise neighbouring coordinates produce visible stripes)
        long hash = (x * PRIME_X) ^ (y * PRIME_Y) ^ (z * PRIME_Z);
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        // Fast-range reduction: maps the low 32 bits to 0-99 without a modulo
        int bucket = (int) (((hash & 0xFFFFFFFFL) * 100) >>> 32);
        return bucket < density;
    }

    // -----------------------------------------------------------------------
    // Mod lifecycle
    // -----------------------------------------------------------------------
    public CullifyMod(ModContainer modContainer) {
        VERSION = modContainer.getModInfo().getVersion().toString();
        modContainer.registerConfig(ModConfig.Type.CLIENT, CullifyConfig.SPEC);
        modContainer.getEventBus().addListener(this::onClientSetup);
        modContainer.getEventBus().addListener(this::onConfigReload);

        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            modContainer.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (client, parent) -> new com.fluxsyum.cullify.client.CullifyConfigScreen(parent)
            );
            com.fluxsyum.cullify.client.ClientModBusSubscriber.register(modContainer.getEventBus());
        }
    }

    private void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        try {
            Class.forName("net.caffeinemc.mods.sodium.client.world.LevelSlice", false, this.getClass().getClassLoader());
            CullifyDebugManager.sodiumDetected = true;
        } catch (Throwable ignored) {
            CullifyDebugManager.sodiumDetected = false;
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

    /**
     * Flags that a full re-render of all visible sections is required.
     * The actual reload is deferred until the next client tick when no screen
     * is open, preventing rendering issues if the config is changed while the
     * game is paused.
     */
    public static void scheduleWorldReload() {
        reloadRequired = true;
    }

    // -----------------------------------------------------------------------
    // Plant classification
    // -----------------------------------------------------------------------
    public enum PlantType {
        GRASS,
        FLOWER,
        OTHER,
        NONE
    }

    public static PlantType computePlantType(BlockState state) {
        Block block = state.getBlock();

        // 1. Direct block checks first (extremely fast reference comparisons)
        if (block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN ||
            block == Blocks.SHORT_GRASS || block == Blocks.FERN) {
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

        // 2. Class exclusions for trees, saplings, mushrooms, crops
        if (block instanceof LeavesBlock ||
            block instanceof SaplingBlock ||
            block instanceof MushroomBlock ||
            block instanceof NetherFungusBlock ||
            block instanceof FlowerBedBlock ||
            block instanceof CropBlock) {
            return PlantType.NONE;
        }

        if (block instanceof BushBlock) {
            return PlantType.OTHER;
        }

        // 3. Tag checks (O(1) set lookup)
        if (state.is(BlockTags.FLOWERS)) {
            return PlantType.FLOWER;
        }

        // 4. Fallback registry lookup for modded blocks
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return PlantType.NONE;
        }
        String path = id.getPath();

        // Keyword exceptions for mod compatibility
        if (path.contains("leaves") || path.contains("petal") || path.contains("sapling") || path.contains("mushroom")) {
            return PlantType.NONE;
        }

        if (path.contains("grass") || path.contains("fern")) {
            if (!path.contains("block")) {
                return PlantType.GRASS;
            }
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

    // -----------------------------------------------------------------------
    // Shape math
    // -----------------------------------------------------------------------
    public static boolean isInShape(double dx, double dy, double dz, double limit, CullifyConfig.CullingShape shape) {
        switch (shape) {
            case TRIANGLE:
                return (dz >= -limit / 2.0) && (dz <= limit - Math.abs(dx) * 1.73205080757);
            case HEXAGON:
                return Math.abs(dx) <= limit && (Math.abs(dx) * 0.5 + Math.abs(dz) * 0.86602540378 <= limit);
            case STAR:
                return isInTriangleUp(dx, dz, limit) || isInTriangleDown(dx, dz, limit);
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

    private static boolean isInTriangleUp(double dx, double dz, double limit) {
        return (dz >= -limit / 2.0) && (dz <= limit - Math.abs(dx) * 1.73205080757);
    }

    private static boolean isInTriangleDown(double dx, double dz, double limit) {
        return (dz <= limit / 2.0) && (dz >= -limit + Math.abs(dx) * 1.73205080757);
    }

    // -----------------------------------------------------------------------
    // Section classification — shared by ClientEventHandler (dirty-section
    // scanning) and MixinLevelSlice (27-section AABB cache).
    //
    // Takes the section bounds as SIGNED offsets relative to the camera
    // (dMin = sectionMin - camera, dMax = sectionMax - camera) so that
    // asymmetric shapes (TRIANGLE, STAR extend further in +Z than -Z) and
    // oversized ones (HEXAGON's circumradius exceeds the limit) classify
    // correctly on every side of the player.
    // -----------------------------------------------------------------------
    public static final byte SECTION_STRADDLING   = 0;
    public static final byte SECTION_FULLY_KEPT   = 1;
    public static final byte SECTION_FULLY_CULLED = 2;

    /** The hexagon's farthest point sits at 2/sqrt(3) * limit from the center. */
    private static final double HEXAGON_CIRCUMRADIUS = 1.1547005383792517;

    public static byte classifySection(double dMinX, double dMaxX, double dMinY, double dMaxY,
                                       double dMinZ, double dMaxZ, double threshold) {
        if (threshold < 0) {
            return SECTION_FULLY_KEPT;
        }

        CullifyConfig.CullingShape shape = cachedShape;

        // Nearest/farthest absolute per-axis distances from the camera to the box
        double nearDx = dMinX > 0 ? dMinX : (dMaxX < 0 ? -dMaxX : 0.0);
        double nearDy = dMinY > 0 ? dMinY : (dMaxY < 0 ? -dMaxY : 0.0);
        double nearDz = dMinZ > 0 ? dMinZ : (dMaxZ < 0 ? -dMaxZ : 0.0);
        double farDx = Math.max(-dMinX, dMaxX);
        double farDy = Math.max(-dMinY, dMaxY);
        double farDz = Math.max(-dMinZ, dMaxZ);

        switch (shape) {
            case BOX: {
                double minDist = Math.max(nearDx, Math.max(nearDy, nearDz));
                if (minDist > threshold) return SECTION_FULLY_CULLED;
                double maxDist = Math.max(farDx, Math.max(farDy, farDz));
                return maxDist <= threshold ? SECTION_FULLY_KEPT : SECTION_STRADDLING;
            }
            case CYLINDER:
            case CIRCLE: {
                double minDistSq = nearDx * nearDx + nearDz * nearDz;
                if (minDistSq > threshold * threshold) return SECTION_FULLY_CULLED;
                double maxDistSq = farDx * farDx + farDz * farDz;
                return maxDistSq <= threshold * threshold ? SECTION_FULLY_KEPT : SECTION_STRADDLING;
            }
            case SQUARE: {
                double minDist = Math.max(nearDx, nearDz);
                if (minDist > threshold) return SECTION_FULLY_CULLED;
                double maxDist = Math.max(farDx, farDz);
                return maxDist <= threshold ? SECTION_FULLY_KEPT : SECTION_STRADDLING;
            }
            case STAR: {
                // Circumradius of the star equals the limit, so radial rejection is exact
                double minDistSq = nearDx * nearDx + nearDz * nearDz;
                if (minDistSq > threshold * threshold) return SECTION_FULLY_CULLED;
                // Concave shape: only fully kept when all four corners sit in the SAME triangle
                boolean up = isInTriangleUp(dMinX, dMinZ, threshold) && isInTriangleUp(dMinX, dMaxZ, threshold)
                          && isInTriangleUp(dMaxX, dMinZ, threshold) && isInTriangleUp(dMaxX, dMaxZ, threshold);
                if (up) return SECTION_FULLY_KEPT;
                boolean down = isInTriangleDown(dMinX, dMinZ, threshold) && isInTriangleDown(dMinX, dMaxZ, threshold)
                            && isInTriangleDown(dMaxX, dMinZ, threshold) && isInTriangleDown(dMaxX, dMaxZ, threshold);
                return down ? SECTION_FULLY_KEPT : SECTION_STRADDLING;
            }
            case TRIANGLE:
            case HEXAGON: {
                // Radial rejection must use each shape's circumradius, not the limit
                double cullRadius = shape == CullifyConfig.CullingShape.HEXAGON
                        ? threshold * HEXAGON_CIRCUMRADIUS : threshold;
                double minDistSq = nearDx * nearDx + nearDz * nearDz;
                if (minDistSq > cullRadius * cullRadius) return SECTION_FULLY_CULLED;
                // Convex 2D shapes: fully kept when all four signed corners are inside
                boolean inside = isInShape(dMinX, 0, dMinZ, threshold, shape) && isInShape(dMinX, 0, dMaxZ, threshold, shape)
                              && isInShape(dMaxX, 0, dMinZ, threshold, shape) && isInShape(dMaxX, 0, dMaxZ, threshold, shape);
                return inside ? SECTION_FULLY_KEPT : SECTION_STRADDLING;
            }
            case SPHERE:
            default: {
                double minDistSq = nearDx * nearDx + nearDy * nearDy + nearDz * nearDz;
                if (minDistSq > threshold * threshold) return SECTION_FULLY_CULLED;
                double maxDistSq = farDx * farDx + farDy * farDy + farDz * farDz;
                return maxDistSq <= threshold * threshold ? SECTION_FULLY_KEPT : SECTION_STRADDLING;
            }
        }
    }

    // -----------------------------------------------------------------------
    // shouldCull — main hot-path entry point
    //
    // Double-Tall Plant support:
    //   If the blockstate has HALF=UPPER, we resolve the culling decision from
    //   the LOWER half position (pos.below()) so both halves always match.
    // -----------------------------------------------------------------------
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

        // Cache double-block property lookup in duck interface to avoid map checks
        if (((com.fluxsyum.cullify.duck.CullifyBlockState) (Object) state).cullify$isDoubleBlockUpperHalf()) {
            y -= 1;
        }

        double limit = getCullDistance(type);
        if (limit < 0) {
            return false;
        }

        // Light-Aware Culling: scale the effective cull limit using pre-fetched light factor
        if (cachedLightAware) {
            limit *= lightFactor;
        }

        double dx = x + 0.5 - playerX;
        double dy = y + 0.5 - playerY;
        double dz = z + 0.5 - playerZ;

        // LOD density filter — deterministic hash, removes distant sparse blocks (optimized fast range)
        if (applyLod) {
            int lodDensity = cachedLodDensity;
            if (lodDensity < 100) {
                double distSq = dx * dx + dz * dz;
                double halfLimitSq = (limit * 0.5) * (limit * 0.5);
                // Only apply density thinning in the outer 50% of the cull radius
                if (distSq > halfLimitSq && !passesLodDensity(x, y, z, lodDensity)) {
                     if (countStats) {
                         CullifyDebugManager.culledBlocks.increment();
                     }
                     return true;
                }
            }
        }

        // Inline shape check for SPHERE (default/most common) and CYLINDER/CIRCLE
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
