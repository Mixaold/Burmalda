package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.Random;

public class GromIzMolniyDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int LIGHTNING_COOLDOWN = 600; // 30 seconds
    private int strikeIndex = 0;
    private int strikeCooldown = 0;

    public GromIzMolniyDebuff() {
        super("grom_iz_molniy", "⚡ Thunder Salvo",
                "5 lightning bolts strike in sequence",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        strikeIndex = 0;
        strikeCooldown = 20; // first strike after 1 second
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (strikeCooldown > 0) {
            strikeCooldown--;
            return;
        }

        if (strikeIndex >= 5) {
            // Wait for cooldown before next salvo
            if (ticksActive % LIGHTNING_COOLDOWN == 0) {
                strikeIndex = 0;
                strikeCooldown = 0;
            }
            return;
        }

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> com.mixaold.burmalda.util.BurmaldaCompat.world(p).getRegistryKey() == net.minecraft.world.World.OVERWORLD)
                .toList();
        if (players.isEmpty()) return;

        // Prefer surface players; if all underground — still strike, just aim at surface above them
        List<ServerPlayerEntity> surfacePlayers = new java.util.ArrayList<>();
        for (ServerPlayerEntity p : players) {
            ServerWorld w = com.mixaold.burmalda.util.BurmaldaCompat.world(p);
            int surfY = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                    (int) p.getX(), (int) p.getZ());
            if (p.getY() >= surfY - 5) surfacePlayers.add(p);
        }

        List<ServerPlayerEntity> pool = surfacePlayers.isEmpty() ? players : surfacePlayers;
        ServerPlayerEntity target = pool.get(RANDOM.nextInt(pool.size()));
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(target);

        // Strike position: surface above the chosen target
        double ox = (RANDOM.nextDouble() - 0.5) * 8;
        double oz = (RANDOM.nextDouble() - 0.5) * 8;
        double lx = target.getX() + ox;
        double lz = target.getZ() + oz;
        double ly = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) lx, (int) lz);

        // Single thunder sound at strike position — everyone in range hears it once
        world.playSound(null, lx, ly, lz,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10000f, 0.8f);

        LightningEntity lightning = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.LIGHTNING_BOLT, world);
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(lx, ly, lz);
            world.spawnEntity(lightning);
        }

        BurmaldaAdvancements.trigger(target, BurmaldaAdvancements.LIGHTNING_HIT);
        strikeIndex++;
        strikeCooldown = 15; // 0.75 sec between strikes
    }
}
