package me.zziger.obsoverlay.api;

import me.zziger.obsoverlay.OverlayComponentStack;
import me.zziger.obsoverlay.OverlayFramebufferType;
import me.zziger.obsoverlay.component.IOverlayComponent;

/**
 * Public API of the mod, kept source compatible with the 1.21.x versions.
 *
 * <p>{@code beginDraw}/{@code endDraw} no longer switch framebuffers: they mark the GUI content
 * added in between as belonging to an overlay component. Calls are only valid while the GUI is
 * being extracted (they are nested with the vanilla extraction calls).</p>
 */
public interface IOverlayAPI {
    default void beginDraw(IOverlayComponent component) {
        OverlayComponentStack.push(component);
    }

    default void endDraw(IOverlayComponent component) {
        OverlayComponentStack.pop();
    }

    default void beginDraw(OverlayFramebufferType type) {
        OverlayComponentStack.pushTag(OverlayComponentStack.API_TAG);
    }

    default void endDraw(OverlayFramebufferType type) {
        OverlayComponentStack.pop();
    }

    /**
     * No-op in the 26.3 port: depth handling for world space content is implemented by the overlay
     * target itself ({@code RenderTarget#copyDepthFrom}) once those components are ported.
     */
    default void backupDepth(boolean overrideDepth) {
    }
}
