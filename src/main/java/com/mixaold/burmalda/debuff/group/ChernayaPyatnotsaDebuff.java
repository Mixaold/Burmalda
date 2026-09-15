package com.mixaold.burmalda.debuff.group;

import com.mixaold.burmalda.debuff.Debuff;
import com.mixaold.burmalda.debuff.DebuffType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.List;

public class ChernayaPyatnotsaDebuff extends Debuff {

    private static final int   TICK_INTERVAL = 5;    // pull every 5 ticks
    private static final double SEARCH_RADIUS = 64.0; // blocks around each player
    private static final double MIN_DIST      = 1.5;  // stop pulling when this close
    private static final double BASE_SPEED    = 0.4;  // blocks/tick at long range

    public ChernayaPyatnotsaDebuff() {
        super("chernaya_pyatnitsa", "Black Friday",
                "All ground items fly toward players",
                DebuffType.GROUP);
    }

    @Override
    public void onGroupStart(MinecraftServer server) {
        server.getPlayerManager().broadcast(
                Text.translatableWithFallback("burmalda.chat.chernaya_pyatnitsa.start", "§6[Black Friday] §fEverything on the ground flies to you. SALES!"), false);
    }

    @Override
    public void onGroupTick(MinecraftServer server, int ticksActive) {
        if (ticksActive % TICK_INTERVAL != 0) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : players) {
                Box box = Box.of(com.mixaold.burmalda.util.BurmaldaCompat.pos(player), SEARCH_RADIUS * 2, SEARCH_RADIUS, SEARCH_RADIUS * 2);
                List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box,
                        e -> !e.isRemoved() && com.mixaold.burmalda.util.BurmaldaCompat.entityWorld(e) == world);

                for (ItemEntity item : items) {
                    // Find nearest player to this item
                    ServerPlayerEntity nearest = null;
                    double minDist = Double.MAX_VALUE;
                    for (ServerPlayerEntity p : players) {
                        if (com.mixaold.burmalda.util.BurmaldaCompat.world(p) != world) continue;
                        double d = p.squaredDistanceTo(item);
                        if (d < minDist) { minDist = d; nearest = p; }
                    }
                    if (nearest == null) continue;
                    minDist = Math.sqrt(minDist);
                    if (minDist < MIN_DIST) continue;

                    double dx = nearest.getX() - item.getX();
                    double dy = nearest.getY() + 0.5 - item.getY();
                    double dz = nearest.getZ() - item.getZ();
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double speed = Math.min(1.0, BASE_SPEED + 2.0 / minDist);

                    item.setVelocity(dx / len * speed, dy / len * speed, dz / len * speed);
                    item.setPickupDelay(0);
                }
            }
        }
    }
}
