package me.zziger.obsoverlay.mixin;

import me.zziger.obsoverlay.OBSOverlayConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "OBS Overlay settings" button to the bottom of the vanilla video settings screen
 * (26.3 renamed that screen to {@code VideoSettingsScreen} and the list to {@code OptionsList#addBig}).
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void obs$addOverlayButton(CallbackInfo ci) {
        this.list.addBig(Button.builder(Component.translatable("obs_overlay.config.title"),
                        button -> Minecraft.getInstance()
                                .setScreenAndShow(OBSOverlayConfigScreen.create((Screen) (Object) this)))
                .build());
    }
}
