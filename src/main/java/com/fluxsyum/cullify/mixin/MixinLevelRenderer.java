package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the block selection outline (hitbox wireframe) from rendering
 * on vegetation blocks that have been visually culled by Cullify.
 *
 * Without this fix, players would see a floating wireframe outline when
 * looking at an invisible (culled) grass or flower block, which breaks
 * visual consistency.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    private ClientLevel level;

    @Inject(method = "renderHitOutline",
            at = @At("HEAD"),
            cancellable = true)
    private void cullify$onRenderHitOutline(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            double cameraX, double cameraY, double cameraZ,
            BlockOutlineRenderState outlineState,
            int lightmap,
            float partialTick,
            CallbackInfo ci) {

        // Cheap cached-field early-out before touching the level (runs every frame)
        if (!CullifyMod.cachedEnabled || !CullifyMod.hasPlayer || this.level == null) return;

        BlockPos blockPos = outlineState.pos();
        BlockState blockState = this.level.getBlockState(blockPos);

        if (CullifyMod.shouldCullNoCount(blockState, blockPos)) {
            ci.cancel();
        }
    }
}
