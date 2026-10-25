package me.zziger.obsoverlay;

import me.zziger.obsoverlay.api.IOverlayAPI;
import me.zziger.obsoverlay.api.impl.DummyOverlayAPI;
import me.zziger.obsoverlay.api.impl.NormalOverlayAPI;
import me.zziger.obsoverlay.component.AllDefaultOverlayComponents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the 26.3 port.
 *
 * <p>Rendering model of this port (Minecraft 26.3 / renderpearl):
 * the vanila GUI is extracted into a {@code GuiRenderState} and drawn in batches by
 * {@code GuiRenderer}. Instead of switching framebuffers in the middle of a pass (which is
 * impossible now), elements belonging to an "overlay" component are tagged during extraction
 * and {@code GuiRenderer} draws those batches into our own {@link OverlayTarget}. The target is
 * composited onto the window back buffer from the native present hook, i.e. after OBS captured
 * the frame.</p>
 */
public final class OBSOverlay {
    public static final String MOD_ID = "obs_overlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static OverlayRenderer renderer;
    private static IOverlayAPI api = new DummyOverlayAPI();
    private static boolean renderFailed;

    private OBSOverlay() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Kept for API compatibility. May be called at any time; returns a no-op implementation while
     * the renderer is unavailable.
     */
    public static IOverlayAPI getAPI() {
        return api;
    }

    /**
     * Lazily creates the renderer. Must be called on the render thread (first call happens from the
     * {@code GameRenderer#render} hook, after the GPU device exists).
     */
    public static OverlayRenderer getRenderer() {
        if (renderer == null && !renderFailed) {
            try {
                OverlayRenderer created = new OverlayRenderer();
                api = new NormalOverlayAPI(created);
                renderer = created;
            } catch (Throwable throwable) {
                renderFailed = true;
                LOGGER.error("[obs_overlay] failed to initialise the overlay renderer", throwable);
                renderer = null;
                api = new DummyOverlayAPI();
            }
        }
        return renderer;
    }

    /** True when overlays are actually split off and composited at present time. */
    public static boolean isActive() {
        OverlayRenderer current = getRenderer();
        return current != null && current.isActive();
    }

    public static void init() {
        OBSOverlayConfig.init();
        AllDefaultOverlayComponents.init();
        warnIfNotOpenGl();
    }

    /**
     * The overlay compositor needs OpenGL: it draws the overlay texture from the native swap hook
     * and reads the texture handle of the overlay target. The Vulkan backend has neither.
     */
    private static void warnIfNotOpenGl() {
        java.nio.file.Path options = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("options.txt");
        if (!java.nio.file.Files.isRegularFile(options)) {
            return;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(options)) {
                if (line.startsWith("preferredGraphicsBackend")) {
                    if (line.contains("vulkan")) {
                        LOGGER.warn("[obs_overlay] this instance is configured with the Vulkan graphics backend."
                                + " OBS Overlay can only composite on OpenGL - set Options > Video Settings >"
                                + " Graphics API to OpenGL, otherwise nothing is hidden from OBS.");
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
            // best effort only
        }
    }
}
