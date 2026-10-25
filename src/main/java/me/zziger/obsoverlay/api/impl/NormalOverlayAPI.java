package me.zziger.obsoverlay.api.impl;

import me.zziger.obsoverlay.OverlayRenderer;
import me.zziger.obsoverlay.api.IOverlayAPI;

/**
 * Active API implementation. Tagging happens in the default interface methods, so this class only
 * exists to stay source compatible with the 1.21.x versions.
 */
public class NormalOverlayAPI implements IOverlayAPI {
    private final OverlayRenderer renderer;

    public NormalOverlayAPI() {
        this(null);
    }

    public NormalOverlayAPI(OverlayRenderer renderer) {
        this.renderer = renderer;
    }

    public OverlayRenderer getRenderer() {
        return renderer;
    }
}
