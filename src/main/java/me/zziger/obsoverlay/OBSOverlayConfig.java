package me.zziger.obsoverlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import me.zziger.obsoverlay.component.IOverlayComponent;
import me.zziger.obsoverlay.component.OverlayComponentRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/**
 * Plain JSON config ({@code config/obs_overlay.json}), read once during client initialisation.
 *
 * <p>Three independent groups of switches, one per tab of {@link OBSOverlayConfigScreen}:</p>
 * <ul>
 *     <li>{@link #overlayComponents} - draw the component on screen but keep it out of the capture.</li>
 *     <li>{@link #autoHideComponents} - do not draw it at all while any screen is open.</li>
 *     <li>{@link #hideAllScreens} / {@link #overlayScreensList} / {@link #overlayHandledScreensList}
 *     - the same, for whole screens.</li>
 * </ul>
 *
 * <p>Counter-intuitive naming, kept from upstream: {@code true} means "hidden from OBS" (the mod is
 * called OBS Overlay, so "overlay enabled" = "rendered as an overlay").</p>
 */
public final class OBSOverlayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static OBSOverlayConfig instance;

    /** component id -> overlay enabled (i.e. visible on screen, invisible to OBS). */
    public HashMap<String, Boolean> overlayComponents = new HashMap<>();
    /** component id -> hide entirely while any screen is open. */
    public HashMap<String, Boolean> autoHideComponents = new HashMap<>();
    /** screen id (see registry) -> overlay enabled. */
    public HashMap<String, Boolean> overlayScreensList = new HashMap<>();

    public boolean hideAllScreens = false;
    public boolean overlayHandledScreensEnabled = false;
    public HashSet<String> overlayHandledScreensList = new HashSet<>();

    public boolean showTestIcon = false;

    /** GUI content is drawn premultiplied by the 26.3 pipelines, so the compositor blends with (1, 1-a). */
    public boolean premultipliedAlpha = true;

    public transient HashSet<Class<?>> overlayScreensClasses = new HashSet<>();

    private OBSOverlayConfig() {
    }

    public static void init() {
        Path path = getPath();
        OBSOverlayConfig loaded = null;
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                loaded = GSON.fromJson(reader, OBSOverlayConfig.class);
            } catch (JsonParseException | IOException exception) {
                OBSOverlay.LOGGER.error("[obs_overlay] failed to read config", exception);
            }
        }
        instance = loaded != null ? loaded : new OBSOverlayConfig();
        instance.updateCache();
        if (loaded == null) {
            instance.save();
        }
    }

    public static OBSOverlayConfig get() {
        return instance;
    }

    public static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(OBSOverlay.MOD_ID + ".json");
    }

    public void updateCache() {
        overlayScreensClasses.clear();
        overlayScreensList.forEach((screenId, state) -> {
            if (state == null || !state) {
                return;
            }
            Class<?> screenClass = OverlayComponentRegistry.hideableScreens.get(screenId);
            if (screenClass != null) {
                overlayScreensClasses.add(screenClass);
            }
        });
    }

    public void save() {
        updateCache();
        pruneStaleComponentKeys();
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            OBSOverlay.LOGGER.error("[obs_overlay] failed to save config", exception);
        }
    }

    /**
     * Drops config entries of components that no longer exist (e.g. the removed world-space
     * components of the 2.x versions), so old config files get cleaned up on the next save.
     */
    private void pruneStaleComponentKeys() {
        if (OverlayComponentRegistry.components.isEmpty()) {
            return;
        }
        HashSet<String> known = new HashSet<>();
        for (IOverlayComponent component : OverlayComponentRegistry.components) {
            known.add(component.getId());
        }
        overlayComponents.keySet().removeIf(id -> !known.contains(id));
        autoHideComponents.keySet().removeIf(id -> !known.contains(id));
    }

    public static boolean isScreenOverlayed(Screen screen) {
        if (screen == null) {
            return false;
        }
        OBSOverlayConfig config = instance;
        if (config == null) {
            return false;
        }
        if (config.hideAllScreens && Minecraft.getInstance() != null && Minecraft.getInstance().level != null) {
            return true;
        }
        if (config.overlayScreensClasses.contains(screen.getClass())) {
            return true;
        }
        if (config.overlayHandledScreensEnabled && screen instanceof AbstractContainerScreen<?> containerScreen) {
            try {
                MenuType<?> type = containerScreen.getMenu().getType();
                Identifier id = BuiltInRegistries.MENU.getKey(type);
                if (id != null) {
                    return config.overlayHandledScreensList.contains(id.toString())
                            || config.overlayHandledScreensList.contains(id.getPath());
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public boolean isOverlayEnabled(String componentId, boolean defaultValue) {
        return Objects.requireNonNullElse(overlayComponents.get(componentId), defaultValue);
    }

    public void setOverlayEnabled(String componentId, boolean value) {
        overlayComponents.put(componentId, value);
    }

    public boolean isAutoHideEnabled(String componentId) {
        return Objects.requireNonNullElse(autoHideComponents.get(componentId), true);
    }

    public void setAutoHideEnabled(String componentId, boolean value) {
        autoHideComponents.put(componentId, value);
    }
}
