package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

public class NochStrakhaDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int WAVE_INTERVAL = 200; // new wave every 10 seconds
    private static final int MOBS_PER_WAVE = 6;
    private static final int MOBS_ON_START = 10;

    private static final List<EntityType<? extends MobEntity>> HOSTILE = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.WITCH, EntityType.PILLAGER,
            EntityType.VINDICATOR, EntityType.HUSK, EntityType.STRAY,
            EntityType.PHANTOM
    );

    public NochStrakhaDebuff() {
        super("noch_strakha", "Night of Fear",
                "Mob waves every 10 seconds. No escape.",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.setTimeOfDay(18000);
        }
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.noch_strakha.start", "§4[Night of Fear] §cThey're coming. A lot of them. Sorry."), false);
        spawnWave(server, MOBS_ON_START);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        // Keep it night
        if (ticksActive % 40 == 0) {
            for (ServerWorld world : server.getWorlds()) {
                long time = world.getTimeOfDay() % 24000;
                if (time < 13000 || time > 23000) {
                    world.setTimeOfDay(18000);
                }
            }
        }
        // Spawn a new wave every WAVE_INTERVAL ticks
        if (ticksActive % WAVE_INTERVAL == 0) {
            spawnWave(server, MOBS_PER_WAVE);
        }
    }

    private static void spawnWave(MinecraftServer server, int count) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            for (int i = 0; i < count; i++) {
                EntityType<? extends MobEntity> type = HOSTILE.get(RANDOM.nextInt(HOSTILE.size()));
                MobEntity mob = com.mixaold.burmalda.util.BurmaldaCompat.create(type, world);
                if (mob == null) continue;
                double ox = (RANDOM.nextDouble() - 0.5) * 12;
                double oz = (RANDOM.nextDouble() - 0.5) * 12;
                mob.setPosition(player.getX() + ox, player.getY(), player.getZ() + oz);
                mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.COMMAND, null);
                mob.setTarget(player);
                world.spawnEntity(mob);
            }
        }
    }
}
