package com.fluxsyum.cullify.mixin;

import dev.engine_room.vanillin.neoforge.mixin.item.ItemColorsAccessor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemColors.class)
public interface ItemColorsAccessorMixin extends ItemColorsAccessor {
    @Accessor("itemColors")
    Map<Item, ItemColor> vanillin$itemColors();
}
