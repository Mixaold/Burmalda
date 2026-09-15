package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Random;

public class BombaDebuff extends Debuff {

    private static final int EXPLODE_AT = 3600; // exactly 3 minutes at 20 TPS
    private static final int COUNTDOWN_START = EXPLODE_AT - 200; // 10 seconds before
    private static final Random RANDOM = new Random();
    private boolean exploded = false;
    private boolean warnedThirty = false;
    private boolean warnedFifteen = false;

    public BombaDebuff() {
        super("bomba", "Timed Bomb",
                "In 3 minutes — explosion",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        exploded = false;
        warnedThirty = false;
        warnedFifteen = false;
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.bomba.start", "§c[☢ BOMB] §fTimer activated. No, you can't run."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        exploded = false;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (exploded) return;

        // Ticking sound — only in the last 15 seconds, escalating
        int remaining = (EXPLODE_AT - ticksActive) / 20;
        if (ticksActive >= 3300 && ticksActive % 20 == 0) {
            float pitch  = remaining > 10 ? 0.9f : 0.6f + (float)(10 - Math.max(remaining, 0)) * 0.13f;
            float volume = remaining > 10 ? 0.7f : 0.8f + (float)(10 - Math.max(remaining, 0)) * 0.07f;
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    SoundCategory.PLAYERS, volume, pitch);
        }

        // Warnings
        if (ticksActive == 3000 && !warnedThirty) {
            warnedThirty = true;
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.bomba.30s", "§c[☢ BOMB] §f30 seconds remaining..."), false);
        }
        if (ticksActive == 3300 && !warnedFifteen) {
            warnedFifteen = true;
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.bomba.15s", "§c[☢ BOMB] §f15 seconds remaining..."), false);
        }

        // 10-second countdown in chat
        if (ticksActive >= COUNTDOWN_START && ticksActive < EXPLODE_AT && ticksActive % 20 == 0) {
            int seconds = (EXPLODE_AT - ticksActive) / 20;
            player.sendMessage(Text.translatableWithFallback(
                    "burmalda.chat.bomba.countdown", "§4[☢ BOMB] §c%s...", String.valueOf(seconds)), false);
        }

        if (ticksActive >= EXPLODE_AT && !exploded) {
            exploded = true;
            BurmaldaAdvancements.trigger(player, BurmaldaAdvancements.BOMBA_EXPLODE);
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            double x = player.getX(), y = player.getY(), z = player.getZ();

            // Central mega explosion
            world.createExplosion(null, x, y, z, 30.0f, true, World.ExplosionSourceType.TNT);

            // Ring of secondary explosions radiating outward
            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2 / 16) * i;
                double r = 10 + RANDOM.nextDouble() * 8;
                world.createExplosion(null,
                        x + Math.cos(angle) * r,
                        y + (RANDOM.nextDouble() - 0.3) * 6,
                        z + Math.sin(angle) * r,
                        8.0f, true, World.ExplosionSourceType.TNT);
            }

            player.sendMessage(Text.translatableWithFallback("burmalda.chat.bomba.explode", "§c[☢ BOMB] §4KABOOM!!!"), false);
        }
    }
}
