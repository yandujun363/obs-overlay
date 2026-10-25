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
 * Cloth Config based settings screen, ported from the 1.21.x version.
 *
 * <p>Reachable from {@code Options > Video Settings > OBS Overlay settings} (see
 * {@code VideoSettingsScreenMixin}); ModMenu integration is not ported yet.</p>
 */
public final class OBSOverlayConfigScreen {
    private OBSOverlayConfigScreen() {
    }

    public static Screen create(Screen parent) {
        OBSOverlayConfig config = OBSOverlayConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("obs_overlay.config.title"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.empty());

        general.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.show_test_icon"), config.showTestIcon)
                .setTooltip(Component.translatable("obs_overlay.config.show_test_icon.tooltip"))
                .setDefaultValue(false)
                .setSaveConsumer(value -> config.showTestIcon = value)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.premultiplied_alpha"), config.premultipliedAlpha)
                .setTooltip(Component.translatable("obs_overlay.config.premultiplied_alpha.tooltip"))
                .setDefaultValue(true)
                .setSaveConsumer(value -> config.premultipliedAlpha = value)
                .build());

        // ---- components to overlay ----
        Map<String, BooleanListEntry> overlayEntries = new HashMap<>();
        SubCategoryBuilder componentsToOverlay = entryBuilder
                .startSubCategory(Component.translatable("obs_overlay.config.components_to_overlay"))
                .setExpanded(true)
                .setTooltip(Component.translatable("obs_overlay.config.components_to_overlay.tooltip"));

        for (IOverlayComponent component : OverlayComponentRegistry.components) {
            BooleanListEntry entry = entryBuilder
                    .startBooleanToggle(Component.translatable("obs_overlay.component." + component.getId()),
                            component.isOverlayEnabled())
                    .setTooltip(Component.translatable("obs_overlay.component." + component.getId() + ".tooltip"))
                    .setDefaultValue(component.isOverlayEnabledDefault())
                    .setSaveConsumer(component::setOverlayEnabled)
                    .build();
            overlayEntries.put(component.getId(), entry);
            componentsToOverlay.add(entry);
        }
        general.addEntry(componentsToOverlay.build());

        // ---- screens to overlay ----
        SubCategoryBuilder screensToOverlay = entryBuilder
                .startSubCategory(Component.translatable("obs_overlay.config.screens_to_overlay"))
                .setTooltip(Component.translatable("obs_overlay.config.screens_to_overlay.tooltip"));

        BooleanListEntry hideAllScreens = entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.hide_all_screens"), config.hideAllScreens)
                .setDefaultValue(false)
                .setSaveConsumer(state -> config.hideAllScreens = state)
                .build();
        screensToOverlay.add(hideAllScreens);

        OverlayComponentRegistry.hideableScreens.forEach((screenId, screenClass) -> {
            BooleanListEntry entry = entryBuilder
                    .startBooleanToggle(Component.translatable("obs_overlay.screen." + screenId),
                            config.overlayScreensList.getOrDefault(screenId, false))
                    .setDefaultValue(false)
                    .setDisplayRequirement(Requirement.isFalse(hideAllScreens))
                    .setSaveConsumer(state -> config.overlayScreensList.put(screenId, state))
                    .build();
            screensToOverlay.add(entry);
        });

        BooleanListEntry customHandledScreens = entryBuilder
                .startBooleanToggle(Component.translatable("obs_overlay.config.enable_custom_handled_screens"),
                        config.overlayHandledScreensEnabled)
                .setTooltip(Component.translatable("obs_overlay.config.enable_custom_handled_screens.tooltip"))
                .setDefaultValue(false)
                .setDisplayRequirement(Requirement.isFalse(hideAllScreens))
                .setSaveConsumer(state -> config.overlayHandledScreensEnabled = state)
                .build();
        screensToOverlay.add(customHandledScreens);

        screensToOverlay.add(entryBuilder
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
                                return Optional.of(Component.translatable("obs_overlay.config.screen_doesnt_exist", id));
                            }
                        } catch (Exception exception) {
                            return Optional.of(Component.translatable("obs_overlay.config.screen_doesnt_exist", id));
                        }
                    }
                    return Optional.empty();
                })
                .build());

        // A copy: cloth-config hands this list to an editable widget.
        List<String> existingScreens = new ArrayList<>(
                BuiltInRegistries.MENU.keySet().stream().map(Identifier::toString).toList());
        screensToOverlay.add(entryBuilder
                .startStrList(Component.translatable("obs_overlay.config.existing_handled_screens"), existingScreens)
                .setTooltip(Component.translatable("obs_overlay.config.existing_handled_screens.tooltip"))
                .setDisplayRequirement(Requirement.all(Requirement.isFalse(hideAllScreens),
                        Requirement.isTrue(customHandledScreens)))
                .setInsertButtonEnabled(false)
                .setDeleteButtonEnabled(false)
                .build());

        general.addEntry(screensToOverlay.build());

        // ---- auto hide ----
        SubCategoryBuilder autoHideComponents = entryBuilder
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
            autoHideComponents.add(entryBuilder
                    .startBooleanToggle(Component.translatable("obs_overlay.component." + component.getId()),
                            component.isAutoHideEnabled())
                    .setDefaultValue(true)
                    .setRequirement(Requirement.isTrue(overlayEntry))
                    .setTooltipSupplier(() -> {
                        Component mainTooltip = Component.translatable(
                                "obs_overlay.component." + component.getId() + ".tooltip");
                        if (overlayEntry.getValue()) {
                            return Optional.of(new Component[]{mainTooltip});
                        }
                        Component optionName = Component.translatable("obs_overlay.component." + component.getId());
                        return Optional.of(new Component[]{
                                mainTooltip,
                                Component.translatable("obs_overlay.config.requires_overlay_enabled", optionName)
                                        .withStyle(ChatFormatting.RED)
                        });
                    })
                    .setSaveConsumer(component::setAutoHideEnabled)
                    .build());
        }
        general.addEntry(autoHideComponents.build());

        builder.setDoesConfirmSave(false);
        builder.setShouldListSmoothScroll(false);
        builder.setShouldTabsSmoothScroll(false);
        builder.setSavingRunnable(config::save);

        return builder.build();
    }
}
