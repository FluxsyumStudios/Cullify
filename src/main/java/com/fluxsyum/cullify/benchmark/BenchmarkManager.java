package com.fluxsyum.cullify.benchmark;

import com.fluxsyum.cullify.CullifyMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.LongAdder;

public class BenchmarkManager {

    public enum State {
        IDLE,
        WARMUP_WITHOUT,
        RUNNING_WITHOUT,
        WARMUP_WITH,
        RUNNING_WITH
    }

    private static State state = State.IDLE;

    // Stats
    public static final LongAdder blocksTested = new LongAdder();
    public static final LongAdder blocksCulled = new LongAdder();
    public static final LongAdder cacheHits = new LongAdder();
    public static final LongAdder cacheMisses = new LongAdder();
    public static final LongAdder cacheRebuilds = new LongAdder();
    public static final LongAdder cacheRebuildTimeNanos = new LongAdder();

    private static long phaseStartTimeMillis = 0;
    private static long phaseDurationMillis = 0;
    private static long lastFrameTimeNanos = 0;

    private static final List<Double> currentPhaseFrameTimes = new ArrayList<>();
    private static final List<Double> withoutFrameTimes = new ArrayList<>();
    private static final List<Double> withFrameTimes = new ArrayList<>();

    public static void start(int seconds) {
        if (state != State.IDLE) return;

        phaseDurationMillis = seconds * 1000L;
        withoutFrameTimes.clear();
        withFrameTimes.clear();
        synchronized (currentPhaseFrameTimes) {
            currentPhaseFrameTimes.clear();
        }
        lastFrameTimeNanos = 0;

        sendMessage(Component.literal("§e[Cullify] Starting Comparison Benchmark..."));
        sendMessage(Component.literal("§7The benchmark runs in two phases of " + seconds + " seconds each."));
        sendMessage(Component.literal("§7Keep your player still during the benchmark."));

        startPhase(State.WARMUP_WITHOUT);
    }

    public static void stop() {
        if (state == State.IDLE) return;
        state = State.IDLE;
        CullifyMod.benchmarkBypass = false;
        Minecraft.getInstance().execute(() -> {
            CullifyMod.updateConfigCache();
            CullifyMod.incrementConfigVersion();
            CullifyMod.voxelGridDirty = true;
            if (Minecraft.getInstance().levelRenderer != null) {
                Minecraft.getInstance().levelRenderer.allChanged();
            }
        });
        sendMessage(Component.literal("§c[Cullify] Benchmark cancelled."));
    }

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static boolean isBypassed() {
        return state == State.WARMUP_WITHOUT || state == State.RUNNING_WITHOUT;
    }

    public static void checkTime() {
        if (state == State.IDLE) return;

        long now = System.currentTimeMillis();
        long elapsed = now - phaseStartTimeMillis;

        if (state == State.WARMUP_WITHOUT) {
            if (elapsed >= 3000L) { // 3 seconds warmup
                startPhase(State.RUNNING_WITHOUT);
            }
        } else if (state == State.RUNNING_WITHOUT) {
            if (elapsed >= phaseDurationMillis) {
                startPhase(State.WARMUP_WITH);
            }
        } else if (state == State.WARMUP_WITH) {
            if (elapsed >= 3000L) { // 3 seconds warmup
                startPhase(State.RUNNING_WITH);
            }
        } else if (state == State.RUNNING_WITH) {
            if (elapsed >= phaseDurationMillis) {
                stopBenchmark();
            }
        }
    }

    public static void recordFrame() {
        if (state == State.IDLE) return;

        // Skip recording frame times during warmups
        if (state == State.WARMUP_WITHOUT || state == State.WARMUP_WITH) {
            lastFrameTimeNanos = System.nanoTime();
            return;
        }

        long now = System.nanoTime();
        if (lastFrameTimeNanos > 0) {
            long diff = now - lastFrameTimeNanos;
            double frameTimeMs = diff / 1_000_000.0;
            synchronized (currentPhaseFrameTimes) {
                currentPhaseFrameTimes.add(frameTimeMs);
            }
        }
        lastFrameTimeNanos = now;
    }

    private static void startPhase(State newState) {
        if (state == State.RUNNING_WITHOUT) {
            synchronized (currentPhaseFrameTimes) {
                withoutFrameTimes.addAll(currentPhaseFrameTimes);
            }
        }

        state = newState;
        phaseStartTimeMillis = System.currentTimeMillis();
        synchronized (currentPhaseFrameTimes) {
            currentPhaseFrameTimes.clear();
        }
        lastFrameTimeNanos = 0;

        if (newState == State.WARMUP_WITHOUT) {
            sendMessage(Component.literal("§e[Cullify] Phase 1/2: Warmup (WITHOUT Cullify) - Loading chunks..."));
            CullifyMod.benchmarkBypass = true;
            Minecraft.getInstance().execute(() -> {
                CullifyMod.updateConfigCache();
                CullifyMod.incrementConfigVersion();
                CullifyMod.voxelGridDirty = true;
                if (Minecraft.getInstance().levelRenderer != null) {
                    Minecraft.getInstance().levelRenderer.allChanged();
                }
            });
        }
        else if (newState == State.RUNNING_WITHOUT) {
            sendMessage(Component.literal("§a[Cullify] Phase 1/2: RECORDING (WITHOUT Cullify)..."));
        }
        else if (newState == State.WARMUP_WITH) {
            sendMessage(Component.literal("§e[Cullify] Phase 2/2: Warmup (WITH Cullify) - Loading chunks..."));
            CullifyMod.benchmarkBypass = false;
            Minecraft.getInstance().execute(() -> {
                CullifyMod.updateConfigCache();
                CullifyMod.incrementConfigVersion();
                CullifyMod.voxelGridDirty = true;
                if (Minecraft.getInstance().levelRenderer != null) {
                    Minecraft.getInstance().levelRenderer.allChanged();
                }
            });
        }
        else if (newState == State.RUNNING_WITH) {
            sendMessage(Component.literal("§a[Cullify] Phase 2/2: RECORDING (WITH Cullify)..."));
            resetStats();
        }
    }

    private static void stopBenchmark() {
        synchronized (currentPhaseFrameTimes) {
            withFrameTimes.addAll(currentPhaseFrameTimes);
        }
        state = State.IDLE;

        BenchmarkReportWriter.saveComparisonReport(withoutFrameTimes, withFrameTimes);
    }

    private static void resetStats() {
        blocksTested.reset();
        blocksCulled.reset();
        cacheHits.reset();
        cacheMisses.reset();
        cacheRebuilds.reset();
        cacheRebuildTimeNanos.reset();
    }

    private static void sendMessage(Component msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(msg);
        }
    }
}
