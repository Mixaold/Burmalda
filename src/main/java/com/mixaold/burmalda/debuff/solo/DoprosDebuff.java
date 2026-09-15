package com.mixaold.burmalda.debuff.solo;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * «Допрос» (Interrogation) — a SOLO trick event. The player is told (lie #1) they were hauled off to a
 * giant megacity, into a thousand-floor tower, floor 900-something. In reality the interrogation room is
 * built right where they were standing, on the ground, a plain dark 9×9 box. An investigator villager
 * ("Сотрудник") fires absurd questions; the player must mash one of two answer pads (ДА / НЕТ) before
 * each question times out — there is no right answer, only the act of answering. After enough answers
 * the clerk lets them go and (lie #2) tells them to take the stairs down — "it's a long way, lift's
 * broken". The player braces for a descent that never happens: the door simply opens onto the ordinary
 * world right outside. No teleport, no corridor, no elevator. "Двери закрываются."
 */
public class DoprosDebuff extends Debuff {

    private static final Random RANDOM = new Random();
    private static final int TOTAL_QUESTIONS = 10;
    private static final int QUESTION_WINDOW = 220; // ~11 s to answer before "silence = consent"
    private static final int BLINDNESS_DURATION = 12000; // generously covers the whole interrogation; cleared on release

    // Pre-game sequence, delivered straight to chat (no HUD bar): silence → lie 1 → lie 2 → questioning.
    // Keeps the exact pacing the old progress bar used so it still reads as a slow, ominous build-up.
    private static final int PHASE_SILENT      = 0; // just teleported, blind, nothing shown yet
    private static final int PHASE_LIE         = 1; // chat: the "big city, floor N" lie
    private static final int PHASE_INTRO       = 2; // chat: the "yes or no, press the pad" line
    private static final int PHASE_QUESTIONING = 3; // real questions in chat
    private static final int SILENT_DELAY   = 80; // 4 s
    private static final int LIE_DELAY      = 70; // ~3.5 s
    private static final int INTRO_DELAY    = 70; // ~3.5 s

    // English fallbacks; ru_ru.json provides the Russian (burmalda.chat.dopros.q.N)
    private static final String[] QUESTIONS = {
        "Where were you the night before last at 23:40?",
        "Do you know a citizen named Tolik from the next stairwell?",
        "Where did you put the three-litre jar off the balcony?",
        "Why is your light level... elevated?",
        "Confirm: are you you?",
        "Whose footprints are these? They're yours.",
        "Did you, or did you not? Answer honestly.",
        "Account for every diamond. We'll wait.",
        "Have you ever mined at night? Think carefully.",
        "Do you admit that the creeper acted on your orders?",
        "Where is the rest of the gold? We know there's more.",
        "Last question. You sure about that? Answer.",
        "Are you cooperating with the investigation? Tick a box.",
        "Sign here. And here. And initial that you read nothing.",
    };

    private static final String[] REACTIONS = {
        "burmalda.chat.dopros.react.0", "burmalda.chat.dopros.react.1",
        "burmalda.chat.dopros.react.2", "burmalda.chat.dopros.react.3",
        "burmalda.chat.dopros.react.4", "burmalda.chat.dopros.react.5",
    };

    private static final class Session {
        ServerWorld world;
        int cx, cy, cz;            // box anchor; cy = floor level (built right on the ground)
        BlockPos yesPos, noPos;    // answer pads
        BlockPos doorLower, doorUpper; // the door the player opens themselves to leave
        UUID clerkUuid;
        int[] order;               // picked question indices into QUESTIONS
        int idx;                   // current question (0..TOTAL_QUESTIONS-1)
        int answered;              // questions resolved (press or timeout)
        int presses;               // clean answers
        int timeouts;              // missed answers
        int ticksOnQuestion;
        int phase;                 // PHASE_* — pre-game sequence before real questioning starts
        int phaseTicksLeft;        // countdown within the current phase
        int floor;                 // the random "floor number" lie, picked once, shown during PHASE_BAR_LIE
        boolean released;          // interrogation over, door open, free to leave — no further teleporting
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private static DoprosDebuff INSTANCE;

    public DoprosDebuff() {
        super("dopros", "The Interrogation",
                "You're way up high, and you're caught — you're about to be interrogated.",
                DebuffType.SOLO);
        INSTANCE = this;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    @Override
    public void onStart(ServerPlayerEntity player) {
        ServerWorld world = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        Session s = new Session();
        s.world = world;

        s.cx = (int) Math.floor(player.getX());
        s.cz = (int) Math.floor(player.getZ());
        // The room is built right on the ground around where the player already stands.
        s.cy = Math.max(world.getBottomY() + 1, player.getBlockPos().getY() - 1);

        // Pick TOTAL_QUESTIONS distinct questions.
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < QUESTIONS.length; i++) pool.add(i);
        Collections.shuffle(pool, RANDOM);
        s.order = new int[TOTAL_QUESTIONS];
        for (int i = 0; i < TOTAL_QUESTIONS; i++) s.order[i] = pool.get(i % pool.size());

        build(s);
        sessions.put(player.getUuid(), s);

        // Seat the player facing the clerk (north). Blind for the whole interrogation — cleared on release.
        com.mixaold.burmalda.util.BurmaldaCompat.teleport(player, world,
                s.cx + 0.5, s.cy + 1, s.cz + 1.5, 180f, 0f);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, BLINDNESS_DURATION, 0, false, false));
        world.playSound(null, s.cx, s.cy + 1, s.cz, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1.2f, 0.7f);
        world.playSound(null, s.cx, s.cy + 1, s.cz, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), SoundCategory.AMBIENT, 1.4f, 0.5f);

        // Silent beat in the dark before anything is shown at all — sealed in, blind, nothing yet.
        s.floor = 870 + RANDOM.nextInt(120);
        s.phase = PHASE_SILENT;
        s.phaseTicksLeft = SILENT_DELAY;
    }

    /** Advances the pre-game sequence by one step once the current phase's timer runs out. */
    private void advancePhase(ServerPlayerEntity player, Session s) {
        switch (s.phase) {
            case PHASE_SILENT -> {
                s.phase = PHASE_LIE;
                s.phaseTicksLeft = LIE_DELAY;
                // Lie #1, in chat: the thousand-floor megacity tower (with the random floor number).
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.start",
                        "§8[Сотрудник] §7Тебя доставили в город. Бизнес-центр «Горизонт», этаж %s. Лифт не работает — но об этом потом.",
                        s.floor), false);
            }
            case PHASE_LIE -> {
                s.phase = PHASE_INTRO;
                s.phaseTicksLeft = INTRO_DELAY;
                // The rules, in chat: answer YES or NO, don't stay silent.
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.intro",
                        "§8[Сотрудник] §7Садись. Отвечай ДА или НЕТ — жми кнопку, не молчи. Молчание мы трактуем сами."), false);
            }
            case PHASE_INTRO -> {
                s.phase = PHASE_QUESTIONING;
                askQuestion(player, s);
            }
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player, int ticksActive) {
        Session s = sessions.get(player.getUuid());
        if (s == null || s.released) return;

        // Keep the suspect at the desk — wander off and you're marched back.
        if (ticksActive % 2 == 0) {
            double dist = player.squaredDistanceTo(s.cx + 0.5, s.cy + 1, s.cz + 1.5);
            if (dist > 6.25) { // ~2.5 blocks
                com.mixaold.burmalda.util.BurmaldaCompat.teleport(player, s.world,
                        s.cx + 0.5, s.cy + 1, s.cz + 1.5, 180f, 0f);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20, 0));
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.stay",
                        "§c[Сотрудник] §7Сидеть. Допрос не окончен."), true);
            }
        }

        if (s.phase != PHASE_QUESTIONING) {
            // Pre-game lie/intro are one-shot chat messages sent on phase entry; here we just pace them.
            if (--s.phaseTicksLeft <= 0) advancePhase(player, s);
            return;
        }

        // Question timeout — "silence is consent" + a touch of Slowness, then move on.
        s.ticksOnQuestion++;
        if (s.ticksOnQuestion >= QUESTION_WINDOW) {
            s.timeouts++;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 0, false, true));
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.timeout",
                    "§c[Сотрудник] §7Молчание — знак согласия. Записал."), false);
            s.world.playSound(null, s.cx, s.cy + 1, s.cz - 3, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1f, 0.7f);
            advance(player, s);
        }
    }

    @Override
    public void onEnd(ServerPlayerEntity player) {
        Session s = sessions.remove(player.getUuid());
        if (s == null) return;
        // No teleporting needed — the room is real, on the ground, exactly where the player already is.
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        releaseClerk(s);
    }

    // ── Answer pads + exit door (wired from BurmaldaMod's UseBlockCallback) ──────
    /** @return true to CANCEL the vanilla interaction. */
    public static boolean handleUse(BlockPos pos, ServerPlayerEntity player) {
        if (INSTANCE == null || pos == null) return false;
        Session s = INSTANCE.sessions.get(player.getUuid());
        if (s == null) return false;
        if (!s.released) {
            if (pos.equals(s.yesPos) || pos.equals(s.noPos)) {
                INSTANCE.registerPress(player, s);
                return true;
            }
            return false;
        }
        // Released: the player opening the door themselves is the punchline — "Height Is a Lie".
        // Don't cancel the interaction, the door must still open normally.
        if (pos.equals(s.doorLower) || pos.equals(s.doorUpper)) {
            com.mixaold.burmalda.advancement.BurmaldaAdvancements.trigger(player,
                    com.mixaold.burmalda.advancement.BurmaldaAdvancements.DOPROS_DOOR);
        }
        return false;
    }

    private void registerPress(ServerPlayerEntity player, Session s) {
        s.presses++;
        // The clerk "writes it down" — same useless reaction regardless of the answer.
        String key = REACTIONS[RANDOM.nextInt(REACTIONS.length)];
        player.sendMessage(Text.translatableWithFallback(key, "§8[Сотрудник] §7Угу. Так и запишем."), true);
        s.world.playSound(null, s.cx, s.cy + 1, s.cz - 3, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.AMBIENT, 0.8f, 1.6f);
        s.world.playSound(null, s.cx, s.cy + 1, s.cz - 3, SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 0.7f, 1.1f);
        advance(player, s);
    }

    private void advance(ServerPlayerEntity player, Session s) {
        s.answered++;
        s.idx++;
        if (s.idx >= TOTAL_QUESTIONS) {
            release(player, s);
        } else {
            askQuestion(player, s);
        }
    }

    private void askQuestion(ServerPlayerEntity player, Session s) {
        s.ticksOnQuestion = 0;
        int qi = s.order[s.idx];
        player.sendMessage(Text.literal("§8[Сотрудник] §7").append(
                Text.translatableWithFallback("burmalda.chat.dopros.q." + qi, QUESTIONS[qi])), false);
        s.world.playSound(null, s.cx, s.cy + 1, s.cz - 3, SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 1f, 0.8f);
    }

    private void release(ServerPlayerEntity player, Session s) {
        s.released = true;
        // Door stays shut — the player opens it themselves, like any normal door, and walks into the
        // ordinary world right outside. No auto-open, no corridor, no teleport. That's the punchline.
        releaseClerk(s);
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        s.world.playSound(null, s.cx, s.cy + 1, s.cz, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.1f, 1f);
        if (s.timeouts > 0) {
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.release_dirty",
                    "§8[Сотрудник] §7Свободен. Наследил ты, конечно, но свободен."), false);
        } else {
            player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.release",
                    "§8[Сотрудник] §7Свободен. Чист как стекло."), false);
        }
        // Lie #2: brace yourself for a very long way down...
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.stairs",
                "§8[Сотрудник] §7Лифт не работает, так что вниз — по лестнице. Этажей много, не торопись."), false);
        // ...the punchline: open that door yourself and you're already in the ordinary world. No descent at all.
        player.sendMessage(Text.translatableWithFallback("burmalda.chat.dopros.doors",
                "§7[Сотрудник] §8Двери закрываются."), false);
    }

    // ── Build ────────────────────────────────────────────────────────────────────
    private void build(Session s) {
        ServerWorld w = s.world;
        int cx = s.cx, cy = s.cy, cz = s.cz;
        BlockState black = Blocks.BLACK_CONCRETE.getDefaultState();

        // Hollow 9×9 box: floor cy, interior cy+1..cy+4, ceiling cy+5. Plain concrete — no nether blocks.
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                set(w, cx + dx, cy, cz + dz, Blocks.GRAY_CONCRETE.getDefaultState());
                set(w, cx + dx, cy + 5, cz + dz, black);
                for (int h = 1; h <= 4; h++) set(w, cx + dx, cy + h, cz + dz, Blocks.AIR.getDefaultState());
            }
        // Walls.
        for (int d = -4; d <= 4; d++)
            for (int h = 1; h <= 4; h++) {
                set(w, cx + d, cy + h, cz - 4, black);
                set(w, cx + d, cy + h, cz + 4, black);
                set(w, cx - 4, cy + h, cz + d, black);
                set(w, cx + 4, cy + h, cz + d, black);
            }

        // A single bulb over the table — the classic interrogation-room look. Rest of the room stays dark.
        set(w, cx, cy + 4, cz - 2, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));

        // Table in front of the clerk (north), x=-1..+1 at z=-2 — single concrete slab of a desk.
        for (int dx = -1; dx <= 1; dx++)
            set(w, cx + dx, cy + 1, cz - 2, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());

        // Answer pads: ДА (lime) left, НЕТ (red) right, symmetric in front of the table; labels on top.
        s.yesPos = new BlockPos(cx - 1, cy + 1, cz - 1);
        s.noPos  = new BlockPos(cx + 1, cy + 1, cz - 1);
        set(w, s.yesPos.getX(), s.yesPos.getY(), s.yesPos.getZ(), Blocks.LIME_CONCRETE.getDefaultState());
        set(w, s.noPos.getX(),  s.noPos.getY(),  s.noPos.getZ(),  Blocks.RED_CONCRETE.getDefaultState());
        standingSign(w, s.yesPos.getX(), s.yesPos.getY() + 1, s.yesPos.getZ(),
                Text.translatableWithFallback("burmalda.debuff.dopros.sign.yes", "§a§lДА"));
        standingSign(w, s.noPos.getX(), s.noPos.getY() + 1, s.noPos.getZ(),
                Text.translatableWithFallback("burmalda.debuff.dopros.sign.no", "§c§lНЕТ"));

        // Door in the south wall (x=0, z=+4) — solid dark wood, fully opaque, no glass or grate.
        // Beyond it is simply the real world, exactly as it already was. No corridor, no teleport.
        // (Early escape is already prevented by the seat leash in onPlayerTick, not by the door itself.)
        BlockState lower = Blocks.DARK_OAK_DOOR.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.DARK_OAK_DOOR.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                .with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        s.doorLower = new BlockPos(cx, cy + 1, cz + 4);
        s.doorUpper = new BlockPos(cx, cy + 2, cz + 4);
        set(w, s.doorLower.getX(), s.doorLower.getY(), s.doorLower.getZ(), lower);
        set(w, s.doorUpper.getX(), s.doorUpper.getY(), s.doorUpper.getZ(), upper);

        spawnClerk(s);
    }

    private void spawnClerk(Session s) {
        ServerWorld world = s.world;
        VillagerEntity clerk = new VillagerEntity(EntityType.VILLAGER, world);
        // Centered in its own floor cell (cz-2.5), a full cell clear of the wall behind it — spawning
        // exactly on a cell boundary (the old cz-3.0) let half its hitbox clip into the wall and suffocate.
        clerk.setPos(s.cx + 0.5, s.cy + 1, s.cz - 2.5);
        //? if >=1.21.5 {
        /*clerk.setVillagerData(clerk.getVillagerData().withProfession(
                world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.VILLAGER_PROFESSION)
                        .getOrThrow(VillagerProfession.LIBRARIAN)));
        *///?} else {
        clerk.setVillagerData(clerk.getVillagerData().withProfession(VillagerProfession.LIBRARIAN));
        //?}
        clerk.setCustomName(Text.translatableWithFallback("burmalda.debuff.dopros.clerk", "§8Сотрудник"));
        clerk.setCustomNameVisible(true);
        clerk.setAiDisabled(true);
        clerk.setPersistent();
        clerk.setYaw(0f); // faces south, toward the suspect
        clerk.setHeadYaw(0f);
        world.spawnEntity(clerk);
        s.clerkUuid = clerk.getUuid();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private VillagerEntity findClerk(Session s) {
        if (s.clerkUuid == null) return null;
        var e = s.world.getEntity(s.clerkUuid);
        return e instanceof VillagerEntity v ? v : null;
    }

    /** Hands the clerk back its AI so it just wanders off naturally, like any other villager. */
    private void releaseClerk(Session s) {
        VillagerEntity clerk = findClerk(s);
        if (clerk != null) clerk.setAiDisabled(false);
    }

    private void standingSign(ServerWorld w, int x, int y, int z, Text... lines) {
        BlockPos p = new BlockPos(x, y, z);
        if (!w.isInBuildLimit(p)) return;
        // Rotation 0 = facing south, readable by the player seated south of the table looking north.
        w.setBlockState(p, Blocks.DARK_OAK_SIGN.getDefaultState().with(Properties.ROTATION, 0), Block.NOTIFY_LISTENERS);
        if (w.getBlockEntity(p) instanceof SignBlockEntity sbe) {
            SignText t = sbe.getFrontText();
            for (int i = 0; i < lines.length && i < 4; i++) t = t.withMessage(i, lines[i]);
            sbe.setText(t, true);
        }
    }

    private void set(ServerWorld w, int x, int y, int z, BlockState state) {
        BlockPos p = new BlockPos(x, y, z);
        if (w.isInBuildLimit(p)) w.setBlockState(p, state, Block.NOTIFY_LISTENERS);
    }
}
