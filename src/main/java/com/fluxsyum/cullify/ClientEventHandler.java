package com.fluxsyum.cullify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@EventBusSubscriber(modid = CullifyMod.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    /**
     * Dedicated scheduler for async config saves with debounce.
     * Config saves (disk I/O) are NEVER done on the main/render thread.
     */
    private static final ScheduledExecutorService ASYNC_SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Cullify-Async-Saver");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Reference to the pending save future — cancelled and replaced on each change. */
    private static final AtomicReference<ScheduledFuture<?>> pendingSave = new AtomicReference<>(null);

    public static void scheduleDebouncedSave() {
        ScheduledFuture<?> existing = pendingSave.getAndSet(null);
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future = ASYNC_SAVER.schedule(() -> {
            try { CullifyConfig.SPEC.save(); } catch (Throwable e) {}
        }, 2, TimeUnit.SECONDS);
        pendingSave.set(future);
    }

    /**
     * Single-thread background executor for section-state classification.
     * MIN_PRIORITY + daemon ensures it never competes with the render thread
     * and is automatically killed when the JVM exits.
     */
    private static final ExecutorService ASYNC_CLASSIFIER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Cullify-Async-Classifier");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Prevents queueing more than one pending classification task at a time. */
    private static final AtomicBoolean classifierPending = new AtomicBoolean(false);

    private static double lastPlayerX = 0;
    private static double lastPlayerY = 0;
    private static double lastPlayerZ = 0;
    private static boolean initialized = false;
    private static int ticksSinceLastCheck = 0;
    private static int debugTickCounter = 0;

    // Mirrored config values for per-tick change detection (main thread only)
    private static boolean lastEnabled = true;
    private static boolean lastCullGrass = true;
    private static boolean lastCullFlowers = true;
    private static boolean lastCullOther = true;
    private static int lastGrassDist = 48;
    private static int lastFlowerDist = 48;
    private static int lastOtherDist = 32;
    private static CullifyConfig.CullingShape lastCullingShape = CullifyConfig.CullingShape.SPHERE;
    private static boolean lastSmartScale = false;
    private static int lastTargetFps = 60;
    private static boolean lastLightAware = false;

    /** Tick counter for Smart Scale FPS adjustment (runs every 20 ticks = 1 second) */
    private static int smartScaleTickCounter = 0;

    /**
     * Thread-safe map from packed section position to its classified state.
     * Written by the background classifier thread, cleared from the main thread.
     */
    private static final ConcurrentHashMap<Long, SectionState> sectionStates = new ConcurrentHashMap<>();
    private static volatile int tickCounter = 0;

    public static class SectionState {
        public byte grassState;
        public byte flowerState;
        public byte otherState;
        public double lastRebuildX;
        public double lastRebuildY;
        public double lastRebuildZ;
        public int lastSeenTick;
    }

    @SubscribeEvent
    public static void onRenderFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        com.fluxsyum.cullify.benchmark.BenchmarkManager.recordFrame();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) {
            CullifyMod.hasPlayer = false;
            initialized = false;
            sectionStates.clear();
            return;
        }

        // Open config menu on key mapping press
        if (com.fluxsyum.cullify.client.ClientModBusSubscriber.configKeyMapping != null &&
            com.fluxsyum.cullify.client.ClientModBusSubscriber.configKeyMapping.consumeClick()) {
            mc.setScreen(new com.fluxsyum.cullify.client.CullifyConfigScreen(null));
        }

        Vec3 cameraPos;
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null &&
                mc.gameRenderer.getMainCamera().isInitialized()) {
            cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        } else {
            cameraPos = player.position();
        }

        CullifyMod.playerX = cameraPos.x;
        CullifyMod.playerY = cameraPos.y;
        CullifyMod.playerZ = cameraPos.z;
        CullifyMod.hasPlayer = true;

        if (!initialized) {
            lastPlayerX = cameraPos.x;
            lastPlayerY = cameraPos.y;
            lastPlayerZ = cameraPos.z;
            lastEnabled       = CullifyConfig.ENABLED.get();
            lastCullGrass     = CullifyConfig.CULL_GRASS.get();
            lastCullFlowers   = CullifyConfig.CULL_FLOWERS.get();
            lastCullOther     = CullifyConfig.CULL_OTHER_PLANTS.get();
            lastGrassDist     = CullifyConfig.GRASS_CULL_DISTANCE.get();
            lastFlowerDist    = CullifyConfig.FLOWER_CULL_DISTANCE.get();
            lastOtherDist     = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
            lastCullingShape  = CullifyConfig.CULLING_SHAPE.get();
            initialized = true;
            ticksSinceLastCheck = 0;
            debugTickCounter = 0;
            CullifyMod.updateConfigCache();
            CullifyMod.voxelGridDirty = true;
            CullifyDebugManager.syncFromConfig();
            return;
        }

        boolean enabled      = CullifyConfig.ENABLED.get();
        boolean cullGrass    = CullifyConfig.CULL_GRASS.get();
        boolean cullFlowers  = CullifyConfig.CULL_FLOWERS.get();
        boolean cullOther    = CullifyConfig.CULL_OTHER_PLANTS.get();
        int     grassDist    = CullifyConfig.GRASS_CULL_DISTANCE.get();
        int     flowerDist   = CullifyConfig.FLOWER_CULL_DISTANCE.get();
        int     otherDist    = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
        CullifyConfig.CullingShape cullingShape = CullifyConfig.CULLING_SHAPE.get();
        boolean smartScale   = CullifyConfig.SMART_SCALE.get();
        int     targetFps    = CullifyConfig.TARGET_FPS.get();
        boolean lightAware   = CullifyConfig.LIGHT_AWARE_CULLING.get();

        if (enabled != lastEnabled
                || cullGrass    != lastCullGrass
                || cullFlowers  != lastCullFlowers
                || cullOther    != lastCullOther
                || grassDist    != lastGrassDist
                || flowerDist   != lastFlowerDist
                || otherDist    != lastOtherDist
                || cullingShape != lastCullingShape
                || smartScale   != lastSmartScale
                || targetFps    != lastTargetFps
                || lightAware   != lastLightAware) {
            lastEnabled      = enabled;
            lastCullGrass    = cullGrass;
            lastCullFlowers  = cullFlowers;
            lastCullOther    = cullOther;
            lastGrassDist    = grassDist;
            lastFlowerDist   = flowerDist;
            lastOtherDist    = otherDist;
            lastCullingShape = cullingShape;
            lastSmartScale   = smartScale;
            lastTargetFps    = targetFps;
            lastLightAware   = lightAware;
            sectionStates.clear();
            CullifyMod.sectionLightFactors.clear();
            scheduleDebouncedSave();
            CullifyMod.updateConfigCache();
            CullifyMod.incrementConfigVersion();
            CullifyMod.voxelGridDirty = true;
            CullifyMod.scheduleWorldReload();
        }

        // Smart Scale: adjust the distance multiplier every 20 ticks based on current FPS
        if (CullifyMod.cachedSmartScale) {
            smartScaleTickCounter++;
            if (smartScaleTickCounter >= 20) {
                smartScaleTickCounter = 0;
                int currentFps = mc.getFps();
                int target = CullifyMod.cachedTargetFps;
                float factor = CullifyMod.smartScaleFactor;
                if (currentFps < target) {
                    // FPS below target: shrink distances (min 0.5)
                    factor = Math.max(0.5f, factor - 0.05f);
                } else if (currentFps >= target + 10) {
                    // FPS comfortably above target: expand distances back (max 1.0)
                    factor = Math.min(1.0f, factor + 0.05f);
                }
                if (factor != CullifyMod.smartScaleFactor) {
                    CullifyMod.smartScaleFactor = factor;
                    CullifyMod.voxelGridDirty = true;
                }
            }
        } else {
            // Reset factor when Smart Scale is disabled
            if (CullifyMod.smartScaleFactor != 1.0f) {
                CullifyMod.smartScaleFactor = 1.0f;
                CullifyMod.voxelGridDirty = true;
                smartScaleTickCounter = 0;
            }
        }

        // Benchmark tick update
        com.fluxsyum.cullify.benchmark.BenchmarkManager.checkTime();

        debugTickCounter++;
        if (debugTickCounter >= 20) {
            debugTickCounter = 0;
            CullifyDebugManager.syncFromConfig();
            if (CullifyConfig.DEBUG_MODE.get()) {
                CullifyDebugManager.snapshot();
            }
        }

        ticksSinceLastCheck++;
        if (ticksSinceLastCheck >= 5) {
            ticksSinceLastCheck = 0;
            tickCounter++;

            double dx = cameraPos.x - lastPlayerX;
            double dy = cameraPos.y - lastPlayerY;
            double dz = cameraPos.z - lastPlayerZ;
            double distMovedSq = dx * dx + dy * dy + dz * dz;

            if (distMovedSq > 0.25) {
                // Update position snapshot immediately so the next tick doesn't re-trigger
                lastPlayerX = cameraPos.x;
                lastPlayerY = cameraPos.y;
                lastPlayerZ = cameraPos.z;

                // Rebuild voxel grid on background thread if dirty or player moved significantly
                if (CullifyMod.voxelGridDirty || distMovedSq > 16.0) {
                    final double rpx = cameraPos.x;
                    final double rpy = cameraPos.y;
                    final double rpz = cameraPos.z;
                    ASYNC_CLASSIFIER.submit(() -> CullifyMod.rebuildVoxelGrid(rpx, rpy, rpz));
                }

                // Submit section classification to background thread — at most one pending at a time
                if (classifierPending.compareAndSet(false, true)) {
                    final Vec3 snapPos     = cameraPos;
                    final ClientLevel snapLevel = level;
                    ASYNC_CLASSIFIER.submit(() -> {
                        try {
                            checkChunkTransitions(snapLevel, snapPos);
                        } finally {
                            classifierPending.set(false);
                        }
                    });
                }
            }
        }
    }

    @SuppressWarnings("null")
    private static void send(LocalPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cullify")
                .then(Commands.literal("menu").executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new com.fluxsyum.cullify.client.CullifyConfigScreen(null));
                    });
                    return 1;
                }))
                .then(Commands.literal("debug").executes(ctx -> {
                    boolean newState = !CullifyConfig.DEBUG_MODE.get();
                    CullifyConfig.DEBUG_MODE.set(newState);
                    scheduleDebouncedSave();
                    CullifyDebugManager.syncFromConfig();
                    Minecraft mc = Minecraft.getInstance();
                    LocalPlayer player = mc.player;
                    if (player != null) {
                        send(player, "§e[Cullify]§r Debug mode: " + (newState ? "§aON" : "§cOFF"));
                    }
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    sectionStates.clear();
                    CullifyMod.updateConfigCache();
                    CullifyMod.incrementConfigVersion();
                    CullifyMod.voxelGridDirty = true;
                    CullifyMod.scheduleWorldReload();
                    Minecraft mc = Minecraft.getInstance();
                    LocalPlayer player = mc.player;
                    if (player != null) {
                        send(player, "§e[Cullify]§r World reload scheduled.");
                    }
                    return 1;
                }))
                .then(Commands.literal("stats").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    LocalPlayer player = mc.player;
                    if (player != null) {
                        CullifyDebugManager.snapshot();
                        send(player, "§e§l[Cullify Statistics]§r");
                        send(player, "");
                        send(player, "§7Cullify: " + (CullifyConfig.ENABLED.get() ? "§aENABLED" : "§cDISABLED"));
                        send(player, "§7Renderer: " + (CullifyDebugManager.sodiumDetected ? "§aSodium" : "§eVanilla"));
                        send(player, "§7Mixin Applied: " + (CullifyDebugManager.mixinLevelSliceApplied ? "§a✔" : "§c✘"));
                        send(player, "");
                        send(player, "§7Grass Distance: §f" + CullifyConfig.GRASS_CULL_DISTANCE.get() + " blocks");
                        send(player, "§7Flower Distance: §f" + CullifyConfig.FLOWER_CULL_DISTANCE.get() + " blocks");
                        send(player, "§7Other Distance: §f" + CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get() + " blocks");
                        send(player, "");
                        String drawCallsStr = CullifyDebugManager.sodiumDetected
                            ? "§dMDI (Sodium)"
                            : "§d" + CullifyDebugManager.lastDrawCalls + " (Vanilla)";
                        send(player,
                            "§7Last sec — Culled: §a" + CullifyDebugManager.lastCulledBlocks +
                            "§7 | Water: §b" + CullifyDebugManager.lastWaterReplacements +
                            "§7 | Chunks: §6" + CullifyDebugManager.lastChunkUpdates +
                            "§7 | Draws: " + drawCallsStr
                        );
                    }
                    return 1;
                }))
                .then(Commands.literal("verbose").executes(ctx -> {
                    if (CullifyDebugManager.debugLevel == CullifyDebugManager.DebugLevel.VERBOSE) {
                        CullifyDebugManager.debugLevel = CullifyDebugManager.DebugLevel.BASIC;
                    } else {
                        CullifyDebugManager.debugLevel = CullifyDebugManager.DebugLevel.VERBOSE;
                        CullifyConfig.DEBUG_MODE.set(true);
                        scheduleDebouncedSave();
                    }
                    Minecraft mc = Minecraft.getInstance();
                    LocalPlayer player = mc.player;
                    if (player != null) {
                        send(player, "§e[Cullify]§r Debug level: §a" + CullifyDebugManager.debugLevel.name());
                    }
                    return 1;
                }))
                .then(Commands.literal("benchmark")
                    .then(Commands.literal("start")
                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3600))
                            .executes(context -> {
                                int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds");
                                if (com.fluxsyum.cullify.benchmark.BenchmarkManager.isRunning()) {
                                    context.getSource().sendFailure(Component.literal("Benchmark is already running!"));
                                    return 0;
                                }
                                com.fluxsyum.cullify.benchmark.BenchmarkManager.start(seconds);
                                context.getSource().sendSuccess(() -> Component.literal("§aStarted Cullify benchmark for " + seconds + " seconds."), false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("stop")
                        .executes(context -> {
                            if (!com.fluxsyum.cullify.benchmark.BenchmarkManager.isRunning()) {
                                context.getSource().sendFailure(Component.literal("No benchmark is currently running."));
                                return 0;
                            }
                            com.fluxsyum.cullify.benchmark.BenchmarkManager.stop();
                            context.getSource().sendSuccess(() -> Component.literal("§eBenchmark stopped manually."), false);
                            return 1;
                        })
                    )
                )
        );
    }

    /**
     * Called from the background classifier thread. Computes which sections have
     * changed culling state, collects dirty positions, and dispatches the actual
     * {@code setSectionDirty} calls back to the main thread to preserve render-thread safety.
     */
    private static void checkChunkTransitions(ClientLevel level, Vec3 cameraPos) {
        if (!CullifyMod.cachedEnabled) {
            return;
        }

        // Use cached distances (already account for enabled flags returning -1 when disabled)
        double grassDist  = CullifyMod.cachedCullGrass   ? CullifyMod.cachedGrassDist  : -1;
        double flowerDist = CullifyMod.cachedCullFlowers  ? CullifyMod.cachedFlowerDist : -1;
        double otherDist  = CullifyMod.cachedCullOther    ? CullifyMod.cachedOtherDist  : -1;

        double maxDist = Math.max(grassDist, Math.max(flowerDist, otherDist));
        if (maxDist < 0) return;

        int pSecX = ((int) cameraPos.x) >> 4;
        int pSecY = ((int) cameraPos.y) >> 4;
        int pSecZ = ((int) cameraPos.z) >> 4;
        int secRange = ((int) maxDist + 16) >> 4;

        int minSecY = level.getMinSection();
        int maxSecY = level.getMaxSection() - 1;

        double px = cameraPos.x;
        double py = cameraPos.y;
        double pz = cameraPos.z;

        int currentTick = tickCounter;

        // Collect sections that need a render rebuild; dispatched to main thread below
        List<Long> dirtyPositions = new ArrayList<>();

        for (int sx = pSecX - secRange; sx <= pSecX + secRange; sx++) {
            double minX  = sx << 4;
            double maxX  = minX + 16.0;
            double nearDx = px < minX ? minX - px : (px > maxX ? px - maxX : 0.0);
            double farDx  = px < minX + 8.0 ? maxX - px : px - minX;

            for (int sz = pSecZ - secRange; sz <= pSecZ + secRange; sz++) {
                try {
                    if (!level.getChunkSource().hasChunk(sx, sz)) {
                        continue;
                    }
                } catch (Exception e) {
                    continue; // Chunk source modified concurrently or level closed, skip safely
                }
                double minZ   = sz << 4;
                double maxZ   = minZ + 16.0;
                double nearDz = pz < minZ ? minZ - pz : (pz > maxZ ? pz - maxZ : 0.0);
                double farDz  = pz < minZ + 8.0 ? maxZ - pz : pz - minZ;

                for (int sy = Math.max(minSecY, pSecY - secRange); sy <= Math.min(maxSecY, pSecY + secRange); sy++) {
                    double minY   = sy << 4;
                    double maxY   = minY + 16.0;
                    double nearDy = py < minY ? minY - py : (py > maxY ? py - maxY : 0.0);
                    double farDy  = py < minY + 8.0 ? maxY - py : py - minY;

                    byte gState = classifySection(nearDx, farDx, nearDy, farDy, nearDz, farDz, grassDist);
                    byte fState = classifySection(nearDx, farDx, nearDy, farDy, nearDz, farDz, flowerDist);
                    byte oState = classifySection(nearDx, farDx, nearDy, farDy, nearDz, farDz, otherDist);

                    Long posLong = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
                    SectionState secState = sectionStates.get(posLong);

                    boolean rebuild = false;

                    if (secState == null) {
                        secState = new SectionState();
                        secState.grassState  = gState;
                        secState.flowerState = fState;
                        secState.otherState  = oState;
                        secState.lastRebuildX = px;
                        secState.lastRebuildY = py;
                        secState.lastRebuildZ = pz;
                        secState.lastSeenTick = currentTick;
                        sectionStates.put(posLong, secState);
                        rebuild = true;
                    } else {
                        secState.lastSeenTick = currentTick;

                        if (secState.grassState != gState || secState.flowerState != fState || secState.otherState != oState) {
                            rebuild = true;
                            secState.grassState  = gState;
                            secState.flowerState = fState;
                            secState.otherState  = oState;
                            secState.lastRebuildX = px;
                            secState.lastRebuildY = py;
                            secState.lastRebuildZ = pz;
                        } else if (gState == 0 || fState == 0 || oState == 0) {
                            double rDx = px - secState.lastRebuildX;
                            double rDy = py - secState.lastRebuildY;
                            double rDz = pz - secState.lastRebuildZ;
                            if (rDx * rDx + rDy * rDy + rDz * rDz > 4.0) {
                                rebuild = true;
                                secState.lastRebuildX = px;
                                secState.lastRebuildY = py;
                                secState.lastRebuildZ = pz;
                            }
                        }
                    }

                    if (rebuild) {
                        dirtyPositions.add(posLong);
                    }

                    // Light-Aware Culling: compute section light importance factor
                    if (CullifyMod.cachedLightAware) {
                        try {
                            // Sample brightness at the center block of this section
                            int cx = (sx << 4) + 8;
                            int cy = (sy << 4) + 8;
                            int cz = (sz << 4) + 8;
                            net.minecraft.core.BlockPos centerPos = new net.minecraft.core.BlockPos(cx, cy, cz);
                            int sky   = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, centerPos);
                            int block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, centerPos);
                            // Weight sky light more heavily — a single torch should not fully restore distance
                            float importance = (0.75f * sky + 0.25f * block) / 15.0f;
                            // Clamp to [0.3, 1.0]: fully dark = 30% of configured distance; full sun = 100%
                            float lightFactor = Math.clamp(0.3f + 0.7f * importance, 0.3f, 1.0f);
                            Float existing = CullifyMod.sectionLightFactors.get(posLong);
                            if (existing == null || Math.abs(existing - lightFactor) > 0.04f) {
                                CullifyMod.sectionLightFactors.put(posLong, lightFactor);
                                // Trigger a re-render so the new effective radius takes effect
                                if (!rebuild) dirtyPositions.add(posLong);
                            }
                        } catch (Exception ignored) {
                            // Section may not be fully loaded — skip safely
                        }
                    }

                }
            }
        }

        // Evict stale entries (safe on ConcurrentHashMap)
        if (currentTick % 20 == 0) {
            sectionStates.entrySet().removeIf(entry -> (currentTick - entry.getValue().lastSeenTick) > 20);
            // Also evict light factors for sections no longer tracked
            if (CullifyMod.cachedLightAware) {
                CullifyMod.sectionLightFactors.keySet().retainAll(sectionStates.keySet());
            }
        }

        // Dispatch dirty-marking to the main/render thread — invokeSetSectionDirty is not thread-safe
        if (!dirtyPositions.isEmpty()) {
            Minecraft mcRef = Minecraft.getInstance();
            mcRef.execute(() -> {
                LevelRenderer lr = mcRef.levelRenderer;
                if (lr == null) return;
                com.fluxsyum.cullify.mixin.LevelRendererAccessor acc =
                    (com.fluxsyum.cullify.mixin.LevelRendererAccessor) lr;
                if (acc.getViewArea() == null) return;
                for (long packed : dirtyPositions) {
                    int sx = net.minecraft.core.SectionPos.x(packed);
                    int sy = net.minecraft.core.SectionPos.y(packed);
                    int sz = net.minecraft.core.SectionPos.z(packed);
                    acc.invokeSetSectionDirty(sx, sy, sz, false);
                    CullifyDebugManager.chunkUpdates.increment();
                }
            });
        }
    }

    private static byte classifySection(double nearDx, double farDx, double nearDy, double farDy,
                                        double nearDz, double farDz, double threshold) {
        if (threshold < 0) {
            return 1;
        }

        // Use cached shape to avoid SPEC.get() overhead
        CullifyConfig.CullingShape shape = CullifyMod.cachedShape;

        // Fast early-out: nearest corner is already beyond threshold → fully culled
        if (shape == CullifyConfig.CullingShape.SPHERE) {
            double minDistSq = nearDx * nearDx + nearDy * nearDy + nearDz * nearDz;
            if (minDistSq > threshold * threshold) return 2;
        } else if (shape == CullifyConfig.CullingShape.BOX) {
            double minDist = Math.max(nearDx, Math.max(nearDy, nearDz));
            if (minDist > threshold) return 2;
        } else if (shape == CullifyConfig.CullingShape.SQUARE) {
            double minDist = Math.max(nearDx, nearDz);
            if (minDist > threshold) return 2;
        } else { // CYLINDER, CIRCLE, TRIANGLE, HEXAGON, STAR
            double minDistSq = nearDx * nearDx + nearDz * nearDz;
            if (minDistSq > threshold * threshold) return 2;
        }

        // For BOX: check if entire AABB is inside
        if (shape == CullifyConfig.CullingShape.BOX) {
            double maxDist = Math.max(farDx, Math.max(farDy, farDz));
            if (maxDist <= threshold) {
                return 1;
            }
            return 0;
        }

        // General case: check all corners
        boolean c1 = CullifyMod.isInShape(nearDx, nearDy, nearDz, threshold, shape);
        boolean c2 = CullifyMod.isInShape(nearDx, nearDy, farDz,  threshold, shape);
        boolean c3 = CullifyMod.isInShape(farDx,  nearDy, nearDz, threshold, shape);
        boolean c4 = CullifyMod.isInShape(farDx,  nearDy, farDz,  threshold, shape);

        boolean fullyIn = c1 && c2 && c3 && c4;

        if (shape == CullifyConfig.CullingShape.SPHERE) {
            boolean c5 = CullifyMod.isInShape(nearDx, farDy, nearDz, threshold, shape);
            boolean c6 = CullifyMod.isInShape(nearDx, farDy, farDz,  threshold, shape);
            boolean c7 = CullifyMod.isInShape(farDx,  farDy, nearDz, threshold, shape);
            boolean c8 = CullifyMod.isInShape(farDx,  farDy, farDz,  threshold, shape);
            fullyIn = fullyIn && c5 && c6 && c7 && c8;
        }

        return fullyIn ? (byte) 1 : (byte) 0;
    }
}
