package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Random;

public class DozhdSvineyDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 300; // 15 seconds

    public DozhdSvineyDebuff() {
        super("dozhd_sviney", "Pig Rain",
                "Pigs fall from the sky onto all players",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            for (int i = 0; i < 8; i++) {
                double ox = (RANDOM.nextDouble() - 0.5) * 10;
                double oz = (RANDOM.nextDouble() - 0.5) * 10;
                PigEntity pig = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.PIG, world);
                if (pig == null) continue;
                pig.setPosition(player.getX() + ox, player.getY() + 20, player.getZ() + oz);
                world.spawnEntity(pig);
            }
        }
    }
}
