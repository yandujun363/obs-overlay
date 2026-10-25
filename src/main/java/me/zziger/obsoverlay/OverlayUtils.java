package me.zziger.obsoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;

public final class OverlayUtils {
    private OverlayUtils() {
    }

    public static void showToast(Component title, Component description) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            ToastManager manager = minecraft.gui.toastManager();
            SystemToast.add(manager, SystemToast.SystemToastId.LOW_DISK_SPACE, title, description);
        });
    }
}
