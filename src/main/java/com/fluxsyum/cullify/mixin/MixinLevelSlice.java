package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyDebugManager;
import com.fluxsyum.cullify.CullifyMod;
import com.fluxsyum.cullify.benchmark.BenchmarkManager;
import com.fluxsyum.cullify.duck.CullifyBlockState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimizes culling for Sodium's LevelSlice by classifying the 27 adjacent chunk sections
 * into FULLY_CULLED, FULLY_KEPT, or STRADDLING states to avoid per-block calculations.
 *
 * Only {@code getBlockState(int, int, int)} is hooked: Sodium's {@code getBlockState(BlockPos)}
 * is a thin overload that delegates straight to it, so hooking both would test every kept
 * plant twice.
 *
 * Threading: Sodium gives each chunk-build worker its own ChunkBuildContext, hence its own
 * BlockRenderCache and its own LevelSlice. Instances are never shared between threads, so
 * the cache fields below need no volatile or synchronization.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
public class MixinLevelSlice {

    @Shadow(remap = false) private int originBlockX;
    @Shadow(remap = false) private int originBlockY;
    @Shadow(remap = false) private int originBlockZ;

    private static final byte STRADDLING = 0;
    private static final byte FULLY_KEPT = 1;
    private static final byte FULLY_CULLED = 2;

    @Unique private final byte[] cullify$grassCache  = new byte[27];
    @Unique private final byte[] cullify$flowerCache = new byte[27];
    @Unique private final byte[] cullify$otherCache  = new byte[27];
    @Unique private final float[] cullify$lightFactors = new float[27];

    @Unique private boolean cullify$cacheValid = false;
    @Unique private int cullify$lastConfigVersion = -1;

    // Rebuild cache when a new section is copied into LevelSlice
    @Inject(method = "copyData", at = @At("TAIL"), remap = false)
    private void cullify$onCopyData(CallbackInfo ci) {
        cullify$cacheValid = false;
        if (!CullifyDebugManager.mixinLevelSliceApplied) {
            CullifyDebugManager.mixinLevelSliceApplied = true;
        }
        if (CullifyMod.cachedEnabled && CullifyMod.hasPlayer) {
            cullify$rebuildCache();
        }
    }

    // Invalidate cache on reset
    @Inject(method = "reset", at = @At("HEAD"), remap = false)
    private void cullify$onReset(CallbackInfo ci) {
        cullify$cacheValid = false;
    }

    /**
     * The meshing hot path — runs for every block Sodium reads while building a chunk.
     * Uses {@link ModifyReturnValue} rather than a cancellable {@code @Inject} so that no
     * CallbackInfoReturnable is allocated on the calls that return a non-plant block, which
     * is the overwhelming majority of them.
     */
    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), remap = false)
    private BlockState cullify$onGetBlockStateCoords(BlockState state, int x, int y, int z) {
        if (!CullifyMod.cachedEnabled || !CullifyMod.hasPlayer || state == null) {
            return state;
        }

        CullifyMod.PlantType type = ((CullifyBlockState) (Object) state).cullify$getPlantType();
        if (type == CullifyMod.PlantType.NONE) return state;

        int lx = (x - originBlockX) >> 4;
        int ly = (y - originBlockY) >> 4;
        int lz = (z - originBlockZ) >> 4;

        boolean benchmark = BenchmarkManager.isRunning();
        if (benchmark) BenchmarkManager.blocksTested.increment();

        boolean inBounds = lx >= 0 && lx <= 2 && ly >= 0 && ly <= 2 && lz >= 0 && lz <= 2;
        int sectionIdx = inBounds ? (ly * 9 + lz * 3 + lx) : -1;

        if (cullify$cacheValid && cullify$lastConfigVersion == CullifyMod.configVersion && inBounds) {
            byte cacheState = cullify$getCacheState(type, sectionIdx);

            if (cacheState == FULLY_CULLED) {
                if (CullifyDebugManager.statsEnabled) {
                    CullifyDebugManager.culledBlocks.increment();
                }
                if (benchmark) {
                    BenchmarkManager.blocksCulled.increment();
                    BenchmarkManager.cacheHits.increment();
                }
                return CullifyMod.getCulledState(state);
            } else if (cacheState == FULLY_KEPT) {
                if (benchmark) BenchmarkManager.cacheHits.increment();
                return state;
            }
            if (benchmark) BenchmarkManager.cacheMisses.increment();
        } else {
            if (benchmark) BenchmarkManager.cacheMisses.increment();
        }

        float lightFactor = CullifyMod.NEUTRAL_LIGHT_FACTOR;
        if (inBounds) {
            lightFactor = cullify$lightFactors[sectionIdx];
        } else if (CullifyMod.cachedLightAware) {
            lightFactor = CullifyMod.getLightFactor(x, y, z);
        }

        if (CullifyMod.shouldCull(state, x, y, z, lightFactor)) {
            if (benchmark) BenchmarkManager.blocksCulled.increment();
            return CullifyMod.getCulledState(state);
        }
        return state;
    }

    // Rebuild the AABB distance cache for the 27 sections.
    // Called ONLY from copyData (once per section) — never from getBlockState.
    @Unique
    private void cullify$rebuildCache() {
        boolean benchmark = BenchmarkManager.isRunning();
        long start = benchmark ? System.nanoTime() : 0;

        // Capture stable local snapshots of volatile player position and config.
        final double px = CullifyMod.playerX;
        final double py = CullifyMod.playerY;
        final double pz = CullifyMod.playerZ;

        final double grassDist  = CullifyMod.getCullDistance(CullifyMod.PlantType.GRASS);
        final double flowerDist = CullifyMod.getCullDistance(CullifyMod.PlantType.FLOWER);
        final double otherDist  = CullifyMod.getCullDistance(CullifyMod.PlantType.OTHER);

        // One snapshot read for the whole rebuild — the field is volatile and may be
        // republished by the classifier thread at any time.
        final boolean lightAware = CullifyMod.cachedLightAware;
        final it.unimi.dsi.fastutil.longs.Long2FloatMap lightFactors =
                lightAware ? CullifyMod.sectionLightFactors : null;

        for (int ly = 0; ly < 3; ly++) {
            // Section bounds as signed offsets relative to the camera
            final double dMinY = originBlockY + (ly << 4) - py;
            final double dMaxY = dMinY + 16.0;

            int sy = (originBlockY >> 4) + ly - 1;

            for (int lz = 0; lz < 3; lz++) {
                final double dMinZ = originBlockZ + (lz << 4) - pz;
                final double dMaxZ = dMinZ + 16.0;

                int sz = (originBlockZ >> 4) + lz - 1;

                for (int lx = 0; lx < 3; lx++) {
                    final double dMinX = originBlockX + (lx << 4) - px;
                    final double dMaxX = dMinX + 16.0;

                    int sx = (originBlockX >> 4) + lx - 1;

                    final int sectionIdx = ly * 9 + lz * 3 + lx;

                    final byte gState = CullifyMod.classifySection(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, grassDist);
                    cullify$grassCache[sectionIdx]  = gState;
                    // Equal thresholds classify identically — reuse instead of recomputing
                    cullify$flowerCache[sectionIdx] = flowerDist == grassDist ? gState
                            : CullifyMod.classifySection(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, flowerDist);
                    cullify$otherCache[sectionIdx]  = otherDist == grassDist ? gState
                            : (otherDist == flowerDist ? cullify$flowerCache[sectionIdx]
                            : CullifyMod.classifySection(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, otherDist));

                    // Light-Aware Culling: cache light factor
                    if (lightAware) {
                        cullify$lightFactors[sectionIdx] =
                                lightFactors.get(net.minecraft.core.SectionPos.asLong(sx, sy, sz));
                    } else {
                        cullify$lightFactors[sectionIdx] = CullifyMod.NEUTRAL_LIGHT_FACTOR;
                    }
                }
            }
        }

        // Update configVersion snapshot and mark cache valid
        cullify$lastConfigVersion = CullifyMod.configVersion;
        cullify$cacheValid = true;

        if (benchmark) {
            BenchmarkManager.cacheRebuildTimeNanos.add(System.nanoTime() - start);
            BenchmarkManager.cacheRebuilds.increment();
        }
    }

    // Retrieve cached culling status for a plant type
    @Unique
    private byte cullify$getCacheState(CullifyMod.PlantType type, int sectionIdx) {
        switch (type) {
            case GRASS:
                return cullify$grassCache[sectionIdx];
            case FLOWER:
                return cullify$flowerCache[sectionIdx];
            case OTHER:
                return cullify$otherCache[sectionIdx];
            default:
                return FULLY_KEPT;
        }
    }
}
