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

@EventBusSubscriber(modid = CullifyMod.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {
    private static double lastPlayerX = 0;
    private static double lastPlayerY = 0;
    private static double lastPlayerZ = 0;
    private static boolean initialized = false;
    private static int ticksSinceLastCheck = 0;
    private static int debugTickCounter = 0;

    private static boolean lastEnabled = true;
    private static boolean lastCullGrass = true;
    private static boolean lastCullFlowers = true;
    private static boolean lastCullOther = true;
    private static int lastGrassDist = 48;
    private static int lastFlowerDist = 48;
    private static int lastOtherDist = 32;
    private static CullifyConfig.CullingShape lastCullingShape = CullifyConfig.CullingShape.SPHERE;

    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<SectionState> sectionStates = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static int tickCounter = 0;

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
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null && mc.gameRenderer.getMainCamera().isInitialized()) {
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
            lastEnabled = CullifyConfig.ENABLED.get();
            lastCullGrass = CullifyConfig.CULL_GRASS.get();
            lastCullFlowers = CullifyConfig.CULL_FLOWERS.get();
            lastCullOther = CullifyConfig.CULL_OTHER_PLANTS.get();
            lastGrassDist = CullifyConfig.GRASS_CULL_DISTANCE.get();
            lastFlowerDist = CullifyConfig.FLOWER_CULL_DISTANCE.get();
            lastOtherDist = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
            lastCullingShape = CullifyConfig.CULLING_SHAPE.get();
            initialized = true;
            ticksSinceLastCheck = 0;
            debugTickCounter = 0;
            CullifyDebugManager.syncFromConfig();
            return;
        }

        // Detect configuration changes to trigger re-renders
        boolean enabled = CullifyConfig.ENABLED.get();
        boolean cullGrass = CullifyConfig.CULL_GRASS.get();
        boolean cullFlowers = CullifyConfig.CULL_FLOWERS.get();
        boolean cullOther = CullifyConfig.CULL_OTHER_PLANTS.get();
        int grassDist = CullifyConfig.GRASS_CULL_DISTANCE.get();
        int flowerDist = CullifyConfig.FLOWER_CULL_DISTANCE.get();
        int otherDist = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();
        CullifyConfig.CullingShape cullingShape = CullifyConfig.CULLING_SHAPE.get();

        if (enabled != lastEnabled 
                || cullGrass != lastCullGrass 
                || cullFlowers != lastCullFlowers 
                || cullOther != lastCullOther 
                || grassDist != lastGrassDist 
                || flowerDist != lastFlowerDist 
                || otherDist != lastOtherDist
                || cullingShape != lastCullingShape) {
            lastEnabled = enabled;
            lastCullGrass = cullGrass;
            lastCullFlowers = cullFlowers;
            lastCullOther = cullOther;
            lastGrassDist = grassDist;
            lastFlowerDist = flowerDist;
            lastOtherDist = otherDist;
            lastCullingShape = cullingShape;
            sectionStates.clear();
            CullifyConfig.SPEC.save();
            CullifyMod.incrementConfigVersion();
            CullifyMod.scheduleWorldReload();
        }

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
                checkChunkTransitions(mc, level, cameraPos);
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
                    CullifyConfig.SPEC.save();
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
                        CullifyConfig.SPEC.save();
                    }
                    Minecraft mc = Minecraft.getInstance();
                    LocalPlayer player = mc.player;
                    if (player != null) {
                        send(player, "§e[Cullify]§r Debug level: §a" + CullifyDebugManager.debugLevel.name());
                    }
                    return 1;
                }))
        );
    }

    private static void checkChunkTransitions(Minecraft mc, ClientLevel level, Vec3 cameraPos) {
        if (!CullifyConfig.ENABLED.get()) {
            return;
        }

        double grassDist = CullifyConfig.GRASS_CULL_DISTANCE.get();
        double flowerDist = CullifyConfig.FLOWER_CULL_DISTANCE.get();
        double otherDist = CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get();

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

        for (int sx = pSecX - secRange; sx <= pSecX + secRange; sx++) {
            double minX = sx << 4;
            double maxX = minX + 16.0;
            double nearDx = px < minX ? minX - px : (px > maxX ? px - maxX : 0.0);
            double farDx = px < minX + 8.0 ? maxX - px : px - minX;
            double dxSqNear = nearDx * nearDx;
            double dxSqFar = farDx * farDx;

            for (int sz = pSecZ - secRange; sz <= pSecZ + secRange; sz++) {
                if (!level.getChunkSource().hasChunk(sx, sz)) {
                    continue;
                }
                double minZ = sz << 4;
                double maxZ = minZ + 16.0;
                double nearDz = pz < minZ ? minZ - pz : (pz > maxZ ? pz - maxZ : 0.0);
                double farDz = pz < minZ + 8.0 ? maxZ - pz : pz - minZ;
                double dzSqNear = nearDz * nearDz;
                double dzSqFar = farDz * farDz;

                for (int sy = Math.max(minSecY, pSecY - secRange); sy <= Math.min(maxSecY, pSecY + secRange); sy++) {
                    double minY = sy << 4;
                    double maxY = minY + 16.0;
                    double nearDy = py < minY ? minY - py : (py > maxY ? py - maxY : 0.0);
                    double farDy = py < minY + 8.0 ? maxY - py : py - minY;

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
                        secState.lastSeenTick = tickCounter;
                        sectionStates.put(posLong, secState);
                        rebuild = true;
                    } else {
                        secState.lastSeenTick = tickCounter;

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
                        accessor.invokeSetSectionDirty(sx, sy, sz, false);
                        CullifyDebugManager.chunkUpdates.increment();
                    }
                }
            }
        }

        if (tickCounter % 20 == 0) {
            sectionStates.long2ObjectEntrySet().removeIf(entry -> entry.getValue().lastSeenTick != tickCounter);
        }
    }

    private static byte classifySection(double nearDx, double farDx, double nearDy, double farDy, double nearDz, double farDz, double threshold) {
        if (threshold < 0) {
            return 1;
        }

        CullifyConfig.CullingShape shape = CullifyConfig.CULLING_SHAPE.get();

        // Fast culling check (if the minimum distance is greater than the threshold, it is outside)
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

        // For box shape, check if the entire AABB is contained within the shape
        if (shape == CullifyConfig.CullingShape.BOX) {
            double maxDist = Math.max(farDx, Math.max(farDy, farDz));
            if (maxDist <= threshold) {
                return 1;
            }
            return 0;
        }

        // Check if the corners are contained in the shape (sufficient for 2D convex shapes)
        boolean c1 = CullifyMod.isInShape(nearDx, nearDy, nearDz, threshold, shape);
        boolean c2 = CullifyMod.isInShape(nearDx, nearDy, farDz, threshold, shape);
        boolean c3 = CullifyMod.isInShape(farDx, nearDy, nearDz, threshold, shape);
        boolean c4 = CullifyMod.isInShape(farDx, nearDy, farDz, threshold, shape);
        
        boolean fullyIn = c1 && c2 && c3 && c4;
        
        if (shape == CullifyConfig.CullingShape.SPHERE) {
            // For sphere, also validate the upper Y bound of the block section
            boolean c5 = CullifyMod.isInShape(nearDx, farDy, nearDz, threshold, shape);
            boolean c6 = CullifyMod.isInShape(nearDx, farDy, farDz, threshold, shape);
            boolean c7 = CullifyMod.isInShape(farDx, farDy, nearDz, threshold, shape);
            boolean c8 = CullifyMod.isInShape(farDx, farDy, farDz, threshold, shape);
            fullyIn = fullyIn && c5 && c6 && c7 && c8;
        }

        if (fullyIn) {
            return 1;
        }

        return 0;
    }
}

