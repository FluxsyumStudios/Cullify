package com.fluxsyum.cullify.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin that conditionally applies Sodium/Embeddium-specific mixins
 * only when those classes or mods are actually present in the game environment.
 */
public class CullifyMixinConfigPlugin implements IMixinConfigPlugin {

    private static Boolean hasSodiumCached = null;

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, CullifyMixinConfigPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean detectSodium() {
        if (hasSodiumCached != null) {
            return hasSodiumCached;
        }

        boolean found = false;

        // 1. Try LoadingModList (standard Forge early detection)
        try {
            net.minecraftforge.fml.loading.LoadingModList list = net.minecraftforge.fml.loading.LoadingModList.get();
            if (list != null && (list.getModFileById("sodium") != null || list.getModFileById("embeddium") != null)) {
                found = true;
            }
        } catch (Throwable ignored) {}

        // 2. Class presence check (safe Class.forName check)
        if (!found) {
            found = isClassPresent("me.jellysquid.mods.sodium.client.world.WorldSlice")
                 || isClassPresent("org.embeddedt.embeddium.client.world.WorldSlice")
                 || isClassPresent("me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext");
        }

        // Cache only if LoadingModList is initialized, or if found is true.
        // If not found, and LoadingModList might not be ready yet, don't cache 'false' too early.
        boolean loadingModListReady = false;
        try {
            loadingModListReady = (net.minecraftforge.fml.loading.LoadingModList.get() != null);
        } catch (Throwable ignored) {}

        if (found || loadingModListReady) {
            hasSodiumCached = found;
        }

        return found;
    }

    @Override
    public void onLoad(String mixinPackage) {
        // Do NOT trigger detection in onLoad because classloaders and LoadingModList are not fully initialized yet.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean hasSodium = detectSodium();

        // MixinLevelSlice: only apply when Embeddium/Sodium is present
        if (mixinClassName.contains("MixinLevelSlice")) {
            return hasSodium;
        }

        // MixinLevelRendererDrawCalls: only apply for vanilla renderer (no Sodium)
        if (mixinClassName.contains("MixinLevelRendererDrawCalls")) {
            return !hasSodium;
        }

        // All other mixins always apply
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
