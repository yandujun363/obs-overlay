package me.zziger.obsoverlay.component.type;

import me.zziger.obsoverlay.OBSOverlayConfig;
import me.zziger.obsoverlay.component.IOverlayComponent;
import me.zziger.obsoverlay.component.OverlayComponentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public class DefaultOverlayComponent implements IOverlayComponent {
    private final Identifier id;
    private final String configKey;
    private final boolean canAutoHide;
    private final boolean defaultOverlay;

    private boolean overlay;
    private boolean autoHide;

    public DefaultOverlayComponent(Identifier id, boolean defaultOverlay, boolean canAutoHide) {
        this.id = id;
        this.configKey = id.toLanguageKey();
        this.canAutoHide = canAutoHide;
        this.defaultOverlay = defaultOverlay;

        OBSOverlayConfig config = OBSOverlayConfig.get();
        this.overlay = config.isOverlayEnabled(configKey, defaultOverlay);
        this.autoHide = canAutoHide && config.isAutoHideEnabled(configKey);
    }

    @Override
    public boolean canAutoHide() {
        return canAutoHide;
    }

    @Override
    public String getId() {
        return configKey;
    }

    @Override
    public boolean isOverlayEnabledDefault() {
        return defaultOverlay;
    }

    @Override
    public boolean isOverlayEnabled() {
        return overlay;
    }

    @Override
    public void setOverlayEnabled(boolean value) {
        this.overlay = value;
        OBSOverlayConfig.get().setOverlayEnabled(configKey, value);
    }

    @Override
    public boolean isAutoHideEnabled() {
        return canAutoHide && autoHide;
    }

    @Override
    public void setAutoHideEnabled(boolean value) {
        if (!canAutoHide) {
            return;
        }
        this.autoHide = value;
        OBSOverlayConfig.get().setAutoHideEnabled(configKey, value);
    }

    @Override
    public boolean isHidden() {
        if (!isAutoHideEnabled()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return false;
        }
        Screen currentScreen = minecraft.gui.screen();
        return currentScreen != null && !OverlayComponentRegistry.ignoredScreens.contains(currentScreen.getClass());
    }
}
