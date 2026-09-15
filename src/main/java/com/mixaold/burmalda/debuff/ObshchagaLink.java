package com.mixaold.burmalda.debuff;

import com.mixaold.burmalda.util.BurmaldaLogger;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class ObshchagaLink {

    private final List<UUID> playerUuids = new ArrayList<>();
    private final UUID hostUuid;
    private final Map<UUID, List<ItemStack>> inventorySnapshots = new HashMap<>();
    private List<ItemStack> sharedState = null;
    public boolean isReplicating = false;

    public ObshchagaLink(ServerPlayerEntity host) {
        hostUuid = host.getUuid();
        playerUuids.add(host.getUuid());
        snapshotInventory(host);
        sharedState = copyInventory(host);
        BurmaldaLogger.info("ObshchagaLink: host " + host.getName().getString());
    }

    public void addPlayer(ServerPlayerEntity player) {
        if (playerUuids.contains(player.getUuid())) return;
        playerUuids.add(player.getUuid());
        snapshotInventory(player);
        if (sharedState != null) {
            applyStateTo(player, sharedState);
        }
        BurmaldaLogger.info("ObshchagaLink: added " + player.getName().getString());
    }

    private void snapshotInventory(ServerPlayerEntity player) {
        inventorySnapshots.put(player.getUuid(), copyInventory(player));
    }

    private static List<ItemStack> copyInventory(ServerPlayerEntity player) {
        List<ItemStack> copy = new ArrayList<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            copy.add(player.getInventory().getStack(i).copy());
        }
        return copy;
    }

    private static void applyStateTo(ServerPlayerEntity player, List<ItemStack> state) {
        int size = Math.min(state.size(), player.getInventory().size());
        for (int i = 0; i < size; i++) {
            player.getInventory().setStack(i, state.get(i).copy());
        }
        player.playerScreenHandler.syncState();
    }

    public boolean contains(UUID uuid) {
        return playerUuids.contains(uuid);
    }

    public int getPlayerCount() {
        return playerUuids.size();
    }

    public List<UUID> getPlayerUuids() {
        return Collections.unmodifiableList(playerUuids);
    }

    /**
     * Called every few ticks. Detects which player changed their inventory
     * and propagates that change to all other linked players.
     */
    public void tickSync(MinecraftServer server) {
        if (isReplicating || sharedState == null) return;
        for (UUID uuid : playerUuids) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            if (!inventoryMatchesState(player, sharedState)) {
                isReplicating = true;
                sharedState = copyInventory(player);
                for (UUID otherId : playerUuids) {
                    if (otherId.equals(uuid)) continue;
                    ServerPlayerEntity other = server.getPlayerManager().getPlayer(otherId);
                    if (other == null) continue;
                    applyStateTo(other, sharedState);
                }
                isReplicating = false;
                BurmaldaLogger.info("ObshchagaLink: synced from " + player.getName().getString());
                break; // one change per call to avoid conflicts
            }
        }
    }

    private static boolean inventoryMatchesState(ServerPlayerEntity player, List<ItemStack> state) {
        int size = Math.min(player.getInventory().size(), state.size());
        for (int i = 0; i < size; i++) {
            ItemStack current = player.getInventory().getStack(i);
            ItemStack expected = state.get(i);
            if (current.getCount() != expected.getCount()) return false;
            if (!ItemStack.areItemsAndComponentsEqual(current, expected)) return false;
        }
        return true;
    }

    /**
     * Called when Общага ends.
     * Host keeps their current inventory (whatever they accumulated).
     * All non-host players get their pre-link inventory restored.
     * Pass the disconnecting player directly so their snapshot can be applied
     * even if they are already removed from the player manager.
     */
    public void restore(MinecraftServer server, ServerPlayerEntity disconnecting) {
        isReplicating = true;
        for (UUID uuid : playerUuids) {
            if (uuid.equals(hostUuid)) continue;
            List<ItemStack> snapshot = inventorySnapshots.get(uuid);
            if (snapshot == null) continue;
            ServerPlayerEntity player = uuid.equals(disconnecting != null ? disconnecting.getUuid() : null)
                    ? disconnecting
                    : server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            applyStateTo(player, snapshot);
        }
        isReplicating = false;
        BurmaldaLogger.info("ObshchagaLink restored.");
    }
}
