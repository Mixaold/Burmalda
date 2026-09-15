package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

public class ChernayaDyraDebuff extends Debuff {

    private static final Random RANDOM = new Random();

    private static final double KILL_RADIUS    = 1.0;
    private static final double GRAVITY_RADIUS = 10.0;
    private static final int SOUND_INTERVAL    = 25;   // ~1.25 sec
    private static final int PARTICLE_INTERVAL = 2;

    private Vec3d holePos = null;

    public ChernayaDyraDebuff() {
        super("chernaya_dyra", "Black Hole",
                "Follows you and absorbs everything nearby",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        holePos = com.mixaold.burmalda.util.BurmaldaCompat.pos(player).add(Math.cos(angle) * 50, 2, Math.sin(angle) * 50);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.chernaya_dyra.start", "§5[◉ Black Hole] §fIt exists. And it's hungry."), false);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        holePos = null;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (holePos == null) return;
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);

        // All players in the same world
        List<ServerPlayerEntity> allPlayers = new java.util.ArrayList<>();
        for (ServerPlayerEntity p : com.mixaold.burmalda.util.BurmaldaCompat.server(player).getPlayerManager().getPlayerList()) {
            if (com.mixaold.burmalda.util.BurmaldaCompat.world(p) == world) allPlayers.add(p);
        }

        // Nearest player first
        Vec3d targetPos = com.mixaold.burmalda.util.BurmaldaCompat.pos(player);
        double dpNearest = holePos.distanceTo(com.mixaold.burmalda.util.BurmaldaCompat.pos(player));
        for (ServerPlayerEntity p : allPlayers) {
            double d = holePos.distanceTo(com.mixaold.burmalda.util.BurmaldaCompat.pos(p));
            if (d < dpNearest) { dpNearest = d; targetPos = com.mixaold.burmalda.util.BurmaldaCompat.pos(p); }
        }

        double minDist = dpNearest;
        if (dpNearest > 80) {
            // Too far — charge STRAIGHT at the nearest player, ignoring mobs, no escaping it.
            minDist = dpNearest; // targetPos already the player
        } else {
            // In range: nearest entity wins. Once it's right on top of the player (~≤15 blocks) a closer
            // mob can steal its focus — the player's chance to slip the hole onto a mob.
            List<LivingEntity> nearbyMobs = world.getEntitiesByClass(LivingEntity.class,
                    Box.of(holePos, 64, 64, 64),
                    e -> e.isAlive() && !(e instanceof ServerPlayerEntity));
            for (LivingEntity m : nearbyMobs) {
                double d = holePos.distanceTo(com.mixaold.burmalda.util.BurmaldaCompat.pos(m));
                if (d < minDist) { minDist = d; targetPos = com.mixaold.burmalda.util.BurmaldaCompat.pos(m); }
            }
        }

        double distToTarget = minDist;
        double speed;
        if      (distToTarget > 100) speed = 0.80;
        else if (distToTarget > 50)  speed = 0.40;
        else if (distToTarget > 20)  speed = 0.18;
        else                         speed = 0.07;

        if (distToTarget > 0.5) {
            Vec3d dir = targetPos.subtract(holePos).normalize();
            holePos = holePos.add(dir.multiply(speed));
        }

        // Gravity pull for ALL nearby players
        for (ServerPlayerEntity p : allPlayers) {
            double d = holePos.distanceTo(com.mixaold.burmalda.util.BurmaldaCompat.pos(p));
            if (d > KILL_RADIUS && d < GRAVITY_RADIUS) {
                double force = (GRAVITY_RADIUS - d) / GRAVITY_RADIUS * 0.08;
                Vec3d pull = holePos.subtract(com.mixaold.burmalda.util.BurmaldaCompat.pos(p)).normalize().multiply(force);
                com.mixaold.burmalda.util.BurmaldaCompat.pushVelocity(p, pull.x, pull.y, pull.z);
            }
        }

        // Loose items on the floor are far lighter than a player — the hole grabs them from much
        // farther out and reels them in hard.
        double itemRadius = GRAVITY_RADIUS * 2.4; // ~24 blocks vs 10 for players
        for (net.minecraft.entity.ItemEntity item : world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                Box.of(holePos, itemRadius * 2, itemRadius * 2, itemRadius * 2), e -> e.isAlive())) {
            Vec3d ip = com.mixaold.burmalda.util.BurmaldaCompat.pos(item);
            double d = holePos.distanceTo(ip);
            if (d <= 1.6) {
                item.discard();                       // reached the hole → devoured
            } else if (d < itemRadius) {
                double force = (itemRadius - d) / itemRadius * 0.22 + 0.03;
                Vec3d pull = holePos.subtract(ip).normalize().multiply(force);
                item.setVelocity(item.getVelocity().multiply(0.6).add(pull));
                com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(item);
            }
        }

        // (No explosion when a player is consumed — they're simply crushed like the mobs below.)

        // Kill nearby mobs/players who touch the hole
        Box killBox = Box.of(holePos, KILL_RADIUS * 2, KILL_RADIUS * 2, KILL_RADIUS * 2);
        List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, killBox,
                e -> e.isAlive());
        for (LivingEntity victim : victims) {
            com.mixaold.burmalda.util.BurmaldaCompat.damage(victim, world.getDamageSources().generic(), victim.getMaxHealth() * 2 + 100);
        }

        // Devour blocks in a radius-2 sphere every 3 ticks (a bit bigger than before)
        if (ticksActive % 3 == 0) {
            BlockPos center = BlockPos.ofFloored(holePos);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 5) continue; // ~radius 2.2 sphere
                        BlockPos bp = center.add(dx, dy, dz);
                        if (!world.isAir(bp)
                                && !world.getBlockState(bp).isOf(Blocks.BEDROCK)
                                && !world.getBlockState(bp).isOf(Blocks.BARRIER)) {
                            world.removeBlock(bp, false);
                        }
                    }
                }
            }
        }

        // Dense black particles — sent to each player with force=true (no 32-block limit)
        if (ticksActive % PARTICLE_INTERVAL == 0) {
            for (ServerPlayerEntity p : allPlayers) {
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p, ParticleTypes.SQUID_INK, true,
                        holePos.x, holePos.y + 0.5, holePos.z,
                        18, 0.55, 0.55, 0.55, 0.02);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p, ParticleTypes.LARGE_SMOKE, true,
                        holePos.x, holePos.y + 0.5, holePos.z,
                        8, 0.40, 0.40, 0.40, 0.0);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p, ParticleTypes.ASH, true,
                        holePos.x, holePos.y + 0.5, holePos.z,
                        10, 0.60, 0.60, 0.60, 0.01);
            }

            // Underwater the swirl is hidden below the surface from anyone standing on top —
            // mark its spot with a dark smoke plume right at the water surface above the hole.
            BlockPos hbp = BlockPos.ofFloored(holePos.x, holePos.y, holePos.z);
            if (!world.getBlockState(hbp).getFluidState().isEmpty()) {
                int sy = hbp.getY();
                int limit = sy + 64;
                while (sy < limit && !world.getBlockState(new BlockPos(hbp.getX(), sy, hbp.getZ())).getFluidState().isEmpty()) sy++;
                for (ServerPlayerEntity p : allPlayers) {
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p, ParticleTypes.LARGE_SMOKE, true,
                            holePos.x, sy + 0.2, holePos.z, 14, 0.55, 0.15, 0.55, 0.03);
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p, ParticleTypes.ASH, true,
                            holePos.x, sy + 0.1, holePos.z, 10, 0.60, 0.10, 0.60, 0.02);
                }
            }
        }

        // Ambient sound
        if (ticksActive % SOUND_INTERVAL == 0) {
            world.playSound(null, holePos.x, holePos.y, holePos.z,
                    SoundEvents.ENTITY_ENDERMAN_AMBIENT,
                    SoundCategory.HOSTILE, 1.8f, 0.25f + RANDOM.nextFloat() * 0.15f);
        }
    }
}
