package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyDebugManager;
import com.fluxsyum.cullify.CullifyMod;
import com.fluxsyum.cullify.duck.CullifyBlockState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimizes culling for Sodium's LevelSlice by classifying the 27 adjacent chunk sections
 * into FULLY_CULLED, FULLY_KEPT, or STRADDLING states to avoid per-block calculations.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice")
public class MixinLevelSlice {

    @Shadow(remap = false) private int originX;
    @Shadow(remap = false) private int originY;
    @Shadow(remap = false) private int originZ;

    private static final byte STRADDLING = 0;
    private static final byte FULLY_KEPT = 1;
    private static final byte FULLY_CULLED = 2;

    @Unique private final byte[] cullify$grassCache = new byte[27];
    @Unique private final byte[] cullify$flowerCache = new byte[27];
    @Unique private final byte[] cullify$otherCache = new byte[27];
    @Unique private final float[] cullify$lightFactors = new float[27];

    @Unique private volatile boolean cullify$cacheValid = false;
    @Unique private volatile int cullify$lastConfigVersion = -1;

    // Rebuild cache when a new section is copied into LevelSlice
    @Inject(method = "copyData", at = @At("TAIL"), remap = false)
    private void cullify$onCopyData(CallbackInfo ci) {
        cullify$cacheValid = false;
        CullifyDebugManager.mixinLevelSliceApplied = true;
        if (CullifyMod.cachedEnabled && CullifyMod.hasPlayer) {
            cullify$rebuildCache();
        }
    }

    // Invalidate cache on reset
    @Inject(method = "reset", at = @At("HEAD"), remap = false)
    private void cullify$onReset(CallbackInfo ci) {
        cullify$cacheValid = false;
    }

    // Primary getBlockState hook for block coordinate lookup
    @Inject(method = {"getBlockState", "m_62982_"},
            at = @At("RETURN"), cancellable = true, remap = false)
    private void cullify$onGetBlockStateCoords(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (!CullifyMod.cachedEnabled || !CullifyMod.hasPlayer) {
            return;
        }

        BlockState state = cir.getReturnValue();
        if (state == null) return;

        CullifyMod.PlantType type = ((CullifyBlockState) (Object) state).cullify$getPlantType();
        if (type == CullifyMod.PlantType.NONE) return;

        int lx = (x - originX) >> 4;
        int ly = (y - originY) >> 4;
        int lz = (z - originZ) >> 4;

        boolean inBounds = lx >= 0 && lx <= 2 && ly >= 0 && ly <= 2 && lz >= 0 && lz <= 2;
        int sectionIdx = inBounds ? (ly * 9 + lz * 3 + lx) : -1;

        boolean benchmark = com.fluxsyum.cullify.benchmark.BenchmarkManager.isRunning();
        if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.blocksTested.increment();

        if (cullify$cacheValid && cullify$lastConfigVersion == CullifyMod.configVersion && inBounds) {
            byte cacheState = cullify$getCacheState(type, sectionIdx);

            if (cacheState == FULLY_CULLED) {
                CullifyDebugManager.culledBlocks.increment();
                if (benchmark) {
                    com.fluxsyum.cullify.benchmark.BenchmarkManager.blocksCulled.increment();
                    com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheHits.increment();
                }
                cir.setReturnValue(CullifyMod.getCulledState(state));
                return;
            } else if (cacheState == FULLY_KEPT) {
                if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheHits.increment();
                return;
            }
            if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheMisses.increment();
        } else {
            if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheMisses.increment();
        }

        float lightFactor = 1.0f;
        if (inBounds) {
            lightFactor = cullify$lightFactors[sectionIdx];
        } else if (CullifyMod.cachedLightAware) {
            int sx = x >> 4;
            int sy = y >> 4;
            int sz = z >> 4;
            long secKey = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
            Float factor = CullifyMod.sectionLightFactors.get(secKey);
            if (factor != null) {
                lightFactor = factor;
            }
        }

        if (CullifyMod.shouldCull(state, x, y, z, lightFactor)) {
            if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.blocksCulled.increment();
            cir.setReturnValue(CullifyMod.getCulledState(state));
        }
    }

    // Secondary getBlockState hook for BlockPos lookup
    @Inject(method = {"getBlockState", "m_8055_"},
            at = @At("RETURN"), cancellable = true, remap = false)
    private void cullify$onGetBlockStatePos(net.minecraft.core.BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!CullifyMod.cachedEnabled || !CullifyMod.hasPlayer) {
            return;
        }

        BlockState state = cir.getReturnValue();
        if (state == null) return;

        CullifyMod.PlantType type = ((CullifyBlockState) (Object) state).cullify$getPlantType();
        if (type == CullifyMod.PlantType.NONE) return;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        int lx = (x - originX) >> 4;
        int ly = (y - originY) >> 4;
        int lz = (z - originZ) >> 4;

        boolean inBounds = lx >= 0 && lx <= 2 && ly >= 0 && ly <= 2 && lz >= 0 && lz <= 2;
        int sectionIdx = inBounds ? (ly * 9 + lz * 3 + lx) : -1;

        boolean benchmark = com.fluxsyum.cullify.benchmark.BenchmarkManager.isRunning();
        if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.blocksTested.increment();

        if (cullify$cacheValid && cullify$lastConfigVersion == CullifyMod.configVersion && inBounds) {
            byte cacheState = cullify$getCacheState(type, sectionIdx);

            if (cacheState == FULLY_CULLED) {
                CullifyDebugManager.culledBlocks.increment();
                if (benchmark) {
                    com.fluxsyum.cullify.benchmark.BenchmarkManager.blocksCulled.increment();
                    com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheHits.increment();
                }
                cir.setReturnValue(CullifyMod.getCulledState(state));
                return;
            } else if (cacheState == FULLY_KEPT) {
                if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheHits.increment();
                return;
            }
            if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheMisses.increment();
        } else {
            if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheMisses.increment();
        }

        float lightFactor = 1.0f;
        if (inBounds) {
            lightFactor = cullify$lightFactors[sectionIdx];
        } else if (CullifyMod.cachedLightAware) {
            int sx = x >> 4;
            int sy = y >> 4;
            int sz = z >> 4;
            long secKey = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
            Float factor = CullifyMod.sectionLightFactors.get(secKey);
            if (factor != null) {
                lightFactor = factor;
            }
        }

        if (CullifyMod.shouldCull(state, x, y, z, lightFactor)) {
            if (benchmark) com.fluxsyum.cullify.benchmark.BenchmarkManager.blocksCulled.increment();
            cir.setReturnValue(CullifyMod.getCulledState(state));
        }
    }

    // Rebuild the AABB distance cache for the 27 sections
    @Unique
    private void cullify$rebuildCache() {
        boolean benchmark = com.fluxsyum.cullify.benchmark.BenchmarkManager.isRunning();
        long start = benchmark ? System.nanoTime() : 0;

        final double px = CullifyMod.playerX;
        final double py = CullifyMod.playerY;
        final double pz = CullifyMod.playerZ;

        final double grassDist = CullifyMod.getCullDistance(CullifyMod.PlantType.GRASS);
        final double flowerDist = CullifyMod.getCullDistance(CullifyMod.PlantType.FLOWER);
        final double otherDist = CullifyMod.getCullDistance(CullifyMod.PlantType.OTHER);

        for (int ly = 0; ly < 3; ly++) {
            // Section bounds as signed offsets relative to the camera
            final double dMinY = originY + (ly << 4) - py;
            final double dMaxY = dMinY + 16.0;

            int sy = (originY >> 4) + ly - 1;

            for (int lz = 0; lz < 3; lz++) {
                final double dMinZ = originZ + (lz << 4) - pz;
                final double dMaxZ = dMinZ + 16.0;

                int sz = (originZ >> 4) + lz - 1;

                for (int lx = 0; lx < 3; lx++) {
                    final double dMinX = originX + (lx << 4) - px;
                    final double dMaxX = dMinX + 16.0;

                    int sx = (originX >> 4) + lx - 1;

                    final int sectionIdx = ly * 9 + lz * 3 + lx;

                    final byte gState = CullifyMod.classifySection(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, grassDist);
                    cullify$grassCache[sectionIdx] = gState;
                    // Equal thresholds classify identically — reuse instead of recomputing
                    cullify$flowerCache[sectionIdx] = flowerDist == grassDist ? gState
                            : CullifyMod.classifySection(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, flowerDist);
                    cullify$otherCache[sectionIdx] = otherDist == grassDist ? gState
                            : (otherDist == flowerDist ? cullify$flowerCache[sectionIdx]
                            : CullifyMod.classifySection(dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ, otherDist));

                    if (CullifyMod.cachedLightAware) {
                        long secKey = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
                        Float factor = CullifyMod.sectionLightFactors.get(secKey);
                        cullify$lightFactors[sectionIdx] = factor != null ? factor : 1.0f;
                    } else {
                        cullify$lightFactors[sectionIdx] = 1.0f;
                    }
                }
            }
        }

        cullify$lastConfigVersion = CullifyMod.configVersion;
        cullify$cacheValid = true;

        if (benchmark) {
            com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheRebuildTimeNanos.add(System.nanoTime() - start);
            com.fluxsyum.cullify.benchmark.BenchmarkManager.cacheRebuilds.increment();
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
