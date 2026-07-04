package com.fluxsyum.cullify;

import com.fluxsyum.cullify.duck.CullifyBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
    public static String VERSION = "1.3.1";
    public static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/logo.png");

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
    /** Grass cull radius squared (pre-computed from distance) */
    public static volatile double  cachedGrassDist     = 48;
    public static volatile double  cachedFlowerDist    = 48;
    public static volatile double  cachedOtherDist     = 32;
    public static volatile CullifyConfig.CullingShape cachedShape = CullifyConfig.CullingShape.SPHERE;
    /** LOD density threshold: 0 = cull all, 100 = cull nothing at near-border (disabled) */
    public static volatile int     cachedLodDensity    = 100;

    public static volatile boolean benchmarkBypass = false;

    /**
     * Refresh all cached config values. Called whenever a config change is detected.
     * This must only be called from the main client thread.
     */
    public static void updateConfigCache() {
        cachedEnabled     = CullifyConfig.ENABLED.get() && !benchmarkBypass;
        cachedCullGrass   = CullifyConfig.CULL_GRASS.get();
        cachedCullFlowers = CullifyConfig.CULL_FLOWERS.get();
        cachedCullOther   = CullifyConfig.CULL_OTHER_PLANTS.get();
        cachedGrassDist   = CullifyConfig.GRASS_CULL_DISTANCE.get();
        cachedFlowerDist  = CullifyConfig.FLOWER_CULL_DISTANCE.get();
        cachedOtherDist   = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
        cachedShape       = CullifyConfig.CULLING_SHAPE.get();
        cachedLodDensity  = CullifyConfig.LOD_DENSITY.get();
    }

    // -----------------------------------------------------------------------
    // Voxel Grid — flat boolean[128×128×128] grid centred on the player for
    // O(1) visibility lookups. Rebuilt on player movement or config change.
    //
    // Coordinate mapping (local index):
    //   ix = ((int)x - gridOriginX) + 64  → range [0, 127]
    //   iy = ((int)y - gridOriginY) + 64
    //   iz = ((int)z - gridOriginZ) + 64
    //   index = iy * 128*128 + iz * 128 + ix
    //
    // A slot is TRUE when the block is VISIBLE (inside the culling shape),
    // FALSE when culled. We keep the "visible" semantics to make the initial
    // state (all-false = all culled) a safe fallback until the first rebuild.
    // -----------------------------------------------------------------------
    private static final int GRID_HALF   = 64;
    private static final int GRID_SIZE   = 128;
    private static final int GRID_AREA   = GRID_SIZE * GRID_SIZE;
    private static final int GRID_VOLUME = GRID_SIZE * GRID_SIZE * GRID_SIZE;

    private static final boolean[] gridA = new boolean[GRID_VOLUME];
    private static final boolean[] gridB = new boolean[GRID_VOLUME];
    private static boolean useA = true;

    /** Grid cells: true = block is within the culling radius (KEPT), false = culled */
    public static volatile boolean[] voxelGrid = gridA;

    /** World-space origin of the voxel grid (block at local index 0,0,0) */
    public static volatile int gridOriginX = 0;
    public static volatile int gridOriginY = 0;
    public static volatile int gridOriginZ = 0;

    /** Whether the grid needs a full rebuild */
    public static volatile boolean voxelGridDirty = true;

    /**
     * Rebuilds the entire voxel grid around the given camera position.
     * This is an O(128³) operation (~2M iterations) and should only be called
     * when the player moves more than a few blocks or config changes.
     *
     * <p>To keep the grid rebuild fast we use the cached shape and distances,
     * and we call {@link #isInShape} which inlines a simple conditional chain.</p>
     */
    public static void rebuildVoxelGrid(double px, double py, double pz) {
        // Capture a stable local snapshot of all config values before the loop.
        // This avoids reading volatile fields 2M+ times inside the hot loop and
        // prevents torn reads if the main thread updates them mid-rebuild.
        final double grassLimit  = cachedCullGrass   ? cachedGrassDist  : -1;
        final double flowerLimit = cachedCullFlowers  ? cachedFlowerDist : -1;
        final double otherLimit  = cachedCullOther    ? cachedOtherDist  : -1;
        final CullifyConfig.CullingShape shape = cachedShape;

        final int ox = (int) px - GRID_HALF;
        final int oy = (int) py - GRID_HALF;
        final int oz = (int) pz - GRID_HALF;

        // Build into a local buffer first to avoid partial grid reads from
        // other threads while the rebuild is still in progress.
        final boolean[] localGrid = useA ? gridB : gridA;
        java.util.Arrays.fill(localGrid, false);

        for (int iy = 0; iy < GRID_SIZE; iy++) {
            final double dy = (oy + iy + 0.5) - py;
            final int baseY = iy * GRID_AREA;
            for (int iz = 0; iz < GRID_SIZE; iz++) {
                final double dz = (oz + iz + 0.5) - pz;
                final int baseYZ = baseY + iz * GRID_SIZE;
                for (int ix = 0; ix < GRID_SIZE; ix++) {
                    final double dx = (ox + ix + 0.5) - px;
                    // A cell is "kept" (visible) if ANY plant type would keep it.
                    // This is a conservative union — the exact plant type is still
                    // checked during getBlockState, but FULLY_CULLED sections
                    // can be detected quickly in MixinLevelSlice.
                    localGrid[baseYZ + ix] =
                        (grassLimit  >= 0 && isInShape(dx, dy, dz, grassLimit,  shape)) ||
                        (flowerLimit >= 0 && isInShape(dx, dy, dz, flowerLimit, shape)) ||
                        (otherLimit  >= 0 && isInShape(dx, dy, dz, otherLimit,  shape));
                }
            }
        }

        // Publish results atomically: update origin then copy buffer, then clear dirty flag.
        // Other threads read voxelGridDirty first, so publishing the flag last is safe.
        gridOriginX = ox;
        gridOriginY = oy;
        gridOriginZ = oz;
        voxelGrid = localGrid;
        useA = !useA;
        voxelGridDirty = false;
    }

    /**
     * Returns whether a world block at (x, y, z) is inside the culling radius
     * (i.e., should be KEPT) using the voxel grid for O(1) lookup.
     * Falls back to per-block math if the position is outside the grid.
     */
    public static boolean isKeptByVoxelGrid(int x, int y, int z, double limit, CullifyConfig.CullingShape shape) {
        int ix = x - gridOriginX;
        int iy = y - gridOriginY;
        int iz = z - gridOriginZ;
        if (ix >= 0 && ix < GRID_SIZE && iy >= 0 && iy < GRID_SIZE && iz >= 0 && iz < GRID_SIZE) {
            return voxelGrid[iy * GRID_AREA + iz * GRID_SIZE + ix];
        }
        // Outside grid bounds — fall back to exact math
        double dx = x + 0.5 - playerX;
        double dy = y + 0.5 - playerY;
        double dz = z + 0.5 - playerZ;
        return isInShape(dx, dy, dz, limit, shape);
    }

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
        long hash = (x * PRIME_X) ^ (y * PRIME_Y) ^ (z * PRIME_Z);
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        int bucket = (int) ((hash & Long.MAX_VALUE) % 100L);
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

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modContainer.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (client, parent) -> new com.fluxsyum.cullify.client.CullifyConfigScreen(parent)
            );
            com.fluxsyum.cullify.client.ClientModBusSubscriber.register(modContainer.getEventBus());
        }
    }

    private void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        try {
            Class.forName("toni.sodiumoptionsapi.api.OptionGUIConstruction", false, this.getClass().getClassLoader());
            Class<?> compatClass = Class.forName("com.fluxsyum.cullify.compat.SodiumCompat");
            compatClass.getMethod("registerOptions").invoke(null);
        } catch (Throwable ignored) {
        }

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
            voxelGridDirty = true;
            CullifyDebugManager.syncFromConfig();
            scheduleWorldReload();
        }
    }

    private static volatile boolean reloadScheduled = false;

    public static void scheduleWorldReload() {
        if (reloadScheduled) return;
        reloadScheduled = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                reloadScheduled = false;
                if (mc.levelRenderer != null) {
                    mc.levelRenderer.allChanged();
                }
            });
        }
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

        // 2. Class exclusions for trees, saplings, mushrooms, crops
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

        // 3. Tag checks (O(1) set lookup)
        if (state.is(BlockTags.FLOWERS)) {
            return PlantType.FLOWER;
        }

        // 4. Fallback registry lookup for modded blocks
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
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
                return cachedCullGrass   ? cachedGrassDist  : -1;
            case FLOWER:
                return cachedCullFlowers ? cachedFlowerDist : -1;
            case OTHER:
                return cachedCullOther   ? cachedOtherDist  : -1;
            default:
                return -1;
        }
    }

    public static double getCullDistanceSq(PlantType type) {
        double limit = getCullDistance(type);
        if (limit < 0) return -1;
        return limit * limit;
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
            case STAR: {
                boolean inTriangleUp   = (dz >= -limit / 2.0) && (dz <= limit - Math.abs(dx) * 1.73205080757);
                boolean inTriangleDown = (dz <= limit / 2.0)  && (dz >= -limit + Math.abs(dx) * 1.73205080757);
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

    private static boolean shouldCullInternal(BlockState state, int x, int y, int z, boolean countStats, boolean applyLod) {
        if (!cachedEnabled || !hasPlayer) {
            return false;
        }

        PlantType type = getPlantType(state);
        if (type == PlantType.NONE) {
            return false;
        }

        // Double-block plants: always use LOWER half's position so both halves share
        // the exact same culling decision and never go out of sync visually.
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            y -= 1;
        }

        double limit = getCullDistance(type);
        if (limit < 0) {
            return false;
        }

        double dx = x + 0.5 - playerX;
        double dy = y + 0.5 - playerY;
        double dz = z + 0.5 - playerZ;

        // LOD density filter — deterministic hash, removes distant sparse blocks
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

        if (isOutsideShape(dx, dy, dz, limit)) {
            if (countStats) {
                CullifyDebugManager.culledBlocks.increment();
            }
            return true;
        }
        return false;
    }

    public static boolean shouldCull(BlockState state, int x, int y, int z) {
        return shouldCullInternal(state, x, y, z, true, true);
    }

    public static boolean shouldCullNoCount(BlockState state, BlockPos pos) {
        return shouldCullNoCount(state, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean shouldCullNoCount(BlockState state, int x, int y, int z) {
        return shouldCullInternal(state, x, y, z, false, true);
    }
}
