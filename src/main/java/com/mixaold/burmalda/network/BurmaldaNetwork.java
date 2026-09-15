package com.mixaold.burmalda.network;

import com.mixaold.burmalda.debuff.DebuffManager;
import com.mixaold.burmalda.debuff.solo.KolesoUdachiDebuff;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class BurmaldaNetwork {

    // ─── Payload: Debuff assigned to a player ────────────────────────────────
    public record DebuffAssignedPayload(String debuffId, String name, String description)
            implements CustomPayload {
        public static final CustomPayload.Id<DebuffAssignedPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "debuff_assigned"));
        public static final PacketCodec<PacketByteBuf, DebuffAssignedPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, DebuffAssignedPayload::debuffId,
                        PacketCodecs.STRING, DebuffAssignedPayload::name,
                        PacketCodecs.STRING, DebuffAssignedPayload::description,
                        DebuffAssignedPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Debuff cleared for a player ────────────────────────────────
    public record DebuffClearedPayload() implements CustomPayload {
        public static final CustomPayload.Id<DebuffClearedPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "debuff_cleared"));
        public static final PacketCodec<PacketByteBuf, DebuffClearedPayload> CODEC =
                PacketCodec.unit(new DebuffClearedPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Group debuff started ───────────────────────────────────────
    public record GroupDebuffStartPayload(String debuffId, String name, String description)
            implements CustomPayload {
        public static final CustomPayload.Id<GroupDebuffStartPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "group_debuff_start"));
        public static final PacketCodec<PacketByteBuf, GroupDebuffStartPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, GroupDebuffStartPayload::debuffId,
                        PacketCodecs.STRING, GroupDebuffStartPayload::name,
                        PacketCodecs.STRING, GroupDebuffStartPayload::description,
                        GroupDebuffStartPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Group debuff ended ─────────────────────────────────────────
    public record GroupDebuffEndedPayload() implements CustomPayload {
        public static final CustomPayload.Id<GroupDebuffEndedPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "group_debuff_ended"));
        public static final PacketCodec<PacketByteBuf, GroupDebuffEndedPayload> CODEC =
                PacketCodec.unit(new GroupDebuffEndedPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Group debuff instant-done (e.g. Blind Auction — gray HUD) ──
    public record GroupDebuffDonePayload() implements CustomPayload {
        public static final CustomPayload.Id<GroupDebuffDonePayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "group_debuff_done"));
        public static final PacketCodec<PacketByteBuf, GroupDebuffDonePayload> CODEC =
                PacketCodec.unit(new GroupDebuffDonePayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Timer sync (every 20 ticks) ────────────────────────────────
    public record TimerSyncPayload(int ticksRemaining, int cycleCount) implements CustomPayload {
        public static final CustomPayload.Id<TimerSyncPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "timer_sync"));
        public static final PacketCodec<PacketByteBuf, TimerSyncPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, TimerSyncPayload::ticksRemaining,
                        PacketCodecs.INTEGER, TimerSyncPayload::cycleCount,
                        TimerSyncPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Mod activated broadcast ────────────────────────────────────
    public record ModActivatedPayload(String activatorName) implements CustomPayload {
        public static final CustomPayload.Id<ModActivatedPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "mod_activated"));
        public static final PacketCodec<PacketByteBuf, ModActivatedPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ModActivatedPayload::activatorName,
                        ModActivatedPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Obshchaga link info ─────────────────────────────────────────
    public record ObshchagaLinkPayload(String linkedNames)
            implements CustomPayload {
        public static final CustomPayload.Id<ObshchagaLinkPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "obshchaga_link"));
        public static final PacketCodec<PacketByteBuf, ObshchagaLinkPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ObshchagaLinkPayload::linkedNames,
                        ObshchagaLinkPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Announcement overlay (Filosof, Kollektory, etc.) ───────────
    public record AnnouncementPayload(String title, String body, boolean darken) implements CustomPayload {
        public static final CustomPayload.Id<AnnouncementPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "announcement"));
        public static final PacketCodec<PacketByteBuf, AnnouncementPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, AnnouncementPayload::title,
                        PacketCodecs.STRING, AnnouncementPayload::body,
                        //? if >=1.21.4 {
                        /*PacketCodecs.BOOLEAN, AnnouncementPayload::darken,
                        *///? } else {
                        PacketCodecs.BOOL, AnnouncementPayload::darken,
                        //? }
                        AnnouncementPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Casino: server→client open wheel screen ─────────────────────────────
    public record CasinoOpenPayload(String g1Id, String g1Name, String g2Id, String g2Name)
            implements CustomPayload {
        public static final CustomPayload.Id<CasinoOpenPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "casino_open"));
        public static final PacketCodec<PacketByteBuf, CasinoOpenPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, CasinoOpenPayload::g1Id,
                        PacketCodecs.STRING, CasinoOpenPayload::g1Name,
                        PacketCodecs.STRING, CasinoOpenPayload::g2Id,
                        PacketCodecs.STRING, CasinoOpenPayload::g2Name,
                        CasinoOpenPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Casino: client→server player clicked "Играть" ───────────────────────
    public record CasinoSpinPayload() implements CustomPayload {
        public static final CustomPayload.Id<CasinoSpinPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "casino_spin"));
        public static final PacketCodec<PacketByteBuf, CasinoSpinPayload> CODEC =
                PacketCodec.unit(new CasinoSpinPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Casino: server→client result index (0-6) ────────────────────────────
    public record CasinoResultPayload(int section) implements CustomPayload {
        public static final CustomPayload.Id<CasinoResultPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "casino_result"));
        public static final PacketCodec<PacketByteBuf, CasinoResultPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, CasinoResultPayload::section,
                        CasinoResultPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Casino: server→client timeout (show fate overlay) ───────────────────
    public record CasinoTimeoutPayload() implements CustomPayload {
        public static final CustomPayload.Id<CasinoTimeoutPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "casino_timeout"));
        public static final PacketCodec<PacketByteBuf, CasinoTimeoutPayload> CODEC =
                PacketCodec.unit(new CasinoTimeoutPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Wheel of Fortune skipped this player's next debuff ─────────
    public record WheelSkipPayload() implements CustomPayload {
        public static final CustomPayload.Id<WheelSkipPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "wheel_skip"));
        public static final PacketCodec<PacketByteBuf, WheelSkipPayload> CODEC =
                PacketCodec.unit(new WheelSkipPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Payload: Solo debuff failed (eaten potato, etc.) ────────────────────
    public record DebuffFailedPayload() implements CustomPayload {
        public static final CustomPayload.Id<DebuffFailedPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "debuff_failed"));
        public static final PacketCodec<PacketByteBuf, DebuffFailedPayload> CODEC =
                PacketCodec.unit(new DebuffFailedPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Guide: S2C open guide screen for all players ────────────────────────
    // players = comma-joined names of all online players; empty string if solo
    public record GuideOpenPayload(boolean solo, String players) implements CustomPayload {
        public static final CustomPayload.Id<GuideOpenPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "guide_open"));
        public static final PacketCodec<PacketByteBuf, GuideOpenPayload> CODEC =
                PacketCodec.tuple(
                        //? if >=1.21.4 {
                        /*PacketCodecs.BOOLEAN,   GuideOpenPayload::solo,
                        *///? } else {
                        PacketCodecs.BOOL,   GuideOpenPayload::solo,
                        //? }
                        PacketCodecs.STRING, GuideOpenPayload::players,
                        GuideOpenPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Guide: C2S player clicked "Начать" (ready to start) ─────────────────
    public record PlayerReadyPayload() implements CustomPayload {
        public static final CustomPayload.Id<PlayerReadyPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "player_ready"));
        public static final PacketCodec<PacketByteBuf, PlayerReadyPayload> CODEC =
                PacketCodec.unit(new PlayerReadyPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Guide: S2C broadcast who is ready (comma-joined names) ─────────────
    public record ReadyUpdatePayload(String readyNames) implements CustomPayload {
        public static final CustomPayload.Id<ReadyUpdatePayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "ready_update"));
        public static final PacketCodec<PacketByteBuf, ReadyUpdatePayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ReadyUpdatePayload::readyNames,
                        ReadyUpdatePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Guide: S2C all players ready — begin 5-second countdown ─────────────
    public record StartCountdownPayload() implements CustomPayload {
        public static final CustomPayload.Id<StartCountdownPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "start_countdown"));
        public static final PacketCodec<PacketByteBuf, StartCountdownPayload> CODEC =
                PacketCodec.unit(new StartCountdownPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Guide: C2S player clicked "Передумал" — cancel pending start ─────────
    public record PlayerCancelPayload() implements CustomPayload {
        public static final CustomPayload.Id<PlayerCancelPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "player_cancel"));
        public static final PacketCodec<PacketByteBuf, PlayerCancelPayload> CODEC =
                PacketCodec.unit(new PlayerCancelPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Guide: S2C mod start cancelled — tell all clients to close guide ─────
    public record GuideCancelledPayload() implements CustomPayload {
        public static final CustomPayload.Id<GuideCancelledPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "guide_cancelled"));
        public static final PacketCodec<PacketByteBuf, GuideCancelledPayload> CODEC =
                PacketCodec.unit(new GuideCancelledPayload());
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Menu: S2C open the menu (canManage → owner who may edit the whitelist) ──
    public record OpenMenuPayload(boolean canManage, String whitelistCsv) implements CustomPayload {
        public static final CustomPayload.Id<OpenMenuPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "open_menu"));
        public static final PacketCodec<PacketByteBuf, OpenMenuPayload> CODEC =
                PacketCodec.tuple(
                        //? if >=1.21.5 {
                        /*PacketCodecs.BOOLEAN,
                        *///? } else
                        PacketCodecs.BOOL,
                        OpenMenuPayload::canManage,
                        PacketCodecs.STRING, OpenMenuPayload::whitelistCsv,
                        OpenMenuPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Menu: C2S owner toggles a player's menu-whitelist membership ─────────
    public record WhitelistTogglePayload(String name) implements CustomPayload {
        public static final CustomPayload.Id<WhitelistTogglePayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "wl_toggle"));
        public static final PacketCodec<PacketByteBuf, WhitelistTogglePayload> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, WhitelistTogglePayload::name, WhitelistTogglePayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Menu: C2S turn off active debuffs (empty CSV → everyone + group) ────────
    public record ClearDebuffsPayload(String targetCsv) implements CustomPayload {
        public static final CustomPayload.Id<ClearDebuffsPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "clear_debuffs"));
        public static final PacketCodec<PacketByteBuf, ClearDebuffsPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, ClearDebuffsPayload::targetCsv, ClearDebuffsPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Menu: S2C updated whitelist (CSV of names) to refresh the open screen ──
    // ─── Event timer bar above the hotbar (data = "label|frac|color") ────────
    public record EventTimerPayload(String data) implements CustomPayload {
        public static final CustomPayload.Id<EventTimerPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "event_timer"));
        public static final PacketCodec<PacketByteBuf, EventTimerPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, EventTimerPayload::data, EventTimerPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public record WhitelistSyncPayload(String whitelistCsv) implements CustomPayload {
        public static final CustomPayload.Id<WhitelistSyncPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "wl_sync"));
        public static final PacketCodec<PacketByteBuf, WhitelistSyncPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, WhitelistSyncPayload::whitelistCsv, WhitelistSyncPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ─── Menu: C2S assign a debuff (group=true → everyone; else CSV of target names) ──
    public record AssignDebuffPayload(String debuffId, boolean group, String targetCsv)
            implements CustomPayload {
        public static final CustomPayload.Id<AssignDebuffPayload> ID =
                new CustomPayload.Id<>(Identifier.of("burmalda", "assign_debuff"));
        public static final PacketCodec<PacketByteBuf, AssignDebuffPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING,  AssignDebuffPayload::debuffId,
                        //? if >=1.21.5 {
                        /*PacketCodecs.BOOLEAN,
                        *///? } else
                        PacketCodecs.BOOL,
                        AssignDebuffPayload::group,
                        PacketCodecs.STRING,  AssignDebuffPayload::targetCsv,
                        AssignDebuffPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(DebuffAssignedPayload.ID, DebuffAssignedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DebuffClearedPayload.ID, DebuffClearedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupDebuffStartPayload.ID, GroupDebuffStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupDebuffEndedPayload.ID, GroupDebuffEndedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupDebuffDonePayload.ID, GroupDebuffDonePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerSyncPayload.ID, TimerSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ModActivatedPayload.ID, ModActivatedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ObshchagaLinkPayload.ID, ObshchagaLinkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnnouncementPayload.ID, AnnouncementPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DebuffFailedPayload.ID, DebuffFailedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WheelSkipPayload.ID, WheelSkipPayload.CODEC);

        // Casino payloads
        PayloadTypeRegistry.playS2C().register(CasinoOpenPayload.ID, CasinoOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CasinoResultPayload.ID, CasinoResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CasinoTimeoutPayload.ID, CasinoTimeoutPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CasinoSpinPayload.ID, CasinoSpinPayload.CODEC);

        // Guide payloads
        PayloadTypeRegistry.playS2C().register(GuideOpenPayload.ID,       GuideOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReadyUpdatePayload.ID,     ReadyUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StartCountdownPayload.ID,  StartCountdownPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GuideCancelledPayload.ID,  GuideCancelledPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlayerReadyPayload.ID,     PlayerReadyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlayerCancelPayload.ID,    PlayerCancelPayload.CODEC);

        // Menu payloads
        PayloadTypeRegistry.playS2C().register(OpenMenuPayload.ID,        OpenMenuPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AssignDebuffPayload.ID,    AssignDebuffPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhitelistTogglePayload.ID, WhitelistTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WhitelistSyncPayload.ID,   WhitelistSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EventTimerPayload.ID,      EventTimerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearDebuffsPayload.ID,    ClearDebuffsPayload.CODEC);

        // Server-side receiver: player clicked "Играть" (casino)
        ServerPlayNetworking.registerGlobalReceiver(CasinoSpinPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            // Run on the server thread — onPlayerSpin mutates per-player tick state read by onPlayerTick.
            com.mixaold.burmalda.util.BurmaldaCompat.server(player).execute(() -> {
                var debuff = DebuffManager.getActiveDebuff(player.getUuid());
                if (debuff instanceof KolesoUdachiDebuff casino) {
                    casino.onPlayerSpin(player);
                }
            });
        });

        // Server-side receiver: player clicked "Начать" in guide confirm screen
        ServerPlayNetworking.registerGlobalReceiver(PlayerReadyPayload.ID, (payload, context) ->
                com.mixaold.burmalda.chat.ActivationHandler.onPlayerReady(context.player()));

        // Server-side receiver: player clicked "Передумал" — cancel pending start
        ServerPlayNetworking.registerGlobalReceiver(PlayerCancelPayload.ID, (payload, context) ->
                com.mixaold.burmalda.chat.ActivationHandler.onPlayerCancel(com.mixaold.burmalda.util.BurmaldaCompat.server(context.player())));

        // Server-side receiver: menu assign request — RE-VALIDATE access here (grief guard:
        // a modified client could send this directly without ever opening the menu).
        ServerPlayNetworking.registerGlobalReceiver(AssignDebuffPayload.ID, (payload, context) -> {
            ServerPlayerEntity sender = context.player();
            if (!com.mixaold.burmalda.chat.ActivationHandler.canUseMenu(sender)) return;
            var server = com.mixaold.burmalda.util.BurmaldaCompat.server(sender);
            if (server == null) return;
            server.execute(() -> {
                if (payload.group()) {
                    com.mixaold.burmalda.debuff.Debuff g =
                            com.mixaold.burmalda.debuff.DebuffRegistry.getGroupById(payload.debuffId());
                    if (g != null) DebuffManager.forceAssignGroup(server, g);
                } else {
                    com.mixaold.burmalda.debuff.Debuff d =
                            com.mixaold.burmalda.debuff.DebuffRegistry.getSoloById(payload.debuffId());
                    if (d == null) return;
                    for (String name : payload.targetCsv().split(",")) {
                        String n = name.trim();
                        if (n.isEmpty()) continue;
                        ServerPlayerEntity target = server.getPlayerManager().getPlayer(n);
                        if (target != null) DebuffManager.forceAssignSolo(server, target, d);
                    }
                }
            });
        });

        // Server-side receiver: owner toggles a player's menu-whitelist membership.
        // RE-VALIDATE the owner tier here — only owners may grant menu access (grief guard).
        ServerPlayNetworking.registerGlobalReceiver(WhitelistTogglePayload.ID, (payload, context) -> {
            ServerPlayerEntity sender = context.player();
            if (!com.mixaold.burmalda.chat.ActivationHandler.canManageWhitelist(sender)) return;
            var server = com.mixaold.burmalda.util.BurmaldaCompat.server(sender);
            if (server == null) return;
            server.execute(() -> {
                String n = payload.name() == null ? "" : payload.name().trim();
                if (!n.isEmpty()) com.mixaold.burmalda.menu.MenuWhitelist.toggle(n);
                sendWhitelistSync(sender, com.mixaold.burmalda.menu.MenuWhitelist.csv());
            });
        });

        // Server-side receiver: menu "turn off active debuffs" — re-validate menu access.
        ServerPlayNetworking.registerGlobalReceiver(ClearDebuffsPayload.ID, (payload, context) -> {
            ServerPlayerEntity sender = context.player();
            if (!com.mixaold.burmalda.chat.ActivationHandler.canUseMenu(sender)) return;
            var server = com.mixaold.burmalda.util.BurmaldaCompat.server(sender);
            if (server == null) return;
            server.execute(() -> {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (String n : payload.targetCsv().split(",")) {
                    String t = n.trim();
                    if (!t.isEmpty()) names.add(t);
                }
                DebuffManager.clearActive(server, names);
            });
        });
    }

    public static void sendOpenMenu(ServerPlayerEntity player, boolean canManage, String whitelistCsv) {
        ServerPlayNetworking.send(player, new OpenMenuPayload(canManage, whitelistCsv));
    }

    /** Show/update the event timer bar above the player's hotbar. frac 0..1, color = ARGB fill. */
    /** labelKey is a translation key resolved on the client (per-locale); time is a literal like "1:12". */
    public static void sendEventTimer(ServerPlayerEntity player, String labelKey, String time, float frac, int color) {
        ServerPlayNetworking.send(player, new EventTimerPayload(labelKey + "|" + time + "|" + frac + "|" + color));
    }

    public static void sendWhitelistSync(ServerPlayerEntity player, String whitelistCsv) {
        ServerPlayNetworking.send(player, new WhitelistSyncPayload(whitelistCsv));
    }

    public static void sendDebuffAssigned(ServerPlayerEntity player, String id, String name, String desc) {
        ServerPlayNetworking.send(player, new DebuffAssignedPayload(id, name, desc));
    }

    public static void sendDebuffCleared(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new DebuffClearedPayload());
    }

    public static void sendGroupDebuffStart(ServerPlayerEntity player, String id, String name, String desc) {
        ServerPlayNetworking.send(player, new GroupDebuffStartPayload(id, name, desc));
    }

    public static void sendGroupDebuffEnded(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new GroupDebuffEndedPayload());
    }

    public static void sendGroupDebuffDone(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new GroupDebuffDonePayload());
    }

    public static void sendTimerSync(ServerPlayerEntity player, int ticksRemaining, int cycleCount) {
        ServerPlayNetworking.send(player, new TimerSyncPayload(ticksRemaining, cycleCount));
    }

    public static void sendModActivated(ServerPlayerEntity player, String activatorName) {
        ServerPlayNetworking.send(player, new ModActivatedPayload(activatorName));
    }

    public static void sendObshchagaLink(ServerPlayerEntity player, String linkedNames) {
        ServerPlayNetworking.send(player, new ObshchagaLinkPayload(linkedNames));
    }

    public static void sendAnnouncement(ServerPlayerEntity player, String title, String body) {
        ServerPlayNetworking.send(player, new AnnouncementPayload(title, body, false));
    }

    public static void sendAnnouncement(ServerPlayerEntity player, String title, String body, boolean darken) {
        ServerPlayNetworking.send(player, new AnnouncementPayload(title, body, darken));
    }

    public static void sendDebuffFailed(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new DebuffFailedPayload());
    }

    public static void sendWheelSkip(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new WheelSkipPayload());
    }

    public static void sendCasinoOpen(ServerPlayerEntity player,
                                       String g1Id, String g1Name,
                                       String g2Id, String g2Name) {
        ServerPlayNetworking.send(player, new CasinoOpenPayload(g1Id, g1Name, g2Id, g2Name));
    }

    public static void sendCasinoResult(ServerPlayerEntity player, int section) {
        ServerPlayNetworking.send(player, new CasinoResultPayload(section));
    }

    public static void sendCasinoTimeout(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new CasinoTimeoutPayload());
    }

    public static void sendGuideOpen(ServerPlayerEntity player, boolean solo, String players) {
        ServerPlayNetworking.send(player, new GuideOpenPayload(solo, players));
    }

    public static void sendReadyUpdate(ServerPlayerEntity player, String readyNames) {
        ServerPlayNetworking.send(player, new ReadyUpdatePayload(readyNames));
    }

    public static void sendStartCountdown(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new StartCountdownPayload());
    }

    public static void sendGuideCancelled(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new GuideCancelledPayload());
    }
}
