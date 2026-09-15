package com.mixaold.burmalda.debuff;

import com.mixaold.burmalda.network.BurmaldaNetwork;
import com.mixaold.burmalda.sound.BurmaldaSounds;
import com.mixaold.burmalda.state.BurmaldaState;
import com.mixaold.burmalda.util.BurmaldaLogger;
import com.mixaold.burmalda.util.BurmaldaCompat;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class DebuffManager {

    // Live debuff instances per player: UUID → (Debuff, ticksActive)
    private static final Map<UUID, ActiveEntry> activeDebuffs = new HashMap<>();

    // "Колесо удачи" skip-next flags
    private static final Set<UUID> skipNextSoloSet = new HashSet<>();
    private static boolean skipNextSoloAll = false;
    // Active group debuff
    private static Debuff activeGroupDebuff = null;
    private static int groupTicks = 0;

    // Active Obshchaga links
    private static final List<ObshchagaLink> obshchagaLinks = new ArrayList<>();

    // Track recently assigned debuffs per player (last 5, independent per player)
    private static final Map<UUID, Deque<String>> recentDebuffs = new HashMap<>();
    private static final int RECENT_MEMORY = 5;

    // Floating nametag entities above player heads
    private record DisplayEntry(ArmorStandEntity entity, ServerWorld world) {}
    private static final Map<UUID, DisplayEntry> debuffDisplayIds = new HashMap<>();

    private static final Random RANDOM = new Random();

    // ─── Debuff tips: 30% chance, shown in chat 3 s after the debuff is assigned ──
    private static final int TIP_DELAY_TICKS = 60; // 3 s
    private static final int TIP_CHANCE_PCT  = 30;
    private static final java.util.Map<UUID, String> pendingTip = new java.util.HashMap<>();
    private static String pendingGroupTip = null;
    // Only debuffs with a hidden mechanic or a non-obvious exploit get a tip.
    private static final java.util.Set<String> TIPPED = java.util.Set.of(
        "dublyor","obshchaga","goryachaya_kartoshka","ekho_urona","obratnaya_zhizn",
        "obratnyy_golod","slepoe_doverie","dezhavu","bomba","chernaya_dyra","prityazhenie",
        "motyga_sudby","kvantovy_ryukzak","kleptoman","vspylchivyy_borov","nablyudatel",
        "slepoy_auktsion","epidemiya","shakhed","feromony","kollektory","tekhpodderzhka"
    );

    private static void maybeScheduleTip(UUID uuid, String id) {
        if (TIPPED.contains(id) && RANDOM.nextInt(100) < TIP_CHANCE_PCT)
            pendingTip.put(uuid, "burmalda.tip." + id);
        else
            pendingTip.remove(uuid);
    }

    private static void sendTip(ServerPlayerEntity player, String key) {
        player.sendMessage(net.minecraft.text.Text.translatableWithFallback("burmalda.tip.prefix", "§b[Tip] §f")
                .append(net.minecraft.text.Text.translatableWithFallback(key, "")), false);
    }

    private record ActiveEntry(Debuff debuff, int[] ticks) {}

    // ─── Assign solo debuffs to all online players ───────────────────────────
    public static void assignSoloDebuffs(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        for (ServerPlayerEntity player : players) {
            endPlayerDebuff(server, player);
        }

        for (ServerPlayerEntity player : players) {
            if (activeDebuffs.containsKey(player.getUuid())) continue;

            if (skipNextSoloAll || skipNextSoloSet.remove(player.getUuid())) {
                player.sendMessage(Text.translatableWithFallback("burmalda.chat.wheel.skip", "§a[🎰 Wheel of Fortune] §fLuck spared you — debuff skipped!"), false);
                BurmaldaNetwork.sendWheelSkip(player);
                continue;
            }

            Debuff debuff = pickSoloDebuff(player.getUuid());
            applyDebuffToPlayer(server, player, debuff);
        }

        if (skipNextSoloAll) {
            skipNextSoloAll = false;
            skipNextSoloSet.clear();
        }
    }

    public static void skipNextSoloFor(UUID playerUuid) {
        skipNextSoloSet.add(playerUuid);
    }

    public static void skipNextSoloForAll() {
        skipNextSoloAll = true;
    }

    private static Debuff pickSoloDebuff(UUID playerUuid) {
        List<Debuff> all    = DebuffRegistry.getSoloDebuffs();
        Deque<String> recent = recentDebuffs.getOrDefault(playerUuid, new ArrayDeque<>());
        List<Debuff> pool   = new ArrayList<>(all.size());
        for (Debuff d : all) {
            if (!recent.contains(d.getId())) pool.add(d);
        }
        if (pool.isEmpty()) pool = new ArrayList<>(all);
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static void applyDebuffToPlayer(MinecraftServer server, ServerPlayerEntity player,
                                             Debuff debuff) {
        debuff.onStart(player);
        activeDebuffs.put(player.getUuid(), new ActiveEntry(debuff, new int[]{0}));

        // Track last RECENT_MEMORY debuffs per player independently
        Deque<String> recent = recentDebuffs.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
        recent.addLast(debuff.getId());
        if (recent.size() > RECENT_MEMORY) recent.removeFirst();

        BurmaldaNetwork.sendDebuffAssigned(player, debuff.getId(), debuff.getName(), debuff.getDescription());
        BurmaldaSounds.playTune(player);
        BurmaldaLogger.info("Assigned " + debuff.getId() + " to " + player.getName().getString());

        // Announce in chat who got which debuff
        String localName = net.minecraft.text.Text.translatableWithFallback(
                "burmalda.debuff." + debuff.getId() + ".name", debuff.getName()).getString();
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.assigned",
                        "§c[Burmalda] §e%s §7got: §f%s",
                        player.getName().getString(), localName), false);

        BurmaldaState state = BurmaldaState.getServerState(server);
        state.playerDebuffMap.put(player.getUuid(), debuff.getId());
        state.markDirty();

        // Spawn floating nametag above player
        spawnDebuffDisplay(player, debuff.getId(), debuff.getName());

        // Maybe queue a helpful/funny tip to show 3 s later
        maybeScheduleTip(player.getUuid(), debuff.getId());

        if ("obshchaga".equals(debuff.getId())) {
            setupObshchaga(server, player);
        }
    }

    // ─── Floating nametag (ArmorStand) management ────────────────────────────

    private static void spawnDebuffDisplay(ServerPlayerEntity player, String debuffId, String fallbackName) {
        clearDebuffDisplay(player);

        ArmorStandEntity stand = BurmaldaCompat.create(EntityType.ARMOR_STAND, com.mixaold.burmalda.util.BurmaldaCompat.world(player));
        if (stand == null) return;

        ((com.mixaold.burmalda.mixin.ArmorStandMarkerInvoker) stand).burmalda$setMarker(true);
        stand.setCustomName(
                Text.translatableWithFallback("burmalda.debuff." + debuffId + ".name", fallbackName)
                        .formatted(Formatting.YELLOW));
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.addCommandTag("burmalda_display");
        // marker renders name at exact entity Y — put just above player head nametag
        stand.setPosition(player.getX(), player.getY() + 2.1, player.getZ());
        ServerWorld spawnWorld = com.mixaold.burmalda.util.BurmaldaCompat.world(player);
        spawnWorld.spawnEntity(stand);
        // Hide the stand from its own owner — others still see the label
        player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(stand.getId()));

        debuffDisplayIds.put(player.getUuid(), new DisplayEntry(stand, spawnWorld));
    }

    private static void clearDebuffDisplay(ServerPlayerEntity player) {
        DisplayEntry entry = debuffDisplayIds.remove(player.getUuid());
        if (entry == null) return;
        if (!entry.entity().isRemoved()) entry.entity().remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        clearDebuffDisplay(player);
    }

    // ─── Obshchaga: link host with available others (1 or 2) ─────────────────
    private static void setupObshchaga(MinecraftServer server, ServerPlayerEntity host) {
        List<ServerPlayerEntity> others = new ArrayList<>(server.getPlayerManager().getPlayerList());
        others.remove(host);
        Collections.shuffle(others, RANDOM);

        ObshchagaLink link = new ObshchagaLink(host);

        for (ServerPlayerEntity partner : others) {
            link.addPlayer(partner);
            Debuff pd = pickSoloDebuff(partner.getUuid());
            applyDebuffToPlayer(server, partner, pd);
        }

        obshchagaLinks.add(link);
        broadcastObshchagaLink(server, link);
    }

    /** Called when a new player joins — adds them to any active Obshchaga with room. */
    public static void tryAddToObshchaga(MinecraftServer server, ServerPlayerEntity newPlayer) {
        for (ObshchagaLink link : obshchagaLinks) {
            if (!link.contains(newPlayer.getUuid())) {
                link.addPlayer(newPlayer);
                broadcastObshchagaLink(server, link);
                newPlayer.sendMessage(
                        Text.translatableWithFallback("burmalda.chat.obshchaga.joined", "§c[Dorm] §fYou were pulled in. You're now linked."), false);
                return;
            }
        }
    }

    private static void broadcastObshchagaLink(MinecraftServer server, ObshchagaLink link) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : link.getPlayerUuids()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p != null) names.add(p.getName().getString());
        }
        String joined = String.join("§f, §e", names);
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.obshchaga.linked", "§c[Burmalda] §fDorm linked: §e%s§f!", joined), false);
        String linkedNames = String.join(", ", names);
        for (UUID uuid : link.getPlayerUuids()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p != null) BurmaldaNetwork.sendObshchagaLink(p, linkedNames);
        }
    }

    // ─── End player debuff ────────────────────────────────────────────────────
    private static void endPlayerDebuff(MinecraftServer server, ServerPlayerEntity player) {
        ActiveEntry entry = activeDebuffs.remove(player.getUuid());
        if (entry == null) return;
        try {
            entry.debuff().onEnd(player);
        } catch (Exception e) {
            BurmaldaLogger.error("Error ending debuff " + entry.debuff().getId()
                    + " for " + player.getName().getString(), e);
        }
        BurmaldaNetwork.sendDebuffCleared(player);
        clearDebuffDisplay(player);

        obshchagaLinks.removeIf(link -> {
            if (link.contains(player.getUuid())) {
                // Clear the "Связаны" indicator for every surviving member of this link
                // (partners keep a different active debuff, so their state won't self-clear).
                for (UUID uuid : link.getPlayerUuids()) {
                    if (uuid.equals(player.getUuid())) continue;
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                    if (p != null) BurmaldaNetwork.sendObshchagaLink(p, "");
                }
                // Pass player directly so their snapshot can be restored even if they're disconnecting
                link.restore(server, player);
                return true;
            }
            return false;
        });
    }

    // ─── Tick all active player debuffs ──────────────────────────────────────
    public static void tickPlayerDebuffs(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ActiveEntry entry = activeDebuffs.get(player.getUuid());
            if (entry == null) continue;

            entry.ticks()[0]++;
            if (entry.ticks()[0] == TIP_DELAY_TICKS) {
                String tk = pendingTip.remove(player.getUuid());
                if (tk != null) sendTip(player, tk);
            }
            try {
                entry.debuff().onPlayerTick(player, entry.ticks()[0]);
            } catch (Exception e) {
                BurmaldaLogger.error("Error ticking debuff " + entry.debuff().getId(), e);
            }

            // Sync nametag position to follow player
            DisplayEntry displayEntry = debuffDisplayIds.get(player.getUuid());
            if (displayEntry != null) {
                // If player changed dimension, remove old stand from the old world first
                if (displayEntry.world() != com.mixaold.burmalda.util.BurmaldaCompat.world(player)) {
                    if (!displayEntry.entity().isRemoved())
                        displayEntry.entity().remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    debuffDisplayIds.remove(player.getUuid());
                    spawnDebuffDisplay(player, entry.debuff().getId(), entry.debuff().getName());
                } else if (!displayEntry.entity().isRemoved()) {
                    displayEntry.entity().setPosition(player.getX(), player.getY() + 2.1, player.getZ());
                    if (entry.ticks()[0] % 20 == 0) {
                        player.networkHandler.sendPacket(
                                new EntitiesDestroyS2CPacket(displayEntry.entity().getId()));
                    }
                } else {
                    debuffDisplayIds.remove(player.getUuid());
                    spawnDebuffDisplay(player, entry.debuff().getId(), entry.debuff().getName());
                }
            }
        }
    }

    // ─── Assign group debuff ─────────────────────────────────────────────────
    public static void assignGroupDebuff(MinecraftServer server) {
        Debuff group = DebuffRegistry.randomGroup();
        activeGroupDebuff = group;
        groupTicks = 0;
        group.onGroupStart(server);
        pendingGroupTip = (TIPPED.contains(group.getId()) && RANDOM.nextInt(100) < TIP_CHANCE_PCT)
                ? "burmalda.tip." + group.getId() : null;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendGroupDebuffStart(player, group.getId(), group.getName(), group.getDescription());
        }

        BurmaldaLogger.info("Group debuff assigned: " + group.getId());

        BurmaldaState state = BurmaldaState.getServerState(server);
        state.currentGroupDebuffId = group.getId();
        state.groupDebuffTicks = 0;
        state.markDirty();
    }

    // ─── Tick group debuff ────────────────────────────────────────────────────
    public static void tickGroupDebuff(MinecraftServer server) {
        if (activeGroupDebuff == null) return;
        groupTicks++;
        if (groupTicks == TIP_DELAY_TICKS && pendingGroupTip != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendTip(p, pendingGroupTip);
            pendingGroupTip = null;
        }
        try {
            activeGroupDebuff.onGroupTick(server, groupTicks);
        } catch (Exception e) {
            BurmaldaLogger.error("Error ticking group debuff " + activeGroupDebuff.getId(), e);
        }
    }

    // ─── End group debuff ─────────────────────────────────────────────────────
    public static void endGroupDebuff(MinecraftServer server) {
        if (activeGroupDebuff == null) return;
        try {
            activeGroupDebuff.onGroupEnd(server);
        } catch (Exception e) {
            BurmaldaLogger.error("Error ending group debuff " + activeGroupDebuff.getId(), e);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendGroupDebuffEnded(player);
        }
        BurmaldaLogger.info("Group debuff ended: " + activeGroupDebuff.getId());
        activeGroupDebuff = null;
        groupTicks = 0;

        BurmaldaState state = BurmaldaState.getServerState(server);
        state.currentGroupDebuffId = null;
        state.groupDebuffTicks = 0;
        state.markDirty();
    }

    // ─── Broadcast timer sync ─────────────────────────────────────────────────
    public static void broadcastTimerSync(MinecraftServer server, int ticksRemaining, int cycleCount) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendTimerSync(player, ticksRemaining, cycleCount);
        }
    }

    // ─── Damage propagation for Obshchaga ────────────────────────────────────
    public static void onPlayerBlockBreak(ServerPlayerEntity player,
                                          net.minecraft.util.math.BlockPos pos,
                                          net.minecraft.block.BlockState state) {
        ActiveEntry entry = activeDebuffs.get(player.getUuid());
        if (entry == null) return;
        try {
            entry.debuff().onBlockBreak(player, pos, state);
        } catch (Exception e) {
            BurmaldaLogger.error("Error in onBlockBreak for " + entry.debuff().getId(), e);
        }
    }

    public static void onPlayerDamaged(ServerPlayerEntity victim, float amount) {
        for (ObshchagaLink link : obshchagaLinks) {
            if (!link.contains(victim.getUuid()) || link.isReplicating) continue;
            link.isReplicating = true;
            for (UUID uuid : link.getPlayerUuids()) {
                if (uuid.equals(victim.getUuid())) continue;
                ServerPlayerEntity other = com.mixaold.burmalda.util.BurmaldaCompat.server(victim).getPlayerManager().getPlayer(uuid);
                if (other != null && other.isAlive()) {
                    BurmaldaCompat.damage(other, com.mixaold.burmalda.util.BurmaldaCompat.world(victim).getDamageSources().generic(), amount);
                }
            }
            link.isReplicating = false;
        }
    }

    // ─── Inventory change propagation for Obshchaga ──────────────────────────
    public static void onPlayerDropItem(ServerPlayerEntity dropper, net.minecraft.item.ItemStack stack) {
        // tickObshchagaLinks handles this, but force an immediate sync on drop
        for (ObshchagaLink link : obshchagaLinks) {
            if (!link.contains(dropper.getUuid()) || link.isReplicating) continue;
            link.tickSync(com.mixaold.burmalda.util.BurmaldaCompat.server(dropper));
            break;
        }
    }

    public static void tickObshchagaLinks(MinecraftServer server) {
        for (ObshchagaLink link : obshchagaLinks) {
            link.tickSync(server);
        }
    }

    // ─── Debug / command helpers ──────────────────────────────────────────────

    public static void forceAssignSolo(MinecraftServer server, ServerPlayerEntity player, Debuff debuff) {
        endPlayerDebuff(server, player);
        applyDebuffToPlayer(server, player, debuff);
    }

    public static void forceAssignGroup(MinecraftServer server, Debuff group) {
        if (activeGroupDebuff != null) endGroupDebuff(server);
        activeGroupDebuff = group;
        groupTicks = 0;
        group.onGroupStart(server);
        pendingGroupTip = (TIPPED.contains(group.getId()) && RANDOM.nextInt(100) < TIP_CHANCE_PCT)
                ? "burmalda.tip." + group.getId() : null;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendGroupDebuffStart(p, group.getId(), group.getName(), group.getDescription());
        }
        BurmaldaState state = BurmaldaState.getServerState(server);
        state.currentGroupDebuffId = group.getId();
        state.groupDebuffTicks = 0;
        state.markDirty();
    }

    /**
     * Turn OFF currently-active debuffs without resetting the mod/timer. Empty {@code targetNames}
     * → clear every player's solo debuff AND end the active group debuff; otherwise clear only the
     * named players' solo debuffs. Used by the assignment menu.
     */
    public static void clearActive(MinecraftServer server, java.util.Collection<String> targetNames) {
        BurmaldaState state = BurmaldaState.getServerState(server);
        if (targetNames == null || targetNames.isEmpty()) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) endPlayerDebuff(server, p);
            if (activeGroupDebuff != null) endGroupDebuff(server);
            state.playerDebuffMap.clear();
        } else {
            for (String name : targetNames) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(name);
                if (p != null) { endPlayerDebuff(server, p); state.playerDebuffMap.remove(p.getUuid()); }
            }
        }
        state.markDirty();
    }

    public static void forceReset(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            endPlayerDebuff(server, player);
        }
        if (activeGroupDebuff != null) endGroupDebuff(server);
        obshchagaLinks.clear();
        recentDebuffs.clear();
        BurmaldaState state = BurmaldaState.getServerState(server);
        state.modActive = false;
        state.playerDebuffMap.clear();
        state.currentGroupDebuffId = null;
        state.ticksInCycle = 0;
        state.cycleCount = 0;
        state.markDirty();
    }

    public static void onModStart(MinecraftServer server) {
        // Reset all static state that may linger from a previous session/world
        activeGroupDebuff = null;
        groupTicks = 0;
        activeDebuffs.clear();
        obshchagaLinks.clear();
        skipNextSoloAll = false;
        skipNextSoloSet.clear();
        debuffDisplayIds.values().forEach(e -> {
            if (!e.entity().isRemoved()) e.entity().remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        });
        debuffDisplayIds.clear();
        // Reset any debuff-specific static flags that survive world reloads
        com.mixaold.burmalda.debuff.group.MagazinZakrytDebuff.containersBlocked = false;
        // Notify connected players
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BurmaldaNetwork.sendDebuffCleared(player);
            BurmaldaNetwork.sendGroupDebuffEnded(player);
        }
        BurmaldaLogger.info("DebuffManager state reset on mod start.");
    }

    public static Debuff getActiveDebuff(UUID playerUuid) {
        ActiveEntry e = activeDebuffs.get(playerUuid);
        return e != null ? e.debuff() : null;
    }

    public static Debuff getActiveGroupDebuff() {
        return activeGroupDebuff;
    }

    public static List<ObshchagaLink> getObshchagaLinks() {
        return Collections.unmodifiableList(obshchagaLinks);
    }

    public static void markDebuffFailed(MinecraftServer server, String debuffId) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Debuff d = getActiveDebuff(p.getUuid());
            if (d != null && d.getId().equals(debuffId)) {
                BurmaldaNetwork.sendDebuffFailed(p);
                break;
            }
        }
    }
}
