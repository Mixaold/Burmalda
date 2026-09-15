package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MeteoroitnyStormDebuff extends Debuff {

    private static final int SPAWN_INTERVAL = 600;
    private static final int FALL_TICKS     = 27;   // a bit faster descent
    private static final int STEAM_TICKS    = 200;
    private static final int SPAWN_HEIGHT   = 70;
    private static final Random RANDOM      = new Random();

    // Flattened visual sphere: bottom cut at dy=-1, slightly squashed
    private static final int[][] SPHERE_OFFSETS = buildSphereOffsets();

    private static class PendingMeteor {
        final int   impactTick;
        final int   steamEndTick;
        final double x, z;
        final double spawnX, spawnZ;
        final int   groundY;
        final ServerWorld world;
        final List<DisplayEntity.BlockDisplayEntity> entities;
        boolean impacted = false;

        PendingMeteor(int impactTick, int steamEndTick,
                      double x, int groundY, double z,
                      double spawnX, double spawnZ,
                      ServerWorld world, List<DisplayEntity.BlockDisplayEntity> entities) {
            this.impactTick   = impactTick;
            this.steamEndTick = steamEndTick;
            this.x = x; this.groundY = groundY; this.z = z;
            this.spawnX = spawnX; this.spawnZ = spawnZ;
            this.world    = world;
            this.entities = entities;
        }
    }

    private final List<PendingMeteor> pending = new ArrayList<>();

    public MeteoroitnyStormDebuff() {
        super("meteorit_shtorm", "Meteor Storm",
                "NASA didn't warn you. Expect impacts.",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive == 0) {
            pending.clear();
            return;
        }

        if (ticksActive % SPAWN_INTERVAL == 0) {
            // Выбираем только игроков не в Незере
            var eligible = server.getPlayerManager().getPlayerList().stream()
                    .filter(p -> com.mixaold.burmalda.util.BurmaldaCompat.world(p).getRegistryKey() == World.OVERWORLD)
                    .toList();
            if (!eligible.isEmpty()) {
                ServerPlayerEntity target = eligible.get(RANDOM.nextInt(eligible.size()));
                scheduleMeteor(server, target, ticksActive);
            }
        }

        List<PendingMeteor> toRemove = new ArrayList<>();
        for (PendingMeteor pm : pending) {
            if (!pm.impacted) {
                int remaining = pm.impactTick - ticksActive;
                if (remaining > 0) {
                    double progress = 1.0 - (double) remaining / FALL_TICKS;
                    double curX = pm.spawnX + (pm.x - pm.spawnX) * progress;
                    double curZ = pm.spawnZ + (pm.z - pm.spawnZ) * progress;
                    double curY = pm.groundY + SPAWN_HEIGHT * (1.0 - progress);
                    moveSphere(pm, curX, curY, curZ);
                    spawnTrail(pm.world, curX, curY, curZ, remaining);
                } else {
                    pm.impacted = true;
                    removeSphere(pm);
                    impact(pm);
                }
            } else {
                if (ticksActive <= pm.steamEndTick) {
                    if (ticksActive % 4 == 0) {
                        spawnSteam(pm.world, pm.x, pm.groundY, pm.z);
                    }
                } else {
                    toRemove.add(pm);
                }
            }
        }
        pending.removeAll(toRemove);
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        for (PendingMeteor pm : new ArrayList<>(pending)) {
            if (!pm.impacted) {
                removeSphere(pm);
            }
        }
        pending.clear();
    }

    // ─── Планирование ────────────────────────────────────────────────────────────

    private void scheduleMeteor(MinecraftServer server, ServerPlayerEntity target, int ticksActive) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(target);

        double tx, tz;
        if (RANDOM.nextBoolean()) {
            tx = target.getX() + RANDOM.nextInt(17) - 8;
            tz = target.getZ() + RANDOM.nextInt(17) - 8;
        } else {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double dist  = 20 + RANDOM.nextInt(21);
            tx = target.getX() + Math.cos(angle) * dist;
            tz = target.getZ() + Math.sin(angle) * dist;
        }
        int ix = (int) Math.floor(tx);
        int iz = (int) Math.floor(tz);

        // Target the actual ground (under leaves) so the meteor craters the ground — the tree/leaves it
        // fell through are cleared at impact (clearColumnAbove), so it punches straight through, not stops.
        int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ix, iz);
        if (groundY < world.getBottomY() + 5) return;

        int spawnHeight = SPAWN_HEIGHT;

        double spawnX = tx, spawnZ = tz;
        if (RANDOM.nextBoolean()) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double drift = 6 + RANDOM.nextInt(10);
            spawnX = tx + Math.cos(angle) * drift;
            spawnZ = tz + Math.sin(angle) * drift;
        }
        double spawnY = groundY + spawnHeight;

        List<DisplayEntity.BlockDisplayEntity> entities = new ArrayList<>(SPHERE_OFFSETS.length);
        for (int[] off : SPHERE_OFFSETS) {
            DisplayEntity.BlockDisplayEntity de =
                    new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            de.setPosition(spawnX + off[0] - 0.5, spawnY + off[1] - 0.5, spawnZ + off[2] - 0.5);
            de.setBlockState(pickBlock());
            de.setViewRange(64.0f);
            world.spawnEntity(de);
            entities.add(de);
        }

        pending.add(new PendingMeteor(
                ticksActive + FALL_TICKS,
                ticksActive + FALL_TICKS + STEAM_TICKS,
                tx, groundY, tz,
                spawnX, spawnZ,
                world, entities));

        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.meteorit_shtorm.incoming", "§c[☄ Meteor Storm] §fIncoming."), false);

        world.playSound(null, spawnX, spawnY, spawnZ,
                SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.AMBIENT, 3.0f, 0.4f);
    }

    // ─── Перемещение/удаление сферы DisplayEntity ────────────────────────────────

    private void moveSphere(PendingMeteor pm, double cx, double cy, double cz) {
        for (int i = 0; i < pm.entities.size(); i++) {
            DisplayEntity.BlockDisplayEntity e = pm.entities.get(i);
            if (!e.isRemoved()) {
                int[] off = SPHERE_OFFSETS[i];
                e.setPosition(cx + off[0] - 0.5, cy + off[1] - 0.5, cz + off[2] - 0.5);
            }
        }
    }

    private void removeSphere(PendingMeteor pm) {
        for (DisplayEntity.BlockDisplayEntity e : pm.entities) {
            if (!e.isRemoved()) e.remove(DisplayEntity.RemovalReason.DISCARDED);
        }
    }

    // ─── Хвост во время падения ───────────────────────────────────────────────

    private void spawnTrail(ServerWorld world, double x, double y, double z, int remaining) {
        for (int seg = 0; seg < 10; seg++) {
            double ty      = y + seg * 2.5;
            int    count   = Math.max(1, 8 - seg);
            double spread  = 0.15 + seg * 0.08;

            for (ServerPlayerEntity sp : world.getPlayers()) {
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                        x, ty, z, count, spread, 0.05, spread, 0.008);
                if (seg < 4) {
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.LARGE_SMOKE, true,
                            x, ty, z, Math.max(1, 5 - seg), spread * 0.7, 0.05, spread * 0.7, 0.0);
                }
            }
        }

        if (remaining % 5 == 0) {
            world.playSound(null, x, y, z,
                    SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.AMBIENT,
                    1.5f, 0.5f + RANDOM.nextFloat() * 0.3f);
        }
    }

    // ─── Удар ────────────────────────────────────────────────────────────────────

    private void impact(PendingMeteor pm) {
        ServerWorld world = pm.world;
        int ix = (int) Math.floor(pm.x);
        int iz = (int) Math.floor(pm.z);
        double cx = pm.x, cz = pm.z;
        int groundY = pm.groundY;

        if (isBuilding(world, ix, groundY, iz)) {
            punchThroughBuilding(world, ix, groundY, iz);
        } else {
            createNaturalCrater(world, ix, groundY, iz);
        }

        // Clear the tree/leaves the meteor fell through, so it punches straight to the ground
        // instead of getting "stuck" on the canopy or leaving foliage floating overhead.
        clearColumnAbove(world, ix, groundY, iz);

        world.createExplosion(null, cx, groundY, cz, 3.0f, false, World.ExplosionSourceType.TNT);

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                cx, groundY, cz, 3, 1.5, 1.5, 1.5, 0.0);
        world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                cx, groundY + 1, cz, 30, 2.0, 0.5, 2.0, 0.08);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                cx, groundY + 1, cz, 20, 2.5, 1.0, 2.5, 0.04);

        world.playSound(null, cx, groundY, cz,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 3.0f, 0.7f);
    }

    // ─── Умное пробивание ─────────────────────────────────────────────────────────

    /** Blow away the tree/foliage column the meteor fell through (radius 1, up to 30 blocks). */
    private void clearColumnAbove(ServerWorld world, int ix, int groundY, int iz) {
        for (int dy = 1; dy <= 30; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(ix + dx, groundY + dy, iz + dz);
                    BlockState s = world.getBlockState(p);
                    if (s.isAir()) continue;
                    if (s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.SAPLINGS)
                            || s.isIn(BlockTags.FLOWERS) || s.getBlock() == Blocks.VINE)
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
    }

    /** True если в колонне 3×3 под точкой удара есть не-природные блоки (здание). */
    private boolean isBuilding(ServerWorld world, int ix, int groundY, int iz) {
        for (int dy = 0; dy >= -8; dy--)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (!isNaturalBlock(world.getBlockState(new BlockPos(ix + dx, groundY + dy, iz + dz))))
                        return true;
        return false;
    }

    private boolean isNaturalBlock(BlockState state) {
        if (state.isAir()) return true;
        Block b = state.getBlock();
        return state.isIn(BlockTags.BASE_STONE_OVERWORLD)
            || state.isIn(BlockTags.DIRT)
            || state.isIn(BlockTags.SAND)
            || state.isIn(BlockTags.LOGS)
            || state.isIn(BlockTags.LEAVES)
            || state.isIn(BlockTags.COAL_ORES)
            || state.isIn(BlockTags.IRON_ORES)
            || state.isIn(BlockTags.GOLD_ORES)
            || state.isIn(BlockTags.DIAMOND_ORES)
            || b == Blocks.GRAVEL     || b == Blocks.CLAY
            || b == Blocks.GRASS_BLOCK || b == Blocks.SNOW_BLOCK
            || b == Blocks.ICE        || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE
            || b == Blocks.WATER      || b == Blocks.LAVA
            || b == Blocks.SANDSTONE  || b == Blocks.RED_SANDSTONE
            || b == Blocks.NETHERRACK || b == Blocks.BASALT
            || b == Blocks.CALCITE    || b == Blocks.TUFF
            || b == Blocks.BEDROCK    || b == Blocks.MOSS_BLOCK
            || b == Blocks.TERRACOTTA || b == Blocks.WHITE_TERRACOTTA
            || b == Blocks.BROWN_TERRACOTTA || b == Blocks.BLACKSTONE;
    }

    /**
     * Пробивает здание насквозь: чистит шахту 3×3 вниз через все
     * искусственные блоки и воздух, останавливается только когда
     * встречает реальный грунт (камень/земля) — не воздух внутри!
     */
    private void punchThroughBuilding(ServerWorld world, int ix, int groundY, int iz) {
        int bottomY = groundY - 20; // fallback
        for (int dy = -1; dy >= -40; dy--) {
            BlockState center = world.getBlockState(new BlockPos(ix, groundY + dy, iz));

            // Нашли реальный грунт ниже здания — останавливаемся
            if (isActualGround(center) && dy <= -3) {
                bottomY = groundY + dy + 1;
                break;
            }

            // Чистим все не-природные блоки на этом уровне (воздух = пропускаем)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(ix + dx, groundY + dy, iz + dz);
                    BlockState state = world.getBlockState(p);
                    if (!state.isAir() && !isNaturalBlock(state)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
        }
        placeMeteoriteMass(world, ix, bottomY, iz);
    }

    /** Настоящий грунт — не воздух, не постройка. Камень, земля, песок, гравий. */
    private boolean isActualGround(BlockState state) {
        if (state.isAir()) return false;
        Block b = state.getBlock();
        return state.isIn(BlockTags.BASE_STONE_OVERWORLD)
            || state.isIn(BlockTags.DIRT)
            || b == Blocks.GRASS_BLOCK || b == Blocks.GRAVEL
            || b == Blocks.SAND       || b == Blocks.RED_SAND
            || b == Blocks.BEDROCK    || b == Blocks.CLAY;
    }

    /** Кратер в природном рельефе: чаша max 5 блоков вглубь. */
    private void createNaturalCrater(ServerWorld world, int ix, int groundY, int iz) {
        // Эллипсоид: широкий XZ (R=3.5), мелкий Y (R=2.5), только нижняя половина
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = -5; dy <= 0; dy++)
                for (int dz = -4; dz <= 4; dz++) {
                    double nx = dx / 3.5, ny = dy / 2.5, nz = dz / 3.5;
                    if (nx * nx + ny * ny + nz * nz <= 1.0) {
                        BlockPos p = new BlockPos(ix + dx, groundY + dy, iz + dz);
                        if (world.isInBuildLimit(p))
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
        placeMeteoriteMass(world, ix, groundY - 4, iz);
    }

    /** Сплющенная масса метеорита: широкая и плоская, как камень что врезался. */
    private void placeMeteoriteMass(ServerWorld world, int cx, int cy, int cz) {
        // Приплюснутый эллипсоид: R_XZ=3, R_bottom=1, R_top=2
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -3; dz <= 3; dz++) {
                    double rx = dx / 3.0;
                    double ry = dy < 0 ? dy / 1.2 : dy / 2.0;
                    double rz = dz / 3.0;
                    if (rx * rx + ry * ry + rz * rz <= 1.0) {
                        BlockPos p = new BlockPos(cx + dx, cy + dy, cz + dz);
                        if (world.isInBuildLimit(p))
                            world.setBlockState(p, pickBlock(), Block.NOTIFY_LISTENERS);
                    }
                }
    }

    // ─── Пар после удара ─────────────────────────────────────────────────────────

    private void spawnSteam(ServerWorld world, double cx, int groundY, double cz) {
        world.spawnParticles(ParticleTypes.CLOUD,
                cx + RANDOM.nextDouble() * 4 - 2,
                groundY + RANDOM.nextDouble() * 2,
                cz + RANDOM.nextDouble() * 4 - 2,
                1, 0.15, 0.05, 0.15, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE,
                cx + RANDOM.nextDouble() * 3 - 1.5,
                groundY + RANDOM.nextDouble() * 1.5,
                cz + RANDOM.nextDouble() * 3 - 1.5,
                1, 0.1, 0.1, 0.1, 0.0);
    }

    // ─── Блоки ───────────────────────────────────────────────────────────────────

    private BlockState pickBlock() {
        return switch (RANDOM.nextInt(10)) {
            case 0, 1, 2, 3, 4 -> Blocks.BROWN_TERRACOTTA.getDefaultState();
            case 5, 6           -> Blocks.TERRACOTTA.getDefaultState();
            case 7, 8           -> Blocks.BLACKSTONE.getDefaultState();
            default             -> Blocks.GRAVEL.getDefaultState();
        };
    }

    // ─── Формы ───────────────────────────────────────────────────────────────────

    /**
     * Сплющенная визуальная сфера: низ обрезан на dy=-1,
     * нижняя часть слегка сжата — метеорит выглядит как летящий камень, а не шар.
     */
    private static int[][] buildSphereOffsets() {
        List<int[]> list = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    double fy = dy < 0 ? dy * 1.6 : dy;
                    if (dx * dx + fy * fy + dz * dz <= 4.5)
                        list.add(new int[]{dx, dy, dz});
                }
        return list.toArray(new int[0][]);
    }

}
