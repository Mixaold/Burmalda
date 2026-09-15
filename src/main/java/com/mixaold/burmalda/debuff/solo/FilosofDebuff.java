package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class FilosofDebuff extends Debuff {

    private static final int FREEZE_INTERVAL = 400;
    private static final int FREEZE_DURATION = 80;
    private static final Random RANDOM = new Random();

    private static final String[] PHRASES = {
        "burmalda.filosof.phrase.0",
        "burmalda.filosof.phrase.1",
        "burmalda.filosof.phrase.2",
        "burmalda.filosof.phrase.3",
        "burmalda.filosof.phrase.4",
        "burmalda.filosof.phrase.5",
        "burmalda.filosof.phrase.6",
        "burmalda.filosof.phrase.7",
        "burmalda.filosof.phrase.8",
        "burmalda.filosof.phrase.9",
        "burmalda.filosof.phrase.10"
    };

    private int frozenTicks = 0;

    public FilosofDebuff() {
        super("filosof", "Philosopher",
                "Every 20 seconds — an existential question",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        frozenTicks = 0;
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        frozenTicks = 0;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % FREEZE_INTERVAL == 0) {
            frozenTicks = FREEZE_DURATION;
            String phrase = PHRASES[RANDOM.nextInt(PHRASES.length)];
            BurmaldaNetwork.sendAnnouncement(player, "burmalda.debuff.filosof.name", phrase, true);
        }

        if (frozenTicks > 0) {
            frozenTicks--;
            Vec3d v = player.getVelocity();
            player.setVelocity(0, v.y, 0);
            com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(player);
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        }
    }
}
