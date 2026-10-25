package me.zziger.obsoverlay;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu entry point. Only loaded when ModMenu is installed (the entrypoint namespace is requested
 * by ModMenu itself), so ModMenu stays an optional dependency.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return OBSOverlayConfigScreen::create;
    }
}
