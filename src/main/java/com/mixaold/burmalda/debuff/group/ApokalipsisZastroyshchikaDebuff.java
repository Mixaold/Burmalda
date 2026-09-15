package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ApokalipsisZastroyshchikaDebuff extends Debuff {

    private static final String[] NAME_KEYS = {
        "burmalda.apokalipsis_zastroyshchika.name.0", "burmalda.apokalipsis_zastroyshchika.name.1",
        "burmalda.apokalipsis_zastroyshchika.name.2", "burmalda.apokalipsis_zastroyshchika.name.3",
        "burmalda.apokalipsis_zastroyshchika.name.4", "burmalda.apokalipsis_zastroyshchika.name.5",
        "burmalda.apokalipsis_zastroyshchika.name.6", "burmalda.apokalipsis_zastroyshchika.name.7"
    };
    private static final String[] NAME_FALLBACKS = {
        "Sunny", "Promising", "Cozy Courtyard",
        "Horizon", "New Quarters", "Central",
        "Rainbow", "Northern"
    };

    // Палитры: [стена чётных этажей, стена нечётных этажей]
    private static final Block[][] PALETTES = {
        { Blocks.GRAY_CONCRETE,     Blocks.POLISHED_DEEPSLATE   },
        { Blocks.SMOOTH_SANDSTONE,  Blocks.CUT_SANDSTONE        },
        { Blocks.WHITE_CONCRETE,    Blocks.QUARTZ_BLOCK         },
        { Blocks.BLACKSTONE,        Blocks.POLISHED_BLACKSTONE  },
        { Blocks.BLUE_CONCRETE,     Blocks.LIGHT_BLUE_CONCRETE  },
    };

    private static final int SPAWN_INTERVAL = 800;
    private static final int MAX_BUILDINGS  = 5;
    private static final int SPAWN_RADIUS   = 55;
    // Min gap (Chebyshev, centre-to-centre) between buildings so 20-wide blocks never overlap.
    private static final int MIN_GAP        = 30;
    private static final int W              = 20;
    private static final int FLOORS         = 5;
    private static final int FH             = 4;
    private static final Random RANDOM      = new Random();

    private int buildingsSpawned = 0;
    private final List<BlockPos> builtOrigins = new ArrayList<>();

    public ApokalipsisZastroyshchikaDebuff() {
        super("apokalipsis_zastroyshchika", "Apocalypse Developer",
                "The city is growing. Nobody asked you.",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        buildingsSpawned = 0;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        spawnNearPlayer(server, players.get(RANDOM.nextInt(players.size())));
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.apokalipsis_zastroyshchika.start",
                        "§7[GC «EcoPark»] §fBeginning of comprehensive development of the territory."), false);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (buildingsSpawned >= MAX_BUILDINGS) return;
        if (ticksActive % SPAWN_INTERVAL != 0) return;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        spawnNearPlayer(server, players.get(RANDOM.nextInt(players.size())));
    }

    @Override
    public void onGroupEnd(MinecraftServer server) { }

    // ─── Спавн здания ────────────────────────────────────────────────────────

    private void spawnNearPlayer(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;

        // Pick a spot far enough from every existing building so they never spawn into each other.
        int cx = 0, cz = 0, cy = 0;
        boolean found = false;
        for (int attempt = 0; attempt < 32; attempt++) {
            int tx = (int) player.getX() + (RANDOM.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS);
            int tz = (int) player.getZ() + (RANDOM.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS);
            if (!isSpaced(tx, tz)) continue;
            int ty = getFloorY(world, tx, tz);
            if (ty < 5 || ty > 250) continue;
            cx = tx; cz = tz; cy = ty; found = true; break;
        }
        if (!found) return; // no non-overlapping spot nearby — skip this one

        int palette = RANDOM.nextInt(PALETTES.length);
        BlockPos origin = new BlockPos(cx - 10, cy, cz - 10);
        buildBuilding(world, origin, palette);

        connectRoads(world, origin);

        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        // Жители в лобби
        spawnResident(world, new BlockPos(ox + 9, oy + 1, oz + 8));
        spawnResident(world, new BlockPos(ox + 11, oy + 1, oz + 14));
        spawnResident(world, new BlockPos(ox + 5, oy + 1, oz + 12));

        // Жители на верхних этажах
        for (int floor = 1; floor < 5; floor++) {
            int roomY = oy + floor * FH + 1;
            spawnResident(world, new BlockPos(ox + 5 + RANDOM.nextInt(9), roomY, oz + 5 + RANDOM.nextInt(9)));
            if (RANDOM.nextBoolean())
                spawnResident(world, new BlockPos(ox + 5 + RANDOM.nextInt(9), roomY, oz + 5 + RANDOM.nextInt(9)));
        }

        world.playSound(null, cx, cy, cz, SoundEvents.BLOCK_ANVIL_LAND,     SoundCategory.BLOCKS, 5.0f, 0.3f);
        world.playSound(null, cx, cy, cz, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 3.0f, 0.5f);

        buildingsSpawned++;
        int nameIdx = RANDOM.nextInt(NAME_KEYS.length);
        Text name = Text.translatableWithFallback(NAME_KEYS[nameIdx], NAME_FALLBACKS[nameIdx]);
        Text msg = buildingsSpawned == 3
                ? Text.translatableWithFallback("burmalda.chat.apokalipsis_zastroyshchika.milestone",
                        "§c[Developer] §fWHERE THE TREES USED TO BE — NOW THERE'S US.")
                : Text.translatableWithFallback("burmalda.chat.apokalipsis_zastroyshchika.complex_done",
                        "§7[EcoPark Development] §fResidential complex «%s» delivered. Apartments from 2.5M/m².", name);
        server.getPlayerManager().broadcast(msg, false);
    }

    // True if (cx,cz) is at least MIN_GAP (Chebyshev) from every already-built building centre.
    private boolean isSpaced(int cx, int cz) {
        for (BlockPos o : builtOrigins) {
            int ecx = o.getX() + 10, ecz = o.getZ() + 10;
            if (Math.max(Math.abs(cx - ecx), Math.abs(cz - ecz)) < MIN_GAP) return false;
        }
        return true;
    }

    private void spawnResident(ServerWorld world, BlockPos pos) {
        VillagerEntity v = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.VILLAGER, world);
        if (v == null) return;
        v.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        v.setCustomName(Text.translatableWithFallback("burmalda.apokalipsis_zastroyshchika.resident", "§7[Resident]"));
        v.setCustomNameVisible(false);
        v.setPersistent();
        world.spawnEntity(v);
    }

    // ─── Дороги между зданиями ────────────────────────────────────────────────

    private void connectRoads(ServerWorld world, BlockPos newOrigin) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos prev : builtOrigins) {
            double d = Math.pow(newOrigin.getX() - prev.getX(), 2)
                     + Math.pow(newOrigin.getZ() - prev.getZ(), 2);
            if (d < nearestDist) { nearestDist = d; nearest = prev; }
        }
        builtOrigins.add(newOrigin);
        if (nearest == null) return;

        int[] a = closestExit(newOrigin, nearest);
        int[] b = closestExit(nearest, newOrigin);
        buildRoad(world, a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    private int[] closestExit(BlockPos building, BlockPos target) {
        int ox = building.getX(), oy = building.getY(), oz = building.getZ();
        int tx = target.getX() + W / 2, tz = target.getZ() + W / 2;
        // 2 блока за стеной, чтобы не задеть козырьки
        int[][] exits = {
            {ox + 10, oy, oz + W + 2},
            {ox + 10, oy, oz - 3},
            {ox + W + 2, oy, oz + 10},
        };
        int[] best = exits[0];
        double bestD = Double.MAX_VALUE;
        for (int[] e : exits) {
            double d = Math.pow(e[0] - tx, 2) + Math.pow(e[2] - tz, 2);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private void buildRoad(ServerWorld world, int x1, int y1, int z1, int x2, int y2, int z2) {
        int xLen  = Math.abs(x2 - x1);
        int zLen  = Math.abs(z2 - z1);
        int total = xLen + zLen;
        if (total == 0) return;

        int stepX = x2 >= x1 ? 1 : -1;
        int stepZ = z2 >= z1 ? 1 : -1;
        int step  = 0;

        for (int i = 0; i <= xLen; i++, step++) {
            int iy = y1 + (y2 - y1) * step / total;
            placeRoadSlice(world, x1 + i * stepX, z1, iy, false, step);
        }
        for (int i = 1; i <= zLen; i++, step++) {
            int iy = y1 + (y2 - y1) * step / total;
            placeRoadSlice(world, x2, z1 + i * stepZ, iy, true, step);
        }
    }

    private void placeRoadSlice(ServerWorld world, int cx, int cz, int targetY, boolean crossX, int step) {
        Block center = (step % 6 == 0) ? Blocks.WHITE_CONCRETE : Blocks.GRAY_CONCRETE;
        placeRoadColumn(world, cx,   cz,   targetY, center);
        if (crossX) {
            placeRoadColumn(world, cx - 1, cz, targetY, Blocks.GRAY_CONCRETE);
            placeRoadColumn(world, cx + 1, cz, targetY, Blocks.GRAY_CONCRETE);
            placeRoadColumn(world, cx - 2, cz, targetY, Blocks.LIGHT_GRAY_CONCRETE);
            placeRoadColumn(world, cx + 2, cz, targetY, Blocks.LIGHT_GRAY_CONCRETE);
            if (step % 12 == 6) { placeLamp(world, cx-3, cz, targetY); placeLamp(world, cx+3, cz, targetY); }
            if (step % 9  == 3)   placeTrashBin(world, cx+2, cz, targetY);
            if (step % 18 == 9)   placeStreetTree(world, cx-3, cz, targetY);
        } else {
            placeRoadColumn(world, cx, cz - 1, targetY, Blocks.GRAY_CONCRETE);
            placeRoadColumn(world, cx, cz + 1, targetY, Blocks.GRAY_CONCRETE);
            placeRoadColumn(world, cx, cz - 2, targetY, Blocks.LIGHT_GRAY_CONCRETE);
            placeRoadColumn(world, cx, cz + 2, targetY, Blocks.LIGHT_GRAY_CONCRETE);
            if (step % 12 == 6) { placeLamp(world, cx, cz-3, targetY); placeLamp(world, cx, cz+3, targetY); }
            if (step % 9  == 3)   placeTrashBin(world, cx, cz+2, targetY);
            if (step % 18 == 9)   placeStreetTree(world, cx, cz-3, targetY);
        }
    }

    private void placeRoadColumn(ServerWorld world, int x, int z, int targetY, Block mat) {
        if (isNearBuilding(x, z)) return;

        int groundY = getTrueGroundY(world, x, z);
        int floorY  = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
        if (groundY < 5 || groundY > 250) return;

        int walkAt     = Math.max(groundY, targetY);
        int roadBlockY = walkAt - 1;

        // Вырубить деревья, грибы и растения в колонке
        clearVegetation(world, x, groundY, z, roadBlockY);

        set(world, x, roadBlockY, z, mat.getDefaultState());

        // Подпорки через ямы и воду
        for (int y = floorY; y < roadBlockY; y++)
            set(world, x, y, z, Blocks.SMOOTH_STONE.getDefaultState());
    }

    private void clearVegetation(ServerWorld world, int x, int startY, int z, int roadBlockY) {
        int consecAir = 0;
        for (int y = startY; y < startY + 60; y++) {
            BlockState bs = world.getBlockState(new BlockPos(x, y, z));
            if (bs.isAir()) {
                if (y > roadBlockY) consecAir++;
                if (consecAir >= 3) break;
                continue;
            }
            consecAir = 0;
            if (isVegetation(bs))
                set(world, x, y, z, Blocks.AIR.getDefaultState());
            else if (y > roadBlockY)
                break; // твёрдый блок выше уровня дороги — не трогаем
        }
    }

    private boolean isVegetation(BlockState bs) {
        Block b = bs.getBlock();
        return bs.isIn(BlockTags.LOGS)
            || bs.isIn(BlockTags.LEAVES)
            || bs.isIn(BlockTags.FLOWERS)
            || b == Blocks.RED_MUSHROOM_BLOCK || b == Blocks.BROWN_MUSHROOM_BLOCK
            || b == Blocks.MUSHROOM_STEM
            || b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS
            || b == Blocks.FERN  || b == Blocks.LARGE_FERN
            || b == Blocks.VINE  || b == Blocks.DEAD_BUSH
            || b == Blocks.BAMBOO || b == Blocks.SUGAR_CANE
            || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.LILY_PAD;
    }

    private int getTrueGroundY(ServerWorld world, int x, int z) {
        int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        for (int y = top - 1; y > 10; y--) {
            BlockState bs = world.getBlockState(new BlockPos(x, y, z));
            if (!bs.isAir() && !isVegetation(bs))
                return y + 1;
        }
        return world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
    }

    private boolean isNearBuilding(int x, int z) {
        for (BlockPos o : builtOrigins)
            if (x >= o.getX() - 1 && x < o.getX() + W + 1
             && z >= o.getZ() - 1 && z < o.getZ() + W + 1) return true;
        return false;
    }

    private void placeLamp(ServerWorld world, int x, int z, int targetY) {
        if (isNearBuilding(x, z)) return;
        int groundY = getTrueGroundY(world, x, z);
        if (groundY < 5 || groundY > 250) return;
        int base = Math.max(groundY, targetY) - 1;
        clearVegetation(world, x, groundY, z, base);
        set(world, x, base+1, z, Blocks.OAK_FENCE.getDefaultState());
        set(world, x, base+2, z, Blocks.OAK_FENCE.getDefaultState());
        set(world, x, base+3, z, Blocks.OAK_FENCE.getDefaultState());
        set(world, x, base+4, z, Blocks.SEA_LANTERN.getDefaultState());
    }

    private void placeTrashBin(ServerWorld world, int x, int z, int targetY) {
        if (isNearBuilding(x, z)) return;
        int groundY = getTrueGroundY(world, x, z);
        if (groundY < 5 || groundY > 250) return;
        int base = Math.max(groundY, targetY) - 1;
        set(world, x, base+1, z, Blocks.BARREL.getDefaultState());
    }

    private void placeStreetTree(ServerWorld world, int x, int z, int targetY) {
        if (isNearBuilding(x, z)) return;
        int groundY = getTrueGroundY(world, x, z);
        if (groundY < 5 || groundY > 250) return;
        int base = Math.max(groundY, targetY) - 1;
        clearVegetation(world, x, groundY, z, base);
        BlockState leaf = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        set(world, x, base+1, z, Blocks.OAK_LOG.getDefaultState());
        set(world, x, base+2, z, Blocks.OAK_LOG.getDefaultState());
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                set(world, x+dx, base+3, z+dz, leaf);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0) set(world, x+dx, base+4, z+dz, leaf);
        set(world, x, base+4, z, leaf);
    }

    // ─── Постройка здания ─────────────────────────────────────────────────────

    private void buildBuilding(ServerWorld world, BlockPos origin, int palette) {
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        Block wallA   = PALETTES[palette][0];
        Block wallB   = PALETTES[palette][1];
        Block roofMat = PALETTES[palette][0];

        // Шахта лестницы: x=1, z=2..5 — здесь нет плит этажей, проходит насквозь
        // Подход с севера (z=1), идёшь на юг (+z) — ступени поднимают на каждый этаж
        // (FACING SOUTH = подниматься на юг, +z — игрок подходит с севера, идёт на юг)

        // Укрепить основание
        for (int fx = 0; fx < W; fx++)
            for (int fz = 0; fz < W; fz++)
                for (int dy = -3; dy <= -1; dy++) {
                    BlockState cur = world.getBlockState(new BlockPos(ox+fx, oy+dy, oz+fz));
                    if (cur.isAir() || !cur.getFluidState().isEmpty())
                        set(world, ox+fx, oy+dy, oz+fz, Blocks.GRAY_CONCRETE.getDefaultState());
                }

        // Расчистить объём
        for (int fx = 0; fx < W; fx++)
            for (int fz = 0; fz < W; fz++)
                for (int dy = 0; dy <= 28; dy++)
                    set(world, ox+fx, oy+dy, oz+fz, Blocks.AIR.getDefaultState());

        // ── Этажи ──────────────────────────────────────────────────────────────
        for (int floor = 0; floor < FLOORS; floor++) {
            int floorY = oy + floor * FH;
            Block wall = (floor % 2 == 0) ? wallA : wallB;

            // Плита этажа — пропускаем шахту (x=1, z=2..5)
            for (int fx = 0; fx < W; fx++)
                for (int fz = 0; fz < W; fz++) {
                    if (fx == 1 && fz >= 2 && fz <= 5) continue; // шахта лестницы
                    set(world, ox+fx, floorY, oz+fz, Blocks.SMOOTH_STONE.getDefaultState());
                }

            // Внешние стены (3 блока высота)
            for (int dy = 1; dy <= 3; dy++) {
                int wy = floorY + dy;
                for (int i = 0; i < W; i++) {
                    set(world, ox+i,   wy, oz,     wall.getDefaultState());
                    set(world, ox+i,   wy, oz+W-1, wall.getDefaultState());
                    set(world, ox,     wy, oz+i,   wall.getDefaultState());
                    set(world, ox+W-1, wy, oz+i,   wall.getDefaultState());
                }
            }

            // Окна: 4 секции × 2 блока на всех 4 стенах
            int[][] wins = {{2,3},{7,8},{12,13},{17,18}};
            for (int[] wp : wins) {
                for (int i = wp[0]; i <= wp[1]; i++) {
                    set(world, ox+i, floorY+1, oz,     Blocks.GLASS.getDefaultState());
                    set(world, ox+i, floorY+2, oz,     Blocks.GLASS.getDefaultState());
                    set(world, ox+i, floorY+1, oz+W-1, Blocks.GLASS.getDefaultState());
                    set(world, ox+i, floorY+2, oz+W-1, Blocks.GLASS.getDefaultState());
                    set(world, ox,     floorY+1, oz+i, Blocks.GLASS.getDefaultState());
                    set(world, ox,     floorY+2, oz+i, Blocks.GLASS.getDefaultState());
                    set(world, ox+W-1, floorY+1, oz+i, Blocks.GLASS.getDefaultState());
                    set(world, ox+W-1, floorY+2, oz+i, Blocks.GLASS.getDefaultState());
                }
            }

            if (floor == 0) {
                // 3 входа: Юг x=7-12, Север x=7-12, Восток z=7-12
                for (int dx = 7; dx <= 12; dx++)
                    for (int dy = 1; dy <= 3; dy++) {
                        set(world, ox+dx, floorY+dy, oz+W-1, Blocks.AIR.getDefaultState());
                        set(world, ox+dx, floorY+dy, oz,     Blocks.AIR.getDefaultState());
                    }
                for (int dz = 7; dz <= 12; dz++)
                    for (int dy = 1; dy <= 3; dy++)
                        set(world, ox+W-1, floorY+dy, oz+dz, Blocks.AIR.getDefaultState());

                // Декоративные пилоны у входов (wallB — контрастный материал)
                for (int dy = 1; dy <= 3; dy++) {
                    set(world, ox+6,   floorY+dy, oz+W-1, wallB.getDefaultState());
                    set(world, ox+13,  floorY+dy, oz+W-1, wallB.getDefaultState());
                    set(world, ox+6,   floorY+dy, oz,     wallB.getDefaultState());
                    set(world, ox+13,  floorY+dy, oz,     wallB.getDefaultState());
                    set(world, ox+W-1, floorY+dy, oz+6,   wallB.getDefaultState());
                    set(world, ox+W-1, floorY+dy, oz+13,  wallB.getDefaultState());
                }
                // Фонари у входов (внутри, у пилонов)
                set(world, ox+6,   floorY+3, oz+W-2, Blocks.LANTERN.getDefaultState());
                set(world, ox+13,  floorY+3, oz+W-2, Blocks.LANTERN.getDefaultState());
                set(world, ox+6,   floorY+3, oz+1,   Blocks.LANTERN.getDefaultState());
                set(world, ox+13,  floorY+3, oz+1,   Blocks.LANTERN.getDefaultState());
                set(world, ox+W-2, floorY+3, oz+6,   Blocks.LANTERN.getDefaultState());
                set(world, ox+W-2, floorY+3, oz+13,  Blocks.LANTERN.getDefaultState());
                // Козырёк над входами (снаружи здания, 1 блок наружу)
                for (int dx = 7; dx <= 12; dx++) {
                    set(world, ox+dx, floorY+4, oz+W, Blocks.OAK_SLAB.getDefaultState());
                    set(world, ox+dx, floorY+4, oz-1, Blocks.OAK_SLAB.getDefaultState());
                }
                for (int dz = 7; dz <= 12; dz++)
                    set(world, ox+W, floorY+4, oz+dz, Blocks.OAK_SLAB.getDefaultState());

                // ── Лобби ──────────────────────────────────────────────────────
                for (int fx = 1; fx < W-1; fx++)
                    for (int fz = 1; fz < W-1; fz++) {
                        if (fx == 1 && fz >= 2 && fz <= 5) continue;
                        set(world, ox+fx, floorY, oz+fz, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
                    }
                // Ресепшн
                set(world, ox+6,  floorY+1, oz+4, Blocks.BOOKSHELF.getDefaultState());
                set(world, ox+7,  floorY+1, oz+4, Blocks.BOOKSHELF.getDefaultState());
                set(world, ox+8,  floorY+1, oz+4, Blocks.BOOKSHELF.getDefaultState());
                set(world, ox+13, floorY+1, oz+4, Blocks.BOOKSHELF.getDefaultState());
                set(world, ox+14, floorY+1, oz+4, Blocks.BOOKSHELF.getDefaultState());
                set(world, ox+9,  floorY+1, oz+4, Blocks.CRAFTING_TABLE.getDefaultState());
                set(world, ox+10, floorY+1, oz+4, Blocks.CRAFTING_TABLE.getDefaultState());
                // Столы ожидания
                set(world, ox+5,  floorY+1, oz+10, Blocks.OAK_FENCE.getDefaultState());
                set(world, ox+5,  floorY+2, oz+10, Blocks.OAK_SLAB.getDefaultState());
                set(world, ox+14, floorY+1, oz+10, Blocks.OAK_FENCE.getDefaultState());
                set(world, ox+14, floorY+2, oz+10, Blocks.OAK_SLAB.getDefaultState());
                // Хранилище
                set(world, ox+5,  floorY+1, oz+15, Blocks.BARREL.getDefaultState());
                set(world, ox+6,  floorY+1, oz+15, Blocks.BARREL.getDefaultState());
                set(world, ox+14, floorY+1, oz+15, Blocks.BARREL.getDefaultState());
                set(world, ox+15, floorY+1, oz+15, Blocks.BARREL.getDefaultState());
                // Фонари лобби
                set(world, ox+5,  floorY+3, oz+8,  Blocks.LANTERN.getDefaultState());
                set(world, ox+14, floorY+3, oz+8,  Blocks.LANTERN.getDefaultState());
                set(world, ox+9,  floorY+3, oz+14, Blocks.LANTERN.getDefaultState());
            } else {
                // Коридорные фонари (пентхаус — морские)
                for (int fx = 5; fx <= 15; fx += 5)
                    set(world, ox+fx, floorY+3, oz+9,
                            (floor == 4) ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.LANTERN.getDefaultState());

                switch (floor) {
                    case 1: // Жилой: шкафы-стеллажи, кухни, сундуки
                        for (int fx = 3; fx <= 7; fx++)
                            set(world, ox+fx, floorY+1, oz+2, Blocks.BOOKSHELF.getDefaultState());
                        for (int fx = 12; fx <= 17; fx++)
                            set(world, ox+fx, floorY+1, oz+2, Blocks.BOOKSHELF.getDefaultState());
                        set(world, ox+3,  floorY+1, oz+8, Blocks.CRAFTING_TABLE.getDefaultState());
                        set(world, ox+4,  floorY+1, oz+8, Blocks.BARREL.getDefaultState());
                        set(world, ox+5,  floorY+1, oz+8, Blocks.BARREL.getDefaultState());
                        set(world, ox+15, floorY+1, oz+8, Blocks.CRAFTING_TABLE.getDefaultState());
                        set(world, ox+16, floorY+1, oz+8, Blocks.BARREL.getDefaultState());
                        set(world, ox+14, floorY+1, oz+8, Blocks.BARREL.getDefaultState());
                        set(world, ox+3,  floorY+1, oz+14, Blocks.OAK_FENCE.getDefaultState());
                        set(world, ox+3,  floorY+2, oz+14, Blocks.OAK_SLAB.getDefaultState());
                        set(world, ox+16, floorY+1, oz+14, Blocks.OAK_FENCE.getDefaultState());
                        set(world, ox+16, floorY+2, oz+14, Blocks.OAK_SLAB.getDefaultState());
                        set(world, ox+8,  floorY+1, oz+16, Blocks.CHEST.getDefaultState());
                        set(world, ox+9,  floorY+1, oz+16, Blocks.CHEST.getDefaultState());
                        set(world, ox+10, floorY+1, oz+16, Blocks.CHEST.getDefaultState());
                        set(world, ox+11, floorY+1, oz+16, Blocks.CHEST.getDefaultState());
                        set(world, ox+3,  floorY+3, oz+3,  Blocks.LANTERN.getDefaultState());
                        set(world, ox+16, floorY+3, oz+3,  Blocks.LANTERN.getDefaultState());
                        set(world, ox+3,  floorY+3, oz+16, Blocks.LANTERN.getDefaultState());
                        set(world, ox+16, floorY+3, oz+16, Blocks.LANTERN.getDefaultState());
                        break;
                    case 2: // Офисный: рабочие столы, архивные полки, переговорная
                        for (int fx = 3; fx <= 7; fx++) {
                            set(world, ox+fx, floorY+1, oz+5,  Blocks.CRAFTING_TABLE.getDefaultState());
                            set(world, ox+fx, floorY+1, oz+10, Blocks.CRAFTING_TABLE.getDefaultState());
                            set(world, ox+fx, floorY+1, oz+15, Blocks.CRAFTING_TABLE.getDefaultState());
                        }
                        for (int fx = 12; fx <= 16; fx++) {
                            set(world, ox+fx, floorY+1, oz+5,  Blocks.CRAFTING_TABLE.getDefaultState());
                            set(world, ox+fx, floorY+1, oz+10, Blocks.CRAFTING_TABLE.getDefaultState());
                            set(world, ox+fx, floorY+1, oz+15, Blocks.CRAFTING_TABLE.getDefaultState());
                        }
                        for (int fz = 3; fz <= 16; fz++) {
                            set(world, ox+17, floorY+1, oz+fz, Blocks.BOOKSHELF.getDefaultState());
                            set(world, ox+17, floorY+2, oz+fz, Blocks.BOOKSHELF.getDefaultState());
                        }
                        set(world, ox+3, floorY+1, oz+3,  Blocks.CHEST.getDefaultState());
                        set(world, ox+3, floorY+1, oz+4,  Blocks.CHEST.getDefaultState());
                        set(world, ox+3, floorY+1, oz+16, Blocks.CHEST.getDefaultState());
                        set(world, ox+3, floorY+1, oz+17, Blocks.CHEST.getDefaultState());
                        // Переговорный стол
                        set(world, ox+9,  floorY+1, oz+9, Blocks.OAK_FENCE.getDefaultState());
                        set(world, ox+9,  floorY+2, oz+9, Blocks.OAK_SLAB.getDefaultState());
                        set(world, ox+10, floorY+1, oz+9, Blocks.OAK_FENCE.getDefaultState());
                        set(world, ox+10, floorY+2, oz+9, Blocks.OAK_SLAB.getDefaultState());
                        set(world, ox+9,  floorY+3, oz+9, Blocks.SEA_LANTERN.getDefaultState());
                        break;
                    case 3: // Склад/мастерская: бочки стопками, печи, верстаки
                        for (int fz = 3; fz <= 16; fz += 2) {
                            set(world, ox+3,  floorY+1, oz+fz, Blocks.BARREL.getDefaultState());
                            set(world, ox+4,  floorY+1, oz+fz, Blocks.BARREL.getDefaultState());
                            set(world, ox+3,  floorY+2, oz+fz, Blocks.BARREL.getDefaultState());
                            set(world, ox+4,  floorY+2, oz+fz, Blocks.BARREL.getDefaultState());
                            set(world, ox+15, floorY+1, oz+fz, Blocks.BARREL.getDefaultState());
                            set(world, ox+16, floorY+1, oz+fz, Blocks.BARREL.getDefaultState());
                            set(world, ox+15, floorY+2, oz+fz, Blocks.BARREL.getDefaultState());
                            set(world, ox+16, floorY+2, oz+fz, Blocks.BARREL.getDefaultState());
                        }
                        set(world, ox+7,  floorY+1, oz+5, Blocks.FURNACE.getDefaultState());
                        set(world, ox+8,  floorY+1, oz+5, Blocks.FURNACE.getDefaultState());
                        set(world, ox+12, floorY+1, oz+5, Blocks.FURNACE.getDefaultState());
                        set(world, ox+13, floorY+1, oz+5, Blocks.FURNACE.getDefaultState());
                        set(world, ox+9,  floorY+1, oz+14, Blocks.CRAFTING_TABLE.getDefaultState());
                        set(world, ox+10, floorY+1, oz+14, Blocks.CRAFTING_TABLE.getDefaultState());
                        set(world, ox+11, floorY+1, oz+14, Blocks.CRAFTING_TABLE.getDefaultState());
                        set(world, ox+9,  floorY+1, oz+9, Blocks.CHEST.getDefaultState());
                        set(world, ox+10, floorY+1, oz+9, Blocks.CHEST.getDefaultState());
                        set(world, ox+11, floorY+1, oz+9, Blocks.CHEST.getDefaultState());
                        set(world, ox+5,  floorY+3, oz+5,  Blocks.LANTERN.getDefaultState());
                        set(world, ox+14, floorY+3, oz+5,  Blocks.LANTERN.getDefaultState());
                        set(world, ox+5,  floorY+3, oz+14, Blocks.LANTERN.getDefaultState());
                        set(world, ox+14, floorY+3, oz+14, Blocks.LANTERN.getDefaultState());
                        break;
                    case 4: // Пентхаус: золото, изумруды, морские фонари, длинный стол
                        set(world, ox+5,  floorY+3, oz+5,  Blocks.SEA_LANTERN.getDefaultState());
                        set(world, ox+14, floorY+3, oz+5,  Blocks.SEA_LANTERN.getDefaultState());
                        set(world, ox+5,  floorY+3, oz+14, Blocks.SEA_LANTERN.getDefaultState());
                        set(world, ox+14, floorY+3, oz+14, Blocks.SEA_LANTERN.getDefaultState());
                        set(world, ox+9,  floorY+3, oz+5,  Blocks.SEA_LANTERN.getDefaultState());
                        set(world, ox+9,  floorY+3, oz+14, Blocks.SEA_LANTERN.getDefaultState());
                        set(world, ox+3,  floorY+1, oz+3,  Blocks.GOLD_BLOCK.getDefaultState());
                        set(world, ox+16, floorY+1, oz+3,  Blocks.GOLD_BLOCK.getDefaultState());
                        set(world, ox+3,  floorY+1, oz+16, Blocks.GOLD_BLOCK.getDefaultState());
                        set(world, ox+16, floorY+1, oz+16, Blocks.GOLD_BLOCK.getDefaultState());
                        for (int fx = 7; fx <= 13; fx++) {
                            set(world, ox+fx, floorY+1, oz+8, Blocks.OAK_FENCE.getDefaultState());
                            set(world, ox+fx, floorY+2, oz+8, Blocks.OAK_SLAB.getDefaultState());
                        }
                        for (int fx = 7; fx <= 13; fx += 2) {
                            set(world, ox+fx, floorY+1, oz+7, Blocks.OAK_STAIRS.getDefaultState()
                                    .with(StairsBlock.FACING, Direction.SOUTH)
                                    .with(StairsBlock.HALF,   BlockHalf.BOTTOM)
                                    .with(StairsBlock.SHAPE,  StairShape.STRAIGHT));
                            set(world, ox+fx, floorY+1, oz+9, Blocks.OAK_STAIRS.getDefaultState()
                                    .with(StairsBlock.FACING, Direction.NORTH)
                                    .with(StairsBlock.HALF,   BlockHalf.BOTTOM)
                                    .with(StairsBlock.SHAPE,  StairShape.STRAIGHT));
                        }
                        for (int fz = 3; fz <= 7; fz++) {
                            set(world, ox+17, floorY+1, oz+fz, Blocks.BOOKSHELF.getDefaultState());
                            set(world, ox+17, floorY+2, oz+fz, Blocks.BOOKSHELF.getDefaultState());
                        }
                        set(world, ox+9,  floorY+1, oz+14, Blocks.EMERALD_BLOCK.getDefaultState());
                        set(world, ox+10, floorY+1, oz+14, Blocks.EMERALD_BLOCK.getDefaultState());
                        break;
                }
            }
        }

        // ── Крыша ──────────────────────────────────────────────────────────────
        int roofY = oy + FLOORS * FH;
        for (int fx = 0; fx < W; fx++)
            for (int fz = 0; fz < W; fz++)
                set(world, ox+fx, roofY, oz+fz, roofMat.getDefaultState());

        // Ограждение крыши
        for (int i = 0; i < W; i++) {
            set(world, ox+i,   roofY+1, oz,     Blocks.IRON_BARS.getDefaultState());
            set(world, ox+i,   roofY+1, oz+W-1, Blocks.IRON_BARS.getDefaultState());
            set(world, ox,     roofY+1, oz+i,   Blocks.IRON_BARS.getDefaultState());
            set(world, ox+W-1, roofY+1, oz+i,   Blocks.IRON_BARS.getDefaultState());
        }

        // Кондиционеры
        for (int[] ac : new int[][]{{3,3},{9,14},{15,3}})
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++)
                    set(world, ox+ac[0]+dx, roofY+1, oz+ac[1]+dz, Blocks.IRON_BLOCK.getDefaultState());

        // Антенна
        set(world, ox+10, roofY+1, oz+10, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox+10, roofY+2, oz+10, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox+10, roofY+3, oz+10, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox+10, roofY+4, oz+10, Blocks.SEA_LANTERN.getDefaultState());

        // ── Лестничная шахта (размещается ПОСЛЕ крыши) ────────────────────────
        // Шахта: x=1, z=2..5, все полы пропущены.
        // FACING = NORTH → низкая сторона обращена на север, поднимаешься идя на юг (+z)
        // Каждый этаж: 4 ступени по z, ступень i на высоте floorY+i, z=oz+i+1
        //   step1=(floorY+1,z+2), step2=(floorY+2,z+3), step3=(floorY+3,z+4), step4=(floorY+4,z+5)
        // step4 перезаписывает плиту следующего этажа (она там уже пропущена) или крышу (не пропущена, но перезапишется здесь)
        BlockState stair = Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.SOUTH)
                .with(StairsBlock.HALF,   BlockHalf.BOTTOM)
                .with(StairsBlock.SHAPE,  StairShape.STRAIGHT);

        for (int floor = 0; floor < FLOORS; floor++) {
            int floorY = oy + floor * FH;
            set(world, ox+1, floorY+1, oz+2, stair);
            set(world, ox+1, floorY+2, oz+3, stair);
            set(world, ox+1, floorY+3, oz+4, stair);
            set(world, ox+1, floorY+4, oz+5, stair); // выход на следующий этаж / крышу
            // Фонарь у входа в лестницу
            set(world, ox+2, floorY+3, oz+2, Blocks.LANTERN.getDefaultState());
        }

        // Шахта на крыше: держим AIR чтобы игрок мог вылезти
        // (крышный loop залил всё roofMat — нужно снова вырезать проход)
        set(world, ox+1, roofY, oz+2, Blocks.AIR.getDefaultState());
        set(world, ox+1, roofY, oz+3, Blocks.AIR.getDefaultState());
        set(world, ox+1, roofY, oz+4, Blocks.AIR.getDefaultState());
        // oz+5 — стэйр (выход на крышу), уже перезаписан loop выше
    }

    private static int getFloorY(ServerWorld world, int x, int z) {
        if (world.getRegistryKey() == net.minecraft.world.World.NETHER) {
            // В Незере потолок из бедрока на y≈127 — ищем пол сверху вниз
            for (int y = 110; y >= 4; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (!world.getBlockState(p).isAir()
                        && world.getBlockState(p.up()).isAir()
                        && world.getBlockState(p.up(2)).isAir()) {
                    return y + 1;
                }
            }
            return 40;
        }
        return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        BlockPos p = new BlockPos(x, y, z);
        if (w.isInBuildLimit(p)) w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
    }
}
