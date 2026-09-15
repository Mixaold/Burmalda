package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class MassovoeVoskresenieDebuff extends Debuff {

    private static final Random RANDOM = new Random();

    private final List<UUID> spawnedZombies = new ArrayList<>();
    private boolean done = false;

    public MassovoeVoskresenieDebuff() {
        super("massovoe_voskreseniye", "Mass Resurrection",
                "Zombie hordes rise around every player",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        spawnedZombies.clear();
        done = false;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            int count = 5 + RANDOM.nextInt(6);
            for (int i = 0; i < count; i++) {
                ZombieEntity zombie = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.ZOMBIE, world);
                if (zombie == null) continue;
                double ox = (RANDOM.nextDouble() - 0.5) * 16;
                double oz = (RANDOM.nextDouble() - 0.5) * 16;
                zombie.setPosition(player.getX() + ox, player.getY(), player.getZ() + oz);
                zombie.setTarget(player);
                zombie.setPersistent();
                world.spawnEntity(zombie);
                spawnedZombies.add(zombie.getUuid());
            }
        }
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.massovoe_voskreseniye.start",
                        "§4[Массовое воскрешение] §fМёртвые снова ходят."), false);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (done || spawnedZombies.isEmpty()) return;
        if (ticksActive % 20 != 0) return;

        for (UUID uuid : spawnedZombies) {
            boolean found = false;
            for (ServerWorld world : server.getWorlds()) {
                Entity e = world.getEntity(uuid);
                if (e != null && e.isAlive()) {
                    found = true;
                    break;
                }
            }
            if (found) return; // at least one zombie still alive
        }

        // All zombies dead or removed
        done = true;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendGroupDebuffDone(player);
        }
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.massovoe_voskreseniye.done",
                        "§a[Mass Resurrection] §fAll the dead have been put to rest."), false);
    }
}
