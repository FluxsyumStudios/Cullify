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
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
public class MixinLevelSlice {

    @Shadow(remap = false) private int originBlockX;
    @Shadow(remap = false) private int originBlockY;
    @Shadow(remap = false) private int originBlockZ;

    private static final byte STRADDLING = 0;
    private static final byte FULLY_KEPT = 1;
    private static final byte FULLY_CULLED = 2;

    @Unique private final byte[] cullify$grassCache = new byte[27];
    @Unique private final byte[] cullify$flowerCache = new byte[27];
    @Unique private final byte[] cullify$otherCache = new byte[27];

    @Unique private boolean cullify$cacheValid = false;
    @Unique private int cullify$lastConfigVersion = -1;

    // Rebuild cache when a new section is copied into LevelSlice
    @Inject(method = "copyData", at = @At("TAIL"), remap = false)
    private void cullify$onCopyData(CallbackInfo ci) {
        cullify$cacheValid = false;
        CullifyDebugManager.mixinLevelSliceApplied = true;
        if (CullifyConfig.ENABLED.get() && CullifyMod.hasPlayer) {
            cullify$rebuildCache();
        }
    }

    // Invalidate cache on reset
    @Inject(method = "reset", at = @At("HEAD"), remap = false)
    private void cullify$onReset(CallbackInfo ci) {
        cullify$cacheValid = false;
    }

    // Primary getBlockState hook for block coordinate lookup
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void cullify$onGetBlockStateCoords(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (!CullifyConfig.ENABLED.get() || !CullifyMod.hasPlayer) {
            return;
        }

        BlockState state = cir.getReturnValue();
        if (state == null) return;

        CullifyMod.PlantType type = ((CullifyBlockState) (Object) state).cullify$getPlantType();
        if (type == CullifyMod.PlantType.NONE) return;

        if (!cullify$cacheValid || cullify$lastConfigVersion != CullifyMod.configVersion) {
            cullify$lastConfigVersion = CullifyMod.configVersion;
            cullify$rebuildCache();
        }

        int lx = (x - originBlockX) >> 4;
        int ly = (y - originBlockY) >> 4;
        int lz = (z - originBlockZ) >> 4;

        if (lx < 0 || lx > 2 || ly < 0 || ly > 2 || lz < 0 || lz > 2) {
            if (CullifyMod.shouldCull(state, x, y, z)) {
                cir.setReturnValue(CullifyMod.getCulledState(state));
            }
            return;
        }

        int sectionIdx = ly * 9 + lz * 3 + lx;
        byte cacheState = cullify$getCacheState(type, sectionIdx);

        if (cacheState == FULLY_CULLED) {
            CullifyDebugManager.culledBlocks.increment();
            cir.setReturnValue(CullifyMod.getCulledState(state));
        } else if (cacheState == STRADDLING) {
            if (CullifyMod.shouldCull(state, x, y, z)) {
                cir.setReturnValue(CullifyMod.getCulledState(state));
            }
        }
    }

    // Secondary getBlockState hook for BlockPos lookup
    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), cancellable = true, remap = true)
    private void cullify$onGetBlockStatePos(net.minecraft.core.BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!CullifyConfig.ENABLED.get() || !CullifyMod.hasPlayer) {
            return;
        }

        BlockState state = cir.getReturnValue();
        if (state == null) return;

        CullifyMod.PlantType type = ((CullifyBlockState) (Object) state).cullify$getPlantType();
        if (type == CullifyMod.PlantType.NONE) return;

        if (!cullify$cacheValid || cullify$lastConfigVersion != CullifyMod.configVersion) {
            cullify$lastConfigVersion = CullifyMod.configVersion;
            cullify$rebuildCache();
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int lx = (x - originBlockX) >> 4;
        int ly = (y - originBlockY) >> 4;
        int lz = (z - originBlockZ) >> 4;

        if (lx < 0 || lx > 2 || ly < 0 || ly > 2 || lz < 0 || lz > 2) {
            if (CullifyMod.shouldCull(state, x, y, z)) {
                cir.setReturnValue(CullifyMod.getCulledState(state));
            }
            return;
        }

        int sectionIdx = ly * 9 + lz * 3 + lx;
        byte cacheState = cullify$getCacheState(type, sectionIdx);

        if (cacheState == FULLY_CULLED) {
            CullifyDebugManager.culledBlocks.increment();
            cir.setReturnValue(CullifyMod.getCulledState(state));
        } else if (cacheState == STRADDLING) {
            if (CullifyMod.shouldCull(state, x, y, z)) {
                cir.setReturnValue(CullifyMod.getCulledState(state));
            }
        }
    }

    // Rebuild the AABB distance cache for the 27 sections
    @Unique
    private void cullify$rebuildCache() {
        double px = CullifyMod.playerX;
        double py = CullifyMod.playerY;
        double pz = CullifyMod.playerZ;

        double grassDist = CullifyMod.getCullDistance(CullifyMod.PlantType.GRASS);
        double flowerDist = CullifyMod.getCullDistance(CullifyMod.PlantType.FLOWER);
        double otherDist = CullifyMod.getCullDistance(CullifyMod.PlantType.OTHER);

        for (int ly = 0; ly < 3; ly++) {
            double minY = originBlockY + (ly << 4);
            double maxY = minY + 16.0;
            double nearDy = py < minY ? minY - py : (py > maxY ? py - maxY : 0.0);
            double farDy = py < minY + 8.0 ? maxY - py : py - minY;

            for (int lz = 0; lz < 3; lz++) {
                double minZ = originBlockZ + (lz << 4);
                double maxZ = minZ + 16.0;
                double nearDz = pz < minZ ? minZ - pz : (pz > maxZ ? pz - maxZ : 0.0);
                double farDz = pz < minZ + 8.0 ? maxZ - pz : pz - minZ;

                for (int lx = 0; lx < 3; lx++) {
                    double minX = originBlockX + (lx << 4);
                    double maxX = minX + 16.0;
                    double nearDx = px < minX ? minX - px : (px > maxX ? px - maxX : 0.0);
                    double farDx = px < minX + 8.0 ? maxX - px : px - minX;

                    int sectionIdx = ly * 9 + lz * 3 + lx;

                    cullify$grassCache[sectionIdx] = cullify$classify(nearDx, farDx, nearDy, farDy, nearDz, farDz, grassDist);
                    cullify$flowerCache[sectionIdx] = cullify$classify(nearDx, farDx, nearDy, farDy, nearDz, farDz, flowerDist);
                    cullify$otherCache[sectionIdx] = cullify$classify(nearDx, farDx, nearDy, farDy, nearDz, farDz, otherDist);
                }
            }
        }

        cullify$cacheValid = true;
    }

    // Classify a section's culling status based on distances and threshold
    @Unique
    private static byte cullify$classify(double nearDx, double farDx, double nearDy, double farDy, double nearDz, double farDz, double threshold) {
        if (threshold < 0) {
            return FULLY_KEPT;
        }

        CullifyConfig.CullingShape shape = CullifyConfig.CULLING_SHAPE.get();

        // Fast culling check (if the minimum distance is greater than the threshold, it is outside)
        if (shape == CullifyConfig.CullingShape.SPHERE) {
            double minDistSq = nearDx * nearDx + nearDy * nearDy + nearDz * nearDz;
            if (minDistSq > threshold * threshold) return FULLY_CULLED;
        } else if (shape == CullifyConfig.CullingShape.BOX) {
            double minDist = Math.max(nearDx, Math.max(nearDy, nearDz));
            if (minDist > threshold) return FULLY_CULLED;
        } else if (shape == CullifyConfig.CullingShape.SQUARE) {
            double minDist = Math.max(nearDx, nearDz);
            if (minDist > threshold) return FULLY_CULLED;
        } else { // CYLINDER, CIRCLE, TRIANGLE, HEXAGON, STAR
            double minDistSq = nearDx * nearDx + nearDz * nearDz;
            if (minDistSq > threshold * threshold) return FULLY_CULLED;
        }

        // For box shape, check if the entire AABB is contained within the shape
        if (shape == CullifyConfig.CullingShape.BOX) {
            double maxDist = Math.max(farDx, Math.max(farDy, farDz));
            if (maxDist <= threshold) {
                return FULLY_KEPT;
            }
            return STRADDLING;
        }

        // Check if the corners are contained in the shape (sufficient for 2D convex shapes)
        boolean c1 = CullifyMod.isInShape(nearDx, nearDy, nearDz, threshold, shape);
        boolean c2 = CullifyMod.isInShape(nearDx, nearDy, farDz, threshold, shape);
        boolean c3 = CullifyMod.isInShape(farDx, nearDy, nearDz, threshold, shape);
        boolean c4 = CullifyMod.isInShape(farDx, nearDy, farDz, threshold, shape);
        
        boolean fullyIn = c1 && c2 && c3 && c4;
        
        if (shape == CullifyConfig.CullingShape.SPHERE) {
            // For sphere, also validate the upper Y bound of the block section
            boolean c5 = CullifyMod.isInShape(nearDx, farDy, nearDz, threshold, shape);
            boolean c6 = CullifyMod.isInShape(nearDx, farDy, farDz, threshold, shape);
            boolean c7 = CullifyMod.isInShape(farDx, farDy, nearDz, threshold, shape);
            boolean c8 = CullifyMod.isInShape(farDx, farDy, farDz, threshold, shape);
            fullyIn = fullyIn && c5 && c6 && c7 && c8;
        }

        if (fullyIn) {
            return FULLY_KEPT;
        }

        return STRADDLING;
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

