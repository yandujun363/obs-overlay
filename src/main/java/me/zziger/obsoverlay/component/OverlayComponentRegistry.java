package me.zziger.obsoverlay.component;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class OverlayComponentRegistry {
    public static final ArrayList<IOverlayComponent> components = new ArrayList<>();
    /** Screens that do not trigger auto-hide (the HUD stays visible behind them). */
    public static final ArrayList<Class<?>> ignoredScreens = new ArrayList<>(List.of(ChatScreen.class));

    /**
     * Screens that can be hidden from the capture as a whole.
     *
     * <p>Insertion ordered on purpose: the settings screen iterates this map, and a stable order
     * (survival inventory, creative inventory, pause menu, command block) is easier to scan than
     * the arbitrary order of a {@link java.util.HashMap}.</p>
     */
    public static final LinkedHashMap<String, Class<?>> hideableScreens = new LinkedHashMap<>();

    static {
        hideableScreens.put("inventory", InventoryScreen.class);
        hideableScreens.put("creative_inventory", CreativeModeInventoryScreen.class);
        hideableScreens.put("pause_menu", PauseScreen.class);
        hideableScreens.put("command_block", CommandBlockEditScreen.class);
    }

    private OverlayComponentRegistry() {
    }

    public static IOverlayComponent registerComponent(IOverlayComponent component) {
        components.add(component);
        return component;
    }

    public static void registerComponents(IOverlayComponent... newComponents) {
        for (IOverlayComponent component : newComponents) {
            registerComponent(component);
        }
    }

    public static void addIgnoredScreen(Class<?> screen) {
        ignoredScreens.add(screen);
    }

    public static void addHideableScreen(String id, Class<?> screen) {
        hideableScreens.put(id, screen);
    }
}
