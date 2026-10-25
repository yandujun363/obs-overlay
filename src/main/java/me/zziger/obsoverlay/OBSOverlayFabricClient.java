package me.zziger.obsoverlay;

import net.fabricmc.api.ClientModInitializer;

public final class OBSOverlayFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OBSOverlay.init();
    }
}
