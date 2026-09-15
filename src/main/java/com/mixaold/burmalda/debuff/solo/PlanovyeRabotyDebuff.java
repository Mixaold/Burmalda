package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class PlanovyeRabotyDebuff extends Debuff {

    private static final int HOLE_INTERVAL = 400; // 20 sec
    private static final int WATER_DURATION = 160; // 8 sec

    private int nextHoleTick = HOLE_INTERVAL;
    private int closeHoleTick = -1;
    private BlockPos holeCenter = null;
    private BlockPos prevHoleCenter = null;
    private final Map<BlockPos, BlockState> savedBlocks = new HashMap<>();
    private int holeCount = 0;

    public PlanovyeRabotyDebuff() {
        super("planovye_raboty", "Scheduled Works",
                "The utility company is digging up the road under you. For the seventh time this month.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        nextHoleTick = HOLE_INTERVAL;
        closeHoleTick = -1;
        holeCenter = null;
        prevHoleCenter = null;
        savedBlocks.clear();
        holeCount = 0;
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.planovye_raboty.start",
                "§e[Utilities] §fScheduled works in progress. We apologize for the inconvenience."), false);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);

        if (ticksActive == nextHoleTick - 20) {
            spawnWarning(world, player);
        }

        if (ticksActive == nextHoleTick) {
            digHoleAndFlood(world, player);
            closeHoleTick = ticksActive + WATER_DURATION;
            nextHoleTick = ticksActive + HOLE_INTERVAL;
            holeCount++;
        }

        if (closeHoleTick > 0 && ticksActive == closeHoleTick) {
            closeHoleAndLayRoad(world, player);
            closeHoleTick = -1;
        }
    }

    private void spawnWarning(ServerWorld world, ServerPlayerEntity player) {
        for (ServerPlayerEntity sp : world.getPlayers()) {
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CRIT, true,
                    player.getX(), player.getY(), player.getZ(), 10, 0.5, 0.2, 0.5, 0.1);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(), SoundCategory.BLOCKS, 1.5f, 1.0f);
    }

    private void digHoleAndFlood(ServerWorld world, ServerPlayerEntity player) {
        BlockPos center = BlockPos.ofFloored(player.getX(), player.getY() - 1, player.getZ());
        holeCenter = center;
        savedBlocks.clear();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos bp = center.add(dx, dy, dz);
                    if (world.isInBuildLimit(bp)) {
                        savedBlocks.put(bp, world.getBlockState(bp));
                        world.setBlockState(bp, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos bp = center.add(dx, -1, dz);
                if (world.isInBuildLimit(bp)) {
                    world.setBlockState(bp, Blocks.WATER.getDefaultState());
                }
            }
        }

        for (ServerPlayerEntity sp : world.getPlayers()) {
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                    player.getX(), player.getY(), player.getZ(), 8, 0.5, 0.5, 0.5, 0.05);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 2.0f, 0.5f);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.planovye_raboty.digging",
                "§e[Utilities] §fCaution! Excavation work."), true);
    }

    private void closeHoleAndLayRoad(ServerWorld world, ServerPlayerEntity player) {
        if (holeCenter == null) return;
        BlockPos center = holeCenter;

        for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) {
            BlockState saved = entry.getValue();
            if (!saved.isAir() && !saved.getFluidState().isStill()) {
                world.setBlockState(entry.getKey(), saved);
            }
        }
        savedBlocks.clear();

        // Lay 3×3 road patch at the hole's own Y level
        int roadY = center.getY();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos roadPos = new BlockPos(center.getX() + dx, roadY, center.getZ() + dz);
                if (world.isInBuildLimit(roadPos)) {
                    world.setBlockState(roadPos, getRoadBlock(dx, 0));
                }
            }
        }

        placeSign(world, center);

        if (holeCount % 3 == 0) {
            placeLampPost(world, center);
        }

        if (prevHoleCenter != null) {
            buildRoadBetween(world, prevHoleCenter, center);
        }
        prevHoleCenter = center;

        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.5f, 1.2f);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.planovye_raboty.hole_closed",
                "§e[Utilities] §fWork completed. Thank you for your patience."), true);
    }

    private BlockState getRoadBlock(int side, int step) {
        if (side == -1 || side == 1) return Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
        if (step % 4 == 0) return Blocks.WHITE_CONCRETE.getDefaultState();
        return Blocks.BLACK_CONCRETE.getDefaultState();
    }

    /** First solid (non-air, non-fluid) block Y at or below startY, so posts can be grounded. */
    private int groundY(ServerWorld world, int x, int z, int startY) {
        int limit = Math.max(world.getBottomY() + 1, startY - 40);
        for (int y = startY; y >= limit; y--) {
            BlockState bs = world.getBlockState(new BlockPos(x, y, z));
            if (!bs.isAir() && bs.getFluidState().isEmpty()) return y;
        }
        return limit;
    }

    private void placeSign(ServerWorld world, BlockPos center) {
        int x = center.getX();
        int z = center.getZ() - 2;
        int roadY = center.getY(); // same level as the road

        // Grounded fence post: build down to solid ground so it never floats over water/holes
        int g = groundY(world, x, z, roadY);
        for (int y = g + 1; y <= roadY + 1; y++) {
            setAt(world, x, y, z, Blocks.OAK_FENCE.getDefaultState());
        }

        BlockPos signPos = new BlockPos(x, roadY + 2, z);
        if (world.isInBuildLimit(signPos)) {
            world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState());
            if (world.getBlockEntity(signPos) instanceof SignBlockEntity signBE) {
                SignText text = signBE.getFrontText()
                        .withMessage(0, Text.translatableWithFallback("burmalda.planovye_raboty.sign.0", "⚠ Works"))
                        .withMessage(1, Text.translatableWithFallback("burmalda.planovye_raboty.sign.1", "in progress"));
                signBE.setText(text, true);
            }
        }
    }

    private void placeLampPost(ServerWorld world, BlockPos center) {
        int x = center.getX() + 3;
        int z = center.getZ();
        int roadY = center.getY();
        int headY = roadY + 5;                 // lamp head height above the road
        int g = groundY(world, x, z, roadY);   // solid ground below

        BlockState pole = Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState();
        // Sleek pole grounded from the floor up to the head — never floats
        for (int y = g + 1; y <= headY; y++) {
            setAt(world, x, y, z, pole);
        }
        // Arm reaching out over the road + a hanging lantern under it = real street lamp
        setAt(world, x - 1, headY, z, Blocks.POLISHED_BLACKSTONE.getDefaultState());
        BlockPos lampPos = new BlockPos(x - 1, headY - 1, z);
        if (world.isInBuildLimit(lampPos)) {
            world.setBlockState(lampPos,
                    Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
        }
    }

    private void setAt(ServerWorld world, int x, int y, int z, BlockState state) {
        BlockPos p = new BlockPos(x, y, z);
        if (world.isInBuildLimit(p)) world.setBlockState(p, state, net.minecraft.block.Block.NOTIFY_LISTENERS);
    }

    private void buildRoadBetween(ServerWorld world, BlockPos from, BlockPos to) {
        int x0 = from.getX(), z0 = from.getZ(), y0 = from.getY();
        int x1 = to.getX(),   z1 = to.getZ(),   y1 = to.getY();
        int adx = Math.abs(x1 - x0), adz = Math.abs(z1 - z0);
        int totalSteps = Math.max(adx, adz);
        int sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        int err = adx - adz;
        int step = 0;
        int cx = x0, cz = z0;

        while (true) {
            // Interpolate Y between the two hole positions
            int ry = (totalSteps == 0) ? y0 : y0 + (y1 - y0) * step / totalSteps;
            boolean majorX = adx >= adz;
            for (int side = -1; side <= 1; side++) {
                int bx = majorX ? cx : cx + side;
                int bz = majorX ? cz + side : cz;
                BlockPos rp = new BlockPos(bx, ry, bz);
                if (world.isInBuildLimit(rp)) {
                    world.setBlockState(rp, getRoadBlock(side, step));
                }
            }
            step++;
            if (cx == x1 && cz == z1) break;
            int e2 = 2 * err;
            if (e2 > -adz) { err -= adz; cx += sx; }
            if (e2 < adx) { err += adx; cz += sz; }
        }
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.planovye_raboty.end",
                "§e[Utilities] §fWork completed. The next one's already the day after tomorrow."), false);
    }
}
