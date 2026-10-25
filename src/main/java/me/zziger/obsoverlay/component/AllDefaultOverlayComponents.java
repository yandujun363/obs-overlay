package me.zziger.obsoverlay.component;

import me.zziger.obsoverlay.OBSOverlay;
import me.zziger.obsoverlay.component.type.HUDOverlayComponent;

/**
 * Component list of the 26.3 port.
 *
 * <p>Only GUI/HUD components: world-space content (signs, chests, maps, name tags, banners, beacon
 * beams) is not part of this port. In 26.3 world rendering is submit based, so hiding in-world
 * content needs a completely different approach (skip in the main pass, copy depth, second pass)
 * and was dropped; per-component comparison with upstream is in {@code docs/UPSTREAM-DIFF.md}.</p>
 *
 * <p>The declaration order is the order of the list in the settings screen ("HUD elements" tab):
 * the default-on component first, then the ones covering most of the screen, then the overlays that
 * only show up on demand. Keep it in sync with {@code docs/CONFIG.md} §3.</p>
 */
public final class AllDefaultOverlayComponents {
    public static final IOverlayComponent debugMenu = new HUDOverlayComponent(OBSOverlay.id("debug_menu"), true, true);
    public static final IOverlayComponent mainHud = new HUDOverlayComponent(OBSOverlay.id("main_hud"), false, true);
    public static final IOverlayComponent chat = new HUDOverlayComponent(OBSOverlay.id("chat"), false, true);
    public static final IOverlayComponent chatBar = new HUDOverlayComponent(OBSOverlay.id("chat_bar"), false, false);
    public static final IOverlayComponent scoreboards = new HUDOverlayComponent(OBSOverlay.id("scoreboards"), false, true);
    public static final IOverlayComponent playerList = new HUDOverlayComponent(OBSOverlay.id("player_list"), false, false);
    public static final IOverlayComponent actionbar = new HUDOverlayComponent(OBSOverlay.id("actionbar"), false, true);
    public static final IOverlayComponent titleSubtitle = new HUDOverlayComponent(OBSOverlay.id("title_subtitle"), false, true);
    public static final IOverlayComponent effects = new HUDOverlayComponent(OBSOverlay.id("effects"), false, true);
    public static final IOverlayComponent subtitles = new HUDOverlayComponent(OBSOverlay.id("subtitles"), false, true);

    private AllDefaultOverlayComponents() {
    }

    public static void init() {
        OverlayComponentRegistry.registerComponents(debugMenu, mainHud, chat, chatBar, scoreboards, playerList,
                actionbar, titleSubtitle, effects, subtitles);
    }
}
