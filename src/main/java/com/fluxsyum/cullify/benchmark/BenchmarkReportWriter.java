package com.fluxsyum.cullify.benchmark;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class BenchmarkReportWriter {

    public static class Metrics {
        public double avgFps;
        public double minFps;
        public double maxFps;
        public double low1Fps;
        public double low01Fps;

        public static Metrics compute(List<Double> frameTimes) {
            Metrics m = new Metrics();
            if (frameTimes.isEmpty()) {
                return m;
            }

            double totalTimeMs = 0;
            for (double t : frameTimes) {
                totalTimeMs += t;
            }
            m.avgFps = 1000.0 / (totalTimeMs / frameTimes.size());

            List<Double> sorted = new ArrayList<>(frameTimes);
            sorted.sort(Double::compareTo);

            m.minFps = 1000.0 / sorted.get(sorted.size() - 1);
            m.maxFps = 1000.0 / sorted.get(0);

            // 1% Low: Average of worst 1% frames
            int count1 = Math.max(1, (int) (sorted.size() * 0.01));
            double sum1 = 0;
            for (int i = sorted.size() - count1; i < sorted.size(); i++) {
                sum1 += sorted.get(i);
            }
            m.low1Fps = 1000.0 / (sum1 / count1);

            // 0.1% Low: Average of worst 0.1% frames
            int count01 = Math.max(1, (int) (sorted.size() * 0.001));
            double sum01 = 0;
            for (int i = sorted.size() - count01; i < sorted.size(); i++) {
                sum01 += sorted.get(i);
            }
            m.low01Fps = 1000.0 / (sum01 / count01);

            return m;
        }
    }

    public static File saveComparisonReport(List<Double> withoutTimes, List<Double> withTimes) {
        Metrics metricsWithout = Metrics.compute(withoutTimes);
        Metrics metricsWith = Metrics.compute(withTimes);

        // Calculate performance gains
        double gainAvg = metricsWithout.avgFps > 0 ? ((metricsWith.avgFps - metricsWithout.avgFps) / metricsWithout.avgFps) * 100.0 : 0.0;
        double gainMin = metricsWithout.minFps > 0 ? ((metricsWith.minFps - metricsWithout.minFps) / metricsWithout.minFps) * 100.0 : 0.0;
        double gainMax = metricsWithout.maxFps > 0 ? ((metricsWith.maxFps - metricsWithout.maxFps) / metricsWithout.maxFps) * 100.0 : 0.0;
        double gain1 = metricsWithout.low1Fps > 0 ? ((metricsWith.low1Fps - metricsWithout.low1Fps) / metricsWithout.low1Fps) * 100.0 : 0.0;
        double gain01 = metricsWithout.low01Fps > 0 ? ((metricsWith.low01Fps - metricsWithout.low01Fps) / metricsWithout.low01Fps) * 100.0 : 0.0;

        // Collect other metrics
        long tested = BenchmarkManager.blocksTested.sum();
        long culled = BenchmarkManager.blocksCulled.sum();
        long hits = BenchmarkManager.cacheHits.sum();
        long misses = BenchmarkManager.cacheMisses.sum();
        long rebuilds = BenchmarkManager.cacheRebuilds.sum();
        long rebuildTimeNanos = BenchmarkManager.cacheRebuildTimeNanos.sum();

        double cullRatio = tested > 0 ? (culled / (double) tested) * 100.0 : 0.0;
        double hitRatio = (hits + misses) > 0 ? (hits / (double) (hits + misses)) * 100.0 : 0.0;
        double avgRebuildTimeMs = rebuilds > 0 ? (rebuildTimeNanos / (double) rebuilds) / 1_000_000.0 : 0.0;

        // Build file content
        String report = buildReportString(
                metricsWithout, metricsWith,
                gainAvg, gainMin, gainMax, gain1, gain01,
                tested, culled, cullRatio, hits, misses, hitRatio, rebuilds, avgRebuildTimeMs
        );

        // Write to file
        File logsDir = new File(Minecraft.getInstance().gameDirectory, "logs/cullify_benchmarks");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        String filename = "benchmark_comparison_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt";
        File file = new File(logsDir, filename);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(report);
            
            Component fileLink = Component.literal(file.getName())
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.OpenFile(file))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cullify.benchmark.click_to_open")))
                            .withUnderlined(true)
                            .withColor(ChatFormatting.GREEN)
                    );
            
            sendMessage(Component.translatable("cullify.benchmark.completed", fileLink)
                    .withStyle(ChatFormatting.GOLD));
        } catch (IOException e) {
            sendMessage(Component.literal("§c[Cullify] Failed to save benchmark to file: " + e.getMessage()));
        }

        // Print comparative summary in chat
        sendMessage(Component.literal("§e============================================="));
        sendMessage(Component.literal("§6            CULLIFY BENCHMARK RESULTS"));
        sendMessage(Component.literal("§e============================================="));
        sendMessage(Component.literal(String.format("§7Without Cullify: §fAvg: %.1f FPS §7| §fMin: %.1f §7| §f1%% Low: %.1f",
                metricsWithout.avgFps, metricsWithout.minFps, metricsWithout.low1Fps)));
        sendMessage(Component.literal(String.format("§aWith Cullify:    §fAvg: %.1f FPS §7| §fMin: %.1f §7| §f1%% Low: %.1f",
                metricsWith.avgFps, metricsWith.minFps, metricsWith.low1Fps)));
        sendMessage(Component.literal(String.format("§ePerformance Gain: §a%+.2f%% Avg FPS §7| §a%+.2f%% 1%% Lows",
                gainAvg, gain1)));
        sendMessage(Component.literal("§e============================================="));

        return file;
    }

    private static String buildReportString(
            Metrics mWithout, Metrics mWith,
            double gainAvg, double gainMin, double gainMax, double gain1, double gain01,
            long tested, long culled, double cullRatio, 
            long hits, long misses, double hitRatio, long rebuilds, double avgRebuildTimeMs) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================================================\n");
        sb.append("                 CULLIFY COMPARISON BENCHMARK REPORT\n");
        sb.append("=========================================================================\n");
        sb.append("Date: ").append(new Date().toString()).append("\n");
        sb.append("\n");
        sb.append("--- HARDWARE & SYSTEM ---\n");
        sb.append("OS: ").append(HardwareProfiler.getOS()).append("\n");
        sb.append("CPU: ").append(HardwareProfiler.getCPU()).append("\n");
        sb.append("GPU: ").append(HardwareProfiler.getGPU()).append("\n");
        sb.append("RAM: ").append(HardwareProfiler.getRAM()).append("\n");
        sb.append("Java: ").append(HardwareProfiler.getJavaVersion()).append("\n");
        sb.append("\n");
        sb.append("--- RESULTS COMPARISON ---\n");
        sb.append(String.format("%-22s %-20s %-20s %-15s\n", "METRIC", "WITHOUT CULLIFY", "WITH CULLIFY", "GAIN"));
        sb.append(String.format("%-22s %-20s %-20s %-+14.2f%%\n", "Average FPS", String.format("%.2f FPS", mWithout.avgFps), String.format("%.2f FPS", mWith.avgFps), gainAvg));
        sb.append(String.format("%-22s %-20s %-20s %-+14.2f%%\n", "Minimum FPS", String.format("%.2f FPS", mWithout.minFps), String.format("%.2f FPS", mWith.minFps), gainMin));
        sb.append(String.format("%-22s %-20s %-20s %-+14.2f%%\n", "Maximum FPS", String.format("%.2f FPS", mWithout.maxFps), String.format("%.2f FPS", mWith.maxFps), gainMax));
        sb.append(String.format("%-22s %-20s %-20s %-+14.2f%%\n", "1% Low FPS", String.format("%.2f FPS", mWithout.low1Fps), String.format("%.2f FPS", mWith.low1Fps), gain1));
        sb.append(String.format("%-22s %-20s %-20s %-+14.2f%%\n", "0.1% Low FPS", String.format("%.2f FPS", mWithout.low01Fps), String.format("%.2f FPS", mWith.low01Fps), gain01));
        sb.append("\n");
        sb.append("--- CULLING STATISTICS (WITH CULLIFY) ---\n");
        sb.append("Total Blocks Tested: ").append(tested).append("\n");
        sb.append("Total Blocks Culled: ").append(culled).append("\n");
        sb.append("Culling Efficiency:  ").append(String.format("%.2f%%\n", cullRatio));
        sb.append("\n");
        sb.append("--- CACHE PERFORMANCE (WITH CULLIFY) ---\n");
        sb.append("Cache Hits (Fast Path):   ").append(hits).append("\n");
        sb.append("Cache Misses (Slow Path): ").append(misses).append("\n");
        sb.append("Cache Hit Ratio:          ").append(String.format("%.2f%%\n", hitRatio));
        sb.append("\n");
        sb.append("--- AABB GRID REBUILDS ---\n");
        sb.append("Total Rebuilds: ").append(rebuilds).append("\n");
        sb.append("Average Rebuild Time: ").append(String.format("%.4f ms\n", avgRebuildTimeMs));
        sb.append("Total Time in Rebuilds: ").append(String.format("%.2f ms\n", BenchmarkManager.cacheRebuildTimeNanos.sum() / 1_000_000.0));
        sb.append("=========================================================================\n");
        return sb.toString();
    }

    private static void sendMessage(Component msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(msg);
        }
    }
}
