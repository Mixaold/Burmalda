package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class NetDebuff extends Debuff {

    private static final int FREEZE_DURATION = 60;

    private int frozenTicks = 0;
    private boolean prevOnGround = true;
    private ScreenHandler prevScreenHandler = null;

    public NetDebuff() {
        super("net", "No",
                "Jumped? Nope. Opened chest? Nope. No.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        frozenTicks = 0;
        prevOnGround = player.isOnGround();
        prevScreenHandler = player.currentScreenHandler;
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        frozenTicks = 0;
        prevScreenHandler = null;
    }

    @Override
    public void onCausedDamage(ServerPlayerEntity attacker) {
        trigger();
    }

    /** Called from BurmaldaMod when player places a block while this debuff is active. */
    public void triggerExternal() {
        trigger();
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        boolean onGround = player.isOnGround();
        Vec3d vel = player.getVelocity();

        // Jump detection
        if (prevOnGround && !onGround && vel.y > 0) {
            trigger();
        }
        prevOnGround = onGround;

        // Block inventory open detection (chest, furnace, crafting table, etc.)
        ScreenHandler current = player.currentScreenHandler;
        if (current != prevScreenHandler && !(current instanceof PlayerScreenHandler)) {
            trigger();
        }
        prevScreenHandler = current;

        // Apply freeze
        if (frozenTicks > 0) {
            frozenTicks--;
            // Slowness 127 stops horizontal movement completely on the client side
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 2, 127, false, false, false));
            player.setVelocity(0, player.getVelocity().y, 0);
            com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(player);
            if (frozenTicks % 20 == 0 && frozenTicks > 0) {
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.net.countdown", "§c[No] §f%s sec...", frozenTicks / 20), true);
            }
        }
    }

    private void trigger() {
        frozenTicks = FREEZE_DURATION;
    }
}
