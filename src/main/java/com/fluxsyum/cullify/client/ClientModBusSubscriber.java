package com.fluxsyum.cullify.client;

import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyDebugManager;
import com.fluxsyum.cullify.CullifyMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public class ClientModBusSubscriber {

    private static final ResourceLocation LOGO_RL = ResourceLocation.fromNamespaceAndPath(CullifyMod.MOD_ID, "textures/gui/logo.png");
    public static net.minecraft.client.KeyMapping configKeyMapping;

    // Registers mod event bus listeners manually to avoid deprecated EventBusSubscriber annotation
    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.addListener(ClientModBusSubscriber::registerGuiLayers);
        modEventBus.addListener(ClientModBusSubscriber::registerKeyMappings);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        configKeyMapping = new net.minecraft.client.KeyMapping(
            "key.cullify.config",
            com.mojang.blaze3d.platform.InputConstants.KEY_K,
            "key.categories.cullify"
        );
        event.register(configKeyMapping);
    }

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR,
            ResourceLocation.fromNamespaceAndPath(CullifyMod.MOD_ID, "debug_hud"),
            (graphics, deltaTracker) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.hideGui || mc.gameMode == null) {
                    return;
                }

                if (!CullifyConfig.DEBUG_MODE.get()) {
                    return;
                }

                int x = 10;
                int y = 10;
                int w = 185;
                int h = 125;

                // Render background card
                graphics.fill(x, y, x + w, y + h, 0xBB1E1E1E);
                
                // Border outline
                graphics.fill(x, y, x + w, y + 1, 0x44FFFFFF);
                graphics.fill(x, y + h - 1, x + w, y + h, 0x44FFFFFF);
                graphics.fill(x, y, x + 1, y + h, 0x44FFFFFF);
                graphics.fill(x + w - 1, y, x + w, y + h, 0x44FFFFFF);

                // Mod logo
                graphics.blit(LOGO_RL, x + 8, y + 8, 0.0f, 0.0f, 16, 16, 16, 16);

                Font font = mc.font;
                graphics.drawString(font, "§e§lCullify Debug", x + 30, y + 12, 0xFFFFFFFF, false);
                graphics.fill(x + 8, y + 30, x + w - 8, y + 31, 0x33FFFFFF);

                boolean enabled = CullifyConfig.ENABLED.get();
                boolean sodium = CullifyDebugManager.sodiumDetected;
                
                String statusText = "Status: " + (enabled ? "§aEnabled" : "§cDisabled");
                String pathText = "Renderer: " + (sodium ? "§bSodium" : "§7Vanilla");

                String grassText = "Cull Grass: " + (CullifyConfig.CULL_GRASS.get() ? "§aON §7(" + CullifyConfig.GRASS_CULL_DISTANCE.get() + "m)" : "§cOFF");
                String flowersText = "Cull Flowers: " + (CullifyConfig.CULL_FLOWERS.get() ? "§aON §7(" + CullifyConfig.FLOWER_CULL_DISTANCE.get() + "m)" : "§cOFF");
                String otherText = "Cull Other: " + (CullifyConfig.CULL_OTHER_PLANTS.get() ? "§aON §7(" + CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get() + "m)" : "§cOFF");

                String hiddenText = "Hidden: §e" + CullifyDebugManager.lastCulledBlocks;
                String waterText = "Waterlogged: §9" + CullifyDebugManager.lastWaterReplacements;
                String drawsText = "Draws: §d" + (sodium ? "MDI" : CullifyDebugManager.lastDrawCalls);

                graphics.drawString(font, statusText, x + 10, y + 37, 0xFFFFFFFF, false);
                graphics.drawString(font, pathText, x + 10, y + 47, 0xFFFFFFFF, false);
                graphics.drawString(font, grassText, x + 10, y + 57, 0xFFFFFFFF, false);
                graphics.drawString(font, flowersText, x + 10, y + 67, 0xFFFFFFFF, false);
                graphics.drawString(font, otherText, x + 10, y + 77, 0xFFFFFFFF, false);
                
                graphics.fill(x + 8, y + 89, x + w - 8, y + 90, 0x22FFFFFF);
                
                graphics.drawString(font, hiddenText, x + 10, y + 95, 0xFFFFFFFF, false);
                graphics.drawString(font, waterText, x + 10, y + 105, 0xFFFFFFFF, false);
                graphics.drawString(font, drawsText, x + 10, y + 115, 0xFFFFFFFF, false);
            }
        );
    }
}

