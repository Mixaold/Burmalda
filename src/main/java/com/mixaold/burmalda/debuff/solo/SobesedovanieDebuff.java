package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;

import java.util.Random;
import java.util.UUID;

public class SobesedovanieDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int NAG_INTERVAL = 160; // 8 s of silence before the HR nags

    private static final String[] QUESTIONS = {
        "Расскажите о вашем опыте работы с мечом.",
        "Почему именно мы?",
        "Назовите ваш главный недостаток.",
        "Кем вы видите себя через 5 лет?",
        "Вы умеете работать в команде?"
    };

    // English fallbacks; ru_ru.json provides the Russian versions (burmalda.chat.sobesedovanie.nag.N)
    private static final String[] NAGS = {
        "Well?", "How long do I have to wait?", "We're listening...", "Time is money, answer please.",
        "Hey, are you there? Waiting for your answer in chat.", "Don't stay silent — this is an interview.", "And...?"
    };

    // Solo-debuff instances are shared between players, so interview state is kept per player.
    private static final java.util.Map<UUID, Interview> INTERVIEWS = new java.util.HashMap<>();

    private static final class Interview {
        UUID hrUuid;
        BlockPos seatPos;
        int questionIndex = 0;
        int badAnswers = 0;
        int goodAnswers = 0;
        boolean ended = false;
        boolean waiting = false; // waiting for the player's chat answer
        int ticksSilent = 0;     // ticks since the last prompt/nag
    }

    public SobesedovanieDebuff() {
        super("sobesedovanie", "Job Interview",
                "You have been invited to an interview. You are seated. Movement is prohibited.",
                DebuffType.SOLO);
    }

    @Override
    public void onStart(ServerPlayerEntity player) {
        Interview iv = new Interview();
        INTERVIEWS.put(player.getUuid(), iv);

        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        BlockPos anchor = player.getBlockPos();

        buildOffice(world, anchor);

        iv.seatPos = anchor;
        player.teleport(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, true);

        // HR villager — AI disabled so it stays behind the desk
        VillagerEntity hr = new VillagerEntity(EntityType.VILLAGER, world);
        hr.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() - 4.5);
        //? if >=1.21.5 {
        /*hr.setVillagerData(hr.getVillagerData().withProfession(
                world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.VILLAGER_PROFESSION)
                        .getOrThrow(VillagerProfession.LIBRARIAN)));
        *///?} else {
        hr.setVillagerData(hr.getVillagerData().withProfession(VillagerProfession.LIBRARIAN));
        //?}
        hr.setCustomName(Text.translatableWithFallback("burmalda.chat.sobesedovanie.hr_name", "§eHR Manager"));
        hr.setCustomNameVisible(true);
        hr.setAiDisabled(true);
        hr.setPersistent();
        world.spawnEntity(hr);
        iv.hrUuid = hr.getUuid();

        world.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 1.2f);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.start", "§e[HR] §fPlease, have a seat. Let's begin."), false);
        askQuestion(player, iv);
    }

    // ── Building ─────────────────────────────────────────────────────────────

    private void buildOffice(ServerWorld world, BlockPos anchor) {
        int ox = anchor.getX(), oy = anchor.getY(), oz = anchor.getZ();

        // Ground surface — 21×21 polished andesite
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++)
                set(world, ox + x, oy - 1, oz + z, Blocks.POLISHED_ANDESITE.getDefaultState());

        // Clear full interior (7 floors × 5 blocks each)
        for (int x = -9; x <= 9; x++)
            for (int y = 0; y <= 34; y++)
                for (int z = -9; z <= 9; z++)
                    set(world, ox + x, oy + y, oz + z, Blocks.AIR.getDefaultState());

        // Ceiling — 21×21, sea lantern grid
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                boolean light = (x % 5 == 0 && z % 5 == 0);
                set(world, ox + x, oy + 35, oz + z,
                        light ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
            }

        // Walls — 35 tall (7 floors), pillar/glass pattern
        for (int i = -10; i <= 10; i++) {
            for (int y = 0; y <= 34; y++) {
                set(world, ox + i, oy + y, oz - 10, wallBlock(i, y));
                boolean entrance = (i >= -2 && i <= 2 && y <= 2);
                if (!entrance)
                    set(world, ox + i, oy + y, oz + 10, wallBlock(i, y));
                set(world, ox + 10, oy + y, oz + i, wallBlock(i, y));
                set(world, ox - 10, oy + y, oz + i, wallBlock(i, y));
            }
        }

        // Interior floor plates with sea lantern lights (at every 5-block band)
        for (int fy : new int[]{5, 10, 15, 20, 25, 30}) {
            for (int x = -9; x <= 9; x++)
                for (int z = -9; z <= 9; z++)
                    set(world, ox + x, oy + fy, oz + z, Blocks.WHITE_CONCRETE.getDefaultState());
            for (int x = -5; x <= 5; x += 5)
                for (int z = -5; z <= 5; z += 5)
                    set(world, ox + x, oy + fy, oz + z, Blocks.SEA_LANTERN.getDefaultState());
        }

        // === GROUND FLOOR: HR interview room ===
        set(world, ox, oy - 1, oz,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        set(world, ox - 1, oy, oz - 3, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox + 1, oy, oz - 3, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox - 1, oy + 1, oz - 3, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        set(world, ox,     oy + 1, oz - 3, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        set(world, ox + 1, oy + 1, oz - 3, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        set(world, ox - 3, oy - 1, oz + 2,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        set(world, ox + 3, oy - 1, oz + 2,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        set(world, ox - 9, oy, oz - 9, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 9, oy, oz - 9, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox - 9, oy, oz + 9, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 9, oy, oz + 9, Blocks.POTTED_FERN.getDefaultState());

        // === FLOOR 2 (oy+5): Open office — rows of desks ===
        int b2 = oy + 5;
        for (int dx : new int[]{-6, -1, 4}) {
            set(world, ox + dx,     b2 + 1, oz - 7, Blocks.OAK_FENCE.getDefaultState());
            set(world, ox + dx + 1, b2 + 1, oz - 7, Blocks.OAK_FENCE.getDefaultState());
            set(world, ox + dx,     b2 + 2, oz - 7, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
            set(world, ox + dx + 1, b2 + 2, oz - 7, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
            set(world, ox + dx,     b2 + 1, oz - 5,
                    Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        }
        set(world, ox - 8, b2 + 1, oz - 8, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 8, b2 + 1, oz - 8, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox - 8, b2 + 1, oz + 8, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 8, b2 + 1, oz + 8, Blocks.POTTED_FERN.getDefaultState());

        // === FLOOR 3 (oy+10): Library ===
        int b3 = oy + 10;
        for (int bx = -8; bx <= 8; bx += 2) {
            set(world, ox + bx, b3 + 1, oz - 8, Blocks.BOOKSHELF.getDefaultState());
            set(world, ox + bx, b3 + 2, oz - 8, Blocks.BOOKSHELF.getDefaultState());
            set(world, ox + bx, b3 + 3, oz - 8, Blocks.BOOKSHELF.getDefaultState());
        }
        set(world, ox - 1, b3 + 1, oz, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox + 1, b3 + 1, oz, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox - 1, b3 + 2, oz, Blocks.OAK_SLAB.getDefaultState());
        set(world, ox,     b3 + 2, oz, Blocks.OAK_SLAB.getDefaultState());
        set(world, ox + 1, b3 + 2, oz, Blocks.OAK_SLAB.getDefaultState());
        set(world, ox,     b3 + 1, oz + 2,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        set(world, ox,     b3 + 1, oz - 2,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
        set(world, ox + 3, b3 + 1, oz,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        set(world, ox - 3, b3 + 1, oz,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));

        // === FLOOR 4 (oy+15): Storage — barrels with loot ===
        int b4 = oy + 15;
        int[][] barrelPositions = {
            {-7, b4 + 1, -7}, {-4, b4 + 1, -7}, {-1, b4 + 1, -7},
            { 2, b4 + 1, -7}, { 5, b4 + 1, -7}, { 7, b4 + 1, -7},
            {-7, b4 + 2, -7}, {-4, b4 + 2, -7}, {-1, b4 + 2, -7},
            { 2, b4 + 2, -7}, { 5, b4 + 2, -7},
            {-7, b4 + 1, -4}, {-4, b4 + 1, -4}, {-1, b4 + 1, -4},
            { 2, b4 + 1, -4}, { 5, b4 + 1, -4},
        };
        for (int[] bp : barrelPositions) {
            BlockPos bpos = new BlockPos(ox + bp[0], bp[1], oz + bp[2]);
            set(world, ox + bp[0], bp[1], oz + bp[2], Blocks.BARREL.getDefaultState());
            if (world.getBlockEntity(bpos) instanceof BarrelBlockEntity barrel) {
                fillBarrelWithLoot(barrel);
            }
        }
        set(world, ox - 7, b4 + 1, oz + 5, Blocks.LANTERN.getDefaultState());
        set(world, ox + 7, b4 + 1, oz + 5, Blocks.LANTERN.getDefaultState());
        set(world, ox,     b4 + 1, oz + 7, Blocks.LANTERN.getDefaultState());

        // === FLOOR 5 (oy+20): Lounge ===
        int b5 = oy + 20;
        set(world, ox - 6, b5 + 1, oz - 6, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 6, b5 + 1, oz - 6, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox - 6, b5 + 1, oz + 6, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 6, b5 + 1, oz + 6, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox,     b5 + 1, oz - 7, Blocks.POTTED_CACTUS.getDefaultState());
        set(world, ox,     b5 + 1, oz + 7, Blocks.POTTED_CACTUS.getDefaultState());
        set(world, ox - 1, b5 + 1, oz, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox + 1, b5 + 1, oz, Blocks.OAK_FENCE.getDefaultState());
        set(world, ox - 1, b5 + 2, oz, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        set(world, ox,     b5 + 2, oz, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        set(world, ox + 1, b5 + 2, oz, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        set(world, ox - 4, b5 + 1, oz,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        set(world, ox + 4, b5 + 1, oz,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        set(world, ox,     b5 + 1, oz + 4,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        set(world, ox,     b5 + 1, oz - 4,
                Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));

        // === FLOOR 6 (oy+25): Conference room ===
        int b6 = oy + 25;
        for (int dx = -5; dx <= 5; dx++) {
            set(world, ox + dx, b6 + 1, oz, Blocks.OAK_FENCE.getDefaultState());
            set(world, ox + dx, b6 + 2, oz, Blocks.OAK_SLAB.getDefaultState());
        }
        for (int dx = -4; dx <= 4; dx += 2) {
            set(world, ox + dx, b6 + 1, oz + 2,
                    Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
            set(world, ox + dx, b6 + 1, oz - 2,
                    Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
        }
        set(world, ox - 8, b6 + 1, oz - 8, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 8, b6 + 1, oz - 8, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox - 8, b6 + 1, oz + 8, Blocks.POTTED_FERN.getDefaultState());
        set(world, ox + 8, b6 + 1, oz + 8, Blocks.POTTED_FERN.getDefaultState());

        // === FLOOR 7 (oy+30): Top floor — lanterns only ===
        int b7 = oy + 30;
        set(world, ox - 6, b7 + 1, oz - 6, Blocks.LANTERN.getDefaultState());
        set(world, ox + 6, b7 + 1, oz - 6, Blocks.LANTERN.getDefaultState());
        set(world, ox - 6, b7 + 1, oz + 6, Blocks.LANTERN.getDefaultState());
        set(world, ox + 6, b7 + 1, oz + 6, Blocks.LANTERN.getDefaultState());

        // === LADDER SHAFT along west wall (x=-9, z=+8) ===
        // Clear floor-plate holes first so climbing is unobstructed
        for (int fy : new int[]{5, 10, 15, 20, 25, 30}) {
            set(world, ox - 9, oy + fy, oz + 8, Blocks.AIR.getDefaultState());
        }
        // Ladders attach to west wall (x=-10 is solid), placed at x=-9 facing EAST
        for (int y = 0; y <= 34; y++) {
            set(world, ox - 9, oy + y, oz + 8,
                    Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        }
    }

    private void fillBarrelWithLoot(BarrelBlockEntity barrel) {
        ItemStack[] pool = {
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.DIAMOND_SHOVEL),
            new ItemStack(Items.DIAMOND_HOE),
            new ItemStack(Items.DIAMOND_HELMET),
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS),
            new ItemStack(Items.DIAMOND_BOOTS),
            new ItemStack(Items.NETHERITE_INGOT, 2),
            new ItemStack(Items.NETHERITE_SWORD),
            new ItemStack(Items.NETHERITE_PICKAXE),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
            new ItemStack(Items.GOLDEN_APPLE, 4),
            new ItemStack(Items.GOLDEN_CARROT, 24),
            new ItemStack(Items.COOKED_BEEF, 32),
            new ItemStack(Items.BREAD, 32),
            new ItemStack(Items.ENDER_PEARL, 8),
            new ItemStack(Items.TNT, 8),
            new ItemStack(Items.OBSIDIAN, 16),
            new ItemStack(Items.IRON_INGOT, 64),
            new ItemStack(Items.GOLD_INGOT, 32),
            new ItemStack(Items.DIAMOND, 12),
            new ItemStack(Items.EXPERIENCE_BOTTLE, 16),
            new ItemStack(Items.TOTEM_OF_UNDYING),
            new ItemStack(Items.BOW),
            new ItemStack(Items.ARROW, 64),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.ENDER_EYE, 12),
            new ItemStack(Items.BLAZE_ROD, 8),
        };
        ArrayList<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) slots.add(i);
        Collections.shuffle(slots, RANDOM);
        int count = 12 + RANDOM.nextInt(10); // 12–21 items per barrel
        for (int i = 0; i < count; i++) {
            barrel.setStack(slots.get(i), pool[RANDOM.nextInt(pool.length)].copy());
        }
        barrel.markDirty();
    }

    /** White concrete pillar at every 5th column or every 5th floor; glass pane otherwise. */
    private BlockState wallBlock(int col, int y) {
        if (col % 5 == 0 || y % 5 == 0) return Blocks.WHITE_CONCRETE.getDefaultState();
        return Blocks.GLASS.getDefaultState();
    }

    private void set(ServerWorld world, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.isInBuildLimit(pos)) world.setBlockState(pos, state, net.minecraft.block.Block.NOTIFY_LISTENERS);
    }

    // ── Interview logic ──────────────────────────────────────────────────────

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        Interview iv = INTERVIEWS.get(player.getUuid());
        if (iv == null || iv.ended) return;

        // Lock player in seat
        if (iv.seatPos != null && ticksActive % 2 == 0) {
            double dist = player.squaredDistanceTo(
                    iv.seatPos.getX() + 0.5, iv.seatPos.getY(), iv.seatPos.getZ() + 0.5);
            if (dist > 2.25) {
                player.teleport(iv.seatPos.getX() + 0.5, iv.seatPos.getY(), iv.seatPos.getZ() + 0.5, true);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20, 0));
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.stay", "§c[HR] §fWhere are you going? We're not done yet."), true);
            }
        }

        // The HR waits for a chat answer; if the player stays silent, it nags.
        if (iv.waiting) {
            iv.ticksSilent++;
            if (iv.ticksSilent >= NAG_INTERVAL) {
                iv.ticksSilent = 0;
                int n = RANDOM.nextInt(NAGS.length);
                VillagerEntity hr = findHr(com.mixaold.burmalda.util.BurmaldaCompat.world(player), iv);
                if (hr != null) com.mixaold.burmalda.util.BurmaldaCompat.world(player).playSound(null, hr.getX(), hr.getY(), hr.getZ(),
                        SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
                player.sendMessage(Text.literal("§e[HR] §f").append(
                        Text.translatableWithFallback("burmalda.chat.sobesedovanie.nag." + n, NAGS[n])), false);
            }
        }
    }

    private static void askQuestion(ServerPlayerEntity player, Interview iv) {
        player.sendMessage(Text.literal("§e[HR] §f").append(
                Text.translatableWithFallback("burmalda.chat.sobesedovanie.question." + iv.questionIndex,
                        QUESTIONS[iv.questionIndex])), false);
        iv.waiting = true;
        iv.ticksSilent = 0;
    }

    /** Called from the chat listener whenever a player sends a message. */
    public static void handleChat(ServerPlayerEntity player) {
        Interview iv = INTERVIEWS.get(player.getUuid());
        if (iv == null || iv.ended || !iv.waiting) return;
        iv.waiting = false;
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        evaluateAnswer(world, player, iv);
        if (iv.ended) return;
        iv.questionIndex++;
        if (iv.questionIndex < QUESTIONS.length) {
            askQuestion(player, iv);
        } else {
            finishInterview(world, player, iv);
        }
    }

    private static void evaluateAnswer(ServerWorld world, ServerPlayerEntity player, Interview iv) {
        VillagerEntity hr = findHr(world, iv);
        if (RANDOM.nextBoolean()) {
            iv.goodAnswers++;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0));
            if (hr != null)
                for (ServerPlayerEntity sp : world.getPlayers())
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.HAPPY_VILLAGER, true,
                            hr.getX(), hr.getY() + 1, hr.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.good", "§a[HR] §fNot bad. (%s/3 good)", String.valueOf(iv.goodAnswers)), false);
            if (iv.goodAnswers >= 3) earlySuccess(world, player, iv);
        } else {
            iv.badAnswers++;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0));
            if (hr != null) {
                world.playSound(null, hr.getX(), hr.getY(), hr.getZ(),
                        SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
                for (ServerPlayerEntity sp : world.getPlayers())
                    com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.ANGRY_VILLAGER, true,
                            hr.getX(), hr.getY() + 1, hr.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
            }
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.bad", "§c[HR] §fMm. I see."), false);
            if (iv.badAnswers >= 3) earlyFail(world, player, iv);
        }
    }

    private static void earlySuccess(ServerWorld world, ServerPlayerEntity player, Interview iv) {
        iv.ended = true;
        iv.seatPos = null;
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2.0f, 1.5f);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 600, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 1));
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.early_success", "§a[HR] §fImpressive. You're hired — ahead of schedule."), false);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.hired", "§a[Interview] §fYou got the job. Salary TBD."), false);
        VillagerEntity hr = findHr(world, iv);
        if (hr != null) {
            for (ServerPlayerEntity sp : world.getPlayers())
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.HAPPY_VILLAGER, true,
                        hr.getX(), hr.getY() + 1, hr.getZ(), 15, 0.5, 0.5, 0.5, 0.0);
            hr.setAiDisabled(false);
        }
    }

    private static void earlyFail(ServerWorld world, ServerPlayerEntity player, Interview iv) {
        iv.ended = true;
        iv.seatPos = null;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 400, 1));
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.reject", "§c[HR] §fWe'll consider other candidates. Thank you for your time."), false);
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.not_hired", "§c[Interview] §fYou didn't get the job."), false);
        VillagerEntity hr = findHr(world, iv);
        if (hr != null) hr.setAiDisabled(false); // уходит сам
    }

    /** All questions answered — wrap up the interview. */
    private static void finishInterview(ServerWorld world, ServerPlayerEntity player, Interview iv) {
        iv.ended = true;
        iv.seatPos = null;
        if (RANDOM.nextBoolean()) {
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.callback", "§a[HR] §fWe'll call you back."), false);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 300, 0));
        } else {
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.sobesedovanie.probation", "§a[HR] §fYou're hired! Probation period — 3 years."), false);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
        }
        VillagerEntity hr = findHr(world, iv);
        if (hr != null) {
            hr.setAiDisabled(false); // отпускаем — идёт куда хочет
            for (ServerPlayerEntity sp : world.getPlayers())
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(world, sp, ParticleTypes.HAPPY_VILLAGER, true,
                        hr.getX(), hr.getY() + 1, hr.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
        }
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        Interview iv = INTERVIEWS.remove(player.getUuid());
        if (iv == null || iv.ended) return;
        finishInterview(com.mixaold.burmalda.util.BurmaldaCompat.world(player), player, iv);
    }

    private static VillagerEntity findHr(ServerWorld world, Interview iv) {
        if (iv.hrUuid == null) return null;
        var e = world.getEntity(iv.hrUuid);
        return e instanceof VillagerEntity v ? v : null;
    }
}
