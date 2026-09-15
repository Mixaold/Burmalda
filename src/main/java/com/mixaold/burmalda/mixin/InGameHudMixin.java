package com.mixaold.burmalda.mixin;

import com.mixaold.burmalda.debuff.ClientDebuffState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class InGameHudMixin {

    /**
     * Hides the hotbar (slot items, durability, offhand) for:
     * - slepoe_doverie (Слепое доверие)
     */
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void burmalda_hideHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientDebuffState.hideHotbar()) {
            ci.cancel();
        }
    }

    /**
     * Hides health/food/armor status bars for:
     * - slepoe_doverie (Слепое доверие) — hides everything
     * - kot_shrodingera (Кот Шрёдингера) — hides HP only (but we hide status bars to be safe)
     */
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void burmalda_hideStatusBars(DrawContext context, CallbackInfo ci) {
        if (ClientDebuffState.hideHealth()) {
            ci.cancel();
        }
    }

    // The dedicated renderExperienceBar method existed through 1.21.5; in 1.21.8 it was inlined
    // and the bar is gated by shouldShowExperienceBar() instead.
    //? if >=1.21.8 {
    /*@Inject(method = "shouldShowExperienceBar", at = @At("HEAD"), cancellable = true)
    private void burmalda_hideXpBar(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (ClientDebuffState.hideXpBar()) {
            cir.setReturnValue(false);
        }
    }
    *///? } else {
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void burmalda_hideXpBar(DrawContext context, int x, CallbackInfo ci) {
        if (ClientDebuffState.hideXpBar()) {
            ci.cancel();
        }
    }
    //? }
}
