package com.mixaold.burmalda.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.21.2+ replacement for PlayerEntity.eatFood (which was removed): detects the Hot Potato
 * debuff item being consumed via LivingEntity.consumeItem. Empty on 1.21.1.
 */
@Mixin(LivingEntity.class)
public class LivingEntityFoodMixin {
    //? if >=1.21.2 {
    /*@org.spongepowered.asm.mixin.injection.Inject(method = "consumeItem", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void burmalda_onConsumeItem(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!(((Object) this) instanceof net.minecraft.server.network.ServerPlayerEntity player)) return;
        net.minecraft.item.ItemStack stack = player.getActiveItem();
        if (com.mixaold.burmalda.debuff.solo.GoryachayaKartoshkaDebuff.isHotPotato(stack)) {
            com.mixaold.burmalda.debuff.solo.GoryachayaKartoshkaDebuff.onPlayerAtePotato(player);
        }
    }
    *///? }
}
