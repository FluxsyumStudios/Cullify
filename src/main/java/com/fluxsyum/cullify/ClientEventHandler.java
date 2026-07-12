package com.fluxsyum.cullify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import com.fluxsyum.cullify.command.CullifyCommands;


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
    private static int lastLodDensity = 100;
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
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            com.fluxsyum.cullify.benchmark.BenchmarkManager.recordFrame();

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && com.fluxsyum.cullify.benchmark.BenchmarkManager.isRunning()) {
                com.fluxsyum.cullify.benchmark.BenchmarkManager.State bState = com.fluxsyum.cullify.benchmark.BenchmarkManager.getState();
                float currentYaw = com.fluxsyum.cullify.benchmark.BenchmarkManager.startYaw;

                if (bState == com.fluxsyum.cullify.benchmark.BenchmarkManager.State.RUNNING_WITHOUT ||
                    bState == com.fluxsyum.cullify.benchmark.BenchmarkManager.State.RUNNING_WITH) {
                    long elapsed = System.currentTimeMillis() - com.fluxsyum.cullify.benchmark.BenchmarkManager.getPhaseStartTimeMillis();
                    long duration = com.fluxsyum.cullify.benchmark.BenchmarkManager.getPhaseDurationMillis();
                    float progress = duration > 0 ? (float) elapsed / duration : 0.0f;
                    progress = Math.min(1.0f, Math.max(0.0f, progress));
                    currentYaw = com.fluxsyum.cullify.benchmark.BenchmarkManager.startYaw + progress * 360.0f;
                }

                mc.player.setYRot(currentYaw);
                mc.player.yRotO = currentYaw;
                mc.player.setXRot(0.0f);
                mc.player.xRotO = 0.0f;
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) {
            CullifyMod.hasPlayer = false;
            initialized = false;
            sectionStates.clear();
            return;
        }

        // Lock position and reset velocity during benchmark to keep player in the same spot
        if (com.fluxsyum.cullify.benchmark.BenchmarkManager.isRunning()) {
            player.setPos(com.fluxsyum.cullify.benchmark.BenchmarkManager.startX,
                           com.fluxsyum.cullify.benchmark.BenchmarkManager.startY,
                           com.fluxsyum.cullify.benchmark.BenchmarkManager.startZ);
            player.setDeltaMovement(0, 0, 0);
            player.xxa = 0.0f;
            player.yya = 0.0f;
            player.zza = 0.0f;
        }

        // Deferred reload check: only reload chunks when no screen is open (game is unpaused)
        if (CullifyMod.reloadRequired && mc.screen == null) {
            CullifyMod.reloadRequired = false;
            if (mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
                mc.execute(() -> {
                    if (mc.levelRenderer != null) {
                        mc.levelRenderer.allChanged();
                    }
                });
            }
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
        // IMPORTANT: hasPlayer must be set TRUE *before* the initialized check,
        // otherwise the early return on first tick leaves hasPlayer=false and culling
        // never activates until the second tick — or at all, if config reads are wrong.
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
            lastLodDensity    = CullifyConfig.LOD_DENSITY.get();
            lastSmartScale    = CullifyConfig.SMART_SCALE.get();
            lastTargetFps     = CullifyConfig.TARGET_FPS.get();
            lastLightAware    = CullifyConfig.LIGHT_AWARE_CULLING.get();

            initialized = true;
            ticksSinceLastCheck = 0;
            debugTickCounter = 0;
            smartScaleTickCounter = 0;
            CullifyMod.updateConfigCache();
            CullifyDebugManager.syncFromConfig();
            // Trigger a full world reload so chunks already loaded when the player joined
            // get re-meshed with culling active. Without this, only newly-loaded chunks get culled.
            CullifyMod.incrementConfigVersion();
            CullifyMod.scheduleWorldReload();
            // Do NOT return early — fall through to the movement tick below.
        }

        // Detect configuration changes to trigger re-renders
        boolean enabled      = CullifyConfig.ENABLED.get();
        boolean cullGrass    = CullifyConfig.CULL_GRASS.get();
        boolean cullFlowers  = CullifyConfig.CULL_FLOWERS.get();
        boolean cullOther    = CullifyConfig.CULL_OTHER_PLANTS.get();
        int     grassDist    = CullifyConfig.GRASS_CULL_DISTANCE.get();
        int     flowerDist   = CullifyConfig.FLOWER_CULL_DISTANCE.get();
        int     otherDist    = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
        CullifyConfig.CullingShape cullingShape = CullifyConfig.CULLING_SHAPE.get();
        int     lodDensity   = CullifyConfig.LOD_DENSITY.get();
        boolean smartScale   = CullifyConfig.SMART_SCALE.get();
        int     targetFps    = CullifyConfig.TARGET_FPS.get();
        boolean lightAware   = CullifyConfig.LIGHT_AWARE_CULLING.get();

        if (enabled          != lastEnabled
                || cullGrass     != lastCullGrass
                || cullFlowers   != lastCullFlowers
                || cullOther     != lastCullOther
                || grassDist     != lastGrassDist
                || flowerDist    != lastFlowerDist
                || otherDist     != lastOtherDist
                || cullingShape  != lastCullingShape
                || lodDensity    != lastLodDensity
                || smartScale    != lastSmartScale
                || targetFps     != lastTargetFps
                || lightAware    != lastLightAware) {

            lastEnabled      = enabled;
            lastCullGrass    = cullGrass;
            lastCullFlowers  = cullFlowers;
            lastCullOther    = cullOther;
            lastGrassDist    = grassDist;
            lastFlowerDist   = flowerDist;
            lastOtherDist    = otherDist;
            lastCullingShape = cullingShape;
            lastLodDensity   = lodDensity;
            lastSmartScale   = smartScale;
            lastTargetFps    = targetFps;
            lastLightAware   = lightAware;

            sectionStates.clear();
            scheduleDebouncedSave();
            CullifyMod.updateConfigCache();
            CullifyMod.incrementConfigVersion();
            CullifyMod.scheduleWorldReload();
        }

        // Smart Scale: adjust the distance multiplier every 20 ticks based on current FPS
        if (CullifyMod.cachedSmartScale) {
            smartScaleTickCounter++;
            if (smartScaleTickCounter >= 20) {
                smartScaleTickCounter = 0;
                int currentFps = com.fluxsyum.cullify.mixin.MinecraftAccessor.getFps();
                int target = CullifyMod.cachedTargetFps;
                float currentFactor = CullifyMod.smartScaleFactor;
                if (currentFps < target) {
                    // FPS below target: shrink distances (min 0.5)
                    CullifyMod.smartScaleFactor = Math.max(0.5f, currentFactor - 0.05f);
                    CullifyMod.updateScaledDistances();
                    CullifyMod.incrementConfigVersion();
                } else if (currentFps >= target + 10 && currentFactor < 1.0f) {
                    // FPS comfortably above target: expand distances back (max 1.0)
                    CullifyMod.smartScaleFactor = Math.min(1.0f, currentFactor + 0.05f);
                    CullifyMod.updateScaledDistances();
                    CullifyMod.incrementConfigVersion();
                }
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
                queueChunkTransitions(mc, level, cameraPos);
                lastPlayerX = cameraPos.x;
                lastPlayerY = cameraPos.y;
                lastPlayerZ = cameraPos.z;
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
                    CullifyMod.incrementConfigVersion();
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
                        String drawCallsStr = CullifyDebugManager.sodiumDetected ? "§dMDI (Sodium)" : "§d" + CullifyDebugManager.lastDrawCalls + " (Vanilla)";
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
        );

        // Register benchmark sub-commands
        CullifyCommands.register(event.getDispatcher());
    }

    private static void queueChunkTransitions(Minecraft mc, ClientLevel level, Vec3 cameraPos) {
        if (!CullifyMod.cachedEnabled) {
            return;
        }

        // Only queue a new task if one is not already running or pending in the thread pool.
        if (classifierPending.compareAndSet(false, true)) {
            ASYNC_CLASSIFIER.submit(() -> {
                try {
                    checkChunkTransitions(mc, level, cameraPos);
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    classifierPending.set(false);
                }
            });
        }
    }

    private static void checkChunkTransitions(Minecraft mc, ClientLevel level, Vec3 cameraPos) {
        double grassDist = CullifyMod.cachedGrassDistScaled;
        double flowerDist = CullifyMod.cachedFlowerDistScaled;
        double otherDist = CullifyMod.cachedOtherDistScaled;
        double maxDist = Math.max(grassDist, Math.max(flowerDist, otherDist));

        int pSecX = ((int) cameraPos.x) >> 4;
        int pSecY = ((int) cameraPos.y) >> 4;
        int pSecZ = ((int) cameraPos.z) >> 4;

        int secRange = ((int) maxDist + 16) >> 4;

        LevelRenderer levelRenderer = mc.levelRenderer;
        if (levelRenderer == null) {
            return;
        }

        com.fluxsyum.cullify.mixin.LevelRendererAccessor accessor = (com.fluxsyum.cullify.mixin.LevelRendererAccessor) levelRenderer;
        if (accessor.getViewArea() == null) {
            return;
        }

        int minSecY = level.getMinSection();
        int maxSecY = level.getMaxSection() - 1;

        double px = cameraPos.x;
        double py = cameraPos.y;
        double pz = cameraPos.z;

        final int activeTick = tickCounter;

        for (int sx = pSecX - secRange; sx <= pSecX + secRange; sx++) {
            double minX = sx << 4;
            double maxX = minX + 16.0;
            double nearDx = px < minX ? minX - px : (px > maxX ? px - maxX : 0.0);
            double farDx = px < minX + 8.0 ? maxX - px : px - minX;

            for (int sz = pSecZ - secRange; sz <= pSecZ + secRange; sz++) {
                if (!level.getChunkSource().hasChunk(sx, sz)) {
                    continue;
                }
                double minZ = sz << 4;
                double maxZ = minZ + 16.0;
                double nearDz = pz < minZ ? minZ - pz : (pz > maxZ ? pz - maxZ : 0.0);
                double farDz = pz < minZ + 8.0 ? maxZ - pz : pz - minZ;

                for (int sy = Math.max(minSecY, pSecY - secRange); sy <= Math.min(maxSecY, pSecY + secRange); sy++) {
                    double minY = sy << 4;
                    double maxY = minY + 16.0;
                    double nearDy = py < minY ? minY - py : (py > maxY ? py - maxY : 0.0);
                    double farDy = py < minY + 8.0 ? maxY - py : py - minY;

                    // Light-Aware Culling: pre-calculate section light factors on background thread
                    if (CullifyMod.cachedLightAware) {
                        long secKey = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
                        if (!CullifyMod.sectionLightFactors.containsKey(secKey)) {
                            // Compute average block light for the section
                            long sumLight = 0;
                            long count = 0;
                            int startX = sx << 4;
                            int startY = sy << 4;
                            int startZ = sz << 4;
                            // Step by 4 blocks to sample 64 points per section
                            for (int bx = 2; bx < 16; bx += 4) {
                                for (int by = 2; by < 16; by += 4) {
                                    for (int bz = 2; bz < 16; bz += 4) {
                                        net.minecraft.core.BlockPos samplePos = new net.minecraft.core.BlockPos(startX + bx, startY + by, startZ + bz);
                                        sumLight += level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, samplePos);
                                        count++;
                                    }
                                }
                            }
                            float avgBlockLight = (float) sumLight / count;
                            // Scale factor: 1.0 in bright areas, down to 0.4 in caves/darkness
                            float lightFactor = 0.4f + 0.6f * (avgBlockLight / 15.0f);
                            CullifyMod.sectionLightFactors.put(secKey, lightFactor);
                        }
                    }

                    byte gState = classifySection(nearDx, farDx, nearDy, farDy, nearDz, farDz, grassDist);
                    byte fState = classifySection(nearDx, farDx, nearDy, farDy, nearDz, farDz, flowerDist);
                    byte oState = classifySection(nearDx, farDx, nearDy, farDy, nearDz, farDz, otherDist);

                    long posLong = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
                    SectionState secState = sectionStates.get(posLong);

                    boolean rebuild = false;

                    if (secState == null) {
                        secState = new SectionState();
                        secState.grassState = gState;
                        secState.flowerState = fState;
                        secState.otherState = oState;
                        secState.lastRebuildX = px;
                        secState.lastRebuildY = py;
                        secState.lastRebuildZ = pz;
                        secState.lastSeenTick = activeTick;
                        sectionStates.put(posLong, secState);
                        rebuild = true;
                    } else {
                        secState.lastSeenTick = activeTick;

                        if (secState.grassState != gState || secState.flowerState != fState || secState.otherState != oState) {
                            rebuild = true;
                            secState.grassState = gState;
                            secState.flowerState = fState;
                            secState.otherState = oState;
                            secState.lastRebuildX = px;
                            secState.lastRebuildY = py;
                            secState.lastRebuildZ = pz;
                        }
                        else if (gState == 0 || fState == 0 || oState == 0) {
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
                        final int finalSx = sx;
                        final int finalSy = sy;
                        final int finalSz = sz;
                        // LevelRenderer operations must run on main thread
                        mc.execute(() -> {
                            if (mc.levelRenderer != null) {
                                accessor.invokeSetSectionDirty(finalSx, finalSy, finalSz, false);
                            }
                        });
                        CullifyDebugManager.chunkUpdates.increment();
                    }
                }
            }
        }

        if (activeTick % 20 == 0) {
            sectionStates.entrySet().removeIf(entry -> entry.getValue().lastSeenTick != activeTick);
            // Clear light factor cache periodically to adapt to torch placement/destruction
            if (activeTick % 100 == 0) {
                CullifyMod.sectionLightFactors.clear();
            }
        }
    }

    private static byte classifySection(double nearDx, double farDx, double nearDy, double farDy, double nearDz, double farDz, double threshold) {
        if (threshold < 0) {
            return 1;
        }

        CullifyConfig.CullingShape shape = CullifyMod.cachedShape;

        // Fast culling check (if the minimum distance is greater than the threshold, it is outside)
        if (shape == CullifyConfig.CullingShape.SPHERE) {
            double minDistSq = nearDx * nearDx + nearDy * nearDy + nearDz * nearDz;
            if (minDistSq > threshold * threshold) return 2;
            
            // Farthest corner check for Sphere
            double maxDistSq = farDx * farDx + farDy * farDy + farDz * farDz;
            if (maxDistSq <= threshold * threshold) return 1;
            return 0;
        } else if (shape == CullifyConfig.CullingShape.BOX) {
            double minDist = Math.max(nearDx, Math.max(nearDy, nearDz));
            if (minDist > threshold) return 2;
            
            // Farthest corner check for Box
            double maxDist = Math.max(farDx, Math.max(farDy, farDz));
            if (maxDist <= threshold) return 1;
            return 0;
        } else if (shape == CullifyConfig.CullingShape.CYLINDER || shape == CullifyConfig.CullingShape.CIRCLE) {
            double minDistSq = nearDx * nearDx + nearDz * nearDz;
            if (minDistSq > threshold * threshold) return 2;
            
            // Farthest corner check in XZ plane
            double maxDistSq = farDx * farDx + farDz * farDz;
            if (maxDistSq <= threshold * threshold) return 1;
            return 0;
        } else if (shape == CullifyConfig.CullingShape.SQUARE) {
            double minDist = Math.max(nearDx, nearDz);
            if (minDist > threshold) return 2;
            
            // Farthest corner check in XZ plane
            double maxDist = Math.max(farDx, farDz);
            if (maxDist <= threshold) return 1;
            return 0;
        } else { // TRIANGLE, HEXAGON, STAR
            double minDistSq = nearDx * nearDx + nearDz * nearDz;
            if (minDistSq > threshold * threshold) return 2;
        }

        // Check if the corners are contained in the shape (sufficient for 2D convex shapes)
        boolean c1 = CullifyMod.isInShape(nearDx, nearDy, nearDz, threshold, shape);
        boolean c2 = CullifyMod.isInShape(nearDx, nearDy, farDz, threshold, shape);
        boolean c3 = CullifyMod.isInShape(farDx, nearDy, nearDz, threshold, shape);
        boolean c4 = CullifyMod.isInShape(farDx, nearDy, farDz, threshold, shape);

        boolean fullyIn = c1 && c2 && c3 && c4;

        if (fullyIn) {
            return 1;
        }

        return 0;
    }
}
