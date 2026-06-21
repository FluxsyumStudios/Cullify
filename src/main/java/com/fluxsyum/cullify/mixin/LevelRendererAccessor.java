package com.fluxsyum.cullify.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @org.spongepowered.asm.mixin.gen.Accessor("viewArea")
    net.minecraft.client.renderer.ViewArea getViewArea();

    @Invoker("setSectionDirty")
    void invokeSetSectionDirty(int x, int y, int z, boolean reRenderImmediately);
}
