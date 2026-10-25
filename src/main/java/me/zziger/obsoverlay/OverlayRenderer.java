package me.zziger.obsoverlay;

import com.mojang.blaze3d.pipeline.RenderTarget;
import me.zziger.obsoverlay.nativehook.NativeHook;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Owns the overlay targets and the native present hook.
 *
 * <p>Lifecycle per frame:
 * <ol>
 *     <li>{@link #beginFrame()} (from {@code GameRenderer#render} HEAD) resets the tag table and
 *     clears the overlay target.</li>
 *     <li>{@code GuiRenderer} redirects tagged draw ranges into {@link #overlay()}.</li>
 *     <li>The native swap hook composites the overlay onto the window back buffer.</li>
 * </ol></p>
 */
public final class OverlayRenderer {
    private final OverlayTarget overlay = new OverlayTarget(OverlayFramebufferType.NORMAL);
    private final NativeHook nativeHook;
    private final boolean active;
    private int frameId;
    private boolean announcedActive;
    private boolean noHookToastShown;
    private boolean notFiringToastShown;

    public OverlayRenderer() {
        NativeHook hook = null;
        try {
            hook = new NativeHook(this);
            hook.init();
        } catch (Throwable throwable) {
            OBSOverlay.LOGGER.error("[obs_overlay] native present hook could not be installed", throwable);
            hook = null;
        }
        this.nativeHook = hook;
        this.active = hook != null && hook.isHooked();
        if (!this.active) {
            OBSOverlay.LOGGER.warn("[obs_overlay] no native present hook - HUD components will be rendered normally,"
                    + " nothing is hidden from OBS. (On Windows this usually means the Vulkan graphics backend is active;"
                    + " switch Video Settings -> Graphics API to OpenGL.)");
        }
    }

    /**
     * Splitting content off is only safe once the present hook is known to run: otherwise the
     * hidden components would be rendered into the overlay target and never reach the screen.
     * The first captured frame therefore renders everything normally.
     */
    public boolean isActive() {
        boolean result = active && nativeHook != null && nativeHook.hasFired()
                && !me.zziger.obsoverlay.nativehook.GlCompositor.isBroken();
        if (result && !announcedActive) {
            announcedActive = true;
            OBSOverlay.LOGGER.info("[obs_overlay] present hook fired - overlay splitting is now active");
        }
        return result;
    }

    public OverlayTarget overlay() {
        return overlay;
    }

    public int frameId() {
        return frameId;
    }

    /**
     * Called at the start of the GUI extraction phase. The tag table only lives for one
     * extract + render cycle, so it has to be reset before extraction (not before rendering,
     * which happens later in the same frame).
     */
    public void beginExtraction() {
        OverlayComponentStack.reset();
    }

    public void beginFrame() {
        frameId++;
        if (!active) {
            if (!noHookToastShown) {
                noHookToastShown = true;
                OverlayUtils.showToast(Component.translatable("obs_overlay.toast.disabled"),
                        Component.translatable("obs_overlay.toast.no_hook"));
            }
            return;
        }
        if (!nativeHook.hasFired() && frameId == 600 && !notFiringToastShown) {
            notFiringToastShown = true;
            OverlayUtils.showToast(Component.translatable("obs_overlay.toast.disabled"),
                    Component.translatable("obs_overlay.toast.not_firing"));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) {
            return;
        }
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (main == null) {
            return;
        }
        overlay.ensureSize(main.width, main.height);
        overlay.clear();
    }
}
