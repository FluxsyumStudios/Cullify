package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyMod;
import com.fluxsyum.cullify.duck.CullifyBlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
 * but since the result is deterministic and reference writes are atomic in Java,
 * no synchronization is needed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockState implements CullifyBlockState {

    @Unique
    private volatile CullifyMod.PlantType cullify$plantType;

    @Unique
    private volatile Boolean cullify$hasFluidCached;

    @Unique
    private volatile BlockState cullify$fluidBlockStateCached;

    @Unique
    private volatile Boolean cullify$isDoubleBlockUpperHalfCached;

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
        Boolean cached = this.cullify$hasFluidCached;
        if (cached == null) {
            BlockState state = (BlockState) (Object) this;
            cached = !state.getFluidState().isEmpty();
            this.cullify$hasFluidCached = cached;
        }
        return cached;
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
        Boolean cached = this.cullify$isDoubleBlockUpperHalfCached;
        if (cached == null) {
            BlockState state = (BlockState) (Object) this;
            cached = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                     state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            this.cullify$isDoubleBlockUpperHalfCached = cached;
        }
        return cached;
    }
}
