package com.fluxsyum.cullify.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin that conditionally applies Sodium/Embeddium-specific mixins
 * only when those classes are actually present on the classpath.
 *
 * IMPORTANT: shouldApplyMixin is called during class transformation, BEFORE FML's
 * LoadingModList is populated. We MUST use Class.forName for detection — using
 * LoadingModList.get() here will always return null/empty and break detection.
 *
 * Class.forName with initialize=false is safe at transformation time because the
 * class files are already on the classpath even before they are initialized.
 */
public class CullifyMixinConfigPlugin implements IMixinConfigPlugin {

    /**
     * Detected once on first shouldApplyMixin call. Uses tri-state:
     * null = not checked yet, TRUE = Sodium/Embeddium present, FALSE = absent.
     */
    private static Boolean hasSodiumCached = null;

    private static boolean isClassPresent(String className) {
        try {
            String path = className.replace('.', '/') + ".class";
            ClassLoader cl = CullifyMixinConfigPlugin.class.getClassLoader();
            if (cl != null && cl.getResource(path) != null) {
                return true;
            }
            cl = Thread.currentThread().getContextClassLoader();
            if (cl != null && cl.getResource(path) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean detectSodium() {
        if (hasSodiumCached != null) {
            return hasSodiumCached;
        }

        // Primary check: Embeddium (Forge port of Sodium) uses the same me.jellysquid package
        // for version 0.3.x which is the standard 1.20.1 Embeddium release.
        boolean found = false;

        // Check 1: Embeddium / Sodium LevelSlice class (primary signal)
        if (!found) {
            found = isClassPresent("me.jellysquid.mods.sodium.client.world.LevelSlice");
        }

        // Check 2: Embeddium newer package namespace (future-proofing)
        if (!found) {
            found = isClassPresent("org.embeddedt.embeddium.client.world.LevelSlice");
        }

        // Check 3: Any Embeddium/Sodium render region class as fallback
        if (!found) {
            found = isClassPresent("me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext");
        }

        // Check 4: Try FML LoadingModList as a last resort (may be null this early, but worth trying)
        if (!found) {
            try {
                net.minecraftforge.fml.loading.LoadingModList list = net.minecraftforge.fml.loading.LoadingModList.get();
                if (list != null && (list.getModFileById("sodium") != null
                        || list.getModFileById("embeddium") != null)) {
                    found = true;
                }
            } catch (Throwable ignored) {}
        }

        hasSodiumCached = found;
        return found;
    }

    @Override
    public void onLoad(String mixinPackage) {
        // Trigger detection early so it's ready for shouldApplyMixin calls
        detectSodium();
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
