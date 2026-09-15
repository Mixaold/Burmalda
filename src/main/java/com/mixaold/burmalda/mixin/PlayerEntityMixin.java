package com.mixaold.burmalda.mixin;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffManager;
import com.mixaold.burmalda.debuff.solo.GoryachayaKartoshkaDebuff;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    /**
     * Fires after PlayerEntity.damage() resolves.
     *
     * Handles two debuffs:
     * 1. Общага  — propagates the same damage amount to the other two linked players.
     * 2. Тряпичная кукла — applies extra knockback so the total effect is ≈3×.
     *
     * Both are no-ops on the client (instanceof ServerPlayerEntity guard) and when the
     * hit is a replication from Общага itself (DebuffManager guards against loops via
     * ObshchagaLink.isReplicating).
     */
    @Inject(method = "damage", at = @At("TAIL"))
    //? if >=1.21.2 {
    /*private void burmalda_onDamage(net.minecraft.server.world.ServerWorld world, DamageSource source, float amount,
                                    CallbackInfoReturnable<Boolean> cir) {
    *///? } else {
    private void burmalda_onDamage(DamageSource source, float amount,
                                    CallbackInfoReturnable<Boolean> cir) {
    //? }
        // Only process on the server and only when damage was actually applied
        if (!cir.getReturnValueZ()) return;
        if (!(((Object) this) instanceof ServerPlayerEntity player)) return;

        // ── Общага: replicate damage to linked players ───────────────────────
        DebuffManager.onPlayerDamaged(player, amount);

        Debuff debuff = DebuffManager.getActiveDebuff(player.getUuid());

        // ── Generic onDamage hook (EkhoUrona, etc.) ──────────────────────────
        if (debuff != null) {
            debuff.onDamage(player, amount);
        }

        // ── onCausedDamage: notify the attacker's debuff ──────────────────────
        var attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity attackerPlayer) {
            Debuff attackerDebuff = DebuffManager.getActiveDebuff(attackerPlayer.getUuid());
            if (attackerDebuff != null) {
                attackerDebuff.onCausedDamage(attackerPlayer);
            }
            // ── Hot Potato: tag-by-touch — the item jumps from attacker to victim ─
            GoryachayaKartoshkaDebuff.tryTransferOnHit(attackerPlayer, player);
        }

        // ── Тряпичная кукла: massive knockback from ANY damage (hit, fall, fire…) ─
        if (debuff != null && "tryapichnaya_kukla".equals(debuff.getId())) {
            double dx, dz;
            if (attacker != null) {
                dx = attacker.getX() - player.getX();
                dz = attacker.getZ() - player.getZ();
            } else {
                // No attacker (fall, fire, drowning…) — fling in a random horizontal direction
                double ang = Math.random() * Math.PI * 2.0;
                dx = Math.cos(ang);
                dz = Math.sin(ang);
            }
            player.takeKnockback(3.5, dx, dz);  // ~10x vanilla
            // Launch upward AND push the whole velocity to the player's own client — a server-side
            // velocity change otherwise never reaches the affected player (broke on 1.21.11).
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                com.mixaold.burmalda.util.BurmaldaCompat.pushVelocity(sp, 0.0, 0.7, 0.0);
            } else {
                player.addVelocity(0.0, 0.7, 0.0);
                com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(player);
            }
        }
    }

    /** Fires when a player finishes eating (1.21.1 only; 1.21.2+ handled by LivingEntityFoodMixin). */
    //? if <1.21.2 {
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void burmalda_onEatFood(CallbackInfoReturnable<ItemStack> cir) {
        if (!(((Object) this) instanceof ServerPlayerEntity player)) return;
        // getActiveItem() returns the item currently being consumed at HEAD of eatFood
        ItemStack stack = player.getActiveItem();
        if (GoryachayaKartoshkaDebuff.isHotPotato(stack)) {
            GoryachayaKartoshkaDebuff.onPlayerAtePotato(player);
        }
    }
    //? }
}
