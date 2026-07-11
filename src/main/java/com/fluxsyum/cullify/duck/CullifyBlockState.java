package com.fluxsyum.cullify.duck;

import com.fluxsyum.cullify.CullifyMod;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Duck interface injected into BlockBehaviour.BlockStateBase via Mixin.
 * Provides direct field access for Cullify properties on every BlockState,
 * eliminating the need for ConcurrentHashMap lookups during chunk meshing.
 */
public interface CullifyBlockState {

    /**
     * Returns the cached PlantType for this BlockState.
     * Computed lazily on first access and stored permanently.
     */
    CullifyMod.PlantType cullify$getPlantType();

    /**
     * Returns whether this BlockState contains a fluid (e.g. waterlogged seagrass).
     * Cached lazily.
     */
    boolean cullify$hasFluid();

    /**
     * Returns the BlockState of the fluid contained in this block.
     * For waterlogged blocks, returns WATER. For non-fluid blocks, returns AIR.
     * Cached lazily.
     */
     BlockState cullify$getFluidBlockState();

    /**
     * Returns whether this BlockState is the UPPER half of a double block.
     * Cached lazily.
     */
    boolean cullify$isDoubleBlockUpperHalf();
}
