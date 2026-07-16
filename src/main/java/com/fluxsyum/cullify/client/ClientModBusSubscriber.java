package com.fluxsyum.cullify.client;

import com.fluxsyum.cullify.CullifyDebugManager;
import com.fluxsyum.cullify.CullifyMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public class ClientModBusSubscriber {

    private static final Identifier LOGO_RL = CullifyMod.LOGO;
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
            net.minecraft.client.KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cullify", "cullify"))
        );
        event.register(configKeyMapping);
    }

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR,
            Identifier.fromNamespaceAndPath(CullifyMod.MOD_ID, "debug_hud"),
            (graphics, deltaTracker) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.hideGui || mc.gameMode == null) {
                    return;
                }

                // Use debugLevel flag instead of CullifyConfig.DEBUG_MODE.get() every frame
                if (CullifyDebugManager.debugLevel == CullifyDebugManager.DebugLevel.OFF) {
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
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_RL, x + 8, y + 8, 0.0f, 0.0f, 16, 16, 16, 16);

                Font font = mc.font;
                graphics.text(font, "§e§lCullify Debug", x + 30, y + 12, 0xFFFFFFFF, false);
                graphics.fill(x + 8, y + 30, x + w - 8, y + 31, 0x33FFFFFF);

                // Read from cached fields — zero ModConfigSpec overhead per frame
                boolean enabled = CullifyMod.cachedEnabled;
                boolean sodium = CullifyDebugManager.sodiumDetected;
                
                String statusText = "Status: " + (enabled ? "§aEnabled" : "§cDisabled");
                String pathText = "Renderer: " + (sodium ? "§bSodium" : "§7Vanilla");

                String grassText = "Cull Grass: " + (CullifyMod.cachedCullGrass ? "§aON §7(" + (int)CullifyMod.cachedGrassDist + "m)" : "§cOFF");
                String flowersText = "Cull Flowers: " + (CullifyMod.cachedCullFlowers ? "§aON §7(" + (int)CullifyMod.cachedFlowerDist + "m)" : "§cOFF");
                String otherText = "Cull Other: " + (CullifyMod.cachedCullOther ? "§aON §7(" + (int)CullifyMod.cachedOtherDist + "m)" : "§cOFF");

                String hiddenText = "Hidden: §e" + CullifyDebugManager.lastCulledBlocks;
                String waterText = "Waterlogged: §9" + CullifyDebugManager.lastWaterReplacements;
                // Sodium batches via MDI and vanilla's 26.1 pipeline no longer exposes a
                // per-section draw call, so there is no meaningful number to show here.
                String drawsText = "Draws: §d" + (sodium ? "MDI" : "N/A");

                graphics.text(font, statusText, x + 10, y + 37, 0xFFFFFFFF, false);
                graphics.text(font, pathText, x + 10, y + 47, 0xFFFFFFFF, false);
                graphics.text(font, grassText, x + 10, y + 57, 0xFFFFFFFF, false);
                graphics.text(font, flowersText, x + 10, y + 67, 0xFFFFFFFF, false);
                graphics.text(font, otherText, x + 10, y + 77, 0xFFFFFFFF, false);
                
                graphics.fill(x + 8, y + 89, x + w - 8, y + 90, 0x22FFFFFF);
                
                graphics.text(font, hiddenText, x + 10, y + 95, 0xFFFFFFFF, false);
                graphics.text(font, waterText, x + 10, y + 105, 0xFFFFFFFF, false);
                graphics.text(font, drawsText, x + 10, y + 115, 0xFFFFFFFF, false);
            }
        );
    }
}


