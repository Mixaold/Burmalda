package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RazlomyDebuff extends Debuff {

    private static final int CRACK_INTERVAL = 500;
    private static final Random RANDOM = new Random();

    private int baseLength = 10;
    private int baseDepth = 20;

    private static final class CrackRecord {
        final double cx, cy, cz, spreadX, spreadZ;
        CrackRecord(double cx, double cy, double cz, double spreadX, double spreadZ) {
            this.cx = cx; this.cy = cy; this.cz = cz;
            this.spreadX = spreadX; this.spreadZ = spreadZ;
        }
    }
    private final List<CrackRecord> cracks = new ArrayList<>();

    public RazlomyDebuff() {
        super("razlomy", "Rifts",
                "The ground beneath you splits open. It only gets worse.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        baseLength = 10;
        baseDepth = 20;
        cracks.clear();
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.razlomy.start",
                "§8[Rifts] §fThe ground beneath you is unstable."), false);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);

        if (ticksActive % CRACK_INTERVAL == CRACK_INTERVAL - 20) {
            spawnWarning(world, player);
        }
        if (ticksActive % CRACK_INTERVAL == 0 && ticksActive > 0) {
            spawnCrack(world, player);
            baseLength = Math.min(baseLength + 3, 30);
            baseDepth = Math.min(baseDepth + 2, 30);
        }

        // Continuous ambient effects — every 3 ticks
        if (ticksActive % 3 == 0 && !cracks.isEmpty()) {
            emitAmbient(world);
        }
        // Big burst every 3 sec
        if (ticksActive % 60 == 0 && !cracks.isEmpty()) {
            emitBurst(world);
        }
    }

    private void spawnWarning(ServerWorld world, ServerPlayerEntity player) {
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        world.playSound(null, px, py, pz, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 2.5f, 0.3f);
        world.playSound(null, px, py, pz, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0f, 0.25f);
        for (ServerPlayerEntity sp : world.getPlayers()) {
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CRIT, true,
                    px, py, pz, 30, 3.5, 0.1, 3.5, 0.25);
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.LARGE_SMOKE, true,
                    px, py + 1, pz, 15, 2.0, 0.5, 2.0, 0.06);
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                    px, py + 0.5, pz, 8, 1.5, 0.0, 1.5, 0.1);
        }
    }

    private void spawnCrack(ServerWorld world, ServerPlayerEntity player) {
        double px = player.getX(), pz = player.getZ();

        int cx = (int) px + RANDOM.nextInt(11) - 5;
        int cz = (int) pz + RANDOM.nextInt(11) - 5;
        int surfY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);

        int length = baseLength + RANDOM.nextInt(6);
        int depth = baseDepth + RANDOM.nextInt(5);
        int width = 4 + RANDOM.nextInt(2);
        boolean axisX = RANDOM.nextBoolean();

        double spreadX = axisX ? length / 2.0 : width / 2.0;
        double spreadZ = axisX ? width / 2.0 : length / 2.0;
        double midX = axisX ? cx + length / 2.0 : cx;
        double midZ = axisX ? cz : cz + length / 2.0;

        for (int step = 0; step < length; step++) {
            int zigzag = (step % 4 == 0) ? RANDOM.nextInt(3) - 1 : 0;

            for (int w = 0; w < width; w++) {
                int bx = axisX ? cx + step : cx + w - width / 2 + zigzag;
                int bz = axisX ? cz + w - width / 2 + zigzag : cz + step;
                boolean isEdge = (w == 0 || w == width - 1);

                for (int d = 0; d < depth; d++) {
                    BlockPos bp = new BlockPos(bx, surfY - 1 - d, bz);
                    if (!world.isInBuildLimit(bp)) continue;

                    if (d == 0) {
                        // Top surface — magma on edges (glows permanently, drips lava naturally)
                        world.setBlockState(bp, isEdge
                                ? Blocks.BLACKSTONE.getDefaultState()
                                : Blocks.AIR.getDefaultState());
                    } else if (d <= 2) {
                        world.setBlockState(bp, isEdge
                                ? Blocks.BLACKSTONE.getDefaultState()
                                : Blocks.AIR.getDefaultState());
                    } else if (d == depth - 1) {
                        world.setBlockState(bp, RANDOM.nextInt(100) < 25
                                ? Blocks.LAVA.getDefaultState()
                                : Blocks.BLACKSTONE.getDefaultState());
                    } else {
                        world.setBlockState(bp, isEdge
                                ? (RANDOM.nextBoolean() ? Blocks.BLACKSTONE.getDefaultState() : Blocks.BASALT.getDefaultState())
                                : Blocks.AIR.getDefaultState());
                    }
                }
            }

            // Per-step particles as crack "tears open" — sweeps along the crack
            double stepX = axisX ? cx + step + 0.5 : cx + 0.5;
            double stepZ = axisX ? cz + 0.5 : cz + step + 0.5;
            for (ServerPlayerEntity sp : world.getPlayers()) {
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CRIT, true,
                        stepX, surfY + 0.5, stepZ, 8, 0.3, 0.8, 0.3, 0.3);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.LARGE_SMOKE, true,
                        stepX, surfY + 0.5, stepZ, 5, 0.4, 0.3, 0.4, 0.05);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                        stepX, surfY + 0.5, stepZ, 3, 0.3, 0.0, 0.3, 0.1);
            }
        }

        // Register for ambient effects (keep max 8 cracks)
        cracks.add(new CrackRecord(midX, surfY, midZ, spreadX, spreadZ));
        if (cracks.size() > 8) cracks.remove(0);

        // Massive formation burst
        world.playSound(null, cx, surfY, cz, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 5.0f, 0.3f);
        world.playSound(null, cx, surfY, cz, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4.0f, 0.4f);
        world.playSound(null, cx + length / 2, surfY, cz, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 3.0f, 0.55f);

        for (ServerPlayerEntity sp : world.getPlayers()) {
            // Giant smoke wall rising from the entire crack length
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                    midX, surfY + 1, midZ, 80, spreadX, 0.2, spreadZ, 0.15);
            // Massive dust cloud
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.LARGE_SMOKE, true,
                    midX, surfY + 2, midZ, 80, spreadX, 3.0, spreadZ, 0.2);
            // Earth/rock debris flying upward
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CRIT, true,
                    midX, surfY + 1, midZ, 60, spreadX, 2.0, spreadZ, 0.5);
            // Explosions spread along crack
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.EXPLOSION_EMITTER, true,
                    midX, surfY + 1, midZ, 3, spreadX * 0.7, 0, spreadZ * 0.7, 0);
            // Fire shooting up
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.FLAME, true,
                    midX, surfY, midZ, 50, spreadX * 0.8, 1.0, spreadZ * 0.8, 0.2);
            // Lava flying
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.LAVA, true,
                    midX, surfY, midZ, 20, spreadX * 0.5, 0.5, spreadZ * 0.5, 0);
            // Dripping lava at edges
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.DRIPPING_LAVA, true,
                    midX, surfY + 1, midZ, 25, spreadX, 0.3, spreadZ, 0);
        }

        player.sendMessage(Text.translatableWithFallback("burmalda.chat.razlomy.another",
                "§8[Rifts] §fAnother one."), true);
    }

    private void emitAmbient(ServerWorld world) {
        for (CrackRecord cr : cracks) {
            double ox = (RANDOM.nextDouble() - 0.5) * cr.spreadX * 2;
            double oz = (RANDOM.nextDouble() - 0.5) * cr.spreadZ * 2;
            for (ServerPlayerEntity sp : world.getPlayers()) {
                // Smoke rising continuously
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                        cr.cx + ox, cr.cy + 0.5, cr.cz + oz,
                        3, 0.2, 0.0, 0.2, 0.09);
                // Fire flicker
                if (RANDOM.nextInt(3) == 0) {
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.FLAME, true,
                            cr.cx + ox * 0.8, cr.cy + 0.3, cr.cz + oz * 0.8,
                            2, 0.25, 0.25, 0.25, 0.06);
                }
                // Lava drip
                if (RANDOM.nextInt(6) == 0) {
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.DRIPPING_LAVA, true,
                            cr.cx + ox, cr.cy + 0.5, cr.cz + oz,
                            2, 0.3, 0.1, 0.3, 0.0);
                }
            }
        }
    }

    private void emitBurst(ServerWorld world) {
        for (CrackRecord cr : cracks) {
            for (ServerPlayerEntity sp : world.getPlayers()) {
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                        cr.cx, cr.cy + 1, cr.cz,
                        25, cr.spreadX, 0.0, cr.spreadZ, 0.12);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.LARGE_SMOKE, true,
                        cr.cx, cr.cy + 1, cr.cz,
                        20, cr.spreadX * 0.8, 1.5, cr.spreadZ * 0.8, 0.1);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.FLAME, true,
                        cr.cx, cr.cy + 0.5, cr.cz,
                        15, cr.spreadX * 0.7, 0.5, cr.spreadZ * 0.7, 0.08);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.LAVA, true,
                        cr.cx, cr.cy + 0.3, cr.cz,
                        8, cr.spreadX * 0.4, 0.2, cr.spreadZ * 0.4, 0);
            }
        }
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        cracks.clear();
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.razlomy.end",
                "§8[Rifts] §fThe cracks remain. The ground doesn't."), false);
    }
}
