package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Pandora's Box — a big GROUP location event (~50 blocks across). A themed temple holds a LOCKED
 * treasure chest; the key is a lever hidden in one of several detailed huts scattered around the
 * site. Armoured guardians and a captain defend it, environmental traps line the way, a boss-bar
 * counts down, and the temple slowly crumbles as time runs out. Open the chest before the timer.
 */
public class YashchikPandoryDebuff extends Debuff {

    private static final int DEADLINE   = 900;  // 45 seconds
    private static final int BASE_HALF  = 5;    // temple base 11×11
    private static final int TIERS      = 5;
    private static final Random RANDOM  = new Random();

    private static YashchikPandoryDebuff ACTIVE;

    private ServerWorld world;
    private BlockPos chestPos, basePos, leverPos;
    private int cx, cy, cz;            // temple centre / ground
    private UUID hologramUuid;
    private boolean resolved, unlocked;
    private Theme theme;

    private record Theme(Text name, Block main, Block accent, Block alt, Block plank, Block log, Block light) {}
    private static final Theme[] THEMES = {
        new Theme(Text.translatableWithFallback("burmalda.chat.yashchik_pandory.theme_ancient_temple", "Ancient Temple"),   Blocks.STONE_BRICKS,     Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.OAK_PLANKS,    Blocks.OAK_LOG,          Blocks.LANTERN),
        new Theme(Text.translatableWithFallback("burmalda.chat.yashchik_pandory.theme_desert_pyramid", "Desert Pyramid"), Blocks.SANDSTONE,    Blocks.CHISELED_SANDSTONE,    Blocks.CUT_SANDSTONE,      Blocks.SMOOTH_SANDSTONE, Blocks.STRIPPED_OAK_LOG, Blocks.LANTERN),
        new Theme(Text.translatableWithFallback("burmalda.chat.yashchik_pandory.theme_jungle_ruins", "Jungle Ruins"), Blocks.MOSSY_COBBLESTONE,Blocks.COBBLESTONE,           Blocks.MOSSY_STONE_BRICKS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_LOG,       Blocks.LANTERN),
        new Theme(Text.translatableWithFallback("burmalda.chat.yashchik_pandory.theme_ice_altar", "Ice Altar"), Blocks.PACKED_ICE,       Blocks.BLUE_ICE,              Blocks.SNOW_BLOCK,         Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,       Blocks.SEA_LANTERN),
    };

    public YashchikPandoryDebuff() {
        super("yashchik_pandory", "Pandora's Box",
                "Find the key, fight to the temple and open the treasure chest before time runs out",
                DebuffType.GROUP);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────
    @Override
    public void onGroupStart(MinecraftServer server) {
        resolved = false; unlocked = false;
        chestPos = basePos = leverPos = null; hologramUuid = null;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        ServerPlayerEntity anchor = players.get(RANDOM.nextInt(players.size()));
        world = com.mixaold.burmalda.util.BurmaldaCompat.world(anchor);
        ACTIVE = this;
        theme = THEMES[RANDOM.nextInt(THEMES.length)];

        // Centre the location well away from the anchor so nobody spawns inside it.
        double ang = RANDOM.nextDouble() * Math.PI * 2;
        int dist = 28 + RANDOM.nextInt(8);
        cx = (int) Math.floor(anchor.getX() + Math.cos(ang) * dist);
        cz = (int) Math.floor(anchor.getZ() + Math.sin(ang) * dist);
        cy = groundAt(cx, cz, (int) Math.floor(anchor.getY()));
        basePos = new BlockPos(cx, cy, cz);

        chestPos = buildTemple();
        fillChest();
        buildHuts();          // one hides the lever-key
        buildRuins();
        placeTraps();
        spawnGuardians(server);
        spawnHologram();

        world.playSound(null, cx, cy, cz, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 3f, 0.8f);
        server.getPlayerManager().broadcast(Text.translatableWithFallback(
                "burmalda.chat.yashchik_pandory.start",
                "§e[Pandora's Box] §f%s! Find the §6key lever§f in one of the buildings, "
                + "break through to the temple and open the chest!", theme.name()), false);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (resolved || world == null || chestPos == null) return;

        // Treasure aura orbiting the chest
        if (ticksActive % 4 == 0) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                for (int k = 0; k < 3; k++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 1.4 + RANDOM.nextDouble() * 0.6;
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p,
                            unlocked ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE, true,
                            chestPos.getX() + 0.5 + Math.cos(a) * r, chestPos.getY() + 0.2 + RANDOM.nextDouble() * 1.8,
                            chestPos.getZ() + 0.5 + Math.sin(a) * r, 1, 0.03, 0.04, 0.03, 0.0);
                }
            }
        }

        // Timer bar above the hotbar (sent to every player)
        if (ticksActive % 4 == 0) {
            int left = Math.max(0, DEADLINE - ticksActive);
            int sec = left / 20;
            String time = (sec / 60) + ":" + String.format("%02d", sec % 60);
            String key = unlocked ? "burmalda.timer.pandora.unlocked" : "burmalda.timer.pandora.locked";
            int color = unlocked ? 0xFF22CC22 : 0xFFCC2222;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
                BurmaldaNetwork.sendEventTimer(p, key, time, left / (float) DEADLINE, color);
        }

        if (!world.getBlockState(chestPos).isOf(Blocks.CHEST)) { succeed(server); return; }
        if (ticksActive >= DEADLINE) fail(server);
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        cleanup();
    }

    /** Global use-callback hook. Returns true to CANCEL the interaction (locked chest). */
    public static boolean handleUse(BlockPos pos, ServerPlayerEntity player, MinecraftServer server) {
        YashchikPandoryDebuff a = ACTIVE;
        if (a == null || a.resolved || pos == null) return false;

        if (pos.equals(a.leverPos) && !a.unlocked) {
            a.unlocked = true;
            a.world.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                    SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1.4f, 0.7f);
            server.getPlayerManager().broadcast(Text.translatableWithFallback(
                    "burmalda.chat.yashchik_pandory.lever_found",
                    "§a[Pandora's Box] §f%s found the key! The chest in the temple is unlocked — run!", player.getName().getString()), false);
            com.mixaold.burmalda.advancement.BurmaldaAdvancements.trigger(player, com.mixaold.burmalda.advancement.BurmaldaAdvancements.PANDORA_OPENED);
            return false;
        }
        if (pos.equals(a.chestPos)) {
            if (!a.unlocked) {
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.yashchik_pandory.locked", "§c[Pandora's Box] §fLocked. Find the §6key lever§f in one of the buildings around here."), true);
                return true; // cancel — don't open
            }
            a.succeed(server);
            return false;
        }
        return false;
    }

    /** Block-break hook. If a player smashes the treasure chest, the loot already dropped — count it as a win. */
    public static void handleBreak(BlockPos pos, ServerPlayerEntity player, MinecraftServer server) {
        YashchikPandoryDebuff a = ACTIVE;
        if (a == null || a.resolved || pos == null) return;
        if (pos.equals(a.chestPos)) {
            com.mixaold.burmalda.advancement.BurmaldaAdvancements.trigger(player, com.mixaold.burmalda.advancement.BurmaldaAdvancements.PANDORA_OPENED);
            a.succeed(server);
        }
    }

    private void succeed(MinecraftServer server) {
        resolved = true;
        if (ACTIVE == this) ACTIVE = null;
        double bx = chestPos.getX() + 0.5, by = chestPos.getY() + 1.0, bz = chestPos.getZ() + 0.5;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p, ParticleTypes.TOTEM_OF_UNDYING, true, bx, by, bz, 90, 0.7, 0.9, 0.7, 0.45);
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, p, ParticleTypes.FIREWORK, true, bx, by + 0.6, bz, 60, 0.9, 0.9, 0.9, 0.18);
        }
        world.playSound(null, chestPos.getX(), chestPos.getY(), chestPos.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.5f, 1.2f);
        world.playSound(null, chestPos.getX(), chestPos.getY(), chestPos.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 2f, 1.0f);
        server.getPlayerManager().broadcast(Text.translatableWithFallback("burmalda.chat.yashchik_pandory.success", "§a[Pandora's Box] §fThe chest is open! The treasure is yours."), false);
        cleanup();
        strikeThrough(server);
    }

    private void fail(MinecraftServer server) {
        resolved = true;
        if (ACTIVE == this) ACTIVE = null;
        if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) chest.clear();

        BlockPos s = basePos != null ? basePos : chestPos;
        for (int i = 0; i < 8; i++) {
            ZombieEntity z = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.ZOMBIE, world);
            if (z == null) continue;
            z.setPosition(s.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 8, s.getY() + 1, s.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 8);
            equip(z, EquipmentSlot.HEAD, Items.DIAMOND_HELMET); equip(z, EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD);
            z.setPersistent(); world.spawnEntity(z);
        }

        // PUNISHMENT — Pandora's box: failing to seal it in time unleashes every evil onto everyone.
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 140, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,  300, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON,    160, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,  300, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER,    300, 0));
        }
        world.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 1.4f);
        server.getPlayerManager().broadcast(Text.translatableWithFallback("burmalda.chat.yashchik_pandory.fail", "§4[Pandora's Box] §cTime's up — the reward burned away."), false);
        cleanup();
        strikeThrough(server);
    }

    private void cleanup() {
        removeHologram();
        if (ACTIVE == this) ACTIVE = null;
    }

    private void strikeThrough(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) BurmaldaNetwork.sendGroupDebuffDone(p);
    }

    // ── Build: temple ───────────────────────────────────────────────────────
    private BlockPos buildTemple() {
        Block[] stone = { theme.main(), theme.alt(), theme.accent() };
        // Foundation: fill each base column DOWN to solid ground so it never floats on slopes.
        for (int dx = -BASE_HALF; dx <= BASE_HALF; dx++)
            for (int dz = -BASE_HALF; dz <= BASE_HALF; dz++) {
                Block fb = stone[Math.floorMod(dx + dz, 3)];
                for (int dy = -1; dy >= -18; dy--) {
                    BlockPos p = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState bs = world.getBlockState(p);
                    if (!bs.isAir() && bs.getFluidState().isEmpty()) break; // reached real ground
                    setState(fb.getDefaultState(), cx + dx, cy + dy, cz + dz);
                }
            }

        int apexY = cy;
        for (int t = 0; t < TIERS; t++) {
            int half = BASE_HALF - t;
            apexY = cy + t;
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++)
                    set(stone[Math.floorMod(dx + dz + t, 3)], cx + dx, apexY, cz + dz);
        }

        int sy = apexY + 1;
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2)
                for (int h = 0; h < 3; h++) set(theme.log(), cx + sx, sy + h, cz + sz);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) set(theme.plank(), cx + dx, sy + 3, cz + dz);
        set(theme.light(), cx, sy + 2, cz);
        set(Blocks.CHEST, cx, sy, cz);

        // Easter egg: a tiny hidden vault buried in the dead centre of the pyramid — dig the core to find it.
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                setState(Blocks.AIR.getDefaultState(), cx + dx, cy + 1, cz + dz);
        set(Blocks.GLOWSTONE,  cx + 1, cy + 1, cz + 1);
        set(Blocks.GOLD_BLOCK, cx - 1, cy + 1, cz - 1);
        set(Blocks.GOLD_BLOCK, cx - 1, cy + 1, cz + 1);
        // The real reward for digging to the core: a jackpot chest of rare, genuinely useful gear.
        set(Blocks.CHEST, cx, cy + 1, cz);
        if (world.getBlockEntity(new BlockPos(cx, cy + 1, cz)) instanceof ChestBlockEntity secret) {
            ItemStack[] hoard = {
                new ItemStack(Items.ELYTRA),                    new ItemStack(Items.TOTEM_OF_UNDYING, 2),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 3), new ItemStack(Items.NETHERITE_INGOT, 4),
                new ItemStack(Items.NETHER_STAR),               new ItemStack(Items.DIAMOND_BLOCK, 4),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 32),     new ItemStack(Items.NETHERITE_SCRAP, 4)
            };
            for (int i = 0; i < hoard.length; i++) secret.setStack(10 + i, hoard[i]);
        }

        return new BlockPos(cx, sy, cz);
    }

    // ── Build: huts (кибитки), one hides the lever-key ────────────────────────
    private void buildHuts() {
        int huts = 4 + RANDOM.nextInt(2);            // 4-5
        int keyHut = RANDOM.nextInt(huts);
        for (int i = 0; i < huts; i++) {
            double a = (Math.PI * 2 * i / huts) + RANDOM.nextDouble() * 0.6;
            int r = 13 + RANDOM.nextInt(8);
            int hx = cx + (int) Math.round(Math.cos(a) * r);
            int hz = cz + (int) Math.round(Math.sin(a) * r);
            int hy = groundAt(hx, hz, cy);
            buildHut(hx, hy, hz, i == keyHut);
        }
    }

    private void buildHut(int hx, int hy, int hz, boolean hasLever) {
        Block wall = theme.plank(), corner = theme.log();
        // floor
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) set(theme.alt(), hx + dx, hy - 1, hz + dz);
        // walls (3 high) with corner posts, a doorway gap and window panes
        for (int h = 0; h < 3; h++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue; // interior
                    boolean post = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (dz == -2 && dx == 0 && h < 2) continue;           // doorway
                    Block b = post ? corner : wall;
                    if (!post && h == 1 && ((Math.abs(dx) == 2 && dz == 0) || (Math.abs(dz) == 2 && dx == 0)))
                        b = Blocks.GLASS_PANE;                            // window
                    set(b, hx + dx, hy + h, hz + dz);
                }
        // stepped roof
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) set(wall, hx + dx, hy + 3, hz + dz);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) set(corner, hx + dx, hy + 4, hz + dz);
        set(theme.light(), hx - 1, hy, hz + 1); // interior lantern on the floor corner
        set(theme.light(), hx, hy + 5, hz);     // roof beacon

        if (hasLever) {
            set(theme.accent(), hx, hy, hz);                 // pedestal
            leverPos = new BlockPos(hx, hy + 1, hz);
            setState(Blocks.LEVER.getDefaultState().with(WallMountedBlock.FACE, BlockFace.FLOOR), hx, hy + 1, hz);
        } else {
            set(Blocks.BARREL, hx, hy, hz);                  // a little extra loot stashed inside
            fillBarrel(hx, hy, hz);
        }
    }

    private static final Loot[] BARREL_POOL = {
        new Loot(Items.IRON_INGOT, 2, 8),   new Loot(Items.GOLD_INGOT, 1, 6),   new Loot(Items.COAL, 4, 16),
        new Loot(Items.BREAD, 2, 6),        new Loot(Items.COOKED_BEEF, 2, 8),  new Loot(Items.ARROW, 8, 24),
        new Loot(Items.REDSTONE, 4, 16),    new Loot(Items.LAPIS_LAZULI, 4, 12),new Loot(Items.EXPERIENCE_BOTTLE, 2, 8),
        new Loot(Items.IRON_PICKAXE, 1, 1), new Loot(Items.GOLDEN_APPLE, 1, 2), new Loot(Items.ENDER_PEARL, 1, 3),
        new Loot(Items.EMERALD, 1, 4),      new Loot(Items.TORCH, 8, 16),       new Loot(Items.STRING, 2, 6)
    };

    private void fillBarrel(int hx, int hy, int hz) {
        if (!(world.getBlockEntity(new BlockPos(hx, hy, hz)) instanceof BarrelBlockEntity barrel)) return;
        List<Loot> picks = new ArrayList<>(Arrays.asList(BARREL_POOL));
        Collections.shuffle(picks, RANDOM);
        int n = 2 + RANDOM.nextInt(3); // 2-4 items
        for (int i = 0; i < n && i < picks.size(); i++) {
            Loot l = picks.get(i);
            int c = l.min() + (l.max() > l.min() ? RANDOM.nextInt(l.max() - l.min() + 1) : 0);
            barrel.setStack(RANDOM.nextInt(barrel.size()), new ItemStack(l.item(), c));
        }
    }

    // ── Build: ruins + traps ──────────────────────────────────────────────────
    private void buildRuins() {
        Block[] rubble = { theme.main(), theme.alt(), theme.accent(), Blocks.COBBLESTONE, Blocks.ANDESITE };
        int features = 9 + RANDOM.nextInt(5);
        for (int f = 0; f < features; f++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            int r = (BASE_HALF + 3) + RANDOM.nextInt(16);
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            int gy = groundAt(x, z, cy);
            switch (RANDOM.nextInt(3)) {
                case 0 -> { int h = 1 + RANDOM.nextInt(4); for (int y = 0; y < h; y++) set(rubble[RANDOM.nextInt(rubble.length)], x, gy + y, z); }
                case 1 -> { int n = 3 + RANDOM.nextInt(4); for (int k = 0; k < n; k++) set(rubble[RANDOM.nextInt(rubble.length)], x + RANDOM.nextInt(3) - 1, gy, z + RANDOM.nextInt(3) - 1); }
                default -> { int len = 3 + RANDOM.nextInt(4); boolean ax = RANDOM.nextBoolean(); for (int k = 0; k < len; k++) set(rubble[RANDOM.nextInt(rubble.length)], x + (ax ? k : 0), gy, z + (ax ? 0 : k)); }
            }
        }
    }

    /** Environmental hazards on the way to the temple — fire, cobwebs, magma. No TNT. */
    private void placeTraps() {
        for (int i = 0; i < 10; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            int r = 2 + RANDOM.nextInt(BASE_HALF + 4);
            int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            int gy = groundAt(x, z, cy);
            Block hazard = switch (RANDOM.nextInt(3)) {
                case 0 -> Blocks.FIRE;
                case 1 -> Blocks.COBWEB;
                default -> Blocks.MAGMA_BLOCK;
            };
            if (hazard == Blocks.MAGMA_BLOCK) set(hazard, x, gy - 1, z);
            else if (world.isAir(new BlockPos(x, gy, z))) set(hazard, x, gy, z);
        }
    }

    // ── Guardians ──────────────────────────────────────────────────────────────
    private void spawnGuardians(MinecraftServer server) {
        // Captain on the apex
        ZombieEntity cap = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.ZOMBIE, world);
        if (cap != null) {
            cap.setPosition(cx + 0.5, chestPos.getY(), cz + 2.5);
            equip(cap, EquipmentSlot.HEAD, Items.NETHERITE_HELMET);
            equip(cap, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE);
            equip(cap, EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD);
            cap.setCustomName(Text.translatableWithFallback("burmalda.yashchik_pandory.guard_name", "§4§lBox Guardian"));
            cap.setCustomNameVisible(true);
            cap.setPersistent();
            world.spawnEntity(cap);
        }
        // Grunts around the base
        for (int i = 0; i < 5; i++) {
            boolean archer = RANDOM.nextBoolean();
            MobEntity m = archer
                    ? com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.SKELETON, world)
                    : com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.ZOMBIE, world);
            if (m == null) continue;
            double a = RANDOM.nextDouble() * Math.PI * 2;
            int r = BASE_HALF + 1 + RANDOM.nextInt(3);
            m.setPosition(cx + Math.cos(a) * r, cy + 1, cz + Math.sin(a) * r);
            equip(m, EquipmentSlot.HEAD, Items.IRON_HELMET);
            equip(m, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
            equip(m, EquipmentSlot.MAINHAND, archer ? Items.BOW : Items.IRON_SWORD);
            m.setPersistent();
            world.spawnEntity(m);
        }
    }

    // ── Loot + helpers ─────────────────────────────────────────────────────────
    private record Loot(net.minecraft.item.Item item, int min, int max) {}
    private static final Loot[] LOOT_POOL = {
        new Loot(Items.DIAMOND, 2, 8),            new Loot(Items.NETHERITE_INGOT, 1, 2),
        new Loot(Items.NETHERITE_SCRAP, 1, 3),    new Loot(Items.IRON_INGOT, 8, 32),
        new Loot(Items.GOLD_INGOT, 6, 24),        new Loot(Items.EMERALD, 4, 16),
        new Loot(Items.DIAMOND_PICKAXE, 1, 1),    new Loot(Items.DIAMOND_SWORD, 1, 1),
        new Loot(Items.DIAMOND_AXE, 1, 1),        new Loot(Items.SHIELD, 1, 1),
        new Loot(Items.CROSSBOW, 1, 1),           new Loot(Items.ENCHANTED_GOLDEN_APPLE, 1, 2),
        new Loot(Items.GOLDEN_APPLE, 2, 5),       new Loot(Items.TOTEM_OF_UNDYING, 1, 1),
        new Loot(Items.GOLDEN_CARROT, 8, 16),     new Loot(Items.COOKED_BEEF, 12, 32),
        new Loot(Items.EXPERIENCE_BOTTLE, 16, 48),new Loot(Items.ENDER_PEARL, 2, 8),
        new Loot(Items.OBSIDIAN, 4, 12),          new Loot(Items.TNT, 2, 6),
        new Loot(Items.FIREWORK_ROCKET, 8, 16),   new Loot(Items.ARROW, 16, 48),
        new Loot(Items.NAME_TAG, 1, 1),           new Loot(Items.SADDLE, 1, 1),
        new Loot(Items.IRON_BLOCK, 2, 6),         new Loot(Items.GOLD_BLOCK, 1, 4),
        new Loot(Items.DIAMOND_BLOCK, 1, 2),      new Loot(Items.ELYTRA, 1, 1),
        new Loot(Items.NETHER_STAR, 1, 1)
    };

    private void fillChest() {
        if (!(world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) return;
        List<Loot> picks = new ArrayList<>(Arrays.asList(LOOT_POOL));
        Collections.shuffle(picks, RANDOM);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < chest.size(); i++) slots.add(i);
        Collections.shuffle(slots, RANDOM);
        int count = 18 + RANDOM.nextInt(8); // 18-25 scattered stacks
        for (int i = 0; i < count && i < picks.size() && i < slots.size(); i++) {
            Loot l = picks.get(i);
            int n = l.min() + (l.max() > l.min() ? RANDOM.nextInt(l.max() - l.min() + 1) : 0);
            chest.setStack(slots.get(i), new ItemStack(l.item(), n));
        }
    }

    private void spawnHologram() {
        ArmorStandEntity holo = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.ARMOR_STAND, world);
        if (holo == null) return;
        holo.setPosition(chestPos.getX() + 0.5, chestPos.getY() + 0.7, chestPos.getZ() + 0.5);
        ((com.mixaold.burmalda.mixin.ArmorStandMarkerInvoker) holo).burmalda$setMarker(true);
        holo.setInvisible(true); holo.setNoGravity(true); holo.setInvulnerable(true); holo.setSilent(true);
        holo.setCustomName(Text.translatableWithFallback("burmalda.yashchik_pandory.holo_name", "§e§lPandora's Box"));
        holo.setCustomNameVisible(true);
        world.spawnEntity(holo);
        hologramUuid = holo.getUuid();
    }

    private void removeHologram() {
        if (world != null && hologramUuid != null) {
            Entity e = world.getEntity(hologramUuid);
            if (e != null) e.remove(Entity.RemovalReason.DISCARDED);
            hologramUuid = null;
        }
    }

    private void equip(MobEntity m, EquipmentSlot slot, net.minecraft.item.Item item) {
        m.equipStack(slot, new ItemStack(item));
        m.setEquipmentDropChance(slot, 0f);
    }

    private int groundAt(int x, int z, int fallback) {
        int gy = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        return (gy < 5 || gy > 250) ? fallback : gy;
    }

    private void set(Block b, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (world.isInBuildLimit(p)) world.setBlockState(p, b.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private void setState(BlockState s, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (world.isInBuildLimit(p)) world.setBlockState(p, s, Block.NOTIFY_LISTENERS);
    }
}
