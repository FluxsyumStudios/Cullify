package com.fluxsyum.cullify.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that counts draw calls per section under Vanilla renderer.
 * Separated from MixinLevelRenderer to avoid injection preparation failures and crashes
 * when Sodium/Embeddium is loaded.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererDrawCalls {

    @Inject(method = "renderSectionLayer",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void cullify$onDrawSection(CallbackInfo ci) {
        com.fluxsyum.cullify.CullifyDebugManager.drawCalls.increment();
    }
}
