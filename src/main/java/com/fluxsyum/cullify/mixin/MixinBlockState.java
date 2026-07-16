package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyMod;
import com.fluxsyum.cullify.duck.CullifyBlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Injects lazy-cached Cullify fields directly into every BlockState instance.
 *
 * This eliminates the ConcurrentHashMap lookup overhead during chunk meshing.
 * Each field is computed once on first access and stored permanently, since
 * BlockState objects are immutable singletons created at game startup.
 *
 * Thread safety: Multiple worker threads may compute the same value concurrently,
 * but since the result is deterministic and reference and byte writes are both atomic
 * in Java, no synchronization is needed. A thread that races and reads a not-yet-written
 * field simply recomputes the same value.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockState implements CullifyBlockState {

    @Unique
    private volatile CullifyMod.PlantType cullify$plantType;

    @Unique
    private byte cullify$hasFluidCached;

    @Unique
    private volatile BlockState cullify$fluidBlockStateCached;

    @Unique
    private byte cullify$isDoubleBlockUpperHalfCached;

    @Override
    @Unique
    public CullifyMod.PlantType cullify$getPlantType() {
        CullifyMod.PlantType type = this.cullify$plantType;
        if (type == null) {
            type = CullifyMod.computePlantType((BlockState) (Object) this);
            this.cullify$plantType = type;
        }
        return type;
    }

    @Override
    @Unique
    public boolean cullify$hasFluid() {
        byte cached = this.cullify$hasFluidCached;
        if (cached == CULLIFY_UNCOMPUTED) {
            BlockState state = (BlockState) (Object) this;
            boolean value = !state.getFluidState().isEmpty();
            cached = value ? CULLIFY_TRUE : CULLIFY_FALSE;
            this.cullify$hasFluidCached = cached;
        }
        return cached == CULLIFY_TRUE;
    }

    @Override
    @Unique
    public BlockState cullify$getFluidBlockState() {
        BlockState cached = this.cullify$fluidBlockStateCached;
        if (cached == null) {
            BlockState state = (BlockState) (Object) this;
            FluidState fluidState = state.getFluidState();
            if (fluidState.isEmpty()) {
                cached = Blocks.AIR.defaultBlockState();
            } else {
                cached = fluidState.createLegacyBlock();
            }
            this.cullify$fluidBlockStateCached = cached;
        }
        return cached;
    }

    @Override
    @Unique
    public boolean cullify$isDoubleBlockUpperHalf() {
        byte cached = this.cullify$isDoubleBlockUpperHalfCached;
        if (cached == CULLIFY_UNCOMPUTED) {
            BlockState state = (BlockState) (Object) this;
            boolean value = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
            cached = value ? CULLIFY_TRUE : CULLIFY_FALSE;
            this.cullify$isDoubleBlockUpperHalfCached = cached;
        }
        return cached == CULLIFY_TRUE;
    }
}
