package com.mixaold.burmalda.timer;

import com.mixaold.burmalda.advancement.BurmaldaAdvancements;
import com.mixaold.burmalda.debuff.DebuffManager;
import com.mixaold.burmalda.state.BurmaldaState;
import com.mixaold.burmalda.util.BurmaldaLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class DebuffTimer {

    /** 3 minutes at 20 TPS */
    public static final int CYCLE_TICKS = 3600;
    /** Group debuff fires every 2nd solo cycle */
    public static final int GROUP_EVERY_N_CYCLES = 2;

    /** Countdown for the initial 5-second delay before first debuff assignment. */
    private static int pendingFirstAssignTicks = 0;

    public static void schedulePendingAssign() {
        pendingFirstAssignTicks = 100; // 5 seconds
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DebuffTimer::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        BurmaldaState state = BurmaldaState.getServerState(server);
        if (!state.modActive) return;

        // Handle initial assignment delay
        if (pendingFirstAssignTicks > 0) {
            pendingFirstAssignTicks--;
            if (pendingFirstAssignTicks == 0) {
                DebuffManager.assignSoloDebuffs(server);
            }
            return;
        }

        state.ticksInCycle++;
        state.markDirty();

        // Sync timer to all players every 20 ticks (1 second)
        if (state.ticksInCycle % 20 == 0) {
            int remaining = CYCLE_TICKS - state.ticksInCycle;
            DebuffManager.broadcastTimerSync(server, remaining, state.cycleCount);
        }

        // Tick active debuffs every tick
        DebuffManager.tickPlayerDebuffs(server);

        // Sync Obshchaga shared inventories every 5 ticks
        if (state.ticksInCycle % 5 == 0) {
            DebuffManager.tickObshchagaLinks(server);
        }

        // Tick group debuff if active
        if (state.currentGroupDebuffId != null) {
            state.groupDebuffTicks++;
            DebuffManager.tickGroupDebuff(server);

            // Group debuff also lasts CYCLE_TICKS (3 min)
            if (state.groupDebuffTicks >= CYCLE_TICKS) {
                DebuffManager.endGroupDebuff(server);
            }
        }

        // New cycle
        if (state.ticksInCycle >= CYCLE_TICKS) {
            state.ticksInCycle = 0;
            state.cycleCount++;
            BurmaldaLogger.info("New debuff cycle #" + state.cycleCount);

            // Cycle survived — only grant to players in the red (≤40% HP)
            for (var p : server.getPlayerManager().getPlayerList())
                if (p.isAlive() && p.getHealth() <= p.getMaxHealth() * 0.4f)
                    BurmaldaAdvancements.trigger(p, BurmaldaAdvancements.CYCLE_SURVIVED);
            // Assign solo debuffs to all online players
            DebuffManager.assignSoloDebuffs(server);

            // Every 3rd cycle → also assign a group debuff
            if (state.cycleCount % GROUP_EVERY_N_CYCLES == 0) {
                DebuffManager.assignGroupDebuff(server);
            }

            state.markDirty();
        }
    }
}
