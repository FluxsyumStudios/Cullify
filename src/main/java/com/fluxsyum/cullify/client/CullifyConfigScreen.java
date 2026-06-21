package com.fluxsyum.cullify.client;

import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CullifyConfigScreen extends Screen {
    private final Screen parent;

    public CullifyConfigScreen(Screen parent) {
        super(Component.translatable("cullify.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int btnW = 150;
        int btnH = 20;
        int col1X = centerX - 155;
        int col2X = centerX + 5;

        // Left column: global toggles
        this.addRenderableWidget(Button.builder(
            getEnabledText(),
            btn -> {
                CullifyConfig.ENABLED.set(!CullifyConfig.ENABLED.get());
                btn.setMessage(getEnabledText());
                onConfigChange();
            }
        ).bounds(col1X, centerY - 45, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            getDebugModeText(),
            btn -> {
                CullifyConfig.DEBUG_MODE.set(!CullifyConfig.DEBUG_MODE.get());
                btn.setMessage(getDebugModeText());
                onConfigChange();
            }
        ).bounds(col1X, centerY - 15, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            getShapeText(),
            btn -> {
                CullifyConfig.CullingShape current = CullifyConfig.CULLING_SHAPE.get();
                CullifyConfig.CullingShape[] values = CullifyConfig.CullingShape.values();
                int nextIdx = (current.ordinal() + 1) % values.length;
                CullifyConfig.CullingShape next = values[nextIdx];
                CullifyConfig.CULLING_SHAPE.set(next);
                btn.setMessage(getShapeText());
                onConfigChange();
            }
        ).bounds(col1X, centerY + 15, btnW, btnH).build());

        // Right column: vegetation types culling and distances
        this.addRenderableWidget(Button.builder(
            getGrassText(),
            btn -> {
                CullifyConfig.CULL_GRASS.set(!CullifyConfig.CULL_GRASS.get());
                btn.setMessage(getGrassText());
                onConfigChange();
            }
        ).bounds(col2X, centerY - 60, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            getGrassDistText(),
            btn -> {
                int nextDist = cycleDistance(CullifyConfig.GRASS_CULL_DISTANCE.get());
                CullifyConfig.GRASS_CULL_DISTANCE.set(nextDist);
                btn.setMessage(getGrassDistText());
                onConfigChange();
            }
        ).bounds(col2X, centerY - 38, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            getFlowersText(),
            btn -> {
                CullifyConfig.CULL_FLOWERS.set(!CullifyConfig.CULL_FLOWERS.get());
                btn.setMessage(getFlowersText());
                onConfigChange();
            }
        ).bounds(col2X, centerY - 10, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            getFlowersDistText(),
            btn -> {
                int nextDist = cycleDistance(CullifyConfig.FLOWER_CULL_DISTANCE.get());
                CullifyConfig.FLOWER_CULL_DISTANCE.set(nextDist);
                btn.setMessage(getFlowersDistText());
                onConfigChange();
            }
        ).bounds(col2X, centerY + 12, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            getOtherText(),
            btn -> {
                CullifyConfig.CULL_OTHER_PLANTS.set(!CullifyConfig.CULL_OTHER_PLANTS.get());
                btn.setMessage(getOtherText());
                onConfigChange();
            }
        ).bounds(col2X, centerY + 40, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            getOtherDistText(),
            btn -> {
                int nextDist = cycleDistance(CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get());
                CullifyConfig.OTHER_PLANT_CULL_DISTANCE.set(nextDist);
                btn.setMessage(getOtherDistText());
                onConfigChange();
            }
        ).bounds(col2X, centerY + 62, btnW, btnH).build());

        // Back / Close button
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.done"),
            btn -> this.onClose()
        ).bounds(centerX - 75, centerY + 95, 150, 20).build());
    }

    private void onConfigChange() {
        CullifyConfig.SPEC.save();
        CullifyMod.incrementConfigVersion();
        CullifyMod.scheduleWorldReload();
    }

    private int cycleDistance(int current) {
        if (current < 32) return 32;
        if (current < 48) return 48;
        if (current < 64) return 64;
        if (current < 96) return 96;
        if (current < 128) return 128;
        if (current < 192) return 192;
        if (current < 256) return 256;
        return 16;
    }

    private Component getEnabledText() {
        return Component.literal("Culling: " + (CullifyConfig.ENABLED.get() ? "§aENABLED" : "§cDISABLED"));
    }

    private Component getDebugModeText() {
        return Component.literal("Debug HUD: " + (CullifyConfig.DEBUG_MODE.get() ? "§aON" : "§cOFF"));
    }

    private Component getShapeText() {
        CullifyConfig.CullingShape shape = CullifyConfig.CULLING_SHAPE.get();
        String color;
        switch (shape) {
            case SPHERE: color = "§a"; break; // Light Green
            case CYLINDER: color = "§b"; break; // Aqua/Cyan
            case BOX: color = "§6"; break; // Gold/Orange
            case TRIANGLE: color = "§c"; break; // Light Red
            case HEXAGON: color = "§e"; break; // Yellow
            case STAR: color = "§d"; break; // Light Purple
            case SQUARE: color = "§5"; break; // Purple
            case CIRCLE: color = "§3"; break; // Dark Aqua
            default: color = "§f"; break;
        }
        return Component.literal("Shape: " + color + shape.name());
    }

    private Component getGrassText() {
        return Component.literal("Cull Grass: " + (CullifyConfig.CULL_GRASS.get() ? "§aON" : "§cOFF"));
    }

    private Component getGrassDistText() {
        return Component.literal("  └ Distance: §e" + CullifyConfig.GRASS_CULL_DISTANCE.get() + "m");
    }

    private Component getFlowersText() {
        return Component.literal("Cull Flowers: " + (CullifyConfig.CULL_FLOWERS.get() ? "§aON" : "§cOFF"));
    }

    private Component getFlowersDistText() {
        return Component.literal("  └ Distance: §e" + CullifyConfig.FLOWER_CULL_DISTANCE.get() + "m");
    }

    private Component getOtherText() {
        return Component.literal("Cull Other/Algae: " + (CullifyConfig.CULL_OTHER_PLANTS.get() ? "§aON" : "§cOFF"));
    }

    private Component getOtherDistText() {
        return Component.literal("  └ Distance: §e" + CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get() + "m");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Render central dialog box
        graphics.fill(centerX - 170, centerY - 85, centerX + 170, centerY + 85, 0xAA101010);
        graphics.fill(centerX - 170, centerY - 85, centerX + 170, centerY - 84, 0x44FFFFFF);
        graphics.fill(centerX - 170, centerY + 84, centerX + 170, centerY + 85, 0x44FFFFFF);
        graphics.fill(centerX - 170, centerY - 85, centerX - 169, centerY + 85, 0x44FFFFFF);
        graphics.fill(centerX + 169, centerY - 85, centerX + 170, centerY + 85, 0x44FFFFFF);

        graphics.drawString(this.font, "§2§lCullify §r§fConfig Menu", centerX - 150, centerY - 77, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "§7Version Alpha", centerX + 70, centerY - 77, 0xFFFFFFFF, false);
        
        graphics.fill(centerX - 160, centerY - 67, centerX + 160, centerY - 66, 0x33FFFFFF);

        graphics.drawCenteredString(this.font, "§8Press Key [K] or use /cullify menu in chat", centerX, centerY + 73, 0xFFFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        super.renderBackground(graphics);
    }
}
