package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.Random;

public class KontsertDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 40; // every 2 seconds

    private static final List<net.minecraft.sound.SoundEvent> NOTES = List.of(
            SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_GUITAR.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value()
    );

    public KontsertDebuff() {
        super("kontsert", "Concert",
                "Random sounds at max volume",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        net.minecraft.sound.SoundEvent note = NOTES.get(RANDOM.nextInt(NOTES.size()));
        float pitch = 0.5f + RANDOM.nextFloat() * 1.5f;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            com.mixaold.burmalda.util.BurmaldaCompat.world(player).playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    note, SoundCategory.RECORDS, 2.0f, pitch);
        }
    }
}
