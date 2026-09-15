package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ZooparkDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int MOBS_ON_START   = 8;
    private static final int RESPAWN_INTERVAL = 600;  // 30 sec
    private static final int MOBS_ON_RESPAWN  = 5;
    private static final double WITHER_LEASH_DIST = 300.0; // kill wither if all players farther

    private static final List<EntityType<? extends MobEntity>> TYPES = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CAVE_SPIDER,
            EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.RAVAGER,
            EntityType.WITCH, EntityType.DROWNED, EntityType.WITHER_SKELETON
    );

    // Wither tracking: entity IDs of active withers
    private final List<Integer> witherIds = new ArrayList<>();
    private boolean witherEverSpawned = false;
    private int witherSpawnCount = 0; // max 2 per debuff cycle

    public ZooparkDebuff() {
        super("zoopark", "Zoo",
                "Mob waves keep coming",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        witherIds.clear();
        witherEverSpawned = false;
        witherSpawnCount  = 0;
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.zoopark.start", "§6[Zoo] §fThe cage doors are open. Not a metaphor."), false);
        spawnWave(server, MOBS_ON_START);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        // Spawn mob waves
        if (ticksActive % RESPAWN_INTERVAL == 0) {
            spawnWave(server, MOBS_ON_RESPAWN);
        }

        // Check Wither leash: kill any Wither that strays >300 blocks from all players
        if (ticksActive % 40 == 0 && !witherIds.isEmpty()) {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            List<Integer> dead = new ArrayList<>();
            for (int wid : witherIds) {
                Entity entity = findEntity(server, wid);
                if (entity == null) { dead.add(wid); continue; }
                double minDist = Double.MAX_VALUE;
                for (ServerPlayerEntity p : players) {
                    double d = entity.squaredDistanceTo(p);
                    if (d < minDist) minDist = d;
                }
                if (minDist > WITHER_LEASH_DIST * WITHER_LEASH_DIST) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                    dead.add(wid);
                    server.getPlayerManager().broadcast(
                            Text.translatableWithFallback("burmalda.chat.zoopark.wither_gone", "§8[Zoo] §7Wither dissolved. Smart."), false);
                }
            }
            witherIds.removeAll(dead);
        }
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        // Clean up remaining withers (DISCARDED — no drops)
        for (int wid : witherIds) {
            Entity entity = findEntity(server, wid);
            if (entity != null) entity.remove(Entity.RemovalReason.DISCARDED);
        }
        witherIds.clear();

        // Achievement: survived Зоопарк when a Wither was present
        if (witherEverSpawned) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isAlive()) {
                    BurmaldaAdvancements.trigger(p, BurmaldaAdvancements.ZOOPARK_WITHER_SURVIVED);
                }
            }
        }
        witherEverSpawned = false;
    }

    private Entity findEntity(MinecraftServer server, int id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntityById(id);
            if (e != null) return e;
        }
        return null;
    }

    private void spawnWave(MinecraftServer server, int count) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
            for (int i = 0; i < count; i++) {
                EntityType<? extends MobEntity> type = TYPES.get(RANDOM.nextInt(TYPES.size()));
                MobEntity mob = com.mixaold.burmalda.util.BurmaldaCompat.create(type, world);
                if (mob == null) continue;
                double ox = (RANDOM.nextDouble() - 0.5) * 10;
                double oz = (RANDOM.nextDouble() - 0.5) * 10;
                mob.setPosition(player.getX() + ox, player.getY(), player.getZ() + oz);
                mob.setTarget(player);
                world.spawnEntity(mob);
            }

            // 10% chance to spawn a Wither per player per wave
            if (RANDOM.nextFloat() < 0.10f) {
                spawnWither(world, player);
            }
        }
    }

    private void spawnWither(ServerWorld world, ServerPlayerEntity player) {
        if (witherSpawnCount >= 2) return;

        WitherEntity wither = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.WITHER, world);
        if (wither == null) return;
        double ox = (RANDOM.nextDouble() - 0.5) * 14;
        double oz = (RANDOM.nextDouble() - 0.5) * 14;
        wither.setPosition(player.getX() + ox, player.getY() + 3, player.getZ() + oz);
        wither.setTarget(player);
        wither.setPersistent();
        world.spawnEntity(wither);
        witherIds.add(wither.getId());
        witherEverSpawned = true;
        witherSpawnCount++;

        net.minecraft.text.Text witherMsg = witherSpawnCount == 2
                ? Text.translatableWithFallback("burmalda.chat.zoopark.wither2", "§4[Zoo] §cWHAT?? ANOTHER ONE?!??!?!?")
                : Text.translatableWithFallback("burmalda.chat.zoopark.wither1", "§4[Zoo] §cWITHER HAS ENTERED THE CHAT. Pray.");
        world.getServer().getPlayerManager().broadcast(witherMsg, false);
    }
}
