package com.mixaold.burmalda.chat;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.network.BurmaldaNetwork;
import com.mixaold.burmalda.state.BurmaldaState;
import com.mixaold.burmalda.timer.DebuffTimer;
import com.mixaold.burmalda.util.BurmaldaLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.*;
import java.util.stream.Collectors;

public class ActivationHandler {

    private static boolean awaitingConfirmation = false;
    private static final Random RANDOM = new Random();

    // ── Guide / start flow ────────────────────────────────────────────────────
    private static final Set<UUID> pendingPlayers  = new HashSet<>();
    private static final Set<UUID> readyPlayers    = new HashSet<>();
    private static boolean   countdownStarted = false;
    private static int       countdownTicks   = -1;
    private static MinecraftServer pendingServer    = null;
    private static String    pendingActivator = "???";
    // Who launched Burmalda this session — basis of the menu access gate.
    private static UUID      activatorUuid    = null;

    /**
     * Owner tier — may open the menu AND manage its whitelist. A grief guard: only these can
     * grant menu access to others. Operators (perm level ≥2), or whoever activated Burmalda this
     * session (covers the singleplayer/host owner who typed the trigger, even with cheats off).
     */
    public static boolean canManageWhitelist(ServerPlayerEntity p) {
        if (p == null) return false;
        if (p.isCreativeLevelTwoOp()) return true; // op level ≥2 — uniform across all versions
        if (p.getUuid().equals(activatorUuid)) return true;
        return pendingActivator != null && pendingActivator.equals(p.getName().getString());
    }

    /** Who may open / use the debuff-assignment menu — owner tier, or anyone the owner whitelisted. */
    public static boolean canUseMenu(ServerPlayerEntity p) {
        if (p == null) return false;
        if (canManageWhitelist(p)) return true;
        return com.mixaold.burmalda.menu.MenuWhitelist.contains(p.getName().getString());
    }

    // ── World-join greeting — по одному на каждого игрока ────────────────────
    // UUID → время когда отправить (ms)
    private static final Map<UUID, Long> pendingGreetings = new HashMap<>();

    private static final String[] MEME_PHRASE_KEYS = {
            "burmalda.chat.phrase.0",
            "burmalda.chat.phrase.1",
            "burmalda.chat.phrase.2",
            "burmalda.chat.phrase.3",
            "burmalda.chat.phrase.4",
            "burmalda.chat.phrase.5",
            "burmalda.chat.phrase.6",
            "burmalda.chat.phrase.7",
            "burmalda.chat.phrase.8",
            "burmalda.chat.phrase.9"
    };

    public static void register() {

        // ── Reset pending greetings on each world/server start ───────────────
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            pendingGreetings.clear();
        });

        // ── Chat listener ─────────────────────────────────────────────────────
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String content = message.getSignedContent();
            MinecraftServer server = com.mixaold.burmalda.util.BurmaldaCompat.server(sender);

            // If this player is in a Job Interview, their message is the answer the HR is waiting for.
            server.execute(() -> com.mixaold.burmalda.debuff.solo.SobesedovanieDebuff.handleChat(sender));

            // Let the active debuffs react to chat (e.g. military roll-call). Both the player's own
            // solo debuff and the active group debuff get a shot.
            server.execute(() -> {
                com.mixaold.burmalda.debuff.Debuff solo =
                        com.mixaold.burmalda.debuff.DebuffManager.getActiveDebuff(sender.getUuid());
                if (solo != null) solo.onChat(sender, content, server);
                com.mixaold.burmalda.debuff.Debuff grp =
                        com.mixaold.burmalda.debuff.DebuffManager.getActiveGroupDebuff();
                if (grp != null) grp.onChat(sender, content, server);
            });

            if (content.equalsIgnoreCase("start") || content.equalsIgnoreCase("пошла возня")) {
                awaitingConfirmation = true;
                server.execute(() -> server.getPlayerManager().broadcast(
                        Text.translatable("burmalda.chat.confirm_prompt"), false));
                return;
            }

            if (awaitingConfirmation && (content.equalsIgnoreCase("burmalda") || content.equalsIgnoreCase("бурмалда"))) {
                BurmaldaState state = BurmaldaState.getServerState(server);
                if (!state.modActive) {
                    awaitingConfirmation = false;
                    pendingActivator = sender.getName().getString();
                    activatorUuid = sender.getUuid();
                    openGuideForAll(server);
                    BurmaldaLogger.info("Guide opened by " + pendingActivator);
                } else {
                    awaitingConfirmation = false;
                    sender.sendMessage(Text.literal("§c[Burmalda] ").append(Text.translatable("burmalda.chat.already_active")), false);
                }
            }
        });

        // ── Server tick: countdown to actual mod start + greeting delay ───────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (countdownTicks > 0) {
                countdownTicks--;
                if (countdownTicks == 0) {
                    doStartMod(server);
                }
            }
            if (!pendingGreetings.isEmpty()) {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> it = pendingGreetings.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, Long> entry = it.next();
                    if (now >= entry.getValue()) {
                        ServerPlayerEntity gp = server.getPlayerManager().getPlayer(entry.getKey());
                        if (gp != null) sendMixaoldGreeting(gp);
                        it.remove();
                    }
                }
            }
        });

        // ── Player join: schedule Mixaold greeting for this player in 2 s ───────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            pendingGreetings.put(handler.player.getUuid(), System.currentTimeMillis() + 2000L);
        });

        // ── No-permission command so the clickable chat phrase can start the mod ──
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, ra, env) ->
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("burmalda_start")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                    requestActivation(ctx.getSource().getServer(), p != null ? p.getName().getString() : "Server");
                    return 1;
                })));

        // ── Player disconnect: remove from pending, re-check ──────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            pendingGreetings.remove(uuid);
            pendingPlayers.remove(uuid);
            readyPlayers.remove(uuid);
            if (!pendingPlayers.isEmpty() && readyPlayers.containsAll(pendingPlayers)
                    && !countdownStarted && pendingServer != null) {
                beginCountdown(server);
            }
        });
    }

    // ── Called from BurmaldaNetwork C2S handler ───────────────────────────────
    public static void onPlayerReady(ServerPlayerEntity player) {
        if (pendingServer == null || countdownStarted) return;
        readyPlayers.add(player.getUuid());

        String readyStr = readyPlayers.stream()
                .map(uuid -> {
                    ServerPlayerEntity p = pendingServer.getPlayerManager().getPlayer(uuid);
                    return p != null ? p.getName().getString() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        for (ServerPlayerEntity p : pendingServer.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendReadyUpdate(p, readyStr);
        }

        if (readyPlayers.containsAll(pendingPlayers)) {
            beginCountdown(pendingServer);
        }
    }

    public static void onPlayerCancel(MinecraftServer server) {
        if (pendingServer == null || countdownStarted) return;

        pendingPlayers.clear();
        readyPlayers.clear();
        countdownStarted = false;
        countdownTicks   = -1;
        pendingServer    = null;
        awaitingConfirmation = false;

        server.getPlayerManager().broadcast(
                Text.literal("§c[Burmalda] ").append(Text.translatable("burmalda.chat.cancelled")), false);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendGuideCancelled(p);
        }
        BurmaldaLogger.info("Guide cancelled by a player");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Starts the activation flow directly (used by the clickable chat phrase / command). */
    public static void requestActivation(MinecraftServer server, String activatorName) {
        BurmaldaState state = BurmaldaState.getServerState(server);
        if (state.modActive) {
            server.getPlayerManager().broadcast(
                    Text.literal("§c[Burmalda] ").append(Text.translatable("burmalda.chat.already_active")), false);
            return;
        }
        awaitingConfirmation = false;
        pendingActivator = activatorName;
        openGuideForAll(server);
        BurmaldaLogger.info("Guide opened via click/command by " + activatorName);
    }

    private static void openGuideForAll(MinecraftServer server) {
        pendingPlayers.clear();
        readyPlayers.clear();
        countdownStarted = false;
        countdownTicks   = -1;
        pendingServer    = server;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        boolean solo = players.size() == 1;

        for (ServerPlayerEntity p : players) pendingPlayers.add(p.getUuid());

        String playersStr = solo ? "" : players.stream()
                .map(p -> p.getName().getString())
                .collect(Collectors.joining(","));

        for (ServerPlayerEntity p : players) {
            BurmaldaNetwork.sendGuideOpen(p, solo, playersStr);
        }
    }

    private static void beginCountdown(MinecraftServer server) {
        if (countdownStarted) return;
        countdownStarted = true;
        countdownTicks   = 100; // 5 seconds
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendStartCountdown(p);
        }
        BurmaldaLogger.info("Guide confirmed — mod starts in 5 s");
    }

    private static void doStartMod(MinecraftServer server) {
        BurmaldaState state = BurmaldaState.getServerState(server);
        if (state.modActive) return;

        com.mixaold.burmalda.debuff.DebuffManager.onModStart(server);

        state.modActive    = true;
        state.ticksInCycle = 0;
        state.markDirty();

        String phraseKey = MEME_PHRASE_KEYS[RANDOM.nextInt(MEME_PHRASE_KEYS.length)];
        server.getPlayerManager().broadcast(
                Text.literal("§a[Burmalda] §f").append(Text.translatable("burmalda.chat.started"))
                        .append(Text.literal(" §7")).append(Text.translatable(phraseKey)), false);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            BurmaldaAdvancements.trigger(p, BurmaldaAdvancements.MOD_STARTED);
            BurmaldaAdvancements.trigger(p, BurmaldaAdvancements.WELCOME);
            BurmaldaNetwork.sendModActivated(p, pendingActivator);
        }

        DebuffTimer.schedulePendingAssign();
        BurmaldaLogger.info("Mod activated (via guide) by " + pendingActivator);

        pendingPlayers.clear();
        readyPlayers.clear();
        countdownStarted = false;
        countdownTicks   = -1;
        pendingServer    = null;
    }

    private static void sendMixaoldGreeting(ServerPlayerEntity player) {
        MutableText authorLink = Text.literal("Mixaold").styled(s -> s
                .withColor(TextColor.fromRgb(0x55AAFF))
                .withBold(true)
                .withUnderline(true)
                .withClickEvent(
                        //? if >=1.21.5 {
                        /*new ClickEvent.OpenUrl(java.net.URI.create("https://www.youtube.com/@MIXAOLD"))
                        *///? } else {
                        new ClickEvent(ClickEvent.Action.OPEN_URL,
                                "https://www.youtube.com/@MIXAOLD")
                        //? }
                ));

        // The activation phrase itself is clickable — clicking it starts the mod.
        MutableText phrase = Text.translatable("burmalda.chat.greeting.phrase").styled(s -> s.withClickEvent(
                //? if >=1.21.5 {
                /*new ClickEvent.RunCommand("/burmalda_start")
                *///? } else {
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/burmalda_start")
                //? }
        ));

        Text msg = Text.literal("§7[")
                .append(authorLink)
                .append(Text.literal("§7] §f"))
                .append(Text.translatable("burmalda.chat.greeting.before"))
                .append(phrase)
                .append(Text.translatable("burmalda.chat.greeting.after"));

        player.sendMessage(msg, false);
    }
}
