package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Block;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RemontnikDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final List<Block> BLOCKS = new ArrayList<>();

    private static List<Block> getBlocks() {
        if (BLOCKS.isEmpty()) {
            Registries.BLOCK.stream()
                .filter(b -> {
                    if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) return false;
                    if (b instanceof FluidBlock) return false;
                    if (b == Blocks.WATER || b == Blocks.LAVA) return false;
                    if (b == Blocks.FIRE || b == Blocks.SOUL_FIRE) return false;
                    if (b == Blocks.END_PORTAL || b == Blocks.NETHER_PORTAL || b == Blocks.END_GATEWAY) return false;
                    return true;
                })
                .forEach(BLOCKS::add);
        }
        return BLOCKS;
    }

    private BlockPos lastPos = null;

    public RemontnikDebuff() {
        super("remontnik", "Fate's Repairman",
                "Block under your feet changes to a random one",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        lastPos = player.getBlockPos();
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        lastPos = null;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        BlockPos current = player.getBlockPos();
        if (current.equals(lastPos)) return;
        lastPos = current;

        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        BlockPos below = current.down();
        if (!world.isAir(below)) {
            List<Block> pool = getBlocks();
            world.setBlockState(below, pool.get(RANDOM.nextInt(pool.size())).getDefaultState());
        }
    }
}
