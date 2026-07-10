package com.fluxsyum.cullify.client;

import com.fluxsyum.cullify.CullifyMod;
import com.fluxsyum.cullify.benchmark.BenchmarkReportWriter;
import com.fluxsyum.cullify.benchmark.BenchmarkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom in-game results screen for the Cullify benchmark.
 * Renders glassmorphic containers, performance gains cards, and a downsampled
 * double-line chart comparing FPS stability over time.
 */
public class BenchmarkResultsScreen extends Screen {

    private final Screen parent;
    private final List<Double> withoutFrameTimes;
    private final List<Double> withFrameTimes;
    private final File reportFile;

    private final BenchmarkReportWriter.Metrics metricsWithout;
    private final BenchmarkReportWriter.Metrics metricsWith;

    private List<Double> chartPointsWithout;
    private List<Double> chartPointsWith;
    private double minFpsValue = 0.0;
    private double maxFpsValue = 120.0;

    private float currentSin = 0.0F;

    public BenchmarkResultsScreen(Screen parent, List<Double> withoutFrameTimes, List<Double> withFrameTimes, File reportFile) {
        super(Component.literal("Benchmark Results"));
        this.parent = parent;
        this.withoutFrameTimes = new ArrayList<>(withoutFrameTimes);
        this.withFrameTimes = new ArrayList<>(withFrameTimes);
        this.reportFile = reportFile;

        this.metricsWithout = BenchmarkReportWriter.Metrics.compute(this.withoutFrameTimes);
        this.metricsWith = BenchmarkReportWriter.Metrics.compute(this.withFrameTimes);

        prepareChartPoints();
    }

    private void prepareChartPoints() {
        int pointsCount = 40;
        this.chartPointsWithout = getFpsPoints(this.withoutFrameTimes, pointsCount);
        this.chartPointsWith = getFpsPoints(this.withFrameTimes, pointsCount);

        double min = Double.MAX_VALUE;
        double max = 0.0;

        for (double fps : this.chartPointsWithout) {
            if (fps < min) min = fps;
            if (fps > max) max = fps;
        }
        for (double fps : this.chartPointsWith) {
            if (fps < min) min = fps;
            if (fps > max) max = fps;
        }

        if (min == Double.MAX_VALUE) {
            min = 0.0;
        }

        // Add padding
        this.minFpsValue = Math.max(0.0, min - 5.0);
        this.maxFpsValue = max + 10.0;
        if (this.maxFpsValue <= this.minFpsValue) {
            this.maxFpsValue = this.minFpsValue + 60.0;
        }
    }

    private List<Double> getFpsPoints(List<Double> frameTimes, int pointsCount) {
        List<Double> result = new ArrayList<>();
        if (frameTimes.isEmpty()) {
            for (int i = 0; i < pointsCount; i++) result.add(0.0);
            return result;
        }
        double size = frameTimes.size();
        double step = size / pointsCount;
        for (int i = 0; i < pointsCount; i++) {
            int start = (int) (i * step);
            int end = (int) Math.min(size, (i + 1) * step);
            if (start >= end) {
                result.add(result.isEmpty() ? 0.0 : result.get(result.size() - 1));
                continue;
            }
            double sum = 0;
            for (int j = start; j < end; j++) {
                sum += frameTimes.get(j);
            }
            double avgTimeMs = sum / (end - start);
            double fps = avgTimeMs > 0 ? 1000.0 / avgTimeMs : 0.0;
            result.add(fps);
        }
        return result;
    }

    @Override
    protected void init() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        this.currentSin = (float) Math.sin(System.currentTimeMillis() * 0.003);
        this.extractBackground(graphics, mouseX, mouseY, partialTicks);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int mainWidth = 380;
        int mainHeight = 220;
        int mainX1 = centerX - mainWidth / 2;
        int mainY1 = centerY - mainHeight / 2;
        int mainX2 = centerX + mainWidth / 2;
        int mainY2 = centerY + mainHeight / 2;

        // Opaque background
        graphics.fill(mainX1, mainY1, mainX2, mainY2, 0xDD0B0C0E);
        drawGlowingRect(graphics, mainX1, mainY1, mainX2, mainY2, 0xFF2ECC71);

        // Header Title
        graphics.centeredText(this.font, Component.literal("§2§lCullify §a§lBenchmark Results"), centerX, mainY1 + 10, 0xFFFFFFFF);

        // Left Metrics Column
        int leftX = mainX1 + 15;
        int statsY = mainY1 + 35;

        double gainAvg = this.metricsWithout.avgFps > 0 ? ((this.metricsWith.avgFps - this.metricsWithout.avgFps) / this.metricsWithout.avgFps) * 100.0 : 0.0;
        double gain1 = this.metricsWithout.low1Fps > 0 ? ((this.metricsWith.low1Fps - this.metricsWithout.low1Fps) / this.metricsWithout.low1Fps) * 100.0 : 0.0;

        // Metrics Section Card
        drawSectionBackground(graphics, leftX, statsY, centerX - 10, mainY2 - 35);
        graphics.text(this.font, Component.literal("§6§nComparative Stats"), leftX + 8, statsY + 6, 0xFFFFFFFF, false);

        int rowY = statsY + 22;
        graphics.text(this.font, Component.literal("Average FPS:"), leftX + 8, rowY, 0xFFE0E0E0, false);
        graphics.text(this.font, Component.literal(String.format("§7Without: §f%.1f", this.metricsWithout.avgFps)), leftX + 16, rowY + 10, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.literal(String.format("§aWith:    §f%.1f", this.metricsWith.avgFps)), leftX + 16, rowY + 20, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.literal(String.format("§eGain:    §a%+.1f%%", gainAvg)), leftX + 16, rowY + 30, 0xFFFFFFFF, false);

        rowY += 45;
        graphics.text(this.font, Component.literal("1% Low FPS (Stability):"), leftX + 8, rowY, 0xFFE0E0E0, false);
        graphics.text(this.font, Component.literal(String.format("§7Without: §f%.1f", this.metricsWithout.low1Fps)), leftX + 16, rowY + 10, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.literal(String.format("§aWith:    §f%.1f", this.metricsWith.low1Fps)), leftX + 16, rowY + 20, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.literal(String.format("§eGain:    §a%+.1f%%", gain1)), leftX + 16, rowY + 30, 0xFFFFFFFF, false);

        // Right Chart Column
        int rightX1 = centerX + 10;
        int rightX2 = mainX2 - 15;
        int chartY1 = statsY + 20;
        int chartY2 = mainY2 - 35;

        graphics.text(this.font, Component.literal("§d§nFPS Stability Graph"), rightX1, statsY + 6, 0xFFFFFFFF, false);

        // Chart box background
        graphics.fill(rightX1, chartY1, rightX2, chartY2, 0x33000000);
        graphics.fill(rightX1, chartY1, rightX2, chartY1 + 1, 0x11FFFFFF);
        graphics.fill(rightX1, chartY2 - 1, rightX2, chartY2, 0x11FFFFFF);
        graphics.fill(rightX1, chartY1, rightX1 + 1, chartY2, 0x11FFFFFF);
        graphics.fill(rightX2 - 1, chartY1, rightX2, chartY2, 0x11FFFFFF);

        // Mid line
        int midY = (chartY1 + chartY2) / 2;
        for (int x = rightX1 + 2; x < rightX2 - 2; x += 4) {
            graphics.fill(x, midY, x + 2, midY + 1, 0x11FFFFFF);
        }

        // Draw Chart Lines
        drawFpsChartLine(graphics, rightX1, rightX2, chartY1, chartY2, this.chartPointsWithout, 0xFFE74C3C); // Red for Without
        drawFpsChartLine(graphics, rightX1, rightX2, chartY1, chartY2, this.chartPointsWith, 0xFF2ECC71);    // Green for With

        // Chart Labels
        graphics.text(this.font, Component.literal(String.format("%.0f", this.maxFpsValue)), rightX1 + 4, chartY1 + 3, 0x66FFFFFF, false);
        graphics.text(this.font, Component.literal(String.format("%.0f", this.minFpsValue)), rightX1 + 4, chartY2 - 11, 0x66FFFFFF, false);

        // Bottom Legend under chart
        int legendY = chartY2 + 4;
        graphics.fill(rightX1, legendY + 3, rightX1 + 10, legendY + 5, 0xFFE74C3C);
        graphics.text(this.font, Component.literal("Without"), rightX1 + 13, legendY, 0x88FFFFFF, false);

        graphics.fill(rightX1 + 70, legendY + 3, rightX1 + 80, legendY + 5, 0xFF2ECC71);
        graphics.text(this.font, Component.literal("With"), rightX1 + 83, legendY, 0x88FFFFFF, false);

        // Bottom Action Buttons
        int buttonY1 = mainY2 - 25;
        int buttonY2 = mainY2 - 8;

        int doneX1 = centerX - 120;
        int doneX2 = centerX - 10;
        boolean doneHovered = mouseX >= doneX1 && mouseX <= doneX2 && mouseY >= buttonY1 && mouseY <= buttonY2;
        drawButton(graphics, doneX1, buttonY1, doneX2, buttonY2, Component.translatable("gui.done"), doneHovered);

        int reportX1 = centerX + 10;
        int reportX2 = centerX + 120;
        boolean reportHovered = mouseX >= reportX1 && mouseX <= reportX2 && mouseY >= buttonY1 && mouseY <= buttonY2;
        boolean hasReport = this.reportFile != null && this.reportFile.exists();
        drawButton(graphics, reportX1, buttonY1, reportX2, buttonY2, Component.literal("Open Report"), reportHovered && hasReport);

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private void drawFpsChartLine(GuiGraphicsExtractor graphics, int x1, int x2, int y1, int y2, List<Double> points, int color) {
        if (points.size() < 2) return;
        int w = x2 - x1 - 2;
        int h = y2 - y1 - 4;

        int prevX = -1;
        int prevY = -1;

        for (int i = 0; i < points.size(); i++) {
            double fps = points.get(i);
            int cx = x1 + 1 + (int) ((double) i / (points.size() - 1) * w);
            int cy = y2 - 2 - (int) ((fps - this.minFpsValue) / (this.maxFpsValue - this.minFpsValue) * h);
            // clamp Y within box limits
            cy = Math.min(y2 - 2, Math.max(y1 + 2, cy));

            if (prevX != -1) {
                drawLine(graphics, prevX, prevY, cx, cy, color);
            }
            prevX = cx;
            prevY = cy;
        }
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

    private void drawSectionBackground(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        graphics.fill(x1, y1, x2, y2, 0x11FFFFFF);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, Component label, boolean isHovered) {
        graphics.fill(x1, y1, x2, y2, isHovered ? 0x22FFFFFF : 0x11FFFFFF);
        if (isHovered) {
            float pulse = 0.6F + 0.4F * this.currentSin;
            int glowColor = ((int) (120 * pulse) << 24) | 0x2ECC71;
            graphics.fill(x1, y1, x2, y1 + 1, glowColor);
            graphics.fill(x1, y2 - 1, x2, y2, glowColor);
            graphics.fill(x1, y1, x1 + 1, y2, glowColor);
            graphics.fill(x2 - 1, y1, x2, y2, glowColor);
        }
        graphics.centeredText(this.font, label, (x1 + x2) / 2, y1 + (y2 - y1 - 8) / 2, 0xFFFFFFFF);
    }

    private void drawGlowingRect(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float pulse = 0.4F + 0.6F * this.currentSin;

        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);

        int alpha1 = (int) (40 * pulse) << 24;
        int glowColor1 = alpha1 | (r << 16) | (g << 8) | b;
        graphics.fill(x1 - 1, y1 - 1, x2 + 1, y1, glowColor1);
        graphics.fill(x1 - 1, y2, x2 + 1, y2 + 1, glowColor1);
        graphics.fill(x1 - 1, y1 - 1, x1, y2 + 1, glowColor1);
        graphics.fill(x2, y1 - 1, x2 + 1, y2 + 1, glowColor1);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int mainHeight = 220;
        int mainY2 = centerY + mainHeight / 2;

        int buttonY1 = mainY2 - 25;
        int buttonY2 = mainY2 - 8;

        int doneX1 = centerX - 120;
        int doneX2 = centerX - 10;
        if (mouseX >= doneX1 && mouseX <= doneX2 && mouseY >= buttonY1 && mouseY <= buttonY2) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.onClose();
            return true;
        }

        int reportX1 = centerX + 10;
        int reportX2 = centerX + 120;
        if (mouseX >= reportX1 && mouseX <= reportX2 && mouseY >= buttonY1 && mouseY <= buttonY2) {
            if (this.reportFile != null && this.reportFile.exists()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                try {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(this.reportFile);
                    } else {
                        String os = System.getProperty("os.name").toLowerCase();
                        if (os.contains("win")) {
                            Runtime.getRuntime().exec(new String[]{"explorer.exe", this.reportFile.getAbsolutePath()});
                        } else if (os.contains("mac")) {
                            Runtime.getRuntime().exec(new String[]{"open", this.reportFile.getAbsolutePath()});
                        } else {
                            Runtime.getRuntime().exec(new String[]{"xdg-open", this.reportFile.getAbsolutePath()});
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }
        }

        return super.mouseClicked(event, handled);
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
