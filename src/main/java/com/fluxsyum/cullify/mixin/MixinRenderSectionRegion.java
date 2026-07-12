package com.fluxsyum.cullify.mixin;

import com.fluxsyum.cullify.CullifyMod;
import com.fluxsyum.cullify.duck.CullifyBlockState;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fallback culling for vanilla's RenderChunkRegion (non-Sodium path).
 * Uses primitive coordinates and duck interface for fast plant type lookups.
 * Replaces culled plants with their fluid state when waterlogged.
 */
@Mixin(RenderChunkRegion.class)
public class MixinRenderSectionRegion {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void cullify$onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) return;

        // Quick check: skip non-plant blocks via duck interface (O(1) field read)
        CullifyMod.PlantType type = ((CullifyBlockState) (Object) state).cullify$getPlantType();
        if (type == CullifyMod.PlantType.NONE) return;

        if (CullifyMod.shouldCull(state, pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(CullifyMod.getCulledState(state));
        }
    }
}
