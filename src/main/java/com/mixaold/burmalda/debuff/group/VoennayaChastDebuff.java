package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.*;

public class VoennayaChastDebuff extends Debuff {

    private static final int SOLDIER_COUNT    = 7;
    private static final int COMMAND_INTERVAL = 200; // 10 сек между командами
    private static final int MAX_VIOLATIONS   = 3;
    private static final int COMMANDS_TO_WIN  = 5;
    private static final int LEASH            = 5;   // блоков от позиции
    private static final double MARSH_TARGET       = 5.0;  // блоков для МАРШ
    private static final double BEGOM_MARSH_TARGET = 10.0; // блоков для БЕГОМ МАРШ
    private static final int    JUMP_TARGET        = 10;   // прыжков для ПРЫЖКИ
    private static final Random RANDOM        = new Random();

    private ServerWorld baseWorld;
    private int baseX, baseY, baseZ;

    private int commanderId = -1;
    private final List<Integer> soldierIds = new ArrayList<>();
    private final Map<UUID, BlockPos> formationPos = new HashMap<>();
    // позиции солдат для принудительного возврата: entityId -> [x,y,z]
    private final Map<Integer, double[]> soldierPos = new HashMap<>();

    private final Map<Integer, Float> entityLastHealth = new HashMap<>();

    private int violations   = 0;
    private int commandsDone = 0;
    private boolean finished = false;

    private Command currentCmd    = null;
    private int cmdIssuedAt       = -1;
    private int lastCommandTick   = -COMMAND_INTERVAL; // первая команда через 20 сек

    // для отжиманий / упора лёжа
    private final Map<UUID, Integer> sneakReps  = new HashMap<>();
    private final Map<UUID, Boolean> wasSneaking = new HashMap<>();
    private final Map<UUID, Integer> proneTicks  = new HashMap<>();
    // для марша / бега
    private final Map<UUID, double[]> marchStart = new HashMap<>();
    // для кругом
    private final Map<UUID, Float> yawStart = new HashMap<>();
    // для прыжков
    private final Map<UUID, Integer> jumpReps    = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();

    // for roll-call (перекличка) — who answered "Я!" in chat this command
    private final Set<UUID> rollCallAnswered = new HashSet<>();
    // for запевай — who sent any chat message this command
    private final Set<UUID> zapevaySang = new HashSet<>();

    private enum Command {
        SMIRNO(160), VOLNO(200), MARSH(100), OTZHIMANIYA(300), GAZY(100), RAZOYDIS(200), PEREKLICHKA(200),
        KRUGOM(60), ZAPEVAY(160), UPOR_LYOZHA_PRINYAT(280), BEGOM_MARSH(120), PRYZHKI(200);
        final int dur;
        Command(int d) { this.dur = d; }
    }

    public VoennayaChastDebuff() {
        super("voennaya_chast", "Military Base",
                "Fall in. The commander is not in a good mood.",
                DebuffType.GROUP);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    public void onGroupStart(MinecraftServer server) {
        violations = commandsDone = 0;
        finished = false;
        currentCmd = null;
        cmdIssuedAt = -1;
        lastCommandTick = -COMMAND_INTERVAL;
        soldierIds.clear();
        formationPos.clear();
        soldierPos.clear();
        entityLastHealth.clear();

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> com.mixaold.burmalda.util.BurmaldaCompat.world(p).getRegistryKey() == World.OVERWORLD)
                .toList();
        if (players.isEmpty()) return;

        double avgX = players.stream().mapToDouble(ServerPlayerEntity::getX).average().orElse(0);
        double avgZ = players.stream().mapToDouble(ServerPlayerEntity::getZ).average().orElse(0);

        baseWorld = com.mixaold.burmalda.util.BurmaldaCompat.world(players.get(0));

        baseX = (int) avgX;
        baseZ = (int) avgZ;
        // Средняя высота по площадке — база будет ровной
        int totalY = 0, cnt = 0;
        for (int ox = -35; ox <= 35; ox += 7)
            for (int oz = -35; oz <= 35; oz += 7) {
                totalY += getFloorY(baseWorld, baseX + ox, baseZ + oz);
                cnt++;
            }
        baseY = totalY / cnt;

        buildBase();
        spawnFormation(players);
        spawnCommander();
        spawnGuardGolems();

        server.getPlayerManager().broadcast(Text.translatableWithFallback(
                "burmalda.chat.voennaya_chast.start", "§c[☭ Military Base] §4FALL IN, MAGGOTS!"), false);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (finished || baseWorld == null) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();

        // Завершение по времени — 2 мин 30 сек
        if (ticksActive >= 3000 && !finished) {
            server.getPlayerManager().broadcast(Text.translatableWithFallback(
                    "burmalda.chat.voennaya_chast.times_up",
                    "§7[Commander] That's enough. Fall out. Dismissed, all of you."), false);
            for (int id : soldierIds) {
                if (baseWorld.getEntityById(id) instanceof VillagerEntity s)
                    s.setAiDisabled(false);
            }
            finished = true;
            return;
        }

        // Частицы ГАЗОВ
        if (currentCmd == Command.GAZY && ticksActive % 2 == 0) {
            for (ServerPlayerEntity p : players) {
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(baseWorld, p, ParticleTypes.LARGE_SMOKE, true,
                        p.getX(), p.getY() + 1, p.getZ(), 4, 0.5, 0.5, 0.5, 0.0);
                com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(baseWorld, p, ParticleTypes.SMOKE, true,
                        p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
            }
        }

        // Трекинг отжиманий / упора лёжа
        if (currentCmd == Command.OTZHIMANIYA || currentCmd == Command.UPOR_LYOZHA_PRINYAT) {
            for (ServerPlayerEntity p : players) {
                boolean sneak = p.isSneaking();
                if (currentCmd == Command.OTZHIMANIYA) {
                    boolean was = wasSneaking.getOrDefault(p.getUuid(), false);
                    if (sneak && !was) sneakReps.merge(p.getUuid(), 1, Integer::sum);
                    wasSneaking.put(p.getUuid(), sneak);
                } else if (sneak) {
                    proneTicks.merge(p.getUuid(), 1, Integer::sum);
                }
            }
        }

        // Трекинг прыжков — считаем отрыв от земли (плац ровный, уйти с земли можно только прыжком)
        if (currentCmd == Command.PRYZHKI) {
            for (ServerPlayerEntity p : players) {
                boolean onGround = p.isOnGround();
                if (!onGround && wasOnGround.getOrDefault(p.getUuid(), true))
                    jumpReps.merge(p.getUuid(), 1, Integer::sum);
                wasOnGround.put(p.getUuid(), onGround);
            }
        }

        // Прогресс текущей команды в actionbar
        if (currentCmd != null) updateActionbar(players, ticksActive);

        // Простая отдача солдат на команды (марш на месте, бродят на вольно, и т.д.)
        animateSoldiers(ticksActive);

        // Проверка ударов по командиру/солдатам
        if (ticksActive % 5 == 0) {
            checkEntityHealth(server, players);
            if (finished) return;
        }

        // Проверка строя (каждые 10 тиков, не во время вольного/разойдись/марша/бега)
        if (ticksActive % 10 == 0 && currentCmd != null
                && currentCmd != Command.VOLNO
                && currentCmd != Command.RAZOYDIS
                && currentCmd != Command.MARSH
                && currentCmd != Command.BEGOM_MARSH) {
            checkFormation(server, players);
            if (finished) return;
        }

        // Конец текущей команды
        if (currentCmd != null && ticksActive >= cmdIssuedAt + currentCmd.dur) {
            evaluateCommand(server, players);
            currentCmd = null;
            if (finished) return;
        }

        // Следующая команда
        if (currentCmd == null && (ticksActive - lastCommandTick) >= COMMAND_INTERVAL) {
            issueCommand(server, players, ticksActive);
            lastCommandTick = ticksActive;
        }

        // Командир ходит вдоль шеренги
        if (ticksActive % 80 == 0) nudgeCommander();

    }

    /** Показывает прогресс текущей команды в actionbar, аналогично счётчику отжиманий. */
    private void updateActionbar(List<ServerPlayerEntity> players, int ticksActive) {
        int secLeft = Math.max(0, (cmdIssuedAt + currentCmd.dur) - ticksActive) / 20 + 1;
        switch (currentCmd) {
            case OTZHIMANIYA -> {
                for (ServerPlayerEntity p : players) {
                    int reps = Math.min(10, sneakReps.getOrDefault(p.getUuid(), 0));
                    Text t = Text.translatableWithFallback("burmalda.chat.voennaya_chast.cmd.otzhimaniya.progress",
                            "Push-ups: §f%s§7/10", reps);
                    p.sendMessage(Text.literal(reps >= 10 ? "§a" : "§e").append(t), true);
                }
            }
            case PRYZHKI -> {
                for (ServerPlayerEntity p : players) {
                    int reps = Math.min(JUMP_TARGET, jumpReps.getOrDefault(p.getUuid(), 0));
                    Text t = Text.translatableWithFallback("burmalda.chat.voennaya_chast.cmd.pryzhki.progress",
                            "Jumps: §f%s§7/%s", reps, JUMP_TARGET);
                    p.sendMessage(Text.literal(reps >= JUMP_TARGET ? "§a" : "§e").append(t), true);
                }
            }
            case UPOR_LYOZHA_PRINYAT -> {
                int totalSec = currentCmd.dur / 20;
                for (ServerPlayerEntity p : players) {
                    int heldSec = Math.min(totalSec, proneTicks.getOrDefault(p.getUuid(), 0) / 20);
                    Text t = Text.translatableWithFallback("burmalda.chat.voennaya_chast.cmd.upor_lyozha.progress",
                            "Prone hold: §f%s§7/%ss", heldSec, totalSec);
                    p.sendMessage(Text.literal(heldSec >= totalSec ? "§a" : "§e").append(t), true);
                }
            }
            case MARSH -> {
                for (ServerPlayerEntity p : players) {
                    double dist = marchDistance(p);
                    int shown = (int) Math.min(MARSH_TARGET, dist);
                    Text t = Text.translatableWithFallback("burmalda.chat.voennaya_chast.cmd.marsh.progress",
                            "March: §f%s§7/%s", shown, (int) MARSH_TARGET);
                    p.sendMessage(Text.literal(dist >= MARSH_TARGET ? "§a" : "§e").append(t), true);
                }
            }
            case BEGOM_MARSH -> {
                for (ServerPlayerEntity p : players) {
                    double dist = marchDistance(p);
                    int shown = (int) Math.min(BEGOM_MARSH_TARGET, dist);
                    Text t = Text.translatableWithFallback("burmalda.chat.voennaya_chast.cmd.begom_marsh.progress",
                            "Run: §f%s§7/%s", shown, (int) BEGOM_MARSH_TARGET);
                    p.sendMessage(Text.literal(dist >= BEGOM_MARSH_TARGET ? "§a" : "§e").append(t), true);
                }
            }
            case PEREKLICHKA -> {
                Text t = Text.translatableWithFallback("burmalda.chat.voennaya_chast.cmd.pereklichka.progress",
                        "Roll call: §f%s§7/%s", rollCallAnswered.size(), players.size());
                Text colored = Text.literal(rollCallAnswered.size() >= players.size() ? "§a" : "§e").append(t);
                for (ServerPlayerEntity p : players) p.sendMessage(colored, true);
            }
            case ZAPEVAY -> {
                Text t = Text.translatableWithFallback("burmalda.chat.voennaya_chast.cmd.zapevay.progress",
                        "Singing: §f%s§7/%s", zapevaySang.size(), players.size());
                Text colored = Text.literal(zapevaySang.size() >= players.size() ? "§a" : "§e").append(t);
                for (ServerPlayerEntity p : players) p.sendMessage(colored, true);
            }
            case SMIRNO -> sendCountdown(players, "burmalda.chat.voennaya_chast.cmd.smirno.progress", "Attention: §f%ss", secLeft);
            case VOLNO -> sendCountdown(players, "burmalda.chat.voennaya_chast.cmd.volno.progress", "At ease: §f%ss", secLeft);
            case GAZY -> sendCountdown(players, "burmalda.chat.voennaya_chast.cmd.gazy.progress", "Gas: §f%ss", secLeft);
            case RAZOYDIS -> sendCountdown(players, "burmalda.chat.voennaya_chast.cmd.razoydis.progress", "Dismissed: §f%ss", secLeft);
            case KRUGOM -> sendCountdown(players, "burmalda.chat.voennaya_chast.cmd.krugom.progress", "About-face: §f%ss", secLeft);
        }
    }

    private void sendCountdown(List<ServerPlayerEntity> players, String key, String fallback, int secLeft) {
        Text t = Text.literal("§e").append(Text.translatableWithFallback(key, fallback, secLeft));
        for (ServerPlayerEntity p : players) p.sendMessage(t, true);
    }

    private double marchDistance(ServerPlayerEntity p) {
        double[] s = marchStart.get(p.getUuid());
        if (s == null) return 0;
        double dx = p.getX() - s[0], dz = p.getZ() - s[1];
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Простая визуальная отдача солдат на текущую команду — не статичная стойка. */
    private void animateSoldiers(int ticksActive) {
        if (currentCmd == null) return;
        switch (currentCmd) {
            case MARSH -> bobSoldiers(ticksActive, 8, 0.16);
            case BEGOM_MARSH -> bobSoldiers(ticksActive, 4, 0.22);
            case PRYZHKI -> bobSoldiers(ticksActive, 10, 0.42);
            case OTZHIMANIYA, UPOR_LYOZHA_PRINYAT -> {
                if (ticksActive % 16 == 0) {
                    boolean down = (ticksActive / 16) % 2 == 0;
                    for (int id : soldierIds) {
                        if (baseWorld.getEntityById(id) instanceof VillagerEntity s) {
                            double[] pos = soldierPos.get(id);
                            if (pos == null) continue;
                            s.setPosition(pos[0], pos[1] - (down ? 0.35 : 0), pos[2]);
                        }
                    }
                }
            }
            case ZAPEVAY -> {
                float yaw = 180f + (float) Math.sin(ticksActive * 0.3) * 18f;
                for (int id : soldierIds) {
                    if (baseWorld.getEntityById(id) instanceof VillagerEntity s) s.setHeadYaw(yaw);
                }
            }
            default -> { }
        }
    }

    private void bobSoldiers(int ticksActive, int period, double power) {
        if (ticksActive % period != 0) return;
        for (int id : soldierIds) {
            if (baseWorld.getEntityById(id) instanceof VillagerEntity s && s.isOnGround()) {
                s.setVelocity(0, power, 0);
                com.mixaold.burmalda.util.BurmaldaCompat.markVelocityDirty(s);
            }
        }
    }

    @Override
    public void onGroupEnd(MinecraftServer server) {
        // База и командир остаются навсегда
    }

    // ─── Команды ─────────────────────────────────────────────────────────────────

    private void issueCommand(MinecraftServer server, List<ServerPlayerEntity> players, int tick) {
        Command cmd = Command.values()[RANDOM.nextInt(Command.values().length)];
        currentCmd  = cmd;
        cmdIssuedAt = tick;
        sneakReps.clear(); wasSneaking.clear(); marchStart.clear(); rollCallAnswered.clear();
        proneTicks.clear(); yawStart.clear(); zapevaySang.clear(); jumpReps.clear(); wasOnGround.clear();

        switch (cmd) {
            case SMIRNO -> {
                snapSoldiers();
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.smirno.issue",
                        "§c[Commander] §4§lATTEN-TION!! §cFreeze like statues! Twitch an eyebrow and you're digging trenches to Berlin!"), false);
                for (ServerPlayerEntity p : players)
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 4, false, false));
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 3f, 1.5f);
            }
            case VOLNO -> {
                // Вольно — отпускаем ИИ солдат, пусть бродят
                for (int id : soldierIds) {
                    if (baseWorld.getEntityById(id) instanceof VillagerEntity s) {
                        s.setAiDisabled(false);
                        s.getNavigation().startMovingTo(s.getX() + RANDOM.nextInt(7) - 3, s.getY(), s.getZ() + RANDOM.nextInt(7) - 3, 0.4);
                    }
                }
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.volno.issue",
                        "§a[Commander] §fAT EASE. §7Rest up, troops. I'm being nice — it won't last."), false);
                for (ServerPlayerEntity p : players) {
                    p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 4f));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0, false, false));
                }
            }
            case MARSH -> {
                snapSoldiers();
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.marsh.issue",
                        "§e[Commander] §fFORWARD — §e§lMARCH! §fKnees up! If your soul leaves through your heels, that's normal!"), false);
                for (ServerPlayerEntity p : players)
                    marchStart.put(p.getUuid(), new double[]{p.getX(), p.getZ()});
            }
            case BEGOM_MARSH -> {
                snapSoldiers();
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.begom_marsh.issue",
                        "§e[Commander] §fDOUBLE TIME — §e§lMARCH! §fMove those hooves, the motherland doesn't wait for stragglers!"), false);
                for (ServerPlayerEntity p : players)
                    marchStart.put(p.getUuid(), new double[]{p.getX(), p.getZ()});
            }
            case OTZHIMANIYA -> {
                snapSoldiers();
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.otzhimaniya.issue",
                        "§e[Commander] §fEVERYONE DROP AND GIVE ME §e§l10! §fWe work the abs, nobody asked the body's opinion!"), false);
                for (ServerPlayerEntity p : players) sneakReps.put(p.getUuid(), 0);
            }
            case UPOR_LYOZHA_PRINYAT -> {
                snapSoldiers();
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.upor_lyozha.issue",
                        "§e[Commander] §f§lASSUME THE POSITION! §fDown and don't move, the floor's your new home!"), false);
                for (ServerPlayerEntity p : players) proneTicks.put(p.getUuid(), 0);
            }
            case GAZY -> {
                snapSoldiers();
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.gazy.issue",
                        "§4[Commander] §4§lGA-A-AS!!! §cMask on — or keep sniffing the motherland the hard way!"), false);
                for (ServerPlayerEntity p : players) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA,   100, 0, false, false));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false));
                }
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.AMBIENT, 2f, 0.3f);
            }
            case RAZOYDIS -> {
                for (int id : soldierIds) {
                    if (baseWorld.getEntityById(id) instanceof VillagerEntity s) {
                        s.setAiDisabled(false);
                        s.getNavigation().startMovingTo(s.getX() + RANDOM.nextInt(7) - 3, s.getY(), s.getZ() + RANDOM.nextInt(7) - 3, 0.4);
                    }
                }
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.razoydis.issue",
                        "§7[Commander] Dismissed. Be sharp as cucumbers for the next command, §7troops."), false);
            }
            case PEREKLICHKA -> {
                snapSoldiers();
                String names = players.stream().map(p -> p.getName().getString()).reduce((a, b) -> a + ", " + b).orElse("");
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.pereklichka.issue",
                        "§e[Commander] §f§lROLL CALL! §fI call, you answer! Roster: §7%s", names), false);
                // chat hint: everyone must type "Я"/"here" in chat
                for (ServerPlayerEntity p : players)
                    p.sendMessage(Text.translatableWithFallback(
                            "burmalda.chat.voennaya_chast.cmd.pereklichka.hint",
                            "§6[Roll Call] §fType in chat: §f§lHERE§r §7(or 'present', 'sir yes sir')"), false);
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 3f, 1.3f);
            }
            case ZAPEVAY -> {
                snapSoldiers();
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.zapevay.issue",
                        "§e[Commander] §f§lSTART SINGING! §fThis squad's getting a choir, tone-deaf or not!"), false);
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 3f, 1.0f);
            }
            case KRUGOM -> {
                snapSoldiers();
                for (ServerPlayerEntity p : players) yawStart.put(p.getUuid(), p.getYaw());
                for (int id : soldierIds) {
                    if (baseWorld.getEntityById(id) instanceof VillagerEntity s) {
                        s.setHeadYaw(s.getHeadYaw() + 180f);
                        s.setBodyYaw(s.getBodyYaw() + 180f);
                    }
                }
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.krugom.issue",
                        "§e[Commander] §f§lABOUT — FACE! §fLeft, over the shoulder, snap to it — confused? I'll say it twice!"), false);
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 3f, 1.7f);
            }
            case PRYZHKI -> {
                snapSoldiers();
                for (ServerPlayerEntity p : players) jumpReps.put(p.getUuid(), 0);
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.pryzhki.issue",
                        "§e[Commander] §fJUMP IN PLACE — §e§l10 TIMES! §fHup! Hup! Pretend the ground's lava, maggots!"), false);
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 3f, 1.4f);
            }
        }
    }

    private void evaluateCommand(MinecraftServer server, List<ServerPlayerEntity> players) {
        boolean success = true;

        switch (currentCmd) {
            case MARSH -> {
                for (ServerPlayerEntity p : players) {
                    if (marchDistance(p) < MARSH_TARGET * 0.6) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.marsh.success", "§a[Commander] §fMade it. §6Achievement unlocked: 'Locomotion For Hire'.",
                        "cmd.marsh.fail", "§c[Commander] §fStanding like a fence post?! §4March means WALK, not put down roots!");
            }
            case BEGOM_MARSH -> {
                for (ServerPlayerEntity p : players) {
                    if (marchDistance(p) < BEGOM_MARSH_TARGET * 0.6) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.begom_marsh.success", "§a[Commander] §fNow THAT'S running! §6Achievement unlocked: 'Olympics Cancelled, You Earned It Anyway'.",
                        "cmd.begom_marsh.fail", "§c[Commander] §fWas that running or a pensioner's stroll?! §4Double time, I said!");
            }
            case OTZHIMANIYA -> {
                for (ServerPlayerEntity p : players) {
                    if (sneakReps.getOrDefault(p.getUuid(), 0) < 10) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.otzhimaniya.success", "§a[Commander] §fTen done. §6Achievement unlocked: 'The Floor Loves You Ten Times'.",
                        "cmd.otzhimaniya.fail", "§c[Commander] §fCan't count to ten?! §4Ten, I said! Bunch of noodles!");
            }
            case UPOR_LYOZHA_PRINYAT -> {
                int needTicks = (int) (currentCmd.dur * 0.6);
                for (ServerPlayerEntity p : players) {
                    if (proneTicks.getOrDefault(p.getUuid(), 0) < needTicks) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.upor_lyozha.success", "§a[Commander] §fHeld it like champs. §6Achievement unlocked: 'Horizontal Heroism'.",
                        "cmd.upor_lyozha.fail", "§c[Commander] §fYou stood up?! §4I said LIE DOWN, not stretch!");
            }
            case PEREKLICHKA -> {
                for (ServerPlayerEntity p : players) {
                    if (!rollCallAnswered.contains(p.getUuid())) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.pereklichka.success", "§a[Commander] §fWhole company present. §6Achievement unlocked: 'Nobody Fled (Yet)'.",
                        "cmd.pereklichka.fail", "§c[Commander] §fWho stayed quiet on roll call?! §4Going rogue, huh. Extra duty for you!");
            }
            case ZAPEVAY -> {
                for (ServerPlayerEntity p : players) {
                    if (!zapevaySang.contains(p.getUuid())) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.zapevay.success", "§a[Commander] §fChoir's rough, but everyone sang. §6Achievement unlocked: 'Eurovision Cancelled'.",
                        "cmd.zapevay.fail", "§c[Commander] §fWho stayed silent, look me in the eye?! §4Sing, I said, don't think about it!");
            }
            case KRUGOM -> {
                for (ServerPlayerEntity p : players) {
                    Float start = yawStart.get(p.getUuid());
                    if (start == null) continue;
                    float delta = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(p.getYaw() - start));
                    if (Math.abs(delta - 180f) > 50f) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.krugom.success", "§a[Commander] §fClean turn. §6Achievement unlocked: '360 Minus 180'.",
                        "cmd.krugom.fail", "§c[Commander] §fWas that a turn or were you swatting a fly?! §4About-face, I said!");
            }
            case PRYZHKI -> {
                for (ServerPlayerEntity p : players) {
                    if (jumpReps.getOrDefault(p.getUuid(), 0) < JUMP_TARGET) {
                        success = false;
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false));
                    }
                }
                broadcastOutcome(server, success,
                        "cmd.pryzhki.success", "§a[Commander] §fTen hops, airborne division. §6Achievement unlocked: 'Gravity's Frequent Flyer'.",
                        "cmd.pryzhki.fail", "§c[Commander] §fThat's it?! §4I said jump, not impersonate a sack of potatoes!");
            }
            case SMIRNO -> broadcastOutcome(server, success,
                    "cmd.smirno.success", "§a[Commander] §fStood there like statues. §6Achievement unlocked: 'Furniture With Feelings'.", null, null);
            case VOLNO -> broadcastOutcome(server, success,
                    "cmd.volno.success", "§a[Commander] §fFeeling rested? §6Great, now back to paying off your debt to the motherland.", null, null);
            case GAZY -> broadcastOutcome(server, success,
                    "cmd.gazy.success", "§a[Commander] §fStill breathing? §6Achievement unlocked: 'Lungs Of Steel (Almost)'.", null, null);
            case RAZOYDIS -> broadcastOutcome(server, success,
                    "cmd.razoydis.success", "§a[Commander] §fScattered like pros. §6Achievement unlocked: 'Tactical Dispersal'.", null, null);
        }

        if (success) {
            commandsDone++;
            if (commandsDone >= COMMANDS_TO_WIN) doWinEnd(server, players);
        }
    }

    private void broadcastOutcome(MinecraftServer server, boolean success, String successKey, String successFallback,
                                   String failKey, String failFallback) {
        if (success) {
            server.getPlayerManager().broadcast(Text.translatableWithFallback(
                    "burmalda.chat.voennaya_chast." + successKey, successFallback), false);
        } else if (failKey != null) {
            server.getPlayerManager().broadcast(Text.translatableWithFallback(
                    "burmalda.chat.voennaya_chast." + failKey, failFallback), false);
        }
    }

    @Override
    public void onChat(ServerPlayerEntity sender, String message, MinecraftServer server) {
        if (finished || currentCmd == null) return;
        if (currentCmd == Command.PEREKLICHKA) {
            String m = message.trim().toLowerCase();
            if (m.equals("я") || m.equals("я!") || m.equals("тут") || m.equals("здесь") || m.equals("так точно")
                    || m.equals("here") || m.equals("present") || m.equals("sir yes sir")) {
                if (rollCallAnswered.add(sender.getUuid()))
                    server.getPlayerManager().broadcast(Text.translatableWithFallback(
                            "burmalda.chat.voennaya_chast.cmd.pereklichka.answered",
                            "§a[Commander] §f%s — §apresent and accounted for!", sender.getName().getString()), false);
            }
        } else if (currentCmd == Command.ZAPEVAY) {
            if (!message.isBlank() && zapevaySang.add(sender.getUuid()))
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.cmd.zapevay.sang",
                        "§a[Commander] §f%s §7joined the tune!", sender.getName().getString()), false);
        }
    }

    private void checkFormation(MinecraftServer server, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            BlockPos fpos = formationPos.get(p.getUuid());
            if (fpos == null) continue;
            double dist = com.mixaold.burmalda.util.BurmaldaCompat.pos(p).distanceTo(
                    net.minecraft.util.math.Vec3d.ofCenter(fpos));
            if (dist > LEASH) {
                violations++;
                p.teleport(fpos.getX() + 0.5, fpos.getY(), fpos.getZ() + 0.5, true);
                for (ServerPlayerEntity all : players)
                    all.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false));
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.formation_violation",
                        "§c[Commander] §fPrivate §e%s§f — §4where do you think you're going?! §cBack in line, maggot! Squad — you all get punished!",
                        p.getName().getString()), false);
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 3f, 0.7f);
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 3f, 0.9f);
                if (violations >= MAX_VIOLATIONS) { doFailEnd(server, players); return; }
            }
        }
    }

    private void checkEntityHealth(MinecraftServer server, List<ServerPlayerEntity> players) {
        if (commanderId != -1 && baseWorld.getEntityById(commanderId) instanceof VillagerEntity cmd) {
            float last = entityLastHealth.getOrDefault(commanderId, cmd.getMaxHealth());
            if (cmd.getHealth() < last - 0.1f) {
                LivingEntity atk = cmd.getAttacker();
                String name = atk != null ? atk.getName().getString() : "someone";
                server.getPlayerManager().broadcast(Text.translatableWithFallback(
                        "burmalda.chat.voennaya_chast.commander_hit",
                        "§4[Commander] §cWHO TOUCHED THE COMMANDER?! §4%s§c — §4TO THE FIRING SQUAD! SQUAD — YOU ALL GET PUNISHED!",
                        name), false);
                if (atk instanceof ServerPlayerEntity sp) {
                    sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER,    200, 2, false, true, true));
                    sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,  300, 4, false, true, true));
                    sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, true, true));
                    com.mixaold.burmalda.util.BurmaldaCompat.damage(sp,
                            com.mixaold.burmalda.util.BurmaldaCompat.world(sp).getDamageSources().generic(), 6f);
                    com.mixaold.burmalda.advancement.BurmaldaAdvancements.trigger(sp, com.mixaold.burmalda.advancement.BurmaldaAdvancements.COMMANDER_TOUCHED);
                }
                for (ServerPlayerEntity p : players)
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 300, 2, false, false));
                cmd.setHealth(cmd.getMaxHealth());
                baseWorld.playSound(null, baseX, baseY, baseZ,
                        SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 4f, 0.5f);
                violations += 2;
                if (violations >= MAX_VIOLATIONS) { doFailEnd(server, players); return; }
            }
            entityLastHealth.put(commanderId, cmd.getHealth());
        }

        for (int id : soldierIds) {
            if (baseWorld.getEntityById(id) instanceof VillagerEntity s) {
                float last = entityLastHealth.getOrDefault(id, s.getMaxHealth());
                if (s.getHealth() < last - 0.1f) {
                    LivingEntity atk = s.getAttacker();
                    String name = atk != null ? atk.getName().getString() : "someone";
                    server.getPlayerManager().broadcast(Text.translatableWithFallback(
                            "burmalda.chat.voennaya_chast.soldier_hit",
                            "§c[Commander] §e%s§c — §4YOU LOST YOUR MIND HITTING A PRIVATE?! SQUAD — EVERYONE GETS WEAKNESS!",
                            name), false);
                    if (atk instanceof ServerPlayerEntity sp) {
                        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER,   100, 1, false, true, true));
                        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 3, false, true, true));
                        com.mixaold.burmalda.util.BurmaldaCompat.damage(sp,
                                com.mixaold.burmalda.util.BurmaldaCompat.world(sp).getDamageSources().generic(), 3f);
                    }
                    for (ServerPlayerEntity p : players)
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false));
                    s.setHealth(s.getMaxHealth());
                    violations++;
                    if (violations >= MAX_VIOLATIONS) { doFailEnd(server, players); return; }
                }
                entityLastHealth.put(id, s.getHealth());
            }
        }
    }

    private void doWinEnd(MinecraftServer server, List<ServerPlayerEntity> players) {
        finished = true;
        for (int id : soldierIds) {
            if (baseWorld.getEntityById(id) instanceof VillagerEntity s)
                s.setAiDisabled(false);
        }
        server.getPlayerManager().broadcast(Text.translatableWithFallback(
                "burmalda.chat.voennaya_chast.win_end",
                "§a[Commander] §fTour's over. §6§lDISCHARGE! §fMedal and rations for everyone — you earned it."), false);

        // Dembel reward: a named medal + a ration, and a firework salute over the base.
        net.minecraft.item.ItemStack medal = new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLD_INGOT);
        medal.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.translatableWithFallback(
                "burmalda.chat.voennaya_chast.medal_name", "§6§lMedal 'For Discharge'"));
        for (ServerPlayerEntity p : players) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,    600, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 400, 0));
            p.giveItemStack(medal.copy());
            p.giveItemStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.COOKED_BEEF, 16));
            com.mixaold.burmalda.advancement.BurmaldaAdvancements.trigger(p, com.mixaold.burmalda.advancement.BurmaldaAdvancements.DEMBEL);
            com.mixaold.burmalda.util.BurmaldaCompat.spawnParticles(baseWorld, p, ParticleTypes.FIREWORK, true,
                    baseX + 0.5, baseY + 6, baseZ + 0.5, 100, 5, 2.5, 5, 0.25);
        }
        baseWorld.playSound(null, baseX, baseY + 6, baseZ, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 3f, 1f);
        baseWorld.playSound(null, baseX, baseY + 6, baseZ, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,  SoundCategory.PLAYERS, 3f, 1f);
    }

    private void doFailEnd(MinecraftServer server, List<ServerPlayerEntity> players) {
        finished = true;
        for (int id : soldierIds) {
            if (baseWorld.getEntityById(id) instanceof VillagerEntity s)
                s.setAiDisabled(false);
        }
        server.getPlayerManager().broadcast(Text.translatableWithFallback(
                "burmalda.chat.voennaya_chast.fail_end",
                "§c[Commander] §4Hell of a way to run an army. §cExtra duty for ALL of you!"), false);
        for (ServerPlayerEntity p : players) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,       600, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 600, 1));
        }
    }

    // ─── Спавн ───────────────────────────────────────────────────────────────────

    private void spawnFormation(List<ServerPlayerEntity> players) {
        int total  = players.size() + SOLDIER_COUNT;
        int startX = baseX - (total - 1);
        int fy = baseY + 1; // пол базы на baseY, стоят на baseY+1

        // Игроки
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity p = players.get(i);
            int fx = startX + i * 2;
            int fz = baseZ + 10;
            formationPos.put(p.getUuid(), new BlockPos(fx, fy, fz));
            p.teleport(fx + 0.5, fy, fz + 0.5, true);
        }

        // Солдаты-жители
        for (int i = 0; i < SOLDIER_COUNT; i++) {
            int fx = startX + (players.size() + i) * 2;
            int fz = baseZ + 10;

            VillagerEntity s = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.VILLAGER, baseWorld);
            if (s == null) continue;
            s.setPosition(fx + 0.5, fy, fz + 0.5);
            s.setCustomName(Text.translatableWithFallback("burmalda.voennaya_chast.soldier_name", "§7[Private]"));
            s.setCustomNameVisible(true);
            s.setPersistent();
            s.setHeadYaw(180f);
            s.setBodyYaw(180f);
            s.setAiDisabled(true); // не бегают куда попало
            baseWorld.spawnEntity(s);
            soldierIds.add(s.getId());
            soldierPos.put(s.getId(), new double[]{fx + 0.5, fy, fz + 0.5});
        }
    }

    private void spawnCommander() {
        VillagerEntity cmd = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.VILLAGER, baseWorld);
        if (cmd == null) return;
        cmd.setPosition(baseX + 0.5, baseY + 1, baseZ + 0.5);
        cmd.setCustomName(Text.translatableWithFallback("burmalda.voennaya_chast.commander_name", "§c[Commander]"));
        cmd.setCustomNameVisible(true);
        cmd.setPersistent();
        cmd.setHeadYaw(0f);
        cmd.setBodyYaw(0f);
        baseWorld.spawnEntity(cmd);
        commanderId = cmd.getId();
    }

    /** Телепортирует солдат на их позиции и отключает ИИ — вызывается на каждой строевой команде. */
    private void snapSoldiers() {
        for (int id : soldierIds) {
            if (baseWorld.getEntityById(id) instanceof VillagerEntity s) {
                double[] pos = soldierPos.get(id);
                if (pos == null) continue;
                s.setPosition(pos[0], pos[1], pos[2]);
                s.setVelocity(0, 0, 0);
                s.setHeadYaw(180f);
                s.setBodyYaw(180f);
                s.setAiDisabled(true);
            }
        }
    }

    private void nudgeCommander() {
        if (commanderId == -1) return;
        if (baseWorld.getEntityById(commanderId) instanceof VillagerEntity cmd) {
            double nx = baseX + RANDOM.nextInt(9) - 4;
            cmd.getNavigation().startMovingTo(nx, cmd.getY(), baseZ, 0.5);
        }
    }

    // ─── Постройка базы ───────────────────────────────────────────────────────────

    private void buildBase() {
        clearAndFlatten();
        buildParadeGround();
        buildPerimeter();
        buildBarracks();
        buildHQ();
        buildRunway();
        buildFlagpole();
        buildGreenery();
        buildVehicles();
        buildExtras();
    }

    /** Сносит деревья/холмы, заполняет ямы камнем, кладёт серый бетонный пол. */
    private void clearAndFlatten() {
        int r = 38;
        for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
                int wx = baseX + ox, wz = baseZ + oz;
                // Снести всё от baseY+25 вниз до baseY+1 (деревья, холмы, блоки)
                for (int dy = 25; dy >= 1; dy--) {
                    BlockPos pos = new BlockPos(wx, baseY + dy, wz);
                    if (!baseWorld.isInBuildLimit(pos)) continue;
                    BlockState st = baseWorld.getBlockState(pos);
                    if (!st.isAir() && st.getBlock() != Blocks.BEDROCK)
                        baseWorld.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Заполнить ямы ниже baseY камнем (до 4 блоков вглубь)
                for (int dy = -1; dy >= -4; dy--) {
                    BlockPos pos = new BlockPos(wx, baseY + dy, wz);
                    if (!baseWorld.isInBuildLimit(pos)) continue;
                    BlockState st = baseWorld.getBlockState(pos);
                    if (st.isAir() || st.getFluidState().isStill())
                        baseWorld.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Пол — серый бетон по всей площадке
                set(ox, 0, oz, Blocks.GRAY_CONCRETE.getDefaultState());
            }
        }
    }

    private void buildParadeGround() {
        // Плац — тёмный бетон в центре
        for (int ox = -17; ox <= 17; ox++)
            for (int oz = -7; oz <= 14; oz++)
                set(ox, 0, oz, Blocks.GRAY_CONCRETE.getDefaultState());
        // Белая линия шеренги
        for (int ox = -17; ox <= 17; ox++)
            set(ox, 0, 10, Blocks.WHITE_CONCRETE.getDefaultState());
        // Центральная дорожка командира
        for (int oz = -6; oz <= 9; oz++)
            set(0, 0, oz, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
    }

    private void buildPerimeter() {
        int r = 38;
        for (int i = -r; i <= r; i++) {
            wallCol(i, -r);
            if (Math.abs(i) > 5) wallCol(i, r); // ворота на юге шириной 10
            wallCol(-r, i);
            wallCol(r, i);
        }
        // Арка над воротами (5 высота)
        for (int ox = -5; ox <= 5; ox++) {
            set(ox, 4, r, Blocks.STONE_BRICKS.getDefaultState());
            set(ox, 5, r, Blocks.STONE_BRICKS.getDefaultState());
        }
        set(-5, 3, r, Blocks.STONE_BRICKS.getDefaultState());
        set( 5, 3, r, Blocks.STONE_BRICKS.getDefaultState());
        // Фонари у ворот
        set(-6, 5, r, Blocks.SEA_LANTERN.getDefaultState());
        set( 6, 5, r, Blocks.SEA_LANTERN.getDefaultState());
        // Угловые башни
        buildTower(-r, -r); buildTower(r, -r);
        buildTower(-r, r);  buildTower(r, r);
    }

    private void wallCol(int ox, int oz) {
        set(ox, 1, oz, Blocks.STONE_BRICKS.getDefaultState());
        set(ox, 2, oz, Blocks.STONE_BRICKS.getDefaultState());
        set(ox, 3, oz, Blocks.STONE_BRICKS.getDefaultState());
        set(ox, 4, oz, Blocks.IRON_BARS.getDefaultState());
    }

    private void buildTower(int ox, int oz) {
        for (int y = 1; y <= 7; y++) set(ox, y, oz, Blocks.STONE_BRICKS.getDefaultState());
        // Платформа башни 3×3
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                set(ox + dx, 8, oz + dz, Blocks.STONE_BRICKS.getDefaultState());
        set(ox, 9, oz, Blocks.SEA_LANTERN.getDefaultState());
    }

    private void buildBarracks() {
        int x1 = -36, x2 = -20, z1 = -8, z2 = 13;
        buildBox(x1, z1, x2, z2, Blocks.BRICKS, 5);

        // Светлый пол внутри
        for (int ox = x1 + 1; ox <= x2 - 1; ox++)
            for (int oz = z1 + 1; oz <= z2 - 1; oz++)
                set(ox, 0, oz, Blocks.SMOOTH_STONE.getDefaultState());

        // Окна 2 блока высотой
        for (int oz = z1 + 2; oz < z2 - 2; oz += 4) {
            set(x1, 2, oz, Blocks.GLASS.getDefaultState());
            set(x1, 3, oz, Blocks.GLASS.getDefaultState());
            set(x2, 2, oz, Blocks.GLASS.getDefaultState());
            set(x2, 3, oz, Blocks.GLASS.getDefaultState());
        }

        // Плотные фонари
        for (int oz = z1 + 2; oz <= z2 - 2; oz += 3)
            set((x1 + x2) / 2, 5, oz, Blocks.LANTERN.getDefaultState());

        // Кровати с обеих сторон — голова у стены, ноги в комнату
        BlockState bedHead = Blocks.RED_BED.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.NORTH);
        BlockState bedFoot = Blocks.RED_BED.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.NORTH);
        for (int oz = z1 + 2; oz <= z2 - 5; oz += 3) {
            set(x1 + 2, 1, oz,     bedHead);
            set(x1 + 2, 1, oz + 1, bedFoot);
            set(x2 - 2, 1, oz,     bedHead);
            set(x2 - 2, 1, oz + 1, bedFoot);
        }

        // Сундуки у северной стены — смотрят в комнату (SOUTH)
        BlockState chest = Blocks.CHEST.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
        set(x1 + 2, 1, z1 + 1, chest);
        set(x1 + 3, 1, z1 + 1, chest);
        set(x2 - 2, 1, z1 + 1, chest);
        set(x2 - 3, 1, z1 + 1, chest);

        // Длинный стол по центру
        int cx = (x1 + x2) / 2;
        set(cx, 1, 1, Blocks.OAK_FENCE.getDefaultState());
        set(cx, 1, 4, Blocks.OAK_FENCE.getDefaultState());
        for (int oz = 1; oz <= 4; oz++)
            set(cx, 2, oz, Blocks.OAK_SLAB.getDefaultState());
    }

    private void buildHQ() {
        int x1 = 20, x2 = 36, z1 = -8, z2 = 13;
        buildBox(x1, z1, x2, z2, Blocks.STONE_BRICKS, 5);

        // Пол
        for (int ox = x1 + 1; ox <= x2 - 1; ox++)
            for (int oz = z1 + 1; oz <= z2 - 1; oz++)
                set(ox, 0, oz, Blocks.SMOOTH_STONE.getDefaultState());

        // Окна
        for (int oz = z1 + 2; oz < z2 - 2; oz += 4) {
            set(x1, 2, oz, Blocks.GLASS.getDefaultState());
            set(x1, 3, oz, Blocks.GLASS.getDefaultState());
            set(x2, 2, oz, Blocks.GLASS.getDefaultState());
            set(x2, 3, oz, Blocks.GLASS.getDefaultState());
        }

        // Фонари по центру потолка
        set(28, 5, -4, Blocks.LANTERN.getDefaultState());
        set(28, 5,  4, Blocks.LANTERN.getDefaultState());
        set(28, 5, 10, Blocks.LANTERN.getDefaultState());

        // Угол командира (СЗ): книжные полки вдоль западной стены
        for (int oz = z1 + 1; oz <= -2; oz++) {
            set(x1 + 1, 1, oz, Blocks.BOOKSHELF.getDefaultState());
            set(x1 + 1, 2, oz, Blocks.BOOKSHELF.getDefaultState());
        }
        // Стол командира: ножки забором + столешница из плит
        for (int oz = -5; oz <= -3; oz++) {
            set(x1 + 2, 1, oz, Blocks.OAK_FENCE.getDefaultState());
            set(x1 + 2, 2, oz, Blocks.OAK_SLAB.getDefaultState());
            set(x1 + 3, 1, oz, Blocks.OAK_FENCE.getDefaultState());
            set(x1 + 3, 2, oz, Blocks.OAK_SLAB.getDefaultState());
        }
        set(x1 + 4, 1, -4, Blocks.CRAFTING_TABLE.getDefaultState());

        // Стол брифинга (центр): терракотовый пол + ножки + плита
        for (int ox = 25; ox <= 31; ox++)
            for (int oz = 0; oz <= 4; oz++)
                set(ox, 0, oz, Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState());
        for (int tx : new int[]{25, 28, 31}) {
            set(tx, 1, 0, Blocks.OAK_FENCE.getDefaultState());
            set(tx, 1, 4, Blocks.OAK_FENCE.getDefaultState());
        }
        for (int ox = 25; ox <= 31; ox++)
            for (int oz = 0; oz <= 4; oz++)
                set(ox, 2, oz, Blocks.OAK_SLAB.getDefaultState());

        // Баннеры — настенные, привешены к западной стене и смотрят В комнату (на восток)
        BlockState redWall   = Blocks.RED_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST);
        BlockState whiteWall = Blocks.WHITE_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST);
        set(x1 + 1, 4, -5, redWall);
        set(x1 + 1, 3, -3, redWall);
        set(x1 + 1, 4,  0, whiteWall);
        set(x1 + 1, 3,  3, redWall);
        set(x1 + 1, 4,  6, redWall);

        // Хранилище вдоль восточной стены
        BlockState chest = Blocks.CHEST.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.WEST);
        set(x2 - 1, 1, z1 + 2, chest);
        set(x2 - 1, 1, z1 + 5, chest);
        set(x2 - 1, 1, 8,  chest);
        set(x2 - 1, 1, 10, Blocks.BARREL.getDefaultState());
    }

    /** Коробка: только стены + крыша + дверь. Пол уже залит clearAndFlatten. */
    private void buildBox(int ox1, int oz1, int ox2, int oz2, Block wall, int height) {
        for (int y = 1; y <= height; y++) {
            for (int ox = ox1; ox <= ox2; ox++) {
                set(ox, y, oz1, wall.getDefaultState());
                set(ox, y, oz2, wall.getDefaultState());
            }
            for (int oz = oz1 + 1; oz < oz2; oz++) {
                set(ox1, y, oz, wall.getDefaultState());
                set(ox2, y, oz, wall.getDefaultState());
            }
        }
        // Крыша
        for (int ox = ox1; ox <= ox2; ox++)
            for (int oz = oz1; oz <= oz2; oz++)
                set(ox, height + 1, oz, wall.getDefaultState());
        // Дверь на юге (центр), 2 блока высота
        int doorX = (ox1 + ox2) / 2;
        set(doorX,     1, oz2, Blocks.AIR.getDefaultState());
        set(doorX,     2, oz2, Blocks.AIR.getDefaultState());
        set(doorX - 1, 1, oz2, Blocks.AIR.getDefaultState());
        set(doorX - 1, 2, oz2, Blocks.AIR.getDefaultState());
    }

    private void buildRunway() {
        // Горизонтальная взлётка: слева-направо (вдоль X), полная ширина базы
        int rx1 = -36, rx2 = 36;   // длина (запад-восток)
        int rz1 = -35, rz2 = -21;  // ширина (14 блоков)
        int rzMid = (rz1 + rz2) / 2;

        for (int ox = rx1; ox <= rx2; ox++) {
            for (int oz = rz1; oz <= rz2; oz++) {
                boolean isEdge      = (oz == rz1 || oz == rz2);
                boolean isLight     = isEdge && ox % 4 == 0;
                boolean isCenterLine = (oz == rzMid) && (ox + 36) % 8 < 3;
                set(ox, 0, oz, isLight       ? Blocks.SEA_LANTERN.getDefaultState()
                             : isEdge || isCenterLine ? Blocks.WHITE_CONCRETE.getDefaultState()
                             : Blocks.GRAY_CONCRETE.getDefaultState());
            }
        }
        // Пороговые полосы на торцах (запад и восток)
        for (int oz = rz1 + 1; oz <= rz2 - 1; oz++) {
            set(rx1,     0, oz, Blocks.YELLOW_CONCRETE.getDefaultState());
            set(rx1 + 1, 0, oz, Blocks.YELLOW_CONCRETE.getDefaultState());
            set(rx2,     0, oz, Blocks.YELLOW_CONCRETE.getDefaultState());
            set(rx2 - 1, 0, oz, Blocks.YELLOW_CONCRETE.getDefaultState());
        }
    }

    private void buildFlagpole() {
        // Флагшток между плацом и взлёткой
        for (int y = 1; y <= 10; y++) set(0, y, -14, Blocks.OAK_FENCE.getDefaultState());
        set(0, 11, -14, Blocks.JACK_O_LANTERN.getDefaultState());
        // Фонари у основания
        set(-2, 1, -14, Blocks.SEA_LANTERN.getDefaultState());
        set( 2, 1, -14, Blocks.SEA_LANTERN.getDefaultState());
    }

    private void buildGreenery() {
        // Между взлёткой (z=-21) и плацем (z=-7): полосы по бокам
        fillGrass(-19, -20, -8, -9);
        fillGrass(  8, -20,  19, -9);
        // Юго-запад (левее входной дороги x=-6..6)
        fillGrass(-37, 14, -7, 37);
        // Юго-восток
        fillGrass(  7, 14,  37, 37);
        // 3 компактных дерева в южной зоне
        placeTree(-22, 27);
        placeTree(  6, 25);
        placeTree( 24, 29);
    }

    private void fillGrass(int ox1, int oz1, int ox2, int oz2) {
        for (int ox = ox1; ox <= ox2; ox++)
            for (int oz = oz1; oz <= oz2; oz++)
                set(ox, 0, oz, Blocks.GRASS_BLOCK.getDefaultState());
    }

    private void placeTree(int ox, int oz) {
        // Короткий ствол (2 блока)
        set(ox, 1, oz, Blocks.OAK_LOG.getDefaultState());
        set(ox, 2, oz, Blocks.OAK_LOG.getDefaultState());
        // Крона: 3×3 снизу, крест посередине, одиночная вершина
        BlockState leaf = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                set(ox + dx, 3, oz + dz, leaf);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (!(Math.abs(dx) == 1 && Math.abs(dz) == 1))
                    set(ox + dx, 4, oz + dz, leaf);
        set(ox, 5, oz, leaf);
    }

    private void buildVehicles() {
        buildTank(-14, -3);
        buildTank(  8,  1);
    }

    private void buildTank(int ox, int oz) {
        // Гусеницы (6 блоков, 3 дорожки)
        for (int dx = 0; dx <= 5; dx++) {
            set(ox + dx, 1, oz,     Blocks.BLACK_CONCRETE.getDefaultState());
            set(ox + dx, 1, oz + 1, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
            set(ox + dx, 1, oz + 2, Blocks.BLACK_CONCRETE.getDefaultState());
        }
        // Корпус
        for (int dx = 0; dx <= 5; dx++)
            for (int dz = 0; dz <= 2; dz++)
                set(ox + dx, 2, oz + dz, Blocks.GRAY_CONCRETE.getDefaultState());
        // Лобовая броня
        for (int dz = 0; dz <= 2; dz++)
            set(ox + 5, 2, oz + dz, Blocks.IRON_BLOCK.getDefaultState());
        // Башня 3×3
        for (int dx = 1; dx <= 3; dx++)
            for (int dz = 0; dz <= 2; dz++)
                set(ox + dx, 3, oz + dz, Blocks.GRAY_CONCRETE.getDefaultState());
        // Ствол (3 блока)
        set(ox + 4, 3, oz + 1, Blocks.IRON_BARS.getDefaultState());
        set(ox + 5, 3, oz + 1, Blocks.IRON_BARS.getDefaultState());
        set(ox + 6, 3, oz + 1, Blocks.IRON_BARS.getDefaultState());
        // Антенна
        set(ox + 1, 4, oz + 2, Blocks.OAK_FENCE.getDefaultState());
    }

    private void buildExtras() {
        buildObstacleCourse(-35, -19);
        buildShootingRange(22, 16);
        buildAAGun(-11, 20);
        buildAAGun(7, 20);
        buildRadioTower(12, -17);
        buildTrenches();
        buildHelipad(29, -15);
        buildTent(-34, 15, 10);
        buildTent(-34, 23, 10);
        buildTent(9, 15, 10);
    }

    private void buildObstacleCourse(int ox, int oz) {
        // Перекладины на кольях
        for (int i = 0; i < 4; i++) {
            set(ox + i * 3,     1, oz, Blocks.OAK_FENCE.getDefaultState());
            set(ox + i * 3,     2, oz, Blocks.OAK_LOG.getDefaultState());
            set(ox + i * 3 + 2, 1, oz, Blocks.OAK_FENCE.getDefaultState());
        }
        // Стена для лазания
        for (int y = 1; y <= 4; y++)
            for (int dx = 0; dx <= 3; dx++)
                set(ox + dx, y, oz + 4, Blocks.COBBLESTONE_WALL.getDefaultState());
        // Низкие барьеры
        for (int i = 0; i < 4; i++) {
            set(ox + i * 3,     1, oz + 8, Blocks.COBBLESTONE.getDefaultState());
            set(ox + i * 3 + 1, 1, oz + 8, Blocks.COBBLESTONE.getDefaultState());
        }
        for (int i = 0; i < 3; i++)
            set(ox + i * 3 + 1, 2, oz + 8, Blocks.SAND.getDefaultState());
    }

    private void buildShootingRange(int ox, int oz) {
        // Линия огня (мешки с песком)
        for (int dx = 0; dx < 10; dx++)
            set(ox + dx, 1, oz, Blocks.SAND.getDefaultState());
        // Мишени (сено + красная терракота)
        for (int i = 0; i < 4; i++) {
            int tz = oz + 6 + i * 3;
            set(ox + i * 3,     1, tz, Blocks.HAY_BLOCK.getDefaultState());
            set(ox + i * 3,     2, tz, Blocks.HAY_BLOCK.getDefaultState());
            set(ox + i * 3,     3, tz, Blocks.RED_TERRACOTTA.getDefaultState());
            set(ox + i * 3 + 1, 1, tz, Blocks.HAY_BLOCK.getDefaultState());
            set(ox + i * 3 + 1, 2, tz, Blocks.HAY_BLOCK.getDefaultState());
            set(ox + i * 3 + 1, 3, tz, Blocks.RED_TERRACOTTA.getDefaultState());
        }
    }

    private void buildAAGun(int ox, int oz) {
        // Колёса (основание)
        set(ox - 1, 1, oz,     Blocks.BLACK_CONCRETE.getDefaultState());
        set(ox - 1, 1, oz + 1, Blocks.BLACK_CONCRETE.getDefaultState());
        set(ox + 2, 1, oz,     Blocks.BLACK_CONCRETE.getDefaultState());
        set(ox + 2, 1, oz + 1, Blocks.BLACK_CONCRETE.getDefaultState());
        // Платформа
        set(ox,     1, oz,     Blocks.IRON_BLOCK.getDefaultState());
        set(ox + 1, 1, oz,     Blocks.IRON_BLOCK.getDefaultState());
        set(ox,     1, oz + 1, Blocks.IRON_BLOCK.getDefaultState());
        set(ox + 1, 1, oz + 1, Blocks.IRON_BLOCK.getDefaultState());
        // Поворотная база
        set(ox,     2, oz,     Blocks.GRAY_CONCRETE.getDefaultState());
        set(ox + 1, 2, oz,     Blocks.GRAY_CONCRETE.getDefaultState());
        set(ox,     2, oz + 1, Blocks.GRAY_CONCRETE.getDefaultState());
        set(ox + 1, 2, oz + 1, Blocks.GRAY_CONCRETE.getDefaultState());
        // Стволы (iron bars вверх)
        for (int y = 3; y <= 6; y++) {
            set(ox,     y, oz, Blocks.IRON_BARS.getDefaultState());
            set(ox + 1, y, oz, Blocks.IRON_BARS.getDefaultState());
        }
    }

    private void buildRadioTower(int ox, int oz) {
        // Башня из железных прутьев
        for (int y = 1; y <= 12; y++) {
            set(ox,     y, oz,     Blocks.IRON_BARS.getDefaultState());
            set(ox + 1, y, oz,     Blocks.IRON_BARS.getDefaultState());
            set(ox,     y, oz + 1, Blocks.IRON_BARS.getDefaultState());
            set(ox + 1, y, oz + 1, Blocks.IRON_BARS.getDefaultState());
        }
        // Антенна
        set(ox,     13, oz, Blocks.OAK_FENCE.getDefaultState());
        set(ox + 1, 13, oz, Blocks.OAK_FENCE.getDefaultState());
        set(ox,     14, oz, Blocks.SEA_LANTERN.getDefaultState());
        // Опоры у основания
        set(ox - 2, 1, oz - 2, Blocks.IRON_BARS.getDefaultState());
        set(ox + 3, 1, oz - 2, Blocks.IRON_BARS.getDefaultState());
        set(ox - 2, 1, oz + 3, Blocks.IRON_BARS.getDefaultState());
        set(ox + 3, 1, oz + 3, Blocks.IRON_BARS.getDefaultState());
    }

    private void buildTrenches() {
        // Западная траншея (z=30)
        for (int dx = -37; dx <= -22; dx++) {
            set(dx, -1, 30, Blocks.AIR.getDefaultState());
            set(dx,  0, 30, Blocks.AIR.getDefaultState());
            set(dx,  0, 29, Blocks.DIRT.getDefaultState());
            set(dx,  1, 29, Blocks.DIRT.getDefaultState());
            set(dx,  0, 31, Blocks.DIRT.getDefaultState());
            set(dx,  1, 31, Blocks.DIRT.getDefaultState());
        }
        for (int dx = -37; dx <= -22; dx += 4) {
            set(dx, 1, 29, Blocks.SAND.getDefaultState());
            set(dx, 1, 31, Blocks.SAND.getDefaultState());
        }
        // Восточная траншея (z=30)
        for (int dx = 22; dx <= 37; dx++) {
            set(dx, -1, 30, Blocks.AIR.getDefaultState());
            set(dx,  0, 30, Blocks.AIR.getDefaultState());
            set(dx,  0, 29, Blocks.DIRT.getDefaultState());
            set(dx,  1, 29, Blocks.DIRT.getDefaultState());
            set(dx,  0, 31, Blocks.DIRT.getDefaultState());
            set(dx,  1, 31, Blocks.DIRT.getDefaultState());
        }
        for (int dx = 22; dx <= 37; dx += 4) {
            set(dx, 1, 29, Blocks.SAND.getDefaultState());
            set(dx, 1, 31, Blocks.SAND.getDefaultState());
        }
    }

    private void buildHelipad(int ox, int oz) {
        // Белая площадка 7×7 с обрезанными углами
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (!(Math.abs(dx) == 3 && Math.abs(dz) == 3))
                    set(ox + dx, 0, oz + dz, Blocks.WHITE_CONCRETE.getDefaultState());
        // Жёлтый контур
        for (int dx = -2; dx <= 2; dx++) {
            set(ox + dx, 0, oz - 3, Blocks.YELLOW_CONCRETE.getDefaultState());
            set(ox + dx, 0, oz + 3, Blocks.YELLOW_CONCRETE.getDefaultState());
        }
        for (int dz = -2; dz <= 2; dz++) {
            set(ox - 3, 0, oz + dz, Blocks.YELLOW_CONCRETE.getDefaultState());
            set(ox + 3, 0, oz + dz, Blocks.YELLOW_CONCRETE.getDefaultState());
        }
        // Буква H
        for (int dz = -2; dz <= 2; dz++) {
            set(ox - 2, 0, oz + dz, Blocks.YELLOW_CONCRETE.getDefaultState());
            set(ox + 2, 0, oz + dz, Blocks.YELLOW_CONCRETE.getDefaultState());
        }
        set(ox - 1, 0, oz, Blocks.YELLOW_CONCRETE.getDefaultState());
        set(ox,     0, oz, Blocks.YELLOW_CONCRETE.getDefaultState());
        set(ox + 1, 0, oz, Blocks.YELLOW_CONCRETE.getDefaultState());
        // Угловые маячки
        set(ox - 4, 1, oz - 4, Blocks.SEA_LANTERN.getDefaultState());
        set(ox + 4, 1, oz - 4, Blocks.SEA_LANTERN.getDefaultState());
        set(ox - 4, 1, oz + 4, Blocks.SEA_LANTERN.getDefaultState());
        set(ox + 4, 1, oz + 4, Blocks.SEA_LANTERN.getDefaultState());
    }

    private void buildTent(int ox, int oz, int length) {
        // Пол
        for (int dx = 0; dx < length; dx++)
            for (int dz = 0; dz < 5; dz++)
                set(ox + dx, 0, oz + dz, Blocks.LIGHT_GRAY_WOOL.getDefaultState());
        // Боковые длинные стены
        for (int y = 1; y <= 2; y++)
            for (int dx = 0; dx < length; dx++) {
                set(ox + dx, y, oz,     Blocks.GREEN_TERRACOTTA.getDefaultState());
                set(ox + dx, y, oz + 4, Blocks.GREEN_TERRACOTTA.getDefaultState());
            }
        // Торцевые стены
        for (int y = 1; y <= 2; y++)
            for (int dz = 1; dz <= 3; dz++) {
                set(ox,              y, oz + dz, Blocks.GREEN_TERRACOTTA.getDefaultState());
                set(ox + length - 1, y, oz + dz, Blocks.GREEN_TERRACOTTA.getDefaultState());
            }
        // Фронтоны торцов
        for (int dz = 0; dz <= 4; dz++) {
            set(ox,              3, oz + dz, Blocks.GREEN_TERRACOTTA.getDefaultState());
            set(ox + length - 1, 3, oz + dz, Blocks.GREEN_TERRACOTTA.getDefaultState());
        }
        set(ox,              4, oz + 2, Blocks.GREEN_TERRACOTTA.getDefaultState());
        set(ox + length - 1, 4, oz + 2, Blocks.GREEN_TERRACOTTA.getDefaultState());
        // Крыша A-frame (пропускаем торцы — уже GREEN_TERRACOTTA)
        for (int dx = 1; dx < length - 1; dx++) {
            set(ox + dx, 3, oz,     Blocks.MOSS_BLOCK.getDefaultState());
            set(ox + dx, 3, oz + 1, Blocks.MOSS_BLOCK.getDefaultState());
            set(ox + dx, 4, oz + 2, Blocks.MOSS_BLOCK.getDefaultState());
            set(ox + dx, 3, oz + 3, Blocks.MOSS_BLOCK.getDefaultState());
            set(ox + dx, 3, oz + 4, Blocks.MOSS_BLOCK.getDefaultState());
        }
        // Дверь в переднем торце (восток)
        set(ox + length - 1, 1, oz + 2, Blocks.AIR.getDefaultState());
        set(ox + length - 1, 2, oz + 2, Blocks.AIR.getDefaultState());
        // Окна в боковых стенах
        set(ox + length / 3,     2, oz,     Blocks.GLASS.getDefaultState());
        set(ox + 2 * length / 3, 2, oz,     Blocks.GLASS.getDefaultState());
        set(ox + length / 3,     2, oz + 4, Blocks.GLASS.getDefaultState());
        set(ox + 2 * length / 3, 2, oz + 4, Blocks.GLASS.getDefaultState());
    }

    private void spawnGuardGolems() {
        int[][] spots = {{-25, -25}, {25, -25}, {-25, 25}, {25, 25}};
        for (int[] sp : spots) {
            IronGolemEntity golem = com.mixaold.burmalda.util.BurmaldaCompat.create(EntityType.IRON_GOLEM, baseWorld);
            if (golem == null) continue;
            golem.setPosition(baseX + sp[0] + 0.5, baseY + 1, baseZ + sp[1] + 0.5);
            golem.setCustomName(Text.translatableWithFallback("burmalda.voennaya_chast.guard_name", "§7[Guard]"));
            golem.setCustomNameVisible(true);
            golem.setPersistent();
            baseWorld.spawnEntity(golem);
        }
    }

    private void set(int ox, int oy, int oz, BlockState state) {
        BlockPos pos = new BlockPos(baseX + ox, baseY + oy, baseZ + oz);
        if (baseWorld.isInBuildLimit(pos))
            baseWorld.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static int getFloorY(ServerWorld world, int x, int z) {
        if (world.getRegistryKey() == World.NETHER) {
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
}
