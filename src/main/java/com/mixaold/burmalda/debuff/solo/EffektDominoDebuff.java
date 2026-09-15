package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class EffektDominoDebuff extends Debuff {

    private static final int SELF_TRIGGER_INTERVAL = 600; // 30 sec
    private static final int MAX_CHAIN_DEPTH = 8;
    private static final double CHAIN_CHANCE = 0.35;
    private static final Random RANDOM = new Random();

    private static final Set<BlockState> PROTECTED = new HashSet<>();

    static {
        PROTECTED.add(Blocks.BEDROCK.getDefaultState());
        PROTECTED.add(Blocks.OBSIDIAN.getDefaultState());
        PROTECTED.add(Blocks.CRYING_OBSIDIAN.getDefaultState());
    }

    public EffektDominoDebuff() {
        super("effekt_domino", "Domino Effect",
                "Every block you break triggers its neighbors. You are the disaster.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.effekt_domino.start",
                "§c[Domino] §fYou knocked over the first tile. Good luck."), false);
    }

    @Override
    public void onBlockBreak(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (isProtected(state)) return;
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        spreadDomino(world, player, pos, state.getBlock(), 0, new HashSet<>());
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        if (ticksActive % SELF_TRIGGER_INTERVAL != 0 || ticksActive == 0) return;
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);

        // Random block near player triggers spontaneously
        int ox = RANDOM.nextInt(11) - 5;
        int oy = RANDOM.nextInt(5) - 2;
        int oz = RANDOM.nextInt(11) - 5;
        BlockPos trigger = player.getBlockPos().add(ox, oy, oz);
        BlockState state = world.getBlockState(trigger);
        if (!state.isAir() && !isProtected(state)) {
            world.breakBlock(trigger, true);
            spreadDomino(world, player, trigger, state.getBlock(), 0, new HashSet<>());
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.effekt_domino.self_trigger",
                    "§c[Domino] §fSomething just started on its own."), true);
        }
    }

    private void spreadDomino(ServerWorld world, ServerPlayerEntity player,
                               BlockPos origin, net.minecraft.block.Block targetBlock,
                               int depth, Set<BlockPos> visited) {
        if (depth >= MAX_CHAIN_DEPTH) return;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.offset(dir);
                if (visited.contains(neighbor)) continue;
                if (!world.isInBuildLimit(neighbor)) continue;

                BlockState ns = world.getBlockState(neighbor);
                if (ns.getBlock() != targetBlock) continue;
                if (isProtected(ns)) continue;
                if (RANDOM.nextDouble() > CHAIN_CHANCE) continue;

                visited.add(neighbor);
                world.breakBlock(neighbor, true);

                for (ServerPlayerEntity sp : world.getPlayers()) {
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CRIT, true,
                            neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.05);
                }
                world.playSound(null, neighbor.getX(), neighbor.getY(), neighbor.getZ(),
                        SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 0.5f,
                        0.8f + RANDOM.nextFloat() * 0.4f);

                if (visited.size() < MAX_CHAIN_DEPTH * 6) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private boolean isProtected(BlockState state) {
        return state.isOf(Blocks.BEDROCK)
                || state.isOf(Blocks.OBSIDIAN)
                || state.isOf(Blocks.CRYING_OBSIDIAN)
                || state.isOf(Blocks.BARRIER);
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.effekt_domino.end",
                "§c[Domino] §fThe tiles stopped falling. For now."), false);
    }
}
