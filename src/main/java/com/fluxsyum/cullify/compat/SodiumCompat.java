package com.fluxsyum.cullify.compat;

import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyMod;
import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.*;
import me.jellysquid.mods.sodium.client.gui.options.control.*;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;
import toni.sodiumoptionsapi.api.OptionGUIConstruction;

import java.util.ArrayList;
import java.util.List;

public class SodiumCompat {

    public static void registerOptions() {
        OptionGUIConstruction.EVENT.register(pages -> {
            List<OptionGroup> groups = new ArrayList<>();
            OptionStorage<Void> storage = DummyStorage.INSTANCE;

            OptionGroup.Builder builder = OptionGroup.createBuilder();

            // 1. Global Culling Enable Toggle
            builder.add(OptionImpl.createBuilder(Boolean.class, storage)
                    .setName(Component.translatable("cullify.options.enabled"))
                    .setTooltip(Component.translatable("cullify.options.enabled.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.ENABLED.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.ENABLED.get()
                    )
                    .build());

            // 2. Culling Shape Option
            builder.add(OptionImpl.createBuilder(CullifyConfig.CullingShape.class, storage)
                    .setName(Component.translatable("cullify.options.culling_shape"))
                    .setTooltip(Component.translatable("cullify.options.culling_shape.tooltip"))
                    .setControl(opt -> new CyclingControl<>(opt, CullifyConfig.CullingShape.class, new Component[]{
                            Component.translatable("cullify.options.culling_shape.sphere"),
                            Component.translatable("cullify.options.culling_shape.cylinder"),
                            Component.translatable("cullify.options.culling_shape.box"),
                            Component.translatable("cullify.options.culling_shape.triangle"),
                            Component.translatable("cullify.options.culling_shape.hexagon"),
                            Component.translatable("cullify.options.culling_shape.star"),
                            Component.translatable("cullify.options.culling_shape.square"),
                            Component.translatable("cullify.options.culling_shape.circle")
                    }))
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.CULLING_SHAPE.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.CULLING_SHAPE.get()
                    )
                    .build());

            // 3. LOD Density Option
            builder.add(OptionImpl.createBuilder(Integer.class, storage)
                    .setName(Component.translatable("cullify.menu.option.lod"))
                    .setTooltip(Component.translatable("cullify.menu.lod_density.tooltip"))
                    .setControl(opt -> new SliderControl(opt, 0, 100, 5, v -> v >= 100 ? Component.translatable("gui.none") : Component.literal(v + "%")))
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.LOD_DENSITY.set(v);
                                CullifyMod.updateConfigCache();
                                CullifyMod.incrementConfigVersion();
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.LOD_DENSITY.get()
                    )
                    .build());

            // 4. Smart Scale Toggle
            builder.add(OptionImpl.createBuilder(Boolean.class, storage)
                    .setName(Component.translatable("cullify.menu.option.smart_scale"))
                    .setTooltip(Component.translatable("cullify.menu.option.smart_scale.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.SMART_SCALE.set(v);
                                CullifyMod.updateConfigCache();
                            },
                            s -> CullifyConfig.SMART_SCALE.get()
                    )
                    .build());

            // 5. Target FPS Option
            builder.add(OptionImpl.createBuilder(Integer.class, storage)
                    .setName(Component.translatable("cullify.menu.option.target_fps"))
                    .setTooltip(Component.translatable("cullify.menu.option.target_fps.tooltip"))
                    .setControl(opt -> new SliderControl(opt, 30, 240, 10, v -> Component.literal(v + " FPS")))
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.TARGET_FPS.set(v);
                                CullifyMod.updateConfigCache();
                            },
                            s -> CullifyConfig.TARGET_FPS.get()
                    )
                    .build());

            // 6. Light-Aware Culling Toggle
            builder.add(OptionImpl.createBuilder(Boolean.class, storage)
                    .setName(Component.translatable("cullify.menu.option.light_aware"))
                    .setTooltip(Component.translatable("cullify.menu.option.light_aware.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.LIGHT_AWARE_CULLING.set(v);
                                CullifyMod.updateConfigCache();
                                CullifyMod.sectionLightFactors.clear();
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.LIGHT_AWARE_CULLING.get()
                    )
                    .build());

            // 7. Cull Grass Toggle
            builder.add(OptionImpl.createBuilder(Boolean.class, storage)
                    .setName(Component.translatable("cullify.options.cull_grass"))
                    .setTooltip(Component.translatable("cullify.options.cull_grass.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.CULL_GRASS.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.CULL_GRASS.get()
                    )
                    .build());

            // 8. Grass Cull Distance Option
            builder.add(OptionImpl.createBuilder(Integer.class, storage)
                    .setName(Component.translatable("cullify.options.grass_distance"))
                    .setTooltip(Component.translatable("cullify.options.grass_distance.tooltip"))
                    .setControl(opt -> new SliderControl(opt, 16, 256, 8, value -> Component.translatable("cullify.options.blocks", value)))
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.GRASS_CULL_DISTANCE.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.GRASS_CULL_DISTANCE.get()
                    )
                    .build());

            // 9. Cull Flowers Toggle
            builder.add(OptionImpl.createBuilder(Boolean.class, storage)
                    .setName(Component.translatable("cullify.options.cull_flowers"))
                    .setTooltip(Component.translatable("cullify.options.cull_flowers.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.CULL_FLOWERS.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.CULL_FLOWERS.get()
                    )
                    .build());

            // 10. Flower Cull Distance Option
            builder.add(OptionImpl.createBuilder(Integer.class, storage)
                    .setName(Component.translatable("cullify.options.flower_distance"))
                    .setTooltip(Component.translatable("cullify.options.flower_distance.tooltip"))
                    .setControl(opt -> new SliderControl(opt, 16, 256, 8, value -> Component.translatable("cullify.options.blocks", value)))
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.FLOWER_CULL_DISTANCE.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.FLOWER_CULL_DISTANCE.get()
                    )
                    .build());

            // 11. Cull Other/Algae Toggle
            builder.add(OptionImpl.createBuilder(Boolean.class, storage)
                    .setName(Component.translatable("cullify.options.cull_other_plants"))
                    .setTooltip(Component.translatable("cullify.options.cull_other_plants.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.CULL_OTHER_PLANTS.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.CULL_OTHER_PLANTS.get()
                    )
                    .build());

            // 12. Other Plants Cull Distance Option
            builder.add(OptionImpl.createBuilder(Integer.class, storage)
                    .setName(Component.translatable("cullify.options.other_distance"))
                    .setTooltip(Component.translatable("cullify.options.other_distance.tooltip"))
                    .setControl(opt -> new SliderControl(opt, 16, 256, 8, value -> Component.translatable("cullify.options.blocks", value)))
                    .setBinding(
                            (s, v) -> {
                                CullifyConfig.OTHER_PLANT_CULL_DISTANCE.set(v);
                                CullifyMod.scheduleWorldReload();
                            },
                            s -> CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get()
                    )
                    .build());

            // 13. Debug Mode Option
            builder.add(OptionImpl.createBuilder(Boolean.class, storage)
                    .setName(Component.translatable("cullify.options.debug_mode"))
                    .setTooltip(Component.translatable("cullify.options.debug_mode.tooltip"))
                    .setControl(TickBoxControl::new)
                    .setBinding(
                            (s, v) -> CullifyConfig.DEBUG_MODE.set(v),
                            s -> CullifyConfig.DEBUG_MODE.get()
                    )
                    .build());

            groups.add(builder.build());

            pages.add(new OptionPage(Component.literal("Cullify"), ImmutableList.copyOf(groups)));
        });
    }

    private static class DummyStorage implements OptionStorage<Void> {
        public static final DummyStorage INSTANCE = new DummyStorage();

        @Override
        public Void getData() {
            return null;
        }

        @Override
        public void save() {
            CullifyConfig.SPEC.save();
        }
    }
}
