package me.zziger.obsoverlay.api.impl;

import me.zziger.obsoverlay.OverlayFramebufferType;
import me.zziger.obsoverlay.api.IOverlayAPI;
import me.zziger.obsoverlay.component.IOverlayComponent;

/** No-op API, used when the renderer is unavailable. */
public class DummyOverlayAPI implements IOverlayAPI {
    @Override
    public void beginDraw(IOverlayComponent component) {
    }

    @Override
    public void endDraw(IOverlayComponent component) {
    }

    @Override
    public void beginDraw(OverlayFramebufferType type) {
    }

    @Override
    public void endDraw(OverlayFramebufferType type) {
    }
}
