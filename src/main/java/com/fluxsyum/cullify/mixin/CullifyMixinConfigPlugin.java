package com.fluxsyum.cullify.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin that conditionally applies Sodium-specific mixins
 * only when Sodium's classes are actually present in the classpath.
 *
 * Uses Class.forName with initialize=false for reliable detection that works
 * across all environments (dev, production, different classloaders).
 */
public class CullifyMixinConfigPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean hasSodium = false;
        try {
            Class.forName("net.caffeinemc.mods.sodium.neoforge.SodiumForgeMod", false, this.getClass().getClassLoader());
            hasSodium = true;
        } catch (Throwable ignored) {
        }

        if (mixinClassName.contains("MixinLevelSlice")) {
            return hasSodium;
        }
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
