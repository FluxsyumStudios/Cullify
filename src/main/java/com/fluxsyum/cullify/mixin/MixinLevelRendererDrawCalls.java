package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyDebugManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that counts draw calls per render layer under Vanilla renderer.
 * Separated from MixinLevelRenderer to avoid injection preparation failures
 * when Sodium/Embeddium is loaded (the config plugin skips this mixin then).
 *
 * In MC 1.20.1, VertexBuffer.draw()V no longer exists — the API changed.
 * We inject at TAIL of renderChunkLayer instead, which fires once per layer draw,
 * giving an accurate call count without brittle obfuscation-dependent INVOKE targets.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererDrawCalls {

    @Inject(
        method = "renderChunkLayer",
        at = @At("TAIL")
    )
    private void cullify$onRenderChunkLayerTail(
            RenderType renderType,
            PoseStack poseStack,
            double camX, double camY, double camZ,
            Matrix4f projMatrix,
            CallbackInfo ci) {
        CullifyDebugManager.drawCalls.increment();
    }
}
