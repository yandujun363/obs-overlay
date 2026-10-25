package me.zziger.obsoverlay;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import me.zziger.obsoverlay.component.IOverlayComponent;
import me.zziger.obsoverlay.component.OverlayComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cloth Config based settings screen.
 *
 * <p>Reachable from {@code Options > Video Settings > OBS Overlay settings} (see
 * {@link me.zziger.obsoverlay.mixin.VideoSettingsScreenMixin}) and from ModMenu
 * ({@link ModMenuIntegration}).</p>
 *
 * <p>The screen is split into three tabs so the three independent switches of the mod do not get
 * mixed up:</p>
 * <ol>
 *     <li><b>HUD elements</b> - per-component "draw it on my screen, keep it out of the capture",
 *     plus the auto-hide sub-list ("do not draw it at all while a screen is open").</li>
 *     <li><b>Screens &amp; menus</b> - whole screens (inventory, pause menu, command block, any
 *     container by menu id) hidden the same way.</li>
 *     <li><b>Advanced &amp; diagnostics</b> - test icon, premultiplied alpha, config file path.</li>
 * </ol>
 *
 * <p>Toggles apply immediately (the component objects hold the values the renderer reads); the JSON
 * file is written when the screen is saved. Editing the file by hand requires a restart, because the
 * config is read once during client initialisation.</p>
 */
public final class OBSOverlayConfigScreen {
    private OBSOverlayConfigScreen() {
    }

    public static Screen create(Screen parent) {
        OBSOverlayConfig config = OBSOverlayConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("obs_overlay.config.title"))
                .setDoesConfirmSave(false)
                .setShouldListSmoothScroll(false)
                .setShouldTabsSmoothScroll(false);
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        addHudTab(builder, entryBuilder);
        addScreensTab(builder, entryBuilder, config);
        addAdvancedTab(builder, entryBuilder, config);

        builder.setSavingRunnable(config::save);
        return builder.build();
    }

    /** Tab 1: HUD components + the "hide while any interface is open" sub-list. */
    private static void addHudTab(ConfigBuilder builder, ConfigEntryBuilder entryBuilder) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("obs_overlay.config.category.hud"));
        category.addEntry(entryBuilder
                .startTextDescription(Component.translatable("obs_overlay.config.hud.intro"))
                .build());

        Map<String, BooleanListEntry> overlayEntries = new HashMap<>();
        SubCategoryBuilder overlaySub = entryBuilder
                .startSubCategory(Component.translatable("obs_overlay.config.components_to_overlay"))
                .setExpanded(true)
                .setTooltip(Component.translatable("obs_overlay.config.components_to_overlay.tooltip"));

        for (IOverlayComponent component : OverlayComponentRegistry.components) {
            Component name = Component.translatable("obs_overlay.component." + component.getId());
            BooleanListEntry entry = entryBuilder
                    .startBooleanToggle(name, component.isOverlayEnabled())
                    .setTooltip(
                            Component.translatable("obs_overlay.component." + component.getId() + ".tooltip"),
                            Component.translatable("obs_overlay.config.overlay_hint"),
                            defaultLabel(component.isOverlayEnabledDefault()))
                    .setDefaultValue(component.isOverlayEnabledDefault())
                    .setSaveConsumer(component::setOverlayEnabled)
                    .build();
            overlayEntries.put(component.getId(), entry);
            overlaySub.add(entry);
        }
        category.addEntry(overlaySub.build());

        SubCategoryBuilder autoHideSub = entryBuilder
                .startSubCategory(Component.translatable("obs_overlay.config.auto_hide_components"))
                .setExpanded(false)
                .setTooltip(Component.translatable("obs_overlay.config.auto_hide_components.tooltip"));

        for (IOverlayComponent component : OverlayComponentRegistry.components) {
            if (!component.canAutoHide()) {
                continue;
            }
            BooleanListEntry overlayEntry = overlayEntries.get(component.getId());
            if (overlayEntry == null) {
                continue;
            }
            autoHideSub.add(entryBuilder
                    .startBooleanToggle(Component.translatable("obs_overlay.component." + component.getId()),
                            component.isAutoHideEnabled())
                    .setDefaultValue(true)
                    .setRequirement(Requirement.isTrue(overlayEntry))
                    .setTooltipSupplier(() -> {
                        Component mainTooltip = Component.translatable(
                                "obs_overlay.component." + component.getId() + ".tooltip");
                        Component autoHideHint = Component.translatable("obs_overlay.config.auto_hide_hint");
                        if (overlayEntry.getValue()) {
                            return Optional.of(new Component[]{mainTooltip, autoHideHint});
                        }
                        Component optionName = Component.translatable("obs_overlay.component." + component.getId());
                        return Optional.of(new Component[]{
                                mainTooltip,
                                autoHideHint,
                                Component.translatable("obs_overlay.config.requires_overlay_enabled", optionName)
                                        .withStyle(ChatFormatting.RED)
                        });
                    })
                    .setSaveConsumer(component::setAutoHideEnabled)
                    .build());
        }
        category.addEntry(autoHideSub.build());
    }

    /** Tab 2: whole screens (inventory / pause menu / command block + custom container menu ids). */
    private static void addScreensTab(ConfigBuilder builder, ConfigEntryBuilder entryBuilder,
                                      OBSOverlayConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("obs_overlay.config.category.screens"));
        category.addEntry(entryBuilder
                .startTextDescription(Component.translatable("obs_overlay.config.screens.intro"))
                .build());

        BooleanListEntry hideAllScreens = entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.hide_all_screens"),
                        config.hideAllScreens)
                .setTooltip(
                        Component.translatable("obs_overlay.config.hide_all_screens.tooltip"),
                        Component.translatable("obs_overlay.config.screen_hint"))
                .setDefaultValue(false)
                .setSaveConsumer(state -> config.hideAllScreens = state)
                .build();
        category.addEntry(hideAllScreens);

        SubCategoryBuilder knownScreens = entryBuilder
                .startSubCategory(Component.translatable("obs_overlay.config.screens_to_overlay"))
                .setExpanded(true)
                .setTooltip(Component.translatable("obs_overlay.config.screens_to_overlay.tooltip"));
        OverlayComponentRegistry.hideableScreens.forEach((screenId, screenClass) -> knownScreens.add(entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.screen." + screenId),
                        config.overlayScreensList.getOrDefault(screenId, false))
                .setTooltip(Component.translatable("obs_overlay.config.screen_hint"))
                .setDefaultValue(false)
                .setDisplayRequirement(Requirement.isFalse(hideAllScreens))
                .setSaveConsumer(state -> config.overlayScreensList.put(screenId, state))
                .build()));
        category.addEntry(knownScreens.build());

        SubCategoryBuilder customScreens = entryBuilder
                .startSubCategory(Component.translatable("obs_overlay.config.custom_screens"))
                .setExpanded(false)
                .setTooltip(Component.translatable("obs_overlay.config.custom_handled_screens.tooltip"));

        BooleanListEntry customHandledScreens = entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.enable_custom_handled_screens"),
                        config.overlayHandledScreensEnabled)
                .setTooltip(Component.translatable("obs_overlay.config.enable_custom_handled_screens.tooltip"))
                .setDefaultValue(false)
                .setDisplayRequirement(Requirement.isFalse(hideAllScreens))
                .setSaveConsumer(state -> config.overlayHandledScreensEnabled = state)
                .build();
        customScreens.add(customHandledScreens);

        customScreens.add(entryBuilder
                .startStrList(Component.translatable("obs_overlay.config.custom_handled_screens"),
                        new ArrayList<>(config.overlayHandledScreensList))
                .setTooltip(Component.translatable("obs_overlay.config.custom_handled_screens.tooltip"))
                .setSaveConsumer(list -> config.overlayHandledScreensList = new HashSet<>(list))
                .setDisplayRequirement(Requirement.all(Requirement.isFalse(hideAllScreens),
                        Requirement.isTrue(customHandledScreens)))
                .setErrorSupplier(list -> {
                    for (String id : list) {
                        if (id == null || id.isBlank()) {
                            continue;
                        }
                        try {
                            Identifier identifier = Identifier.parse(id.trim());
                            if (!BuiltInRegistries.MENU.containsKey(identifier)) {
                                return Optional.of(Component.translatable(
                                        "obs_overlay.config.screen_doesnt_exist", id));
                            }
                        } catch (Exception exception) {
                            return Optional.of(Component.translatable(
                                    "obs_overlay.config.screen_doesnt_exist", id));
                        }
                    }
                    return Optional.empty();
                })
                .build());

        // A copy: cloth-config hands this list to an editable widget.
        List<String> existingScreens = new ArrayList<>(
                BuiltInRegistries.MENU.keySet().stream().map(Identifier::toString).toList());
        customScreens.add(entryBuilder
                .startStrList(Component.translatable("obs_overlay.config.existing_handled_screens"), existingScreens)
                .setTooltip(Component.translatable("obs_overlay.config.existing_handled_screens.tooltip"))
                .setDisplayRequirement(Requirement.all(Requirement.isFalse(hideAllScreens),
                        Requirement.isTrue(customHandledScreens)))
                .setInsertButtonEnabled(false)
                .setDeleteButtonEnabled(false)
                .build());

        category.addEntry(customScreens.build());
    }

    /** Tab 3: diagnostics and the two knobs that are not about what to hide. */
    private static void addAdvancedTab(ConfigBuilder builder, ConfigEntryBuilder entryBuilder,
                                       OBSOverlayConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("obs_overlay.config.category.advanced"));
        category.addEntry(entryBuilder
                .startTextDescription(Component.translatable("obs_overlay.config.advanced.intro"))
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.show_test_icon"),
                        config.showTestIcon)
                .setTooltip(Component.translatable("obs_overlay.config.show_test_icon.tooltip"))
                .setDefaultValue(false)
                .setSaveConsumer(value -> config.showTestIcon = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.premultiplied_alpha"),
                        config.premultipliedAlpha)
                .setTooltip(Component.translatable("obs_overlay.config.premultiplied_alpha.tooltip"))
                .setDefaultValue(true)
                .setSaveConsumer(value -> config.premultipliedAlpha = value)
                .build());

        category.addEntry(entryBuilder
                .startTextDescription(Component.translatable("obs_overlay.config.config_file",
                        OBSOverlayConfig.getPath().toAbsolutePath().toString()))
                .build());
    }

    private static Component defaultLabel(boolean value) {
        return Component.translatable(value
                ? "obs_overlay.config.default_on"
                : "obs_overlay.config.default_off");
    }
}
