package com.fluxsyum.cullify;

import java.util.concurrent.atomic.LongAdder;

/**
 * Central debug and diagnostics manager for Cullify.
 *
 * All counters use LongAdder for lock-free thread-safe increments from
 * chunk builder worker threads, with periodic snapshots read from the
 * render thread for display.
 */
public class CullifyDebugManager {

    // --- Thread-safe counters (incremented from worker threads) ---

    /** Total vegetation blocks hidden during chunk meshing. */
    public static final LongAdder culledBlocks = new LongAdder();

    /** Total vegetation blocks replaced with fluid state (WATER) instead of AIR. */
    public static final LongAdder waterReplacements = new LongAdder();

    /** Total chunk sections marked dirty for rebuild due to distance transitions. */
    public static final LongAdder chunkUpdates = new LongAdder();

    /** Total draw calls emitted for chunk sections. */
    public static final LongAdder drawCalls = new LongAdder();

    // --- Snapshot values (read on render thread for display) ---

    public static volatile long lastCulledBlocks;
    public static volatile long lastWaterReplacements;
    public static volatile long lastChunkUpdates;
    public static volatile long lastDrawCalls;

    // --- Detection flags ---

    /** Whether Sodium was detected at runtime. */
    public static volatile boolean sodiumDetected;

    /** Whether MixinLevelSlice was successfully applied. */
    public static volatile boolean mixinLevelSliceApplied;

    // --- Debug level ---

    public enum DebugLevel {
        /** No debug output. */
        OFF,
        /** Basic statistics on action bar. */
        BASIC,
        /** Verbose logging and extended HUD. */
        VERBOSE
    }

    /**
     * Current debug level. Controlled via config and /cullify command.
     * Read directly, but always write through {@link #setDebugLevel} so that
     * {@link #statsEnabled} stays in sync.
     */
    public static volatile DebugLevel debugLevel = DebugLevel.OFF;

    /**
     * Whether the diagnostic counters should be fed.
     *
     * The counters exist purely to drive the debug HUD, but they are incremented from the
     * chunk-build workers once per culled block — a contended atomic on a path that runs
     * millions of times per chunk build. Gating them keeps that cost out of normal play.
     * Mirrors {@link #debugLevel} as a plain boolean so the hot path reads one volatile
     * field instead of an enum reference.
     */
    public static volatile boolean statsEnabled = false;

    /** Sets the debug level, keeping the counter gate in sync. */
    public static void setDebugLevel(DebugLevel level) {
        debugLevel = level;
        statsEnabled = level != DebugLevel.OFF;
    }

    /**
     * Takes a snapshot of all counters (sumThenReset) for display.
     * Should be called periodically from the render thread (e.g. every 20 ticks).
     */
    public static void snapshot() {
        lastCulledBlocks = culledBlocks.sumThenReset();
        lastWaterReplacements = waterReplacements.sumThenReset();
        lastChunkUpdates = chunkUpdates.sumThenReset();
        lastDrawCalls = drawCalls.sumThenReset();
    }

    /**
     * Resets all counters and snapshots to zero.
     */
    public static void reset() {
        culledBlocks.reset();
        waterReplacements.reset();
        chunkUpdates.reset();
        drawCalls.reset();
        lastCulledBlocks = 0;
        lastWaterReplacements = 0;
        lastChunkUpdates = 0;
        lastDrawCalls = 0;
    }

    /**
     * Syncs the debug level with the config value.
     */
    public static void syncFromConfig() {
        if (CullifyConfig.DEBUG_MODE.get()) {
            if (debugLevel == DebugLevel.OFF) {
                setDebugLevel(DebugLevel.BASIC);
            }
        } else {
            setDebugLevel(DebugLevel.OFF);
        }
    }
}
