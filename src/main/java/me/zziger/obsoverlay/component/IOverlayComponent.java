package me.zziger.obsoverlay.component;

/**
 * A hideable GUI/HUD component.
 *
 * <p>Semantics in 26.3: {@link #isOverlayEnabled()} means "draw this on my screen but not into the
 * captured frame". The component is tagged during the extraction phase; the renderer decides per
 * draw batch whether it goes to the main target or to the overlay target.</p>
 */
public interface IOverlayComponent {
    boolean canAutoHide();

    /** Unique id, used as config key and localisation key (e.g. {@code obs_overlay.debug_menu}). */
    String getId();

    boolean isOverlayEnabledDefault();

    boolean isOverlayEnabled();

    void setOverlayEnabled(boolean value);

    boolean isAutoHideEnabled();

    void setAutoHideEnabled(boolean value);

    /** True when the component must not be rendered at all (e.g. auto-hidden behind a screen). */
    boolean isHidden();
}
