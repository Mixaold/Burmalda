package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Random;

public class ApokalipsisDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int INTERVAL = 200; // every 10 seconds

    public ApokalipsisDebuff() {
        super("apokalipsis", "Apocalypse",
                "Lightning strikes randomly near players",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % INTERVAL != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) continue;
            double ox = (RANDOM.nextDouble() - 0.5) * 60;
            double oz = (RANDOM.nextDouble() - 0.5) * 60;

            LightningEntity lightning = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.LIGHTNING_BOLT, world);
            if (lightning != null) {
                lightning.refreshPositionAfterTeleport(player.getX() + ox, player.getY(), player.getZ() + oz);
                world.spawnEntity(lightning);
            }
        }
    }
}
