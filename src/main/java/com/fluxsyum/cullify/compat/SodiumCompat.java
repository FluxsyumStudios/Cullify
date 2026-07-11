package com.fluxsyum.cullify.compat;

import com.fluxsyum.cullify.CullifyConfig;
import com.fluxsyum.cullify.CullifyMod;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers Cullify's options natively into the Sodium 0.8.x config UI.
 * Registered via the "sodium:config_api_user" property in neoforge.mods.toml.
 */
public class SodiumCompat implements ConfigEntryPoint {

    private static final StorageEventHandler STORAGE_HANDLER = com.fluxsyum.cullify.ClientEventHandler::scheduleDebouncedSave;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        // Use registerOwnModOptions() so Sodium reads our mod metadata automatically
        ModOptionsBuilder modOptions = builder.registerOwnModOptions();
        modOptions.setNonTintedIcon(CullifyMod.LOGO);

        OptionPageBuilder page = createCullifyPage(builder);
        modOptions.addPage(page);
    }

    @Override
    public void registerConfigEarly(ConfigBuilder builder) {
        // Nothing needed here
    }

    private OptionPageBuilder createCullifyPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.translatable("cullify.options.page"));

        OptionGroupBuilder mainGroup = builder.createOptionGroup();

        // 1. Global Enable Toggle
        mainGroup.addOption(builder.createBooleanOption(Identifier.fromNamespaceAndPath("cullify", "enabled"))
                .setName(Component.translatable("cullify.options.enabled"))
                .setTooltip(Component.translatable("cullify.options.enabled.tooltip"))
                .setDefaultValue(true)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.ENABLED.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.ENABLED.get()
                )
        );

        // 2. Culling Shape
        mainGroup.addOption(builder.createEnumOption(Identifier.fromNamespaceAndPath("cullify", "culling_shape"), CullifyConfig.CullingShape.class)
                .setName(Component.translatable("cullify.options.culling_shape"))
                .setTooltip(Component.translatable("cullify.options.culling_shape.tooltip"))
                .setDefaultValue(CullifyConfig.CullingShape.SPHERE)
                .setStorageHandler(STORAGE_HANDLER)
                .setElementNameProvider(v -> Component.translatable("cullify.options.culling_shape." + v.name().toLowerCase(java.util.Locale.ROOT)))
                .setBinding(
                        v -> {
                            CullifyConfig.CULLING_SHAPE.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.CULLING_SHAPE.get()
                )
        );

        // 3. LOD Density Slider
        mainGroup.addOption(builder.createIntegerOption(Identifier.fromNamespaceAndPath("cullify", "lod_density"))
                .setName(Component.translatable("cullify.menu.option.lod"))
                .setTooltip(Component.translatable("cullify.menu.lod_density.tooltip"))
                .setRange(0, 100, 5)
                .setValueFormatter(v -> v >= 100 ? Component.translatable("gui.none") : Component.literal(v + "%"))
                .setDefaultValue(100)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.LOD_DENSITY.set(v);
                            CullifyMod.updateConfigCache();
                            CullifyMod.incrementConfigVersion();
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.LOD_DENSITY.get()
                )
        );

        // 4. Smart Scale Toggle
        mainGroup.addOption(builder.createBooleanOption(Identifier.fromNamespaceAndPath("cullify", "smart_scale"))
                .setName(Component.translatable("cullify.menu.option.smart_scale"))
                .setTooltip(Component.translatable("cullify.menu.option.smart_scale.tooltip"))
                .setDefaultValue(false)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.SMART_SCALE.set(v);
                            CullifyMod.updateConfigCache();
                        },
                        () -> CullifyConfig.SMART_SCALE.get()
                )
        );

        // 5. Target FPS Slider
        mainGroup.addOption(builder.createIntegerOption(Identifier.fromNamespaceAndPath("cullify", "target_fps"))
                .setName(Component.translatable("cullify.menu.option.target_fps"))
                .setTooltip(Component.translatable("cullify.menu.option.target_fps.tooltip"))
                .setRange(30, 240, 10)
                .setValueFormatter(v -> Component.literal(v + " FPS"))
                .setDefaultValue(60)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.TARGET_FPS.set(v);
                            CullifyMod.updateConfigCache();
                        },
                        () -> CullifyConfig.TARGET_FPS.get()
                )
        );

        // 6. Light-Aware Culling Toggle
        mainGroup.addOption(builder.createBooleanOption(Identifier.fromNamespaceAndPath("cullify", "light_aware_culling"))
                .setName(Component.translatable("cullify.menu.option.light_aware"))
                .setTooltip(Component.translatable("cullify.menu.option.light_aware.tooltip"))
                .setDefaultValue(false)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.LIGHT_AWARE_CULLING.set(v);
                            CullifyMod.updateConfigCache();
                            CullifyMod.sectionLightFactors.clear();
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.LIGHT_AWARE_CULLING.get()
                )
        );

        page.addOptionGroup(mainGroup);


        // Grass Group
        OptionGroupBuilder grassGroup = builder.createOptionGroup();

        grassGroup.addOption(builder.createBooleanOption(Identifier.fromNamespaceAndPath("cullify", "cull_grass"))
                .setName(Component.translatable("cullify.options.cull_grass"))
                .setTooltip(Component.translatable("cullify.options.cull_grass.tooltip"))
                .setDefaultValue(true)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.CULL_GRASS.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.CULL_GRASS.get()
                )
        );

        grassGroup.addOption(builder.createIntegerOption(Identifier.fromNamespaceAndPath("cullify", "grass_distance"))
                .setName(Component.translatable("cullify.options.grass_distance"))
                .setTooltip(Component.translatable("cullify.options.grass_distance.tooltip"))
                .setRange(16, 256, 8)
                .setValueFormatter(v -> Component.translatable("cullify.options.blocks", v))
                .setDefaultValue(48)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.GRASS_CULL_DISTANCE.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.GRASS_CULL_DISTANCE.get()
                )
        );

        page.addOptionGroup(grassGroup);

        // Flowers Group
        OptionGroupBuilder flowerGroup = builder.createOptionGroup();

        flowerGroup.addOption(builder.createBooleanOption(Identifier.fromNamespaceAndPath("cullify", "cull_flowers"))
                .setName(Component.translatable("cullify.options.cull_flowers"))
                .setTooltip(Component.translatable("cullify.options.cull_flowers.tooltip"))
                .setDefaultValue(true)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.CULL_FLOWERS.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.CULL_FLOWERS.get()
                )
        );

        flowerGroup.addOption(builder.createIntegerOption(Identifier.fromNamespaceAndPath("cullify", "flower_distance"))
                .setName(Component.translatable("cullify.options.flower_distance"))
                .setTooltip(Component.translatable("cullify.options.flower_distance.tooltip"))
                .setRange(16, 256, 8)
                .setValueFormatter(v -> Component.translatable("cullify.options.blocks", v))
                .setDefaultValue(48)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.FLOWER_CULL_DISTANCE.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.FLOWER_CULL_DISTANCE.get()
                )
        );

        page.addOptionGroup(flowerGroup);

        // Other Plants Group
        OptionGroupBuilder otherGroup = builder.createOptionGroup();

        otherGroup.addOption(builder.createBooleanOption(Identifier.fromNamespaceAndPath("cullify", "cull_other_plants"))
                .setName(Component.translatable("cullify.options.cull_other_plants"))
                .setTooltip(Component.translatable("cullify.options.cull_other_plants.tooltip"))
                .setDefaultValue(true)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.CULL_OTHER_PLANTS.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.CULL_OTHER_PLANTS.get()
                )
        );

        otherGroup.addOption(builder.createIntegerOption(Identifier.fromNamespaceAndPath("cullify", "other_distance"))
                .setName(Component.translatable("cullify.options.other_distance"))
                .setTooltip(Component.translatable("cullify.options.other_distance.tooltip"))
                .setRange(16, 256, 8)
                .setValueFormatter(v -> Component.translatable("cullify.options.blocks", v))
                .setDefaultValue(32)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.OTHER_PLANT_CULL_DISTANCE.set(v);
                            CullifyMod.scheduleWorldReload();
                        },
                        () -> CullifyConfig.OTHER_PLANT_CULL_DISTANCE.get()
                )
        );

        page.addOptionGroup(otherGroup);

        // Debug Group
        OptionGroupBuilder debugGroup = builder.createOptionGroup();

        debugGroup.addOption(builder.createBooleanOption(Identifier.fromNamespaceAndPath("cullify", "debug_mode"))
                .setName(Component.translatable("cullify.options.debug_mode"))
                .setTooltip(Component.translatable("cullify.options.debug_mode.tooltip"))
                .setDefaultValue(false)
                .setStorageHandler(STORAGE_HANDLER)
                .setBinding(
                        v -> {
                            CullifyConfig.DEBUG_MODE.set(v);
                        },
                        () -> CullifyConfig.DEBUG_MODE.get()
                )
        );

        page.addOptionGroup(debugGroup);

        return page;
    }
}

