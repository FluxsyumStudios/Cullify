package com.fluxsyum.cullify.client;

import com.fluxsyum.cullify.ClientEventHandler;
import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.Identifier;

import java.util.List;

public class CullifyConfigScreen extends Screen {
    private static final Identifier LOGO_TEXTURE = CullifyMod.LOGO;
    private final Screen parent;
    private int currentTab = 0; // 0 = General, 1 = Vegetation
    private float currentSin;
    private int activeSlider = -1; // -1=none, 0=grass_dist, 1=flower_dist, 2=other_dist, 3=lod_density, 4=target_fps
    private int generalScroll = 0;
    private int vegetationScroll = 0;

    private int getMaxGeneralScroll() {
        // 7 cards of height 25 with 5px spacing = 205px. Viewport = 140px. Max scroll = 65px.
        return 65;
    }

    private int getMaxVegetationScroll() {
        return 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.currentTab == 0) {
            this.generalScroll = Math.clamp(this.generalScroll - (int) (scrollY * 12), 0, getMaxGeneralScroll());
            return true;
        } else if (this.currentTab == 1) {
            this.vegetationScroll = Math.clamp(this.vegetationScroll - (int) (scrollY * 12), 0, getMaxVegetationScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }


    // Pre-allocated static 3D vertices and edges for culling shape previews
    private static final float[][] BOX_VERTICES = {
        {-16,-16,-16}, {16,-16,-16}, {16,16,-16}, {-16,16,-16},
        {-16,-16,16}, {16,-16,16}, {16,16,16}, {-16,16,16}
    };
    private static final int[][] BOX_EDGES = {
        {0,1}, {1,2}, {2,3}, {3,0},
        {4,5}, {5,6}, {6,7}, {7,4},
        {0,4}, {1,5}, {2,6}, {3,7}
    };

    private static final float[][] TRIANGLE_VERTICES = {
        {0, -18, 0}, {-16, 12, -12}, {16, 12, -12}, {0, 12, 16}
    };
    private static final int[][] TRIANGLE_EDGES = {
        {0,1}, {0,2}, {0,3}, {1,2}, {2,3}, {3,1}
    };

    private static final float[][] SQUARE_VERTICES = {
        {-18, 0, -18}, {18, 0, -18}, {18, 0, 18}, {-18, 0, 18}
    };
    private static final int[][] SQUARE_EDGES = {
        {0,1}, {1,2}, {2,3}, {3,0}
    };

    private static final float[][] CYLINDER_VERTICES = new float[16][3];
    private static final int[][] CYLINDER_EDGES = new int[24][2];

    private static final float[][] HEXAGON_VERTICES = new float[12][3];
    private static final int[][] HEXAGON_EDGES = new int[18][2];

    private static final float[][] STAR_VERTICES = new float[20][3];
    private static final int[][] STAR_EDGES = new int[30][2];

    private static final float[][] CIRCLE_VERTICES = new float[12][3];
    private static final int[][] CIRCLE_EDGES = new int[12][2];

    private static final float[][] SPHERE_VERTICES = new float[36][3];
    private static final int[][] SPHERE_EDGES = new int[36][2];

    static {
        // Initialize Cylinder vertices and edges
        for (int i = 0; i < 8; i++) {
            double angle = i * 2.0 * Math.PI / 8.0;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            CYLINDER_VERTICES[i][0] = cos * 16.0F;
            CYLINDER_VERTICES[i][1] = -16.0F;
            CYLINDER_VERTICES[i][2] = sin * 16.0F;
            CYLINDER_VERTICES[i+8][0] = cos * 16.0F;
            CYLINDER_VERTICES[i+8][1] = 16.0F;
            CYLINDER_VERTICES[i+8][2] = sin * 16.0F;

            CYLINDER_EDGES[i] = new int[]{i, (i + 1) % 8};
            CYLINDER_EDGES[i + 8] = new int[]{i + 8, ((i + 1) % 8) + 8};
            CYLINDER_EDGES[i + 16] = new int[]{i, i + 8};
        }

        // Initialize Hexagon vertices and edges
        for (int i = 0; i < 6; i++) {
            double angle = i * 2.0 * Math.PI / 6.0;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            HEXAGON_VERTICES[i][0] = cos * 16.0F;
            HEXAGON_VERTICES[i][1] = -16.0F;
            HEXAGON_VERTICES[i][2] = sin * 16.0F;
            HEXAGON_VERTICES[i+6][0] = cos * 16.0F;
            HEXAGON_VERTICES[i+6][1] = 16.0F;
            HEXAGON_VERTICES[i+6][2] = sin * 16.0F;

            HEXAGON_EDGES[i] = new int[]{i, (i + 1) % 6};
            HEXAGON_EDGES[i + 6] = new int[]{i + 6, ((i + 1) % 6) + 6};
            HEXAGON_EDGES[i + 12] = new int[]{i, i + 6};
        }

        // Initialize Extruded 3D Star
        for (int i = 0; i < 10; i++) {
            double angle = i * 2.0 * Math.PI / 10.0;
            float r = (i % 2 == 0) ? 18.0F : 8.0F;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            STAR_VERTICES[i][0] = cos * r;
            STAR_VERTICES[i][1] = -5.0F;
            STAR_VERTICES[i][2] = sin * r;
            STAR_VERTICES[i+10][0] = cos * r;
            STAR_VERTICES[i+10][1] = 5.0F;
            STAR_VERTICES[i+10][2] = sin * r;

            STAR_EDGES[i] = new int[]{i, (i + 1) % 10};
            STAR_EDGES[i + 10] = new int[]{i + 10, ((i + 1) % 10) + 10};
            STAR_EDGES[i + 20] = new int[]{i, i + 10};
        }

        // Initialize Circle
        for (int i = 0; i < 12; i++) {
            double angle = i * 2.0 * Math.PI / 12.0;
            CIRCLE_VERTICES[i][0] = (float) Math.cos(angle) * 18.0F;
            CIRCLE_VERTICES[i][1] = 0.0F;
            CIRCLE_VERTICES[i][2] = (float) Math.sin(angle) * 18.0F;

            CIRCLE_EDGES[i] = new int[]{i, (i + 1) % 12};
        }

        // Initialize Sphere (consists of three orthogonal rings)
        for (int i = 0; i < 12; i++) {
            double angle = i * 2.0 * Math.PI / 12.0;
            float cos = (float) Math.cos(angle) * 18.0F;
            float sin = (float) Math.sin(angle) * 18.0F;
            // XY ring
            SPHERE_VERTICES[i][0] = cos;
            SPHERE_VERTICES[i][1] = sin;
            SPHERE_VERTICES[i][2] = 0.0F;
            // YZ ring
            SPHERE_VERTICES[i+12][0] = 0.0F;
            SPHERE_VERTICES[i+12][1] = cos;
            SPHERE_VERTICES[i+12][2] = sin;
            // XZ ring
            SPHERE_VERTICES[i+24][0] = cos;
            SPHERE_VERTICES[i+24][1] = 0.0F;
            SPHERE_VERTICES[i+24][2] = sin;

            SPHERE_EDGES[i] = new int[]{i, (i + 1) % 12};
            SPHERE_EDGES[i + 12] = new int[]{i + 12, ((i + 1) % 12) + 12};
            SPHERE_EDGES[i + 24] = new int[]{i + 24, ((i + 1) % 12) + 24};
        }
    }

    public CullifyConfigScreen(Screen parent) {
        super(Component.translatable("cullify.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
    }

    private void onConfigChange() {
        CullifyMod.updateConfigCache();
        CullifyMod.incrementConfigVersion();
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

    private void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
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

    private void drawWireframe(GuiGraphicsExtractor graphics, int cx, int cy, float[][] vertices, int[][] edges, int color) {
        double angle = (System.currentTimeMillis() % 360000) / 1000.0 * 0.5;
        double bobbing = Math.sin(System.currentTimeMillis() * 0.002) * 3.0;
        int pCY = cy + (int) bobbing;

        double cosYaw = Math.cos(angle);
        double sinYaw = Math.sin(angle);
        double cosPitch = Math.cos(angle * 0.6);
        double sinPitch = Math.sin(angle * 0.6);

        int[][] projected = new int[vertices.length][2];
        for (int i = 0; i < vertices.length; i++) {
            float x = vertices[i][0];
            float y = vertices[i][1];
            float z = vertices[i][2];

            double rx = x * cosYaw - z * sinYaw;
            double rz = x * sinYaw + z * cosYaw;
            double ry = y * cosPitch - rz * sinPitch;

            projected[i][0] = cx + (int) rx;
            projected[i][1] = pCY + (int) ry;
        }

        for (int[] edge : edges) {
            drawLine(graphics, projected[edge[0]][0], projected[edge[0]][1], projected[edge[1]][0], projected[edge[1]][1], color);
        }
    }

    private void drawGlowingRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float pulse = 0.4F + 0.6F * this.currentSin;

        // Core solid border line
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);

        // Extremely soft Glow Layer 1: 1px offset, ~15% opacity max
        int alpha1 = (int) (40 * pulse) << 24;
        int glowColor1 = alpha1 | (r << 16) | (g << 8) | b;
        graphics.fill(x1 - 1, y1 - 1, x2 + 1, y1, glowColor1);
        graphics.fill(x1 - 1, y2, x2 + 1, y2 + 1, glowColor1);
        graphics.fill(x1 - 1, y1 - 1, x1, y2 + 1, glowColor1);
        graphics.fill(x2, y1 - 1, x2 + 1, y2 + 1, glowColor1);

        // Extremely soft Glow Layer 2: 2px offset, ~6% opacity max
        int alpha2 = (int) (16 * pulse) << 24;
        int glowColor2 = alpha2 | (r << 16) | (g << 8) | b;
        graphics.fill(x1 - 2, y1 - 2, x2 + 2, y1 - 1, glowColor2);
        graphics.fill(x1 - 2, y2 + 1, x2 + 2, y2 + 2, glowColor2);
        graphics.fill(x1 - 2, y1 - 2, x1 - 1, y2 + 2, glowColor2);
        graphics.fill(x2 + 1, y1 - 2, x2 + 2, y2 + 2, glowColor2);
    }

    private void drawCustomTooltip(GuiGraphicsExtractor graphics, Component title, Component description, Component impactLabel, int colorImpact, int x, int y) {
        int maxWidth = 180;
        List<FormattedCharSequence> splitLines = this.font.split(description, maxWidth);
        
        int tooltipW = maxWidth + 12;
        int tooltipH = (splitLines.size() + 2) * 10 + 8;
        
        int tx = x + 10;
        int ty = y + 10;
        if (tx + tooltipW > this.width) {
            tx = x - 10 - tooltipW;
        }
        if (ty + tooltipH > this.height) {
            ty = this.height - tooltipH - 5;
        }
        
        // 100% opaque background to block overlapping elements showing through
        graphics.fill(tx, ty, tx + tooltipW, ty + tooltipH, 0xFF0B0C0E);
        drawGlowingRect(graphics, tx, ty, tx + tooltipW, ty + tooltipH, 0xFF2ECC71);
        
        graphics.text(this.font, title, tx + 6, ty + 5, 0xFFFFFFFF, false);
        
        for (int i = 0; i < splitLines.size(); i++) {
            graphics.text(this.font, splitLines.get(i), tx + 6, ty + 15 + (i * 10), 0xFF888888, false);
        }
        
        int impactY = ty + 15 + (splitLines.size() * 10);
        graphics.text(this.font, Component.translatable("cullify.menu.tooltip.performance.impact"), tx + 6, impactY, 0xFF555555, false);
        graphics.text(this.font, impactLabel, tx + 6 + this.font.width(Component.translatable("cullify.menu.tooltip.performance.impact")), impactY, colorImpact, false);
    }

    private void drawToggleSwitch(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, boolean value) {
        int height = y2 - y1;
        int trackW = 24;
        int trackH = 10;
        int tx = x2 - trackW - 6;
        int ty = y1 + (height - trackH) / 2;
        
        graphics.fill(tx, ty, tx + trackW, ty + trackH, value ? 0xFF1B5E20 : 0xFFB71C1C);
        
        int knobSize = 12;
        int kx = value ? (tx + trackW - knobSize) : tx;
        int ky = ty + (trackH - knobSize) / 2;
        graphics.fill(kx, ky, kx + knobSize, ky + knobSize, value ? 0xFF2ECC71 : 0xFFE74C3C);
    }

    private void drawOptionCard(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, Component title, boolean isHovered, boolean centered) {
        drawOptionCardBackground(graphics, x1, y1, x2, y2, isHovered);
        if (centered) {
            graphics.centeredText(this.font, title, (x1 + x2) / 2, y1 + (y2 - y1 - 8) / 2, 0xFFFFFFFF);
        } else {
            graphics.text(this.font, title, x1 + 8, y1 + (y2 - y1 - 8) / 2, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        this.currentSin = (float) Math.sin(System.currentTimeMillis() * 0.003);
        this.extractBackground(graphics, mouseX, mouseY, partialTicks);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        int mainX1 = centerX - 180;
        int mainY1 = centerY - 95;
        int mainX2 = centerX + 180;
        int mainY2 = centerY + 90;
        
        graphics.fill(mainX1, mainY1, mainX2, mainY2, 0xEE0B0C0E);
        
        drawGlowingRect(graphics, mainX1, mainY1, mainX2, mainY2, 0xFF2ECC71);
        
        int divX = centerX - 110;
        graphics.fill(divX, mainY1 + 5, divX + 1, mainY2 - 5, 0x22FFFFFF);
        
        // Draw Logo texture scaled to 32x32
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, mainX1 + 19, mainY1 + 10, 0.0F, 0.0F, 32, 32, 32, 32);
        // Draw Name centered below the logo
        graphics.centeredText(this.font, "§2§lCullify", mainX1 + 35, mainY1 + 46, 0xFFFFFFFF);
        
        int tabX1 = mainX1 + 5;
        int tabX2 = divX - 5;
        
        int tab1Y1 = centerY - 25;
        int tab1Y2 = centerY - 5;
        boolean tab1Hovered = mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab1Y1 && mouseY <= tab1Y2;
        if (currentTab == 0) {
            graphics.fill(tabX1, tab1Y1, tabX1 + 2, tab1Y2, 0xFF2ECC71);
            graphics.fill(tabX1 + 2, tab1Y1, tabX2, tab1Y2, 0x222ECC71);
            graphics.text(this.font, Component.translatable("cullify.menu.tab.general"), tabX1 + 8, tab1Y1 + 6, 0xFFFFFFFF, false);
        } else {
            if (tab1Hovered) {
                graphics.fill(tabX1, tab1Y1, tabX2, tab1Y2, 0x11FFFFFF);
            }
            graphics.text(this.font, Component.translatable("cullify.menu.tab.general"), tabX1 + 8, tab1Y1 + 6, 0x88888888, false);
        }

        int tab2Y1 = centerY;
        int tab2Y2 = centerY + 20;
        boolean tab2Hovered = mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab2Y1 && mouseY <= tab2Y2;
        if (currentTab == 1) {
            graphics.fill(tabX1, tab2Y1, tabX1 + 2, tab2Y2, 0xFF2ECC71);
            graphics.fill(tabX1 + 2, tab2Y1, tabX2, tab2Y2, 0x222ECC71);
            graphics.text(this.font, Component.translatable("cullify.menu.tab.vegetation"), tabX1 + 8, tab2Y1 + 6, 0xFFFFFFFF, false);
        } else {
            if (tab2Hovered) {
                graphics.fill(tabX1, tab2Y1, tabX2, tab2Y2, 0x11FFFFFF);
            }
            graphics.text(this.font, Component.translatable("cullify.menu.tab.vegetation"), tabX1 + 8, tab2Y1 + 6, 0x88888888, false);
        }

        Component activeTooltipTitle = null;
        Component activeTooltipDesc = null;
        Component activeTooltipImpact = null;
        int activeTooltipColor = 0xFF555555;

        if (currentTab == 0) {
            int cardX1 = divX + 10;
            int cardX2 = centerX + 40;
            
            int viewportX1 = divX + 5;
            int viewportX2 = centerX + 42;
            int viewportY1 = centerY - 75;
            int viewportY2 = centerY + 65;

            // Enable scissor clipping so scrolled options don't overflow the container
            graphics.enableScissor(viewportX1, viewportY1, viewportX2, viewportY2);

            int startY = centerY - 75 - this.generalScroll;
            boolean inViewport = mouseY >= viewportY1 && mouseY <= viewportY2;

            // Card 1: Enabled
            int card1Y1 = startY;
            int card1Y2 = card1Y1 + 25;
            boolean card1Hovered = inViewport && mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2;
            drawOptionCard(graphics, cardX1, card1Y1, cardX2, card1Y2, Component.translatable("cullify.menu.option.enabled"), card1Hovered, false);
            drawToggleSwitch(graphics, cardX1, card1Y1, cardX2, card1Y2, CullifyConfig.ENABLED.get());
            if (card1Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.enabled");
                activeTooltipDesc = Component.translatable("cullify.options.enabled.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.none");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Card 2: Debug Mode
            int card2Y1 = startY + 30;
            int card2Y2 = card2Y1 + 25;
            boolean card2Hovered = inViewport && mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2;
            drawOptionCard(graphics, cardX1, card2Y1, cardX2, card2Y2, Component.translatable("cullify.menu.option.debug"), card2Hovered, false);
            drawToggleSwitch(graphics, cardX1, card2Y1, cardX2, card2Y2, CullifyConfig.DEBUG_MODE.get());
            if (card2Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.debug_mode");
                activeTooltipDesc = Component.translatable("cullify.options.debug_mode.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.none");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Card 3: LOD Density
            int card3Y1 = startY + 60;
            int card3Y2 = card3Y1 + 25;
            boolean card3Hovered = inViewport && mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2;
            int d = CullifyConfig.LOD_DENSITY.get();
            String densityStr = d >= 100 ? "OFF" : d + "%";
            drawSlider(graphics, cardX1, card3Y1, cardX2, card3Y2, Component.translatable("cullify.menu.option.lod"), densityStr, d, 0, 100, card3Hovered || this.activeSlider == 3);
            if (card3Hovered) {
                activeTooltipTitle = Component.translatable("cullify.menu.lod_density");
                activeTooltipDesc = Component.translatable("cullify.menu.lod_density.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Card 4: Culling Shape
            int card4Y1 = startY + 90;
            int card4Y2 = card4Y1 + 25;
            boolean card4Hovered = inViewport && mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card4Y1 && mouseY <= card4Y2;
            drawOptionCard(graphics, cardX1, card4Y1, cardX2, card4Y2, Component.translatable("cullify.menu.option.shape"), card4Hovered, false);
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
            graphics.text(this.font, shapeName, cardX2 - 12 - this.font.width(shapeName), card4Y1 + 8, shapeColor, false);
            if (card4Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.culling_shape");
                activeTooltipDesc = Component.translatable("cullify.options.culling_shape.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            // Card 5: Smart Scale
            int card5Y1 = startY + 120;
            int card5Y2 = card5Y1 + 25;
            boolean card5Hovered = inViewport && mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card5Y1 && mouseY <= card5Y2;
            drawOptionCard(graphics, cardX1, card5Y1, cardX2, card5Y2, Component.translatable("cullify.menu.option.smart_scale"), card5Hovered, false);
            drawToggleSwitch(graphics, cardX1, card5Y1, cardX2, card5Y2, CullifyConfig.SMART_SCALE.get());
            if (card5Hovered) {
                activeTooltipTitle = Component.translatable("cullify.menu.option.smart_scale");
                activeTooltipDesc = Component.translatable("cullify.menu.option.smart_scale.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.none");
                activeTooltipColor = 0xFF3498DB;
            }

            // Card 6: Target FPS
            int card6Y1 = startY + 150;
            int card6Y2 = card6Y1 + 25;
            boolean card6Hovered = inViewport && mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card6Y1 && mouseY <= card6Y2;
            int targetFps = CullifyConfig.TARGET_FPS.get();
            drawSlider(graphics, cardX1, card6Y1, cardX2, card6Y2, Component.translatable("cullify.menu.option.target_fps"), targetFps + " FPS", targetFps, 30, 240, card6Hovered || this.activeSlider == 4);
            // Dim the slider when Smart Scale is off
            if (!CullifyConfig.SMART_SCALE.get()) {
                graphics.fill(cardX1, card6Y1, cardX2, card6Y2, 0x88000000);
            }
            if (card6Hovered) {
                activeTooltipTitle = Component.translatable("cullify.menu.option.target_fps");
                activeTooltipDesc = Component.translatable("cullify.menu.option.target_fps.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.none");
                activeTooltipColor = 0xFF3498DB;
            }

            // Card 7: Light-Aware Culling
            int card7Y1 = startY + 180;
            int card7Y2 = card7Y1 + 25;
            boolean card7Hovered = inViewport && mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card7Y1 && mouseY <= card7Y2;
            drawOptionCard(graphics, cardX1, card7Y1, cardX2, card7Y2, Component.translatable("cullify.menu.option.light_aware"), card7Hovered, false);
            drawToggleSwitch(graphics, cardX1, card7Y1, cardX2, card7Y2, CullifyConfig.LIGHT_AWARE_CULLING.get());
            if (card7Hovered) {
                activeTooltipTitle = Component.translatable("cullify.menu.option.light_aware");
                activeTooltipDesc = Component.translatable("cullify.menu.option.light_aware.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFFF1C40F;
            }

            graphics.disableScissor();

            // Draw scrollbar outside the scissor bounds so it does not clip
            drawScrollbar(graphics, centerX + 44, viewportY1, viewportY2, this.generalScroll, getMaxGeneralScroll(), 140);

            int pX1 = centerX + 50;
            int pY1 = centerY - 75;
            int pX2 = mainX2 - 15;
            int pY2 = centerY + 88;
            
            graphics.fill(pX1, pY1, pX2, pY2, 0x15000000);
            graphics.fill(pX1, pY1, pX2, pY1 + 1, 0x22FFFFFF);
            graphics.fill(pX1, pY2 - 1, pX2, pY2, 0x22FFFFFF);
            graphics.fill(pX1, pY1, pX1 + 1, pY2, 0x22FFFFFF);
            graphics.fill(pX2 - 1, pY1, pX2, pY2, 0x22FFFFFF);
            
            graphics.centeredText(this.font, "Shape Preview", (pX1 + pX2) / 2, pY1 + 6, 0x88FFFFFF);
            
            int pCX = (pX1 + pX2) / 2;
            int pCY = (pY1 + pY2) / 2 + 5;
            
            switch (shape) {
                case SPHERE: drawWireframe(graphics, pCX, pCY, SPHERE_VERTICES, SPHERE_EDGES, shapeColor); break;
                case CYLINDER: drawWireframe(graphics, pCX, pCY, CYLINDER_VERTICES, CYLINDER_EDGES, shapeColor); break;
                case BOX: drawWireframe(graphics, pCX, pCY, BOX_VERTICES, BOX_EDGES, shapeColor); break;
                case TRIANGLE: drawWireframe(graphics, pCX, pCY, TRIANGLE_VERTICES, TRIANGLE_EDGES, shapeColor); break;
                case HEXAGON: drawWireframe(graphics, pCX, pCY, HEXAGON_VERTICES, HEXAGON_EDGES, shapeColor); break;
                case STAR: drawWireframe(graphics, pCX, pCY, STAR_VERTICES, STAR_EDGES, shapeColor); break;
                case SQUARE: drawWireframe(graphics, pCX, pCY, SQUARE_VERTICES, SQUARE_EDGES, shapeColor); break;
                case CIRCLE: drawWireframe(graphics, pCX, pCY, CIRCLE_VERTICES, CIRCLE_EDGES, shapeColor); break;
            }

        } else if (currentTab == 1) {
            int cardX1 = divX + 10;
            int cardX2 = centerX + 40;
            int distX1 = centerX + 50;
            int distX2 = mainX2 - 15;

            int card1Y1 = centerY - 60;
            int card1Y2 = centerY - 35;
            boolean card1Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2;
            drawOptionCard(graphics, cardX1, card1Y1, cardX2, card1Y2, Component.translatable("cullify.menu.option.grass"), card1Hovered, false);
            drawToggleSwitch(graphics, cardX1, card1Y1, cardX2, card1Y2, CullifyConfig.CULL_GRASS.get());
            if (card1Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.cull_grass");
                activeTooltipDesc = Component.translatable("cullify.options.cull_grass.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }

            boolean dist1Hovered = mouseX >= distX1 && mouseX <= distX2 && mouseY >= card1Y1 && mouseY <= card1Y2;
            int grassDist = CullifyConfig.GRASS_CULL_DISTANCE.get();
            drawSlider(graphics, distX1, card1Y1, distX2, card1Y2, Component.translatable("cullify.menu.slider.distance"), grassDist + " blocks", grassDist, 16, 256, dist1Hovered || this.activeSlider == 0);
            if (dist1Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.grass_distance");
                activeTooltipDesc = Component.translatable("cullify.options.grass_distance.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }

            int card2Y1 = centerY - 15;
            int card2Y2 = centerY + 10;
            boolean card2Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2;
            drawOptionCard(graphics, cardX1, card2Y1, cardX2, card2Y2, Component.translatable("cullify.menu.option.flowers"), card2Hovered, false);
            drawToggleSwitch(graphics, cardX1, card2Y1, cardX2, card2Y2, CullifyConfig.CULL_FLOWERS.get());
            if (card2Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.cull_flowers");
                activeTooltipDesc = Component.translatable("cullify.options.cull_flowers.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            boolean dist2Hovered = mouseX >= distX1 && mouseX <= distX2 && mouseY >= card2Y1 && mouseY <= card2Y2;
            int flowerDist = CullifyConfig.FLOWER_CULL_DISTANCE.get();
            drawSlider(graphics, distX1, card2Y1, distX2, card2Y2, Component.translatable("cullify.menu.slider.distance"), flowerDist + " blocks", flowerDist, 16, 256, dist2Hovered || this.activeSlider == 1);
            if (dist2Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.flower_distance");
                activeTooltipDesc = Component.translatable("cullify.options.flower_distance.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }

            int card3Y1 = centerY + 30;
            int card3Y2 = centerY + 55;
            boolean card3Hovered = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2;
            drawOptionCard(graphics, cardX1, card3Y1, cardX2, card3Y2, Component.translatable("cullify.menu.option.other"), card3Hovered, false);
            drawToggleSwitch(graphics, cardX1, card3Y1, cardX2, card3Y2, CullifyConfig.CULL_OTHER_PLANTS.get());
            if (card3Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.cull_other_plants");
                activeTooltipDesc = Component.translatable("cullify.options.cull_other_plants.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.low");
                activeTooltipColor = 0xFF2ECC71;
            }

            boolean dist3Hovered = mouseX >= distX1 && mouseX <= distX2 && mouseY >= card3Y1 && mouseY <= card3Y2;
            int otherDist = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
            drawSlider(graphics, distX1, card3Y1, distX2, card3Y2, Component.translatable("cullify.menu.slider.distance"), otherDist + " blocks", otherDist, 16, 256, dist3Hovered || this.activeSlider == 2);
            if (dist3Hovered) {
                activeTooltipTitle = Component.translatable("cullify.options.other_distance");
                activeTooltipDesc = Component.translatable("cullify.options.other_distance.tooltip");
                activeTooltipImpact = Component.translatable("cullify.menu.tooltip.performance.medium");
                activeTooltipColor = 0xFFE67E22;
            }
        }

        int doneX1 = centerX - 80;
        int doneX2 = centerX + 20;
        int doneY1 = centerY + 70;
        int doneY2 = centerY + 85;
        boolean doneHovered = mouseX >= doneX1 && mouseX <= doneX2 && mouseY >= doneY1 && mouseY <= doneY2;
        drawOptionCard(graphics, doneX1, doneY1, doneX2, doneY2, Component.translatable("gui.done"), doneHovered, true);

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        if (activeTooltipTitle != null && activeTooltipDesc != null) {
            drawCustomTooltip(graphics, activeTooltipTitle, activeTooltipDesc, activeTooltipImpact, activeTooltipColor, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int tabX1 = centerX - 175;
        int tabX2 = centerX - 115;
        
        int tab1Y1 = centerY - 25;
        int tab1Y2 = centerY - 5;
        if (mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab1Y1 && mouseY <= tab1Y2) {
            this.currentTab = 0;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        int tab2Y1 = centerY;
        int tab2Y2 = centerY + 20;
        if (mouseX >= tabX1 && mouseX <= tabX2 && mouseY >= tab2Y1 && mouseY <= tab2Y2) {
            this.currentTab = 1;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        int doneX1 = centerX - 80;
        int doneX2 = centerX + 20;
        int doneY1 = centerY + 70;
        int doneY2 = centerY + 85;
        if (mouseX >= doneX1 && mouseX <= doneX2 && mouseY >= doneY1 && mouseY <= doneY2) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.onClose();
            return true;
        }

        if (currentTab == 0) {
            int cardX1 = centerX - 95;
            int cardX2 = centerX + 40;
            
            // Only process option clicks if they occur within the scrollable viewport Y limits
            if (mouseY >= centerY - 75 && mouseY <= centerY + 65) {
                int startY = centerY - 75 - this.generalScroll;

                // Card 1: Enabled
                int card1Y1 = startY;
                int card1Y2 = card1Y1 + 25;
                if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2) {
                    CullifyConfig.ENABLED.set(!CullifyConfig.ENABLED.get());
                    onConfigChange();
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }

                // Card 2: Debug HUD
                int card2Y1 = startY + 30;
                int card2Y2 = card2Y1 + 25;
                if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2) {
                    CullifyConfig.DEBUG_MODE.set(!CullifyConfig.DEBUG_MODE.get());
                    onConfigChange();
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }

                // Card 3: LOD Density
                int card3Y1 = startY + 60;
                int card3Y2 = card3Y1 + 25;
                if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2) {
                    this.activeSlider = 3;
                    updateSliderValue(mouseX);
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }

                // Card 4: Culling Shape
                int card4Y1 = startY + 90;
                int card4Y2 = card4Y1 + 25;
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

                // Card 5: Smart Scale
                int card5Y1 = startY + 120;
                int card5Y2 = card5Y1 + 25;
                if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card5Y1 && mouseY <= card5Y2) {
                    CullifyConfig.SMART_SCALE.set(!CullifyConfig.SMART_SCALE.get());
                    onConfigChange();
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }

                // Card 6: Target FPS
                int card6Y1 = startY + 150;
                int card6Y2 = card6Y1 + 25;
                if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card6Y1 && mouseY <= card6Y2 && CullifyConfig.SMART_SCALE.get()) {
                    this.activeSlider = 4;
                    updateSliderValue(mouseX);
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }

                // Card 7: Light-Aware Culling
                int card7Y1 = startY + 180;
                int card7Y2 = card7Y1 + 25;
                if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card7Y1 && mouseY <= card7Y2) {
                    CullifyConfig.LIGHT_AWARE_CULLING.set(!CullifyConfig.LIGHT_AWARE_CULLING.get());
                    onConfigChange();
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        } else if (currentTab == 1) {
            int cardX1 = centerX - 95;
            int cardX2 = centerX + 40;
            int distX1 = centerX + 50;
            int distX2 = centerX - 15 + 180;

            int card1Y1 = centerY - 60;
            int card1Y2 = centerY - 35;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card1Y1 && mouseY <= card1Y2) {
                CullifyConfig.CULL_GRASS.set(!CullifyConfig.CULL_GRASS.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= distX1 && mouseX <= distX2 && mouseY >= card1Y1 && mouseY <= card1Y2) {
                this.activeSlider = 0;
                updateSliderValue(mouseX);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            int card2Y1 = centerY - 15;
            int card2Y2 = centerY + 10;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card2Y1 && mouseY <= card2Y2) {
                CullifyConfig.CULL_FLOWERS.set(!CullifyConfig.CULL_FLOWERS.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= distX1 && mouseX <= distX2 && mouseY >= card2Y1 && mouseY <= card2Y2) {
                this.activeSlider = 1;
                updateSliderValue(mouseX);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            int card3Y1 = centerY + 30;
            int card3Y2 = centerY + 55;
            if (mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= card3Y1 && mouseY <= card3Y2) {
                CullifyConfig.CULL_OTHER_PLANTS.set(!CullifyConfig.CULL_OTHER_PLANTS.get());
                onConfigChange();
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= distX1 && mouseX <= distX2 && mouseY >= card3Y1 && mouseY <= card3Y2) {
                this.activeSlider = 2;
                updateSliderValue(mouseX);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        this.activeSlider = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (this.activeSlider != -1) {
            updateSliderValue(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private void updateSliderValue(double mouseX) {
        int centerX = this.width / 2;
        int divX = centerX - 110;
        int mainX2 = centerX + 180;

        int distX1 = centerX + 50 + 8;
        int distX2 = mainX2 - 15 - 8;
        int distW = distX2 - distX1;

        int cardX1 = divX + 10 + 8;
        int cardX2 = centerX + 40 - 8;
        int cardW = cardX2 - cardX1;

        if (this.activeSlider == 0) { // grass distance
            double pct = (mouseX - distX1) / distW;
            int val = 16 + (int) (pct * (256 - 16));
            val = Math.clamp((val / 8) * 8, 16, 256);
            CullifyConfig.GRASS_CULL_DISTANCE.set(val);
            onConfigChange();
        } else if (this.activeSlider == 1) { // flower distance
            double pct = (mouseX - distX1) / distW;
            int val = 16 + (int) (pct * (256 - 16));
            val = Math.clamp((val / 8) * 8, 16, 256);
            CullifyConfig.FLOWER_CULL_DISTANCE.set(val);
            onConfigChange();
        } else if (this.activeSlider == 2) { // other distance
            double pct = (mouseX - distX1) / distW;
            int val = 16 + (int) (pct * (256 - 16));
            val = Math.clamp((val / 8) * 8, 16, 256);
            CullifyConfig.OTHER_PLANT_CULL_DISTANCE.set(val);
            onConfigChange();
        } else if (this.activeSlider == 3) { // lod density
            double pct = (mouseX - cardX1) / cardW;
            int val = (int) (pct * 100);
            val = Math.clamp((val / 5) * 5, 0, 100);
            CullifyConfig.LOD_DENSITY.set(val);
            onConfigChange();
        } else if (this.activeSlider == 4) { // target FPS
            if (CullifyConfig.SMART_SCALE.get()) {
                double pct = (mouseX - cardX1) / cardW;
                int val = 30 + (int) (pct * (240 - 30));
                val = Math.clamp((val / 10) * 10, 30, 240);
                CullifyConfig.TARGET_FPS.set(val);
                onConfigChange();
            }
        }
    }

    private void drawSlider(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, Component title, String valueStr, int value, int min, int max, boolean isHovered) {
        drawOptionCardBackground(graphics, x1, y1, x2, y2, isHovered);
        
        graphics.text(this.font, title, x1 + 8, y1 + 4, 0xFFFFFFFF, false);
        graphics.text(this.font, valueStr, x2 - 8 - this.font.width(valueStr), y1 + 4, 0xFF2ECC71, false);

        int trackX1 = x1 + 8;
        int trackX2 = x2 - 8;
        int trackY = y2 - 6;
        
        graphics.fill(trackX1, trackY - 1, trackX2, trackY + 1, 0x33FFFFFF);
        
        double pct = (double) (value - min) / (max - min);
        pct = Math.clamp(pct, 0.0, 1.0);
        int handleX = trackX1 + (int) (pct * (trackX2 - trackX1));
        
        graphics.fill(trackX1, trackY - 1, handleX, trackY + 1, 0xFF2ECC71);
        
        int handleW = 4;
        int handleH = 8;
        graphics.fill(handleX - handleW / 2, trackY - handleH / 2, handleX + handleW / 2 + 1, trackY + handleH / 2 + 1, 0xFFFFFFFF);
        
        float pulse = 0.6F + 0.4F * this.currentSin;
        int glowColor = ((int) (150 * pulse) << 24) | 0x2ECC71;
        graphics.fill(handleX - handleW / 2 - 1, trackY - handleH / 2 - 1, handleX - handleW / 2, trackY + handleH / 2 + 2, glowColor);
        graphics.fill(handleX + handleW / 2 + 1, trackY - handleH / 2 - 1, handleX + handleW / 2 + 2, trackY + handleH / 2 + 2, glowColor);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y1, int y2, int currentScroll, int maxScroll, int viewportHeight) {
        if (maxScroll <= 0) return;
        
        // Draw track
        graphics.fill(x, y1, x + 2, y2, 0x11FFFFFF);
        
        // Calculate thumb size and position
        int trackHeight = y2 - y1;
        int thumbHeight = Math.max(15, (int) ((double) viewportHeight / (viewportHeight + maxScroll) * trackHeight));
        int thumbY = y1 + (int) ((double) currentScroll / maxScroll * (trackHeight - thumbHeight));
        
        // Draw thumb (neon green matching design)
        graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0xFF2ECC71);
    }

    private void drawOptionCardBackground(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, boolean isHovered) {
        graphics.fill(x1, y1, x2, y2, isHovered ? 0x22FFFFFF : 0x11FFFFFF);
        if (isHovered) {
            float pulse = 0.6F + 0.4F * this.currentSin;
            int glowColor = ((int) (120 * pulse) << 24) | 0x2ECC71;
            graphics.fill(x1, y1, x2, y1 + 1, glowColor);
            graphics.fill(x1, y2 - 1, x2, y2, glowColor);
            graphics.fill(x1, y1, x1 + 1, y2, glowColor);
            graphics.fill(x2 - 1, y1, x2, y2, glowColor);
        } else {
            graphics.fill(x1, y1, x2, y1 + 1, 0x15FFFFFF);
            graphics.fill(x1, y2 - 1, x2, y2, 0x15FFFFFF);
            graphics.fill(x1, y1, x1 + 1, y2, 0x15FFFFFF);
            graphics.fill(x2 - 1, y1, x2, y2, 0x15FFFFFF);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (this.minecraft.level != null) {
            this.extractBlurredBackground(graphics);
        } else {
            super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        }
    }
}


