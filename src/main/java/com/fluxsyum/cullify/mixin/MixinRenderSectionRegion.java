package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyMod;
import com.fluxsyum.cullify.duck.CullifyBlockState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fallback culling for vanilla's RenderSectionRegion (non-Sodium path).
 * Uses primitive coordinates and duck interface for fast plant type lookups.
 * Replaces culled plants with their fluid state when waterlogged.
 *
 * Uses {@link ModifyReturnValue} rather than a cancellable {@code @Inject} to avoid
 * allocating a CallbackInfoReturnable on every block read during meshing.
 */
@Mixin(RenderSectionRegion.class)
public class MixinRenderSectionRegion {

    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState cullify$onGetBlockState(BlockState state, BlockPos pos) {
        if (state == null) return state;

        // Quick check: skip non-plant blocks via duck interface (O(1) field read)
        CullifyMod.PlantType type = ((CullifyBlockState) (Object) state).cullify$getPlantType();
        if (type == CullifyMod.PlantType.NONE) return state;

        if (CullifyMod.shouldCull(state, pos.getX(), pos.getY(), pos.getZ())) {
            return CullifyMod.getCulledState(state);
        }
        return state;
    }
}
