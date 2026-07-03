package com.fluxsyum.cullify.client;

import com.fluxsyum.cullify.ClientEventHandler;
import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class CullifyConfigScreen extends Screen {
    private final Screen parent;
    private int currentTab = 0; // 0 = General, 1 = Vegetation

    public CullifyConfigScreen(Screen parent) {
        super(Component.translatable("cullify.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Done / Close button placed cleanly below the main config panel
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.done"),
            btn -> this.onClose()
        ).bounds(centerX - 50, centerY + 98, 100, 20).build());
    }

    private void onConfigChange() {
        CullifyMod.updateConfigCache();
        CullifyMod.incrementConfigVersion();
        CullifyMod.voxelGridDirty = true;
        CullifyMod.scheduleWorldReload();
        ClientEventHandler.scheduleDebouncedSave();
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

    // Version-independent pixel-perfect Bresenham line drawing helper
    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    // Ellipse / circle outline renderer for wireframes
    private void drawEllipse(GuiGraphics graphics, int centerX, int centerY, int rx, int ry, int points, double rotation, int color) {
        for (int i = 0; i < points; i++) {
            double angle1 = i * 2 * Math.PI / points + rotation;
            double angle2 = (i + 1) * 2 * Math.PI / points + rotation;
            int px1 = centerX + (int) (rx * Math.cos(angle1));
            int py1 = centerY + (int) (ry * Math.sin(angle1));
            int px2 = centerX + (int) (rx * Math.cos(angle2));
            int py2 = centerY + (int) (ry * Math.sin(angle2));
            drawLine(graphics, px1, py1, px2, py2, color);
        }
    }

    private void drawCustomTooltip(GuiGraphics graphics, Component title, Component description, Component impactLabel, int colorImpact, int x, int y) {
        int maxWidth = 180;
        List<FormattedCharSequence> splitLines = this.font.split(description, maxWidth);
        
        int tooltipW = maxWidth + 12;
        int tooltipH = (splitLines.size() + 2) * 10 + 8; // title, description lines, performance impact
        
        int tx = x + 10;
        int ty = y + 10;
        if (tx + tooltipW > this.width) {
            tx = x - 10 - tooltipW;
        }
        if (ty + tooltipH > this.height) {
            ty = this.height - tooltipH - 5;
        }
        
        // Background dark glass box
        graphics.fill(tx, ty, tx + tooltipW, ty + tooltipH, 0xEE08090B);
        // Neon green outlines
        graphics.fill(tx, ty, tx + tooltipW, ty + 1, 0xFF2ECC71);
        graphics.fill(tx, ty + tooltipH - 1, tx + tooltipW, ty + tooltipH, 0xFF2ECC71);
        graphics.fill(tx, ty, tx + 1, ty + tooltipH, 0xFF2ECC71);
        graphics.fill(tx + tooltipW - 1, ty, tx + tooltipW, ty + tooltipH, 0xFF2ECC71);
        
        // Title text
        graphics.drawString(this.font, title, tx + 6, ty + 5, 0xFFFFFFFF, false);
        
        // Description lines
        for (int i = 0; i < splitLines.size(); i++) {
            graphics.drawString(this.font, splitLines.get(i), tx + 6, ty + 15 + (i * 10), 0xFF888888, false);
        }
        
        // Performance impact row
        int impactY = ty + 15 + (splitLines.size() * 10);
        graphics.drawString(this.font, Component.translatable("cullify.menu.tooltip.performance.impact"), tx + 6, impactY, 0xFF555555, false);
        graphics.drawString(this.font, impactLabel, tx + 6 + this.font.width(Component.translatable("cullify.menu.tooltip.performance.impact")), impactY, colorImpact, false);
    }

    private void drawToggleSwitch(GuiGraphics graphics, int x1, int y1, int x2, int y2, boolean value) {
        int height = y2 - y1;
        int trackW = 24;
        int trackH = 10;
        int tx = x2 - trackW - 6;
        int ty = y1 + (height - trackH) / 2;
        
        // Render switch track
        graphics.fill(tx, ty, tx + trackW, ty + trackH, value ? 0xFF1B5E20 : 0xFFB71C1C);
        
        // Render switch knob
        int knobSize = 12;
        int kx = value ? (tx + trackW - knobSize) : tx;
        int ky = ty + (trackH - knobSize) / 2;
        graphics.fill(kx, ky, kx + knobSize, ky + knobSize, value ? 0xFF2ECC71 : 0xFFE74C3C);
        
        // Render text label
        String txt = value ? "ON" : "OFF";
        int txtColor = value ? 0xFF2ECC71 : 0xFFE74C3C;
        graphics.drawString(this.font, txt, tx - 6 - this.font.width(txt), y1 + (height - 8) / 2, txtColor, false);
    }

    private void drawOptionCard(GuiGraphics graphics, int x1, int y1, int x2, int y2, Component title, boolean isHovered) {
        graphics.fill(x1, y1, x2, y2, isHovered ? 0x22FFFFFF : 0x11FFFFFF);
        // Soft border lines
        graphics.fill(x1, y1, x2, y1 + 1, 0x15FFFFFF);
        graphics.fill(x1, y2 - 1, x2, y2, 0x15FFFFFF);
        graphics.fill(x1, y1, x1 + 1, y2, 0x15FFFFFF);
        graphics.fill(x2 - 1, y1, x2, y2, 0x15FFFFFF);
        
        graphics.drawString(this.font, title, x1 + 8, y1 + (y2 - y1 - 8) / 2, 0xFFFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        int mainX1 = centerX - 180;
        int mainY1 = centerY - 95;
        int mainX2 = centerX + 180;
        int mainY2 = centerY + 90;
        
        // Draw Glassmorphism background box
        graphics.fill(mainX1, mainY1, mainX2, mainY2, 0xEE0B0C0E);
        
        // Neon green outlines
        graphics.fill(mainX1, mainY1, mainX2, mainY1 + 1, 0xFF2ECC71);
        graphics.fill(mainX1, mainY2 - 1, mainX2, mainY2, 0xFF2ECC71);
        graphics.fill(mainX1, mainY1, mainX1 + 1, mainY2, 0xFF2ECC71);
        graphics.fill(mainX2 - 1, mainY1, mainX2, mainY2, 0xFF2ECC71);
        
        // Sidebar dividing line
        int divX = centerX - 110;
        graphics.fill(divX, mainY1 + 5, divX + 1, mainY2 - 5, 0x22FFFFFF);
        
        // Sidebar Header
        graphics.drawString(this.font, "§2§lCullify", mainX1 + 10, mainY1 + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("cullify.menu.version", CullifyMod.VERSION), mainX1 + 10, mainY1 + 22, 0xFF555555, false);
        
        // Tabs placement coordinates
        int tabX1 = mainX1 + 5;
        int tabX2 = divX - 5;
        
        // Tab 0: General Settings
        int tab1Y1 = centerY - 55;
        int tab1Y2 = centerY - 35;
        boolean tab1Hovered = mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab1Y1 && mouseY <= tab1Y2;
        if (currentTab == 0) {
            graphics.fill(tabX1, tab1Y1, tabX1 + 2, tab1Y2, 0xFF2ECC71);
            graphics.fill(tabX1 + 2, tab1Y1, tabX2, tab1Y2, 0x222ECC71);
            graphics.drawString(this.font, Component.translatable("cullify.menu.tab.general"), tabX1 + 8, tab1Y1 + 6, 0xFFFFFFFF, false);
        } else {
            if (tab1Hovered) {
                graphics.fill(tabX1, tab1Y1, tabX2, tab1Y2, 0x11FFFFFF);
            }
            graphics.drawString(this.font, Component.translatable("cullify.menu.tab.general"), tabX1 + 8, tab1Y1 + 6, 0x88888888, false);
        }

        // Tab 1: Vegetation Settings
        int tab2Y1 = centerY - 30;
        int tab2Y2 = centerY - 10;
        boolean tab2Hovered = mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab2Y1 && mouseY <= tab2Y2;
        if (currentTab == 1) {
            graphics.fill(tabX1, tab2Y1, tabX1 + 2, tab2Y2, 0xFF2ECC71);
            graphics.fill(tabX1 + 2, tab2Y1, tabX2, tab2Y2, 0x222ECC71);
            graphics.drawString(this.font, Component.translatable("cullify.menu.tab.vegetation"), tabX1 + 8, tab2Y1 + 6, 0xFFFFFFFF, false);
        } else {
            if (tab2Hovered) {
                graphics.fill(tabX1, tab2Y1, tabX2, tab2Y2, 0x11FFFFFF);
            }
            graphics.drawString(this.font, Component.translatable("cullify.menu.tab.vegetation"), tabX1 + 8, tab2Y1 + 6, 0x88888888, false);
        }

        // Keep track of active hover tooltip
        Component activeTooltipTitle = null;
        Component activeTooltipDesc = null;
        Component activeTooltipImpact = null;
        int activeTooltipColor = 0xFF555555;

        // Render Active Tab Content
        if (currentTab == 0) {
            int cardX1 = divX + 10;
            int cardX2 = centerX + 30;
            
            // Enabled Toggle Card
            int card1Y1 = centerY - 60;
            int card1Y2 = centerY - 35;
            boolean card1Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2;
            drawOptionCard(graphics, cardX1, card1Y1, cardX2, card1Y2, Component.translatable("cullify.options.enabled"), card1Hovered);
            drawToggleSwitch(graphics, cardX1, card1Y1, cardX2, card1Y2, CullifyConfig.ENABLED.get());
            if (card1Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.enabled");
                activeTooltipDesc = Component.translatable("cullify.options.enabled.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.none");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Debug HUD Toggle Card
            int card2Y1 = centerY - 30;
            int card2Y2 = centerY - 5;
            boolean card2Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2;
            drawOptionCard(graphics, cardX1, card2Y1, cardX2, card2Y2, Component.translatable("cullify.options.debug_mode"), card2Hovered);
            drawToggleSwitch(graphics, cardX1, card2Y1, cardX2, card2Y2, CullifyConfig.DEBUG_MODE.get());
            if (card2Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.debug_mode");
                activeTooltipDesc = Component.translatable("cullify.options.debug_mode.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.none");
                activeTooltipColor = 0xFF2ECC71;
            }

            // LOD Density Card
            int card3Y1 = centerY + 5;
            int card3Y2 = centerY + 30;
            boolean card3Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2;
            drawOptionCard(graphics, cardX1, card3Y1, cardX2, card3Y2, Component.translatable("cullify.menu.lod_density"), card3Hovered);
            int d = CullifyConfig.LOD_DENSITY.get();
            String densityStr;
            int densityColor;
            if (d >= 100) { densityStr = "OFF"; densityColor = 0xFF2ECC71; }
            else if (d >= 75) { densityStr = "75%"; densityColor = 0xFFF1C40F; }
            else if (d >= 50) { densityStr = "50%"; densityColor = 0xFFE67E22; }
            else { densityStr = "25%"; densityColor = 0xFFE74C3C; }
            graphics.drawString(this.font, densityStr, cardX2 - 12 - this.font.width(densityStr), card3Y1 + 8, densityColor, false);
            if (card3Hovered) {
                activeTooltipTitle = Component.translatable("cullify.menu.lod_density");
                activeTooltipDesc = Component.translatable("cullify.menu.lod_density.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Culling Shape Card
            int card4Y1 = centerY + 40;
            int card4Y2 = centerY + 65;
            boolean card4Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card4Y1 && mouseY <= card4Y2;
            drawOptionCard(graphics, cardX1, card4Y1, cardX2, card4Y2, Component.translatable("cullify.options.culling_shape"), card4Hovered);
            CullifyConfig.CullingShape shape = CullifyConfig.CULLING_SHAPE.get();
            String shapeName = shape.name();
            int shapeColor;
            switch (shape) {
                case SPHERE: shapeColor = 0xFF2ECC71; break;
                case CYLINDER: shapeColor = 0xFF3498DB; break;
                case BOX: shapeColor = 0xFFE67E22; break;
                case TRIANGLE: shapeColor = 0xFFE74C3C; break;
                case HEXAGON: shapeColor = 0xFFF1C40F; break;
                case STAR: shapeColor = 0xFF9B59B6; break;
                case SQUARE: shapeColor = 0xFF8E44AD; break;
                case CIRCLE: shapeColor = 0xFF16A085; break;
                default: shapeColor = 0xFFFFFFFF; break;
            }
            graphics.drawString(this.font, shapeName, cardX2 - 12 - this.font.width(shapeName), card4Y1 + 8, shapeColor, false);
            if (card4Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.culling_shape");
                activeTooltipDesc = Component.translatable("cullify.options.culling_shape.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Shape 2D Geometry Live Wireframe Preview Panel
            int pX1 = centerX + 40;
            int pY1 = centerY - 60;
            int pX2 = mainX2 - 15;
            int pY2 = centerY + 65;
            
            // Draw Panel Background and border
            graphics.fill(pX1, pY1, pX2, pY2, 0x15000000);
            graphics.fill(pX1, pY1, pX2, pY1 + 1, 0x22FFFFFF);
            graphics.fill(pX1, pY2 - 1, pX2, pY2, 0x22FFFFFF);
            graphics.fill(pX1, pY1, pX1 + 1, pY2, 0x22FFFFFF);
            graphics.fill(pX2 - 1, pY1, pX2, pY2, 0x22FFFFFF);
            
            graphics.drawCenteredString(this.font, "Shape Preview", (pX1 + pX2) / 2, pY1 + 6, 0x88FFFFFF);
            
            int pCX = (pX1 + pX2) / 2;
            int pCY = (pY1 + pY2) / 2 + 5;
            double rotationAngle = (System.currentTimeMillis() % 360000) / 1000.0 * 0.4;
            
            switch (shape) {
                case SPHERE:
                    drawEllipse(graphics, pCX, pCY, 28, 28, 24, 0, shapeColor);
                    drawEllipse(graphics, pCX, pCY, 28, 9, 24, rotationAngle, shapeColor);
                    drawEllipse(graphics, pCX, pCY, 9, 28, 24, rotationAngle, shapeColor);
                    break;
                case CYLINDER:
                    drawEllipse(graphics, pCX, pCY - 18, 23, 7, 20, rotationAngle, shapeColor);
                    drawEllipse(graphics, pCX, pCY + 18, 23, 7, 20, rotationAngle, shapeColor);
                    drawLine(graphics, pCX - 23, pCY - 18, pCX - 23, pCY + 18, shapeColor);
                    drawLine(graphics, pCX + 23, pCY - 18, pCX + 23, pCY + 18, shapeColor);
                    break;
                case BOX:
                    double cosYaw = Math.cos(rotationAngle);
                    double sinYaw = Math.sin(rotationAngle);
                    double cosPitch = Math.cos(rotationAngle * 0.5);
                    double sinPitch = Math.sin(rotationAngle * 0.5);
                    
                    int[][] projected = new int[8][2];
                    int[][] vertices = {
                        {-18,-18,-18}, {18,-18,-18}, {18,18,-18}, {-18,18,-18},
                        {-18,-18,18}, {18,-18,18}, {18,18,18}, {-18,18,18}
                    };
                    for (int i = 0; i < 8; i++) {
                        int x = vertices[i][0];
                        int y = vertices[i][1];
                        int z = vertices[i][2];
                        double rx = x * cosYaw - z * sinYaw;
                        double rz = x * sinYaw + z * cosYaw;
                        double ry = y * cosPitch - rz * sinPitch;
                        projected[i][0] = pCX + (int) rx;
                        projected[i][1] = pCY + (int) ry;
                    }
                    drawLine(graphics, projected[0][0], projected[0][1], projected[1][0], projected[1][1], shapeColor);
                    drawLine(graphics, projected[1][0], projected[1][1], projected[2][0], projected[2][1], shapeColor);
                    drawLine(graphics, projected[2][0], projected[2][1], projected[3][0], projected[3][1], shapeColor);
                    drawLine(graphics, projected[3][0], projected[3][1], projected[0][0], projected[0][1], shapeColor);
                    drawLine(graphics, projected[4][0], projected[4][1], projected[5][0], projected[5][1], shapeColor);
                    drawLine(graphics, projected[5][0], projected[5][1], projected[6][0], projected[6][1], shapeColor);
                    drawLine(graphics, projected[6][0], projected[6][1], projected[7][0], projected[7][1], shapeColor);
                    drawLine(graphics, projected[7][0], projected[7][1], projected[4][0], projected[4][1], shapeColor);
                    for (int i = 0; i < 4; i++) {
                        drawLine(graphics, projected[i][0], projected[i][1], projected[i+4][0], projected[i+4][1], shapeColor);
                    }
                    break;
                case TRIANGLE:
                    drawEllipse(graphics, pCX, pCY, 28, 28, 3, rotationAngle, shapeColor);
                    break;
                case HEXAGON:
                    drawEllipse(graphics, pCX, pCY, 28, 28, 6, rotationAngle, shapeColor);
                    break;
                case STAR:
                    int outerR = 28;
                    int innerR = 11;
                    int starPoints = 10;
                    int[] px = new int[starPoints];
                    int[] py = new int[starPoints];
                    for (int i = 0; i < starPoints; i++) {
                        double angle = i * 2 * Math.PI / starPoints + rotationAngle;
                        int r = (i % 2 == 0) ? outerR : innerR;
                        px[i] = pCX + (int) (r * Math.cos(angle));
                        py[i] = pCY + (int) (r * Math.sin(angle));
                    }
                    for (int i = 0; i < starPoints; i++) {
                        drawLine(graphics, px[i], py[i], px[(i + 1) % starPoints], py[(i + 1) % starPoints], shapeColor);
                    }
                    break;
                case SQUARE:
                    drawEllipse(graphics, pCX, pCY, 28, 28, 4, rotationAngle, shapeColor);
                    break;
                case CIRCLE:
                    drawEllipse(graphics, pCX, pCY, 28, 28, 24, rotationAngle, shapeColor);
                    break;
            }

        } else if (currentTab == 1) {
            int cardX1 = divX + 10;
            int cardX2 = centerX + 30;
            int distX1 = centerX + 40;
            int distX2 = mainX2 - 15;

            // Grass Toggle Card
            int card1Y1 = centerY - 60;
            int card1Y2 = centerY - 35;
            boolean card1Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2;
            drawOptionCard(graphics, cardX1, card1Y1, cardX2, card1Y2, Component.translatable("cullify.options.cull_grass"), card1Hovered);
            drawToggleSwitch(graphics, cardX1, card1Y1, cardX2, card1Y2, CullifyConfig.CULL_GRASS.get());
            if (card1Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.cull_grass");
                activeTooltipDesc = Component.translatable("cullify.options.cull_grass.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }

            // Grass Distance Card
            boolean dist1Hovered = mouseX >= distX1 && mouseX <= distX2 && mouseY >= card1Y1 && mouseY <= card1Y2;
            int grassDist = CullifyConfig.GRASS_CULL_DISTANCE.get();
            drawOptionCard(graphics, distX1, card1Y1, distX2, card1Y2, Component.translatable("cullify.menu.distance", grassDist), dist1Hovered);
            if (dist1Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.grass_distance");
                activeTooltipDesc = Component.translatable("cullify.options.grass_distance.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }

            // Flowers Toggle Card
            int card2Y1 = centerY - 15;
            int card2Y2 = centerY + 10;
            boolean card2Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2;
            drawOptionCard(graphics, cardX1, card2Y1, cardX2, card2Y2, Component.translatable("cullify.options.cull_flowers"), card2Hovered);
            drawToggleSwitch(graphics, cardX1, card2Y1, cardX2, card2Y2, CullifyConfig.CULL_FLOWERS.get());
            if (card2Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.cull_flowers");
                activeTooltipDesc = Component.translatable("cullify.options.cull_flowers.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Flowers Distance Card
            boolean dist2Hovered = mouseX >= distX1 && mouseX <= distX2 && mouseY >= card2Y1 && mouseY <= card2Y2;
            int flowerDist = CullifyConfig.FLOWER_CULL_DISTANCE.get();
            drawOptionCard(graphics, distX1, card2Y1, distX2, card2Y2, Component.translatable("cullify.menu.distance", flowerDist), dist2Hovered);
            if (dist2Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.flower_distance");
                activeTooltipDesc = Component.translatable("cullify.options.flower_distance.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }

            // Other Toggle Card
            int card3Y1 = centerY + 30;
            int card3Y2 = centerY + 55;
            boolean card3Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2;
            drawOptionCard(graphics, cardX1, card3Y1, cardX2, card3Y2, Component.translatable("cullify.options.cull_other_plants"), card3Hovered);
            drawToggleSwitch(graphics, cardX1, card3Y1, cardX2, card3Y2, CullifyConfig.CULL_OTHER_PLANTS.get());
            if (card3Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.cull_other_plants");
                activeTooltipDesc = Component.translatable("cullify.options.cull_other_plants.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Other Distance Card
            boolean dist3Hovered = mouseX >= distX1 && mouseX <= distX2 && mouseY >= card3Y1 && mouseY <= card3Y2;
            int otherDist = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
            drawOptionCard(graphics, distX1, card3Y1, distX2, card3Y2, Component.translatable("cullify.menu.distance", otherDist), dist3Hovered);
            if (dist3Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.other_distance");
                activeTooltipDesc = Component.translatable("cullify.options.other_distance.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }
        }

        // Render standard button and widgets
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Render custom glass tooltip at the absolute end to overlay correctly
        if (activeTooltipTitle != null && activeTooltipDesc != null) {
            drawCustomTooltip(graphics, activeTooltipTitle, activeTooltipDesc, activeTooltipImpact, activeTooltipColor, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int tabX1 = centerX - 175;
        int tabX2 = centerX - 115;
        
        // Tab General
        int tab1Y1 = centerY - 55;
        int tab1Y2 = centerY - 35;
        if (mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab1Y1 && mouseY <= tab1Y2) {
            this.currentTab = 0;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Tab Vegetation
        int tab2Y1 = centerY - 30;
        int tab2Y2 = centerY - 10;
        if (mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab2Y1 && mouseY <= tab2Y2) {
            this.currentTab = 1;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Card Clicks depending on active tab
        if (currentTab == 0) {
            int cardX1 = centerX - 95;
            int cardX2 = centerX + 25;
            
            // Enabled Toggle Card
            int card1Y1 = centerY - 60;
            int card1Y2 = centerY - 35;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2) {
                CullifyConfig.ENABLED.set(!CullifyConfig.ENABLED.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            // Debug HUD Toggle Card
            int card2Y1 = centerY - 30;
            int card2Y2 = centerY - 5;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2) {
                CullifyConfig.DEBUG_MODE.set(!CullifyConfig.DEBUG_MODE.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            // LOD Density Card
            int card3Y1 = centerY + 5;
            int card3Y2 = centerY + 30;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2) {
                int current = CullifyConfig.LOD_DENSITY.get();
                int next;
                if (current >= 100) next = 75;
                else if (current >= 75)  next = 50;
                else if (current >= 50)  next = 25;
                else                     next = 100;
                CullifyConfig.LOD_DENSITY.set(next);
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            // Culling Shape Card
            int card4Y1 = centerY + 40;
            int card4Y2 = centerY + 65;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card4Y1 && mouseY <= card4Y2) {
                CullifyConfig.CullingShape current = CullifyConfig.CULLING_SHAPE.get();
                CullifyConfig.CullingShape[] values = CullifyConfig.CullingShape.values();
                int nextIdx = (current.ordinal() + 1) % values.length;
                CullifyConfig.CullingShape next = values[nextIdx];
                CullifyConfig.CULLING_SHAPE.set(next);
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        } else if (currentTab == 1) {
            int cardX1 = centerX - 95;
            int cardX2 = centerX + 25;
            int distX1 = centerX + 40;
            int distX2 = centerX - 15 + 180; // mainX2 - 15

            // Grass Toggle Card
            int card1Y1 = centerY - 60;
            int card1Y2 = centerY - 35;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2) {
                CullifyConfig.CULL_GRASS.set(!CullifyConfig.CULL_GRASS.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            // Grass Distance Card
            if (mouseX >= distX1 && mouseX <= distX2 && mouseY >= card1Y1 && mouseY <= card1Y2) {
                int nextDist = cycleDistance(CullifyConfig.GRASS_CULL_DISTANCE.get());
                CullifyConfig.GRASS_CULL_DISTANCE.set(nextDist);
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            // Flowers Toggle Card
            int card2Y1 = centerY - 15;
            int card2Y2 = centerY + 10;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2) {
                CullifyConfig.CULL_FLOWERS.set(!CullifyConfig.CULL_FLOWERS.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            // Flowers Distance Card
            if (mouseX >= distX1 && mouseX <= distX2 && mouseY >= card2Y1 && mouseY <= card2Y2) {
                int nextDist = cycleDistance(CullifyConfig.FLOWER_CULL_DISTANCE.get());
                CullifyConfig.FLOWER_CULL_DISTANCE.set(nextDist);
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            // Other Toggle Card
            int card3Y1 = centerY + 30;
            int card3Y2 = centerY + 55;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2) {
                CullifyConfig.CULL_OTHER_PLANTS.set(!CullifyConfig.CULL_OTHER_PLANTS.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            // Other Distance Card
            if (mouseX >= distX1 && mouseX <= distX2 && mouseY >= card3Y1 && mouseY <= card3Y2) {
                int nextDist = cycleDistance(CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get());
                CullifyConfig.OTHER_PLANT_CULL_DISTANCE.set(nextDist);
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (this.minecraft.level != null) {
            this.renderBlurredBackground(partialTicks);
            this.renderTransparentBackground(graphics);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTicks);
        }
    }
}
