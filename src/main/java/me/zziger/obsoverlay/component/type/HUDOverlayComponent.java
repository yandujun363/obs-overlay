package me.zziger.obsoverlay.component.type;

import net.minecraft.resources.Identifier;

/** HUD component: 2D content drawn by the GUI renderer. */
public class HUDOverlayComponent extends DefaultOverlayComponent {
    public HUDOverlayComponent(Identifier id, boolean defaultOverlay, boolean canAutoHide) {
        super(id, defaultOverlay, canAutoHide);
    }
}
